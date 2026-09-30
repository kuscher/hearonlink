package io.github.kuscher.hearonlink.aap

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Packets captured from AirPods Pro 2 (USB-C) on the HP Googlebook, 2026-09-30 (ids and serials replaced). */
class AapTest {
    private fun parse(hex: String) = AapParser.parse(hex.hexBytes())

    @Test fun handshakeAck() {
        assertEquals(AapEvent.HandshakeAck, parse("0100 0400 0000 0100 0300 0000 0000 0000 0000"))
    }

    @Test fun battery() {
        val e = parse("0400 0400 0400 03 04 01 64 02 01 02 01 64 02 01 08 01 00 04 01") as AapEvent.Battery
        assertEquals(3, e.readings.size)
        assertEquals(BatteryReading(Component.LEFT, 100, ChargeState.DISCHARGING), e.readings[0])
        assertEquals(BatteryReading(Component.RIGHT, 100, ChargeState.DISCHARGING), e.readings[1])
        assertEquals(BatteryReading(Component.CASE, null, ChargeState.DISCONNECTED), e.readings[2])
    }

    @Test fun batteryWithChargingCase() {
        val e = parse("0400 0400 0400 03 02 01 64 02 01 04 01 63 01 01 08 01 11 02 01") as AapEvent.Battery
        val s = PodState().reduce(e)
        assertEquals(Level(100, false), s.right.battery)
        assertEquals(Level(99, true), s.left.battery)
        assertEquals(Level(17, false), s.case)
    }

    @Test fun batteryIgnoresImpossibleLevels() {
        val e = parse("0400 0400 0400 01 04 01 7f 02 01") as AapEvent.Battery
        assertNull(e.readings[0].level)
    }

    @Test fun controlAndMode() {
        val e = parse("0400 0400 0900 0d03 0000 00") as AapEvent.Control
        assertEquals(Control.LISTENING_MODE, e.id)
        val s = PodState().reduce(e)
        assertEquals(ListeningMode.TRANSPARENCY, s.listeningMode)
        assertEquals(ListeningMode.NOISE_CANCELLATION, s.nextMode())
        val cycled = s.copy(controls = s.controls + (Control.LISTENING_CYCLE to listOf(0x0e)))
        assertEquals(ListeningMode.ADAPTIVE, cycled.nextMode())
    }

    @Test fun earAndRole() {
        var s = PodState().reduce(parse("0400 0400 0800 0100 0100"))
        assertTrue(s.leftPrimary)
        s = s.reduce(parse("0400 0400 0600 0001"))
        assertEquals(EarState.IN_EAR, s.left.ear)
        assertEquals(EarState.OUT_OF_EAR, s.right.ear)
        s = s.reduce(parse("0400 0400 0800 0000 0100")).reduce(parse("0400 0400 0600 0001"))
        assertEquals(EarState.OUT_OF_EAR, s.left.ear)
        assertEquals(EarState.IN_EAR, s.right.ear)
    }

    @Test fun info() {
        val strings = listOf("AirPods Pro", "A3048", "Apple Inc.", "SERIAL00TEST", "81.1", "81.1", "1.0.0",
            "com.apple.accessory.updater.app.71", "LEFT00SERIAL", "RIGHT0SERIAL", "8454592")
        val payload = "02 df 00 04 00".hexBytes() + strings.joinToString("\u0000", postfix = "\u0000").toByteArray() + byteArrayOf(0x13, 0x7f, 0)
        val e = AapParser.parse(Aap.message(Aap.Op.INFO, payload)) as AapEvent.Info
        assertEquals("AirPods Pro", e.info.name)
        assertEquals("A3048", e.info.modelNumber)
        assertEquals("81.1", e.info.firmware)
        assertEquals("8454592", e.info.build)
        assertEquals("LEFT00SERIAL", e.info.leftSerial)
        assertEquals(Family.PRO_2_USB_C, PodState().reduce(e).family)
    }

