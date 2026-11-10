package app.saxpractice.ui.settings

import androidx.compose.runtime.Composable
import app.saxpractice.domain.backup.SettingsRecord

/** Opt-in reminders (added in phase 2). */
@Composable
fun RemindersSection(s: SettingsRecord, update: ((SettingsRecord) -> SettingsRecord) -> Unit) = Unit
