package io.github.kuscher.hearonlink.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.EarState
import io.github.kuscher.hearonlink.aap.Feature
import io.github.kuscher.hearonlink.aap.Level
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.PartLevel
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.aap.Press
import io.github.kuscher.hearonlink.data.Prefs
import io.github.kuscher.hearonlink.data.Settings
import io.github.kuscher.hearonlink.gestures.Sensitivity
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.link.Action
import io.github.kuscher.hearonlink.link.Link
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.link.LinkStatus
import io.github.kuscher.hearonlink.link.SystemActions
import io.github.kuscher.hearonlink.system.icon
import io.github.kuscher.hearonlink.system.label
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors

enum class Page(val title: String) {
    HOME("HearOn Link"), NOISE("Noise control"), PRESSES("Stem presses"), PRESS("Press speed and hold"),
    EAR("Ear detection"), GESTURES("Head gestures"), SOUND("Sound"), CALLS("Calls and microphone"),
    HEALTH("Health"), ABOUT("About these AirPods"), SETTINGS("Settings"), DEMO("Try head gestures"),
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

/** "just now", "12 min ago", "3 h ago", "yesterday". */
fun age(at: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - at) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> "yesterday"
    }
}

/** Battery cells from the cache: live parts show ear state or charging, the rest their age. */
fun batteryCells(s: LinkState): List<Cell> {
    val p = s.pod
    fun note(part: PartLevel?, ear: EarState?): String = when {
        part == null -> ""
        part.live && part.charging -> "Charging"
        part.live -> ear?.let(::earNote) ?: ""
        else -> age(part.at)
    }
    fun lv(part: PartLevel?) = part?.let { Level(it.percent, it.charging) }
    val b = s.batteries
    return if (b.single != null && b.left == null && b.right == null) listOf(Cell("Battery", lv(b.single), note(b.single, null)))
    else listOf(
        Cell("Left", lv(b.left), note(b.left, p.left.ear.takeIf { s.connected }), faded = b.left?.live != true),
        Cell("Case", lv(b.case), note(b.case, null), faded = b.case?.live != true),
        Cell("Right", lv(b.right), note(b.right, p.right.ear.takeIf { s.connected }), faded = b.right?.live != true),
    )
}

/** Switch value for controls the AirPods may not report: while connected, unknown shows as off. */
private fun sw(v: Boolean?, live: Boolean) = if (live) (v ?: false) else v

// ---- the AirPods pane (left on a Googlebook, top on a phone) ------------------------------------

@Composable
fun DevicePane(s: LinkState, c: Ctx, modifier: Modifier = Modifier, artWidth: Dp = 250.dp) {
    val p = s.pod
    val live = s.connected
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PodsArt(Modifier.size(artWidth, artWidth * 0.68f), inCase = !live, dim = !live)
        }
        BatteryTrio(batteryCells(s))
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
        else -> "Not connected" to "Open the case near this device, or connect in Bluetooth settings. Battery shows when each part was last seen."
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

// ---- summaries ---------------------------------------------------------------------------------

private val PRESS_NAMES = mapOf(Press.SINGLE to "Press once", Press.DOUBLE to "Press twice", Press.TRIPLE to "Press three times", Press.LONG to "Press and hold")

private fun pressSummary(st: Settings): String {
    val custom = Press.entries.mapNotNull { p ->
        val l = Action.of(st.pressAction("L", p)); val r = Action.of(st.pressAction("R", p))
        if (l == Action.DEFAULT && r == Action.DEFAULT) null
        else "${PRESS_NAMES[p]}: " + (if (l == r || !st.splitBuds) l.label else "${l.label} / ${r.label}")
    }
    return if (custom.isEmpty()) "AirPods defaults" else custom.joinToString(" · ")
}

private fun gestureSummary(st: Settings, phone: Boolean): String = when {
    st.gesturesAnytime -> "Nod: ${Action.of(st.nodAction).label} · Shake: ${Action.of(st.shakeAction).label}"
    phone && st.gestureCalls -> "Nod to answer calls, shake to decline"
    else -> "Off · try them in the demo"
}

private fun speedSummary(p: PodState): String {
    val speed = listOf("Default", "Slower", "Slowest")[(p.control(Control.PRESS_SPEED) ?: 0).coerceIn(0, 2)]
    return "Press speed: $speed"
}

// ---- settings sections ---------------------------------------------------------------------------

