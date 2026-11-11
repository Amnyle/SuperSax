package app.supersax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.supersax.domain.backup.ThemeMode

// A calm palette: muted brass accent on neutral surfaces.
private val Brass = Color(0xFF8A6D2F)
private val BrassLight = Color(0xFFE4C98A)
private val Slate = Color(0xFF4F5B66)
private val SlateLight = Color(0xFFB9C6D1)
private val Sage = Color(0xFF55715E)
private val SageLight = Color(0xFFB6D2BE)

private val Light = lightColorScheme(
    primary = Brass, onPrimary = Color.White, primaryContainer = Color(0xFFF6E7C4), onPrimaryContainer = Color(0xFF2B2106),
    secondary = Slate, onSecondary = Color.White, secondaryContainer = Color(0xFFDDE5EC), onSecondaryContainer = Color(0xFF111D27),
    tertiary = Sage, onTertiary = Color.White, tertiaryContainer = Color(0xFFD5EADA), onTertiaryContainer = Color(0xFF0F2015),
    background = Color(0xFFFBF9F5), surface = Color(0xFFFBF9F5), surfaceVariant = Color(0xFFECE7DE),
)

private val Dark = darkColorScheme(
    primary = BrassLight, onPrimary = Color(0xFF3F2E00), primaryContainer = Color(0xFF5B4614), onPrimaryContainer = Color(0xFFF6E7C4),
    secondary = SlateLight, onSecondary = Color(0xFF22313D), secondaryContainer = Color(0xFF384754), onSecondaryContainer = Color(0xFFDDE5EC),
    tertiary = SageLight, onTertiary = Color(0xFF21362A), tertiaryContainer = Color(0xFF3A5142), onTertiaryContainer = Color(0xFFD5EADA),
    background = Color(0xFF14130F), surface = Color(0xFF14130F), surfaceVariant = Color(0xFF39352C),
)

/** Accent used for Master in the rank staircase. */
val MasterAccent = Color(0xFF9C6ADE)
val InTuneGreen = Color(0xFF4F9A63)
val OffPitchAmber = Color(0xFFD08A2E)

@Composable
fun SaxTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}
