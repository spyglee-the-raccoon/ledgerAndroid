package work.spyglee.ladger.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import work.spyglee.ladger.LadgerApp
import work.spyglee.ladger.domain.MonthRange

/**
 * Keeps the home screen widgets current (D4). Unlike iOS, which refreshes them when the app is left,
 * they update after every change, because they read the database directly:
 * - while a widget is showing, it watches the data itself ([WidgetData.observe]);
 * - [watch] wakes widgets whose update has finished when the data changes;
 * - an alarm at the start of each month resets this month's numbers, as iOS's timeline does;
 * - reboots, clock, time zone and language changes update them too.
 */
object Widgets {
    private val all: List<GlanceAppWidget> get() = listOf(MonthChartWidget(), BudgetWidget(), RecentTransactionsWidget())

    /** Redraws every placed widget, and sets (or clears) the alarm for the next month's start. */
    suspend fun updateAll(context: Context) {
        all.forEach { it.updateAll(context) }
        scheduleMonthStart(context, hasWidgets = all.any { GlanceAppWidgetManager(context).getGlanceIds(it.javaClass).isNotEmpty() })
    }

    /** Updates the widgets after each change to their data or the app lock, while the app's process runs. */
    @OptIn(FlowPreview::class)
    fun watch(context: Context, data: WidgetData, scope: CoroutineScope) {
        scope.launch {
            // The first value is the current state, which the widgets already show.
            data.changes().drop(1).debounce(DEBOUNCE.toMillis()).collect { updateAll(context) }
        }
    }

    private fun scheduleMonthStart(context: Context, hasWidgets: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetMonthReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!hasWidgets) {
            alarmManager.cancel(intent)
            return
        }
        val nextMonth = MonthRange.containing(Instant.now(), ZoneId.systemDefault()).end
        // Not a wake-up alarm: the new month's numbers only matter once someone looks at the screen.
        alarmManager.setWindow(AlarmManager.RTC, nextMonth.toEpochMilli(), MONTH_WINDOW.toMillis(), intent)
    }

    private val DEBOUNCE: Duration = Duration.ofMillis(500)
    private val MONTH_WINDOW: Duration = Duration.ofMinutes(10)
}

/** The month started: this month's chart, balance and budget start from zero. */
class WidgetMonthReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LadgerApp
        val pending = goAsync()
        app.container.applicationScope.launch {
            try {
                Widgets.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
