package app.supersax.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app database. Schemas are exported to `app/schemas/`; every version bump must come with a
 * Room migration (and, if the backup format changes, a [app.supersax.domain.backup.BackupCodec] step).
 */
@Database(
    entities = [
        ProgramEntity::class, SessionEntity::class, SessionBlockLogEntity::class, GoalEntity::class,
        TunerHoldEntity::class, RankEventEntity::class, RankSnapshotEntity::class, PieceProgressEntity::class,
        UserPieceEntity::class, RecordingEntity::class, ReedEntity::class, MouthpieceEntity::class,
        DrillResultEntity::class, LessonProgressEntity::class, AudioRefEntity::class, KeyValueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SaxDatabase : RoomDatabase() {
    abstract fun programs(): ProgramDao
    abstract fun sessions(): SessionDao
    abstract fun goals(): GoalDao
    abstract fun holds(): TunerHoldDao
    abstract fun rank(): RankDao
    abstract fun library(): LibraryDao
    abstract fun media(): MediaDao
    abstract fun learning(): LearningDao
    abstract fun kv(): KeyValueDao

    companion object {
        fun create(context: Context): SaxDatabase =
            Room.databaseBuilder(context, SaxDatabase::class.java, "sax.db").build()
    }
}
