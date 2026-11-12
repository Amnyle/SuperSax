package app.supersax.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import app.supersax.R
import app.supersax.domain.program.ExerciseType
import app.supersax.ui.goals.GoalsScreen
import app.supersax.ui.history.HistoryScreen
import app.supersax.ui.hub.HubEntry
import app.supersax.ui.hub.HubScreen
import app.supersax.ui.hub.HubSection
import app.supersax.ui.home.HomeScreen
import app.supersax.ui.theme.Sax
import app.supersax.ui.onboarding.OnboardingScreen
import app.supersax.ui.program.ProgramEditorScreen
import app.supersax.ui.program.ProgramsScreen
import app.supersax.ui.session.SessionScreen
import app.supersax.ui.session.SessionSummaryScreen
import app.supersax.ui.settings.SettingsScreen

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
    Tab(Routes.TODAY, R.string.home, Icons.Rounded.Home),
    Tab(Routes.TOOLS, R.string.training, Icons.Rounded.FitnessCenter),
    Tab(Routes.PROGRESS, R.string.progress, Icons.Rounded.Insights),
)

/** Floating pill navigation bar: equal tabs, the active one on an amber pill. */
@Composable
private fun BottomBar(route: String?, onTab: (String) -> Unit) {
    val dark = Sax.colors.dark
    val shape = RoundedCornerShape(26.dp)
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(68.dp)
                .shadow(if (dark) 0.dp else 12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.10f))
                .clip(shape).background(if (dark) Sax.colors.elevated else Sax.colors.card)
                .border(1.dp, if (dark) Color(0x1FFFFFFF) else Color(0x1A000000), shape).padding(horizontal = 4.dp),
        ) {
            tabs.forEach { t -> TabItem(t, route == t.route, Modifier.weight(1f)) { onTab(t.route) } }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateDpAsState(if (selected) 56.dp else 28.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "pill")
    val label = stringResource(tab.label)
    Column(
        modifier.fillMaxHeight().selectable(selected, onClick = onClick, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(pill).height(32.dp).clip(CircleShape).background(if (selected) Sax.colors.accent else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(tab.icon, null, tint = if (selected) Sax.colors.onAccent else Sax.colors.textSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 12.sp, color = if (selected) Sax.colors.text else Sax.colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** Navigation graph. [startOnboarding] is true on first launch; [startSignal] changes when the widget asks to start. */
@Composable
fun AppNav(nav: NavHostController, startOnboarding: Boolean, startSignal: Int, updatesSignal: Int = 0, dailySignal: Int = 0, queueSignal: Int = 0, onRecreate: () -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val container = LocalContainer.current
    LaunchedEffect(startSignal) {
        if (startSignal > 0 && !startOnboarding && container.startTodaySession()) nav.navigate(Routes.SESSION) { launchSingleTop = true }
    }
    LaunchedEffect(dailySignal) { if (dailySignal > 0 && !startOnboarding) nav.navigate(ExtraRoutes.DAILY) { launchSingleTop = true } }
    LaunchedEffect(queueSignal) { if (queueSignal > 0 && !startOnboarding) nav.navigate(ExtraRoutes.QUEUE) { launchSingleTop = true } }
    LaunchedEffect(updatesSignal) { if (updatesSignal > 0 && !startOnboarding) nav.navigate(Routes.SETTINGS) { launchSingleTop = true } }
    app.supersax.ui.whatsnew.WhatsNewAfterUpdate()
    app.supersax.ui.whatsnew.CrashReportDialog()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) BottomBar(route) { r ->
                nav.navigate(r) { popUpTo(Routes.TODAY) { saveState = true }; launchSingleTop = true; restoreState = true }
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
                HomeScreen(
                    onStartSession = { nav.navigate(Routes.SESSION) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onPrograms = { nav.navigate(Routes.PROGRAMS) },
                    onPhoneDrill = { type -> phoneDrillRoute(type)?.let { nav.navigate(it) } },
                    onRank = { nav.navigate(ExtraRoutes.RANK) },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onDaily = { nav.navigate(ExtraRoutes.DAILY) },
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
            composable(Routes.TOOLS) {
                HubScreen(stringResource(R.string.training), listOf(
                    HubSection(stringResource(R.string.training_sax), saxTrainingEntries(nav), stringResource(R.string.training_sax_hint)),
                    HubSection(stringResource(R.string.training_phone), phoneTrainingEntries(nav), stringResource(R.string.training_phone_hint)),
                ))
            }
            composable(Routes.PROGRESS) {
                HubScreen(stringResource(R.string.progress), listOf(
                    HubSection(stringResource(R.string.progress_level), extraProgressEntries(nav)),
                    HubSection(stringResource(R.string.progress_plan), planEntries(nav)),
                    HubSection(stringResource(R.string.progress_logs), extraLogEntries(nav)),
                ))
            }
            extraDestinations(nav)
        }
    }
}

/** Route for a phone drill type, if that drill exists in this build. */
fun phoneDrillRoute(type: ExerciseType): String? = extraPhoneDrillRoute(type)

@Composable
private fun planEntries(nav: NavHostController): List<HubEntry> = listOf(
    HubEntry(stringResource(R.string.goals), stringResource(R.string.goals_sub), Icons.Rounded.Flag) { nav.navigate(Routes.GOALS) },
    HubEntry(stringResource(R.string.history), stringResource(R.string.history_sub), Icons.Rounded.History) { nav.navigate(Routes.HISTORY) },
    HubEntry(stringResource(R.string.programs), stringResource(R.string.programs_sub), Icons.AutoMirrored.Rounded.List) { nav.navigate(Routes.PROGRAMS) },
)
