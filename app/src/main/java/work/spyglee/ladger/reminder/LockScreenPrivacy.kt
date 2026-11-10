package work.spyglee.ladger.reminder

import androidx.core.app.NotificationCompat

/**
 * With the app lock on, a notification that shows amounts stays off the lock screen, and shows in full
 * once the phone is unlocked (decision D5). A public version without the amounts wouldn't be enough:
 * Android only shows it when the user has hidden sensitive content on the lock screen, and by default
 * they haven't. With the app lock off, the system default applies.
 */
internal fun NotificationCompat.Builder.keepOffLockScreenIf(appLockOn: Boolean): NotificationCompat.Builder =
    setVisibility(if (appLockOn) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
