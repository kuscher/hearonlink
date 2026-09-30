package io.github.kuscher.hearonlink.data

import android.content.Context
import android.util.Base64
import io.github.kuscher.hearonlink.aap.Level
import io.github.kuscher.hearonlink.gestures.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** App settings (backed up) and what we remember about the AirPods (device only, never backed up). */
data class Settings(
    val theme: String = "wallpaper",          // wallpaper | teal
    val dark: String = "system",              // system | light | dark
    val notifyConnected: Boolean = true,
    val lowBattery: Boolean = true,
    val nearbyAlert: Boolean = true,
    val earPause: Boolean = true,
    val earResume: Boolean = true,
    val duckWhileTalking: Boolean = true,
    val tileTapCycles: Boolean = false,
    val gestureCalls: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val onboarded: Boolean = false,
)

/** Last values we saw, so the app can show them while the AirPods are away. */
@Serializable
data class LastSeen(
    val name: String? = null,
    val model: String? = null,
    val left: Int? = null, val right: Int? = null, val case: Int? = null,
    val leftCharging: Boolean = false, val rightCharging: Boolean = false, val caseCharging: Boolean = false,
    val mode: Int? = null,
    val at: Long = 0,
) {
    val leftLevel get() = left?.let { Level(it, leftCharging) }
    val rightLevel get() = right?.let { Level(it, rightCharging) }
    val caseLevel get() = case?.let { Level(it, caseCharging) }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dev = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    private fun read() = Settings(
        theme = sp.getString("theme", "wallpaper")!!,
        dark = sp.getString("dark", "system")!!,
        notifyConnected = sp.getBoolean("notifyConnected", true),
        lowBattery = sp.getBoolean("lowBattery", true),
        nearbyAlert = sp.getBoolean("nearbyAlert", true),
        earPause = sp.getBoolean("earPause", true),
        earResume = sp.getBoolean("earResume", true),
        duckWhileTalking = sp.getBoolean("duckWhileTalking", true),
        tileTapCycles = sp.getBoolean("tileTapCycles", false),
        gestureCalls = sp.getBoolean("gestureCalls", false),
        sensitivity = runCatching { Sensitivity.valueOf(sp.getString("sensitivity", "NORMAL")!!) }.getOrDefault(Sensitivity.NORMAL),
        onboarded = sp.getBoolean("onboarded", false),
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        sp.edit()
            .putString("theme", s.theme).putString("dark", s.dark)
            .putBoolean("notifyConnected", s.notifyConnected).putBoolean("lowBattery", s.lowBattery)
            .putBoolean("nearbyAlert", s.nearbyAlert).putBoolean("earPause", s.earPause).putBoolean("earResume", s.earResume)
            .putBoolean("duckWhileTalking", s.duckWhileTalking).putBoolean("tileTapCycles", s.tileTapCycles)
            .putBoolean("gestureCalls", s.gestureCalls).putString("sensitivity", s.sensitivity.name)
            .putBoolean("onboarded", s.onboarded)
            .apply()
        _settings.value = s
    }

    /** The AirPods the user picked (Bluetooth address) and their companion-device association. */
    var address: String?
        get() = dev.getString("address", null)
        set(v) { dev.edit().putString("address", v).apply() }
    var associationId: Int
        get() = dev.getInt("associationId", -1)
        set(v) { dev.edit().putInt("associationId", v).apply() }

    var lastSeen: LastSeen
        get() = dev.getString("lastSeen", null)?.let { runCatching { json.decodeFromString<LastSeen>(it) }.getOrNull() } ?: LastSeen()
        set(v) { dev.edit().putString("lastSeen", json.encodeToString(LastSeen.serializer(), v)).apply() }

    /** The AirPods' identity and advert keys (from the AirPods themselves). Private storage only. */
    var irk: ByteArray?
        get() = dev.getString("irk", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(v) { dev.edit().putString("irk", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply() }
    var encKey: ByteArray?
        get() = dev.getString("enc", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(v) { dev.edit().putString("enc", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply() }

    fun forgetDevice() { dev.edit().clear().apply() }
}
