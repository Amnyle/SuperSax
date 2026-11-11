package app.supersax.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.data.repo.TodayPlan
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.goals.GoalEvaluator
import app.supersax.domain.program.Pillar
import app.supersax.domain.program.Program
import app.supersax.domain.program.ProgramDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of the current week (Monday first). */
data class WeekDay(val date: LocalDate, val planned: Boolean, val done: Boolean, val isToday: Boolean)

/** A planned session after today. */
data class Upcoming(val date: LocalDate, val day: ProgramDay)

data class RankSummary(val tier: Int, val division: Int, val limiter: Pillar?)

data class HomeUi(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val plan: TodayPlan? = null,
    val running: Boolean = false,
    val availableMinutes: Int? = null,
    val week: List<WeekDay> = emptyList(),
    val weekDone: Int = 0,
    val weekPlanned: Int = 0,
    val weekMinutes: Int = 0,
    val upcoming: List<Upcoming> = emptyList(),
    val rank: RankSummary? = null,
)

/** The dashboard: today's session with one Start action, this week's log, the rank, and what comes next. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val available = MutableStateFlow<Int?>(null)

    private data class Inputs(
        val program: Program?,
        val player: app.supersax.domain.program.PlayerState,
        val available: Int?,
        val running: Boolean,
        val sessions: List<SessionRecord>,
    )

    private val inputs = combine(
        combine(c.practice.activeProgram, c.practice.player, available, c.sessions.run, c.settings.settings) { p, s, a, r, _ -> listOf(p, s, a, r != null) },
        c.practice.sessions,
    ) { l, sessions ->
        @Suppress("UNCHECKED_CAST")
        Inputs(l[0] as Program?, l[1] as app.supersax.domain.program.PlayerState, l[2] as Int?, l[3] as Boolean, sessions)
    }

    val ui: StateFlow<HomeUi> = combine(inputs, c.rank.result) { i, r -> i to r }.mapLatest { (i, r) ->
        val now = c.clock.nowMs()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = GoalEvaluator.weekStart(today)
        val plannedDays = i.program?.days?.map { it.dayOfWeek }?.toSet() ?: emptySet()
        val sax = i.sessions.filter { !it.phone }
        val playedDates = sax.map { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }.toSet()
        val week = (0L..6L).map { d ->
            val date = monday.plusDays(d)
            WeekDay(date, date.dayOfWeek.value in plannedDays, date in playedDates, date == today)
        }
        val weekStartMs = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekMinutes = sax.filter { it.startedAtMs >= weekStartMs }.sumOf { it.playedSec } / 60
        val upcoming = (1L..14L).map { today.plusDays(it) }
            .mapNotNull { date -> i.program?.days?.firstOrNull { it.dayOfWeek == date.dayOfWeek.value }?.let { Upcoming(date, it) } }
            .take(3)
        HomeUi(
            loading = false, today = today,
            plan = c.today.plan(i.program, i.player, now, i.available), running = i.running, availableMinutes = i.available,
            week = week, weekDone = week.count { it.planned && it.done }, weekPlanned = week.count { it.planned }, weekMinutes = weekMinutes,
            upcoming = upcoming,
            rank = RankSummary(r.overallTier, r.division, r.limiter),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** "Short on time": cut today's session to [minutes] (null restores the plan). */
    fun setAvailable(minutes: Int?) { available.value = minutes }

    /** Starts today's session (or resumes the running one) and calls [onStarted]. */
    fun start(onStarted: () -> Unit) {
        viewModelScope.launch {
            if (c.sessions.run.value != null) { onStarted(); return@launch }
            val plan = ui.value.plan ?: c.today.plan(c.practice.activeProgram.first(), c.practice.playerNow(), c.clock.nowMs())
            val session = plan.session ?: return@launch
            c.sessions.start(session, plan.day?.dayOfWeek)
            onStarted()
        }
    }
}