/** The Googlebook's right pane on the home page: settings with the most-used controls inline. */
@Composable
fun HomeSettings(s: LinkState, settings: Settings, c: Ctx) {
    val p = s.view
    val live = s.connected
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (p.has(Feature.LISTENING_MODES) || !live) Group("Noise control", listOf(
            { m -> SettingRow(m, "Press and hold switches between", enabled = live, below = { CycleChips(p, c, live) }) },
            { m -> SwitchRow(m, "Noise Cancellation with one AirPod", "Keep noise control on when you wear only one", if (live) p.oneBudAnc else null) { c.link.setFlag(Control.ONE_BUD_ANC, it) } },
        ))
        Group("Controls", buildList {
            add { m -> NavRow(m, "Stem presses", pressSummary(settings), R.drawable.ic_hold) { c.go(Page.PRESSES) } }
            add { m -> NavRow(m, "Press speed and hold duration", speedSummary(p), R.drawable.ic_tune, enabled = live) { c.go(Page.PRESS) } }
            if (p.has(Feature.VOLUME_SWIPE)) add { m -> SwitchRow(m, "Swipe the stem to change volume", null, if (live) p.volumeSwipe else null, R.drawable.ic_volume) { c.link.setFlag(Control.VOLUME_SWIPE, it) } }
        })
        if (p.has(Feature.HEAD_GESTURES) || !live) Group("Head gestures", listOf(
            { m -> NavRow(m, "Head gestures", gestureSummary(settings, c.phone), R.drawable.ic_head) { c.go(Page.GESTURES) } },
        ))
        Group("Ear detection", buildList {
            add { m -> SwitchRow(m, "Pause when you take an AirPod out", "Plays again when you put it back in", settings.earPause, R.drawable.ic_ear) { v -> c.prefs.update { it.copy(earPause = v, earResume = v) } } }
            if (p.has(Feature.SLEEP_DETECTION)) add { m -> SwitchRow(m, "Pause when you fall asleep", null, sw(p.sleepDetection, live)) { c.link.setFlag(Control.SLEEP_DETECTION, it) } }
            add { m -> NavRow(m, "More ear detection", null) { c.go(Page.EAR) } }
        })
        Group("Sound and calls", buildList {
            add { m -> NavRow(m, "Sound", "EQ, tone volume" + if (p.has(Feature.CASE_SOUNDS)) ", case sounds" else "", R.drawable.ic_volume) { c.go(Page.SOUND) } }
            if (p.has(Feature.CALL_CONTROLS)) add { m -> NavRow(m, "Calls and microphone", callSummary(p), R.drawable.ic_call) { c.go(Page.CALLS) } }
            if (p.has(Feature.HEART_RATE) || p.has(Feature.HEARING_PROTECTION)) add { m -> NavRow(m, "Health", "Heart rate, hearing protection", R.drawable.ic_head) { c.go(Page.HEALTH) } }
        })
        if (p.has(Feature.OPTIMIZED_CHARGING)) Group("Battery", listOf { m ->
            SwitchRow(m, "Optimized charging", "Waits at 80 % when the AirPods expect a long charge", sw(p.optimizedCharging, live)) { c.link.setFlag(Control.OPTIMIZED_CHARGING, it) }
        })
        Group("These AirPods", listOf(
            { m -> NavRow(m, "About these AirPods", "Name, model, firmware", R.drawable.ic_info) { c.go(Page.ABOUT) } },
        ))
    }
}

private fun callSummary(p: PodState) = when (p.callControlsSwapped) {
    true -> "Press once to end a call"; else -> "Press once to mute, twice to end"
}

