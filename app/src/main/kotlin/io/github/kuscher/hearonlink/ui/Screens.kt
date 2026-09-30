package io.github.kuscher.hearonlink.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.EarState
import io.github.kuscher.hearonlink.aap.Feature
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.data.Prefs
import io.github.kuscher.hearonlink.data.Settings
import io.github.kuscher.hearonlink.link.Link
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.link.LinkStatus
import io.github.kuscher.hearonlink.system.icon
import io.github.kuscher.hearonlink.system.label
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors
import java.text.DateFormat
import java.util.Date

enum class Page(val title: String) {
    HOME("HearOn Link"), NOISE("Noise control"), PRESS("Press and hold"), EAR("Ear detection"),
    GESTURES("Head gestures"), ABOUT("About these AirPods"), SETTINGS("Settings"), DEMO("Try head gestures"),
}

/** Everything the screens need to act. */
class Ctx(val link: Link, val prefs: Prefs, val go: (Page) -> Unit, val phone: Boolean)

private fun earNote(e: EarState) = when (e) {
    EarState.IN_EAR -> "In ear"; EarState.IN_CASE -> "In case"; EarState.OUT_OF_EAR -> "Out"; else -> ""
}

fun statusText(s: LinkState): String = when (s.status) {
    LinkStatus.CONNECTED -> if (s.pod.handshakeDone) "Connected" else "Connecting…"
    LinkStatus.CONNECTING -> "Connecting…"
    LinkStatus.NO_PERMISSION -> "Needs Nearby devices permission"
    LinkStatus.NO_DEVICE -> "No AirPods paired"
    LinkStatus.BLUETOOTH_OFF -> "Bluetooth is off"
    LinkStatus.FAILED -> "Couldn't connect"
    LinkStatus.AWAY -> "Not connected"
}

fun availableModes(p: PodState): List<ListeningMode> = buildList {
    add(ListeningMode.OFF); add(ListeningMode.TRANSPARENCY)
    if (p.has(Feature.ADAPTIVE)) add(ListeningMode.ADAPTIVE)
    add(ListeningMode.NOISE_CANCELLATION)
}

// ---- the AirPods pane (left on a Googlebook, top on a phone) ------------------------------------

@Composable
fun DevicePane(s: LinkState, c: Ctx, modifier: Modifier = Modifier, artWidth: Dp = 250.dp) {
    val p = s.pod
    val live = s.connected
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PodsArt(Modifier.size(artWidth, artWidth * 0.68f), inCase = !live, dim = !live)
        }
        val cells = if (live) listOf(
            Cell("Left", p.left.battery, earNote(p.left.ear)),
            Cell("Case", p.case, if (p.case?.charging == true) "Charging" else ""),
            Cell("Right", p.right.battery, earNote(p.right.ear)),
        ) else {
            val at = s.last.at.takeIf { it > 0 }?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: ""
            listOf(Cell("Left", s.last.leftLevel, at), Cell("Case", s.last.caseLevel, at), Cell("Right", s.last.rightLevel, at))
        }
        BatteryTrio(cells, big = !c.phone || true, faded = !live)
        if (!live) AwayCard(s, c)
        if (live && p.has(Feature.LISTENING_MODES)) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Listening mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModeGroup(p.listeningMode, availableModes(p), onSelect = c.link::setMode)
            if (p.listeningMode == ListeningMode.ADAPTIVE && p.adaptiveLevel != null) AdaptiveSlider(p, c)
        }
        if (live) {
            val rows = buildList<@Composable (Modifier) -> Unit> {
                if (p.has(Feature.CONVERSATION_AWARENESS)) add { m -> SwitchRow(m, "Conversation awareness", "Turns media down when you speak", p.conversationAwareness) { c.link.setFlag(Control.CONVERSATION_AWARENESS, it) } }
                if (p.has(Feature.PERSONALIZED_VOLUME)) add { m -> SwitchRow(m, "Personalized volume", "Adjusts to your surroundings", p.personalizedVolume) { c.link.setFlag(Control.PERSONALIZED_VOLUME, it) } }
            }
            if (rows.isNotEmpty()) Group(rows = rows)
        }
    }
}

