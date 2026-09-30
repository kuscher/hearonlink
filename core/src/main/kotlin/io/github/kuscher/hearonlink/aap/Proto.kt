package io.github.kuscher.hearonlink.aap

import java.io.ByteArrayOutputStream

/**
 * Just enough protocol-buffers wire format for the AirPods' sensor ("RTBuddy") messages:
 * varints and length-delimited fields. Unknown wire types end the parse.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter { rawVarint((field shl 3).toLong()); rawVarint(value); return this }
    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        rawVarint(((field shl 3) or 2).toLong()); rawVarint(value.size.toLong()); out.write(value); return this
    }
    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()

    private fun rawVarint(v: Long) {
        var x = v
        while (x and 0x7fL.inv() != 0L) { out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
    }
}

internal class ProtoField(val number: Int, val varint: Long?, val bytes: ByteArray?)

internal fun parseProto(data: ByteArray): List<ProtoField> {
    val fields = ArrayList<ProtoField>()
    var i = 0
    fun readVarint(): Long {
        var shift = 0; var result = 0L
        while (i < data.size) {
            val b = data[i++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) break
        }
        throw IllegalArgumentException("bad varint")
    }
    try {
        while (i < data.size) {
            val key = readVarint()
            val number = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> fields += ProtoField(number, readVarint(), null)
                2 -> {
                    val len = readVarint().toInt()
                    if (len < 0 || i + len > data.size) return fields
                    fields += ProtoField(number, null, data.copyOfRange(i, i + len)); i += len
                }
                5 -> { if (i + 4 > data.size) return fields; fields += ProtoField(number, data.u32le(i), null); i += 4 }
                else -> return fields
            }
        }
    } catch (_: IllegalArgumentException) {
    }
    return fields
}

internal fun List<ProtoField>.varint(n: Int): Long? = firstOrNull { it.number == n && it.varint != null }?.varint
internal fun List<ProtoField>.bytes(n: Int): ByteArray? = firstOrNull { it.number == n && it.bytes != null }?.bytes