/** A phone's home list: one row per page. */
@Composable
fun PhoneNav(s: LinkState, settings: Settings, c: Ctx) {
    val p = s.view
    val live = s.connected
    val rows = buildList<@Composable (Modifier) -> Unit> {
        if (!live || p.has(Feature.LISTENING_MODES)) add { m -> NavRow(m, "Noise control", "Press and hold, one-AirPod mode", R.drawable.ic_mode_nc, enabled = live) { c.go(Page.NOISE) } }
        add { m -> NavRow(m, "Stem presses", pressSummary(settings), R.drawable.ic_hold) { c.go(Page.PRESSES) } }
        if (!live || p.has(Feature.HEAD_GESTURES)) add { m -> NavRow(m, "Head gestures", gestureSummary(settings, true), R.drawable.ic_head) { c.go(Page.GESTURES) } }
        add { m -> NavRow(m, "Ear detection", if (settings.earPause) "Pause when you take one out" else "Off", R.drawable.ic_ear) { c.go(Page.EAR) } }
        add { m -> NavRow(m, "Sound", "EQ, tone volume", R.drawable.ic_volume) { c.go(Page.SOUND) } }
        if (p.has(Feature.CALL_CONTROLS)) add { m -> NavRow(m, "Calls and microphone", callSummary(p), R.drawable.ic_call) { c.go(Page.CALLS) } }
        if (p.has(Feature.HEART_RATE) || p.has(Feature.HEARING_PROTECTION)) add { m -> NavRow(m, "Health", "Heart rate, hearing protection", R.drawable.ic_head) { c.go(Page.HEALTH) } }
        add { m -> NavRow(m, "Press speed and hold", speedSummary(p), R.drawable.ic_tune, enabled = live) { c.go(Page.PRESS) } }
        add { m -> NavRow(m, "About these AirPods", "Name, model, firmware", R.drawable.ic_info) { c.go(Page.ABOUT) } }
    }
    Group(rows = rows)
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

/** A row whose value is an action, picked from a menu that opens on click. */
@Composable
fun ActionRow(modifier: Modifier, title: String, current: Action, options: List<Action>, enabled: Boolean = true, onPick: (Action) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SettingRow(Modifier, title, current.label, onClick = { open = true }, enabled = enabled) {
            Glyph(R.drawable.ic_down, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            var lastSystem = false
            for (a in options) {
                if (a.system && !lastSystem) Text("System (needs system actions)", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                lastSystem = a.system
                DropdownMenuItem(
                    text = { Text(a.label) },
                    trailingIcon = if (a == current) ({ Glyph(R.drawable.ic_check, size = 18.dp) }) else null,
                    onClick = { open = false; onPick(a) },
                )
            }
        }
    }
}

/** Shown when a chosen action needs the system-actions service and it's off. */
@Composable
private fun SystemActionsCard(needed: Boolean) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val enabled = remember(lifecycle) { SystemActions.enabled(context) }
    if (!needed || enabled) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Turn on system actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "Show desktop, Overview and the other system actions need HearOn Link's system-actions service. It only performs " +
                "the actions you pick; it doesn't read the screen or your input. If the switch is greyed out, open App info › ⋮ › Allow restricted settings first.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Row { PillButton("Open accessibility settings", R.drawable.ic_open, filled = true) { SystemActions.openSettings(context) } }
    }
}

@Composable
fun PageContent(page: Page, s: LinkState, settings: Settings, c: Ctx) {
    val p = s.view
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
            Group("More", buildList<@Composable (Modifier) -> Unit> {
                add { m -> SwitchRow(m, "Noise Cancellation with one AirPod", null, if (live) p.oneBudAnc else null) { c.link.setFlag(Control.ONE_BUD_ANC, it) } }
                if (p.has(Feature.ADAPTIVE) && p.adaptiveLevel != null) add { m -> SettingRow(m, "Adaptive audio", below = { AdaptiveSlider(p, c) }) }
            })
        }
        Page.PRESSES -> PressesPage(settings, c)
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
            if (p.has(Feature.CROWN)) Group("Digital Crown", listOf { m ->
                SettingRow(m, "Turn to raise the volume", below = {
                    Choice(listOf("Back to front", "Front to back"), if (p.crownReversed == true) 1 else 0, enabled = live) { i -> c.link.setControl(Control.CROWN_DIRECTION, if (i == 1) 1 else 2) }
                })
            })
        }
        Page.EAR -> Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Group("On this device", listOf(
                { m -> SwitchRow(m, "Pause when you take an AirPod out", "HearOn Link pauses what's playing", settings.earPause) { v -> c.prefs.update { it.copy(earPause = v) } } },
                { m -> SwitchRow(m, "Play again when you put it back", "Only if HearOn Link paused it", settings.earResume, enabled = settings.earPause) { v -> c.prefs.update { it.copy(earResume = v) } } },
            ))
            Group("On the AirPods", buildList<@Composable (Modifier) -> Unit> {
                add { m -> SwitchRow(m, "Automatic ear detection", "Sound plays only while the AirPods are in your ears", sw(p.earDetection, live)) { c.link.setFlag(Control.EAR_DETECTION, it) } }
                if (p.has(Feature.SLEEP_DETECTION)) add { m -> SwitchRow(m, "Pause when you fall asleep", null, sw(p.sleepDetection, live)) { c.link.setFlag(Control.SLEEP_DETECTION, it) } }
            })
            if (live) Group("Right now", listOf(
                { m -> SettingRow(m, "Left AirPod", earNote(s.pod.left.ear).ifEmpty { "Unknown" }) },
                { m -> SettingRow(m, "Right AirPod", earNote(s.pod.right.ear).ifEmpty { "Unknown" }) },
            ))
        }
        Page.SOUND -> SoundPage(s, c)
        Page.CALLS -> Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Group("Call controls", listOf { m ->
                SettingRow(m, "Press once during a call to", "Pressing twice does the other", enabled = live, below = {
                    Choice(listOf("Mute", "End the call"), if (p.callControlsSwapped == true) 1 else 0, enabled = live) { i ->
                        val first = p.controls[Control.CALL_CONTROLS]?.firstOrNull() ?: 0
                        c.link.setControl(Control.CALL_CONTROLS, first, if (i == 1) 2 else 3)
                    }
                })
            })
            Group("Microphone", listOf { m ->
                SettingRow(m, "Which AirPod listens", enabled = live, below = {
                    val idx = when (p.micMode) { 2 -> 1; 1 -> 2; else -> 0 }
                    Choice(listOf("Automatic", "Left", "Right"), idx, enabled = live) { i -> c.link.setControl(Control.MIC_MODE, when (i) { 1 -> 2; 2 -> 1; else -> 0 }) }
                })
            })
            if (c.phone) Hint("To answer calls with a nod, see Head gestures.")
        }
        Page.HEALTH -> HealthPage(s, c)
        Page.GESTURES -> GesturesPage(s, settings, c)
        Page.ABOUT -> AboutPage(s, c)
        Page.SETTINGS -> AppSettingsPage(settings, c)
        Page.HOME, Page.DEMO -> {}
    }
}

