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
                SwitchRow(m, "Case opened nearby", "Battery at a glance when you open the case and the AirPods aren't connected yet", settings.nearbyAlert) { v ->
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
        Group("About HearOn Link", listOf(
            { m -> SettingRow(m, "Version ${BuildConfig.VERSION_NAME}", "A personal passion project by Alexander Kuscher. Not affiliated with Apple. AirPods is a trademark of Apple Inc.") },
            { m -> NavRow(m, "Source code and licences", "MIT. Protocol research credit: LibrePods.") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kuscher/hearonlink"))) } },
        ))
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
