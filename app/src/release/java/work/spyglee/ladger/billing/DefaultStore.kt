package work.spyglee.ladger.billing

import android.content.Context
import work.spyglee.ladger.BuildConfig

/** Release builds always use Google Play. */
fun defaultStore(context: Context): Store = PlayStore(context, BuildConfig.PLAY_LICENSE_KEY)
