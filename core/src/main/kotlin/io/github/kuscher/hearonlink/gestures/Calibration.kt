package io.github.kuscher.hearonlink.gestures

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Finds which values in the AirPods' motion frames follow a nod and which follow a shake, so the
 * detector doesn't depend on byte offsets that may move between firmware versions.
 *
 * Give it frames recorded while the person nodded and frames recorded while they shook their head.
 * Every little-endian int16 position is scored: a nod axis moves a lot while nodding and little while
 * shaking, and the other way round. Counters and clocks move in both and score low.
 */
object Calibration {
    data class Result(val verticalOffset: Int, val horizontalOffset: Int, val scale: Float, val quality: Float)

    private fun s16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)).toShort().toFloat()

    private fun std(xs: List<Float>): Float {
        if (xs.size < 2) return 0f
        val m = xs.average().toFloat()
        return sqrt(xs.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / (xs.size - 1))
    }

    /** Typical swing size: the 90th percentile of |x − mean|. */
    private fun swing(xs: List<Float>): Float {
        val m = xs.average().toFloat()
        val d = xs.map { abs(it - m) }.sorted()
        return d[(d.size * 9 / 10).coerceAtMost(d.size - 1)]
    }

    fun solve(nod: List<ByteArray>, shake: List<ByteArray>, minFrames: Int = 20): Result? {
        if (nod.size < minFrames || shake.size < minFrames) return null
        val len = (nod + shake).minOf { it.size }
        if (len < 4) return null
        val offsets = (0..len - 2 step 2).toList()
        val sn = offsets.associateWith { o -> std(nod.map { s16(it, o) }) }
        val ss = offsets.associateWith { o -> std(shake.map { s16(it, o) }) }
        // Ignore flat positions entirely: they are constants.
        val floor = 8f
        val v = offsets.filter { sn[it]!! > floor }.maxByOrNull { sn[it]!! / (ss[it]!! + floor) } ?: return null
        val h = offsets.filter { it != v && ss[it]!! > floor }.maxByOrNull { ss[it]!! / (sn[it]!! + floor) } ?: return null
        val rv = sn[v]!! / (ss[v]!! + floor)
        val rh = ss[h]!! / (sn[h]!! + floor)
        val scale = minOf(swing(nod.map { s16(it, v) }), swing(shake.map { s16(it, h) }))
        return Result(v, h, scale, quality = minOf(rv, rh))
    }
}
