package io.github.kuscher.hearonlink.ble

import io.github.kuscher.hearonlink.aap.Family
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Apple's "proximity pairing" BLE advert (manufacturer id 0x004C, type 0x07), which AirPods send
 * from the case and the buds. Offsets below are into the manufacturer data after the company id.
 */
data class ProximityAdvert(
    val productId: Int,
    val family: Family,
    val leftPrimary: Boolean,
    val left: Int?, val right: Int?, val case: Int?,
    val leftCharging: Boolean, val rightCharging: Boolean, val caseCharging: Boolean,
    val leftInEar: Boolean, val rightInEar: Boolean,
    val lidOpen: Boolean,
    val bothInCase: Boolean,
    val fromCase: Boolean,
    val connectionState: Int,
    val encrypted: ByteArray,
) {
    override fun equals(other: Any?) = other is ProximityAdvert && productId == other.productId &&
        left == other.left && right == other.right && case == other.case && lidOpen == other.lidOpen &&
        leftInEar == other.leftInEar && rightInEar == other.rightInEar && encrypted.contentEquals(other.encrypted)
    override fun hashCode() = listOf(productId, left, right, case, lidOpen).hashCode()
}

object Proximity {
    const val APPLE = 0x004C

    /** Hardware scan filter: type 0x07, length 0x19. */
    val FILTER_DATA = byteArrayOf(0x07, 0x19)
    val FILTER_MASK = byteArrayOf(0xff.toByte(), 0xff.toByte())

    fun parse(data: ByteArray): ProximityAdvert? {
        if (data.size < 27 || u(data, 0) != 0x07 || u(data, 1) != 0x19 || u(data, 2) != 0x01) return null
        val product = u(data, 3) or (u(data, 4) shl 8)
        val status = u(data, 5)
        val leftPrimary = status and 0x20 != 0
        val thisInCase = status and 0x40 != 0
        // The in-ear bits are relative to the broadcasting bud; flip when needed.
        val flip = leftPrimary == thisInCase
        val inEarA = status and 0x02 != 0
        val inEarB = status and 0x08 != 0
        val pods = u(data, 6)
        val lo = level(pods and 0x0f); val hi = level(pods shr 4)
        val (left, right) = if (leftPrimary) lo to hi else hi to lo
        val flags = u(data, 7)
        val chg = flags shr 4
        val (lc, rc) = if (leftPrimary) (chg and 1 != 0) to (chg and 2 != 0) else (chg and 2 != 0) to (chg and 1 != 0)
        return ProximityAdvert(
            productId = product,
            family = Family.ofProductId(product),
            leftPrimary = leftPrimary,
            left = left, right = right, case = level(flags and 0x0f),
            leftCharging = lc, rightCharging = rc, caseCharging = chg and 4 != 0,
            leftInEar = if (flip) inEarB else inEarA, rightInEar = if (flip) inEarA else inEarB,
            lidOpen = u(data, 8) and 0x08 == 0,
            bothInCase = status and 0x04 != 0,
            fromCase = thisInCase,
            connectionState = u(data, 10),
            encrypted = data.copyOfRange(11, 27),
        )
    }

    /** 0–10 → ×10 %, 11–14 → 100 %, 15 → unknown. */
    private fun level(n: Int): Int? = when (n) { in 0..10 -> n * 10; in 11..14 -> 100; else -> null }

    data class Precise(val left: Int?, val right: Int?, val case: Int?, val leftCharging: Boolean, val rightCharging: Boolean, val caseCharging: Boolean)

    /** The encrypted part gives 1 % levels. [encKey] comes from the AirPods over AAP (op 0x31). */
    fun decrypt(advert: ProximityAdvert, encKey: ByteArray): Precise? {
        if (encKey.size != 16) return null
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"))
        val d = c.doFinal(advert.encrypted)
        fun lv(b: Int) = (b and 0x7f).takeIf { b != 0xff && it <= 100 }
        val a = d[1].toInt() and 0xff; val b = d[2].toInt() and 0xff; val cs = d[3].toInt() and 0xff
        val (l, r) = if (advert.leftPrimary) a to b else b to a
        return Precise(lv(l), lv(r), lv(cs), l and 0x80 != 0 && l != 0xff, r and 0x80 != 0 && r != 0xff, cs and 0x80 != 0 && cs != 0xff)
    }

    /**
     * Does a random private address belong to the AirPods with this identity key? Standard Bluetooth
     * address resolution: hash = AES(IRK, 0¹³ ‖ prand)[13..15]. [address] is MSB first
     * ("AA:BB:…" order). Keys from the AirPods may be byte-reversed, so both orders are tried.
     */
    fun resolves(address: ByteArray, irk: ByteArray): Boolean {
        if (address.size != 6 || irk.size != 16) return false
        if (u(address, 0) and 0xc0 != 0x40) return false // not a resolvable private address
        val prand = address.copyOfRange(0, 3)
        val hash = address.copyOfRange(3, 6)
        for (key in listOf(irk, irk.reversedArray())) {
            val c = Cipher.getInstance("AES/ECB/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val out = c.doFinal(ByteArray(13) + prand)
            if (out.copyOfRange(13, 16).contentEquals(hash)) return true
        }
        return false
    }

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xff
}
