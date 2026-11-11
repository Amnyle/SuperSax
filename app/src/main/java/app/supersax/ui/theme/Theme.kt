package app.supersax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.supersax.R
import app.supersax.domain.backup.ThemeMode

/**
 * Modern look (same family as SuperHealth): a cool light-grey background, plain white cards without borders,
 * large radii, Google Sans Flex, and one warm accent: saxophone amber.
 */
@Immutable
data class SaxColors(
    val dark: Boolean,
    val background: Color,
    /** cards and lists */
    val card: Color,
    /** blocks placed on a card, tracks, neutral buttons */
    val elevated: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    /** the bright amber: main buttons, the hero card, the selected tab */
    val accent: Color,
    /** ink placed on [accent] */
    val onAccent: Color,
    /** accent readable as text or icon on a card */
    val accentInk: Color,
    /** a soft amber tint for chips and icon tiles */
    val accentSoft: Color,
    val good: Color,
    val warn: Color,
)

/** Saxophone amber (bright) and its deep gold for text on white. */
val Amber = Color(0xFFF7B500)
private val AmberInk = Color(0xFF8A5F00)
private val AmberInkDark = Color(0xFFFFC94A)

private val LightSax = SaxColors(
    dark = false, background = Color(0xFFF1F3F8), card = Color.White, elevated = Color(0xFFE9ECF3),
    text = Color(0xFF1B1C20), textSecondary = Color(0xFF5E636D), textTertiary = Color(0xFFA0A5AE), divider = Color(0xFFE2E5EC),
    accent = Amber, onAccent = Color(0xFF1C1607), accentInk = AmberInk, accentSoft = Color(0xFFFFF1CC),
    good = Color(0xFF34A853), warn = Color(0xFFF2803A),
)

private val DarkSax = SaxColors(
    dark = true, background = Color(0xFF111318), card = Color(0xFF1D2026), elevated = Color(0xFF282B32),
    text = Color(0xFFE3E4E9), textSecondary = Color(0xFFA9ADB7), textTertiary = Color(0xFF6B707A), divider = Color(0xFF30333A),
    accent = Amber, onAccent = Color(0xFF1C1607), accentInk = AmberInkDark, accentSoft = Color(0xFF3A3220),
    good = Color(0xFF5BC27A), warn = Color(0xFFF59A5C),
)

val LocalSax = staticCompositionLocalOf { LightSax }

object Sax {
    val colors: SaxColors @Composable get() = LocalSax.current
}

/** Accent used for Master in the rank staircase. */
val MasterAccent = Color(0xFF9C6ADE)
val InTuneGreen = Color(0xFF4F9A63)
val OffPitchAmber = Color(0xFFD08A2E)

/** Card radius (cards and grouped lists) and the smaller radius of fields and tiles. */
val CardRadius = 24.dp
val FieldRadius = 14.dp

@OptIn(ExperimentalTextApi::class)
private fun sans(weight: Int, opsz: Float) = Font(
    R.font.sans_flex, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

private fun family(opsz: Float) = FontFamily(sans(400, opsz), sans(500, opsz), sans(600, opsz), sans(700, opsz))

/** Body text (optical size 14) and titles and big numbers (optical size 28). */
private val SansText = family(14f)
private val SansDisplay = family(28f)

private fun typography(): Typography {
    val text = TextStyle(fontFamily = SansText, letterSpacing = 0.sp)
    val display = TextStyle(fontFamily = SansDisplay, letterSpacing = 0.sp)
    val digits = display.copy(fontFeatureSettings = "tnum")
    return Typography(
        displayLarge = digits.copy(fontSize = 52.sp, fontWeight = FontWeight.Medium, lineHeight = 58.sp, letterSpacing = (-1).sp),
        displayMedium = digits.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 46.sp, letterSpacing = (-0.7).sp),
        displaySmall = digits.copy(fontSize = 32.sp, fontWeight = FontWeight.Medium, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
        headlineLarge = display.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 38.sp),
        headlineMedium = display.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
        headlineSmall = display.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
        titleLarge = text.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium, lineHeight = 25.sp),
        titleMedium = text.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        titleSmall = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = text.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp, letterSpacing = 0.1.sp),
        bodyMedium = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.15.sp),
        bodySmall = text.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.2.sp),
        labelLarge = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = text.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        labelSmall = text.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(FieldRadius),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(CardRadius),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SaxTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val s = if (dark) DarkSax else LightSax
    // Material roles mapped onto the palette: primary is the readable gold (text buttons, switches, sliders),
    // primaryContainer the bright amber (filled main actions).
    val scheme = if (dark) darkColorScheme(
        primary = s.accentInk, onPrimary = s.onAccent, primaryContainer = s.accent, onPrimaryContainer = s.onAccent,
        secondary = s.accentInk, onSecondary = s.onAccent, secondaryContainer = s.accentSoft, onSecondaryContainer = s.text,
        tertiary = s.good, onTertiary = Color.Black, tertiaryContainer = s.elevated, onTertiaryContainer = s.text,
        background = s.background, onBackground = s.text, surface = s.card, onSurface = s.text,
        surfaceVariant = s.elevated, onSurfaceVariant = s.textSecondary, surfaceContainerLowest = s.background,
        surfaceContainerLow = s.card, surfaceContainer = s.card, surfaceContainerHigh = s.elevated, surfaceContainerHighest = s.elevated,
        outline = Color(0xFF8E9199), outlineVariant = s.divider, error = Color(0xFFFF6B5E),
    ) else lightColorScheme(
        primary = s.accentInk, onPrimary = Color.White, primaryContainer = s.accent, onPrimaryContainer = s.onAccent,
        secondary = s.accentInk, onSecondary = Color.White, secondaryContainer = s.accentSoft, onSecondaryContainer = s.text,
        tertiary = s.good, onTertiary = Color.White, tertiaryContainer = s.elevated, onTertiaryContainer = s.text,
        background = s.background, onBackground = s.text, surface = s.card, onSurface = s.text,
        surfaceVariant = s.elevated, onSurfaceVariant = s.textSecondary, surfaceContainerLowest = Color.White,
        surfaceContainerLow = s.card, surfaceContainer = s.card, surfaceContainerHigh = s.card, surfaceContainerHighest = s.elevated,
        outline = Color(0xFF747880), outlineVariant = s.divider, error = Color(0xFFD93025),
    )
    CompositionLocalProvider(LocalSax provides s) {
        MaterialTheme(colorScheme = scheme, typography = remember { typography() }, shapes = shapes) {
            CompositionLocalProvider(LocalContentColor provides s.text, content = content)
        }
    }
}

/** Colour of phone-only training (no saxophone needed), so the two kinds read apart at a glance. */
val PhoneBlue = androidx.compose.ui.graphics.Color(0xFF4285F4)
