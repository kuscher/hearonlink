package io.github.kuscher.hearonlink.link

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.aap.Control
import io.github.kuscher.hearonlink.aap.EarState
import io.github.kuscher.hearonlink.aap.Feature
import io.github.kuscher.hearonlink.aap.Press
import io.github.kuscher.hearonlink.data.Prefs
import io.github.kuscher.hearonlink.data.Settings
import io.github.kuscher.hearonlink.gestures.Gesture
import io.github.kuscher.hearonlink.gestures.HeadGestureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * What your AirPods can make the Googlebook or phone do:
 * - custom stem presses (the AirPods forward the presses you customised; the rest stay theirs);
 * - head gestures "anytime" (opt-in; head motion streams only while a bud is in your ear);
 * - on phones, nod to accept a ringing call and shake your head to decline.
 * The demo screen pauses all of this so trying gestures never triggers anything.
 */
class Controls(private val context: Context, private val prefs: Prefs, private val link: Link) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val detector = HeadGestureDetector()
    @Volatile var demoOpen = false
        set(v) { field = v; reconcile() }
    @Volatile private var ringing = false
    private var sentMask: Int? = null
    private var sessionKey = ""

    fun start() {
        scope.launch { combine(link.state, prefs.settings) { s, st -> s to st }.collect { reconcile() } }
        scope.launch { link.events.filterIsInstance<AapEvent.StemPress>().collect(::onPress) }
        scope.launch {
            link.head.collect { sample ->
                if (demoOpen) return@collect
                val g = detector.feed(sample) ?: return@collect
                onGesture(g)
            }
        }
    }

    /** Bring stem forwarding, head tracking and the call listener in line with settings and state. */
    @Synchronized
    fun reconcile() {
        val s = link.state.value
        val st = prefs.settings.value
        // New session: the AirPods start from their own defaults, so resend our press mask.
        val key = if (s.connected) "${s.address}" else ""
        if (key != sessionKey) { sessionKey = key; sentMask = null }
        if (s.connected) {
            val mask = pressMask(st)
            if (mask != sentMask && (mask != 0 || sentMask != null || (s.cache.controls[Control.RAW_PRESSES]?.firstOrNull() ?: 0) != 0)) {
                link.setControl(Control.RAW_PRESSES, mask)
                sentMask = mask
            } else if (sentMask == null) sentMask = mask
        }
        // Head gestures anytime: only with a bud in an ear, never while the demo is open.
        val inEar = s.pod.left.ear == EarState.IN_EAR || s.pod.right.ear == EarState.IN_EAR
        val anytime = st.gesturesAnytime && s.connected && s.pod.has(Feature.HEAD_GESTURES) && inEar && !demoOpen
        link.trackHead("anytime", anytime)
        link.trackHead("call", ringing && s.connected && !demoOpen)
        detector.sensitivity = st.sensitivity
        val cal = s.headCal
        detector.verticalScale = cal?.verticalScale ?: HeadGestureDetector.DEFAULT_SCALE
        detector.horizontalScale = cal?.horizontalScale ?: HeadGestureDetector.DEFAULT_SCALE
        calls(st)
    }

    private fun pressMask(st: Settings): Int = Press.mask(Press.entries.filter { p ->
        Action.of(st.pressAction("L", p)) != Action.DEFAULT || Action.of(st.pressAction("R", p)) != Action.DEFAULT
    })

    private fun onPress(e: AapEvent.StemPress) {
        val press = Press.of(e.press) ?: return
        val st = prefs.settings.value
        val bud = if (e.bud == 1) "L" else "R"
        val stored = st.pressAction(bud, press)
        var a = Action.of(stored)
        // This bud keeps the AirPods' own behaviour, but the press was forwarded for the other bud.
        if (a == Action.DEFAULT) a = Action.airpodsDefault(press)
        Actions.perform(context, a, Action.app(stored))
    }

    private fun onGesture(g: Gesture) {
        if (ringing) { answer(g); return }
        val st = prefs.settings.value
        if (!st.gesturesAnytime) return
        val stored = if (g == Gesture.NOD) st.nodAction else st.shakeAction
        Actions.perform(context, Action.of(stored, Action.NONE), Action.app(stored))
    }

    // ---- calls (phones) ----------------------------------------------------------------------

    private var callback: TelephonyCallback? = null

    private fun hasTelephony() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun callsAllowed() = hasTelephony() &&
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    private fun calls(st: Settings) {
        val want = st.gestureCalls && callsAllowed()
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        if (want && callback == null) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    ringing = state == TelephonyManager.CALL_STATE_RINGING
                    detector.reset()
                    reconcile()
                }
            }
            runCatching { tm.registerTelephonyCallback(context.mainExecutor, cb); callback = cb }
                .onFailure { Log.w(Link.TAG, "calls: $it") }
        } else if (!want && callback != null) {
            callback?.let { tm.unregisterTelephonyCallback(it) }
            callback = null; ringing = false
        }
    }

    @Suppress("DEPRECATION")
    private fun answer(g: Gesture) {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching { if (g == Gesture.NOD) telecom.acceptRingingCall() else telecom.endCall() }
            .onFailure { Log.w(Link.TAG, "call gesture: $it") }
        ringing = false
        reconcile()
    }
}
