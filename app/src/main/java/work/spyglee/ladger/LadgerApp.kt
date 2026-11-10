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
        // A force stop clears the alarms; scheduling again replaces them if they still exist.
        container.applicationScope.launch {
            if (container.settings.isReminderOn.first()) container.reminder.schedule()
        }
        container.applicationScope.launch { container.plannedTransactions.reschedule() }
        container.applicationScope.launch { container.receiptFiles.sweepOrphans() }
    }
}
