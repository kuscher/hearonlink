package io.github.kuscher.hearonlink.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class HeadGesturesTest {
    private val rnd = Random(7)

    /** 25 Hz samples: [v] and [h] give each axis' value at time t (seconds). */
    private fun run(d: HeadGestureDetector, seconds: Double, v: (Double) -> Double, h: (Double) -> Double, noise: Double = 60.0): List<Gesture> {
        val out = ArrayList<Gesture>()
        var t = 0.0
        while (t < seconds) {
            val s = HeadSample((t * 1000).toLong(), (v(t) + rnd.nextDouble(-noise, noise)).toFloat(), (h(t) + rnd.nextDouble(-noise, noise)).toFloat())
            d.feed(s)?.let(out::add)
            t += 0.04
        }
        return out
    }

    private fun burst(t: Double, start: Double, amp: Double, hz: Double = 2.2, cycles: Double = 1.5) =
        if (t in start..(start + cycles / hz)) amp * sin(2 * PI * hz * (t - start)) else 0.0

    @Test fun nod() {
        val g = run(HeadGestureDetector(), 3.0, { burst(it, 1.0, 1500.0) }, { 0.0 })
        assertEquals(listOf(Gesture.NOD), g)
    }

    @Test fun shake() {
        val g = run(HeadGestureDetector(), 3.0, { 0.0 }, { burst(it, 1.0, 1800.0, hz = 2.8, cycles = 2.0) })
        assertEquals(listOf(Gesture.SHAKE), g)
    }

    @Test fun stillAndSlowDriftDoNothing() {
        val g = run(HeadGestureDetector(), 6.0, { 400 * sin(it * 0.8) }, { 300 * sin(it * 0.5) })
        assertEquals(emptyList<Gesture>(), g)
    }

    @Test fun diagonalWobbleIsAmbiguous() {
        val g = run(HeadGestureDetector(), 3.0, { burst(it, 1.0, 1500.0) }, { burst(it, 1.0, 1400.0) })
        assertEquals(emptyList<Gesture>(), g)
    }

    @Test fun singleLookDownIsNotANod() {
        val g = run(HeadGestureDetector(), 3.0, { if (it > 1.0) -1500.0 else 0.0 }, { 0.0 })
        assertEquals(emptyList<Gesture>(), g)
    }

    @Test fun twoNodsInARowBothCount() {
        val g = run(HeadGestureDetector(), 5.0, { burst(it, 0.8, 1500.0) + burst(it, 3.0, 1500.0) }, { 0.0 })
        assertEquals(listOf(Gesture.NOD, Gesture.NOD), g)
    }

    @Test fun firmNeedsBiggerMotion() {
        assertNull(run(HeadGestureDetector(Sensitivity.FIRM), 3.0, { burst(it, 1.0, 800.0) }, { 0.0 }).firstOrNull())
        assertEquals(Gesture.NOD, run(HeadGestureDetector(Sensitivity.GENTLE), 3.0, { burst(it, 1.0, 800.0) }, { 0.0 }).firstOrNull())
    }
}
