package work.spyglee.ladger.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.math.abs
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.CategorySlice
import work.spyglee.ladger.ui.appViewModel
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.headerDate
import work.spyglee.ladger.ui.monthTitle
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor

/** How far a horizontal swipe must go to change the month, as iOS's minimum distance. */
private val SWIPE_DISTANCE = 30.dp

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenSourcesOverview: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenIncomes: () -> Unit,
) {
    val viewModel = appViewModel { MainViewModel(it.database) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnDateChange(viewModel::refreshDate)

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    headerDate(state.today),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
            // Side by side on wide screens (tablets, landscape), stacked on phones, as on iOS.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        ChartPanel(state, onAddTransaction, viewModel::showPreviousMonth, viewModel::showNextMonth, Modifier.weight(1f).fillMaxHeight())
                        BalancePanel(state, onOpenSourcesOverview, onOpenExpenses, onOpenIncomes, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ChartPanel(state, onAddTransaction, viewModel::showPreviousMonth, viewModel::showNextMonth, Modifier.weight(1f).fillMaxWidth())
                        BalancePanel(state, onOpenSourcesOverview, onOpenExpenses, onOpenIncomes, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** What the donut shows; a new [month] fades in as a new chart, as on iOS. */
private data class MonthChartContent(val month: YearMonth, val isCurrent: Boolean, val slices: List<CategorySlice>, val balance: BigDecimal)

/**
 * The donut and the add button. Swiping right shows the previous month and swiping left the next,
 * as on iOS; TalkBack users get the same as custom actions.
 */
@Composable
private fun ChartPanel(
    state: MainUiState,
    onAddTransaction: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier,
) {
    val swipeDistance = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    val currentOnPrevious by rememberUpdatedState(onPreviousMonth)
    val currentOnNext by rememberUpdatedState(onNextMonth)
    val previousLabel = stringResource(R.string.previous_month)
    val nextLabel = stringResource(R.string.next_month)
    Column(
        modifier
            .pointerInput(Unit) {
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDragEnd = {
                        if (abs(drag.x) > abs(drag.y) && abs(drag.x) >= swipeDistance) {
                            if (drag.x > 0) currentOnPrevious() else currentOnNext()
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            }
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(previousLabel) { onPreviousMonth(); true }.takeIf { state.canShowPreviousMonth },
                    CustomAccessibilityAction(nextLabel) { onNextMonth(); true }.takeIf { state.canShowNextMonth },
                )
            }
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = MonthChartContent(state.month, state.isCurrentMonth, state.slices, state.monthBalance),
            contentKey = { it.month },
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.9f))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f, fill = false),
            label = "month",
        ) { chart ->
            val title = if (chart.isCurrent) stringResource(R.string.this_month_balance) else monthTitle(chart.month)
            DonutChart(chart.slices, chart.balance, title)
        }
        FilledTonalButton(onClick = onAddTransaction) {
            Text(stringResource(R.string.add_transaction), modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun BalancePanel(
    state: MainUiState,
    onOpenSourcesOverview: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenIncomes: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.total_balance).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatMoney(state.totalBalance),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onOpenSourcesOverview).padding(horizontal = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryButton(
                label = stringResource(R.string.expense),
                amount = state.monthExpenses,
                icon = Icons.Filled.ArrowUpward,
                color = ExpenseColor,
                onClick = onOpenExpenses,
            )
            SummaryButton(
                label = stringResource(R.string.income),
                amount = state.monthIncomes,
                icon = Icons.Filled.ArrowDownward,
                color = IncomeColor,
                onClick = onOpenIncomes,
            )
        }
    }
}

/** The shown month's expense or income total; opens the full list. */
@Composable
private fun SummaryButton(label: String, amount: BigDecimal, icon: ImageVector, color: Color, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(25.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMoney(amount), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Calls [refresh] when the screen resumes and whenever the date, time or time zone changes. */
@Composable
private fun RefreshOnDateChange(refresh: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}
