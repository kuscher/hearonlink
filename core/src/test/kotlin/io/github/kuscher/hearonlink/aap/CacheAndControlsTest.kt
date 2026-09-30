package io.github.kuscher.hearonlink.aap

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheAndControlsTest {
    private fun b(c: Component, lv: Int?, st: ChargeState) = BatteryReading(c, lv, st)

    @Test fun disconnectedKeepsTheLastLevel() {
        var cache = Batteries().fromAap(listOf(
            b(Component.LEFT, 90, ChargeState.DISCHARGING), b(Component.RIGHT, 88, ChargeState.DISCHARGING),
            b(Component.CASE, 72, ChargeState.CHARGING)), now = 1_000)
        // Captured on the HP: right bud in the closed case, case not reporting.
        cache = cache.fromAap(listOf(
            b(Component.LEFT, 87, ChargeState.DISCHARGING), b(Component.RIGHT, null, ChargeState.DISCONNECTED),
            b(Component.CASE, null, ChargeState.DISCONNECTED)), now = 60_000)
        assertEquals(87, cache.left!!.percent); assertTrue(cache.left!!.live)
        assertEquals(88, cache.right!!.percent); assertFalse(cache.right!!.live); assertEquals(1_000L, cache.right!!.at)
        assertEquals(72, cache.case!!.percent); assertTrue(cache.case!!.charging); assertFalse(cache.case!!.live)
    }

    @Test fun advertsFillTheCaseButDontFightLiveValues() {
        var cache = Batteries().fromAap(listOf(b(Component.LEFT, 87, ChargeState.DISCHARGING)), now = 100_000)
        cache = cache.fromAdvert(left = 80, right = 90, case = 64, lc = false, rc = false, cc = false, precise = true, now = 110_000)
        assertEquals(87, cache.left!!.percent)            // live: kept
        // Still live ten minutes later (pushes only come on change): an advert doesn't replace it.
        assertEquals(87, cache.fromAdvert(70, null, null, false, false, false, precise = true, now = 700_000).left!!.percent)
        assertEquals(90, cache.right!!.percent)           // unknown before: taken
        assertEquals(64, cache.case!!.percent); assertEquals(Source.ADVERT_PRECISE, cache.case!!.source)
        // A coarse advert close to a precise value doesn't replace it.
        cache = cache.fromAdvert(null, null, case = 60, lc = false, rc = false, cc = false, precise = false, now = 120_000)
        assertEquals(64, cache.case!!.percent)
    }

    @Test fun staleAndExpiry() {
        val cache = Batteries().fromAap(listOf(b(Component.LEFT, 50, ChargeState.DISCHARGING)), now = 0).stale()
        assertFalse(cache.left!!.live)
        assertNull(cache.expire(now = 25 * 3600_000L).left)
        assertEquals(50, cache.expire(now = 3600_000L).left!!.percent)
    }

    @Test fun eqRoundTrip() {
        val e = AapParser.parse(Aap.message(Aap.Op.CUSTOM_EQ, "0500 07 01 3c 32 46".hexBytes())) as AapEvent.Eq
        assertTrue(e.on); assertEquals(60, e.low); assertEquals(50, e.mid); assertEquals(70, e.high)
        assertArrayEquals("0400 0400 6300 0500 07 02 1e 32 64".hexBytes(), Aap.eq(e.raw, on = false, low = 30, mid = 50, high = 120))
        assertArrayEquals("0400 0400 6300 0500 00 01 32 32 32".hexBytes(), Aap.eq(null, on = true, low = 50, mid = 50, high = 50))
    }

    @Test fun pressesAndNewControls() {
        assertEquals(0x0a, Press.mask(listOf(Press.DOUBLE, Press.LONG)))
        assertEquals(Press.TRIPLE, Press.of(7))
        val s = PodState().reduce(AapParser.parse("0400 0400 0900 2420 0300 00".hexBytes()))
        assertEquals(false, s.callControlsSwapped)
        assertEquals(AapEvent.StemPress(6, 2), AapParser.parse("0400 0400 1900 0602".hexBytes()))
    }

    @Test fun heartRate() {
        assertEquals(72, AapParser.heartRate(AapEvent.Sensor(Aap.SENSOR_HEART_RATE, byteArrayOf(1, 72, 0))))
        assertNull(AapParser.heartRate(AapEvent.Sensor(Aap.SENSOR_DEVMOTION, byteArrayOf(1, 72, 0))))
    }

    @Test fun beatsProductIds() {
        assertEquals(Family.BEATS_FIT_PRO, Family.ofProductId(0x2012))
        assertTrue(Family.BEATS_FIT_PRO.beats)
        assertEquals("Headphones", Family.ofModelNumber("A9999").displayName)
    }
}
