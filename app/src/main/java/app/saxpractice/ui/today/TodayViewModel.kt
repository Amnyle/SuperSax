package app.saxpractice.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.data.repo.TodayPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUi(
    val loading: Boolean = true,
    val plan: TodayPlan? = null,
    val running: Boolean = false,
    val availableMinutes: Int? = null,
)

/** Today's planned session, with one Start action. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val available = MutableStateFlow<Int?>(null)

    val ui: StateFlow<TodayUi> = combine(c.practice.activeProgram, c.practice.player, available, c.sessions.run, c.settings.settings) { p, s, a, r, _ ->
        Inputs(p, s, a, r != null)
    }.mapLatest { i ->
        TodayUi(false, c.today.plan(i.program, i.player, c.clock.nowMs(), i.available), i.running, i.available)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    private data class Inputs(
        val program: app.saxpractice.domain.program.Program?,
        val player: app.saxpractice.domain.program.PlayerState,
        val available: Int?,
        val running: Boolean,
    )

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
