package dev.danger.companion.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import dev.danger.companion.Emotion
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin

object CreatureSound {

    private const val SAMPLE_RATE = 44100
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null

    private enum class Wave { SINE, SQUARE, TRIANGLE }

    private data class Tone(
        val startHz: Double,
        val endHz: Double = startHz,
        val ms: Int,
        val gapMs: Int = 0,
        val gain: Double = 0.7,
        val vibHz: Double = 0.0,
        val vibDepth: Double = 0.0,
        val wave: Wave = Wave.SINE,
    )

    fun play(context: Context, emotion: Emotion, loud: Boolean) {
        val usage = if (loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        val pcm = render(contour(emotion, loud))
        if (pcm.isEmpty()) return
        playPcm(usage, pcm)
    }

    private fun contour(emotion: Emotion, loud: Boolean): List<Tone> {
        val g = if (loud) 0.85 else 0.5
        return when (emotion) {
            Emotion.HAPPY -> listOf(
                Tone(620.0, 780.0, 90, 0, g),
                Tone(880.0, 990.0, 110, 40, g),
            )
            Emotion.THINKING -> listOf(
                Tone(520.0, 560.0, 240, 0, g * 0.9, 11.0, 40.0, Wave.TRIANGLE),
            )
            Emotion.ERROR -> listOf(
                Tone(560.0, 240.0, 280, 0, g, 0.0, 0.0, Wave.SQUARE),
            )
            Emotion.SLEEPY -> listOf(
                Tone(340.0, 250.0, 380, 0, g * 0.7, 6.0, 20.0),
            )
            Emotion.LISTENING -> listOf(
                Tone(700.0, 700.0, 70, 50, g),
                Tone(830.0, 830.0, 90, 0, g),
            )
            Emotion.NEUTRAL -> listOf(
                Tone(660.0, 790.0, 100, 0, g),
            )
        }
    }

    private fun render(tones: List<Tone>): ShortArray {
        val totalMs = tones.sumOf { it.ms + it.gapMs }
        if (totalMs <= 0) return ShortArray(0)
        val out = ShortArray(totalMs * SAMPLE_RATE / 1000)
        var index = 0
        for (tone in tones) {
            val count = tone.ms * SAMPLE_RATE / 1000
            var phase = 0.0
            for (i in 0 until count) {
                val frac = i.toDouble() / count
                val freq = tone.startHz + (tone.endHz - tone.startHz) * frac
                val vibrato = if (tone.vibDepth > 0.0) {
                    sin(2 * PI * tone.vibHz * i / SAMPLE_RATE) * tone.vibDepth
                } else {
                    0.0
                }
                phase += 2 * PI * (freq + vibrato) / SAMPLE_RATE
                val raw = when (tone.wave) {
                    Wave.SINE -> sin(phase)
                    Wave.SQUARE -> if (sin(phase) >= 0.0) 1.0 else -1.0
                    Wave.TRIANGLE -> 2.0 / PI * asin(sin(phase).coerceIn(-1.0, 1.0))
                }
                val value = raw * envelope(i, count) * tone.gain
                if (index < out.size) {
                    out[index++] = (value.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                }
            }
            index += tone.gapMs * SAMPLE_RATE / 1000
        }
        return out
    }

    private fun envelope(i: Int, count: Int): Double {
        val attack = (SAMPLE_RATE * 0.005).toInt().coerceAtLeast(1)
        val release = count * 0.35
        val a = if (i < attack) i.toDouble() / attack else 1.0
        val r = if (i > count - release) (count - i) / release else 1.0
        return a * r
    }

    private fun playPcm(usage: Int, pcm: ShortArray) {
        stop()
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val player = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            player.write(pcm, 0, pcm.size)
            player.play()
            track = player
            val durationMs = pcm.size * 1000L / SAMPLE_RATE + 150
            handler.postDelayed({ if (track === player) stop() }, durationMs)
        } catch (_: Exception) {
            stop()
        }
    }

    private fun stop() {
        val current = track
        track = null
        if (current != null) {
            try {
                current.stop()
            } catch (_: Exception) {
            }
            try {
                current.release()
            } catch (_: Exception) {
            }
        }
    }
}