@Composable
private fun AdaptiveSlider(p: PodState, c: Ctx) {
    var v by remember(p.adaptiveLevel) { mutableFloatStateOf((p.adaptiveLevel ?: 50).toFloat()) }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Slider(value = v, onValueChange = { v = it }, valueRange = 0f..100f, onValueChangeFinished = { c.link.setControl(Control.ADAPTIVE_LEVEL, v.toInt()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Less noise", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("More noise", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AwayCard(s: LinkState, c: Ctx) {
    val context = LocalContext.current
    val (title, text) = when (s.status) {
        LinkStatus.NO_DEVICE -> "No AirPods yet" to "Pair your AirPods in Bluetooth settings: open the case, hold its button until the light flashes white, then pick them."
        LinkStatus.BLUETOOTH_OFF -> "Bluetooth is off" to "Turn Bluetooth on to use your AirPods."
        LinkStatus.FAILED -> "Couldn't reach the AirPods' controls" to ((s.message?.let { "$it. " } ?: "") + "If another AirPods app is running (CAPod, LibrePods), close it and try again.")
        LinkStatus.CONNECTING -> "Connecting…" to "Talking to your AirPods."
        else -> "Not connected" to "Open the case near this device, or connect in Bluetooth settings." +
            (if (s.last.at > 0) " Battery levels are from the time shown." else "")
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalHearOnColors.current.card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.status == LinkStatus.FAILED) PillButton("Try again", filled = true) { c.link.connect() }
            PillButton("Bluetooth settings", R.drawable.ic_open) {
                context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))
            }
        }
    }
}

// ---- settings sections ---------------------------------------------------------------------------

/** The Googlebook's right pane on the home page: settings with the most-used controls inline. */
@Composable
fun HomeSettings(s: LinkState, settings: Settings, c: Ctx) {
    val p = s.pod
    val live = s.connected
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (!live || p.has(Feature.LISTENING_MODES)) Group("Noise control", listOf(
            { m -> SettingRow(m, "Press and hold switches between", enabled = live, below = { CycleChips(p, c, live) }) },
            { m -> SwitchRow(m, "Noise Cancellation with one AirPod", "Keep noise control on when you wear only one", if (live) p.oneBudAnc else null) { c.link.setFlag(Control.ONE_BUD_ANC, it) } },
        ))
        if (!live || p.has(Feature.HEAD_GESTURES)) Group("Head gestures", listOf(
            { m -> NavRow(m, "Try head gestures", if (c.phone) "Nod for yes, shake for no, live" else "Nod for yes, shake for no, live. Answering calls this way works on phones.", R.drawable.ic_head, enabled = live) { c.go(Page.DEMO) } },
        ))
        Group("Ear detection", listOf(
            { m -> SwitchRow(m, "Pause when you take an AirPod out", "Plays again when you put it back in", settings.earPause, R.drawable.ic_ear) { v -> c.prefs.update { it.copy(earPause = v, earResume = v) } } },
        ))
        Group("Press and hold", listOf(
            { m -> NavRow(m, "Press speed and hold duration", pressSummary(p), R.drawable.ic_hold, enabled = live) { c.go(Page.PRESS) } },
            { m -> SwitchRow(m, "Swipe the stem to change volume", null, if (live && p.has(Feature.VOLUME_SWIPE)) p.volumeSwipe else null, R.drawable.ic_volume) { c.link.setFlag(Control.VOLUME_SWIPE, it) } },
        ))
        Group("These AirPods", listOf(
            { m -> NavRow(m, "About these AirPods", "Name, model, firmware", R.drawable.ic_info) { c.go(Page.ABOUT) } },
        ))
    }
}

/** A phone's home list: one row per page. */
@Composable
fun PhoneNav(s: LinkState, settings: Settings, c: Ctx) {
    val p = s.pod
    val live = s.connected
    val rows = buildList<@Composable (Modifier) -> Unit> {
        if (!live || p.has(Feature.LISTENING_MODES)) add { m -> NavRow(m, "Noise control", "Press and hold, one-AirPod mode", R.drawable.ic_mode_nc, enabled = live) { c.go(Page.NOISE) } }
        if (!live || p.has(Feature.HEAD_GESTURES)) add { m -> NavRow(m, "Head gestures", if (settings.gestureCalls) "On · Nod to answer calls, shake to decline" else "Nod for yes, shake for no", R.drawable.ic_head) { c.go(Page.GESTURES) } }
        add { m -> NavRow(m, "Ear detection", if (settings.earPause) "Pause when you take one out" else "Off", R.drawable.ic_ear) { c.go(Page.EAR) } }
        add { m -> NavRow(m, "Press and hold", pressSummary(p), R.drawable.ic_hold, enabled = live) { c.go(Page.PRESS) } }
        add { m -> NavRow(m, "About these AirPods", "Name, model, firmware", R.drawable.ic_info) { c.go(Page.ABOUT) } }
    }
    Group(rows = rows)
}

