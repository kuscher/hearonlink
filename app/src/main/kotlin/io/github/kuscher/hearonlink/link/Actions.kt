package io.github.kuscher.hearonlink.link

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.Feature
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.aap.Press
import io.github.kuscher.hearonlink.hearOn

/**
 * Something a stem press or a head gesture can do. Settings store the name, and for [OPEN_APP] the
 * app's package after a colon ("OPEN_APP:com.example").
 */
enum class Action(val label: String) {
    DEFAULT("AirPods default"),
    NONE("Nothing"),
    PLAY_PAUSE("Play or pause"),
    NEXT("Next track"),
    PREVIOUS("Previous track"),
    VOLUME_UP("Volume up"),
    VOLUME_DOWN("Volume down"),
    MUTE("Mute or unmute"),
    NOISE_CYCLE("Switch noise control"),
    MODE_TRANSPARENCY("Transparency"),
    MODE_ADAPTIVE("Adaptive"),
    MODE_NOISE_CANCELLATION("Noise Cancellation"),
    MODE_OFF("Noise control off"),
    CONVERSATION_AWARENESS("Conversation awareness on or off"),
    ASSISTANT("Voice assistant"),
    SHOW_DESKTOP("Show desktop"),
    OPEN_APP("Open an app"),
    ;

    /** Whether AirPods in state [p] have what this action switches. */
    fun fits(p: PodState) = when (this) {
        NOISE_CYCLE, MODE_TRANSPARENCY, MODE_NOISE_CANCELLATION, MODE_OFF -> p.has(Feature.LISTENING_MODES)
        MODE_ADAPTIVE -> p.has(Feature.ADAPTIVE)
        CONVERSATION_AWARENESS -> p.has(Feature.CONVERSATION_AWARENESS)
        else -> true
    }

    companion object {
        /**
         * The action a stored value names. Values this version doesn't know (the system actions of
         * 0.1: Overview, Back, Notifications, Quick Settings, Screenshot, Lock) give [fallback].
         */
        fun of(stored: String?, fallback: Action = DEFAULT) = entries.firstOrNull { it.name == stored?.substringBefore(':') } ?: fallback

        /** The package a stored [OPEN_APP] value opens. */
        fun app(stored: String?): String? = stored?.substringAfter(':', "")?.ifEmpty { null }

        fun openApp(pkg: String) = "${OPEN_APP.name}:$pkg"

        /** Choices for a stem press (AirPods default first), for AirPods in state [p]: only what they can do. */
        fun forPresses(p: PodState) = entries.filter { it.fits(p) }
        /** Choices for a head gesture ("Nothing" instead of an AirPods default). */
        fun forGestures(p: PodState) = entries.filter { it != DEFAULT && it.fits(p) }

        /** What the AirPods do on their own, for when we intercept one bud's press but not the other's. */
        fun airpodsDefault(p: Press) = when (p) {
            Press.SINGLE -> PLAY_PAUSE; Press.DOUBLE -> NEXT; Press.TRIPLE -> PREVIOUS; Press.LONG -> NOISE_CYCLE
        }
    }
}

object Actions {
    /** "Open Files" for an app action, else the action's own label. */
    fun label(context: Context, stored: String?, fallback: Action = Action.DEFAULT): String {
        val a = Action.of(stored, fallback)
        if (a != Action.OPEN_APP) return a.label
        val pm = context.packageManager
        val name = Action.app(stored)?.let { pkg -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) }.getOrNull() }
        return if (name != null) "Open $name" else a.label
    }

    fun perform(context: Context, a: Action, app: String? = null): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        fun key(code: Int) {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        Log.i(Link.TAG, "action $a")
        return when (a) {
            Action.DEFAULT, Action.NONE -> true
            Action.PLAY_PAUSE -> { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); true }
            Action.NEXT -> { key(KeyEvent.KEYCODE_MEDIA_NEXT); true }
            Action.PREVIOUS -> { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS); true }
            Action.NOISE_CYCLE -> { context.hearOn.link.cycleMode(); true }
            Action.MODE_TRANSPARENCY -> { context.hearOn.link.setMode(ListeningMode.TRANSPARENCY); true }
            Action.MODE_ADAPTIVE -> { context.hearOn.link.setMode(ListeningMode.ADAPTIVE); true }
            Action.MODE_NOISE_CANCELLATION -> { context.hearOn.link.setMode(ListeningMode.NOISE_CANCELLATION); true }
            Action.MODE_OFF -> { context.hearOn.link.setMode(ListeningMode.OFF); true }
            Action.CONVERSATION_AWARENESS -> {
                val link = context.hearOn.link
                link.setFlag(Control.CONVERSATION_AWARENESS, link.state.value.pod.conversationAwareness != true); true
            }
            Action.MUTE -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); true }
            Action.VOLUME_UP -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true }
            Action.VOLUME_DOWN -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true }
            Action.ASSISTANT -> start(context, Intent(Intent.ACTION_VOICE_COMMAND)) || start(context, Intent(Intent.ACTION_ASSIST))
            // The launcher, as the Home key would: on a Googlebook that's the desktop. (Peek itself is
            // a window-manager shortcut apps can't send.)
            Action.SHOW_DESKTOP -> start(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            Action.OPEN_APP -> app?.let { context.packageManager.getLaunchIntentForPackage(it) }?.let { start(context, it) } ?: false
        }
    }

    /**
     * Starts an activity from the background. Android allows that for the companion app of an
     * associated device (the AirPods picked in the setup), so this needs no special permission.
     */
    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(Link.TAG, "start ${intent.action}: $it") }.isSuccess

    /** An app the user can pick for "Open an app". */
    class App(val pkg: String, val name: String)

    /** Every app with a launcher icon, by name (visible through the manifest's launcher query). */
    fun launchable(context: Context): List<App> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }
}
