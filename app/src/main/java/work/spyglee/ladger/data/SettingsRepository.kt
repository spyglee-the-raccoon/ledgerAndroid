package work.spyglee.ladger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val isReminderOn: Flow<Boolean> = dataStore.data.map { it[REMINDER_ON] ?: false }
    val isAppLockOn: Flow<Boolean> = dataStore.data.map { it[APP_LOCK_ON] ?: false }

    suspend fun setReminderOn(value: Boolean) {
        dataStore.edit { it[REMINDER_ON] = value }
    }

    suspend fun setAppLockOn(value: Boolean) {
        dataStore.edit { it[APP_LOCK_ON] = value }
    }

    /** Whether the app has asked once for exact alarms (Android only, D10); it doesn't ask again. */
    val didAskExactAlarms: Flow<Boolean> = dataStore.data.map { it[DID_ASK_EXACT_ALARMS] ?: false }

    suspend fun markAskedExactAlarms() {
        dataStore.edit { it[DID_ASK_EXACT_ALARMS] = true }
    }

    suspend fun didSeedDefaultData(): Boolean = dataStore.data.first()[DID_SEED_DEFAULT_DATA] ?: false

    suspend fun markDefaultDataSeeded() {
        dataStore.edit { it[DID_SEED_DEFAULT_DATA] = true }
    }

    private companion object {
        // Same keys as the iOS app's UserDefaults.
        val REMINDER_ON = booleanPreferencesKey("isReminderOn")
        val APP_LOCK_ON = booleanPreferencesKey("isBiometricsRequired")
        val DID_SEED_DEFAULT_DATA = booleanPreferencesKey("didSeedDefaultData")

        // Android only.
        val DID_ASK_EXACT_ALARMS = booleanPreferencesKey("didAskExactAlarms")
    }
}
