package work.spyglee.ladger.ui.transactions

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.BuildConfig
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.AmountInput
import work.spyglee.ladger.domain.SourceSuggestion
import work.spyglee.ladger.domain.TransactionDraft
import work.spyglee.ladger.domain.receipt.withReceipt

class TransactionEditorViewModel(
    private val container: AppContainer,
    private val transactionId: Long?,
    initialKind: TransactionKind,
    private val decimalSeparator: Char,
    /** A receipt image or PDF shared from another app, read once when the editor opens. */
    sharedReceipt: Uri? = null,
) : ViewModel() {
    private val database = container.database
    private val transactionDao = database.transactionDao()

    val isEditing = transactionId != null

    private val _draft = MutableStateFlow(TransactionDraft(kind = initialKind))
    val draft: StateFlow<TransactionDraft> = _draft.asStateFlow()

    /** The amount as typed; the draft holds the parsed value. */
    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    /** True from confirming a receipt photo until its values are filled in. */
    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    /** The merchant of the last scanned receipt; its category and source are remembered on save. */
    private var scannedMerchant: String? = null

    val categories: StateFlow<List<Category>> =
        database.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sources: StateFlow<List<Source>> =
        database.sourceDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            if (transactionId != null) {
                transactionDao.getById(transactionId)?.let { transaction ->
                    _draft.value = TransactionDraft.from(transaction)
                    _amountText.value = AmountInput.format(transaction.amount, decimalSeparator)
                }
            } else {
                suggestSources()
            }
        }
        sharedReceipt?.let { uri -> scan { container.receipts.recognize(uri) } }
    }

    fun setAmountText(text: String) {
        _amountText.value = text
        _draft.update { it.copy(amount = AmountInput.parse(text)) }
    }

    fun setKind(kind: TransactionKind) {
        if (kind == _draft.value.kind) return
        _draft.update { it.withKind(kind, categories.value) }
        viewModelScope.launch { suggestSources() }
    }

    fun setSource(id: Long) = _draft.update { it.copy(sourceId = id) }
    fun setDestination(id: Long) = _draft.update { it.copy(destinationId = id) }
    fun setCategory(id: Long) = _draft.update { it.copy(categoryId = id) }
    fun setNote(note: String) = _draft.update { it.copy(note = note) }
    fun setDate(date: Instant) = _draft.update { it.copy(date = date) }

    /** The camera opened: load the language model while the user lines up the receipt. */
    fun onCameraOpened() {
        viewModelScope.launch { container.receipts.prewarm() }
    }

    fun onPhotoConfirmed(photo: Bitmap) = scan { container.receipts.recognize(photo) }

    /** Shows "parsing" right away, then fills the form from the recognized text, as iOS's parseText does. */
    private fun scan(recognize: suspend () -> String) {
        _isParsing.value = true
        viewModelScope.launch {
            try {
                val text = recognize()
                if (text.isBlank()) return@launch
                val categories = database.categoryDao().getAll()
                val receipt = container.receipts.parse(text, categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.name })
                if (BuildConfig.DEBUG) Log.d(TAG, "Recognized receipt text:\n$text\nParsed: $receipt")
                val learned = receipt.merchant?.let { container.merchants.learned(it) }
                scannedMerchant = receipt.merchant
                _draft.update { it.withReceipt(receipt, learned, categories) }
                _draft.value.amount?.let { _amountText.value = AmountInput.format(it, decimalSeparator) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An unreadable photo or file leaves the form as it was, as on iOS.
                Log.w(TAG, "Reading the receipt failed", e)
            } finally {
                _isParsing.value = false
            }
        }
    }

    /** Saves and then calls [onSaved]; does nothing while the draft is incomplete. */
    fun save(onSaved: () -> Unit) {
        val draft = _draft.value
        if (!draft.isValid) return
        viewModelScope.launch {
            if (transactionId != null) {
                transactionDao.update(draft.toTransaction(transactionId))
            } else {
                transactionDao.insert(draft.toTransaction())
            }
            learnMerchantCategory(draft)
            onSaved()
        }
    }

    /** Remembers the category and source chosen for a scanned merchant, for its next receipt. */
    private suspend fun learnMerchantCategory(draft: TransactionDraft) {
        val merchant = scannedMerchant ?: return
        val categoryId = draft.categoryId ?: return
        if (draft.kind != TransactionKind.EXPENSE) return
        container.merchants.learn(merchant, categoryId, draft.sourceId)
    }

    /** Fills in the sources the user most likely wants, for new transactions only, as on iOS. */
    private suspend fun suggestSources() {
        if (isEditing) return
        val recent = transactionDao.getRecent(SourceSuggestion.FETCH_LIMIT)
        val draft = _draft.value
        val sourceId = draft.sourceId
            ?: SourceSuggestion.mostLikely(SourceSuggestion.Role.SOURCE, draft.kind, recent).takeIf { draft.kind != TransactionKind.INCOME }
        val destinationId = draft.destinationId
            ?: SourceSuggestion.mostLikely(SourceSuggestion.Role.DESTINATION, draft.kind, recent, excludingId = sourceId)
                .takeIf { draft.kind != TransactionKind.EXPENSE }
        // Only fills empty fields, so a source set meanwhile (e.g. learned from a receipt) is kept.
        _draft.update { it.copy(sourceId = it.sourceId ?: sourceId, destinationId = it.destinationId ?: destinationId) }
    }

    private companion object {
        const val TAG = "TransactionEditor"
    }
}
