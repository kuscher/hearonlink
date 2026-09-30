package io.github.kuscher.hearonlink.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.kuscher.hearonlink.aap.Aap
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.AapParser
import io.github.kuscher.hearonlink.aap.Batteries
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.aap.hex
import io.github.kuscher.hearonlink.data.DeviceCache
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
    /** Live state from the AirPods (empty while not connected). */
    val pod: PodState = PodState(),
    /** What we remember about these AirPods. */
    val cache: DeviceCache = DeviceCache(),
    /** Battery per part: live values plus the last known ones, each with its time. */
    val batteries: Batteries = Batteries(),
    val heartRate: Int? = null,
    val message: String? = null,
) {
    val connected get() = status == LinkStatus.CONNECTED && pod.handshakeDone
    val name: String get() = pod.info?.name ?: cache.name ?: "AirPods"

    /** Head-sensor calibration for the current primary bud (null = uncalibrated defaults). */
    val headCal get() = cache.headCal(pod.leftPrimary)

    /** For display: live state when connected, otherwise what we remember (read-only). */
    val view: PodState get() = if (connected) pod else PodState(info = cache.info, controls = cache.controls)
}

/**
 * The app-wide hub: which AirPods, the live session, and every command. The UI, the tile, the
 * notification and the widget all read [state]; the service keeps a session open while the
 * AirPods are connected.
 */
