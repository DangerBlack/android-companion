package dev.danger.companion.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import dev.danger.companion.CompanionState
import dev.danger.companion.Emotion
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A transient animation pose layered on top of an emotion expression.
 *
 * [REST] is the identity: rendering with it must reproduce the static frame
 * exactly (no lid squash, no gaze drift). The event burst posts a handful of
 * these, e.g. a blink (lid 1 -> ~0.1 -> 1) plus a small gaze settle.
 */
data class BloubAnim(
    /** 1 = eye fully open, ~0.06 = closed. A screen-space vertical squash. */
    val lid: Double = 1.0,
    val dYaw: Double = 0.0,
    val dPitch: Double = 0.0,
    val dRoll: Double = 0.0,
) {
    companion object {
        val REST = BloubAnim()
    }
}

/**
 * Face geometry ported from jeremy-prt/bloub (MIT, (c) Jeremy Perret) — https://github.com/jeremy-prt/bloub
 *
 * The body is a filled circle. The eyes are HOLES punched through it with a
 * PorterDuff CLEAR xfermode, never white shapes on top. Eye positions, sizes and
 * lean are computed on a unit sphere (`eyePoses`) so the perspective compression
 * and the ~26 degree head lean fall out of the math instead of being hardcoded.
 */
object BloubRenderer {

    /* ---- constants ported from face.ts / repere.ts ---------------------- */

    /** Half gap between the eyes on the sphere, degrees (total separation ~31deg). */
    private const val EYE_SPLIT = 15.46

    /** Rest eye size, in ball-radius units. */
    private const val EYE_W = 0.186
    private const val EYE_H = 0.412

    /** Rest head orientation, fitted on the reference frames. */
    private val REST_GAZE = HeadGaze(yaw = 28.49, pitch = 28.62, roll = -13.0)

    /**
     * Body radius as a fraction of the smaller widget side.
     *
     * Derived from bloub's viewBox (`repere.ts`): half-side `DEMI_VIEWBOX = 158`
     * and ball radius `RAYON = 100`, i.e. the ball spans 100/316 ≈ 0.3165 of the
     * full viewBox, leaving a large margin for the orbit rings. The widget has no
     * rings, so we crop tight to the ball itself: a radius of 0.44 * min(w,h)
     * (diameter 0.88) keeps a ~6% breathing margin and nothing clips.
     */
    private const val BALL = 0.44f

    /** Below this normal-z an eye is considered hidden behind the sphere. */
    private const val DEPTH_CUTOFF = 0.02

    /* ---- internal expression model -------------------------------------- */

    private data class Vec3(val x: Double, val y: Double, val z: Double)

    private data class HeadGaze(val yaw: Double, val pitch: Double, val roll: Double)

    private data class EyeCfg(val w: Double, val h: Double, val tilt: Double = 0.0, val open: Double = 1.0)

    private data class EyePose(
        val x: Double,
        val y: Double,
        /** Tangent basis, SVG matrix(a,b,c,d,..) column vectors [a,b] and [c,d]. */
        val a: Double,
        val b: Double,
        val c: Double,
        val d: Double,
        /** Normal z component: > 0 means the eye faces the viewer. */
        val depth: Double,
    )

    private data class Expression(
        val gaze: HeadGaze,
        val split: Double,
        val eyes: List<EyeCfg>,
    )

    private fun eye(w: Double, h: Double, tilt: Double = 0.0, open: Double = 1.0) =
        EyeCfg(w, h, tilt, open)

    /** Both eyes identical; `tilt` becomes a mirrored pair when non-zero. */
    private fun pair(w: Double, h: Double, tilt: Double = 0.0, open: Double = 1.0) = listOf(
        eye(w, h, tilt, open),
        eye(w, h, -tilt, open),
    )

