package io.github.kuscher.hearonlink.link

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import io.github.kuscher.hearonlink.aap.Family
import io.github.kuscher.hearonlink.ble.Proximity
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.system.Notifications

/**
 * "Case opened nearby": a low-power, hardware-filtered BLE scan for Apple's proximity adverts,
 * delivered by the system to [NearbyReceiver] even while the app isn't running. Only adverts whose
 * address resolves with *your* AirPods' identity key count, and their battery is decrypted with
 * the advert key. Both keys come from the AirPods over the control channel.
 */
object Nearby {
    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, NearbyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun scanner(context: Context): BluetoothLeScanner? =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner

    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    /** Scan while the setting is on and we know the keys; otherwise make sure nothing runs. */
    @SuppressLint("MissingPermission")
    fun ensure(context: Context) {
        val app = context.hearOn
        val want = app.prefs.settings.value.nearbyAlert && app.prefs.irk != null && app.prefs.encKey != null
        if (!allowed(context)) return
        val s = scanner(context) ?: return
        runCatching { s.stopScan(intent(context)) }
        if (!want) return
        val filter = ScanFilter.Builder().setManufacturerData(Proximity.APPLE, Proximity.FILTER_DATA, Proximity.FILTER_MASK).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        runCatching { s.startScan(listOf(filter), settings, intent(context)) }
            .onFailure { Log.w(Link.TAG, "BLE scan: $it") }
    }

    private var lastAlert = 0L
    private var lastLidOpen = false

    fun onAdvert(context: Context, r: ScanResult) {
        val app = context.hearOn
        val data = r.scanRecord?.getManufacturerSpecificData(Proximity.APPLE) ?: return
        val advert = Proximity.parse(data) ?: return
        val irk = app.prefs.irk ?: return
        val addr = r.device.address.split(":").map { it.toInt(16).toByte() }.toByteArray()
        if (!Proximity.resolves(addr, irk)) return
        val precise = app.prefs.encKey?.let { Proximity.decrypt(advert, it) }
        val fresh = advert.lidOpen && !lastLidOpen && advert.fromCase
        lastLidOpen = advert.lidOpen
        if (!fresh || app.link.state.value.connected) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlert < 60_000) return
        lastAlert = now
        val family = advert.family.takeIf { it != Family.UNKNOWN }
        Notifications.nearby(
            context, app.link.state.value.name.takeIf { it != "AirPods" } ?: family?.displayName ?: "AirPods",
            precise?.left ?: advert.left, precise?.right ?: advert.right, precise?.case ?: advert.case,
        )
    }
}

class NearbyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java) ?: return
        for (r in results) Nearby.onAdvert(context, r)
    }
}

/** Companion-device association: the system's picker, then presence events for those AirPods. */
object Companion {
    fun manager(context: Context): CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP))
            context.getSystemService(CompanionDeviceManager::class.java) else null

    fun associated(context: Context): Boolean = manager(context)?.myAssociations?.isNotEmpty() == true

    /** Shows Android's device picker filtered to AirPods; [onChooser] launches its dialog. */
    fun associate(context: Context, onChooser: (IntentSender) -> Unit, onDone: (String?) -> Unit, onError: (String) -> Unit) {
        val cdm = manager(context) ?: return onError("This device has no companion-device support")
        val filter = BluetoothDeviceFilter.Builder()
            .addServiceUuid(ParcelUuid(Link.AAP_UUID), null)
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        cdm.associate(request, context.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)
            override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) {
                val app = context.hearOn
                val mac = associationInfo.deviceMacAddress?.toString()?.uppercase()
                app.prefs.associationId = associationInfo.id
                if (mac != null) app.link.select(mac)
                observe(context)
                onDone(mac)
            }
            override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "Couldn't link the AirPods")
        })
    }

    /** Ask for presence events so the service starts when the AirPods connect. */
    fun observe(context: Context) {
        val cdm = manager(context) ?: return
        for (a in cdm.myAssociations) runCatching {
            if (Build.VERSION.SDK_INT >= 36) {
                cdm.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(a.id).build())
            } else {
                @Suppress("DEPRECATION")
                a.deviceMacAddress?.let { cdm.startObservingDevicePresence(it.toString()) }
            }
        }.onFailure { Log.w(Link.TAG, "presence: $it") }
    }
}
