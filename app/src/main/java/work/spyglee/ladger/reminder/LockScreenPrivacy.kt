package work.spyglee.ladger.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * With the app lock on, a notification that shows amounts stays off the lock screen, and shows in full
 * once the phone is unlocked (decision D5). A public version without the amounts wouldn't be enough:
 * Android only shows it when the user has hidden sensitive content on the lock screen, and by default
 * they haven't. With the app lock off, the system default applies.
 */
internal fun NotificationCompat.Builder.keepOffLockScreenIf(appLockOn: Boolean): NotificationCompat.Builder =
    setVisibility(if (appLockOn) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)

/** The notifications that show amounts. */
private val TAGS_WITH_AMOUNTS = setOf(BudgetNotifier.NOTIFICATION_TAG, PlannedTransactionNotifier.NOTIFICATION_TAG)

/**
 * The app lock was just turned on: notifications with amounts that are already showing, posted while it
 * was off, move off the lock screen too. They are posted again unchanged apart from that, without sound.
 */
internal fun keepShownAmountsOffLockScreen(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.activeNotifications
        .filter { it.tag in TAGS_WITH_AMOUNTS && it.notification.visibility != Notification.VISIBILITY_SECRET }
        .forEach { shown ->
            val hidden = Notification.Builder.recoverBuilder(context, shown.notification)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setOnlyAlertOnce(true)
                .build()
            manager.notify(shown.tag, shown.id, hidden)
        }
}
