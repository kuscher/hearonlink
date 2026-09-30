package io.github.kuscher.hearonlink.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class CalibrationTest {
    private val rnd = Random(3)

    /** 58-byte frames: a counter at 0, noise everywhere, the nod axis at 30, the shake axis at 28. */
    private fun frames(n: Int, nod: Double, shake: Double, start: Int): List<ByteArray> = (0 until n).map { i ->
        val b = ByteArray(58) { rnd.nextInt(-2, 3).toByte() }
        fun put(o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte() }
        put(0, start + i)
        val t = i * 0.04
        put(30, (nod * sin(2 * PI * 2.2 * t) + rnd.nextInt(-30, 30)).toInt())
        put(28, (shake * sin(2 * PI * 2.6 * t) + rnd.nextInt(-30, 30)).toInt())
        put(40, (4000 + 500 * sin(t)).toInt()) // slow drift in both phases
        b
    }

    @Test fun findsTheAxes() {
        val r = Calibration.solve(frames(75, 1500.0, 80.0, 0), frames(75, 90.0, 1700.0, 75))!!
        assertEquals(30, r.verticalOffset)
        assertEquals(28, r.horizontalOffset)
        assertTrue("scale ${r.scale}", r.scale in 900f..1800f)
        assertTrue(r.quality > 3f)
    }

    @Test fun needsEnoughFrames() {
        assertNull(Calibration.solve(frames(5, 1500.0, 0.0, 0), frames(5, 0.0, 1500.0, 5)))
    }
}
