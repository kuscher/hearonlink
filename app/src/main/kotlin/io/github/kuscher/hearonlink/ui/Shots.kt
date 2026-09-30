package io.github.kuscher.hearonlink.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.kuscher.hearonlink.aap.BatteryReading
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.ChargeState
import io.github.kuscher.hearonlink.aap.Component
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.DeviceInfo
import io.github.kuscher.hearonlink.aap.EarState
import io.github.kuscher.hearonlink.aap.PodState
import io.github.kuscher.hearonlink.aap.Batteries
import io.github.kuscher.hearonlink.aap.PartLevel
import io.github.kuscher.hearonlink.aap.Source
import io.github.kuscher.hearonlink.data.DeviceCache
import io.github.kuscher.hearonlink.gestures.Gesture
import io.github.kuscher.hearonlink.hearOn
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.link.LinkStatus
import io.github.kuscher.hearonlink.ui.theme.HearOnTheme
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshots for design checks and store listings (debug hooks only): renders HearOn Link's real
 * UI on a private virtual display the app owns, at any size and density, with sample AirPods.
 * Nothing appears on the real screen and no other app is captured.
 */
object Shots {
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, androidx.activity.OnBackPressedDispatcherOwner,
        androidx.activity.result.ActivityResultRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I,
                options: androidx.core.app.ActivityOptionsCompat?) {}
        }
        init { saved.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    fun sample(connected: Boolean = true): LinkState {
        var p = PodState(handshakeDone = true, info = DeviceInfo("AirPods Pro", "A3048", "Apple Inc.", null, "81.2675", null, null, null, null, "8454592"))
        p = p.reduce(AapEvent.Battery(listOf(
            BatteryReading(Component.LEFT, 95, ChargeState.DISCHARGING), BatteryReading(Component.RIGHT, 93, ChargeState.DISCHARGING),
            BatteryReading(Component.CASE, 72, ChargeState.CHARGING),
        )))
        p = p.copy(
            left = p.left.copy(ear = EarState.IN_EAR), right = p.right.copy(ear = EarState.IN_EAR),
            controls = mapOf(
                Control.LISTENING_MODE to listOf(3), Control.CONVERSATION_AWARENESS to listOf(1), Control.PERSONALIZED_VOLUME to listOf(2),
                Control.ADAPTIVE_LEVEL to listOf(50), Control.LISTENING_CYCLE to listOf(0x0e), Control.ONE_BUD_ANC to listOf(2),
                Control.VOLUME_SWIPE to listOf(1), Control.TONE_VOLUME to listOf(75), Control.PRESS_SPEED to listOf(0), Control.HOLD_DURATION to listOf(0),
            ),
        )
        val now = System.currentTimeMillis()
        val cache = DeviceCache(name = "AirPods Pro", model = "A3048", firmware = "81.2675", build = "8454592", controls = p.controls,
            head = mapOf("L" to io.github.kuscher.hearonlink.data.HeadCal(28, 26, 900f, 700f, 6f, now)), lastConnected = now - 42 * 60_000)
        return if (connected) {
            val b = Batteries().fromAap(listOf(
                BatteryReading(Component.LEFT, 95, ChargeState.DISCHARGING), BatteryReading(Component.RIGHT, 93, ChargeState.DISCHARGING),
                BatteryReading(Component.CASE, 72, ChargeState.CHARGING)), now)
            LinkState(LinkStatus.CONNECTED, "00:00:00:00:00:00", p, cache, b)
        } else {
            val b = Batteries(
                PartLevel(80, false, now - 42 * 60_000, Source.LIVE, false), PartLevel(78, false, now - 42 * 60_000, Source.LIVE, false),
                PartLevel(55, false, now - 9 * 60_000, Source.ADVERT_PRECISE, false))
            LinkState(LinkStatus.AWAY, "00:00:00:00:00:00", PodState(), cache, b)
        }
    }

    /** kind: home | page:NOISE | demo | demo:yes | demo:no | away | onboarding | panel */
    fun render(context: Context, kind: String, w: Int, h: Int, dpi: Int, dark: Boolean, out: File, done: (String) -> Unit) {
        val app = context.hearOn
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val dm = context.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("hearon-shot", w, h, dpi, reader.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
        val pres = Presentation(context, vd.display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val owner = Owner()
        // A clean example setup (not the user's own): press twice for Overview, shake to show the desktop.
        val settings = io.github.kuscher.hearonlink.data.Settings(
            dark = if (dark) "dark" else "light", onboarded = kind != "onboarding", theme = "teal",
            presses = mapOf("B.DOUBLE" to "OVERVIEW"), gesturesAnytime = true, nodAction = "PLAY_PAUSE", shakeAction = "SHOW_DESKTOP",
        )
        val state = sample(connected = kind != "away")
        val view = ComposeView(pres.context).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner); setViewTreeViewModelStoreOwner(owner)
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owner,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
                HearOnTheme(settings) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                        when {
                            kind == "panel" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PanelContent(state, app.link) {} }
                            kind == "calibrate" -> CalibrationWizard(state, Ctx(app.link, app.prefs, {}, phone = w < 1000), wide = w >= 1000) {}
                            kind.startsWith("demo") -> AppScreen(state, settings, Page.DEMO,
                                forced = when (kind) { "demo:yes" -> Gesture.NOD; "demo:no" -> Gesture.SHAKE; else -> null })
                            kind.startsWith("page:") -> AppScreen(state, settings, Page.valueOf(kind.removePrefix("page:")))
                            else -> AppScreen(state, settings, Page.HOME)
                        }
                    }
                }
                }
            }
        }
        pres.setContentView(view)
        pres.show()
        Handler(Looper.getMainLooper()).postDelayed({
            val result = try {
                val img = reader.acquireLatestImage() ?: throw IllegalStateException("no frame")
                val plane = img.planes[0]
                val bmp = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(plane.buffer)
                img.close()
                FileOutputStream(out).use { Bitmap.createBitmap(bmp, 0, 0, w, h).compress(Bitmap.CompressFormat.PNG, 100, it) }
                out.absolutePath
            } catch (e: Exception) { "failed ${e.message}" }
            pres.dismiss(); owner.destroy(); vd.release(); reader.close()
            done(result)
        }, 2500)
    }
}
