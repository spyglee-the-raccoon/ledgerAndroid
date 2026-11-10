package work.spyglee.ladger.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.Duration

/**
 * Exact timing for planned transactions and the daily reminder (decision D10).
 *
 * Below Android 12 exact alarms need no permission. Android 12 and 13 grant it on install. From
 * Android 14 the user has to allow it, and can take it back. Without it the alarms stay as before:
 * inexact, so Android may deliver them a few minutes late, or later while the phone is idle.
 */
object ExactAlarms {
    /** Whether the app may set exact alarms now. */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** The system page where the user allows exact alarms for this app; Android 12 and later only. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
}

/**
 * Sets [operation] for [triggerAt] (wall-clock milliseconds): exactly, also while the phone is idle, when
 * [ExactAlarms.allowed]; otherwise within [window] after it, as before D10.
 */
internal fun AlarmManager.setAt(context: Context, triggerAt: Long, window: Duration, operation: PendingIntent) {
    if (ExactAlarms.allowed(context)) {
        try {
            setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            return
        } catch (_: SecurityException) {
            // The user took the permission back just now; fall through to an inexact alarm.
        }
    }
    setWindow(AlarmManager.RTC_WAKEUP, triggerAt, window.toMillis(), operation)
}
