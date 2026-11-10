package work.spyglee.ladger

import android.content.Intent

/**
 * The shortcuts offered when the app icon is long-pressed (`res/xml/shortcuts.xml`), as the iOS
 * quick actions since `8a76b5d`. They open the add transaction screen, Scan with the camera
 * straight away. Like iOS, they wait until the app is unlocked.
 */
enum class QuickAction(val action: String) {
    ADD_TRANSACTION("work.spyglee.ladger.action.ADD_TRANSACTION"),
    SCAN("work.spyglee.ladger.action.SCAN");

    companion object {
        fun from(intent: Intent?): QuickAction? = entries.firstOrNull { it.action == intent?.action }
    }
}
