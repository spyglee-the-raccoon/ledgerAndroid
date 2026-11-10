package work.spyglee.ladger.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import work.spyglee.ladger.LadgerApp
import work.spyglee.ladger.QuickAction
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.domain.WidgetTransaction
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.theme.ExpenseColor
import work.spyglee.ladger.ui.theme.IncomeColor
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.withPhoneRegion

/** The three latest transactions, as the rows of the transaction lists (iOS `RecentTransactionsWidget`). */
class RecentTransactionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = (context.applicationContext as LadgerApp).container.widgetData
        val initial = data.load()
        provideContent {
            val state by remember { data.observe() }.collectAsState(initial)
            WidgetFrame(link = QuickAction.OPEN_ACCOUNTS) {
                val recent = state.snapshot.recentTransactions
                if (recent.isEmpty()) {
                    EmptyState(Icons.AutoMirrored.Outlined.ListAlt, LocalContext.current.getString(R.string.no_transactions))
                } else {
                    TransactionRows(recent, state.hideAmounts)
                }
            }
        }
    }
}

class RecentTransactionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RecentTransactionsWidget()
}

/** As many of the transactions as fit, up to three, with a line between them. */
@Composable
private fun TransactionRows(transactions: List<WidgetTransaction>, hideAmounts: Boolean) {
    val rowHeight = 38.dp
    val fitting = ((LocalSize.current.height - 24.dp) / rowHeight).toInt().coerceIn(1, transactions.size)
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        transactions.take(fitting).forEachIndexed { index, item ->
            if (index > 0) {
                Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.surfaceVariant)) {}
            }
            TransactionRow(item, hideAmounts, modifier = GlanceModifier.fillMaxWidth().height(rowHeight))
        }
    }
}

/**
 * Category badge, title (and note), amount colored by kind, and the time or, before today, the date,
 * as iOS's `TransactionCardContent`. With the app lock on, no amount and no note (D5).
 */
@Composable
private fun TransactionRow(item: WidgetTransaction, hideAmounts: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    val transaction = item.transaction
    val category = item.category
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when {
            transaction.kind == TransactionKind.TRANSFER -> Badge(Icons.Outlined.SyncAlt, CategoryColor.GRAY, 26.dp)
            category != null -> Badge(iconFor(category.systemImage), category.color, 26.dp)
            else -> Badge(Icons.Outlined.QuestionMark, CategoryColor.GRAY, 26.dp)
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            val title = when {
                transaction.kind == TransactionKind.TRANSFER -> context.getString(R.string.transfer)
                category != null -> category.name
                else -> context.getString(R.string.uncategorized)
            }
            Text(title, style = primaryText(), maxLines = 1)
            if (!hideAmounts && transaction.note.isNotBlank()) {
                Text(transaction.note, style = secondaryText(11.sp), maxLines = 1)
            }
        }
        if (!hideAmounts) {
            val color = when (transaction.kind) {
                TransactionKind.EXPENSE -> ColorProvider(ExpenseColor)
                TransactionKind.INCOME -> ColorProvider(IncomeColor)
                TransactionKind.TRANSFER -> GlanceTheme.colors.onSurface
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(formatMoney(transaction.amount), style = primaryText(bold = true, color = color), maxLines = 1)
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(dateText(context, transaction.date), style = primaryText(size = 12.sp), maxLines = 1)
    }
}

/** The time for today's transactions, else day and short month, as iOS's cards with dates. */
private fun dateText(context: Context, date: Instant): String {
    val locale = withPhoneRegion(context.resources.configuration.locales[0] ?: Locale.getDefault())
    val zone = ZoneId.systemDefault()
    val local = date.atZone(zone)
    return if (local.toLocalDate() == LocalDate.now(zone)) {
        local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    } else {
        local.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
    }
}
