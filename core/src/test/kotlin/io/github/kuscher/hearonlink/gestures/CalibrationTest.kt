package io.github.kuscher.hearonlink.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Frames shaped like the AirPods Pro 2's DEVMOTION6 payload recorded on the HP (58 bytes):
 * counters/clocks at 0, 2, 10, 12, 50 that change every frame; a noisy orientation-like value at 24
 * (±220 even when still); rate-like axes at 26, 28, 30 (±20 when still); gravity around 44–48.
 * Here a nod moves 28 (and a bit of 24), a shake moves 26.
 */
class CalibrationTest {
    private val rnd = Random(3)

    private fun frames(n: Int, nod: Double, shake: Double): List<ByteArray> = (0 until n).map { i ->
        val b = ByteArray(58)
        fun put(o: Int, v: Int) { val c = v.coerceIn(-32768, 32767); b[o] = c.toByte(); b[o + 1] = (c shr 8).toByte() }
        for (o in listOf(0, 2, 10, 12, 50)) put(o, rnd.nextInt(-32768, 32767))
        val t = i * 0.04
        put(24, (1040 + rnd.nextInt(-300, 300) + 0.8 * nod * sin(2 * PI * 2.2 * t)).toInt())
        put(28, (rnd.nextInt(-25, 25) + nod * sin(2 * PI * 2.2 * t)).toInt())
        put(26, (rnd.nextInt(-25, 25) + shake * sin(2 * PI * 2.6 * t)).toInt())
        put(30, rnd.nextInt(-25, 25))
        put(44, 709 + rnd.nextInt(-5, 5)); put(46, 699 + rnd.nextInt(-9, 9)); put(48, 231 + rnd.nextInt(-28, 28))
        b
    }

    @Test fun findsTheRateAxesNotTheNoisyOnes() {
        val r = Calibration.solve(frames(60, 0.0, 0.0), frames(125, 900.0, 40.0), frames(125, 60.0, 700.0))!!
        assertEquals(28, r.verticalOffset)
        assertEquals(26, r.horizontalOffset)
        assertTrue("v scale ${r.verticalScale}", r.verticalScale in 600f..1100f)
        assertTrue("h scale ${r.horizontalScale}", r.horizontalScale in 450f..850f)
        assertTrue("quality ${r.quality}", r.quality > 2f)
    }

    @Test fun needsEnoughFrames() {
        assertNull(Calibration.solve(frames(10, 0.0, 0.0), frames(5, 1500.0, 0.0), frames(5, 0.0, 1500.0)))
    }

    /** The bug users hit: one scale for both axes made small-unit shakes unreachable. */
    @Test fun perAxisScalesMakeSmallShakesCount() {
        val d = HeadGestureDetector(verticalScale = 2166f, horizontalScale = 300f)
        var got: Gesture? = null
        var t = 0.0
        while (t < 3.0) {
            val h = if (t in 1.0..1.8) 320 * sin(2 * PI * 2.6 * (t - 1.0)) else 0.0
            val v = 150 * sin(t * 3)   // a little pitch wobble in big units
            d.feed(HeadSample((t * 1000).toLong(), v.toFloat(), (h + rnd.nextInt(-15, 15)).toFloat()))?.let { got = it }
            t += 0.04
        }
        assertEquals(Gesture.SHAKE, got)
    }
}
