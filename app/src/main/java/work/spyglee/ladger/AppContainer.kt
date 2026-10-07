package work.spyglee.ladger

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import work.spyglee.ladger.data.CollectionRepository
import work.spyglee.ladger.data.CsvDocuments
import work.spyglee.ladger.data.CsvRepository
import work.spyglee.ladger.data.DefaultDataSeeder
import work.spyglee.ladger.data.LadgerDatabase
import work.spyglee.ladger.data.MerchantRepository
import work.spyglee.ladger.data.SettingsRepository
import work.spyglee.ladger.data.settingsDataStore
import work.spyglee.ladger.lock.AppLock
import work.spyglee.ladger.reminder.DailyReminder
import work.spyglee.ladger.scan.ReceiptScanner

/** App-wide dependencies, created once in [LadgerApp]. */
class AppContainer(context: Context, val database: LadgerDatabase = LadgerDatabase.build(context)) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context.settingsDataStore)
    val merchants = MerchantRepository(database.merchantCategoryDao())
    val collections = CollectionRepository(database)
    val csv = CsvRepository(database)
    val csvDocuments = CsvDocuments(context.contentResolver)
    val seeder = DefaultDataSeeder(database, settings, context::getString)
    val reminder = DailyReminder(context)
    val appLock = AppLock()
    val receipts = ReceiptScanner(context)
    val saveSound = SaveSound(context)
}
