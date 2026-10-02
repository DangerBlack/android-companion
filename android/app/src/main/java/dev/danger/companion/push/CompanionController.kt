package dev.danger.companion.push

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import dev.danger.companion.CompanionState
import dev.danger.companion.CompanionStore
import dev.danger.companion.Emotion
import dev.danger.companion.face.BloubAnim
import dev.danger.companion.face.BloubRenderer
import dev.danger.companion.face.FaceAnimBus
import dev.danger.companion.widget.FaceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CompanionController {

    private const val TAG = "CompanionController"

    /** Spacing between burst frames: 7 frames * 100 ms = ~600 ms total. */
    private const val BURST_STEP_MS = 100L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var burst: Job? = null

    suspend fun apply(context: Context, state: CompanionState) {
        burst?.cancel()
        FaceAnimBus.current = BloubAnim.REST
        CompanionStore.save(context, state)
        FaceWidget().updateAll(context)
        launchBurst(context.applicationContext)
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

    /*
     * Posts one blink burst, a frame at a time. Fire-and-forget: no persistent
     * loop and no AlarmManager, so the cost is paid only when an event arrives.
     * An in-flight burst is cancelled first, so overlapping events cannot
     * interleave their frames.
     */
    private fun launchBurst(context: Context) {
        burst?.cancel()
        burst = scope.launch {
            val frames = BloubRenderer.blinkBurst(BURST_STEP_MS / 1000.0)
            for (i in frames.indices) {
                FaceAnimBus.current = frames[i]
                FaceWidget().updateAll(context)
                Log.d(TAG, "burst frame $i/${frames.lastIndex} lid=${frames[i].lid}")
                if (i != frames.lastIndex) delay(BURST_STEP_MS)
            }
        }
    }
}
