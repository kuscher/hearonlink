package io.github.kuscher.hearonlink.ble

import io.github.kuscher.hearonlink.aap.Family
import io.github.kuscher.hearonlink.aap.hexBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class ProximityTest {
    // type 07, len 19, paired 01, model 2420 (Pro 2 USB-C), status 0x22 (left primary, a bud in ear),
    // pods 0x98 (left 80 %, right 90 %), 0x46 (case charging, case 60 %), lid 0x01 (open), colour 0, state 05.
    private val clear = "07 19 01 2420 22 98 46 01 00 05".hexBytes()

    private fun advert(encrypted: ByteArray = ByteArray(16)) = clear + encrypted

    @Test fun parsesLevelsAndModel() {
        val a = Proximity.parse(advert())!!
        assertEquals(Family.PRO_2_USB_C, a.family)
        assertTrue(a.leftPrimary)
        assertEquals(80, a.left); assertEquals(90, a.right); assertEquals(60, a.case)
        assertTrue(a.caseCharging); assertFalse(a.leftCharging)
        assertTrue(a.lidOpen)
        assertEquals(5, a.connectionState)
    }

    @Test fun rejectsOtherFrames() {
        assertNull(Proximity.parse("07 19 00 2420".hexBytes() + ByteArray(23)))
        assertNull(Proximity.parse("10 05 01 02 03".hexBytes()))
    }

    @Test fun decryptsPreciseLevels() {
        val key = ByteArray(16) { (it * 7).toByte() }
        val plain = byteArrayOf(0, 0x57, (0x80 or 0x60).toByte(), 0x2a) + ByteArray(12)
        val c = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val p = Proximity.decrypt(Proximity.parse(advert(c.doFinal(plain)))!!, key)!!
        assertEquals(87, p.left); assertEquals(96, p.right); assertEquals(42, p.case)
        assertTrue(p.rightCharging); assertFalse(p.leftCharging)
    }

    @Test fun resolvesPrivateAddress() {
        val irk = ByteArray(16) { (0x10 + it).toByte() }
        val prand = byteArrayOf(0x5a, 0x12, 0x34)
        val c = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(irk, "AES")) }
        val hash = c.doFinal(ByteArray(13) + prand).copyOfRange(13, 16)
        val addr = prand + hash
        assertTrue(Proximity.resolves(addr, irk))
        assertTrue(Proximity.resolves(addr, irk.reversedArray()))
        assertFalse(Proximity.resolves(byteArrayOf(0x5a, 0x12, 0x34, 0, 0, 0), irk))
        assertNotNull(Proximity.parse(advert()))
    }
}
