package app.saxpractice.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import app.saxpractice.AppContainer
import app.saxpractice.domain.backup.SettingsRecord

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalSettings = staticCompositionLocalOf { SettingsRecord() }
