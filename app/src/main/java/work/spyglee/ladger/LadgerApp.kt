package work.spyglee.ladger

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LadgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch { container.seeder.seedIfNeeded() }
        // A force stop clears the reminder alarm; scheduling again replaces it if it still exists.
        container.applicationScope.launch {
            if (container.settings.isReminderOn.first()) container.reminder.schedule()
        }
    }
}
