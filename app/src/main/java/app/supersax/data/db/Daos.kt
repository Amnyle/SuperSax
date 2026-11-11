package app.supersax.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program ORDER BY updatedAtMs DESC") fun observeAll(): Flow<List<ProgramEntity>>
    @Query("SELECT * FROM program") suspend fun all(): List<ProgramEntity>
    @Query("SELECT * FROM program WHERE id = :id") suspend fun byId(id: String): ProgramEntity?
    @Query("SELECT * FROM program WHERE active = 1 LIMIT 1") fun observeActive(): Flow<ProgramEntity?>
    @Upsert suspend fun upsert(p: ProgramEntity)
    @Query("UPDATE program SET active = (id = :id)") suspend fun setActive(id: String)
    @Query("DELETE FROM program WHERE id = :id") suspend fun delete(id: String)
    @Insert suspend fun insertAll(items: List<ProgramEntity>)
    @Query("DELETE FROM program") suspend fun clear()
}

/** A session row with its block logs. */
data class SessionWithBlocks(
    @androidx.room.Embedded val session: SessionEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "sessionId") val blocks: List<SessionBlockLogEntity>,
)

@Dao
interface SessionDao {
    @Transaction @Query("SELECT * FROM session ORDER BY startedAtMs DESC") fun observeAll(): Flow<List<SessionWithBlocks>>
    @Transaction @Query("SELECT * FROM session ORDER BY startedAtMs") suspend fun all(): List<SessionWithBlocks>
    @Transaction @Query("SELECT * FROM session WHERE id = :id") suspend fun byId(id: String): SessionWithBlocks?
    @Insert suspend fun insert(s: SessionEntity)
    @Insert suspend fun insertBlocks(b: List<SessionBlockLogEntity>)
    @Query("DELETE FROM session WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM session") suspend fun clear()
    @Query("DELETE FROM session_block_log") suspend fun clearBlocks()

    @Transaction
    suspend fun insertWithBlocks(s: SessionEntity, blocks: List<SessionBlockLogEntity>) {
        insert(s)
        insertBlocks(blocks)
    }
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal ORDER BY createdAtMs DESC") fun observeAll(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goal") suspend fun all(): List<GoalEntity>
    @Upsert suspend fun upsert(g: GoalEntity)
    @Query("DELETE FROM goal WHERE id = :id") suspend fun delete(id: String)
    @Insert suspend fun insertAll(items: List<GoalEntity>)
    @Query("DELETE FROM goal") suspend fun clear()
}

@Dao
interface TunerHoldDao {
    @Query("SELECT * FROM tuner_hold ORDER BY atMs DESC") fun observeAll(): Flow<List<TunerHoldEntity>>
    @Query("SELECT * FROM tuner_hold ORDER BY atMs") suspend fun all(): List<TunerHoldEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(h: TunerHoldEntity)
    @Insert suspend fun insertAll(items: List<TunerHoldEntity>)
    @Query("DELETE FROM tuner_hold") suspend fun clear()
}

