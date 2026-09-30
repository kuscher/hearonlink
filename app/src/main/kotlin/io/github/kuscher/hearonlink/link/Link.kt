package io.github.kuscher.hearonlink.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import io.github.kuscher.hearonlink.aap.Aap
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.aap.hex
import io.github.kuscher.hearonlink.data.LastSeen
import io.github.kuscher.hearonlink.data.Prefs
import io.github.kuscher.hearonlink.gestures.HeadSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class LinkStatus { NO_PERMISSION, NO_DEVICE, BLUETOOTH_OFF, AWAY, CONNECTING, CONNECTED, FAILED }

data class LinkState(
    val status: LinkStatus = LinkStatus.NO_DEVICE,
    val address: String? = null,
    val pod: PodState = PodState(),
    val last: LastSeen = LastSeen(),
    val message: String? = null,
) {
    val connected get() = status == LinkStatus.CONNECTED && pod.handshakeDone
    val name: String get() = pod.info?.name ?: last.name ?: "AirPods"
}

/**
 * The app-wide hub: which AirPods, the live session, and every command. The UI, the tile, the
 * notification and the widget all read [state]; the service keeps a session open while the
 * AirPods are connected.
 */
class Link(private val context: Context, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LinkState(address = prefs.address, last = prefs.lastSeen))
    val state: StateFlow<LinkState> = _state

    /** Every decoded message (sensor samples, conversation, stem presses…). */
    private val _events = MutableSharedFlow<AapEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<AapEvent> = _events

    /** Head motion, only while someone asked for it (see [trackHead]). */
    private val _head = MutableSharedFlow<HeadSample>(extraBufferCapacity = 256)
    val head: SharedFlow<HeadSample> = _head

    private var conn: AapConnection? = null
    private var job: Job? = null
    private val reactions = Reactions(context, prefs, this)

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Bonded devices whose Bluetooth records include Apple's accessory protocol. */
    @SuppressLint("MissingPermission")
    fun bondedAirPods(): List<BluetoothDevice> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty().filter { d -> d.uuids?.any { it.uuid == AAP_UUID } == true }
    }

    fun device(): BluetoothDevice? {
        val all = bondedAirPods()
        val saved = prefs.address
        return all.firstOrNull { it.address == saved } ?: all.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    fun select(address: String) {
        if (address != prefs.address) {
            disconnect()
            prefs.address = address
            prefs.lastSeen = LastSeen(name = bondedAirPods().firstOrNull { it.address == address }?.name)
            prefs.irk = null; prefs.encKey = null
            _state.update { LinkState(address = address, last = prefs.lastSeen) }
        }
        refresh()
    }

    /** Is the classic Bluetooth link to our AirPods up right now? */
    fun aclConnected(): Boolean = device()?.let(L2cap::isConnected) ?: false

    /** Recompute the idle status (no session running). */
    fun refresh() {
        if (job?.isActive == true) return
        val status = when {
            !hasPermission() -> LinkStatus.NO_PERMISSION
            adapter?.isEnabled != true -> LinkStatus.BLUETOOTH_OFF
            device() == null -> LinkStatus.NO_DEVICE
            else -> LinkStatus.AWAY
        }
        _state.update { it.copy(status = status, address = device()?.address ?: it.address, pod = PodState()) }
    }

    /** Open a session if the AirPods are connected. Idempotent. */
    fun connect() {
        if (job?.isActive == true) return
        val d = device() ?: return refresh()
        if (prefs.address == null) prefs.address = d.address
        job = scope.launch {
            _state.update { it.copy(status = LinkStatus.CONNECTING, address = d.address, message = null, pod = PodState()) }
            val c = AapConnection(d)
            conn = c
            var failure: Throwable? = null
            try {
                c.run(onOpen = { _state.update { it.copy(status = LinkStatus.CONNECTED) } }, onEvent = ::onEvent)
            } catch (e: Exception) {
                failure = e
                Log.i(TAG, "session ended: $e")
            } finally {
                conn = null
                headOwners.clear()
                reactions.reset()
            }
            val stillLinked = L2cap.isConnected(d)
            _state.update {
                it.copy(
                    status = if (failure != null && stillLinked) LinkStatus.FAILED else LinkStatus.AWAY,
                    message = failure?.message?.takeIf { stillLinked },
                    pod = PodState(),
                )
            }
        }
    }

    fun disconnect() {
        conn?.close()
        job?.cancel()
    }

    private fun onEvent(e: AapEvent) {
        val before = _state.value.pod
        val pod = before.reduce(e)
        if (pod != before) _state.update { it.copy(pod = pod) }
        when (e) {
            is AapEvent.Keys -> { e.irk?.let { prefs.irk = it }; e.encKey?.let { prefs.encKey = it } }
            is AapEvent.Battery, is AapEvent.Info, is AapEvent.Control -> remember(pod)
            is AapEvent.Sensor -> HeadMotion.decode(e.payload)?.let { _head.tryEmit(it) }
            else -> {}
        }
        if (DEBUG && e !is AapEvent.Sensor) Log.i(TAG, "rx $e")
        reactions.onEvent(e, before, pod)
        _events.tryEmit(e)
    }

    private var lastSave = 0L
    private fun remember(p: PodState) {
        val now = System.currentTimeMillis()
        val l = LastSeen(
            name = p.info?.name ?: _state.value.last.name, model = p.info?.modelNumber ?: _state.value.last.model,
            left = p.left.battery?.percent, right = p.right.battery?.percent, case = p.case?.percent ?: _state.value.last.case,
            leftCharging = p.left.battery?.charging == true, rightCharging = p.right.battery?.charging == true,
            caseCharging = p.case?.charging == true, mode = p.listeningMode?.code, at = now,
        )
        _state.update { it.copy(last = l) }
        if (now - lastSave > 5_000) { lastSave = now; prefs.lastSeen = l }
    }

    fun saveNow() { prefs.lastSeen = _state.value.last }

    // ---- commands ----------------------------------------------------------------------------

    fun sendRaw(packet: ByteArray): Boolean {
        val c = conn ?: return false
        if (DEBUG) Log.i(TAG, "tx ${packet.hex()}")
        c.send(packet); return true
    }

    /** Send a control and show it right away (the AirPods echo the value when they apply it). */
    fun setControl(id: Int, vararg value: Int) {
        if (!sendRaw(Aap.control(id, *value))) return
        _state.update { it.copy(pod = it.pod.copy(controls = it.pod.controls + (id to value.toList()))) }
    }

    fun setFlag(id: Int, on: Boolean) = setControl(id, if (on) Control.ON else Control.OFF)

    fun setMode(m: ListeningMode) {
        // Off only works once the AirPods allow it in the cycle.
        if (m == ListeningMode.OFF && _state.value.pod.flag(Control.ALLOW_OFF) != true) setFlag(Control.ALLOW_OFF, true)
        setControl(Control.LISTENING_MODE, m.code)
    }

    fun cycleMode() = setMode(_state.value.pod.nextMode())

    fun setCycle(modes: Set<ListeningMode>) {
        if (modes.size < 2) return
        if (ListeningMode.OFF in modes) setFlag(Control.ALLOW_OFF, true)
        setControl(Control.LISTENING_CYCLE, ListeningMode.cycleMask(modes))
    }

    fun rename(name: String) {
        if (name.isBlank() || !sendRaw(Aap.rename(name.trim()))) return
        _state.update { s -> s.copy(pod = s.pod.copy(info = s.pod.info?.copy(name = name.trim()))) }
    }

    // ---- head tracking -----------------------------------------------------------------------

    private val headOwners = HashSet<String>()
    private var seq = 1

    /** Head motion streams only while at least one owner (the demo, a ringing call) wants it. */
    @Synchronized
    fun trackHead(owner: String, on: Boolean) {
        val was = headOwners.isNotEmpty()
        if (on) headOwners += owner else headOwners -= owner
        val now = headOwners.isNotEmpty()
        if (was == now) return
        val service = if ((_state.value.pod.info?.build?.firstOrNull()?.digitToIntOrNull() ?: 8) >= 8) Aap.SENSOR_DEVMOTION else Aap.SENSOR_ACTIVITY
        sendRaw(Aap.sensorStream(seq++, service, if (now) Aap.HEAD_TRACKING_INTERVAL_US else 0))
    }

    companion object {
        const val TAG = "HearOnLink"
        val AAP_UUID: UUID = UUID.fromString(Aap.SERVICE_UUID)
        var DEBUG = false
        fun now() = SystemClock.elapsedRealtime()
    }
}
