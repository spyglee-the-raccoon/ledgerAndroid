package work.spyglee.ladger.ui.transactions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.categoryKind
import work.spyglee.ladger.scan.ReceiptCameraScreen
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.CategoryIcon
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.ReadableWidth
import work.spyglee.ladger.ui.components.ReceiptItemRow
import work.spyglee.ladger.ui.components.ReceiptPage
import work.spyglee.ladger.ui.components.ReceiptPhotos
import work.spyglee.ladger.ui.components.SegmentedSelector
import work.spyglee.ladger.ui.components.SwipeToDelete
import work.spyglee.ladger.ui.formatDateTime
import work.spyglee.ladger.ui.localDecimalSeparator
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

@Composable
fun kindLabel(kind: TransactionKind): String = stringResource(
    when (kind) {
        TransactionKind.INCOME -> R.string.income
        TransactionKind.EXPENSE -> R.string.expense
        TransactionKind.TRANSFER -> R.string.transfer
    }
)

/**
 * Adds or edits a transaction. New transactions can be filled from a receipt photo; a receipt
 * shared from another app ([sharedReceipt]) is read as soon as the screen opens.
 */
@Composable
fun TransactionEditorScreen(transactionId: Long?, initialKind: TransactionKind, onDone: () -> Unit, sharedReceipt: Uri? = null) {
    val decimalSeparator = localDecimalSeparator()
    val viewModel = appViewModel { TransactionEditorViewModel(it, transactionId, initialKind, decimalSeparator, sharedReceipt) }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val isParsing by viewModel.isParsing.collectAsStateWithLifecycle()
    val isRecognizingItems by viewModel.isRecognizingItems.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val available = categories.filter { it.kind == draft.kind.categoryKind }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val cameraDenied = stringResource(R.string.camera_denied)
    val openSettings = stringResource(R.string.open_settings)
    val openCamera = {
        showCamera = true
        viewModel.onCameraOpened()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            scope.launch {
                if (snackbar.showSnackbar(cameraDenied, actionLabel = openSettings) == SnackbarResult.ActionPerformed) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            }
        }
    }

    if (showCamera) {
        ReceiptCameraScreen(
            onPhotoConfirmed = { photo ->
                showCamera = false
                viewModel.onPhotoConfirmed(photo)
            },
            onClose = { showCamera = false },
        )
        return
    }

    ReadableWidth {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                LadgerTopBar(
                    title = stringResource(if (viewModel.isEditing) R.string.edit_transaction else R.string.add_transaction),
                    onBack = onDone,
                ) {
                    // Saving while the items are read would drop them.
                    IconButton(onClick = { viewModel.save(onDone) }, enabled = draft.isValid && !isRecognizingItems) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DateTimeField(draft.date, viewModel::setDate)
                if (isParsing) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.parsing), modifier = Modifier.padding(start = 12.dp))
                    }
                } else if (!viewModel.isEditing) {
                    FilledTonalButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                openCamera()
                            } else {
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = hasCamera,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.scan_your_receipt))
                    }
                }
                SegmentedSelector(
                    options = TransactionKind.entries,
                    selected = draft.kind,
                    label = { kindLabel(it) },
                    onSelect = viewModel::setKind,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = viewModel::setAmountText,
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.kind != TransactionKind.INCOME) {
                    PickerField(
                        label = stringResource(R.string.source),
                        options = sources.map { PickerOption(it.id, it.name, iconFor(it.systemImage), it.color.toColor()) },
                        selectedId = draft.sourceId,
                        onSelect = viewModel::setSource,
                    )
                }
                if (draft.kind != TransactionKind.EXPENSE) {
                    PickerField(
                        label = stringResource(R.string.destination),
                        options = sources.map { PickerOption(it.id, it.name, iconFor(it.systemImage), it.color.toColor()) },
                        selectedId = draft.destinationId,
                        onSelect = viewModel::setDestination,
                    )
                }
                if (draft.kind != TransactionKind.TRANSFER) {
                    PickerField(
                        label = stringResource(R.string.category),
                        options = available.map { PickerOption(it.id, it.name, iconFor(it.systemImage), it.color.toColor()) },
                        selectedId = draft.categoryId,
                        onSelect = viewModel::setCategory,
                    )
                }
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = viewModel::setNote,
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ReceiptSections(viewModel, draft.kind)
            }
        }
    }
}

