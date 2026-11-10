package work.spyglee.ladger.ui.transactions

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.BuildConfig
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.CategoryKind
import work.spyglee.ladger.data.ReceiptItem
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.AmountInput
import work.spyglee.ladger.domain.SourceSuggestion
import work.spyglee.ladger.domain.TransactionDraft
import work.spyglee.ladger.domain.receipt.ParsedReceiptItem
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

    /** True from confirming a receipt until its items are read; saving then would drop the items still loading. */
    private val _isRecognizingItems = MutableStateFlow(false)
    val isRecognizingItems: StateFlow<Boolean> = _isRecognizingItems.asStateFlow()

    /** The merchant of the last scanned receipt; its category and source are remembered on save. */
    private var scannedMerchant: String? = null

    /** The scanned page (JPEG) and the products read from it, saved with a new transaction. */
    private val _scannedPhotos = MutableStateFlow<List<ByteArray>>(emptyList())
    val scannedPhotos: StateFlow<List<ByteArray>> = _scannedPhotos.asStateFlow()
    private val _scannedItems = MutableStateFlow<List<ParsedReceiptItem>>(emptyList())
    val scannedItems: StateFlow<List<ParsedReceiptItem>> = _scannedItems.asStateFlow()

    /** What an edited transaction was saved with. */
    val savedPhotos: StateFlow<List<File>> = (transactionId?.let(container.receiptFiles::observePhotos) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedItems: StateFlow<List<ReceiptItem>> = (transactionId?.let(container.receiptFiles::observeItems) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** Reads the photo and keeps a smaller copy of it to save with the transaction, as iOS keeps scanned pages. */
    fun onPhotoConfirmed(photo: Bitmap) = scan(photo) { container.receipts.recognize(photo) }

    fun removeScannedItem(index: Int) = _scannedItems.update { items -> items.filterIndexed { i, _ -> i != index } }

    fun deleteSavedItem(item: ReceiptItem) {
        viewModelScope.launch { container.receiptFiles.deleteItem(item) }
    }

    /**
     * Shows "parsing" right away, then fills the form from the recognized text, as iOS's parseText does.
     * The products are read afterwards in their own request while the form can already be edited.
     * A [photo] is meanwhile made into the page saved with the transaction.
     */
    private fun scan(photo: Bitmap? = null, recognize: suspend () -> String) {
        _isParsing.value = true
        _isRecognizingItems.value = true
        _scannedItems.value = emptyList()
        _scannedPhotos.value = emptyList()
        viewModelScope.launch {
            val page = photo?.let { async { container.receipts.photoData(it) } }
            try {
                readReceipt(recognize())
                page?.let { _scannedPhotos.value = listOf(it.await()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An unreadable photo or file leaves the form as it was, as on iOS.
                Log.w(TAG, "Reading the receipt failed", e)
            } finally {
                _isParsing.value = false
                _isRecognizingItems.value = false
            }
        }
    }

    private suspend fun readReceipt(text: String) {
        if (text.isBlank()) return
        val categories = database.categoryDao().getAll()
        val receipt = container.receipts.parse(text, categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.name })
        if (BuildConfig.DEBUG) Log.d(TAG, "Recognized receipt text:\n$text\nParsed: $receipt")
        val learned = receipt.merchant?.let { container.merchants.learned(it) }
        scannedMerchant = receipt.merchant
        _draft.update { it.withReceipt(receipt, learned, categories) }
        _draft.value.amount?.let { _amountText.value = AmountInput.format(it, decimalSeparator) }

        _isParsing.value = false
        _scannedItems.value = container.receipts.parseItems(text)
        if (BuildConfig.DEBUG) Log.d(TAG, "Recognized items: ${_scannedItems.value}")
    }

    /**
     * Saves and then calls [onSaved]; does nothing while the draft is incomplete or items are still
     * being read. A new transaction keeps the scanned page, and its items if it is an expense, as on iOS.
     */
    fun save(onSaved: () -> Unit) {
        val draft = _draft.value
        if (!draft.isValid || _isRecognizingItems.value) return
        viewModelScope.launch {
            if (transactionId != null) {
                transactionDao.update(draft.toTransaction(transactionId))
            } else {
                val id = transactionDao.insert(draft.toTransaction())
                val items = if (draft.kind == TransactionKind.EXPENSE) _scannedItems.value else emptyList()
                container.receiptFiles.attach(id, _scannedPhotos.value, items)
                container.saveSound.play()
            }
            learnMerchantCategory(draft)
            container.budgets.check()
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
