package app.saxpractice.data.repo

import app.saxpractice.data.assets.ContentRepository
import app.saxpractice.data.settings.SettingsRepository
import app.saxpractice.domain.planner.PieceChoice
import app.saxpractice.domain.planner.PlannedSession
import app.saxpractice.domain.planner.SessionPlanner
import app.saxpractice.domain.program.PhoneDrillSlot
import app.saxpractice.domain.program.PlayerState
import app.saxpractice.domain.program.Program
import app.saxpractice.domain.program.ProgramDay
import java.time.Instant
import java.time.ZoneId

/** Chooses library pieces for a session ("something new" and "something fun"). */
fun interface PieceChooser {
    suspend fun choose(player: PlayerState): PieceChoice
}

/** What is planned for a given day. */
data class TodayPlan(
    val program: Program?,
    val day: ProgramDay?,
    /** True when today is not a planned day and [day] is the next planned day offered instead. */
    val offDay: Boolean,
    val session: PlannedSession?,
    val phoneDrills: List<PhoneDrillSlot>,
)

/** Resolves the active program into today's concrete session. */
class TodayService(
    private val practice: PracticeRepository,
    private val content: ContentRepository,
    private val settings: SettingsRepository,
    var pieceChooser: PieceChooser = PieceChooser { PieceChoice() },
) {
    suspend fun plan(program: Program?, player: PlayerState, nowMs: Long, availableMinutes: Int? = null): TodayPlan {
        val dow = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).dayOfWeek.value
        if (program == null || program.days.isEmpty()) return TodayPlan(program, null, false, null, emptyList())
        val today = program.dayFor(dow)
        val day = today ?: nextDay(program, dow)
        val restSec = settings.current().restSec
        val session = day?.let {
            SessionPlanner.plan(
                programId = program.id, day = it, state = player, config = content.generatorConfig,
                enduranceCapSec = minOf(program.enduranceCapSec, player.enduranceCapSec).coerceAtLeast(60),
                restSec = restSec, pieces = pieceChooser.choose(player), availableMinutes = availableMinutes,
            )
        }
        return TodayPlan(program, day, today == null, session, program.phoneDrills.filter { it.dayOfWeek == dow })
    }

    private fun nextDay(p: Program, dow: Int): ProgramDay? =
        (1..7).map { ((dow - 1 + it) % 7) + 1 }.firstNotNullOfOrNull { p.dayFor(it) }
}
