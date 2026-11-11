package app.supersax.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.supersax.R
import app.supersax.domain.backup.SettingsRecord
import app.supersax.ui.components.Stepper
import app.supersax.work.Reminders

/** Opt-in reminders: off by default; notification permission is asked only when turned on. */
@Composable
fun RemindersSection(s: SettingsRecord, update: ((SettingsRecord) -> SettingsRecord) -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) update { it.copy(remindersEnabled = false) }
    }
    LaunchedEffect(s.remindersEnabled, s.reminderHour, s.reminderMinute) {
        Reminders.sync(context, s.remindersEnabled, s.reminderHour, s.reminderMinute)
    }
    ToggleRow(stringResource(R.string.reminders), stringResource(R.string.reminders_hint), s.remindersEnabled) { on ->
        update { it.copy(remindersEnabled = on) }
        if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (s.remindersEnabled) {
        Stepper(stringResource(R.string.reminder_hour), s.reminderHour, { v -> update { it.copy(reminderHour = v) } }, 0..23) { "%02d".format(it) }
        Stepper(stringResource(R.string.reminder_minute), s.reminderMinute, { v -> update { it.copy(reminderMinute = v) } }, 0..55, 5) { "%02d".format(it) }
        if (!Reminders.canNotify(context)) Text(stringResource(R.string.reminders_no_permission), color = MaterialTheme.colorScheme.error)
    }
}
