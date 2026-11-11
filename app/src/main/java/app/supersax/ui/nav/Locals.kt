package app.supersax.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import app.supersax.AppContainer
import app.supersax.domain.backup.SettingsRecord

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalSettings = staticCompositionLocalOf { SettingsRecord() }
