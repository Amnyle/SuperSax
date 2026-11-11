package app.supersax.domain.rank

import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.common.LessonProgressRecord
import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.RankEventRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.common.TunerHoldRecord
import app.supersax.domain.pitch.Instrument
import java.time.ZoneId

/** Everything the rank is computed from (a Kotlin model; the calculation is the Java [RankCalculator]). All facts come from stored logs. */
data class RankEvents(
    val rankEvents: List<RankEventRecord> = emptyList(),
    val holds: List<TunerHoldRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    /** ISO days of week planned by the active program. */
    val plannedDays: Set<Int> = emptySet(),
    val pieceProgress: List<PieceProgressRecord> = emptyList(),
    /** Difficulty of each piece id (from the library). */
    val pieceDifficulty: Map<String, Int> = emptyMap(),
    val drills: List<DrillResultRecord> = emptyList(),
    val lessons: List<LessonProgressRecord> = emptyList(),
    val zone: ZoneId = ZoneId.of("UTC"),
    val instrument: Instrument = Instrument.ALTO_SAX,
)
