package io.github.kuscher.hearonlink.data

import android.content.Context
import android.util.Base64
import io.github.kuscher.hearonlink.aap.Batteries
import io.github.kuscher.hearonlink.aap.DeviceInfo
import io.github.kuscher.hearonlink.aap.PartLevel
import io.github.kuscher.hearonlink.aap.Press
import io.github.kuscher.hearonlink.aap.Source
import io.github.kuscher.hearonlink.gestures.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** App settings (backed up). Actions are stored by name (see link/Actions.kt). */
data class Settings(
    val theme: String = "wallpaper",          // wallpaper | teal
    val dark: String = "system",              // system | light | dark
    val lowBattery: Boolean = true,
    val nearbyAlert: Boolean = true,
    val earPause: Boolean = true,
    val earResume: Boolean = true,
    val duckWhileTalking: Boolean = true,
    val tileTapCycles: Boolean = false,
    val gestureCalls: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val onboarded: Boolean = false,
    /** Stem presses: "B" (both buds) or "L"/"R" when [splitBuds] is on, then "." + press → action name. */
    val presses: Map<String, String> = emptyMap(),
    val splitBuds: Boolean = false,
    /** Head gestures outside the demo and calls. */
    val gesturesAnytime: Boolean = false,
    val nodAction: String = "NONE",
    val shakeAction: String = "SHOW_DESKTOP",
) {
    /** The action name for a press on a bud ("L" or "R"); "DEFAULT" means the AirPods handle it. */
    fun pressAction(bud: String, p: Press): String =
        presses[(if (splitBuds) bud else "B") + "." + p.name] ?: "DEFAULT"
}

@Serializable
data class CachedLevel(val percent: Int, val charging: Boolean, val at: Long, val source: String)

/** What we remember about one pair of AirPods, so the app can show it while they're away. */
@Serializable
data class DeviceCache(
    val name: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val build: String? = null,
    val left: CachedLevel? = null, val right: CachedLevel? = null, val case: CachedLevel? = null, val single: CachedLevel? = null,
    val controls: Map<Int, List<Int>> = emptyMap(),
    /** Head-sensor calibration: int16 offsets for up/down and sideways, and a typical swing size. */
    val headVertical: Int? = null, val headHorizontal: Int? = null, val headScale: Float? = null,
    val lastConnected: Long = 0,
) {
    val info: DeviceInfo? get() = if (name == null && model == null) null else DeviceInfo(name, model, null, null, firmware, null, null, null, null, build)

    val batteries: Batteries get() = Batteries(left?.part(), right?.part(), case?.part(), single?.part())

    fun with(b: Batteries) = copy(left = b.left?.cached(), right = b.right?.cached(), case = b.case?.cached(), single = b.single?.cached())

    companion object {
        private fun CachedLevel.part() = PartLevel(percent, charging, at, runCatching { Source.valueOf(source) }.getOrDefault(Source.LIVE), live = false)
        private fun PartLevel.cached() = CachedLevel(percent, charging, at, source.name)
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dev = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    private fun read() = Settings(
        theme = sp.getString("theme", "wallpaper")!!,
        dark = sp.getString("dark", "system")!!,
        lowBattery = sp.getBoolean("lowBattery", true),
        nearbyAlert = sp.getBoolean("nearbyAlert", true),
        earPause = sp.getBoolean("earPause", true),
        earResume = sp.getBoolean("earResume", true),
        duckWhileTalking = sp.getBoolean("duckWhileTalking", true),
        tileTapCycles = sp.getBoolean("tileTapCycles", false),
        gestureCalls = sp.getBoolean("gestureCalls", false),
        sensitivity = runCatching { Sensitivity.valueOf(sp.getString("sensitivity", "NORMAL")!!) }.getOrDefault(Sensitivity.NORMAL),
        onboarded = sp.getBoolean("onboarded", false),
        presses = sp.getString("presses", "")!!.split(';').mapNotNull { e ->
            e.split('=').takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] }
        }.toMap(),
        splitBuds = sp.getBoolean("splitBuds", false),
        gesturesAnytime = sp.getBoolean("gesturesAnytime", false),
        nodAction = sp.getString("nodAction", "NONE")!!,
        shakeAction = sp.getString("shakeAction", "SHOW_DESKTOP")!!,
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        sp.edit()
            .putString("theme", s.theme).putString("dark", s.dark)
            .putBoolean("lowBattery", s.lowBattery)
            .putBoolean("nearbyAlert", s.nearbyAlert).putBoolean("earPause", s.earPause).putBoolean("earResume", s.earResume)
            .putBoolean("duckWhileTalking", s.duckWhileTalking).putBoolean("tileTapCycles", s.tileTapCycles)
            .putBoolean("gestureCalls", s.gestureCalls).putString("sensitivity", s.sensitivity.name)
            .putBoolean("onboarded", s.onboarded)
            .putString("presses", s.presses.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putBoolean("splitBuds", s.splitBuds)
            .putBoolean("gesturesAnytime", s.gesturesAnytime)
            .putString("nodAction", s.nodAction).putString("shakeAction", s.shakeAction)
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

    fun cache(address: String?): DeviceCache =
        address?.let { a -> dev.getString("cache.$a", null)?.let { runCatching { json.decodeFromString<DeviceCache>(it) }.getOrNull() } } ?: DeviceCache()

    fun saveCache(address: String?, c: DeviceCache) {
        if (address != null) dev.edit().putString("cache.$address", json.encodeToString(DeviceCache.serializer(), c)).apply()
    }

    /** The AirPods' identity and advert keys (from the AirPods themselves). Private storage only. */
    var irk: ByteArray?
        get() = dev.getString("irk", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(v) { dev.edit().putString("irk", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply() }
    var encKey: ByteArray?
        get() = dev.getString("enc", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(v) { dev.edit().putString("enc", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply() }

    fun forgetDevice() { dev.edit().clear().apply() }
}
