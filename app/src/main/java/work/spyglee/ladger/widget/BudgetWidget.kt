package work.spyglee.ladger.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import work.spyglee.ladger.LadgerApp
import work.spyglee.ladger.QuickAction
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.domain.BudgetLevel
import work.spyglee.ladger.domain.Budgets
import work.spyglee.ladger.domain.WidgetBudget
import work.spyglee.ladger.ui.formatMoney
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.withPhoneRegion

/** The general monthly limit, or the first active category budget when it's off (iOS `BudgetWidget`). */
class BudgetWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = (context.applicationContext as LadgerApp).container.widgetData
        val initial = data.load()
        provideContent {
            val state by remember { data.observe() }.collectAsState(initial)
            WidgetFrame(link = QuickAction.OPEN_BUDGETS) {
                val budget = state.snapshot.current(Instant.now()).budget
                if (budget == null) {
                    EmptyState(Icons.Outlined.Speed, LocalContext.current.getString(R.string.widget_no_budget))
                } else {
                    BudgetCard(budget, state.hideAmounts)
                }
            }
        }
    }
}

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BudgetWidget()
}

/**
 * The budget card of the budgets screen and iOS's `BudgetCardContent`: name, what's left, spent of the
 * limit, the share used and a bar. With the app lock on, only the name and the bar (D5).
 */
@Composable
private fun BudgetCard(budget: WidgetBudget, hideAmounts: Boolean) {
    val context = LocalContext.current
    val usage = budget.usage
    val barColor = when (Budgets.level(usage)) {
        BudgetLevel.INACTIVE -> CategoryColor.GRAY
        BudgetLevel.FINE -> CategoryColor.GREEN
        BudgetLevel.CLOSE -> CategoryColor.ORANGE
        BudgetLevel.OVER -> CategoryColor.RED
    }
    val limit = usage.budget.monthlyLimit
    val remaining = limit - usage.spent
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            budget.category?.let { category ->
                Badge(iconFor(category.systemImage), category.color, 32.dp)
                Spacer(GlanceModifier.width(8.dp))
            }
            Column {
                Text(budget.category?.name ?: context.getString(R.string.general_budget), style = primaryText(size = 15.sp), maxLines = 1)
                if (!hideAmounts && limit.signum() > 0) {
                    val over = remaining.signum() < 0
                    Text(
                        text = context.getString(if (over) R.string.budget_over else R.string.budget_left, formatMoney(remaining.abs())),
                        style = if (over) primaryText(size = 13.sp, color = CategoryColor.RED.provider()) else secondaryText(13.sp),
                        maxLines = 1,
                    )
                }
            }
            if (hideAmounts) {
                Spacer(GlanceModifier.defaultWeight())
                LockIcon()
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        if (!hideAmounts) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatMoney(usage.spent), style = primaryText(bold = true), maxLines = 1)
                Text(" / ", style = secondaryText(13.sp))
                Text(formatMoney(limit), style = secondaryText(13.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Text(percent(usage.ratio), style = primaryText(bold = true, color = barColor.provider()))
            }
            Spacer(GlanceModifier.height(8.dp))
        }
        LinearProgressIndicator(
            progress = usage.ratio.coerceIn(0.0, 1.0).toFloat(),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = barColor.provider(),
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}

private fun percent(ratio: Double): String =
    NumberFormat.getPercentInstance(withPhoneRegion(Locale.getDefault())).apply { maximumFractionDigits = 0 }.format(ratio)
