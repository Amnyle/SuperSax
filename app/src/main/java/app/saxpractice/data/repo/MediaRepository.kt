package app.saxpractice.data.repo

import app.saxpractice.data.db.SaxDatabase
import app.saxpractice.data.db.toEntity
import app.saxpractice.data.db.toRecord
import app.saxpractice.domain.common.AudioRefRecord
import app.saxpractice.domain.common.MouthpieceRecord
import app.saxpractice.domain.common.RecordingRecord
import app.saxpractice.domain.common.ReedRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Recordings (files in app-private storage), reeds, mouthpieces and slow-down player references. */
class MediaRepository(private val db: SaxDatabase, val recordingsDir: File) {
    val recordings: Flow<List<RecordingRecord>> = db.media().observeRecordings().map { l -> l.map { it.toRecord() } }
    suspend fun saveRecording(r: RecordingRecord) = db.media().upsertRecording(r.toEntity())
    suspend fun deleteRecording(r: RecordingRecord) {
        File(recordingsDir, r.fileName).delete()
        db.media().deleteRecording(r.id)
    }
    fun fileOf(r: RecordingRecord) = File(recordingsDir, r.fileName)

    val reeds: Flow<List<ReedRecord>> = db.media().observeReeds().map { l -> l.map { it.toRecord() } }
    suspend fun saveReed(r: ReedRecord) = db.media().upsertReed(r.toEntity())

    val mouthpieces: Flow<List<MouthpieceRecord>> = db.media().observeMouthpieces().map { l -> l.map { it.toRecord() } }
    suspend fun saveMouthpiece(m: MouthpieceRecord) = db.media().upsertMouthpiece(m.toEntity())

    val audioRefs: Flow<List<AudioRefRecord>> = db.media().observeAudioRefs().map { l -> l.map { it.toRecord() } }
    suspend fun saveAudioRef(a: AudioRefRecord) = db.media().upsertAudioRef(a.toEntity())
    suspend fun deleteAudioRef(id: String) = db.media().deleteAudioRef(id)
}
