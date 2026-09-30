package io.github.kuscher.hearonlink.ui

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.aap.Level
import io.github.kuscher.hearonlink.aap.ListeningMode
import io.github.kuscher.hearonlink.system.icon
import io.github.kuscher.hearonlink.system.label
import io.github.kuscher.hearonlink.ui.theme.LocalHearOnColors
import kotlinx.coroutines.delay

// ---- window: caption bar ----------------------------------------------------------------------

/**
 * Desktop windows (Googlebook OS): the system caption bar is made transparent only so we can paint
 * it the header's colour; the header row itself sits below it (no app content in the caption).
 */
object Caption {
    fun enable(activity: Activity, lightBackground: Boolean) {
        if (Build.VERSION.SDK_INT < 35) return
        val c = activity.window.insetsController ?: return
        c.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
            WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
        )
        c.setSystemBarsAppearance(
            if (lightBackground) WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS,
        )
    }
}

@Composable
fun CaptionSpacer(color: Color) {
    val top = WindowInsets.captionBar.getTop(LocalDensity.current)
    if (top > 0) Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { top.toDp() }).background(color))
}

/** The header row right below the system caption (or at the top on phones). */
@Composable
fun HeaderRow(background: Color, height: Dp = 52.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(height).background(background).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ---- icons, buttons, tooltips ------------------------------------------------------------------

@Composable
fun Glyph(@DrawableRes id: Int, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalContentColor.current) =
    Icon(painterResource(id), contentDescription = null, modifier = modifier.size(size), tint = tint)

/** A tooltip after the pointer rests a moment on [content]; never on a mere pass-over. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoverTip(label: String, content: @Composable (MutableInteractionSource) -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(hovered, pressed) { if (hovered && !pressed) { delay(550); state.show() } else state.dismiss() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below, 6.dp),
        tooltip = { PlainTooltip(shape = RoundedCornerShape(8.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) } },
        state = state,
        enableUserInput = false,
    ) { content(interaction) }
}

@Composable
fun TipIconButton(@DrawableRes icon: Int, label: String, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    HoverTip(label) { interaction ->
        Box(
            Modifier.size(size).clip(CircleShape).hoverable(interaction)
                .clickable(interactionSource = interaction, indication = ripple(), onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Glyph(icon, size = 21.dp, tint = tint) }
    }
}

@Composable
fun StatusDot(text: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun PillButton(text: String, @DrawableRes icon: Int? = null, filled: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (filled) c.primary else c.primaryContainer, contentColor = if (filled) c.onPrimary else c.onPrimaryContainer,
    ) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Glyph(icon, size = 18.dp)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TextAction(text: String, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, contentColor = color) {
        Text(text, Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}

// ---- the AirPods drawing ----------------------------------------------------------------------

/** Our own simple drawing of two buds and a case (no product renders). */
@Composable
fun PodsArt(modifier: Modifier = Modifier, inCase: Boolean = false, dim: Boolean = false) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.3f
    val body = if (dark) Color(0xFFE9EEED) else Color.White
    val edge = if (dark) Color(0xFF8E9B98) else Color(0xFFBCC8C5)
    val mesh = if (dark) Color(0xFFB7C3C0) else Color(0xFFD7E0DE)
    val led = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(250.dp, 170.dp)) {
        val u = size.width / 250f
        val a = if (dim) 0.55f else 1f
        fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color) {
            drawRoundRect(fill.copy(alpha = a), Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
            drawRoundRect(edge.copy(alpha = a), Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = Stroke(1.5f * u))
        }
        rr(67f, 70f, 116f, 92f, 30f, body)
        drawLine(edge.copy(alpha = a), Offset(68f * u, 98f * u), Offset(182f * u, 98f * u), 1.5f * u)
        drawCircle(led.copy(alpha = a), 3.2f * u, Offset(125f * u, 124f * u))
        if (!inCase) {
            pod(33f, false, u, a, body, edge, mesh)
            pod(217f, true, u, a, body, edge, mesh)
        }
    }
}

