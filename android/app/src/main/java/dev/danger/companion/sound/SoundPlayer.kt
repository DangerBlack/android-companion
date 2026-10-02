package dev.danger.companion.sound

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

object SoundPlayer {

    private const val TAG = "SoundPlayer"

    fun play(context: Context, sound: String) {
        val mode = sound.trim().lowercase()
        if (mode.isEmpty() || mode == "none") return

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return
            AudioManager.RINGER_MODE_VIBRATE -> {
                vibrate(context)
                return
            }
        }

        if (isDoNotDisturbActive(context)) {
            vibrate(context)
            return
        }

        when (mode) {
            "soft" -> playSoft(context)
            "alert" -> {
                playAlert(context)
                vibrate(context)
            }
            else -> playSoft(context)
        }
    }

    private fun isDoNotDisturbActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> false
            else -> true
        }
    }

    private fun playSoft(context: Context) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            tone.release()
        } catch (e: Exception) {
            Log.w(TAG, "soft tone failed", e)
        }
    }

    private fun playAlert(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (e: Exception) {
            Log.w(TAG, "alert ringtone failed", e)
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "vibration failed", e)
        }
    }
}
