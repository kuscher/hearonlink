package io.github.kuscher.hearonlink.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.link.LinkService
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.ui.MainActivity
import io.github.kuscher.hearonlink.ui.PanelActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: shows the listening mode and battery. A tap opens the small panel (or, if
 * you prefer, switches to the next press-and-hold mode); a long-press opens the app.
 */
class ModeTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        val link = hearOn.link
        job?.cancel()
        job = scope.launch { link.state.collect(::show) }
        if (!link.state.value.connected && link.hasPermission() && link.aclConnected()) LinkService.start(this)
    }

    override fun onStopListening() { job?.cancel(); job = null }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onClick() {
        val app = hearOn
        if (app.link.state.value.connected && app.prefs.settings.value.tileTapCycles) { app.link.cycleMode(); return }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startActivityAndCollapse(pi)
    }

    private fun show(s: LinkState) {
        val t = qsTile ?: return
        val mode = s.pod.listeningMode
        val lowest = listOfNotNull(s.pod.left.battery?.percent, s.pod.right.battery?.percent, s.pod.single?.percent).minOrNull()
        when {
            s.connected && mode != null && mode != ListeningMode.OFF -> {
                t.state = Tile.STATE_ACTIVE
                t.label = mode.label
                t.subtitle = listOfNotNull(s.name, lowest?.let { "$it %" }).joinToString(" · ")
                t.icon = Icon.createWithResource(this, mode.icon)
            }
            s.connected -> {
                t.state = Tile.STATE_INACTIVE
                t.label = getString(R.string.tile_label)
                t.subtitle = if (mode == ListeningMode.OFF) "Off" else s.name
                t.icon = Icon.createWithResource(this, R.drawable.ic_mode_off)
            }
            else -> {
                t.state = Tile.STATE_INACTIVE
                t.label = s.name
                t.subtitle = "Not connected"
                t.icon = Icon.createWithResource(this, R.drawable.ic_mode_nc)
            }
        }
        t.stateDescription = t.subtitle
        t.updateTile()
    }
}

/** Home-screen widget: battery for left, case and right. */
class BatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = context.hearOn.link.state.value
        provideContent { GlanceTheme { WidgetContent(s) } }
    }

    @Composable
    private fun WidgetContent(s: LinkState) {
        val live = s.connected
        val p = s.pod
        val cells = listOf(
            "Left" to (if (live) p.left.battery?.percent else s.last.left),
            "Case" to (if (live) p.case?.percent else s.last.case),
            "Right" to (if (live) p.right.battery?.percent else s.last.right),
        )
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(22.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                if (live) s.name else "${s.name} · not connected",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { i, (label, v) ->
                    Column(GlanceModifier.defaultWeight()) {
                        Text(v?.let { "$it %" } ?: "–", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                }
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var last = 0L
        fun refresh(context: Context) {
            val now = System.currentTimeMillis()
            if (now - last < 3_000) return
            last = now
            scope.launch { runCatching { BatteryWidget().updateAll(context.applicationContext) } }
        }
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}
