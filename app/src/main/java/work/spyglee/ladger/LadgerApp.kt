package work.spyglee.ladger

import android.app.Application
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import work.spyglee.ladger.ui.PhoneLocale
import work.spyglee.ladger.widget.Widgets

class LadgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        updatePhoneLocale()
        container = AppContainer(this)
        container.applicationScope.launch { container.seeder.seedIfNeeded() }
        // A force stop clears the alarms; scheduling again replaces them if they still exist.
        container.applicationScope.launch {
            if (container.settings.isReminderOn.first()) container.reminder.schedule()
        }
        container.applicationScope.launch { container.plannedTransactions.reschedule() }
        container.applicationScope.launch { container.receiptFiles.sweepOrphans() }
        Widgets.watch(this, container.widgetData, container.applicationScope)
    }

    /** The phone's language, region or light/dark theme may have changed while the app was running. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePhoneLocale()
        container.applicationScope.launch { Widgets.updateAll(this@LadgerApp) }
    }

    /** Amounts keep the phone's region when the app has a language of its own (D8); see [PhoneLocale]. */
    private fun updatePhoneLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PhoneLocale.current = getSystemService(LocaleManager::class.java).systemLocales[0]
        }
    }
}
