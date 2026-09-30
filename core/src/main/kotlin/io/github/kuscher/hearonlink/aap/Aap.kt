package io.github.kuscher.hearonlink.aap

/**
 * Apple's accessory protocol (AAP) as AirPods speak it over classic Bluetooth L2CAP, PSM 0x1001.
 *
 * Every message is `[type u16][service u16][opcode u16][payload…]`, little-endian. Normal messages
 * have type 4 and service 4. Byte layouts come from public protocol notes (LibrePods' research,
 * credited in the README) and our own captures; this is an independent implementation.
 */
object Aap {
    const val PSM = 0x1001
    const val SERVICE_UUID = "74ec2172-0bad-4d01-8f77-997b2be0722a"

    const val TYPE_CONNECT = 0
    const val TYPE_CONNECT_RESPONSE = 1
    const val TYPE_MESSAGE = 4

    object Op {
        const val CAPABILITIES = 0x02
        const val BATTERY = 0x04
        const val EAR = 0x06
        const val BUD_ROLE = 0x08
        const val CONTROL = 0x09
        const val REQUEST_NOTIFICATIONS = 0x0F
        const val SENSOR = 0x17
        const val STEM_PRESS = 0x19
        const val RENAME = 0x1A
        const val INFO = 0x1D
        const val KEYS_REQUEST = 0x30
        const val KEYS = 0x31
        const val CONVERSATION = 0x4B
        const val FEATURE_FLAGS = 0x4D
        const val CUSTOM_EQ = 0x63
    }

    fun message(opcode: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(TYPE_MESSAGE.toByte(), 0, 4, 0) + u16le(opcode) + payload

    /** Opens the session. The AirPods answer with a CONNECT_RESPONSE, then push their state. */
    val HANDSHAKE: ByteArray = "00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00".hexBytes()

    /** Tells the AirPods what this host supports; without it Adaptive falls back to ANC. */
    val FEATURE_FLAGS: ByteArray = message(Op.FEATURE_FLAGS, "d7 00 00 00 00 00 00 00".hexBytes())

    /** Subscribe to every notification (battery, ear state, controls, …). */
    val REQUEST_NOTIFICATIONS: ByteArray = message(Op.REQUEST_NOTIFICATIONS, "ff ff ff ff".hexBytes())

    /** Ask for the identity key (0x01) and the advert encryption key (0x04). Answered with op 0x31. */
    fun requestKeys(mask: Int = 0x05): ByteArray = message(Op.KEYS_REQUEST, byteArrayOf(mask.toByte(), 0))

    /** A control command: id plus up to four value bytes (always 5 payload bytes). */
    fun control(id: Int, vararg value: Int): ByteArray {
        require(value.size <= 4)
        val p = ByteArray(5); p[0] = id.toByte()
        value.forEachIndexed { i, v -> p[i + 1] = v.toByte() }
        return message(Op.CONTROL, p)
    }

    fun rename(name: String): ByteArray {
        val utf8 = name.toByteArray(Charsets.UTF_8)
        require(utf8.size in 1..255)
        return message(Op.RENAME, byteArrayOf(1, utf8.size.toByte(), 0) + utf8)
    }

    /**
     * Start (intervalMicros > 0) or stop (0) a sensor stream. Sensor messages are protobufs inside
     * `[descriptor u32 = 0x00100000][length u16]`; the settings live in field 8.
     */
    fun sensorStream(seq: Int, service: Int, intervalMicros: Long): ByteArray {
        val proto = ProtoWriter()
            .varint(1, seq.toLong())
            .message(8) {
                varint(1, service.toLong())
                varint(2, 2)
                bytes(3, byteArrayOf(1) + u32le(intervalMicros))
            }
            .toByteArray()
        return message(Op.SENSOR, u32le(SENSOR_DESCRIPTOR) + u16le(proto.size) + proto)
    }

    const val SENSOR_DESCRIPTOR = 0x00100000L
    const val SENSOR_ACTIVITY = 14
    const val SENSOR_DEVMOTION = 16
    const val SENSOR_HEART_RATE = 19
    const val SENSOR_HEART_RATE_CMD = 84
    const val HEAD_TRACKING_INTERVAL_US = 40_000L // 25 Hz
    const val HEART_RATE_INTERVAL_US = 1_000_000L

    /**
     * Custom EQ (op 0x63): `[length u16 = 5][flags][state 1 on / 2 off][low][mid][high]`, bands 0–100.
     * The flags byte isn't understood, so we echo the one the AirPods last sent (0 if none).
     */
    fun eq(template: ByteArray?, on: Boolean, low: Int, mid: Int, high: Int): ByteArray {
        val flags = template?.takeIf { it.size >= 7 }?.get(2) ?: 0
        return message(Op.CUSTOM_EQ, u16le(5) + byteArrayOf(flags, if (on) 1 else 2,
            low.coerceIn(0, 100).toByte(), mid.coerceIn(0, 100).toByte(), high.coerceIn(0, 100).toByte()))
    }
}

/** Stem presses the AirPods can forward to the host instead of handling them (control 0x39). */
enum class Press(val code: Int, val bit: Int) {
    SINGLE(5, 0x01), DOUBLE(6, 0x02), TRIPLE(7, 0x04), LONG(8, 0x08);
    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code }
        fun mask(presses: Collection<Press>) = presses.fold(0) { m, p -> m or p.bit }
    }
}

