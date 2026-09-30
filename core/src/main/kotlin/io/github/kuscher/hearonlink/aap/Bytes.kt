package io.github.kuscher.hearonlink.aap

/** "04 00 0f00" → bytes. Anything that isn't a hex digit is ignored. */
fun String.hexBytes(): ByteArray {
    val digits = filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    require(digits.length % 2 == 0) { "odd number of hex digits" }
    return ByteArray(digits.length / 2) { digits.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xff
internal fun ByteArray.u16le(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.s16le(i: Int): Int = u16le(i).toShort().toInt()
internal fun ByteArray.u32le(i: Int): Long =
    (u16le(i).toLong()) or (u16le(i + 2).toLong() shl 16)

internal fun u16le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
internal fun u32le(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
