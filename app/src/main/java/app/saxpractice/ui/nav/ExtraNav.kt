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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Inventory2
import app.saxpractice.ui.library.LibraryScreen
import app.saxpractice.ui.library.PieceScreen
import app.saxpractice.ui.library.PdfScreen
import app.saxpractice.ui.media.RecordingsScreen
import app.saxpractice.ui.media.GearScreen
import app.saxpractice.ui.rank.RankScreen
import app.saxpractice.ui.learn.LessonsScreen
import app.saxpractice.ui.learn.LessonScreen
import app.saxpractice.ui.learn.ScaleRecognitionScreen
import app.saxpractice.ui.learn.FingeringScreen
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TouchApp
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
    const val LIBRARY = "library"
    const val PIECE = "piece/{id}"
    const val PDF = "pdf/{file}"
    const val RECORDINGS = "recordings?piece={piece}"
    const val GEAR = "gear"
    const val RANK = "rank"
    const val LESSONS = "lessons"
    const val LESSON = "lesson/{id}"
    const val SCALE_REC = "scale_recognition"
    const val FINGERING = "fingering"
    fun lesson(id: String) = "lesson/$id"
    fun piece(id: String) = "piece/$id"
    fun pdf(file: String) = "pdf/$file"
    fun recordings(pieceId: String? = null) = if (pieceId == null) "recordings" else "recordings?piece=$pieceId"
}

/** Destinations of the listening, reading and later phases. */
fun NavGraphBuilder.extraDestinations(nav: NavHostController) {
    composable(ExtraRoutes.TUNER) { TunerScreen(onBack = { nav.popBackStack() }, onHistory = { nav.navigate(ExtraRoutes.TUNER_HISTORY) }) }
    composable(ExtraRoutes.TUNER_HISTORY) { TunerHistoryScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.METRONOME) { MetronomeScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.MATCH) { MatchNoteScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.READING) { ReadingDrillScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.REVIEW) { WeeklyReviewScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.LIBRARY) { LibraryScreen(onPiece = { nav.navigate(ExtraRoutes.piece(it)) }, onPdf = { nav.navigate(ExtraRoutes.pdf(it)) }) }
    composable(ExtraRoutes.PIECE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
        PieceScreen(e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }, onPdf = { nav.navigate(ExtraRoutes.pdf(it)) },
            onRecord = { nav.navigate(ExtraRoutes.recordings(it)) })
    }
    composable(ExtraRoutes.PDF, arguments = listOf(navArgument("file") { type = NavType.StringType })) { e ->
        PdfScreen(e.arguments!!.getString("file")!!, onBack = { nav.popBackStack() })
    }
    composable(ExtraRoutes.RECORDINGS, arguments = listOf(navArgument("piece") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
        RecordingsScreen(e.arguments?.getString("piece"), onBack = { nav.popBackStack() })
    }
    composable(ExtraRoutes.GEAR) { GearScreen(onBack = { nav.popBackStack() }, onCompare = { nav.navigate(ExtraRoutes.recordings()) }) }
    composable(ExtraRoutes.RANK) { RankScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.LESSONS) { LessonsScreen(onBack = { nav.popBackStack() }, onLesson = { nav.navigate(ExtraRoutes.lesson(it)) }) }
    composable(ExtraRoutes.LESSON, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
        LessonScreen(e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
    }
    composable(ExtraRoutes.SCALE_REC) { ScaleRecognitionScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.FINGERING) { FingeringScreen(onBack = { nav.popBackStack() }) }
}

fun extraPhoneDrillRoute(type: ExerciseType): String? = when (type) {
    ExerciseType.READING -> ExtraRoutes.READING
    ExerciseType.SCALE_RECOGNITION -> ExtraRoutes.SCALE_REC
    ExerciseType.THEORY_LESSON -> ExtraRoutes.LESSONS
    ExerciseType.FINGERING -> ExtraRoutes.FINGERING
    else -> null
}

@Composable
fun extraProgressEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.rank), stringResource(R.string.rank_sub), Icons.Default.Leaderboard) { nav.navigate(ExtraRoutes.RANK) },
    HubEntry(stringResource(R.string.recordings), stringResource(R.string.recordings_sub), Icons.Default.Mic) { nav.navigate(ExtraRoutes.recordings()) },
    HubEntry(stringResource(R.string.gear), stringResource(R.string.gear_sub), Icons.Default.Inventory2) { nav.navigate(ExtraRoutes.GEAR) },
    HubEntry(stringResource(R.string.weekly_review), stringResource(R.string.weekly_review_sub), Icons.Default.Summarize) { nav.navigate(ExtraRoutes.REVIEW) },
)

@Composable
fun extraToolEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.tuner), stringResource(R.string.tuner_sub), Icons.Default.GraphicEq) { nav.navigate(ExtraRoutes.TUNER) },
    HubEntry(stringResource(R.string.metronome_drone), stringResource(R.string.metronome_sub), Icons.Default.Timer) { nav.navigate(ExtraRoutes.METRONOME) },
    HubEntry(stringResource(R.string.match_note), stringResource(R.string.match_sub), Icons.Default.Hearing) { nav.navigate(ExtraRoutes.MATCH) },
    HubEntry(stringResource(R.string.ex_reading), stringResource(R.string.reading_sub), Icons.AutoMirrored.Filled.MenuBook) { nav.navigate(ExtraRoutes.READING) },
    HubEntry(stringResource(R.string.ex_scale_recognition), stringResource(R.string.scale_rec_sub), Icons.Default.Piano) { nav.navigate(ExtraRoutes.SCALE_REC) },
    HubEntry(stringResource(R.string.lessons), stringResource(R.string.lessons_sub), Icons.Default.School) { nav.navigate(ExtraRoutes.LESSONS) },
    HubEntry(stringResource(R.string.ex_fingering), stringResource(R.string.fingering_sub), Icons.Default.TouchApp) { nav.navigate(ExtraRoutes.FINGERING) },
)

