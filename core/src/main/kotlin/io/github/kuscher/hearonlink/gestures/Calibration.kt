package io.github.kuscher.hearonlink.gestures

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Finds which values in the AirPods' motion frames follow a nod and which follow a shake, and how big
 * each one swings, so detection doesn't depend on byte offsets or units that vary between firmware
 * versions and between the left and right bud.
 *
 * Three recordings: holding still, nodding, shaking. Every little-endian int16 position is scored.
 * A nod axis moves a lot while nodding, little while shaking and barely while still; a shake axis the
 * other way round. Counters and clocks change even while still and score low.
 */
object Calibration {
    data class Result(
        val verticalOffset: Int,
        val horizontalOffset: Int,
        /** Typical swing of each axis in its own units (the detector normalises by these). */
        val verticalScale: Float,
        val horizontalScale: Float,
        /** How clearly the axes separate (higher is better; below ~2 is unreliable). */
        val quality: Float,
    )

    private fun s16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)).toShort().toFloat()

    private fun std(xs: List<Float>): Float {
        if (xs.size < 2) return 0f
        val m = xs.average().toFloat()
        return sqrt(xs.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / (xs.size - 1))
    }

    /** Typical swing size: the 90th percentile of |x − median|. */
    private fun swing(xs: List<Float>): Float {
        val med = xs.sorted()[xs.size / 2]
        val d = xs.map { abs(it - med) }.sorted()
        return d[(d.size * 9 / 10).coerceAtMost(d.size - 1)]
    }

    fun solve(still: List<ByteArray>, nod: List<ByteArray>, shake: List<ByteArray>, minFrames: Int = 20): Result? {
        if (nod.size < minFrames || shake.size < minFrames) return null
        val len = (still + nod + shake).minOf { it.size }
        if (len < 4) return null
        val offsets = (0..len - 2 step 2).toList()
        fun series(frames: List<ByteArray>, o: Int) = frames.map { s16(it, o) }
        val sS = offsets.associateWith { o -> std(series(still, o)) }
        val sN = offsets.associateWith { o -> std(series(nod, o)) }
        val sH = offsets.associateWith { o -> std(series(shake, o)) }
        val floor = 8f
        fun moves(o: Int, phase: Map<Int, Float>) = phase[o]!! > 3 * sS[o]!! + floor
        val v = offsets.filter { moves(it, sN) }.maxByOrNull { sN[it]!! / (sH[it]!! + sS[it]!! + floor) } ?: return null
        val h = offsets.filter { it != v && moves(it, sH) }.maxByOrNull { sH[it]!! / (sN[it]!! + sS[it]!! + floor) } ?: return null
        val rv = sN[v]!! / (sH[v]!! + sS[v]!! + floor)
        val rh = sH[h]!! / (sN[h]!! + sS[h]!! + floor)
        return Result(v, h, swing(series(nod, v)).coerceAtLeast(floor), swing(series(shake, h)).coerceAtLeast(floor), minOf(rv, rh))
    }

    /** Without a still recording (older callers). */
    fun solve(nod: List<ByteArray>, shake: List<ByteArray>, minFrames: Int = 20) = solve(emptyList(), nod, shake, minFrames)
}
