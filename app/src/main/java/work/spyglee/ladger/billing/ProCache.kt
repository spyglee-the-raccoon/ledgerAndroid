package work.spyglee.ladger.billing

import android.content.Context
import androidx.core.content.edit

/**
 * The last known Pro status, so the statistics don't look locked while the store is asked again
 * (decision D1; iOS starts locked on every launch). It lives in its own preferences file, which the
 * backup rules leave out: a phone restored from a backup asks Google Play afresh.
 */
interface ProCache {
    /** The product that made the user Pro, or null. */
    fun load(): String?

    fun save(activeProduct: String?)
}

class PreferencesProCache(context: Context) : ProCache {
    private val preferences = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    override fun load(): String? = preferences.getString(ACTIVE_PRODUCT, null)

    override fun save(activeProduct: String?) = preferences.edit { putString(ACTIVE_PRODUCT, activeProduct) }

    private companion object {
        const val ACTIVE_PRODUCT = "activeProduct"
    }
}

class MemoryProCache(private var activeProduct: String? = null) : ProCache {
    override fun load(): String? = activeProduct

    override fun save(activeProduct: String?) {
        this.activeProduct = activeProduct
    }
}