/** Control command ids (op 0x09). Booleans are 1 = on, 2 = off. */
object Control {
    const val MIC_MODE = 0x01              // 0 automatic, 1 always right, 2 always left
    const val EAR_DETECTION = 0x0A
    const val LISTENING_MODE = 0x0D
    const val CLICK_HOLD = 0x16            // [right][left]: 1 noise control, 5 voice assistant
    const val PRESS_SPEED = 0x17
    const val HOLD_DURATION = 0x18
    const val LISTENING_CYCLE = 0x1A
    const val ONE_BUD_ANC = 0x1B
    const val CROWN_DIRECTION = 0x1C       // AirPods Max: 1 reversed, 2 default
    const val TONE_VOLUME = 0x1F
    const val SWIPE_INTERVAL = 0x23
    const val CALL_CONTROLS = 0x24         // 2 bytes; the second is 3 (default) or 2 (mute/end swapped)
    const val VOLUME_SWIPE = 0x25
    const val PERSONALIZED_VOLUME = 0x26
    const val CONVERSATION_AWARENESS = 0x28
    const val ADAPTIVE_LEVEL = 0x2E
    const val CASE_SOUNDS = 0x31
    const val ALLOW_OFF = 0x34
    const val SLEEP_DETECTION = 0x35
    const val HEARING_PROTECTION = 0x37
    const val RAW_PRESSES = 0x39           // bitmask of Press.bit forwarded to the host
    const val OPTIMIZED_CHARGING = 0x3B

    const val ON = 1
    const val OFF = 2
}

enum class ListeningMode(val code: Int, val cycleBit: Int) {
    OFF(1, 0x01), NOISE_CANCELLATION(2, 0x02), TRANSPARENCY(3, 0x04), ADAPTIVE(4, 0x08);

    companion object {
        fun of(code: Int): ListeningMode? = entries.firstOrNull { it.code == code }
        /** The order Apple's UI shows them in. */
        val DISPLAY_ORDER = listOf(OFF, TRANSPARENCY, ADAPTIVE, NOISE_CANCELLATION)
        fun fromCycleMask(mask: Int): Set<ListeningMode> = entries.filter { mask and it.cycleBit != 0 }.toSet()
        fun cycleMask(modes: Set<ListeningMode>): Int = modes.fold(0) { m, it -> m or it.cycleBit }
    }
}
