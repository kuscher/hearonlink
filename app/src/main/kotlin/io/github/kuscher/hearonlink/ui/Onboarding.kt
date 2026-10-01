package io.github.kuscher.hearonlink.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.link.Companion
import io.github.kuscher.hearonlink.link.LinkService
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors

/** First run: permission, pick the AirPods (system picker), the tile, notifications. */
@SuppressLint("MissingPermission")
@Composable
fun Onboarding(s: LinkState, c: Ctx) {
    val context = LocalContext.current
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    var refresh by remember { mutableIntStateOf(0) }
    val hasBt = remember(refresh) { granted(Manifest.permission.BLUETOOTH_CONNECT) }
    var chosen by remember { mutableStateOf(c.prefs.address != null && Companion.associated(context)) }
    var tileDone by remember { mutableStateOf(false) }
    val hasNotif = remember(refresh) { granted(Manifest.permission.POST_NOTIFICATIONS) }
    var error by remember { mutableStateOf<String?>(null) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++; c.link.refresh() }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    fun finish() {
        c.prefs.update { it.copy(onboarded = true) }
        if (c.link.aclConnected()) LinkService.start(context)
    }

    val step = when { !hasBt -> 1; !chosen -> 2; !tileDone -> 3; !hasNotif -> 4; else -> 5 }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PodsArt() }
            Text("Set up your AirPods", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text("A few quick steps. Nothing leaves this device.", Modifier.fillMaxWidth().padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Step(1, "Allow Nearby devices", "HearOn Link talks to your AirPods over Bluetooth. It never uses your location.", step) {
                PillButton("Allow", filled = true) { perms.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }
            }
            Step(2, "Choose your AirPods", "Pick them here, then confirm in Android's dialog. HearOn Link talks only to the AirPods you choose.", step) {
                val bonded = remember(refresh, hasBt) { c.link.bondedAirPods() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (bonded.isEmpty()) {
                        Hint("No AirPods are paired yet. Open the case, hold its button until the light flashes white, then pair them in Bluetooth settings.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Bluetooth settings", R.drawable.ic_open) { context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) }
                            TextAction("Check again") { refresh++ }
                        }
                    } else {
                        // One button per paired pair of AirPods; Android then asks to confirm that one.
                        for ((i, d) in bonded.withIndex()) PillButton(d.name ?: d.address, R.drawable.ic_bt, filled = i == 0) {
                            error = null
                            Companion.associate(context, d.address,
                                onChooser = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
                                onDone = { chosen = true },
                                onError = { e -> error = e },
                            )
                        }
                        if (error != null) {
                            // Cancelled, or no companion support: they still work, without the background extras.
                            Hint("Android didn't link them ($error). You can use them anyway; only Show desktop and Open an app from a press or a gesture need the link.")
                            for (d in bonded) TextAction("Use ${d.name ?: d.address} anyway") { c.link.select(d.address); chosen = true }
                        }
                    }
                }
            }
            Step(3, "Add the Quick Settings tile", "Listening mode and battery, one tap away. Optional.", step) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Add tile", R.drawable.ic_tiles, filled = true) { addTile(context); tileDone = true }
                    TextAction("Not now") { tileDone = true }
                }
            }
            Step(4, "Notifications", "Battery while connected, and a heads-up when a bud runs low. Optional.", step) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Allow", filled = true) { perms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                    TextAction("Not now") { finish() }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                if (step == 5) PillButton("Done", filled = true) { finish() }
                else TextAction("Skip for now", MaterialTheme.colorScheme.onSurfaceVariant) { finish() }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String, current: Int, action: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val state = when { n < current -> 0; n == current -> 1; else -> 2 }
    val rowMod = if (state == 1) Modifier.clip(RoundedCornerShape(22.dp)).background(LocalHearOnColors.current.card).padding(horizontal = 20.dp, vertical = 18.dp)
    else Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    Row(rowMod.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(32.dp).clip(if (state == 1) RoundedCornerShape(12.dp) else CircleShape)
                .background(when (state) { 0 -> cs.primary; 1 -> cs.primaryContainer; else -> cs.surfaceContainerHigh }),
            contentAlignment = Alignment.Center,
        ) {
            if (state == 0) Glyph(R.drawable.ic_check, size = 18.dp, tint = cs.onPrimary)
            else Text("$n", style = MaterialTheme.typography.labelLarge, color = if (state == 1) cs.onPrimaryContainer else cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(if (state == 1) 680 else 560)),
                color = if (state == 2) cs.onSurfaceVariant else cs.onSurface, modifier = Modifier.padding(top = 4.dp))
            if (state == 1) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Box(Modifier.padding(top = 10.dp)) { action() }
            }
        }
    }
}
