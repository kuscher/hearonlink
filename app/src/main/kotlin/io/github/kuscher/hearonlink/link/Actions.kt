package io.github.kuscher.hearonlink.link

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.kuscher.hearonlink.aap.Press
import io.github.kuscher.hearonlink.hearOn

/** Something a stem press or a head gesture can do. */
enum class Action(val label: String, val system: Boolean = false) {
    DEFAULT("AirPods default"),
    NONE("Nothing"),
    PLAY_PAUSE("Play or pause"),
    NEXT("Next track"),
    PREVIOUS("Previous track"),
    NOISE_CYCLE("Switch noise control"),
    VOLUME_UP("Volume up"),
    VOLUME_DOWN("Volume down"),
    ASSISTANT("Voice assistant"),
    SHOW_DESKTOP("Show desktop (peek)", system = true),
    OVERVIEW("Overview", system = true),
    BACK("Back", system = true),
    NOTIFICATIONS("Notifications", system = true),
    QUICK_SETTINGS("Quick Settings", system = true),
    SCREENSHOT("Screenshot", system = true),
    LOCK("Lock screen", system = true),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT

        /** Choices for a stem press (AirPods default first). */
        val forPresses = entries.toList()
        /** Choices for a head gesture ("Nothing" instead of an AirPods default). */
        val forGestures = entries.filter { it != DEFAULT }

        /** What the AirPods do on their own, for when we intercept one bud's press but not the other's. */
        fun airpodsDefault(p: Press) = when (p) {
            Press.SINGLE -> PLAY_PAUSE; Press.DOUBLE -> NEXT; Press.TRIPLE -> PREVIOUS; Press.LONG -> NOISE_CYCLE
        }
    }
}

object Actions {
    fun perform(context: Context, a: Action): Boolean {
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
            Action.VOLUME_UP -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true }
            Action.VOLUME_DOWN -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true }
            Action.ASSISTANT -> assistant(context)
            // Peek is a window-manager shortcut apps can't send; Home is the closest system action.
            Action.SHOW_DESKTOP -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_HOME)
            Action.OVERVIEW -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_RECENTS)
            Action.BACK -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_BACK)
            Action.NOTIFICATIONS -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            Action.QUICK_SETTINGS -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            Action.SCREENSHOT -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            Action.LOCK -> SystemActions.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private fun assistant(context: Context): Boolean {
        for (action in listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)) {
            val ok = runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return true
        }
        return false
    }
}

/**
 * Accessibility service used ONLY to perform system actions (Home, Overview, Back, Notifications,
 * Quick Settings, Screenshot, Lock) when you press a stem or move your head. It asks for no events
 * and can't read the screen or your input (see res/xml/system_actions.xml).
 */
class SystemActions : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }

    companion object {
        @Volatile private var instance: SystemActions? = null

        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        /** Turned on in Android's accessibility settings (true even before it binds). */
        fun enabled(context: Context): Boolean {
            if (instance != null) return true
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, SystemActions::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) {
            val detail = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, SystemActions::class.java).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(detail) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
