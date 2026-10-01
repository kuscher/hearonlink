package io.github.kuscher.hearonlink.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kuscher.hearonlink.BuildConfig
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.data.Settings
import io.github.kuscher.hearonlink.link.Nearby
import io.github.kuscher.hearonlink.system.ModeTile

@Composable
fun AppSettingsPage(settings: Settings, c: Ctx) {
    val context = LocalContext.current
    val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        c.prefs.update { it.copy(nearbyAlert = ok) }; Nearby.ensure(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Group("Look", listOf(
            { m ->
                SettingRow(m, "Colours", lead = R.drawable.ic_palette, below = {
                    Choice(listOf("Wallpaper", "HearOn teal"), if (settings.theme == "wallpaper") 0 else 1) { i ->
                        c.prefs.update { it.copy(theme = if (i == 0) "wallpaper" else "teal") }
                    }
                })
            },
            { m ->
                SettingRow(m, "Theme", below = {
                    val opts = listOf("system", "light", "dark")
                    Choice(listOf("System", "Light", "Dark"), opts.indexOf(settings.dark).coerceAtLeast(0)) { i -> c.prefs.update { it.copy(dark = opts[i]) } }
                })
            },
        ))
        Group("Notifications", listOf(
            { m -> SwitchRow(m, "Low battery", "When an AirPod drops to 10 %", settings.lowBattery, R.drawable.ic_bell) { v -> c.prefs.update { it.copy(lowBattery = v) } } },
            { m ->
                SwitchRow(m, "Case opened nearby", "Battery at a glance when you open the case and the AirPods aren't connected yet", settings.nearbyAlert && Nearby.allowed(context)) { v ->
                    if (v && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) scanPermission.launch(Manifest.permission.BLUETOOTH_SCAN)
                    else { c.prefs.update { it.copy(nearbyAlert = v) }; Nearby.ensure(context) }
                }
            },
            { m ->
                NavRow(m, "While connected", "The quiet notification with battery and modes. Hide it in Android's settings if you like.") {
                    context.startActivity(Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, io.github.kuscher.hearonlink.system.Notifications.CH_CONNECTED))
                }
            },
        ))
        Group("Quick Settings", listOf(
            { m -> NavRow(m, "Add the tile", "Listening mode and battery, one tap away", R.drawable.ic_tiles) { addTile(context) } },
            { m -> SwitchRow(m, "Tap switches mode", "Instead of opening the small panel. Long-press always opens HearOn Link.", settings.tileTapCycles) { v -> c.prefs.update { it.copy(tileTapCycles = v) } } },
        ))
        var licences by remember { mutableStateOf(false) }
        Group("About HearOn Link", listOf(
            { m -> SettingRow(m, "Version ${BuildConfig.VERSION_NAME}", "A personal passion project by Alexander Kuscher. Not affiliated with Apple. AirPods is a trademark of Apple Inc.") },
            { m -> NavRow(m, "Open-source licences", "MIT. Credits: LibrePods' protocol research.") { licences = true } },
            { m -> NavRow(m, "Privacy policy", "Nothing leaves this device") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://googlebook.studio/privacy/hearonlink"))) } },
            { m -> NavRow(m, "Source code", "github.com/kuscher/hearonlink") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kuscher/hearonlink"))) } },
        ))
        if (licences) LicencesDialog { licences = false }
        Text("No internet access, no accounts, no tracking. Everything stays on this device.", Modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun addTile(context: android.content.Context) {
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, ModeTile::class.java), context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_mode_nc), context.mainExecutor,
    ) { }
}

/** Every notice and full licence text shipped in assets/licenses, in one scrollable page. */
@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val parts = remember {
        listOf(
            null to "NOTICES.txt",
            "Apache License 2.0" to "Apache-2.0.txt",
            "SIL Open Font License 1.1 (HearOn Sans)" to "OFL-GoogleSans.txt",
            "MIT License (HearOn Link)" to "MIT-HearOnLink.txt",
        ).map { (title, file) ->
            title to (runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault(""))
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = scheme.surface,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f)) {
            Column {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Open-source licences", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TipIconButton(R.drawable.ic_close, "Close") { onDismiss() }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    for ((title, text) in parts) {
                        if (title != null) Text(title, Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
                        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.5.sp, lineHeight = 16.sp), color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