    @Test fun sensorFrameAndAck() {
        val frame = "0400 0400 1700 0000 1000 4400 0806 1003 3a3e 0810 1a3a " +
            "0110 d70f cdfb 0300 0003 1002 4cc8 513d 0000 0000 0000 0000 0000 0000 0000 0000 " +
            "6210 6210 6210 6503 10ff e605 6503 10ff e601 0000 80bf 0100 0000"
        val e = parse(frame) as AapEvent.Sensor
        assertEquals(Aap.SENSOR_DEVMOTION, e.service)
        assertEquals(58, e.payload.size)
        assertEquals(AapEvent.SensorStarted(16), parse("0400 0400 1700 0000 1000 0800 0805 1003 4a02 0810"))
    }

    @Test fun sensorStartMatchesCapture() {
        assertArrayEquals(
            "04000400170000001000 0f00 0873 420b 0810 1002 1a05 0140 9c00 00".hexBytes(),
            Aap.sensorStream(0x73, Aap.SENSOR_DEVMOTION, Aap.HEAD_TRACKING_INTERVAL_US),
        )
        assertArrayEquals(
            "04000400170000001000 0f00 0874 420b 0810 1002 1a05 0100 0000 00".hexBytes(),
            Aap.sensorStream(0x74, Aap.SENSOR_DEVMOTION, 0),
        )
    }

    @Test fun builders() {
        assertArrayEquals("0400 0400 0900 0d02 0000 00".hexBytes(), Aap.control(Control.LISTENING_MODE, 2))
        assertArrayEquals("0400 0400 0f00 ffff ffff".hexBytes(), Aap.REQUEST_NOTIFICATIONS)
        assertArrayEquals("0400 0400 4d00 d700 0000 0000 0000".hexBytes(), Aap.FEATURE_FLAGS)
        assertArrayEquals("0400 0400 3000 0500".hexBytes(), Aap.requestKeys())
        assertArrayEquals("0400 0400 1a00 01 03 00 414243".hexBytes(), Aap.rename("ABC"))
    }

    @Test fun keys() {
        val irk = ByteArray(16) { it.toByte() }
        val enc = ByteArray(16) { (0x80 + it).toByte() }
        val p = byteArrayOf(2, 1, 0, 16, 0) + irk + byteArrayOf(4, 0, 16, 0) + enc
        val e = AapParser.parse(Aap.message(Aap.Op.KEYS, p)) as AapEvent.Keys
        assertArrayEquals(irk, e.irk)
        assertArrayEquals(enc, e.encKey)
    }

    @Test fun conversation() {
        assertTrue(PodState().reduce(parse("0400 0400 4b00 0200 0101")).talking)
        assertFalse(PodState(talking = true).reduce(parse("0400 0400 4b00 0200 0108")).talking)
    }

    @Test fun unknownAndGarbageNeverThrow() {
        assertTrue(parse("0400 0400 5500 0101 0019") is AapEvent.Unknown)
        assertTrue(parse("04") is AapEvent.Unknown)
        assertTrue(parse("0400 0400 0400 05 04") is AapEvent.Battery)
        assertTrue(parse("0400 0400 1700 0000 1000 ffff 08") is AapEvent)
    }

    @Test fun features() {
        val s = PodState(info = DeviceInfo("x", "A3048", null, null, null, null, null, null, null, null))
        assertTrue(s.has(Feature.HEAD_GESTURES))
        assertTrue(s.has(Feature.ADAPTIVE))
        val four = PodState(info = DeviceInfo("x", "A3053", null, null, null, null, null, null, null, null))
        assertFalse(four.has(Feature.LISTENING_MODES))
        assertTrue(four.reduce(parse("0400 0400 0900 0d03 0000 00")).has(Feature.LISTENING_MODES))
    }

    @Test fun cycleMask() {
        assertEquals(setOf(ListeningMode.TRANSPARENCY, ListeningMode.NOISE_CANCELLATION, ListeningMode.ADAPTIVE), ListeningMode.fromCycleMask(0x0e))
        assertEquals(0x07, ListeningMode.cycleMask(setOf(ListeningMode.OFF, ListeningMode.TRANSPARENCY, ListeningMode.NOISE_CANCELLATION)))
    }
}
