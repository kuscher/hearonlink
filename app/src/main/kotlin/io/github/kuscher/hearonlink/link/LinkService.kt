package io.github.kuscher.hearonlink.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.service.quicksettings.TileService
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.system.BatteryWidget
import io.github.kuscher.hearonlink.system.ModeTile
import io.github.kuscher.hearonlink.system.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the AirPods' control channel open while they're connected, so ear detection, conversation
 * awareness, the tile and the notification work with the app closed. Stops shortly after the
 * AirPods leave.
 */
class LinkService : LifecycleService() {
    private val link get() = hearOn.link
    private var watching = false
    private var stopJob: Job? = null
    private var retries = 0
    private var shown = ""
    private var tileKey = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        try {
            startForeground(Notifications.ID_CONNECTED, Notifications.connected(this, link.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            Log.w(Link.TAG, "no foreground start: $e")
            stopSelf(); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) { link.disconnect(); stopSelf(); return START_NOT_STICKY }
        link.connect()
        Nearby.ensure(this)
        if (!watching) {
            watching = true
            lifecycleScope.launch { link.state.collect(::onState) }
        }
        return START_NOT_STICKY
    }

    private fun onState(s: LinkState) {
        val key = Notifications.key(s)
        if (key != shown) { shown = key; Notifications.post(this, Notifications.ID_CONNECTED, Notifications.connected(this, s)) }
        val t = "${s.status}|${s.pod.listeningMode}|${s.batteries.lowestBud()}|${s.batteries.case?.percent}"
        if (t != tileKey) {
            tileKey = t
            TileService.requestListeningState(this, ComponentName(this, ModeTile::class.java))
            BatteryWidget.refresh(this)
        }
        when (s.status) {
            LinkStatus.CONNECTED, LinkStatus.CONNECTING -> {
                stopJob?.cancel(); stopJob = null
                if (s.connected) retries = 0
            }
            LinkStatus.FAILED -> if (retries < 3) {
                retries++
                lifecycleScope.launch { delay(2_500L * retries); link.connect() }
            } else scheduleStop()
            else -> scheduleStop()
        }
    }

    private fun scheduleStop() {
        if (stopJob?.isActive == true) return
        stopJob = lifecycleScope.launch {
            delay(12_000)
            if (!link.state.value.connected) {
                link.saveNow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        link.saveNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.kuscher.hearonlink.STOP"

        /** Start (or poke) the service. Allowed from the background thanks to the companion association. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LinkService::class.java)) }
                .onFailure { Log.w(Link.TAG, "can't start the service: $it") }
        }
    }
}

/** The system tells us when the associated AirPods connect (companion-device presence). */
class PresenceService : CompanionDeviceService() {
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED -> LinkService.start(this)
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> hearOn.link.refresh()
        }
    }

    @Deprecated("Called on Android 13–15")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) { LinkService.start(this) }
}

/** Fallback for devices without a companion association: follow the classic Bluetooth link. */
class BtReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val link = context.hearOn.link
        if (d.address != link.device()?.address) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> LinkService.start(context)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> link.refresh()
        }
    }
}
