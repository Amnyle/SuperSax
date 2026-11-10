package app.saxpractice.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.saxpractice.R
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.ui.audio.MetronomeScreen
import app.saxpractice.ui.audio.TunerHistoryScreen
import app.saxpractice.ui.audio.TunerScreen
import app.saxpractice.ui.drills.MatchNoteScreen
import app.saxpractice.ui.drills.ReadingDrillScreen
import app.saxpractice.ui.hub.HubEntry
import app.saxpractice.ui.review.WeeklyReviewScreen

object ExtraRoutes {
    const val TUNER = "tuner"
    const val TUNER_HISTORY = "tuner_history"
    const val METRONOME = "metronome"
    const val MATCH = "match_note"
    const val READING = "reading_drill"
    const val REVIEW = "weekly_review"
}

/** Destinations of the listening, reading and later phases. */
fun NavGraphBuilder.extraDestinations(nav: NavHostController) {
    composable(ExtraRoutes.TUNER) { TunerScreen(onBack = { nav.popBackStack() }, onHistory = { nav.navigate(ExtraRoutes.TUNER_HISTORY) }) }
    composable(ExtraRoutes.TUNER_HISTORY) { TunerHistoryScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.METRONOME) { MetronomeScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.MATCH) { MatchNoteScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.READING) { ReadingDrillScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.REVIEW) { WeeklyReviewScreen(onBack = { nav.popBackStack() }) }
}

fun extraPhoneDrillRoute(type: ExerciseType): String? = when (type) {
    ExerciseType.READING -> ExtraRoutes.READING
    else -> null
}

@Composable
fun extraProgressEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.weekly_review), stringResource(R.string.weekly_review_sub), Icons.Default.Summarize) { nav.navigate(ExtraRoutes.REVIEW) },
)

@Composable
fun extraToolEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.tuner), stringResource(R.string.tuner_sub), Icons.Default.GraphicEq) { nav.navigate(ExtraRoutes.TUNER) },
    HubEntry(stringResource(R.string.metronome_drone), stringResource(R.string.metronome_sub), Icons.Default.Timer) { nav.navigate(ExtraRoutes.METRONOME) },
    HubEntry(stringResource(R.string.match_note), stringResource(R.string.match_sub), Icons.Default.Hearing) { nav.navigate(ExtraRoutes.MATCH) },
    HubEntry(stringResource(R.string.ex_reading), stringResource(R.string.reading_sub), Icons.AutoMirrored.Filled.MenuBook) { nav.navigate(ExtraRoutes.READING) },
)

