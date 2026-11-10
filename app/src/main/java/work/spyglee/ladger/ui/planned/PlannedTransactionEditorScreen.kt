package work.spyglee.ladger.ui.planned

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.RecurrenceFrequency
import work.spyglee.ladger.data.Source
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.AmountInput
import work.spyglee.ladger.domain.PlannedTransactionDraft
import work.spyglee.ladger.domain.Recurrence
import work.spyglee.ladger.domain.categoryKind
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.components.LadgerTopBar
import work.spyglee.ladger.ui.components.SegmentedSelector
import work.spyglee.ladger.ui.components.SwitchRow
import work.spyglee.ladger.ui.formatDate
import work.spyglee.ladger.ui.localDecimalSeparator
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor
import work.spyglee.ladger.ui.transactions.PickerField
import work.spyglee.ladger.ui.transactions.PickerOption
import work.spyglee.ladger.ui.transactions.kindLabel

class PlannedTransactionEditorViewModel(
    container: AppContainer,
    private val plannedId: Long?,
    private val decimalSeparator: Char,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val repository = container.plannedTransactions

    val isEditing = plannedId != null

    private val _draft = MutableStateFlow(PlannedTransactionDraft())
    val draft: StateFlow<PlannedTransactionDraft> = _draft.asStateFlow()

    /** The amount as typed; the draft holds the parsed value. */
    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    val categories: StateFlow<List<Category>> =
        container.database.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sources: StateFlow<List<Source>> =
        container.database.sourceDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (plannedId != null) {
            viewModelScope.launch {
                repository.get(plannedId)?.let { planned ->
                    _draft.value = PlannedTransactionDraft.from(planned)
                    _amountText.value = AmountInput.format(planned.amount, decimalSeparator)
                }
            }
        }
    }

    fun setAmountText(text: String) {
        _amountText.value = text
        _draft.update { it.copy(amount = AmountInput.parse(text)) }
    }

    fun setKind(kind: TransactionKind) = _draft.update { it.withKind(kind, categories.value) }
    fun setSource(id: Long?) = _draft.update { it.copy(sourceId = id) }
    fun setDestination(id: Long?) = _draft.update { it.copy(destinationId = id) }
    fun setCategory(id: Long?) = _draft.update { it.copy(categoryId = id) }
    fun setNote(note: String) = _draft.update { it.copy(note = note) }
    fun setFrequency(frequency: RecurrenceFrequency) = _draft.update { it.copy(frequency = frequency) }
    fun setInterval(interval: Int) = _draft.update { it.copy(interval = interval.coerceIn(Recurrence.INTERVALS)) }
    fun setPaused(paused: Boolean) = _draft.update { it.copy(isPaused = paused) }
    fun setHasEndDate(enabled: Boolean) = _draft.update { it.withEndDate(enabled, zone) }

    /** Picking a day keeps the time of day, as iOS's date-only picker does. */
    fun setStartDay(day: LocalDate) = _draft.update { it.copy(startDate = it.startDate.onDay(day)) }
    fun setEndDay(day: LocalDate) = _draft.update { it.copy(endDate = (it.endDate ?: it.startDate).onDay(day)) }

    private fun Instant.onDay(day: LocalDate): Instant = atZone(zone).with(day).toInstant()

    /** Saves, records what is already due, and then calls [onSaved]; does nothing while the draft is incomplete. */
    fun save(onSaved: () -> Unit) {
        val draft = _draft.value
        if (!draft.isValid) return
        viewModelScope.launch {
            repository.save(plannedId, draft)
            onSaved()
        }
    }
}

/** Adds or edits a planned transaction, as iOS's `AddPlannedTransactionView`. */
@Composable
fun PlannedTransactionEditorScreen(plannedId: Long?, onDone: () -> Unit) {
    val decimalSeparator = localDecimalSeparator()
    val viewModel = appViewModel { PlannedTransactionEditorViewModel(it, plannedId, decimalSeparator) }
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val available = categories.filter { it.kind == draft.kind.categoryKind }
    val sourceOptions = sources.map { PickerOption(it.id, it.name, iconFor(it.systemImage), it.color.toColor()) }

    // As on iOS, saving asks to allow notifications, and saves whatever the answer.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.save(onDone)
    }
    val save = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.save(onDone)
    }

    Scaffold(
        topBar = {
            LadgerTopBar(
                title = stringResource(if (viewModel.isEditing) R.string.edit_planned_transaction else R.string.add_planned_transaction),
                onBack = onDone,
            ) {
                IconButton(onClick = save, enabled = draft.isValid) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                PickerField(stringResource(R.string.source), sourceOptions, draft.sourceId, viewModel::setSource, onClear = { viewModel.setSource(null) })
            }
            if (draft.kind != TransactionKind.EXPENSE) {
                PickerField(
                    stringResource(R.string.destination),
                    sourceOptions,
                    draft.destinationId,
                    viewModel::setDestination,
                    onClear = { viewModel.setDestination(null) },
                )
            }
            if (draft.kind != TransactionKind.TRANSFER) {
                PickerField(
                    label = stringResource(R.string.category),
                    options = available.map { PickerOption(it.id, it.name, iconFor(it.systemImage), it.color.toColor()) },
                    selectedId = draft.categoryId,
                    onSelect = viewModel::setCategory,
                    onClear = { viewModel.setCategory(null) },
                )
            }

            Column {
                Text(
                    stringResource(R.string.schedule),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrequencyField(draft.frequency, viewModel::setFrequency)
                    IntervalStepper(draft.frequency, draft.interval, viewModel::setInterval)
                    DateField(stringResource(R.string.start_date), draft.startDate, onChange = viewModel::setStartDay)
                    SwitchRow(stringResource(R.string.has_end_date), draft.endDate != null, viewModel::setHasEndDate)
                    draft.endDate?.let { end ->
                        DateField(stringResource(R.string.end_date), end, notBefore = draft.startDate, onChange = viewModel::setEndDay)
                    }
                    if (viewModel.isEditing) {
                        SwitchRow(stringResource(R.string.paused), draft.isPaused, viewModel::setPaused)
                    }
                }
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.note)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyField(selected: RecurrenceFrequency, onSelect: (RecurrenceFrequency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = frequencyLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.frequency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecurrenceFrequency.entries.forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(frequencyLabel(frequency)) },
                    onClick = {
                        onSelect(frequency)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** "Monthly" or "Every 3 months" with minus and plus buttons, like iOS's stepper. */
@Composable
private fun IntervalStepper(frequency: RecurrenceFrequency, interval: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(scheduleDescription(frequency, interval), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(interval - 1) }, enabled = interval > Recurrence.INTERVALS.first) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
        }
        IconButton(onClick = { onChange(interval + 1) }, enabled = interval < Recurrence.INTERVALS.last) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
        }
    }
}

/** Shows a date; tapping picks another day. Days before [notBefore] can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: Instant, onChange: (LocalDate) -> Unit, notBefore: Instant? = null) {
    val zone = ZoneId.systemDefault()
    var picking by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = formatDate(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { picking = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.change_date))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (picking) {
        // The date picker works in UTC milliseconds.
        val firstDay = notBefore?.atZone(zone)?.toLocalDate()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    firstDay == null || !utcDay(utcTimeMillis).isBefore(firstDay)
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(utcDay(it)) }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

private fun utcDay(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
