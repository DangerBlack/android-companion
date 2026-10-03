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
import dev.danger.companion.widget.TextScrollBus
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

    private const val DEFAULT_TTL_MS = 60_000L

    private const val PET_TTL_MS = 4_000L

    private val PET_EMOTIONS = listOf(Emotion.HAPPY, Emotion.LISTENING, Emotion.THINKING, Emotion.SLEEPY)

    private const val SCROLL_LINE_CHARS = 22
    private const val SCROLL_STEP_MS = 2_000L
    private const val SCROLL_LOOPS = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var burst: Job? = null

    @Volatile
    private var scrollJob: Job? = null

    suspend fun apply(context: Context, state: CompanionState) {
        burst?.cancel()
        FaceAnimBus.current = BloubAnim.REST
        CompanionStore.save(context, state)
        FaceWidget().updateAll(context)
        val ttl = state.ttlMs
        if (ttl != null) {
            CompanionWorker.scheduleExpiry(context.applicationContext, ttl)
        } else {
            CompanionWorker.cancelExpiry(context.applicationContext)
        }
        launchBurst(context.applicationContext)
        startScroll(context.applicationContext, state.text)
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
        val effectiveTtl = when {
            ttlMs == null -> DEFAULT_TTL_MS
            ttlMs > 0 -> ttlMs
            else -> null
        }
        val state = CompanionState(
            emotion = emotion,
            text = text,
            instanceId = instanceId,
            instanceLabel = instanceLabel,
            colorArgb = color,
            updatedAt = System.currentTimeMillis(),
            ttlMs = effectiveTtl,
        )
        apply(context, state)
        return state
    }

    suspend fun pet(context: Context) {
        val appContext = context.applicationContext
        scrollJob?.cancel()
        scrollJob = null
        TextScrollBus.page.value = null
        val current = CompanionStore.current(appContext)
        val reaction = PET_EMOTIONS.random()
        burst?.cancel()
        FaceAnimBus.current = BloubAnim.REST
        CompanionStore.save(
            appContext,
            current.copy(
                emotion = reaction,
                text = null,
                ttlMs = PET_TTL_MS,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        FaceWidget().updateAll(appContext)
        CompanionWorker.scheduleExpiry(appContext, PET_TTL_MS)
        launchBurst(appContext)
    }

    /*
     * Widgets cannot scroll natively, so a long message is shown one short window
     * at a time and advanced every SCROLL_STEP_MS, only while that exact message
     * is still the current state. Any new event replaces the job.
     */
    private fun startScroll(context: Context, text: String?) {
        scrollJob?.cancel()
        scrollJob = null
        if (text.isNullOrBlank() || text.length <= SCROLL_LINE_CHARS) {
            TextScrollBus.page.value = null
            return
        }
        val pages = paginate(text, SCROLL_LINE_CHARS)
        if (pages.size <= 1) {
            TextScrollBus.page.value = null
            return
        }
        scrollJob = scope.launch {
            for (loop in 0 until SCROLL_LOOPS) {
                for (page in pages) {
                    if (CompanionStore.current(context).text != text) {
                        TextScrollBus.page.value = null
                        return@launch
                    }
                    TextScrollBus.page.value = page
                    FaceWidget().updateAll(context)
                    delay(SCROLL_STEP_MS)
                }
            }
            TextScrollBus.page.value = null
            FaceWidget().updateAll(context)
        }
    }

    private fun paginate(text: String, maxChars: Int): List<String> {
        val pages = mutableListOf<String>()
        val builder = StringBuilder()
        for (word in text.trim().split(Regex("\\s+"))) {
            if (builder.isNotEmpty() && builder.length + 1 + word.length > maxChars) {
                pages += builder.toString()
                builder.clear()
            }
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(word)
        }
        if (builder.isNotEmpty()) pages += builder.toString()
        return pages
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
