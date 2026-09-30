package io.github.kuscher.hearonlink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.data.Settings
import io.github.kuscher.hearonlink.gestures.Gesture
import io.github.kuscher.hearonlink.gestures.HeadGestureDetector
import io.github.kuscher.hearonlink.gestures.Sensitivity
import io.github.kuscher.hearonlink.link.LinkState
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors
import kotlinx.coroutines.delay

private const val TRACE = 100

/** Live head-gesture demo: nod for yes, shake for no. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DemoScreen(s: LinkState, settings: Settings, c: Ctx, wide: Boolean, forced: Gesture? = null) {
    val link = c.link
    DisposableEffect(s.connected) {
        if (s.connected) link.trackHead("demo", true)
        onDispose { link.trackHead("demo", false) }
    }
    val detector = remember(settings.sensitivity) { HeadGestureDetector(settings.sensitivity) }
    var result by remember { mutableStateOf(forced) }
    var yes by remember { mutableIntStateOf(if (forced != null) 3 else 0) }
    var no by remember { mutableIntStateOf(if (forced != null) 1 else 0) }
    val vTrace = remember { FloatArray(TRACE) }
    val hTrace = remember { FloatArray(TRACE) }
    var head by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var lastSample by remember { mutableLongStateOf(0L) }

    LaunchedEffect(detector) {
        link.head.collect { h ->
            val g = detector.feed(h)
            val i = head % TRACE
            vTrace[i] = detector.lastVertical; hTrace[i] = detector.lastHorizontal
            head++; tick++
            lastSample = h.timeMs
            if (g != null) { result = g; if (g == Gesture.NOD) yes++ else no++ }
        }
    }
    LaunchedEffect(result, tick / 50) { if (result != null && forced == null) { delay(2200); result = null } }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(s.connected) { waited = false; delay(3500); waited = true }

    val caption = when {
        forced == Gesture.NOD -> "Nod detected"
        forced == Gesture.SHAKE -> "Shake detected"
        !s.connected -> "Connect your AirPods to try this"
        head == 0 && waited -> "Waiting for head motion. Put your AirPods in; if they're playing from another device, switch them here."
        head == 0 -> "Starting head tracking…"
        result == Gesture.NOD -> "Nod detected"
        result == Gesture.SHAKE -> "Shake detected"
        else -> "Nod or shake your head"
    }

    val stage: @Composable (Dp) -> Unit = { size ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            YesNoShape(result, size, detector.lastVertical, detector.lastHorizontal, tick)
            Text(caption, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val side: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MotionCard(vTrace, hTrace, head, tick, settings.sensitivity)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tally("Nods", yes, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
                Tally("Shakes", no, LocalHearOnColors.current.no, LocalHearOnColors.current.onNo, Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalHearOnColors.current.card).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sensitivity", style = MaterialTheme.typography.titleSmall)
                val all = Sensitivity.entries
                Choice(listOf("Gentle", "Normal", "Firm"), all.indexOf(settings.sensitivity)) { i -> c.prefs.update { it.copy(sensitivity = all[i]) } }
            }
            Hint("Head motion stops when you leave this screen.")
        }
    }
    if (wide) Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { stage(400.dp) }
        Box(Modifier.width(380.dp).fillMaxHeight().background(LocalHearOnColors.current.chrome).padding(horizontal = 24.dp, vertical = 12.dp)) { side() }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        stage(290.dp)
        side()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YesNoShape(result: Gesture?, size: Dp, v: Float, h: Float, tick: Int) {
    val target: RoundedPolygon = when (result) {
        Gesture.NOD -> MaterialShapes.Cookie9Sided
        Gesture.SHAKE -> MaterialShapes.Clover4Leaf
        null -> MaterialShapes.Circle
    }
    var from by remember { mutableStateOf<RoundedPolygon>(MaterialShapes.Circle) }
    var to by remember { mutableStateOf<RoundedPolygon>(MaterialShapes.Circle) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target !== to) {
            from = to; to = target
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f))
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(when (result) { Gesture.NOD -> cs.primaryContainer; Gesture.SHAKE -> LocalHearOnColors.current.no; null -> cs.surfaceContainerHigh }, label = "fill")
    val ink by animateColorAsState(when (result) { Gesture.NOD -> cs.onPrimaryContainer; Gesture.SHAKE -> LocalHearOnColors.current.onNo; null -> cs.onSurfaceVariant }, label = "ink")
    val ring = cs.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (result == null) drawCircle(ring, radius = this.size.minDimension * 0.47f, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 10.dp.toPx()))))
            val inset = this.size.minDimension * 0.1f
            val w = this.size.minDimension - 2 * inset
            val path: Path = morph.toPath(progress.value.coerceIn(0f, 1.1f), Path())
            translate(inset, inset) { scale(w, w, pivot = Offset.Zero) { drawPath(path, fill) } }
        }
        if (result == null) {
            // The head leans with the live motion: a small, calm hint that it's working.
            Glyph(R.drawable.ic_head, size = size * 0.3f, tint = ink, modifier = Modifier.graphicsLayer {
                rotationZ = (h / 120f).coerceIn(-18f, 18f); translationY = (v / 40f).coerceIn(-24f, 24f)
            })
        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(if (result == Gesture.NOD) R.drawable.ic_check else R.drawable.ic_close, size = size * 0.24f, tint = ink)
            Text(if (result == Gesture.NOD) "Yes" else "No", color = ink, fontSize = (size.value * 0.19f).sp, fontWeight = FontWeight(780), lineHeight = (size.value * 0.2f).sp)
        }
    }
}

@Composable
private fun MotionCard(v: FloatArray, h: FloatArray, head: Int, tick: Int, sensitivity: Sensitivity) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalHearOnColors.current.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Live head motion", style = MaterialTheme.typography.titleSmall)
        Trace("Up and down", v, head, tick, cs.primary, sensitivity)
        Trace("Left and right", h, head, tick, cs.tertiary, sensitivity)
    }
}

@Composable
private fun Trace(label: String, data: FloatArray, head: Int, tick: Int, color: Color, sensitivity: Sensitivity) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val guide = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            tick.hashCode() // redraw on every sample
            val mid = size.height / 2
            drawLine(guide, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            val scale = mid / (1400f * sensitivity.threshold)
            val p = Path()
            val n = minOf(head, TRACE)
            for (k in 0 until n) {
                val idx = (head - n + k).mod(TRACE)
                val x = size.width * k / (TRACE - 1)
                val y = (mid - data[idx] * scale).coerceIn(0f, size.height)
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Tally(label: String, n: Int, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(bg).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
        Text("$n", style = MaterialTheme.typography.headlineMedium, color = fg)
    }
}
