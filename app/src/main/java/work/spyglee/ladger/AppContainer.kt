package work.spyglee.ladger

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import work.spyglee.ladger.data.BudgetRepository
import work.spyglee.ladger.data.CollectionRepository
import work.spyglee.ladger.data.CsvDocuments
import work.spyglee.ladger.data.CsvRepository
import work.spyglee.ladger.data.DefaultDataSeeder
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.MerchantRepository
import work.spyglee.ladger.data.PlannedTransactionRepository
import work.spyglee.ladger.data.ReceiptRepository
import work.spyglee.ladger.data.SettingsRepository
import work.spyglee.ladger.data.settingsDataStore
import work.spyglee.ladger.lock.AppLock
import work.spyglee.ladger.reminder.BudgetNotifier
import work.spyglee.ladger.reminder.DailyReminder
import work.spyglee.ladger.reminder.PlannedTransactionNotifier
import work.spyglee.ladger.scan.ReceiptScanner
import work.spyglee.ladger.widget.WidgetData

/** App-wide dependencies, created once in [LadgerApp]. Tests can pass their own [database] and [settingsStore]. */
class AppContainer(
    context: Context,
    val database: LadgerDatabase = LadgerDatabase.build(context),
    settingsStore: DataStore<Preferences> = context.settingsDataStore,
) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(settingsStore)
    val merchants = MerchantRepository(database.merchantCategoryDao())
    val collections = CollectionRepository(database)
    val csv = CsvRepository(database)
    val csvDocuments = CsvDocuments(context.contentResolver)
    val seeder = DefaultDataSeeder(database, settings, context::getString)
    val reminder = DailyReminder(context)
    val plannedNotifier = PlannedTransactionNotifier(context) { settings.isAppLockOn.first() }
    val budgets = BudgetRepository(database, BudgetNotifier(context) { settings.isAppLockOn.first() })
    val plannedTransactions = PlannedTransactionRepository(database, plannedNotifier, onRecorded = budgets::check)
    val receiptFiles = ReceiptRepository(database, File(context.filesDir, "receipts"))
    val appLock = AppLock()
    val receipts = ReceiptScanner(context)
    val saveSound = SaveSound(context)
    val widgetData = WidgetData(database, settings) { context.getString(R.string.uncategorized) }
}
