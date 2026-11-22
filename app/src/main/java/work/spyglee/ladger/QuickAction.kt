package work.spyglee.ladger

import android.content.Intent

/**
 * Ways into a screen from outside the app. The shortcuts offered when the app icon is long-pressed
 * (`res/xml/shortcuts.xml`), as the iOS quick actions since `8a76b5d`, open the add transaction screen,
 * Scan with the camera straight away. The widgets (D4) open the screens iOS's widget links do. Like
 * iOS, they wait until the app is unlocked.
 */
enum class QuickAction(val action: String) {
    ADD_TRANSACTION("work.spyglee.ladger.action.ADD_TRANSACTION"),
    SCAN("work.spyglee.ladger.action.SCAN"),

    /** The budget widget, as iOS's `ladger://budgeting`. */
    OPEN_BUDGETS("work.spyglee.ladger.action.OPEN_BUDGETS"),

    /** The transactions widget, as iOS's `ladger://sources`. */
    OPEN_ACCOUNTS("work.spyglee.ladger.action.OPEN_ACCOUNTS");

    companion object {
        fun from(intent: Intent?): QuickAction? = entries.firstOrNull { it.action == intent?.action }
    }
}
