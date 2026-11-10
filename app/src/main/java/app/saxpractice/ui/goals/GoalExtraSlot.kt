package app.saxpractice.ui.goals

import androidx.compose.runtime.Composable
import app.saxpractice.domain.common.GoalRecord

/** Extra goal controls (song goals get a piece link and stage once the library exists). */
@Composable
fun GoalExtraSlot(goal: GoalRecord, onUpdate: (GoalRecord) -> Unit) = Unit
