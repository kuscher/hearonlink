package io.github.kuscher.hearonlink.gestures

import kotlin.math.abs

enum class Gesture { NOD, SHAKE }

enum class Sensitivity(val threshold: Float) { GENTLE(0.55f), NORMAL(1f), FIRM(1.6f) }

/** One head-motion sample: [vertical] tracks nodding (pitch), [horizontal] tracks shaking (yaw). */
data class HeadSample(val timeMs: Long, val vertical: Float, val horizontal: Float)

/**
 * Nod / shake detector. Our own design:
 *
 * 1. Each axis is divided by its calibrated swing size ([verticalScale], [horizontalScale]), so both
 *    axes are compared in the same units whatever the sensor reports.
 * 2. Each axis is high-passed (the slow drift of how you hold your head is removed).
 * 3. A swing counts when an axis crosses +T after −T or the other way round (hysteresis), so jitter
 *    around zero never counts. T = [threshold] × sensitivity, in units of a typical swing.
 * 4. A nod is at least [minSwings] swings on the vertical axis within [windowMs], while that axis
 *    carries clearly more motion than the other ([dominance]); a shake is the same sideways.
 * 5. After a gesture the detector rests for [cooldownMs] and forgets the old swings.
 *
 * Uncalibrated defaults (scale 1333, threshold 0.45) mean a swing of about 600 raw units.
 */
class HeadGestureDetector(
    var sensitivity: Sensitivity = Sensitivity.NORMAL,
    var verticalScale: Float = DEFAULT_SCALE,
    var horizontalScale: Float = DEFAULT_SCALE,
    var threshold: Float = 0.45f,
    private val minSwings: Int = 2,
    private val windowMs: Long = 1600,
    private val dominance: Float = 1.6f,
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
            val th = threshold * sensitivity.threshold
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

    /** Filtered values of the last sample in swing units (≈ ±1 for a typical nod or shake). */
    var lastVertical = 0f; private set
    var lastHorizontal = 0f; private set

    fun reset() {
        vertical.reset(); horizontal.reset(); vertical.mean = Float.NaN; horizontal.mean = Float.NaN
        vertical.energy = 0f; horizontal.energy = 0f; lastT = -1
    }

    fun feed(s: HeadSample): Gesture? {
        val dt = if (lastT < 0) 40f else (s.timeMs - lastT).coerceIn(1, 500).toFloat()
        lastT = s.timeMs
        lastVertical = vertical.feed(s.timeMs, s.vertical / verticalScale, dt)
        lastHorizontal = horizontal.feed(s.timeMs, s.horizontal / horizontalScale, dt)
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

    companion object { const val DEFAULT_SCALE = 1333f }
}
