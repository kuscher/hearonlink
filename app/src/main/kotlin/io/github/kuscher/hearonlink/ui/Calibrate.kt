package io.github.kuscher.hearonlink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.Aap
import io.github.kuscher.hearonlink.aap.AapEvent
import io.github.kuscher.hearonlink.data.HeadCal
import io.github.kuscher.hearonlink.gestures.Calibration
import io.github.kuscher.hearonlink.gestures.Gesture
import io.github.kuscher.hearonlink.gestures.HeadGestureDetector
import io.github.kuscher.hearonlink.gestures.HeadSample
import io.github.kuscher.hearonlink.gestures.Sensitivity
import io.github.kuscher.hearonlink.link.HeadMotion
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

private enum class Step(val title: String, val text: String) {
    READY("Get ready", "Wear both AirPods and sit comfortably. It takes about half a minute."),
    STILL("Hold still", "Look straight ahead and keep your head still for a moment."),
    NOD("Nod, like saying yes", "Nod at an easy pace, a little bigger than usual, until the ring fills."),
    SHAKE("Shake, like saying no", "Now shake your head from side to side until the ring fills."),
    CHECK_NOD("Check: nod", "Nod the way you'd normally say yes."),
    CHECK_SHAKE("Check: shake", "Now shake your head the way you'd normally say no."),
    DONE("All set", "HearOn Link now knows how your AirPods feel a nod and a shake."),
    FAILED("Let's try that again", ""),
}

private fun headFrame(e: AapEvent): ByteArray? =
    (e as? AapEvent.Sensor)?.takeIf { it.service == Aap.SENSOR_DEVMOTION || it.service == Aap.SENSOR_ACTIVITY }?.payload

/** Clears the calibration for both buds and puts sensitivity back to Normal. */
fun resetCalibration(c: Ctx) {
    c.link.updateCache { it.copy(head = emptyMap()) }
    c.prefs.update { it.copy(sensitivity = Sensitivity.NORMAL) }
}

/** The demo's calibration card: status, Calibrate, Reset to original. */
@Composable
fun CalibrationCard(s: LinkState, c: Ctx, onCalibrate: () -> Unit) {
    val cal = s.headCal
    val sens = c.prefs.settings.value.sensitivity
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalHearOnColors.current.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Calibration", style = MaterialTheme.typography.titleSmall)
        Text(
            if (cal != null) "Tuned to your AirPods" + (if (cal.quality < 3f) ". The separation was weak: calibrating again with clearer moves may help." else ".")
            else "Using the original values. Calibrate if nods or shakes are missed.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (cal != null) "Calibrate again" else "Calibrate", filled = true) { onCalibrate() }
            if (cal != null || sens != Sensitivity.NORMAL) TextAction("Reset to original") { resetCalibration(c) }
        }
    }
}

