package app.saxpractice.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import app.saxpractice.R
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.ui.goals.GoalsScreen
import app.saxpractice.ui.history.HistoryScreen
import app.saxpractice.ui.hub.HubEntry
import app.saxpractice.ui.hub.HubScreen
import app.saxpractice.ui.onboarding.OnboardingScreen
import app.saxpractice.ui.program.ProgramEditorScreen
import app.saxpractice.ui.program.ProgramsScreen
import app.saxpractice.ui.session.SessionScreen
import app.saxpractice.ui.session.SessionSummaryScreen
import app.saxpractice.ui.settings.SettingsScreen
import app.saxpractice.ui.today.TodayScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val TOOLS = "tools"
    const val PROGRESS = "progress"
    const val SESSION = "session"
    const val SUMMARY = "session_summary"
    const val PROGRAMS = "programs"
    const val PROGRAM_EDIT = "program_edit/{id}"
    const val HISTORY = "history"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    fun programEdit(id: String) = "program_edit/$id"
}

private data class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, R.string.today, Icons.Default.PlayArrow),
    Tab(Routes.TOOLS, R.string.tools, Icons.Default.Build),
    Tab(ExtraRoutes.LIBRARY, R.string.library, Icons.Default.LibraryMusic),
    Tab(Routes.PROGRESS, R.string.progress, Icons.Default.Insights),
)

/** Navigation graph. [startOnboarding] is true on first launch; [startSignal] changes when the widget asks to start. */
@Composable
fun AppNav(nav: NavHostController, startOnboarding: Boolean, startSignal: Int, onRecreate: () -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val container = LocalContainer.current
    LaunchedEffect(startSignal) {
        if (startSignal > 0 && !startOnboarding && container.startTodaySession()) nav.navigate(Routes.SESSION) { launchSingleTop = true }
    }
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo(Routes.TODAY) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.TODAY, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen { manualId ->
                    nav.navigate(Routes.TODAY) { popUpTo(0) }
                    if (manualId != null) nav.navigate(Routes.programEdit(manualId))
                }
            }
            composable(Routes.TODAY) {
                TodayScreen(
                    onStartSession = { nav.navigate(Routes.SESSION) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onPrograms = { nav.navigate(Routes.PROGRAMS) },
                    onPhoneDrill = { type -> phoneDrillRoute(type)?.let { nav.navigate(it) } },
                )
            }
            composable(Routes.SESSION) {
                SessionScreen(
                    onFinished = { nav.navigate(Routes.SUMMARY) { popUpTo(Routes.TODAY) } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SUMMARY) { SessionSummaryScreen(onSaved = { nav.navigate(Routes.TODAY) { popUpTo(0) } }) }
            composable(Routes.PROGRAMS) { ProgramsScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.programEdit(it)) }) }
            composable(Routes.PROGRAM_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                ProgramEditorScreen(e.arguments?.getString("id")!!, onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.GOALS) { GoalsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onRerunOnboarding = { nav.navigate(Routes.ONBOARDING) },
                    onPrograms = { nav.navigate(Routes.PROGRAMS) },
                    onImported = onRecreate,
                )
            }
            composable(Routes.TOOLS) { HubScreen(stringResource(R.string.tools), toolEntries(nav)) }
            composable(Routes.PROGRESS) { HubScreen(stringResource(R.string.progress), progressEntries(nav)) }
            extraDestinations(nav)
        }
    }
}

/** Route for a phone drill type, if that drill exists in this build. */
fun phoneDrillRoute(type: ExerciseType): String? = extraPhoneDrillRoute(type)

@Composable
private fun progressEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.goals), stringResource(R.string.goals_sub), Icons.Default.Flag) { nav.navigate(Routes.GOALS) },
    HubEntry(stringResource(R.string.history), stringResource(R.string.history_sub), Icons.Default.History) { nav.navigate(Routes.HISTORY) },
    HubEntry(stringResource(R.string.programs), stringResource(R.string.programs_sub), Icons.AutoMirrored.Filled.List) { nav.navigate(Routes.PROGRAMS) },
) + extraProgressEntries(nav)

@Composable
private fun toolEntries(nav: NavHostController): List<HubEntry> = extraToolEntries(nav)