private fun pressSummary(p: PodState): String {
    val speed = listOf("Default", "Slower", "Slowest")[(p.control(Control.PRESS_SPEED) ?: 0).coerceIn(0, 2)]
    return "Press speed: $speed"
}

@Composable
private fun CycleChips(p: PodState, c: Ctx, enabled: Boolean) {
    val cycle = p.cycle ?: setOf(ListeningMode.TRANSPARENCY, ListeningMode.NOISE_CANCELLATION)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in availableModes(p)) {
            val on = m in cycle
            FilterChip(
                selected = on, enabled = enabled,
                onClick = { val next = if (on) cycle - m else cycle + m; if (next.size >= 2) c.link.setCycle(next) },
                label = { Text(m.label) },
                leadingIcon = if (on) ({ Glyph(R.drawable.ic_check, size = 16.dp) }) else null,
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
fun PageContent(page: Page, s: LinkState, settings: Settings, c: Ctx) {
    val p = s.pod
    val live = s.connected
    when (page) {
        Page.NOISE -> Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Hint("Press and hold a stem to switch between the modes you tick. Pick at least two.")
            val cycle = p.cycle ?: setOf(ListeningMode.TRANSPARENCY, ListeningMode.NOISE_CANCELLATION)
            Group("Press and hold switches between", availableModes(p).map { m ->
                { mod: Modifier ->
                    SettingRow(mod, m.label, lead = m.icon, enabled = live,
                        onClick = { val next = if (m in cycle) cycle - m else cycle + m; if (next.size >= 2) c.link.setCycle(next) }) {
                        Checkbox(checked = m in cycle, onCheckedChange = null, enabled = live)
                    }
                }
            })
            Group("More", buildList {
                add { m -> SwitchRow(m, "Noise Cancellation with one AirPod", null, if (live) p.oneBudAnc else null) { c.link.setFlag(Control.ONE_BUD_ANC, it) } }
                if (p.has(Feature.ADAPTIVE) && p.adaptiveLevel != null) add { m -> SettingRow(m, "Adaptive audio", below = { AdaptiveSlider(p, c) }) }
            })
        }
        Page.PRESS -> Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Group("Press speed", listOf { m ->
                SettingRow(m, "How fast to press twice or three times", below = {
                    Choice(listOf("Default", "Slower", "Slowest"), p.control(Control.PRESS_SPEED) ?: 0, enabled = live) { c.link.setControl(Control.PRESS_SPEED, it) }
                })
            })
            Group("Press and hold duration", listOf { m ->
                SettingRow(m, "How long to hold", below = {
                    Choice(listOf("Default", "Shorter", "Shortest"), p.control(Control.HOLD_DURATION) ?: 0, enabled = live) { c.link.setControl(Control.HOLD_DURATION, it) }
                })
            })
            if (p.has(Feature.VOLUME_SWIPE)) Group("Volume", listOf(
                { m -> SwitchRow(m, "Swipe the stem to change volume", null, if (live) p.volumeSwipe else null) { c.link.setFlag(Control.VOLUME_SWIPE, it) } },
                { m ->
                    SettingRow(m, "Swipe speed", enabled = live && p.volumeSwipe == true, below = {
                        Choice(listOf("Default", "Longer", "Longest"), p.control(Control.SWIPE_INTERVAL) ?: 0, enabled = live && p.volumeSwipe == true) { c.link.setControl(Control.SWIPE_INTERVAL, it) }
                    })
                },
            ))
            if (p.toneVolume != null) Group("Sounds", listOf { m ->
                var v by remember(p.toneVolume) { mutableFloatStateOf((p.toneVolume ?: 75).toFloat()) }
                SettingRow(m, "Tone volume", "Chimes like connect and low battery", below = {
                    Slider(v, { v = it }, valueRange = 15f..100f, enabled = live, onValueChangeFinished = { c.link.setControl(Control.TONE_VOLUME, v.toInt()) })
                })
            })
        }
        Page.EAR -> Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Group(null, listOf(
                { m -> SwitchRow(m, "Pause when you take an AirPod out", "HearOn Link pauses what's playing", settings.earPause) { v -> c.prefs.update { it.copy(earPause = v) } } },
                { m -> SwitchRow(m, "Play again when you put it back", "Only if HearOn Link paused it", settings.earResume, enabled = settings.earPause) { v -> c.prefs.update { it.copy(earResume = v) } } },
            ))
            if (live) Group("Right now", listOf(
                { m -> SettingRow(m, "Left AirPod", earNote(p.left.ear).ifEmpty { "Unknown" }) },
                { m -> SettingRow(m, "Right AirPod", earNote(p.right.ear).ifEmpty { "Unknown" }) },
            ))
        }
        Page.GESTURES -> GesturesPage(s, settings, c)
        Page.ABOUT -> AboutPage(s, c)
        Page.SETTINGS -> AppSettingsPage(settings, c)
        Page.HOME, Page.DEMO -> {}
    }
}