/** Guided calibration: still → nod → shake, then a check that both are recognised before saving. */
@Composable
fun CalibrationWizard(s: LinkState, c: Ctx, wide: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val link = c.link
    var step by remember { mutableStateOf(Step.READY) }
    var run by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<Calibration.Result?>(null) }
    var motion by remember { mutableFloatStateOf(0f) }
    val progress = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        link.trackHead("calibrate", true)
        onDispose { link.trackHead("calibrate", false) }
    }

    fun save(r: Calibration.Result) {
        val side = if (link.state.value.pod.leftPrimary) "L" else "R"
        val cal = HeadCal(r.verticalOffset, r.horizontalOffset, r.verticalScale, r.horizontalScale, r.quality, System.currentTimeMillis())
        link.updateCache { it.copy(head = it.head + (side to cal)) }
    }

    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        val still = ArrayList<ByteArray>(); val nod = ArrayList<ByteArray>(); val shake = ArrayList<ByteArray>()
        var prev: ByteArray? = null
        var baseline = 0f

        // How much the frame changed: the median |Δ| over all int16 fields, so counters don't dominate.
        fun change(p: ByteArray): Float {
            val q = prev; prev = p
            if (q == null || q.size != p.size) return 0f
            val d = (0 until p.size - 1 step 2).map { o ->
                fun s16(b: ByteArray) = ((b[o].toInt() and 0xff) or (b[o + 1].toInt() shl 8)).toShort().toInt()
                abs(s16(p) - s16(q)).toFloat()
            }.sorted()
            return d[d.size / 2]
        }

        val stillChanges = ArrayList<Float>()
        suspend fun record(ms: Int, into: MutableList<ByteArray>) = coroutineScope {
            progress.snapTo(0f)
            prev = null
            launch { progress.animateTo(1f, tween(ms, easing = LinearEasing)) }
            withTimeoutOrNull(ms.toLong()) {
                link.events.collect { e ->
                    val f = headFrame(e) ?: return@collect
                    into += f
                    val ch = change(f)
                    if (into === still) stillChanges += ch
                    motion = if (baseline > 0f) (ch / (baseline * 6f)).coerceIn(0f, 1f) else (ch / 50f).coerceIn(0f, 1f)
                }
            }
        }

        suspend fun check(r: Calibration.Result, want: Gesture): Boolean {
            val d = HeadGestureDetector(Sensitivity.NORMAL, r.verticalScale, r.horizontalScale)
            hint = ""
            progress.snapTo(0f)
            return coroutineScope {
                val ring = launch { progress.animateTo(1f, tween(8000, easing = LinearEasing)) }
                val seen = withTimeoutOrNull(8000) {
                    link.events.first { e ->
                        val f = headFrame(e) ?: return@first false
                        val sample = HeadMotion.decode(f, r.verticalOffset, r.horizontalOffset) ?: return@first false
                        val g = d.feed(HeadSample(sample.timeMs, sample.vertical, sample.horizontal))
                        motion = (maxOf(abs(d.lastVertical), abs(d.lastHorizontal)) / 1.2f).coerceIn(0f, 1f)
                        if (g != null && g != want) hint = if (want == Gesture.NOD) "That looked like a shake. Try a nod." else "That looked like a nod. Try a shake."
                        g == want
                    }
                } != null
                ring.cancel()
                seen
            }
        }

        message = ""; candidate = null
        step = Step.READY; progress.snapTo(0f); delay(1500)
        step = Step.STILL; record(3000, still)
        baseline = stillChanges.sorted().let { l -> if (l.isEmpty()) 1f else l[l.size / 2].coerceAtLeast(1f) }
        step = Step.NOD; delay(900); record(5500, nod)
        step = Step.SHAKE; delay(900); record(5500, shake)
        runCatching {
            File(context.cacheDir, "calibration-last.txt").writeText(buildString {
                for ((name, list) in listOf("still" to still, "nod" to nod, "shake" to shake)) for (f in list) append(name).append(' ').append(f.joinToString("") { "%02x".format(it) }).append('\n')
            })
        }
        val r = Calibration.solve(still, nod, shake)
        when {
            nod.size < 20 -> { message = "No head motion arrived. Put both AirPods in, and if they're playing from another device, switch them here."; step = Step.FAILED; return@LaunchedEffect }
            r == null -> { message = "I couldn't tell the nod and the shake apart. Try bigger, slower moves, and keep still between steps."; step = Step.FAILED; return@LaunchedEffect }
        }
        candidate = r!!
        step = Step.CHECK_NOD
        val nodOk = check(r, Gesture.NOD)
        step = Step.CHECK_SHAKE
        val shakeOk = check(r, Gesture.SHAKE)
        if (nodOk && shakeOk) { save(r); step = Step.DONE }
        else {
            message = when {
                !nodOk && !shakeOk -> "Neither the nod nor the shake was recognised."
                !nodOk -> "The shake worked, but the nod wasn't recognised."
                else -> "The nod worked, but the shake wasn't recognised."
            } + " Try again with clearer moves, or save it anyway and pick Gentle sensitivity."
            step = Step.FAILED
        }
    }
    LaunchedEffect(Unit) { run++ }

    val order = listOf(Step.STILL, Step.NOD, Step.SHAKE, Step.CHECK_NOD)
    val body: @Composable () -> Unit = {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Where we are: Still · Nod · Shake · Check
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((i, label) in listOf("Still", "Nod", "Shake", "Check").withIndex()) {
                    val cur = when (step) { Step.CHECK_SHAKE -> 3; Step.DONE -> 4; Step.READY, Step.FAILED -> -1; else -> order.indexOf(step) }
                    val on = i == cur; val done = i < cur
                    Text(label, Modifier.clip(CircleShape).background(when { on -> MaterialTheme.colorScheme.primary; done -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerHigh })
                        .padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                        color = when { on -> MaterialTheme.colorScheme.onPrimary; done -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                }
            }
            HeadIllustration(step, progress.value, motion)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(step.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(if (step == Step.FAILED) message else step.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hint.isNotEmpty() && (step == Step.CHECK_NOD || step == Step.CHECK_SHAKE))
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            if (step in listOf(Step.STILL, Step.NOD, Step.SHAKE, Step.CHECK_NOD, Step.CHECK_SHAKE)) Column(Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Motion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Meter((motion * 100).toInt().coerceIn(0, 100), height = 10.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (step) {
                    Step.DONE -> PillButton("Done", filled = true) { onClose() }
                    Step.FAILED -> {
                        TextAction("Cancel", MaterialTheme.colorScheme.onSurfaceVariant) { onClose() }
                        candidate?.let { r -> TextAction("Save anyway") { save(r); onClose() } }
                        PillButton("Try again", filled = true) { run++ }
                    }
                    else -> TextAction("Cancel", MaterialTheme.colorScheme.onSurfaceVariant) { onClose() }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = if (wide) 40.dp else 16.dp),
        contentAlignment = Alignment.TopCenter) { body() }
}

@Composable
private fun HeadIllustration(step: Step, progress: Float, motion: Float) {
    val t = rememberInfiniteTransition(label = "head")
    val swing by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "swing")
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (step != Step.DONE && step != Step.FAILED && step != Step.READY)
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(220.dp), strokeWidth = 8.dp,
                color = cs.primary, trackColor = cs.surfaceContainerHigh)
        val bg = when (step) { Step.DONE -> cs.primaryContainer; Step.FAILED -> LocalHearOnColors.current.no; else -> cs.surfaceContainerHigh }
        Box(Modifier.size(170.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            when (step) {
                Step.DONE -> Glyph(R.drawable.ic_check, size = 80.dp, tint = cs.onPrimaryContainer)
                Step.FAILED -> Glyph(R.drawable.ic_close, size = 72.dp, tint = LocalHearOnColors.current.onNo)
                else -> {
                    val nod = step == Step.NOD || step == Step.CHECK_NOD
                    val shake = step == Step.SHAKE || step == Step.CHECK_SHAKE
                    Glyph(R.drawable.ic_head, size = 84.dp, tint = cs.onSurfaceVariant, modifier = Modifier.graphicsLayer {
                        cameraDistance = 12f * density
                        if (nod) { rotationX = 24f * swing; translationY = 6f * swing * density }
                        if (shake) { rotationY = 32f * swing }
                        if (step == Step.STILL) { scaleX = 1f + 0.03f * swing; scaleY = scaleX }
                        alpha = 0.75f + 0.25f * motion
                    })
                }
            }
        }
    }
}
