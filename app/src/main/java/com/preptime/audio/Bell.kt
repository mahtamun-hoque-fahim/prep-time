package com.preptime.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import com.preptime.timer.BellPort

/** Room-audible sounds on the alarm stream, so they play even when the phone is on silent. */
class Bell(context: Context) : BellPort {
    private val am = context.getSystemService(AudioManager::class.java)

    // shortcut: synthetic tone, swap for a bundled bell asset if it sounds wrong
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()

    private val loop = (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        ?.let { RingtoneManager.getRingtone(context, it) }
        ?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
        }

    private var savedVolume = -1

    override fun ding() {
        tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
    }

    override fun startLoop() {
        loop?.play()
    }

    override fun stopLoop() {
        loop?.stop()
    }

    fun raiseVolume() {
        try {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: SecurityException) {
            // Do Not Disturb can block volume changes; the bell still plays at the user's level.
        }
    }

    fun restoreVolume() {
        if (savedVolume < 0) return
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
        } catch (_: SecurityException) {
        }
    }

    fun release() {
        loop?.stop()
        tone?.release()
    }
}