    private fun expressionFor(emotion: Emotion): Expression = when (emotion) {
        Emotion.NEUTRAL -> Expression(
            gaze = REST_GAZE,
            split = EYE_SPLIT,
            eyes = listOf(eye(EYE_W, EYE_H), eye(EYE_W, EYE_H)),
        )
        Emotion.HAPPY -> Expression(
            // bloub `hilare`: bigger, more joyful squint than `heureux`.
            gaze = HeadGaze(4.0, 14.0, 0.0),
            split = 18.0,
            eyes = pair(0.34, 0.13, 20.0),
        )
        Emotion.THINKING -> Expression(
            gaze = HeadGaze(16.0, -9.0, -15.0),
            split = 16.5,
            eyes = listOf(eye(0.24, 0.46, -8.0), eye(0.2, 0.38, -8.0)),
        )
        Emotion.ERROR -> Expression(
            // bloub `colere`: angry, mirror-tilted eye tops converging inward.
            gaze = HeadGaze(3.0, 7.0, 0.0),
            split = 17.0,
            eyes = pair(0.34, 0.15, 30.0),
        )
        Emotion.SLEEPY -> Expression(
            gaze = HeadGaze(6.0, -9.0, -3.0),
            split = 16.0,
            eyes = pair(0.2, 0.42, 0.0, 0.42),
        )
        Emotion.LISTENING -> Expression(
            gaze = HeadGaze(4.0, 5.0, -4.0),
            split = 16.0,
            eyes = pair(0.21, 0.44),
        )
    }

    /* ---- sphere math (ported from face.ts) ------------------------------ */

    private fun deg(d: Double): Double = d * Math.PI / 180.0

    /** Rotates two orthonormal basis vectors in their common plane. */
    private fun spin(u: Vec3, v: Vec3, angle: Double): Pair<Vec3, Vec3> {
        val c = cos(angle)
        val s = sin(angle)
        return Vec3(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s) to
            Vec3(v.x * c - u.x * s, v.y * c - u.y * s, v.z * c - u.z * s)
    }

    /**
     * Head frame then both eyes. Screen frame: x right, y down, z toward viewer.
     * Index 0 is the inner eye, index 1 the outer eye.
     */
    private fun eyePoses(gaze: HeadGaze, scale: Double, split: Double): List<EyePose> {
        var f = Vec3(0.0, 0.0, 1.0)
        var right = Vec3(1.0, 0.0, 0.0)
        var down = Vec3(0.0, 1.0, 0.0)

        // yaw: forward tilts toward right
        spin(f, right, deg(gaze.yaw)).let { f = it.first; right = it.second }
        // pitch: forward tilts toward up (so away from down)
        spin(down, f, deg(gaze.pitch)).let { down = it.first; f = it.second }
        // roll: head leans in its own plane
        spin(right, down, deg(gaze.roll)).let { right = it.first; down = it.second }

        fun build(side: Double): EyePose {
            val (ef, er) = spin(f, right, deg(split * side))
            return EyePose(
                x = ef.x * scale,
                y = ef.y * scale,
                a = er.x,
                b = er.y,
                c = down.x,
                d = down.y,
                depth = ef.z,
            )
        }

        return listOf(build(-1.0), build(1.0))
    }

    /* ---- event burst animation (ported from face.ts / math.ts) ---------- */

    private fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

    /**
     * Blink squash in screen space, from bloub's `blinkScale`: a lid of 1 keeps
     * the eye open, 0 leaves a 0.06 sliver. Unlike an expression's `open`, this
     * only scales the vertical outputs, so the eye width is preserved.
     */
    private fun blinkScale(lid: Double): Double = 0.06 + 0.94 * clamp01(lid)

    /** Seamless 1D noise, ported from bloub `math.ts` `loopNoise`. */
    private fun loopNoise(t: Double, period: Double, seed: Double): Double {
        val p = t / period * (2.0 * Math.PI)
        return 0.55 * sin(p + seed) +
            0.3 * sin(2.0 * p + seed * 1.7 + 1.1) +
            0.15 * sin(3.0 * p + seed * 2.3 + 2.4)
    }

    /**
     * Small resting gaze wander adapted from bloub's `liveliness()` for a single
     * short burst: the lid stays open and only the head drifts, at a fraction
     * (`wander`) of the original amplitude.
     */
    fun liveliness(t: Double, wander: Double = 0.35): BloubAnim = BloubAnim(
        lid = 1.0,
        dYaw = (loopNoise(t, 11.3, 0.4) * 5.5 + loopNoise(t, 3.7, 2.1) * 1.6) * wander,
        dPitch = (loopNoise(t, 9.1, 1.3) * 4.2 + loopNoise(t, 4.3, 0.7) * 1.3) * wander,
        dRoll = loopNoise(t, 13.7, 3.2) * 2.2 * wander,
    )

