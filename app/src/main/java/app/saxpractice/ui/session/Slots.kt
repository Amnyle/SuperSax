package app.saxpractice.ui.session

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.saxpractice.R
import app.saxpractice.domain.planner.PlannedStep
import app.saxpractice.domain.program.ExerciseType

/** Metronome inside a scale block. */
@Composable
fun MetronomeSlot(bpm: Int) = Unit

/** Optional tuner inside a long-tone block. */
@Composable
fun TunerSlot(step: PlannedStep) = Unit

/** Piece shown in new-material and play-for-fun blocks. */
@Composable
fun PiecePanel(step: PlannedStep) {
    if (step.exerciseType == ExerciseType.NEW_MATERIAL || step.exerciseType == ExerciseType.PLAY_FOR_FUN) {
        Text(stringResource(R.string.piece_free_choice), style = MaterialTheme.typography.bodyMedium)
    }
}