private fun DrawScope.pod(cx: Float, flip: Boolean, u: Float, a: Float, body: Color, edge: Color, mesh: Color) {
    val s = if (flip) -1f else 1f
    rotate(-10f * s, Offset(cx * u, 18f * u)) {
        drawRoundRect(body.copy(alpha = a), Offset((cx - 8) * u, 44f * u), Size(16f * u, 64f * u), CornerRadius(8f * u))
        drawRoundRect(edge.copy(alpha = a), Offset((cx - 8) * u, 44f * u), Size(16f * u, 64f * u), CornerRadius(8f * u), style = Stroke(1.5f * u))
        drawOval(body.copy(alpha = a), Offset((cx - 21) * u, 18f * u), Size(42f * u, 44f * u))
        drawOval(edge.copy(alpha = a), Offset((cx - 21) * u, 18f * u), Size(42f * u, 44f * u), style = Stroke(1.5f * u))
        drawOval(mesh.copy(alpha = a), Offset((cx - 9 * s - 7.5f) * u, 31.5f * u), Size(15f * u, 17f * u))
    }
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---- battery -----------------------------------------------------------------------------------

@Immutable
data class Cell(val label: String, val level: Level?, val note: String)

@Composable
fun BatteryTrio(cells: List<Cell>, big: Boolean = true, faded: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
        for (c in cells) Column(Modifier.weight(1f).semantics(mergeDescendants = true) {
            stateDescription = c.level?.let { "${it.percent} percent${if (it.charging) ", charging" else ""}" } ?: "unknown"
        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.level?.let { "${it.percent}%" } ?: "–",
                    style = (if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight(720)),
                    color = if (c.level == null || faded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                if (c.level?.charging == true) Glyph(R.drawable.ic_bolt, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
            }
            Meter(c.level?.percent ?: 0, faded)
            if (c.note.isNotEmpty()) Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun Meter(percent: Int, faded: Boolean = false, height: Dp = 8.dp) {
    val c = if (percent in 1..20) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(LocalHearOnColors.current.track)) {
        if (percent > 0) Box(Modifier.fillMaxWidth(percent / 100f).height(height).clip(CircleShape).background(if (faded) c.copy(alpha = 0.5f) else c))
    }
}

// ---- listening mode: an M3 Expressive connected button group -------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModeGroup(selected: ListeningMode?, modes: List<ListeningMode>, enabled: Boolean = true, height: Dp = 76.dp, onSelect: (ListeningMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        modes.forEachIndexed { i, m ->
            ToggleButton(
                checked = m == selected,
                onCheckedChange = { onSelect(m) },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(height).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Glyph(m.icon, size = 22.dp)
                    Text(m.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 14.sp)
                }
            }
        }
    }
}

// ---- grouped settings rows (Android 16 style) --------------------------------------------------

fun groupShape(i: Int, n: Int, big: Dp = 22.dp, small: Dp = 6.dp) = when {
    n == 1 -> RoundedCornerShape(big)
    i == 0 -> RoundedCornerShape(big, big, small, small)
    i == n - 1 -> RoundedCornerShape(small, small, big, big)
    else -> RoundedCornerShape(small)
}

/** A titled group of rows; each row is a slot so they share the grouped shape. */
@Composable
fun Group(title: String? = null, rows: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, r -> r(Modifier.clip(groupShape(i, rows.size))) }
        }
    }
}

@Composable
fun SettingRow(
    modifier: Modifier, title: String, sub: String? = null, @DrawableRes lead: Int? = null,
    onClick: (() -> Unit)? = null, enabled: Boolean = true, trailing: @Composable (() -> Unit)? = null,
    below: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().background(LocalHearOnColors.current.card)
    val m = if (onClick != null && enabled) base.clickable(onClick = onClick) else base
    Row(
        m.heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (lead != null) Glyph(lead, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(560)),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (below != null) { Spacer(Modifier.height(6.dp)); below() }
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(modifier: Modifier, title: String, sub: String? = null, checked: Boolean?, @DrawableRes lead: Int? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) =
    SettingRow(modifier, title, sub, lead, onClick = { onChange(checked != true) }, enabled = enabled && checked != null) {
        Switch(
            checked = checked == true, onCheckedChange = { onChange(it) }, enabled = enabled && checked != null,
            thumbContent = if (checked == true) ({ Glyph(R.drawable.ic_check, size = 14.dp) }) else null,
            colors = SwitchDefaults.colors(),
        )
    }

@Composable
fun NavRow(modifier: Modifier, title: String, sub: String? = null, @DrawableRes lead: Int? = null, enabled: Boolean = true, onClick: () -> Unit) =
    SettingRow(modifier, title, sub, lead, onClick, enabled) { Glyph(R.drawable.ic_right, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

/** A small connected choice group (e.g. Gentle · Normal · Firm). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Choice(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected, onCheckedChange = { onSelect(i) }, enabled = enabled,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
fun rememberBool(initial: Boolean = false) = remember { mutableStateOf(initial) }

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SpacerW(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun windowWidthDp(): Dp {
    val info = LocalWindowInfo.current
    val view = LocalView.current
    val px = if (info.containerSize.width > 0) info.containerSize.width else view.width
    return with(LocalDensity.current) { px.toDp() }
}
