package app.saxpractice.ui.session

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.LocaleHelper
import app.saxpractice.R
import app.saxpractice.domain.planner.StepKind
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.blockKindLabel
import app.saxpractice.ui.components.exerciseTypeLabel
import app.saxpractice.ui.components.formatClock
import app.saxpractice.ui.components.scaleLabel
import app.saxpractice.ui.nav.appViewModel

/**
 * The session player: current block, countdown, target, Next / Pause / +1 min / Lips tired.
 * Keeps the screen on; stand mode switches to landscape with large text.
 */
@Composable
fun SessionScreen(onFinished: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { SessionViewModel(it) }
    val run by vm.run.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val bump by vm.bumpOffer.collectAsStateWithLifecycle()
    var stand by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(stand) {
        val activity = context as? Activity
        activity?.requestedOrientation = if (stand) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val r = run
    LaunchedEffect(r?.finished) { if (r?.finished == true) onFinished() }
    if (r == null) { LaunchedEffect(Unit) { onBack() }; return }
    val step = r.step ?: return
    val language = LocaleHelper.effective(context)
    val scale = if (stand) 1.6f else 1f

    ScreenScaffold(
        title = stringResource(R.string.step_of, r.current + 1, r.steps.size),
        onBack = onBack,
        actions = {
            IconButton(onClick = { stand = !stand }) {
                Icon(if (stand) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    stringResource(if (stand) R.string.stand_mode_off else R.string.stand_mode_on))
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isRest = step.kind == StepKind.REST
            Text(
                if (isRest) stringResource(if (step.enforced) R.string.rest_enforced else R.string.rest) else blockKindLabel(step.blockKind),
                style = MaterialTheme.typography.titleMedium, fontSize = MaterialTheme.typography.titleMedium.fontSize * scale,
            )
            if (!isRest) {
                val title = vm.exerciseInfo(step.catalogId)?.title?.get(language) ?: exerciseTypeLabel(step.exerciseType)
                Text(
                    title + if (step.chunks > 1) " (${step.chunk}/${step.chunks})" else "",
                    style = MaterialTheme.typography.headlineSmall, fontSize = MaterialTheme.typography.headlineSmall.fontSize * scale,
                )
            }
            val remaining = r.remainingMs(now)
            Text(
                formatClock((remaining + 999) / 1000),
                fontSize = (72 * scale).sp, fontWeight = FontWeight.Light,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            LinearProgressIndicator(
                progress = { 1f - remaining.toFloat() / r.stepLengthMs().coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            TargetLine(step.target.scaleKey, step.target.bpm, step.target.holdSec, scale)

            ControlRow(
                paused = r.isPaused,
                canSkip = r.canSkip(now),
                playing = !isRest && step.exerciseType?.isPlaying == true,
                onPause = vm::pause, onResume = vm::resume, onAdd = vm::addMinute, onNext = vm::next, onTired = vm::lipsTired,
            )

            if (!isRest) {
                when (step.exerciseType) {
                    ExerciseType.SCALE -> ScalePanel(step, r, vm)
                    ExerciseType.LONG_TONE, ExerciseType.RANGE_EXTENSION -> LongTonePanel(step)
                    ExerciseType.BREATHING_PACER -> BreathingPanel(now)
                    else -> Unit
                }
                vm.exerciseInfo(step.catalogId)?.let { info ->
                    Text(info.description.get(language), style = MaterialTheme.typography.bodyLarge, fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale)
                    info.tips.forEach { Text("• " + it.get(language), style = MaterialTheme.typography.bodyMedium) }
                }
                PiecePanel(step)
            } else {
                Text(stringResource(R.string.rest_hint), style = MaterialTheme.typography.bodyLarge)
                r.steps.getOrNull(r.current + 1)?.let { nextStep ->
                    Text(stringResource(R.string.up_next, blockKindLabel(nextStep.blockKind)), style = MaterialTheme.typography.titleMedium)
                }
            }
            OutlinedButton(onClick = vm::endEarly, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.end_session)) }
        }
    }

    bump?.let { (key, bpm) ->
        AlertDialog(
            onDismissRequest = vm::dismissBump,
            title = { Text(stringResource(R.string.bump_title)) },
            text = { Text(stringResource(R.string.bump_body, scaleLabel(key), bpm)) },
            confirmButton = { Button(onClick = vm::acceptBump) { Text(stringResource(R.string.bump_now, bpm)) } },
            dismissButton = { TextButton(onClick = vm::dismissBump) { Text(stringResource(R.string.bump_later)) } },
        )
    }
}

@Composable
private fun TargetLine(scaleKey: String?, bpm: Int?, holdSec: Int?, scale: Float) {
    val parts = buildList {
        if (scaleKey != null) add(scaleLabel(scaleKey))
        if (bpm != null) add(stringResource(R.string.bpm_value, bpm))
        if (holdSec != null) add(stringResource(R.string.hold_target, holdSec))
    }
    if (parts.isNotEmpty()) Text(
        parts.joinToString(" · "), style = MaterialTheme.typography.titleLarge,
        fontSize = MaterialTheme.typography.titleLarge.fontSize * scale, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ControlRow(
    paused: Boolean,
    canSkip: Boolean,
    playing: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAdd: () -> Unit,
    onNext: () -> Unit,
    onTired: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = if (paused) onResume else onPause, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Text(stringResource(if (paused) R.string.resume else R.string.pause))
        }
        FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.plus_one_min)) }
        Button(onClick = onNext, enabled = canSkip, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.next)) }
    }
    if (playing) OutlinedButton(onClick = onTired, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.lips_tired))
    }
}
