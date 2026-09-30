package io.github.kuscher.hearonlink.aap

enum class Component(val code: Int) { SINGLE(0x01), RIGHT(0x02), LEFT(0x04), CASE(0x08) }

enum class ChargeState { UNKNOWN, CHARGING, DISCHARGING, DISCONNECTED, OPTIMIZED }

data class BatteryReading(val component: Component, val level: Int?, val state: ChargeState) {
    val charging get() = state == ChargeState.CHARGING || state == ChargeState.OPTIMIZED
}

enum class EarState { IN_EAR, OUT_OF_EAR, IN_CASE, UNKNOWN }

data class DeviceInfo(
    val name: String?,
    val modelNumber: String?,
    val manufacturer: String?,
    val serial: String?,
    val firmware: String?,
    val firmwareActive: String?,
    val hardware: String?,
    val leftSerial: String?,
    val rightSerial: String?,
    val build: String?,
) {
    /** Never print serial numbers (they identify the AirPods). */
    override fun toString() = "DeviceInfo(name=$name, model=$modelNumber, firmware=$firmware, build=$build)"
}

/** Everything the AirPods can tell us, decoded. Unknown messages are kept raw. */
sealed interface AapEvent {
    data object HandshakeAck : AapEvent
    data class Battery(val readings: List<BatteryReading>) : AapEvent
    /** Ear state of the primary and the secondary bud; [BudRole] says which one is left. */
    data class Ear(val primary: EarState, val secondary: EarState) : AapEvent
    data class BudRole(val leftPrimary: Boolean) : AapEvent
    class Control(val id: Int, val value: ByteArray) : AapEvent {
        val v1 get() = value.getOrNull(0)?.toInt()?.and(0xff) ?: 0
        override fun toString() = "Control(0x%02x = %s)".format(id, value.hex())
    }
    data class Info(val info: DeviceInfo) : AapEvent
    class Capabilities(val raw: ByteArray) : AapEvent
    /** Conversation awareness: 1–2 you started talking, 3+ ramping, 6/8/9 back to normal. */
    data class Conversation(val level: Int) : AapEvent
    data class StemPress(val press: Int, val bud: Int) : AapEvent
    class Keys(val irk: ByteArray?, val encKey: ByteArray?) : AapEvent {
        override fun toString() = "Keys(irk=${irk != null}, enc=${encKey != null})"
    }
    class Sensor(val service: Int, val payload: ByteArray) : AapEvent {
        override fun toString() = "Sensor($service, ${payload.size} B)"
    }
    data class SensorStarted(val service: Int) : AapEvent
    /** Custom EQ as the AirPods report it; [raw] is kept to echo the unknown flags byte. */
    class Eq(val on: Boolean, val low: Int, val mid: Int, val high: Int, val raw: ByteArray) : AapEvent {
        override fun toString() = "Eq(on=$on, $low/$mid/$high)"
    }
    class Unknown(val type: Int, val opcode: Int, val payload: ByteArray) : AapEvent {
        override fun toString() = "Unknown(type $type, op 0x%02x, %s)".format(opcode, payload.hex())
    }
}

object AapParser {
    fun parse(packet: ByteArray): AapEvent {
        if (packet.size < 4) return AapEvent.Unknown(-1, -1, packet)
        val type = packet.u16le(0)
        if (type == Aap.TYPE_CONNECT_RESPONSE) return AapEvent.HandshakeAck
        if (type != Aap.TYPE_MESSAGE || packet.size < 6) return AapEvent.Unknown(type, -1, packet)
        val op = packet.u16le(4)
        val p = packet.copyOfRange(6, packet.size)
        return runCatching { decode(op, p) }.getOrNull() ?: AapEvent.Unknown(type, op, p)
    }

    private fun decode(op: Int, p: ByteArray): AapEvent? = when (op) {
        Aap.Op.BATTERY -> battery(p)
        Aap.Op.EAR -> if (p.size >= 2) AapEvent.Ear(ear(p.u8(0)), ear(p.u8(1))) else null
        Aap.Op.BUD_ROLE -> if (p.isNotEmpty()) AapEvent.BudRole(p.u8(0) == 1) else null
        Aap.Op.CONTROL -> if (p.size >= 2) AapEvent.Control(p.u8(0), p.copyOfRange(1, minOf(p.size, 5))) else null
        Aap.Op.INFO -> AapEvent.Info(info(p))
        Aap.Op.CAPABILITIES -> AapEvent.Capabilities(p)
        Aap.Op.CONVERSATION -> if (p.size >= 4) AapEvent.Conversation(p.u8(3)) else null
        Aap.Op.STEM_PRESS -> if (p.size >= 2) AapEvent.StemPress(p.u8(0), p.u8(1)) else null
        Aap.Op.KEYS -> keys(p)
        Aap.Op.SENSOR -> sensor(p)
        Aap.Op.CUSTOM_EQ -> if (p.size >= 7) AapEvent.Eq(p.u8(3) == 1, p.u8(4), p.u8(5), p.u8(6), p) else null
        else -> null
    }

