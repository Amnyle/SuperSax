package app.saxpractice.data.db

import app.saxpractice.domain.common.AudioRefRecord
import app.saxpractice.domain.common.BlockLogRecord
import app.saxpractice.domain.common.DrillResultRecord
import app.saxpractice.domain.common.GoalRecord
import app.saxpractice.domain.common.LessonProgressRecord
import app.saxpractice.domain.common.LoopPoint
import app.saxpractice.domain.common.MouthpieceRecord
import app.saxpractice.domain.common.PieceProgressRecord
import app.saxpractice.domain.common.RankEventRecord
import app.saxpractice.domain.common.RankSnapshotRecord
import app.saxpractice.domain.common.RecordingRecord
import app.saxpractice.domain.common.ReedRecord
import app.saxpractice.domain.common.RunRecord
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.domain.common.StoredProgram
import app.saxpractice.domain.common.TunerHoldRecord
import app.saxpractice.domain.common.UserPieceRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Entity <-> record mappers. Records are the Room-independent layer used by the UI and backups. */
internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val runsSer = ListSerializer(RunRecord.serializer())
private val floatsSer = ListSerializer(Float.serializer())
private val loopsSer = ListSerializer(LoopPoint.serializer())
private val tiersSer = MapSerializer(String.serializer(), Int.serializer())

fun ProgramEntity.toRecord() = StoredProgram(id, json, active, updatedAtMs)
fun StoredProgram.toEntity() = ProgramEntity(id, json, active, updatedAtMs)

fun SessionWithBlocks.toRecord() = SessionRecord(
    id = session.id, startedAtMs = session.startedAtMs, endedAtMs = session.endedAtMs,
    programId = session.programId, dayId = session.dayId, plannedDayOfWeek = session.plannedDayOfWeek,
    phone = session.phone, plannedSec = session.plannedSec, playedSec = session.playedSec,
    tiredness = session.tiredness, note = session.note, reedId = session.reedId,
    blocks = blocks.sortedBy { it.stepIndex }.map {
        BlockLogRecord(it.stepIndex, it.blockKind, it.exerciseType, it.catalogId, it.plannedSec, it.actualSec,
            it.lipsTired, it.skipped, it.scaleKey, it.bpm, it.pieceId, it.holdSec)
    },
    runs = dbJson.decodeFromString(runsSer, session.runsJson),
)

fun SessionRecord.toEntity() = SessionEntity(
    id, startedAtMs, endedAtMs, programId, dayId, plannedDayOfWeek, phone, plannedSec, playedSec, tiredness, note, reedId,
    dbJson.encodeToString(runsSer, runs),
)

fun SessionRecord.blockEntities() = blocks.map {
    SessionBlockLogEntity(0, id, it.stepIndex, it.blockKind, it.exerciseType, it.catalogId, it.plannedSec, it.actualSec,
        it.lipsTired, it.skipped, it.scaleKey, it.bpm, it.pieceId, it.holdSec)
}

fun GoalEntity.toRecord() = GoalRecord(id, type, title, createdAtMs, deadlineEpochDay, weeklyMinutes, scaleKey, bpm, pieceId, sectionId, stage, benchmarkKey, archived)
fun GoalRecord.toEntity() = GoalEntity(id, type, title, createdAtMs, deadlineEpochDay, weeklyMinutes, scaleKey, bpm, pieceId, sectionId, stage, benchmarkKey, archived)

fun TunerHoldEntity.toRecord() = TunerHoldRecord(id, atMs, refMidi, stableSec, inTuneShare, toleranceCents, meanCents, driftCents,
    slopeCentsPerSec, minCents, maxCents, dbJson.decodeFromString(floatsSer, traceJson), traceHz)
fun TunerHoldRecord.toEntity() = TunerHoldEntity(id, atMs, refMidi, stableSec, inTuneShare, toleranceCents, meanCents, driftCents,
    slopeCentsPerSec, minCents, maxCents, dbJson.encodeToString(floatsSer, trace), traceHz)

fun RankEventEntity.toRecord() = RankEventRecord(id, atMs, kind, scaleKey, bpm, writtenMidi, verified, evidenceRecordingId)
fun RankEventRecord.toEntity() = RankEventEntity(id, atMs, kind, scaleKey, bpm, writtenMidi, verified, evidenceRecordingId)

fun RankSnapshotEntity.toRecord() = RankSnapshotRecord(atMs, overallTier, division, dbJson.decodeFromString(tiersSer, pillarTiersJson))
fun RankSnapshotRecord.toEntity() = RankSnapshotEntity(atMs, overallTier, division, dbJson.encodeToString(tiersSer, pillarTiers))

fun PieceProgressEntity.toRecord() = PieceProgressRecord(pieceId, sectionId, stage, updatedAtMs, lastPlayedAtMs, evidenceRecordingId)
fun PieceProgressRecord.toEntity() = PieceProgressEntity(pieceId, sectionId, stage, updatedAtMs, lastPlayedAtMs, evidenceRecordingId)

fun UserPieceEntity.toRecord() = UserPieceRecord(id, title, kind, fileName, pieceJson, attachedToPieceId, importedAtMs)
fun UserPieceRecord.toEntity() = UserPieceEntity(id, title, kind, fileName, pieceJson, attachedToPieceId, importedAtMs)

fun RecordingEntity.toRecord() = RecordingRecord(id, atMs, fileName, durationMs, exerciseKey, pieceId, reedId, mouthpieceId, benchmark, note)
fun RecordingRecord.toEntity() = RecordingEntity(id, atMs, fileName, durationMs, exerciseKey, pieceId, reedId, mouthpieceId, benchmark, note)

fun ReedEntity.toRecord() = ReedRecord(id, brand, strength, startedEpochDay, retired, note)
fun ReedRecord.toEntity() = ReedEntity(id, brand, strength, startedEpochDay, retired, note)

fun MouthpieceEntity.toRecord() = MouthpieceRecord(id, name, note)
fun MouthpieceRecord.toEntity() = MouthpieceEntity(id, name, note)

fun DrillResultEntity.toRecord() = DrillResultRecord(id, atMs, kind, level, questions, correct, meanResponseMs, detail)
fun DrillResultRecord.toEntity() = DrillResultEntity(id, atMs, kind, level, questions, correct, meanResponseMs, detail)

fun LessonProgressEntity.toRecord() = LessonProgressRecord(lessonId, completedAtMs, quizScore, attempts)
fun LessonProgressRecord.toEntity() = LessonProgressEntity(lessonId, completedAtMs, quizScore, attempts)

fun AudioRefEntity.toRecord() = AudioRefRecord(id, uri, title, addedAtMs, dbJson.decodeFromString(loopsSer, loopsJson))
fun AudioRefRecord.toEntity() = AudioRefEntity(id, uri, title, addedAtMs, dbJson.encodeToString(loopsSer, loops))
