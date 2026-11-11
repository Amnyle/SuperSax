package app.supersax.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Inventory2
import app.supersax.ui.library.LibraryScreen
import app.supersax.ui.library.PieceScreen
import app.supersax.ui.library.PdfScreen
import app.supersax.ui.media.RecordingsScreen
import app.supersax.ui.media.GearScreen
import app.supersax.ui.rank.RankScreen
import app.supersax.ui.learn.LessonsScreen
import app.supersax.ui.learn.LessonScreen
import app.supersax.ui.learn.FingeringChartScreen
import app.supersax.domain.common.DrillKind
import app.supersax.ui.practice.ChartAction
import app.supersax.ui.practice.DailyScreen
import app.supersax.ui.practice.LevelsScreen
import app.supersax.ui.practice.PlayLevelScreen
import app.supersax.ui.advanced.OvertoneScreen
import app.supersax.ui.advanced.AltissimoScreen
import app.supersax.ui.advanced.SlowDownScreen
import app.supersax.ui.advanced.ScaleCheckerScreen
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TouchApp
import app.supersax.R
import app.supersax.domain.program.ExerciseType
import app.supersax.ui.audio.MetronomeScreen
import app.supersax.ui.audio.TunerHistoryScreen
import app.supersax.ui.audio.TunerScreen
import app.supersax.ui.drills.MatchNoteScreen
import app.supersax.ui.hub.HubEntry
import app.supersax.ui.review.WeeklyReviewScreen

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
    const val FINGERING_CHART = "fingering_chart"
    const val DAILY = "daily"
    const val PLAY = "play/{kind}/{level}"
    fun play(kind: DrillKind, level: Int) = "play/${kind.name}/$level"
    fun lesson(id: String) = "lesson/$id"
    const val OVERTONES = "overtones"
    const val ALTISSIMO = "altissimo"
    const val SLOW_DOWN = "slow_down"
    const val SCALE_CHECKER = "scale_checker"
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
    composable(ExtraRoutes.READING) { LevelsScreen(DrillKind.READING, { nav.popBackStack() }, { nav.navigate(ExtraRoutes.play(DrillKind.READING, it)) }) }
    composable(ExtraRoutes.PLAY, arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("level") { type = NavType.IntType })) { e ->
        val kind = DrillKind.valueOf(e.arguments!!.getString("kind")!!)
        val level = e.arguments!!.getInt("level")
        PlayLevelScreen(kind, level, onClose = { nav.popBackStack() }, onLevel = { l ->
            nav.navigate(ExtraRoutes.play(kind, l)) { popUpTo(e.destination.id) { inclusive = true } }
        })
    }
    composable(ExtraRoutes.DAILY) { DailyScreen(onClose = { nav.popBackStack() }) }
    composable(ExtraRoutes.FINGERING_CHART) { FingeringChartScreen(onBack = { nav.popBackStack() }) }
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
        LessonScreen(e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }, onLesson = { id ->
            nav.navigate(ExtraRoutes.lesson(id)) { popUpTo(e.destination.id) { inclusive = true } }
        })
    }
    composable(ExtraRoutes.SCALE_REC) {
        LevelsScreen(DrillKind.SCALE_RECOGNITION, { nav.popBackStack() }, { nav.navigate(ExtraRoutes.play(DrillKind.SCALE_RECOGNITION, it)) })
    }
    composable(ExtraRoutes.FINGERING) {
        LevelsScreen(DrillKind.FINGERING, { nav.popBackStack() }, { nav.navigate(ExtraRoutes.play(DrillKind.FINGERING, it)) },
            actions = { ChartAction { nav.navigate(ExtraRoutes.FINGERING_CHART) } })
    }
    composable(ExtraRoutes.OVERTONES) { OvertoneScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.ALTISSIMO) { AltissimoScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.SLOW_DOWN) { SlowDownScreen(onBack = { nav.popBackStack() }) }
    composable(ExtraRoutes.SCALE_CHECKER) { ScaleCheckerScreen(onBack = { nav.popBackStack() }) }
}

