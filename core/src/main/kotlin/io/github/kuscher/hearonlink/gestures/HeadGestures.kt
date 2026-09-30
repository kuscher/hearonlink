package io.github.kuscher.hearonlink.gestures

import kotlin.math.abs

enum class Gesture { NOD, SHAKE }

enum class Sensitivity(val threshold: Float) { GENTLE(0.55f), NORMAL(1f), FIRM(1.6f) }

/** One head-motion sample: [vertical] tracks nodding (pitch), [horizontal] tracks shaking (yaw). */
data class HeadSample(val timeMs: Long, val vertical: Float, val horizontal: Float)

/**
 * Nod / shake detector. Our own design:
 *
 * 1. Each axis is high-passed (the slow drift of how you hold your head is removed).
 * 2. A swing counts when an axis crosses +T after −T or the other way round (hysteresis), so
 *    jitter around zero never counts.
 * 3. A nod is at least [minSwings] swings on the vertical axis within [windowMs], while that axis
 *    carries clearly more motion than the other ([dominance]); a shake is the same sideways.
 * 4. After a gesture the detector rests for [cooldownMs] and forgets the old swings.
 *
 * T = [baseThreshold] × sensitivity, in the AirPods' raw sensor units. Calibration sets it to about
 * half of a typical nod ([Calibration.Result.scale]); 600 is the uncalibrated default.
 */
class HeadGestureDetector(
    var sensitivity: Sensitivity = Sensitivity.NORMAL,
    var baseThreshold: Float = 600f,
    private val minSwings: Int = 2,
    private val windowMs: Long = 1600,
    private val dominance: Float = 1.8f,
    private val cooldownMs: Long = 900,
    private val driftMs: Long = 900,
) {
    private inner class Axis {
        var mean = Float.NaN
        var sign = 0
        val swings = ArrayDeque<Long>()
        var energy = 0f

        fun feed(t: Long, x: Float, dt: Float): Float {
            if (mean.isNaN()) mean = x
            val alpha = (dt / driftMs).coerceIn(0f, 1f)
            mean += alpha * (x - mean)
            val v = x - mean
            energy += (abs(v) - energy) * (dt / 400f).coerceIn(0f, 1f)
            val th = baseThreshold * sensitivity.threshold
            val s = if (v > th) 1 else if (v < -th) -1 else 0
            if (s != 0 && s != sign) {
                if (sign != 0) swings.addLast(t)
                sign = s
            }
            while (swings.isNotEmpty() && t - swings.first() > windowMs) swings.removeFirst()
            return v
        }

        fun reset() { swings.clear(); sign = 0 }
    }

    private val vertical = Axis()
    private val horizontal = Axis()
    private var lastT = -1L
    private var restUntil = Long.MIN_VALUE

    /** Filtered values of the last sample (for drawing the live traces). */
    var lastVertical = 0f; private set
    var lastHorizontal = 0f; private set

    fun reset() { vertical.reset(); horizontal.reset(); vertical.mean = Float.NaN; horizontal.mean = Float.NaN; lastT = -1 }

    fun feed(s: HeadSample): Gesture? {
        val dt = if (lastT < 0) 40f else (s.timeMs - lastT).coerceIn(1, 500).toFloat()
        lastT = s.timeMs
        lastVertical = vertical.feed(s.timeMs, s.vertical, dt)
        lastHorizontal = horizontal.feed(s.timeMs, s.horizontal, dt)
        if (s.timeMs < restUntil) { vertical.reset(); horizontal.reset(); return null }

        val g = when {
            vertical.swings.size >= minSwings && vertical.energy > dominance * horizontal.energy -> Gesture.NOD
            horizontal.swings.size >= minSwings && horizontal.energy > dominance * vertical.energy -> Gesture.SHAKE
            else -> null
        }
        if (g != null) {
            restUntil = s.timeMs + cooldownMs
            vertical.reset(); horizontal.reset()
        }
        return g
    }
}