class Link(private val context: Context, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(initial(prefs.address))
    val state: StateFlow<LinkState> = _state

    /** Every decoded message (sensor frames, conversation, stem presses…). */
    private val _events = MutableSharedFlow<AapEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<AapEvent> = _events

    /** Head motion, only while someone asked for it (see [trackSensor]). */
    private val _head = MutableSharedFlow<HeadSample>(extraBufferCapacity = 256)
    val head: SharedFlow<HeadSample> = _head

    private var conn: AapConnection? = null
    private var job: Job? = null
    private val reactions = Reactions(context, prefs, this)

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun initial(address: String?): LinkState {
        val c = prefs.cache(address)
        val now = System.currentTimeMillis()
        return LinkState(address = address, cache = c, batteries = c.batteries.expire(now))
    }

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Bonded devices whose Bluetooth records include Apple's accessory protocol (AirPods, many Beats). */
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
            saveNow()
            prefs.address = address
            prefs.irk = null; prefs.encKey = null
            var c = prefs.cache(address)
            if (c.name == null) c = c.copy(name = bondedAirPods().firstOrNull { it.address == address }?.name)
            _state.value = initial(address).copy(cache = c)
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
        val addr = device()?.address ?: _state.value.address
        if (addr != null && addr != _state.value.address) { prefs.address = addr; _state.value = initial(addr) }
        _state.update { it.copy(status = status, pod = PodState(), batteries = it.batteries.expire(System.currentTimeMillis())) }
    }

    /** Open a session if the AirPods are connected. Idempotent. */
    fun connect() {
        if (job?.isActive == true) return
        val d = device() ?: return refresh()
        if (prefs.address == null) prefs.address = d.address
        if (_state.value.address != d.address) _state.value = initial(d.address)
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
                synchronized(this@Link) { owners.clear() }
                reactions.reset()
            }
            val stillLinked = L2cap.isConnected(d)
            _state.update {
                it.copy(
                    status = if (failure != null && stillLinked) LinkStatus.FAILED else LinkStatus.AWAY,
                    message = failure?.message?.takeIf { stillLinked },
                    pod = PodState(), heartRate = null,
                    batteries = it.batteries.stale(),
                )
            }
            saveNow()
        }
    }

    fun disconnect() {
        conn?.close()
        job?.cancel()
    }

    private fun onEvent(e: AapEvent) {
        val before = _state.value.pod
        val pod = before.reduce(e)
        val now = System.currentTimeMillis()
        if (pod != before) _state.update { it.copy(pod = pod) }
        when (e) {
            is AapEvent.Keys -> { e.irk?.let { prefs.irk = it }; e.encKey?.let { prefs.encKey = it } }
            is AapEvent.Battery -> _state.update { it.copy(batteries = it.batteries.fromAap(e.readings, now)) }
            is AapEvent.Info -> _state.update {
                it.copy(cache = it.cache.copy(name = e.info.name ?: it.cache.name, model = e.info.modelNumber ?: it.cache.model,
                    firmware = e.info.firmware ?: it.cache.firmware, build = e.info.build ?: it.cache.build, lastConnected = now))
            }
            is AapEvent.Control -> _state.update { it.copy(cache = it.cache.copy(controls = pod.controls)) }
            is AapEvent.Sensor -> {
                AapParser.heartRate(e)?.let { bpm -> _state.update { it.copy(heartRate = bpm) } }
                if (e.service == Aap.SENSOR_DEVMOTION || e.service == Aap.SENSOR_ACTIVITY) {
                    val cal = _state.value.headCal
                    HeadMotion.decode(e.payload, cal?.vertical, cal?.horizontal)?.let { _head.tryEmit(it) }
                }
            }
            else -> {}
        }
        if (DEBUG && e !is AapEvent.Sensor) Log.i(TAG, "rx $e")
        reactions.onEvent(e, before, pod)
        _events.tryEmit(e)
        if (e is AapEvent.Battery || e is AapEvent.Info) save(now)
    }

    /** A proximity advert from our AirPods (see Nearby): fills in levels the channel can't see. */
    fun onAdvert(left: Int?, right: Int?, case: Int?, lc: Boolean, rc: Boolean, cc: Boolean, precise: Boolean) {
        val now = System.currentTimeMillis()
        _state.update { it.copy(batteries = it.batteries.fromAdvert(left, right, case, lc, rc, cc, precise, now)) }
        save(now)
    }

    fun updateCache(f: (DeviceCache) -> DeviceCache) { _state.update { it.copy(cache = f(it.cache)) }; saveNow() }

    private var lastSave = 0L
    private fun save(now: Long) { if (now - lastSave > 5_000) { lastSave = now; saveNow() } }

    fun saveNow() {
        val s = _state.value
        prefs.saveCache(s.address, s.cache.with(s.batteries))
    }

    // ---- commands ----------------------------------------------------------------------------

    fun sendRaw(packet: ByteArray): Boolean {
        val c = conn ?: return false
        if (DEBUG) Log.i(TAG, "tx ${packet.hex()}")
        c.send(packet); return true
    }

    /**
     * Send a control and show it right away: the AirPods apply writes without echoing them. The value
     * is remembered too, so settings the AirPods never report still show what you chose.
     */
    fun setControl(id: Int, vararg value: Int) {
        if (!sendRaw(Aap.control(id, *value))) return
        _state.update {
            val controls = it.pod.controls + (id to value.toList())
            it.copy(pod = it.pod.copy(controls = controls), cache = it.cache.copy(controls = it.cache.controls + (id to value.toList())))
        }
        saveNow()
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

    fun setEq(on: Boolean, low: Int, mid: Int, high: Int) {
        val template = _state.value.pod.eq?.raw
        if (!sendRaw(Aap.eq(template, on, low, mid, high))) return
        val raw = Aap.eq(template, on, low, mid, high).copyOfRange(6, 13)
        _state.update { it.copy(pod = it.pod.copy(eq = AapEvent.Eq(on, low, mid, high, raw))) }
    }

    fun rename(name: String) {
        if (name.isBlank() || !sendRaw(Aap.rename(name.trim()))) return
        _state.update { s -> s.copy(pod = s.pod.copy(info = s.pod.info?.copy(name = name.trim())), cache = s.cache.copy(name = name.trim())) }
        saveNow()
    }

    // ---- sensor streams (head motion, heart rate) --------------------------------------------

    private val owners = HashMap<Int, MutableSet<String>>()
    private var seq = 1

    /**
     * Sensor streams run only while at least one owner wants them: head motion for the demo, a
     * ringing call or head gestures; heart rate while its page is open.
     */
    @Synchronized
    fun trackSensor(kind: Sensor, owner: String, on: Boolean) {
        val service = when (kind) {
            Sensor.HEAD -> if (buildMajor() >= 8) Aap.SENSOR_DEVMOTION else Aap.SENSOR_ACTIVITY
            Sensor.HEART -> if (buildMajor() >= 9) Aap.SENSOR_HEART_RATE_CMD else Aap.SENSOR_HEART_RATE
        }
        val set = owners.getOrPut(service) { HashSet() }
        val was = set.isNotEmpty()
        if (on) set += owner else set -= owner
        val now = set.isNotEmpty()
        if (was == now) return
        val interval = if (kind == Sensor.HEAD) Aap.HEAD_TRACKING_INTERVAL_US else Aap.HEART_RATE_INTERVAL_US
        sendRaw(Aap.sensorStream(seq++, service, if (now) interval else 0))
        if (!now && kind == Sensor.HEART) _state.update { it.copy(heartRate = null) }
    }

    fun trackHead(owner: String, on: Boolean) = trackSensor(Sensor.HEAD, owner, on)

    private fun buildMajor(): Int = (_state.value.pod.info?.build ?: _state.value.cache.build)?.firstOrNull()?.digitToIntOrNull() ?: 8

    enum class Sensor { HEAD, HEART }

    companion object {
        const val TAG = "HearOnLink"
        val AAP_UUID: UUID = UUID.fromString(Aap.SERVICE_UUID)
        var DEBUG = false
    }
}
