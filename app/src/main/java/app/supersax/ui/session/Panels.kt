package app.supersax.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.planner.PlannedStep
import app.supersax.domain.session.SessionRun
import app.supersax.ui.components.SectionCard

/** Scale block: clean-run / mistake logging (the metronome panel is added in phase 2). */
@Composable
fun ScalePanel(step: PlannedStep, run: SessionRun, vm: SessionViewModel) {
    val key = step.target.scaleKey ?: return
    val bpm = step.target.bpm ?: 60
    SectionCard(stringResource(R.string.scale_runs_title)) {
        Text(stringResource(R.string.scale_runs_hint), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.scaleRun(key, bpm, clean = true) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.clean_run))
            }
            OutlinedButton(onClick = { vm.scaleRun(key, bpm, clean = false) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.mistake))
            }
        }
        val streak = run.cleanStreak(key)
        val total = run.runs.count { it.scaleKey == key }
        Text(stringResource(R.string.runs_summary, total, streak), style = MaterialTheme.typography.bodyMedium)
        MetronomeSlot(bpm)
    }
}

/** Long-tone block: the hold target and an optional tuner (phase 2). */
@Composable
fun LongTonePanel(step: PlannedStep) {
    SectionCard(stringResource(R.string.long_tone_panel)) {
        Text(stringResource(R.string.long_tone_hint, step.target.holdSec ?: 0), style = MaterialTheme.typography.bodyMedium)
        TunerSlot(step)
    }
}

/** Breathing pacer: in for 4 counts, out for 8 (one count per second). */
@Composable
fun BreathingPanel(nowMs: Long) {
    val phase = ((nowMs / 1000) % 12).toInt()
    val inhale = phase < 4
    val count = if (inhale) phase + 1 else phase - 3
    SectionCard {
        Text(
            stringResource(if (inhale) R.string.breathe_in else R.string.breathe_out, count),
            style = MaterialTheme.typography.displaySmall,
        )
    }
}
