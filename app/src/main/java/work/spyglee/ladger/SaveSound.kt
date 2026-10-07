package work.spyglee.ladger

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * The "ka-ching" after adding a transaction, as on iOS. Like an iOS system sound it stays quiet
 * when the phone is on silent or vibrate.
 */
class SaveSound(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val soundId = soundPool.load(context, R.raw.ka_ching, 1)

    fun play() {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
