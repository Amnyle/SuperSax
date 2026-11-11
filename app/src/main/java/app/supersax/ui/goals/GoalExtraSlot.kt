package app.supersax.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.common.GoalRecord
import app.supersax.domain.common.GoalType
import app.supersax.domain.common.PieceStage
import app.supersax.ui.library.PiecePickerDialog
import app.supersax.ui.nav.LocalContainer

/** Song goals: link a library piece, optionally one section, and the stage to reach. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalExtraSlot(goal: GoalRecord, onUpdate: (GoalRecord) -> Unit) {
    if (goal.type != GoalType.SONG_MILESTONE) return
    val c = LocalContainer.current
    val language = LocaleHelper.effective(LocalContext.current)
    var pick by remember { mutableStateOf(false) }
    val piece = c.libraryService.piece(goal.pieceId)
    TextButton(onClick = { pick = true }) {
        Text(piece?.let { stringResource(R.string.goal_piece, it.file.title.get(language)) } ?: stringResource(R.string.goal_link_piece))
    }
    if (piece != null) {
        if (piece.file.sections.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = goal.sectionId == null, onClick = { onUpdate(goal.copy(sectionId = null)) }, label = { Text(stringResource(R.string.whole_piece)) })
            piece.file.sections.forEach { s ->
                FilterChip(selected = goal.sectionId == s.id, onClick = { onUpdate(goal.copy(sectionId = s.id)) }, label = { Text(s.title.get(language)) })
            }
        }
        Text(stringResource(R.string.goal_target_stage), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PieceStage.entries.drop(1).forEach { s ->
                FilterChip(selected = goal.stage == s.name, onClick = { onUpdate(goal.copy(stage = s.name)) }, label = { Text(stageLabel(s)) })
            }
        }
    }
    if (pick) PiecePickerDialog(onPick = { onUpdate(goal.copy(pieceId = it.id, sectionId = null)); pick = false }, onDismiss = { pick = false }, withNotesOnly = false)
}
