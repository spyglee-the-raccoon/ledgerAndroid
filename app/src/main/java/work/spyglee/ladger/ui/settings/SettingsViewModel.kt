package work.spyglee.ladger.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.domain.csv.CsvImportResult

/** The result alerts of CSV import and export, as on iOS. */
sealed interface SettingsDialog {
    data class ImportFinished(val result: CsvImportResult) : SettingsDialog
    data class ImportFailed(val error: Exception) : SettingsDialog
    data class ExportFinished(val transactionCount: Int) : SettingsDialog

    /** A null [error] means there was nothing to export. */
    data class ExportFailed(val error: Exception?) : SettingsDialog
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val isReminderOn: StateFlow<Boolean> =
        container.settings.isReminderOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val isAppLockOn: StateFlow<Boolean> =
        container.settings.isAppLockOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _dialog = MutableStateFlow<SettingsDialog?>(null)
    val dialog: StateFlow<SettingsDialog?> = _dialog.asStateFlow()

    /** Turning the reminder on must only happen once notifications are allowed. */
    fun setReminderOn(on: Boolean) {
        viewModelScope.launch {
            if (on) container.reminder.schedule() else container.reminder.cancel()
            container.settings.setReminderOn(on)
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _dialog.value = try {
                SettingsDialog.ImportFinished(container.csv.import(container.csvDocuments.read(uris)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsDialog.ImportFailed(e)
            }
        }
    }

    /** Checked before the folder picker opens, so an empty database shows a message instead. */
    suspend fun hasTransactionsToExport(): Boolean = container.csv.hasTransactions()

    fun showNothingToExport() {
        _dialog.value = SettingsDialog.ExportFailed(null)
    }

    fun export(folder: Uri) {
        viewModelScope.launch {
            _dialog.value = try {
                val export = container.csv.export()
                container.csvDocuments.writeToFolder(folder, export.files)
                SettingsDialog.ExportFinished(export.transactionCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsDialog.ExportFailed(e)
            }
        }
    }

    fun dismissDialog() {
        _dialog.value = null
    }
}