@Composable
private fun GesturesPage(s: LinkState, settings: Settings, c: Ctx) {
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_head, size = 44.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nod for yes, shake for no", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Your AirPods feel how your head moves." +
                        (if (c.phone) " HearOn Link can answer and decline calls this way." else " On a phone, HearOn Link can answer and decline calls this way.") +
                        " Head motion is read only while the demo is open or a call rings.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (c.phone) Group(null, listOf(
            { m -> SwitchRow(m, "Answer calls with your head", "Nod to accept, shake to decline", settings.gestureCalls) { v -> c.prefs.update { it.copy(gestureCalls = v) } } },
        )) else Group(null, listOf(
            { m -> SettingRow(m, "Nod", "Yes: accept a call on your phone", R.drawable.ic_check) },
            { m -> SettingRow(m, "Shake", "No: decline it", R.drawable.ic_close) },
        ))
        Group("Sensitivity", listOf { m ->
            SettingRow(m, "How big a nod or shake has to be", below = {
                val all = io.github.kuscher.hearonlink.gestures.Sensitivity.entries
                Choice(listOf("Gentle", "Normal", "Firm"), all.indexOf(settings.sensitivity)) { i -> c.prefs.update { it.copy(sensitivity = all[i]) } }
            })
        })
        Row { PillButton("Try it", R.drawable.ic_play, filled = true) { c.go(Page.DEMO) } }
    }
}

@Composable
private fun AboutPage(s: LinkState, c: Ctx) {
    val context = LocalContext.current
    val info = s.pod.info
    var name by remember(info?.name) { mutableStateOf(info?.name ?: s.name) }
    var showSerials by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Group("Name", listOf { m ->
            SettingRow(m, "Shown on your devices", enabled = s.connected, below = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it.take(32) }, Modifier.weight(1f), singleLine = true, enabled = s.connected)
                    if (s.connected && name.isNotBlank() && name != info?.name) PillButton("Rename", filled = true) { c.link.rename(name) }
                }
            })
        })
        Group("Details", buildList {
            add { m -> SettingRow(m, "Model", listOfNotNull(s.pod.family.takeIf { info != null }?.displayName, info?.modelNumber ?: s.last.model).joinToString(" · ").ifEmpty { "Connect to see" }) }
            info?.firmware?.let { fw -> add { m -> SettingRow(m, "Firmware", fw) } }
            if (info?.serial != null) add { m ->
                SettingRow(m, "Serial numbers", if (showSerials) listOfNotNull(info.serial, info.leftSerial?.let { "Left $it" }, info.rightSerial?.let { "Right $it" }).joinToString(" · ") else "Hidden",
                    onClick = { showSerials = !showSerials }) { TextAction(if (showSerials) "Hide" else "Show") { showSerials = !showSerials } }
            }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Bluetooth settings", R.drawable.ic_open) { context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        Hint("Renaming shows on other devices after they reconnect.")
    }
}

/** A column that scrolls, centred with a comfortable reading width. */
@Composable
fun PageColumn(maxWidth: Dp = 680.dp, padding: Dp = 24.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = padding, vertical = 20.dp)) { content() }
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight(700)))

@Composable
fun FullHeight(content: @Composable () -> Unit) = Box(Modifier.fillMaxHeight()) { content() }
