package app.supersax.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import app.supersax.data.db.KeyValueEntity
import app.supersax.data.db.SaxDatabase
import app.supersax.data.db.blockEntities
import app.supersax.data.db.toEntity
import app.supersax.data.db.toRecord
import app.supersax.data.repo.PracticeRepository
import app.supersax.data.settings.SettingsRepository
import app.supersax.domain.backup.BackupData
import app.supersax.domain.backup.BackupStore
import app.supersax.domain.backup.BackupZip
import app.supersax.domain.program.OnboardingAnswers
import app.supersax.domain.program.PlayerState
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Full export and import of the user's data as a `.zip` (see [BackupZip]) through the storage access
 * framework. Import replaces everything in one Room transaction, then restores the files.
 */
class BackupRepository(
    private val db: SaxDatabase,
    private val settings: SettingsRepository,
    private val recordingsDir: File,
    private val importsDir: File,
    private val clock: () -> Long,
) : BackupStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun snapshot(): BackupData = BackupData(
        exportedAtMs = clock(),
        settings = settings.current(),
        player = db.kv().get(PracticeRepository.KEY_PLAYER)?.let { json.decodeFromString(PlayerState.serializer(), it.json) } ?: PlayerState(),
        onboarding = db.kv().get(PracticeRepository.KEY_ONBOARDING)?.let { json.decodeFromString(OnboardingAnswers.serializer(), it.json) },
        programs = db.programs().all().map { it.toRecord() },
        sessions = db.sessions().all().map { it.toRecord() },
        goals = db.goals().all().map { it.toRecord() },
        holds = db.holds().all().map { it.toRecord() },
        rankEvents = db.rank().events().map { it.toRecord() },
        rankSnapshots = db.rank().snapshots().map { it.toRecord() },
        pieceProgress = db.library().progress().map { it.toRecord() },
        userPieces = db.library().userPieces().map { it.toRecord() },
        recordings = db.media().recordings().map { it.toRecord() },
        reeds = db.media().reeds().map { it.toRecord() },
        mouthpieces = db.media().mouthpieces().map { it.toRecord() },
        drillResults = db.learning().drills().map { it.toRecord() },
        lessons = db.learning().lessons().map { it.toRecord() },
        audioRefs = db.media().audioRefs().map { it.toRecord() },
    )

    override suspend fun replaceAll(data: BackupData) {
        db.withTransaction {
            db.programs().clear(); db.sessions().clearBlocks(); db.sessions().clear(); db.goals().clear(); db.holds().clear()
            db.rank().clearEvents(); db.rank().clearSnapshots(); db.library().clearProgress(); db.library().clearUserPieces()
            db.media().clearRecordings(); db.media().clearReeds(); db.media().clearMouthpieces(); db.media().clearAudioRefs()
            db.learning().clearDrills(); db.learning().clearLessons(); db.kv().clear()

            db.programs().insertAll(data.programs.map { it.toEntity() })
            data.sessions.forEach { db.sessions().insertWithBlocks(it.toEntity(), it.blockEntities()) }
            db.goals().insertAll(data.goals.map { it.toEntity() })
            db.holds().insertAll(data.holds.map { it.toEntity() })
            db.rank().insertEvents(data.rankEvents.map { it.toEntity() })
            db.rank().insertSnapshots(data.rankSnapshots.map { it.toEntity() })
            db.library().insertProgress(data.pieceProgress.map { it.toEntity() })
            db.library().insertUserPieces(data.userPieces.map { it.toEntity() })
            db.media().insertRecordings(data.recordings.map { it.toEntity() })
            db.media().insertReeds(data.reeds.map { it.toEntity() })
            db.media().insertMouthpieces(data.mouthpieces.map { it.toEntity() })
            db.media().insertAudioRefs(data.audioRefs.map { it.toEntity() })
            db.learning().insertDrills(data.drillResults.map { it.toEntity() })
            db.learning().insertLessons(data.lessons.map { it.toEntity() })
            db.kv().put(KeyValueEntity(PracticeRepository.KEY_PLAYER, json.encodeToString(PlayerState.serializer(), data.player)))
            data.onboarding?.let { db.kv().put(KeyValueEntity(PracticeRepository.KEY_ONBOARDING, json.encodeToString(OnboardingAnswers.serializer(), it))) }
        }
        settings.replace(data.settings)
    }

    /** Writes the backup zip to [uri], including recordings and imported files. */
    suspend fun export(resolver: ContentResolver, uri: Uri, includeRecordings: Boolean) {
        val data = snapshot()
        val files = buildMap<String, () -> java.io.InputStream> {
            if (includeRecordings) recordingsDir.listFiles()?.forEach { f -> put(BackupZip.RECORDINGS_DIR + f.name) { f.inputStream() } }
            importsDir.listFiles()?.forEach { f -> put(BackupZip.IMPORTS_DIR + f.name) { f.inputStream() } }
        }
        val out = resolver.openOutputStream(uri, "wt") ?: error("Cannot open $uri")
        out.use { BackupZip.write(it, data, files) }
    }

    /**
     * Reads and validates the zip at [uri] into a staging folder, without touching current data.
     * Call [commit] after the user confirms.
     */
    fun stage(resolver: ContentResolver, uri: Uri, stagingDir: File): BackupData {
        stagingDir.deleteRecursively(); stagingDir.mkdirs()
        val input = resolver.openInputStream(uri) ?: error("Cannot open $uri")
        return input.use {
            BackupZip.read(it) { name, stream ->
                val target = File(stagingDir, name)
                require(target.canonicalPath.startsWith(stagingDir.canonicalPath)) { "Unsafe entry $name" }
                target.parentFile?.mkdirs()
                target.outputStream().use { o -> stream.copyTo(o) }
            }
        }
    }

    /** Replaces all data with [data] and moves the staged files in place. */
    suspend fun commit(data: BackupData, stagingDir: File) {
        replaceAll(data)
        replaceDir(File(stagingDir, BackupZip.RECORDINGS_DIR), recordingsDir, keepIfMissing = true)
        replaceDir(File(stagingDir, BackupZip.IMPORTS_DIR), importsDir, keepIfMissing = false)
        stagingDir.deleteRecursively()
    }

    private fun replaceDir(from: File, to: File, keepIfMissing: Boolean) {
        if (!from.isDirectory) { if (!keepIfMissing) to.listFiles()?.forEach { it.delete() }; return }
        to.mkdirs(); to.listFiles()?.forEach { it.delete() }
        from.listFiles()?.forEach { it.copyTo(File(to, it.name), overwrite = true) }
    }
}
