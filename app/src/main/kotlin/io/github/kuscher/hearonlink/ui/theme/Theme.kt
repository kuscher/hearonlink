package io.github.kuscher.hearonlink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.kuscher.hearonlink.R
import io.github.kuscher.hearonlink.data.Settings

/** HearOn Sans (Google Sans Flex, OFL, renamed): a text cut and a rounded display cut. */
object HearOnFonts {
    private val weights = listOf(400, 500, 600, 700, 800)

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun family(rond: Int, opsz: Float) = FontFamily(weights.map { w ->
        Font(
            R.font.hearon_sans, FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w), FontVariation.Setting("ROND", rond.toFloat()), FontVariation.Setting("opsz", opsz),
            ),
        )
    })

    val text = family(0, 16f)
    val round = family(100, 18f)
    val display = family(100, 40f)
}

private fun typography(): Typography {
    val b = Typography()
    fun TextStyle.t() = copy(fontFamily = HearOnFonts.text)
    fun TextStyle.d(w: Int) = copy(fontFamily = HearOnFonts.display, fontWeight = FontWeight(w))
    return b.copy(
        displayLarge = b.displayLarge.d(760), displayMedium = b.displayMedium.d(760), displaySmall = b.displaySmall.d(740),
        headlineLarge = b.headlineLarge.d(740), headlineMedium = b.headlineMedium.d(720), headlineSmall = b.headlineSmall.d(720),
        titleLarge = b.titleLarge.copy(fontFamily = HearOnFonts.round, fontWeight = FontWeight(680)),
        titleMedium = b.titleMedium.copy(fontFamily = HearOnFonts.round, fontWeight = FontWeight(620)),
        titleSmall = b.titleSmall.copy(fontFamily = HearOnFonts.round, fontWeight = FontWeight(650)),
        bodyLarge = b.bodyLarge.t(), bodyMedium = b.bodyMedium.t(), bodySmall = b.bodySmall.t(),
        labelLarge = b.labelLarge.copy(fontFamily = HearOnFonts.round, fontWeight = FontWeight(620)),
        labelMedium = b.labelMedium.copy(fontFamily = HearOnFonts.round, fontWeight = FontWeight(600)),
        labelSmall = b.labelSmall.t(),
    )
}

/** PodLink teal, the fallback when wallpaper colours aren't used. */
private val TealLight = lightColorScheme(
    primary = Color(0xFF00696B), onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EEEE), onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6362), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E6E3), onSecondaryContainer = Color(0xFF102220),
    tertiary = Color(0xFF9C4230), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD2), onTertiaryContainer = Color(0xFF3B0900),
    background = Color(0xFFF5F9F8), onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF5F9F8), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3E4947),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainer = Color(0xFFE8F0EE), surfaceContainerHigh = Color(0xFFE2EAE8), surfaceContainerHighest = Color(0xFFDCE4E2),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFC4D0CD),
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF6FD6D7), onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51), onPrimaryContainer = Color(0xFFB2EEEE),
    secondary = Color(0xFFB0CCC9), onSecondary = Color(0xFF1B3533),
    secondaryContainer = Color(0xFF2E4644), onSecondaryContainer = Color(0xFFCDE8E5),
    tertiary = Color(0xFFFFB4A2), onTertiary = Color(0xFF5C1F12),
    tertiaryContainer = Color(0xFF7E2B1B), onTertiaryContainer = Color(0xFFFFDBD2),
    background = Color(0xFF0F1514), onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0F1514), onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBAC9C6),
    surfaceContainerLowest = Color(0xFF1C2625), surfaceContainerLow = Color(0xFF151D1C),
    surfaceContainer = Color(0xFF161E1D), surfaceContainerHigh = Color(0xFF223030), surfaceContainerHighest = Color(0xFF2B3634),
    outline = Color(0xFF899392), outlineVariant = Color(0xFF3A4745),
)

/** Two colours the M3 scheme doesn't name: the "No" of the gesture demo and the card fill. */
@Immutable
data class HearOnColors(val no: Color, val onNo: Color, val card: Color, val chrome: Color, val track: Color)

val LocalHearOnColors = staticCompositionLocalOf { HearOnColors(Color.Red, Color.White, Color.White, Color.LightGray, Color.Gray) }

fun schemeFor(settings: Settings, dark: Boolean, context: android.content.Context): ColorScheme =
    if (settings.theme == "wallpaper") (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
    else if (dark) TealDark else TealLight

@Composable
fun isDark(settings: Settings) = when (settings.dark) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HearOnTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val c = schemeFor(settings, dark, LocalContext.current)
    val extra = HearOnColors(
        no = c.tertiaryContainer, onNo = c.onTertiaryContainer,
        card = c.surfaceContainerLowest, chrome = c.surfaceContainer, track = c.surfaceContainerHighest,
    )
    CompositionLocalProvider(LocalHearOnColors provides extra) {
        MaterialExpressiveTheme(colorScheme = c, motionScheme = MotionScheme.expressive(), typography = typography(), content = content)
    }
}
