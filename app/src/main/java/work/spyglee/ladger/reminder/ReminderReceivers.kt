package work.spyglee.ladger.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import work.spyglee.ladger.LadgerApp

/** The reminder alarm went off: show the notification and schedule tomorrow's. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = (context.applicationContext as LadgerApp).container.reminder
        reminder.show()
        reminder.schedule()
    }
}

/**
 * Alarms are cleared on reboot and app updates, and a clock or time zone change moves 20:30,
 * so the reminder is scheduled again in all of these cases if it's on.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as LadgerApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                if (container.settings.isReminderOn.first()) container.reminder.schedule()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
