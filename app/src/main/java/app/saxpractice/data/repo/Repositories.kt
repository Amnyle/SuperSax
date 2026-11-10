package app.saxpractice.data.repo

import app.saxpractice.data.db.KeyValueEntity
import app.saxpractice.data.db.SaxDatabase
import app.saxpractice.data.db.blockEntities
import app.saxpractice.data.db.toEntity
import app.saxpractice.data.db.toRecord
import app.saxpractice.domain.common.DrillResultRecord
import app.saxpractice.domain.common.GoalRecord
import app.saxpractice.domain.common.LessonProgressRecord
import app.saxpractice.domain.common.PieceProgressRecord
import app.saxpractice.domain.common.RankEventRecord
import app.saxpractice.domain.common.RankSnapshotRecord
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.domain.common.StoredProgram
import app.saxpractice.domain.common.TunerHoldRecord
import app.saxpractice.domain.common.UserPieceRecord
import app.saxpractice.domain.program.OnboardingAnswers
import app.saxpractice.domain.program.PlayerState
import app.saxpractice.domain.program.Program
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val repoJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Programs, player state, onboarding answers, sessions and goals. */
class PracticeRepository(private val db: SaxDatabase) {
    val programs: Flow<List<Program>> = db.programs().observeAll().map { list -> list.map { decodeProgram(it.json) } }
    val activeProgram: Flow<Program?> = db.programs().observeActive().map { e -> e?.let { decodeProgram(it.json) } }

    fun decodeProgram(json: String): Program = repoJson.decodeFromString(Program.serializer(), json)
    fun encodeProgram(p: Program): String = repoJson.encodeToString(Program.serializer(), p)

    suspend fun program(id: String): Program? = db.programs().byId(id)?.let { decodeProgram(it.json) }

    suspend fun saveProgram(p: Program, makeActive: Boolean, nowMs: Long) {
        val existing = db.programs().byId(p.id)
        db.programs().upsert(StoredProgram(p.id, encodeProgram(p), existing?.active ?: false, nowMs).toEntity())
        if (makeActive) db.programs().setActive(p.id)
    }

    suspend fun setActive(id: String) = db.programs().setActive(id)
    suspend fun deleteProgram(id: String) = db.programs().delete(id)

    val player: Flow<PlayerState> = db.kv().observe(KEY_PLAYER).map { e ->
        e?.let { repoJson.decodeFromString(PlayerState.serializer(), it.json) } ?: PlayerState()
    }

    suspend fun playerNow(): PlayerState =
        db.kv().get(KEY_PLAYER)?.let { repoJson.decodeFromString(PlayerState.serializer(), it.json) } ?: PlayerState()

    suspend fun savePlayer(s: PlayerState) = db.kv().put(KeyValueEntity(KEY_PLAYER, repoJson.encodeToString(PlayerState.serializer(), s)))

    suspend fun onboarding(): OnboardingAnswers? =
        db.kv().get(KEY_ONBOARDING)?.let { repoJson.decodeFromString(OnboardingAnswers.serializer(), it.json) }

    suspend fun saveOnboarding(a: OnboardingAnswers) =
        db.kv().put(KeyValueEntity(KEY_ONBOARDING, repoJson.encodeToString(OnboardingAnswers.serializer(), a)))

    val sessions: Flow<List<SessionRecord>> = db.sessions().observeAll().map { l -> l.map { it.toRecord() } }
    suspend fun session(id: String): SessionRecord? = db.sessions().byId(id)?.toRecord()
    suspend fun saveSession(s: SessionRecord) = db.sessions().insertWithBlocks(s.toEntity(), s.blockEntities())
    suspend fun deleteSession(id: String) = db.sessions().delete(id)

    val goals: Flow<List<GoalRecord>> = db.goals().observeAll().map { l -> l.map { it.toRecord() } }
    suspend fun saveGoal(g: GoalRecord) = db.goals().upsert(g.toEntity())
    suspend fun deleteGoal(id: String) = db.goals().delete(id)

    companion object {
        const val KEY_PLAYER = "player_state"
        const val KEY_ONBOARDING = "onboarding"
    }
}

/** Tuner holds, rank events and snapshots, drill results and lesson progress. */
class ProgressRepository(private val db: SaxDatabase) {
    val holds: Flow<List<TunerHoldRecord>> = db.holds().observeAll().map { l -> l.map { it.toRecord() } }
    suspend fun saveHold(h: TunerHoldRecord) = db.holds().insert(h.toEntity())

    val rankEvents: Flow<List<RankEventRecord>> = db.rank().observeEvents().map { l -> l.map { it.toRecord() } }
    suspend fun addRankEvent(e: RankEventRecord) = db.rank().insertEvent(e.toEntity())

    val snapshots: Flow<List<RankSnapshotRecord>> = db.rank().observeSnapshots().map { l -> l.map { it.toRecord() } }
    suspend fun saveSnapshot(s: RankSnapshotRecord) = db.rank().insertSnapshot(s.toEntity())
    suspend fun snapshotsNow(): List<RankSnapshotRecord> = db.rank().snapshots().map { it.toRecord() }

    val drills: Flow<List<DrillResultRecord>> = db.learning().observeDrills().map { l -> l.map { it.toRecord() } }
    suspend fun saveDrill(d: DrillResultRecord) = db.learning().insertDrill(d.toEntity())

    val lessons: Flow<List<LessonProgressRecord>> = db.learning().observeLessons().map { l -> l.map { it.toRecord() } }
    suspend fun saveLesson(l: LessonProgressRecord) = db.learning().upsertLesson(l.toEntity())
}

/** Piece stages and user imports. */
class LibraryRepository(private val db: SaxDatabase) {
    val progress: Flow<List<PieceProgressRecord>> = db.library().observeProgress().map { l -> l.map { it.toRecord() } }
    suspend fun saveProgress(p: PieceProgressRecord) = db.library().upsertProgress(p.toEntity())
    suspend fun progressNow(): List<PieceProgressRecord> = db.library().progress().map { it.toRecord() }

    val userPieces: Flow<List<UserPieceRecord>> = db.library().observeUserPieces().map { l -> l.map { it.toRecord() } }
    suspend fun saveUserPiece(p: UserPieceRecord) = db.library().upsertUserPiece(p.toEntity())
    suspend fun deleteUserPiece(id: String) = db.library().deleteUserPiece(id)
}