    private fun battery(p: ByteArray): AapEvent.Battery? {
        val n = p.u8(0)
        val out = ArrayList<BatteryReading>(n)
        for (k in 0 until n) {
            val o = 1 + 5 * k
            if (o + 4 > p.size) break
            val comp = Component.entries.firstOrNull { it.code == p.u8(o) } ?: continue
            val raw = p.u8(o + 2)
            val state = when (p.u8(o + 3)) {
                1 -> ChargeState.CHARGING; 2 -> ChargeState.DISCHARGING; 4 -> ChargeState.DISCONNECTED
                5 -> ChargeState.OPTIMIZED; else -> ChargeState.UNKNOWN
            }
            // 127 has been seen for a missing bud; a disconnected part has no real level.
            val level = raw.takeIf { it in 0..100 && state != ChargeState.DISCONNECTED }
            out += BatteryReading(comp, level, state)
        }
        return AapEvent.Battery(out)
    }

    private fun ear(v: Int) = when (v) {
        0 -> EarState.IN_EAR; 1 -> EarState.OUT_OF_EAR; 2 -> EarState.IN_CASE; else -> EarState.UNKNOWN
    }

    /** NUL-separated strings after a short header; binary blobs follow the strings we use. */
    private fun info(p: ByteArray): DeviceInfo {
        var start = 0
        while (start < p.size && !(printable(p, start) && printable(p, start + 1) && printable(p, start + 2))) start++
        val strings = ArrayList<String>()
        var i = start
        while (i < p.size && strings.size < 11) {
            var j = i
            while (j < p.size && p[j].toInt() != 0) j++
            val s = String(p, i, j - i, Charsets.UTF_8)
            strings += if (s.all { it.code in 0x20..0x7e }) s else ""
            i = j + 1
        }
        fun at(k: Int) = strings.getOrNull(k)?.takeIf { it.isNotEmpty() }
        return DeviceInfo(
            name = at(0), modelNumber = at(1), manufacturer = at(2), serial = at(3),
            firmware = at(4), firmwareActive = at(5), hardware = at(6),
            leftSerial = at(8), rightSerial = at(9), build = at(10),
        )
    }

    private fun printable(p: ByteArray, i: Int) = i < p.size && p.u8(i) in 0x20..0x7e

    private fun keys(p: ByteArray): AapEvent.Keys {
        val n = p.u8(0)
        var i = 1
        var irk: ByteArray? = null
        var enc: ByteArray? = null
        repeat(n) {
            if (i + 4 > p.size) return@repeat
            val type = p.u8(i); val len = p.u8(i + 2)
            if (i + 4 + len > p.size) return@repeat
            val key = p.copyOfRange(i + 4, i + 4 + len)
            when (type) { 0x01 -> irk = key; 0x04 -> enc = key }
            i += 4 + len
        }
        return AapEvent.Keys(irk, enc)
    }

    /** Heart rate from a HEARTRATE sensor payload (bpm in byte 1), or null. */
    fun heartRate(e: AapEvent.Sensor): Int? =
        if ((e.service == Aap.SENSOR_HEART_RATE || e.service == Aap.SENSOR_HEART_RATE_CMD) && e.payload.size >= 2)
            e.payload.u8(1).takeIf { it in 30..230 } else null

    private fun sensor(p: ByteArray): AapEvent? {
        if (p.size < 6 || p.u32le(0) != Aap.SENSOR_DESCRIPTOR) return null
        val len = p.u16le(4)
        val msg = parseProto(p.copyOfRange(6, minOf(p.size, 6 + len)))
        msg.bytes(7)?.let { cmd ->
            val f = parseProto(cmd)
            val service = f.varint(1)?.toInt() ?: return null
            return AapEvent.Sensor(service, f.bytes(3) ?: ByteArray(0))
        }
        msg.bytes(9)?.let { ack -> return AapEvent.SensorStarted(parseProto(ack).varint(1)?.toInt() ?: -1) }
        return null
    }
}
