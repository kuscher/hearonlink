package io.github.kuscher.hearonlink.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import io.github.kuscher.hearonlink.aap.Aap
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.AapParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException

/**
 * The one Android call that isn't public: a classic (BR/EDR) L2CAP socket to a PSM. Android 17's
 * public BluetoothSocketSettings only takes RFCOMM and LE, so we reach the hidden
 * BluetoothDevice.createL2capSocket(int) through LSPosed's HiddenApiBypass (Apache-2.0), exempting
 * only the two Bluetooth classes we touch.
 */
object L2cap {
    @Volatile private var exempted = false

    fun exempt() {
        if (!exempted) exempted = HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/bluetooth/BluetoothDevice;", "Landroid/bluetooth/BluetoothSocket;",
        )
    }

    fun open(device: BluetoothDevice, psm: Int): BluetoothSocket {
        exempt()
        val m = BluetoothDevice::class.java.getMethod("createL2capSocket", Int::class.javaPrimitiveType)
        return m.invoke(device, psm) as BluetoothSocket
    }

    /** Is the classic link to [device] up? Hidden BluetoothDevice.isConnected(). */
    fun isConnected(device: BluetoothDevice): Boolean = runCatching {
        exempt()
        BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean
    }.getOrDefault(false)
}

/**
 * One session on the AirPods' control channel. [run] connects, sends the opening sequence, then
 * reads until the channel closes (the AirPods went away, or [close] was called).
 */
class AapConnection(private val device: BluetoothDevice) {
    @Volatile private var socket: BluetoothSocket? = null
    private val out = Channel<ByteArray>(Channel.BUFFERED)
    @Volatile private var acked = false

    @SuppressLint("MissingPermission")
    suspend fun run(onOpen: () -> Unit, onEvent: (AapEvent) -> Unit) = coroutineScope {
        val s = withContext(Dispatchers.IO) { L2cap.open(device, Aap.PSM) }
        socket = s
        // connect() blocks and ignores interrupts; closing the socket is what ends it early.
        var timedOut = false
        val timer = launch { delay(8_000); timedOut = true; runCatching { s.close() } }
        try {
            withContext(Dispatchers.IO) { s.connect() }
        } catch (e: IOException) {
            throw if (timedOut) IOException("The AirPods didn't answer in time") else e
        } finally {
            timer.cancel()
        }
        onOpen()

        val writer = launch(Dispatchers.IO) {
            val os = s.outputStream
            for (p in out) { os.write(p); os.flush() }
        }
        val opening = launch {
            // Opening sequence, paced; read everything meanwhile (info can arrive before the ack).
            send(Aap.HANDSHAKE)
            delay(400)
            if (!acked) send(Aap.HANDSHAKE)
            delay(150); send(Aap.FEATURE_FLAGS)
            delay(150); send(Aap.REQUEST_NOTIFICATIONS)
            delay(150); send(Aap.requestKeys())
        }
        withContext(Dispatchers.IO) {
            val buf = ByteArray(maxOf(4096, runCatching { s.maxReceivePacketSize }.getOrDefault(0)))
            val input = s.inputStream
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val e = AapParser.parse(buf.copyOf(n))
                    if (e == AapEvent.HandshakeAck) acked = true
                    onEvent(e)
                }
            } catch (_: IOException) {
                // Closed: the AirPods left, or we closed the socket.
            }
        }
        runCatching { s.close() }
        opening.cancel()
        writer.cancel()
    }

    fun send(packet: ByteArray) { out.trySend(packet) }

    fun close() { runCatching { socket?.close() } }
}
