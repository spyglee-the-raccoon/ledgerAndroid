package work.spyglee.ladger.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.ZonedDateTime
import work.spyglee.ladger.R
import work.spyglee.ladger.domain.BudgetHint
import work.spyglee.ladger.domain.BudgetUsage
import work.spyglee.ladger.domain.Budgets
import work.spyglee.ladger.ui.formatMoney

/**
 * The daily reminder notification. It is scheduled one day at a time with an inexact alarm, which
 * needs no exact-alarm permission; [ReminderReceiver] shows it and schedules the next one.
 */
class DailyReminder(context: Context) {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Schedules the next reminder, replacing any pending one. */
    fun schedule(now: ZonedDateTime = ZonedDateTime.now()) {
        val trigger = ReminderSchedule.nextTrigger(now).toInstant().toEpochMilli()
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW.toMillis(), alarmIntent())
    }

    fun cancel() {
        alarmManager.cancel(alarmIntent())
    }

    /** False when the user has turned off notifications for the app (or not yet allowed them on Android 13+). */
    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Shows the reminder; with an active general limit ([general]) it adds how much of it is used, as on iOS. */
    fun show(general: BudgetUsage?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val reminder = context.getString(R.string.reminder_body)
        val body = general?.let { "$reminder\n${budgetHint(it)}" } ?: reminder
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * One of seven lines by how much of the general limit is used. Unlike iOS, which fixes the text when
     * it schedules the repeating reminder, it is worked out when the reminder goes off, so it is current.
     */
    private fun budgetHint(usage: BudgetUsage): String = when (Budgets.hint(usage.ratio)) {
        BudgetHint.WELL_UNDER -> context.getString(R.string.budget_hint_well_under)
        BudgetHint.UNDER_HALF -> context.getString(R.string.budget_hint_under_half)
        BudgetHint.OVER_HALF -> context.getString(R.string.budget_hint_over_half)
        BudgetHint.GETTING_CLOSE -> context.getString(R.string.budget_hint_getting_close)
        BudgetHint.ALMOST -> context.getString(R.string.budget_hint_almost, formatMoney(usage.remaining))
        BudgetHint.REACHED -> context.getString(R.string.budget_hint_reached)
        BudgetHint.OVER -> context.getString(R.string.budget_hint_over, formatMoney(usage.remaining.negate()))
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1

        /** How late the system may deliver the reminder, so it can batch it with other alarms. */
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