/**
 * The scanned receipt page and, for expenses, the products read from it: while adding, what the
 * scan found (items can be swiped away); while editing, what was saved with the transaction.
 */
@Composable
private fun ReceiptSections(viewModel: TransactionEditorViewModel, kind: TransactionKind) {
    val scannedPhotos by viewModel.scannedPhotos.collectAsStateWithLifecycle()
    val savedPhotos by viewModel.savedPhotos.collectAsStateWithLifecycle()
    val scannedItems by viewModel.scannedItems.collectAsStateWithLifecycle()
    val savedItems by viewModel.savedItems.collectAsStateWithLifecycle()
    val isRecognizingItems by viewModel.isRecognizingItems.collectAsStateWithLifecycle()

    val pages = remember(viewModel.isEditing, scannedPhotos, savedPhotos) {
        if (viewModel.isEditing) savedPhotos.map { ReceiptPage.Saved(it) } else scannedPhotos.map { ReceiptPage.Scanned(it) }
    }
    if (pages.isNotEmpty()) {
        Column {
            SectionTitle(stringResource(R.string.receipt))
            ReceiptPhotos(pages)
        }
    }

    if (kind != TransactionKind.EXPENSE) return
    if (viewModel.isEditing && savedItems.isNotEmpty()) {
        Column {
            SectionTitle(stringResource(R.string.receipt_items))
            savedItems.forEach { item ->
                key(item.id) {
                    SwipeToDelete(onDelete = { viewModel.deleteSavedItem(item) }) {
                        ReceiptItemRow(item.name, item.quantity, item.unitPrice, item.totalPrice)
                    }
                }
            }
        }
    } else if (isRecognizingItems || scannedItems.isNotEmpty()) {
        Column {
            SectionTitle(stringResource(R.string.receipt_items))
            if (isRecognizingItems) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.recognizing_items),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            scannedItems.forEachIndexed { index, item ->
                // By identity: equal lines can repeat, and a removed row must not pass its swipe state on.
                key(System.identityHashCode(item)) {
                    SwipeToDelete(onDelete = { viewModel.removeScannedItem(index) }) {
                        ReceiptItemRow(item.name, item.quantity, item.unitPrice, item.totalPrice)
                    }
                }
            }
            if (scannedItems.isNotEmpty()) {
                Text(
                    stringResource(R.string.receipt_items_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A section title aligned with the form's fields, which already have the screen's padding. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

data class PickerOption(val id: Long, val name: String, val icon: ImageVector, val color: Color)

/** A dropdown of sources or categories, each shown with its icon. With [onClear], "None" comes first and clears the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerField(label: String, options: List<PickerOption>, selectedId: Long?, onSelect: (Long) -> Unit, onClear: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = selected?.let { { CategoryIcon(it.icon, it.color, size = 28.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onClear != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.none)) },
                    onClick = {
                        onClear()
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    leadingIcon = { CategoryIcon(option.icon, option.color, size = 28.dp) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Shows the date and time; tapping picks a new date, then a new time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(date: Instant, onChange: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    var step by remember { mutableStateOf(0) } // 0 closed, 1 picking date, 2 picking time
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val current = date.atZone(zone)

    OutlinedTextField(
        value = formatDateTime(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.date)) },
        trailingIcon = {
            IconButton(onClick = { step = 1 }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.change_date))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (step == 1) {
        // The date picker works in UTC milliseconds.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    step = 2
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(dateState) }
    }
    if (step == 2) {
        val timeState = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute)
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val day = pickedDate ?: current.toLocalDate()
                    onChange(LocalDateTime.of(day, LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant())
                    step = 0
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.cancel)) } },
        ) {
            TimePicker(timeState, modifier = Modifier.padding(16.dp))
        }
    }
}