fun extraPhoneDrillRoute(type: ExerciseType): String? = when (type) {
    ExerciseType.READING -> ExtraRoutes.READING
    ExerciseType.SCALE_RECOGNITION -> ExtraRoutes.SCALE_REC
    ExerciseType.THEORY_LESSON -> ExtraRoutes.LESSONS
    ExerciseType.FINGERING -> ExtraRoutes.FINGERING
    else -> null
}

/** Progress: where you stand, and what you logged. */
@Composable
fun extraProgressEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.rank), stringResource(R.string.rank_sub), Icons.Rounded.Leaderboard) { nav.navigate(ExtraRoutes.RANK) },
    HubEntry(stringResource(R.string.weekly_review), stringResource(R.string.weekly_review_sub), Icons.Rounded.Summarize) { nav.navigate(ExtraRoutes.REVIEW) },
)

@Composable
fun extraLogEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.recordings), stringResource(R.string.recordings_sub), Icons.Rounded.Mic) { nav.navigate(ExtraRoutes.recordings()) },
    HubEntry(stringResource(R.string.gear), stringResource(R.string.gear_sub), Icons.Rounded.Inventory2) { nav.navigate(ExtraRoutes.GEAR) },
)

/** Training that needs the saxophone in hand. */
@Composable
fun saxTrainingEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.tuner), stringResource(R.string.tuner_sub), Icons.Rounded.GraphicEq) { nav.navigate(ExtraRoutes.TUNER) },
    HubEntry(stringResource(R.string.metronome_drone), stringResource(R.string.metronome_sub), Icons.Rounded.Timer) { nav.navigate(ExtraRoutes.METRONOME) },
    HubEntry(stringResource(R.string.match_note), stringResource(R.string.match_sub), Icons.Rounded.Hearing) { nav.navigate(ExtraRoutes.MATCH) },
    HubEntry(stringResource(R.string.overtones), stringResource(R.string.overtones_sub), Icons.Rounded.Waves) { nav.navigate(ExtraRoutes.OVERTONES) },
    HubEntry(stringResource(R.string.altissimo), stringResource(R.string.altissimo_sub), Icons.Rounded.North) { nav.navigate(ExtraRoutes.ALTISSIMO) },
    HubEntry(stringResource(R.string.slow_down), stringResource(R.string.slow_down_sub), Icons.Rounded.SlowMotionVideo) { nav.navigate(ExtraRoutes.SLOW_DOWN) },
) + if (LocalSettings.current.experimental) listOf(
    HubEntry(stringResource(R.string.scale_checker), stringResource(R.string.scale_checker_sub), Icons.Rounded.Science) { nav.navigate(ExtraRoutes.SCALE_CHECKER) },
) else emptyList()

/** Training done on the phone alone (no saxophone needed). */
@Composable
fun phoneTrainingEntries(nav: NavHostController): List<HubEntry> {
    val blue = app.supersax.ui.theme.PhoneBlue
    return listOf(
        HubEntry(stringResource(R.string.daily_title), stringResource(R.string.daily_sub), Icons.Rounded.LocalFireDepartment, app.supersax.ui.theme.Sax.colors.warn) {
            nav.navigate(ExtraRoutes.DAILY)
        },
        HubEntry(stringResource(R.string.ex_reading), stringResource(R.string.reading_sub), Icons.AutoMirrored.Rounded.MenuBook, blue) { nav.navigate(ExtraRoutes.READING) },
        HubEntry(stringResource(R.string.ex_scale_recognition), stringResource(R.string.scale_rec_sub), Icons.Rounded.Piano, blue) { nav.navigate(ExtraRoutes.SCALE_REC) },
        HubEntry(stringResource(R.string.lessons), stringResource(R.string.lessons_sub), Icons.Rounded.School, blue) { nav.navigate(ExtraRoutes.LESSONS) },
        HubEntry(stringResource(R.string.ex_fingering), stringResource(R.string.fingering_sub), Icons.Rounded.TouchApp, blue) { nav.navigate(ExtraRoutes.FINGERING) },
    )
}
