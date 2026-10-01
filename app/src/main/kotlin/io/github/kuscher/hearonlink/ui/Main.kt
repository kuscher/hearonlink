package io.github.kuscher.hearonlink.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.Feature
import io.github.kuscher.hearonlink.data.Settings
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.link.Companion
import io.github.kuscher.hearonlink.link.LinkService
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.link.Nearby
import io.github.kuscher.hearonlink.ui.theme.HearOnTheme
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors
import io.github.kuscher.hearonlink.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = hearOn
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle()
            val state by app.link.state.collectAsStateWithLifecycle()
            HearOnTheme(settings) {
                val dark = isDark(settings)
                LaunchedEffect(dark) { Caption.enable(this@MainActivity, !dark) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    AppScreen(state, settings)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val link = hearOn.link
        link.refresh()
        if (link.hasPermission()) {
            Companion.observe(this)
            if (link.aclConnected()) LinkService.start(this)
            Nearby.ensure(this)
        }
    }
}

@Composable
fun AppScreen(s: LinkState, settings: Settings, initialPage: Page = Page.HOME, forced: io.github.kuscher.hearonlink.gestures.Gesture? = null) {
    val context = LocalContext.current
    val app = context.hearOn
    val wide = windowWidthDp() >= 840.dp
    var page by rememberSaveable { mutableStateOf(initialPage) }
    val c = remember(wide) { Ctx(app.link, app.prefs, { page = it }, phone = !wide) }
    BackHandler(page != Page.HOME) { page = Page.HOME }
    val chrome = LocalHearOnColors.current.chrome

    if (!settings.onboarded) {
        Column(Modifier.fillMaxSize()) {
            CaptionSpacer(MaterialTheme.colorScheme.surface)
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Onboarding(s, c)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        CaptionSpacer(chrome)
        Box(Modifier.fillMaxWidth().background(chrome)) { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        // Two panes (a Googlebook; every page but the full-window demo): the header keeps the AirPods'
        // name, and Back sits with the page's title at the top of the settings pane it belongs to.
        val twoPane = wide && page != Page.DEMO
        HeaderRow(chrome, height = if (wide) 52.dp else 64.dp) {
            if (page != Page.HOME && !twoPane) TipIconButton(R.drawable.ic_back, "Back") { page = Page.HOME }
            if (page == Page.HOME || twoPane) DeviceTitle(s, c, big = !wide)
            else Text(page.title, Modifier.padding(start = 4.dp), style = if (wide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (page == Page.DEMO) TextAction("Done") { page = Page.HOME }
            else {
                if (page != Page.SETTINGS) TipIconButton(R.drawable.ic_tune, "HearOn Link settings") { page = Page.SETTINGS }
                MoreMenu(s, c)
            }
        }
        when {
            page == Page.DEMO -> DemoScreen(s, settings, c, wide, forced)
            wide -> Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(452.dp).fillMaxHeight().background(chrome).verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 28.dp),
                ) { DevicePane(s, c) }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageColumn(padding = 40.dp) {
                        if (page == Page.HOME) HomeSettings(s, settings, c)
                        else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (twoPane) Row(Modifier.offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TipIconButton(R.drawable.ic_back, "Back", tint = MaterialTheme.colorScheme.onSurface) { page = Page.HOME }
                                Title(page.title)
                            }
                            PageContent(page, s, settings, c)
                        }
                    }
                }
            }
            page == Page.HOME -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DevicePane(s, c, artWidth = 240.dp)
                PhoneNav(s, settings, c)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            else -> Box(Modifier.fillMaxSize()) {
                PageColumn(padding = 16.dp) { Column { PageContent(page, s, settings, c); Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) } }
            }
        }
    }
}

/** "AirPods Pro ▾ · Connected": the title doubles as the switcher when several AirPods are paired. */
@SuppressLint("MissingPermission")
@Composable
private fun DeviceTitle(s: LinkState, c: Ctx, big: Boolean) {
    var open by remember { mutableStateOf(false) }
    val devices = remember(s.status) { c.link.bondedAirPods() }
    val context = LocalContext.current
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(20.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, maxLines = 1)
                    Glyph(R.drawable.ic_down, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusDot(statusText(s), s.connected)
            }
        }
        DropdownMenu(open, { open = false }) {
            for (d in devices) DropdownMenuItem(
                text = { Text(d.name ?: d.address) },
                leadingIcon = if (d.address == s.address) ({ Glyph(R.drawable.ic_check, size = 18.dp) }) else null,
                onClick = { open = false; c.link.select(d.address); if (c.link.aclConnected()) LinkService.start(context) },
            )
            if (devices.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(text = { Text("Pair new AirPods…") }, onClick = { open = false; context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) })
        }
    }
}

@Composable
private fun MoreMenu(s: LinkState, c: Ctx) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        TipIconButton(R.drawable.ic_more, "More") { open = true }
        DropdownMenu(open, { open = false }) {
            if (s.connected && s.view.has(Feature.HEAD_GESTURES)) DropdownMenuItem(text = { Text("Try head gestures") }, onClick = { open = false; c.go(Page.DEMO) })
            DropdownMenuItem(text = { Text("Bluetooth settings") }, onClick = { open = false; context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) })
            if (!s.connected && c.link.aclConnected()) DropdownMenuItem(text = { Text("Reconnect controls") }, onClick = { open = false; LinkService.start(context) })
            DropdownMenuItem(text = { Text("About these AirPods") }, onClick = { open = false; c.go(Page.ABOUT) })
        }
    }
}

/** The Quick Settings tile's panel: a small window with the essentials. */
class PanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = hearOn
        if (!app.link.state.value.connected && app.link.hasPermission() && app.link.aclConnected()) LinkService.start(this)
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle()
            val s by app.link.state.collectAsStateWithLifecycle()
            HearOnTheme(settings) { PanelContent(s, app.link) { finish() } }
        }
    }
}

/** The panel's content (also used for screenshots). */
@Composable
fun PanelContent(s: LinkState, link: io.github.kuscher.hearonlink.link.Link, close: () -> Unit) {
    val context = LocalContext.current
    val p = s.view
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(400.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.name, style = MaterialTheme.typography.headlineSmall)
                StatusDot(statusText(s), s.connected)
            }
            val live = s.connected
            BatteryTrio(batteryCells(s), big = false)
            if (p.has(Feature.LISTENING_MODES)) ModeGroup(p.listeningMode.takeIf { live }, availableModes(p), enabled = live, height = 68.dp, onSelect = link::setMode)
            if (p.has(Feature.CONVERSATION_AWARENESS)) Group(rows = listOf { m ->
                SwitchRow(m, "Conversation awareness", null, p.conversationAwareness.takeIf { live }) { link.setFlag(Control.CONVERSATION_AWARENESS, it) }
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextAction("Open HearOn Link") {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); close()
                }
                PillButton("Done") { close() }
            }
        }
    }
}
