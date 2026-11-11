package app.supersax.domain.goals

import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.RecordingRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.program.PlayerState

/** Computed progress of a goal. [fraction] is 0..1; [current] and [target] are in the goal's unit. */
data class GoalProgress(val fraction: Double, val done: Boolean, val current: Int, val target: Int)

/** Everything goal progress is computed from. */
data class GoalInputs(
    val sessions: List<SessionRecord>,
    val player: PlayerState,
    val pieceProgress: List<PieceProgressRecord>,
    val recordings: List<RecordingRecord>,
)

