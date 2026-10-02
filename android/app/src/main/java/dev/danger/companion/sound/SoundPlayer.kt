package dev.danger.companion.sound

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import dev.danger.companion.Emotion

object SoundPlayer {

    private const val TAG = "SoundPlayer"

    fun play(context: Context, sound: String, emotion: Emotion = Emotion.NEUTRAL) {
        val mode = sound.trim().lowercase()
        if (mode.isEmpty() || mode == "none") return

        if (isDoNotDisturbActive(context)) {
            vibrate(context)
            return
        }

        when (mode) {
            "alert" -> {
                CreatureSound.play(context, emotion, loud = true)
                vibrate(context)
            }
            else -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                    vibrate(context)
                } else {
                    CreatureSound.play(context, emotion, loud = false)
                }
            }
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