    /**
     * Frames for one blink burst: fast close, short hold, slower reopen. The last
     * frame is [BloubAnim.REST] so the widget always settles on the exact static
     * pose. Callers own the spacing between frames.
     */
    fun blinkBurst(stepSeconds: Double = 0.1): List<BloubAnim> {
        val lids = listOf(1.0, 0.45, 0.10, 0.10, 0.50, 0.85, 1.0)
        return lids.mapIndexed { i, lid ->
            if (i == lids.lastIndex) BloubAnim.REST else liveliness(i * stepSeconds).copy(lid = lid)
        }
    }

    /* ---- rendering ------------------------------------------------------ */

    /**
     * Renders a transparent bitmap: a filled circle in the instance colour with
     * two eye holes cleared out of it. The widget background (opaque/transparent)
     * is handled by FaceWidget, so nothing is painted behind the body.
     *
     * [anim] defaults to [BloubAnim.REST], which reproduces the static render
     * bit-for-bit; the event burst passes explicit frames instead.
     */
    fun render(
        state: CompanionState,
        widthPx: Int,
        heightPx: Int,
        anim: BloubAnim = BloubAnim.REST,
    ): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = width.toFloat()
        val h = height.toFloat()
        val ballRadius = min(w, h) * BALL
        val cx = w / 2f
        val cy = h / 2f

        // Body: a perfect filled circle at the centre.
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = state.colorArgb
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, ballRadius, bodyPaint)

        // Eyes: holes punched out of the body. CLEAR ignores colour and leaves
        // truly transparent pixels, so the transparent widget background shows.
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        val expression = expressionFor(state.emotion)
        // Gaze drift is a REST-zero offset, so the static frame is unchanged.
        val gaze = HeadGaze(
            yaw = expression.gaze.yaw + anim.dYaw,
            pitch = expression.gaze.pitch + anim.dPitch,
            roll = expression.gaze.roll + anim.dRoll,
        )
        // scale = 1.0 -> pose.x/y are already in ball-radius units.
        val poses = eyePoses(gaze, 1.0, expression.split)

        for (i in 0 until 2) {
            val pose = poses[i]
            if (pose.depth <= DEPTH_CUTOFF) continue
            val cfg = expression.eyes[i]

            // Eye's own tilt, composed AFTER the sphere tangent basis. This is
            // what allows mirrored tilts (anger/sadness); the head roll alone
            // would lean both eyes the same way.
            val phi = deg(cfg.tilt)
            val cp = cos(phi)
            val sp = sin(phi)
            val ax = pose.a * cp + pose.c * sp
            val ay = pose.b * cp + pose.d * sp
            val bx = -pose.a * sp + pose.c * cp
            val by = -pose.b * sp + pose.d * cp

            // Blink/open is a vertical squash in SCREEN space, applied after the
            // tangent basis. REST keeps the expression's own `open` exactly; a
            // burst additionally applies bloub's blinkScale to the smaller lid.
            val k = if (anim.lid >= 1.0) cfg.open else blinkScale(min(anim.lid, cfg.open))

            val matrix = Matrix()
            matrix.setValues(
                floatArrayOf(
                    ax.toFloat(), bx.toFloat(), (cx + pose.x * ballRadius).toFloat(),
                    (ay * k).toFloat(), (by * k).toFloat(), (cy + pose.y * ballRadius).toFloat(),
                    0f, 0f, 1f,
                ),
            )

            // Stadium/capsule centred on the origin, in ball-radius units; the
            // matrix rotates/tilts/translates it. Corner radius = half the short
            // side makes the exact bloub eye shape.
            val halfW = (cfg.w * ballRadius / 2.0).toFloat()
            val halfH = (cfg.h * ballRadius / 2.0).toFloat()
            val radius = min(halfW, halfH)
            val rect = RectF(-halfW, -halfH, halfW, halfH)

            canvas.save()
            canvas.concat(matrix)
            canvas.drawRoundRect(rect, radius, radius, clearPaint)
            canvas.restore()
        }

        clearPaint.xfermode = null
        return bitmap
    }
}
