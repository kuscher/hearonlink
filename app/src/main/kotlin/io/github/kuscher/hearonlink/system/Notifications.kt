package io.github.kuscher.hearonlink.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.link.LinkStatus
import io.github.kuscher.hearonlink.ui.MainActivity

val ListeningMode.label: String get() = when (this) {
    ListeningMode.OFF -> "Off"
    ListeningMode.TRANSPARENCY -> "Transparency"
    ListeningMode.ADAPTIVE -> "Adaptive"
    ListeningMode.NOISE_CANCELLATION -> "Noise Cancellation"
}

val ListeningMode.icon: Int get() = when (this) {
    ListeningMode.OFF -> R.drawable.ic_mode_off
    ListeningMode.TRANSPARENCY -> R.drawable.ic_mode_tr
    ListeningMode.ADAPTIVE -> R.drawable.ic_mode_ad
    ListeningMode.NOISE_CANCELLATION -> R.drawable.ic_mode_nc
}

/** "Left 100 % · Right 98 % · Case 72 %", skipping what we don't know. */
fun batteryLine(p: PodState): String = listOfNotNull(
    p.left.battery?.let { "Left ${it.percent} %" },
    p.right.battery?.let { "Right ${it.percent} %" },
    p.single?.let { "${it.percent} %" },
    p.case?.let { "Case ${it.percent} %" },
).joinToString(" · ")

object Notifications {
    const val CH_CONNECTED = "connected"
    const val CH_ALERTS = "alerts"
    const val ID_CONNECTED = 1
    private const val ID_LOW = 2
    private const val ID_NEARBY = 3

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_CONNECTED, "While connected", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Battery and listening mode while your AirPods are connected"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Low battery, and your AirPods' case opened nearby"
        })
    }

    private fun open(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun key(s: LinkState) = "${s.status}|${s.name}|${s.pod.listeningMode}|${batteryLine(s.pod)}"

    /** The foreground-service notification: current mode, battery as metrics, one-tap mode actions. */
    fun connected(context: Context, s: LinkState): Notification {
        val p = s.pod
        val mode = p.listeningMode
        val b = Notification.Builder(context, CH_CONNECTED)
            .setSmallIcon(mode?.icon ?: R.drawable.ic_notify)
            .setContentIntent(open(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setSubText(s.name)
        when {
            s.connected -> {
                b.setContentTitle(mode?.label ?: s.name)
                b.setContentText(batteryLine(p))
                if (Build.VERSION.SDK_INT >= 37) metrics(b, p)
                val modes = ListeningMode.DISPLAY_ORDER.filter { it != mode && (it != ListeningMode.OFF || p.flag(io.github.kuscher.hearonlink.aap.Control.ALLOW_OFF) == true) }
                if (p.listeningMode != null) for (m in modes.takeLast(3)) b.addAction(
                    Notification.Action.Builder(Icon.createWithResource(context, m.icon), m.label, Actions.mode(context, m)).build(),
                )
            }
            s.status == LinkStatus.CONNECTING -> b.setContentTitle("Connecting to ${s.name}…")
            else -> b.setContentTitle(s.name).setContentText("Not connected")
        }
        return b.build()
    }

    @android.annotation.TargetApi(37)
    private fun metrics(b: Notification.Builder, p: PodState) {
        val style = Notification.MetricStyle()
        fun add(label: String, v: Int?) { if (v != null) style.addMetric(Notification.Metric(Notification.Metric.FixedInt(v, "%"), label)) }
        add("Left", p.left.battery?.percent); add("Right", p.right.battery?.percent)
        add("Battery", p.single?.percent); add("Case", p.case?.percent)
        if (style.metrics.isNotEmpty()) b.setStyle(style)
    }

    fun lowBattery(context: Context, which: String, percent: Int, p: PodState) {
        val side = when (which) { "left" -> "Left AirPod"; "right" -> "Right AirPod"; else -> p.displayName }
        val other = when (which) {
            "left" -> p.right.battery?.let { " The right one is at ${it.percent} %." }
            "right" -> p.left.battery?.let { " The left one is at ${it.percent} %." }
            else -> null
        } ?: ""
        post(context, ID_LOW, Notification.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$side at $percent %")
            .setContentText("Charge soon.$other")
            .setContentIntent(open(context))
            .setAutoCancel(true)
            .build())
    }

    fun nearby(context: Context, name: String, left: Int?, right: Int?, case: Int?) {
        val line = listOfNotNull(left?.let { "Left $it %" }, right?.let { "Right $it %" }, case?.let { "Case $it %" }).joinToString(" · ")
        post(context, ID_NEARBY, Notification.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Your $name are nearby")
            .setContentText(line)
            .setContentIntent(open(context))
            .addAction(Notification.Action.Builder(null, "Connect", Actions.connect(context)).build())
            .setAutoCancel(true)
            .setTimeoutAfter(90_000)
            .build())
    }

    fun cancelNearby(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID_NEARBY)

    fun post(context: Context, id: Int, n: Notification) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

/** Notification and tile actions. */
class Actions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.hearOn
        when (intent.action) {
            MODE -> ListeningMode.of(intent.getIntExtra("mode", 0))?.let { app.link.setMode(it) }
            CONNECT -> {
                Notifications.cancelNearby(context)
                connectAudio(context)
            }
        }
    }

    companion object {
        const val MODE = "io.github.kuscher.hearonlink.MODE"
        const val CONNECT = "io.github.kuscher.hearonlink.CONNECT"

        fun mode(context: Context, m: ListeningMode): PendingIntent = PendingIntent.getBroadcast(
            context, 100 + m.code, Intent(context, Actions::class.java).setAction(MODE).putExtra("mode", m.code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun connect(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 200, Intent(context, Actions::class.java).setAction(CONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /**
         * Connect the AirPods' audio. Android 17 lets companion apps call BluetoothDevice.connect();
         * elsewhere we open Bluetooth settings.
         */
        @android.annotation.SuppressLint("MissingPermission")
        fun connectAudio(context: Context) {
            val d = context.hearOn.link.device()
            val ok = if (Build.VERSION.SDK_INT >= 37 && d != null) runCatching { d.connect() }.isSuccess else false
            if (!ok) context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
