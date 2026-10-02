package dev.danger.companion.push

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.danger.companion.CompanionState
import dev.danger.companion.CompanionStore
import dev.danger.companion.Emotion
import dev.danger.companion.widget.FaceWidget

object CompanionController {

    suspend fun apply(context: Context, state: CompanionState) {
        CompanionStore.save(context, state)
        FaceWidget().updateAll(context)
    }

    suspend fun applyJson(context: Context, json: String) {
        val state = CompanionState.fromJson(json) ?: return
        apply(context, state)
    }

    suspend fun applyEmotion(
        context: Context,
        emotion: Emotion,
        text: String? = null,
        instanceId: String = "default",
        instanceLabel: String = "Companion",
        color: Int = CompanionState.DEFAULT_COLOR,
        ttlMs: Long? = null,
    ): CompanionState {
        val state = CompanionState(
            emotion = emotion,
            text = text,
            instanceId = instanceId,
            instanceLabel = instanceLabel,
            colorArgb = color,
            updatedAt = System.currentTimeMillis(),
            ttlMs = ttlMs,
        )
        apply(context, state)
        return state
    }
}
