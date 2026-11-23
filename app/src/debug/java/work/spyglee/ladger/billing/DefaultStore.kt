package work.spyglee.ladger.billing

import android.content.Context
import work.spyglee.ladger.BuildConfig

/**
 * Debug builds: the stand-in store, or Google Play when built with `-Pledger.playBilling=true` (license
 * testers), which then accepts purchases without the licence key until it's set.
 */
fun defaultStore(context: Context): Store =
    if (BuildConfig.PLAY_BILLING) {
        PlayStore(context, BuildConfig.PLAY_LICENSE_KEY, acceptWithoutKey = true)
    } else {
        FakeStore.persistent(context)
    }