@Dao
interface RankDao {
    @Query("SELECT * FROM rank_event ORDER BY atMs") fun observeEvents(): Flow<List<RankEventEntity>>
    @Query("SELECT * FROM rank_event ORDER BY atMs") suspend fun events(): List<RankEventEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(e: RankEventEntity)
    @Insert suspend fun insertEvents(items: List<RankEventEntity>)
    @Query("DELETE FROM rank_event") suspend fun clearEvents()
    @Query("DELETE FROM rank_event WHERE id = :id") suspend fun deleteEvent(id: String)
    @Query("SELECT * FROM rank_snapshot ORDER BY atMs") fun observeSnapshots(): Flow<List<RankSnapshotEntity>>
    @Query("SELECT * FROM rank_snapshot ORDER BY atMs") suspend fun snapshots(): List<RankSnapshotEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSnapshot(s: RankSnapshotEntity)
    @Insert suspend fun insertSnapshots(items: List<RankSnapshotEntity>)
    @Query("DELETE FROM rank_snapshot") suspend fun clearSnapshots()
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM piece_progress") fun observeProgress(): Flow<List<PieceProgressEntity>>
    @Query("SELECT * FROM piece_progress") suspend fun progress(): List<PieceProgressEntity>
    @Upsert suspend fun upsertProgress(p: PieceProgressEntity)
    @Insert suspend fun insertProgress(items: List<PieceProgressEntity>)
    @Query("DELETE FROM piece_progress") suspend fun clearProgress()
    @Query("SELECT * FROM user_piece ORDER BY importedAtMs DESC") fun observeUserPieces(): Flow<List<UserPieceEntity>>
    @Query("SELECT * FROM user_piece") suspend fun userPieces(): List<UserPieceEntity>
    @Upsert suspend fun upsertUserPiece(p: UserPieceEntity)
    @Query("DELETE FROM user_piece WHERE id = :id") suspend fun deleteUserPiece(id: String)
    @Insert suspend fun insertUserPieces(items: List<UserPieceEntity>)
    @Query("DELETE FROM user_piece") suspend fun clearUserPieces()
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM recording ORDER BY atMs DESC") fun observeRecordings(): Flow<List<RecordingEntity>>
    @Query("SELECT * FROM recording") suspend fun recordings(): List<RecordingEntity>
    @Upsert suspend fun upsertRecording(r: RecordingEntity)
    @Query("DELETE FROM recording WHERE id = :id") suspend fun deleteRecording(id: String)
    @Insert suspend fun insertRecordings(items: List<RecordingEntity>)
    @Query("DELETE FROM recording") suspend fun clearRecordings()

    @Query("SELECT * FROM reed ORDER BY startedEpochDay DESC") fun observeReeds(): Flow<List<ReedEntity>>
    @Query("SELECT * FROM reed") suspend fun reeds(): List<ReedEntity>
    @Upsert suspend fun upsertReed(r: ReedEntity)
    @Insert suspend fun insertReeds(items: List<ReedEntity>)
    @Query("DELETE FROM reed") suspend fun clearReeds()

    @Query("SELECT * FROM mouthpiece ORDER BY name") fun observeMouthpieces(): Flow<List<MouthpieceEntity>>
    @Query("SELECT * FROM mouthpiece") suspend fun mouthpieces(): List<MouthpieceEntity>
    @Upsert suspend fun upsertMouthpiece(m: MouthpieceEntity)
    @Insert suspend fun insertMouthpieces(items: List<MouthpieceEntity>)
    @Query("DELETE FROM mouthpiece") suspend fun clearMouthpieces()

    @Query("SELECT * FROM audio_ref ORDER BY addedAtMs DESC") fun observeAudioRefs(): Flow<List<AudioRefEntity>>
    @Query("SELECT * FROM audio_ref") suspend fun audioRefs(): List<AudioRefEntity>
    @Upsert suspend fun upsertAudioRef(a: AudioRefEntity)
    @Query("DELETE FROM audio_ref WHERE id = :id") suspend fun deleteAudioRef(id: String)
    @Insert suspend fun insertAudioRefs(items: List<AudioRefEntity>)
    @Query("DELETE FROM audio_ref") suspend fun clearAudioRefs()
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM drill_result ORDER BY atMs DESC") fun observeDrills(): Flow<List<DrillResultEntity>>
    @Query("SELECT * FROM drill_result ORDER BY atMs") suspend fun drills(): List<DrillResultEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDrill(d: DrillResultEntity)
    @Insert suspend fun insertDrills(items: List<DrillResultEntity>)
    @Query("DELETE FROM drill_result") suspend fun clearDrills()

    @Query("SELECT * FROM lesson_progress") fun observeLessons(): Flow<List<LessonProgressEntity>>
    @Query("SELECT * FROM lesson_progress") suspend fun lessons(): List<LessonProgressEntity>
    @Upsert suspend fun upsertLesson(l: LessonProgressEntity)
    @Insert suspend fun insertLessons(items: List<LessonProgressEntity>)
    @Query("DELETE FROM lesson_progress") suspend fun clearLessons()
}

@Dao
interface KeyValueDao {
    @Query("SELECT * FROM kv WHERE `key` = :key") fun observe(key: String): Flow<KeyValueEntity?>
    @Query("SELECT * FROM kv WHERE `key` = :key") suspend fun get(key: String): KeyValueEntity?
    @Upsert suspend fun put(e: KeyValueEntity)
    @Query("DELETE FROM kv WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM kv") suspend fun clear()
}