@Composable
private fun PressesPage(settings: Settings, c: Ctx) {
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Hint("Pick what each press does. Presses left on “AirPods default” stay with the AirPods, so they work the same with your other devices. Custom presses work while HearOn Link is connected.")
        Group(null, listOf { m ->
            SwitchRow(m, "Set left and right separately", null, settings.splitBuds) { v -> c.prefs.update { it.copy(splitBuds = v) } }
        })
        val buds = if (settings.splitBuds) listOf("L" to "Left AirPod", "R" to "Right AirPod") else listOf("B" to "Both AirPods")
        for ((bud, title) in buds) Group(title, Press.entries.map { press ->
            { m: Modifier ->
                ActionRow(m, PRESS_NAMES[press]!!, Action.of(settings.presses["$bud.${press.name}"]), Action.forPresses) { a ->
                    c.prefs.update { st -> st.copy(presses = st.presses + ("$bud.${press.name}" to a.name)) }
                }
            }
        })
        val needsSystem = Press.entries.any { p -> Action.of(settings.pressAction("L", p)).system || Action.of(settings.pressAction("R", p)).system }
        SystemActionsCard(needsSystem)
    }
}

@Composable
private fun GesturesPage(s: LinkState, settings: Settings, c: Ctx) {
    val context = LocalContext.current
    val callPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) c.prefs.update { it.copy(gestureCalls = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_head, size = 44.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nod for yes, shake for no", style = MaterialTheme.typography.headlineSmall)
                Text("Your AirPods feel how your head moves. Head motion is read only while you use gestures, the demo is open or a call rings.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Group("Anytime", listOf(
            { m -> SwitchRow(m, "Use head gestures anytime", "While an AirPod is in your ear. Uses a little more battery.", settings.gesturesAnytime) { v -> c.prefs.update { it.copy(gesturesAnytime = v) } } },
            { m -> ActionRow(m, "Nod", Action.of(settings.nodAction), Action.forGestures, enabled = settings.gesturesAnytime) { a -> c.prefs.update { it.copy(nodAction = a.name) } } },
            { m -> ActionRow(m, "Shake your head", Action.of(settings.shakeAction), Action.forGestures, enabled = settings.gesturesAnytime) { a -> c.prefs.update { it.copy(shakeAction = a.name) } } },
        ))
        SystemActionsCard(settings.gesturesAnytime && (Action.of(settings.nodAction).system || Action.of(settings.shakeAction).system))
        if (c.phone) Group("Calls", listOf { m ->
            SwitchRow(m, "Answer calls with your head", "Nod to accept, shake to decline", settings.gestureCalls) { v ->
                if (v && !context.hearOn.controls.callsAllowed()) callPerms.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS))
                else c.prefs.update { it.copy(gestureCalls = v) }
            }
        })
        Group("Sensitivity", listOf(
            { m ->
                SettingRow(m, "How big a nod or shake has to be", below = {
                    val all = Sensitivity.entries
                    Choice(listOf("Gentle", "Normal", "Firm"), all.indexOf(settings.sensitivity)) { i -> c.prefs.update { it.copy(sensitivity = all[i]) } }
                })
            },
            { m -> SettingRow(m, "Calibration", if (s.cache.headScale != null) "Tuned to these AirPods" else "Not calibrated yet: calibrate in the demo for the best results") },
        ))
        Row { PillButton("Try it and calibrate", R.drawable.ic_play, filled = true) { c.go(Page.DEMO) } }
    }
}

