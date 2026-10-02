package dev.danger.companion.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.danger.companion.CompanionState
import dev.danger.companion.Emotion
import dev.danger.companion.sound.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CompanionPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val emotion = Emotion.fromString(intent.getStringExtra(EXTRA_EMOTION))
                val text = intent.getStringExtra(EXTRA_TEXT)
                val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: "default"
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Companion"
                val color = CompanionState.parseColor(intent.getStringExtra(EXTRA_COLOR))
                val sound = intent.getStringExtra(EXTRA_SOUND) ?: "none"
                val ttlMs = if (intent.hasExtra(EXTRA_TTL_MS)) {
                    intent.getLongExtra(EXTRA_TTL_MS, 0L)
                } else {
                    null
                }
                CompanionController.applyEmotion(
                    context = appContext,
                    emotion = emotion,
                    text = text,
                    instanceId = instanceId,
                    instanceLabel = label,
                    color = color,
                    ttlMs = ttlMs,
                )
                SoundPlayer.play(appContext, sound, emotion)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "dev.danger.companion.PUSH"
        const val EXTRA_EMOTION = "emotion"
        const val EXTRA_TEXT = "text"
        const val EXTRA_INSTANCE_ID = "instance_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_COLOR = "color"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_TTL_MS = "ttl_ms"
    }
}
