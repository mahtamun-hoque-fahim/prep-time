package com.preptime.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import com.preptime.timer.BellPort

/** Room-audible sounds on the alarm stream, so they play even when the phone is on silent. */
class Bell(private val context: Context) : BellPort {
    private val am = context.getSystemService(AudioManager::class.java)

    // shortcut: synthetic tone, swap for a bundled bell asset if it sounds wrong
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()

    private var loop: Ringtone? = null

    override fun ding() {
        tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
    }

    override fun startLoop() {
        loop?.stop()
        // The user's own sound can be "None"; fall back until one is set. Fresh Ringtone each time: stop() frees it.
        val uri = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .firstNotNullOfOrNull { RingtoneManager.getActualDefaultRingtoneUri(context, it) }
            ?: return
        loop = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    override fun stopLoop() {
        loop?.stop()
        loop = null
    }

    /** Saves the user's level to disk first, so it can be put back even if the process dies. */
    fun raiseVolume() {
        val prefs = prefs(context)
        try {
            if (!prefs.contains(KEY)) prefs.edit().putInt(KEY, am.getStreamVolume(AudioManager.STREAM_ALARM)).commit()
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: SecurityException) {
            // Do Not Disturb can block volume changes; the bell still plays at the user's level.
        }
    }

    fun restoreVolume() = restoreSavedVolume(context)

    fun release() {
        stopLoop()
        tone?.release()
    }

    companion object {
        private const val KEY = "alarm_volume"
        private fun prefs(c: Context) = c.getSharedPreferences("bell", Context.MODE_PRIVATE)

        /** Puts back a level left raised by a timer that never closed (process killed). */
        fun restoreSavedVolume(context: Context) {
            val prefs = prefs(context)
            if (!prefs.contains(KEY)) return
            try {
                context.getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, prefs.getInt(KEY, 0), 0)
            } catch (_: SecurityException) {
            }
            prefs.edit().remove(KEY).apply()
        }
    }
}