@Composable
private fun SoundPage(s: LinkState, c: Ctx) {
    val p = s.view
    val live = s.connected
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        val eq = s.pod.eq
        if (eq != null) Group("Custom EQ", buildList<@Composable (Modifier) -> Unit> {
            add { m -> SwitchRow(m, "Custom EQ", "Your own bass, mids and treble", eq.on) { on -> c.link.setEq(on, eq.low, eq.mid, eq.high) } }
            for ((label, idx) in listOf("Bass" to 0, "Mids" to 1, "Treble" to 2)) add { m ->
                val value = listOf(eq.low, eq.mid, eq.high)[idx]
                var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
                SettingRow(m, label, enabled = eq.on, below = {
                    Slider(v, { v = it }, valueRange = 0f..100f, enabled = eq.on, onValueChangeFinished = {
                        val b = mutableListOf(eq.low, eq.mid, eq.high).also { it[idx] = v.toInt() }
                        c.link.setEq(true, b[0], b[1], b[2])
                    })
                })
            }
        }) else if (live) Hint("Custom EQ appears here when your AirPods' firmware offers it.")
        Group("Sounds", buildList<@Composable (Modifier) -> Unit> {
            if (p.has(Feature.CASE_SOUNDS)) add { m -> SwitchRow(m, "Case sounds", "The case chimes when charging starts or it's being found", sw(p.caseSounds, live)) { c.link.setFlag(Control.CASE_SOUNDS, it) } }
            if (p.toneVolume != null || live) add { m ->
                var v by remember(p.toneVolume) { mutableFloatStateOf((p.toneVolume ?: 75).toFloat()) }
                SettingRow(m, "Tone volume", "Chimes like connect and low battery", enabled = live, below = {
                    Slider(v, { v = it }, valueRange = 15f..100f, enabled = live, onValueChangeFinished = { c.link.setControl(Control.TONE_VOLUME, v.toInt()) })
                })
            }
        })
    }
}

@Composable
private fun HealthPage(s: LinkState, c: Ctx) {
    val p = s.view
    val live = s.connected
    if (p.has(Feature.HEART_RATE)) DisposableEffect(live) {
        if (live) c.link.trackSensor(Link.Sensor.HEART, "page", true)
        onDispose { c.link.trackSensor(Link.Sensor.HEART, "page", false) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (p.has(Feature.HEART_RATE)) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalHearOnColors.current.card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Heart rate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.heartRate?.let { "$it bpm" } ?: if (live) "Measuring…" else "Connect to measure",
                style = MaterialTheme.typography.displaySmall)
            Hint("Measured while this page is open, with the AirPods in.")
        }
        if (p.has(Feature.HEARING_PROTECTION)) Group("Hearing", listOf { m ->
            SwitchRow(m, "Hearing protection", "Lowers loud surroundings in Transparency and Adaptive", sw(p.hearingProtection, live)) { c.link.setFlag(Control.HEARING_PROTECTION, it) }
        })
    }
}

@Composable
private fun AboutPage(s: LinkState, c: Ctx) {
    val context = LocalContext.current
    val info = s.pod.info ?: s.cache.info
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
        val family = s.view.family
        Group("Details", buildList<@Composable (Modifier) -> Unit> {
            add { m -> SettingRow(m, "Model", listOfNotNull(family.takeIf { it != io.github.kuscher.hearonlink.aap.Family.UNKNOWN }?.displayName, info?.modelNumber).joinToString(" · ").ifEmpty { "Connect to see" }) }
            info?.firmware?.let { fw -> add { m -> SettingRow(m, "Firmware", fw) } }
            val live = s.pod.info
            if (live?.serial != null) add { m ->
                SettingRow(m, "Serial numbers", if (showSerials) listOfNotNull(live.serial, live.leftSerial?.let { "Left $it" }, live.rightSerial?.let { "Right $it" }).joinToString(" · ") else "Hidden",
                    onClick = { showSerials = !showSerials }) { TextAction(if (showSerials) "Hide" else "Show") { showSerials = !showSerials } }
            }
            if (s.cache.lastConnected > 0 && !s.connected) add { m -> SettingRow(m, "Last connected", age(s.cache.lastConnected)) }
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
