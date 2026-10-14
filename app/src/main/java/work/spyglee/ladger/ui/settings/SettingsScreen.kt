package work.spyglee.ladger.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.CsvDocuments
import work.spyglee.ladger.domain.csv.CsvImportException
import work.spyglee.ladger.lock.AuthPurpose
import work.spyglee.ladger.ui.LocalAppContainer
import work.spyglee.ladger.ui.LocalAuthenticator
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ListRow
import work.spyglee.ladger.ui.components.SectionHeader
import work.spyglee.ladger.ui.theme.toColor

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenIncomeExpense: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenTopCategories: () -> Unit,
) {
    val viewModel = appViewModel { SettingsViewModel(it) }
    val isReminderOn by viewModel.isReminderOn.collectAsStateWithLifecycle()
    val isAppLockOn by viewModel.isAppLockOn.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val reminder = LocalAppContainer.current.reminder
    val authenticator = LocalAuthenticator.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val notificationsDenied = stringResource(R.string.notifications_denied)
    val openSettings = stringResource(R.string.open_settings)
    val lockUnavailable = stringResource(R.string.lock_unavailable)
    val showNotificationsDenied: () -> Unit = {
        scope.launch {
            if (snackbar.showSnackbar(notificationsDenied, actionLabel = openSettings) == SnackbarResult.ActionPerformed) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setReminderOn(true) else showNotificationsDenied()
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), viewModel::import)
    val exportFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        folder?.let(viewModel::export)
    }

    // As on iOS, the reminder only turns on once notifications are allowed.
    val onReminderChange: (Boolean) -> Unit = { on ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            !on -> viewModel.setReminderOn(false)
            needsPermission -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            !reminder.canNotify() -> showNotificationsDenied()
            else -> viewModel.setReminderOn(true)
        }
    }

    Scaffold(
        topBar = { LadgerTopBar(stringResource(R.string.settings), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsLink(Icons.Outlined.GridView, CategoryColor.BLUE.toColor(), stringResource(R.string.categories), onOpenCategories)
            SettingsLink(Icons.Outlined.CreditCard, CategoryColor.GREEN.toColor(), stringResource(R.string.sources), onOpenSources)
            SettingsLink(Icons.Outlined.Folder, CategoryColor.CYAN.toColor(), stringResource(R.string.collections), onOpenCollections)
            SettingsToggle(
                Icons.Outlined.NotificationsActive,
                CategoryColor.RED.toColor(),
                stringResource(R.string.remind_me),
                checked = isReminderOn,
                onCheckedChange = onReminderChange,
            )
            // The switch flips only after the user authenticates (MainActivity saves the setting).
            SettingsToggle(
                Icons.Outlined.Fingerprint,
                CategoryColor.INDIGO.toColor(),
                stringResource(R.string.lock_the_app),
                checked = isAppLockOn,
                onCheckedChange = { on ->
                    if (!authenticator.authenticate(AuthPurpose.SetAppLock(on))) {
                        scope.launch { snackbar.showSnackbar(lockUnavailable) }
                    }
                },
            )
            SettingsLink(Icons.Outlined.FileDownload, CategoryColor.ORANGE.toColor(), stringResource(R.string.import_csv)) {
                importPicker.launch(CsvDocuments.IMPORT_MIME_TYPES)
            }
            SettingsLink(Icons.Outlined.IosShare, CategoryColor.TEAL.toColor(), stringResource(R.string.export_csv)) {
                scope.launch {
                    if (viewModel.hasTransactionsToExport()) exportFolderPicker.launch(null) else viewModel.showNothingToExport()
                }
            }

            SectionHeader(stringResource(R.string.statistics), Modifier.padding(top = 16.dp))
            SettingsLink(Icons.Outlined.SwapVert, CategoryColor.GREEN.toColor(), stringResource(R.string.income_expense), onOpenIncomeExpense)
            SettingsLink(Icons.Outlined.MoveToInbox, CategoryColor.ORANGE.toColor(), stringResource(R.string.savings), onOpenSavings)
            SettingsLink(Icons.AutoMirrored.Outlined.TrendingUp, CategoryColor.PURPLE.toColor(), stringResource(R.string.top_categories), onOpenTopCategories)
        }
    }

    dialog?.let { SettingsAlert(it, viewModel::dismissDialog) }
}

@Composable
private fun SettingsLink(icon: ImageVector, tint: Color, title: String, onClick: () -> Unit) {
    ListRow(title = title, leading = { Icon(icon, contentDescription = null, tint = tint) }, onClick = onClick)
}

@Composable
private fun SettingsToggle(icon: ImageVector, tint: Color, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListRow(
        title = title,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        leading = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun SettingsAlert(dialog: SettingsDialog, onDismiss: () -> Unit) {
    val title: String
    val message: String
    when (dialog) {
        is SettingsDialog.ImportFinished -> {
            val result = dialog.result
            title = stringResource(R.string.import_finished)
            message = stringResource(R.string.import_summary, result.importedCount, result.createdSources, result.createdCategories, result.skippedCount)
        }
        is SettingsDialog.ImportFailed -> {
            val error = dialog.error
            title = stringResource(R.string.import_failed)
            message = when {
                error is CsvImportException && error.reason == CsvImportException.Reason.UNKNOWN_FORMAT ->
                    stringResource(R.string.import_error_unknown_format, error.fileName)
                error is CsvImportException -> stringResource(R.string.import_error_unreadable, error.fileName)
                else -> error.localizedMessage.orEmpty()
            }
        }
        is SettingsDialog.ExportFinished -> {
            title = stringResource(R.string.export_finished)
            message = pluralStringResource(R.plurals.export_summary, dialog.transactionCount, dialog.transactionCount)
        }
        is SettingsDialog.ExportFailed -> {
            title = stringResource(R.string.export_failed)
            message = dialog.error?.localizedMessage ?: stringResource(R.string.export_nothing)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(title) },
        text = { Text(message) },
    )
}
