package app.saxpractice.domain.backup

import app.saxpractice.domain.common.AudioRefRecord
import app.saxpractice.domain.common.DrillResultRecord
import app.saxpractice.domain.common.GoalRecord
import app.saxpractice.domain.common.LessonProgressRecord
import app.saxpractice.domain.common.MouthpieceRecord
import app.saxpractice.domain.common.PieceProgressRecord
import app.saxpractice.domain.common.RankEventRecord
import app.saxpractice.domain.common.RankSnapshotRecord
import app.saxpractice.domain.common.RecordingRecord
import app.saxpractice.domain.common.ReedRecord
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.domain.common.StoredProgram
import app.saxpractice.domain.common.TunerHoldRecord
import app.saxpractice.domain.common.UserPieceRecord
import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.domain.pitch.NoteNaming
import app.saxpractice.domain.program.OnboardingAnswers
import app.saxpractice.domain.program.PlayerState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class SynthTimbre { SINE, SOFT }

/** All user settings. Language "system" follows the device; otherwise "en" or "fr". */
@Serializable
data class SettingsRecord(
    val language: String = "system",
    val noteNaming: NoteNaming = NoteNaming.LETTERS,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val a4Hz: Double = 440.0,
    val instrument: Instrument = Instrument.ALTO_SAX,
    val silenceNotifications: Boolean = false,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val restSec: Int = 60,
    val experimental: Boolean = false,
    val onboardingDone: Boolean = false,
    val synthTimbre: SynthTimbre = SynthTimbre.SINE,
    val currentReedId: String? = null,
    val currentMouthpieceId: String? = null,
)

/** The whole user dataset, as stored in `backup.json`. */
@Serializable
data class BackupData(
    val schemaVersion: Int = BackupCodec.CURRENT_VERSION,
    val exportedAtMs: Long = 0,
    val settings: SettingsRecord = SettingsRecord(),
    val player: PlayerState = PlayerState(),
    val onboarding: OnboardingAnswers? = null,
    val programs: List<StoredProgram> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    val goals: List<GoalRecord> = emptyList(),
    val holds: List<TunerHoldRecord> = emptyList(),
    val rankEvents: List<RankEventRecord> = emptyList(),
    val rankSnapshots: List<RankSnapshotRecord> = emptyList(),
    val pieceProgress: List<PieceProgressRecord> = emptyList(),
    val userPieces: List<UserPieceRecord> = emptyList(),
    val recordings: List<RecordingRecord> = emptyList(),
    val reeds: List<ReedRecord> = emptyList(),
    val mouthpieces: List<MouthpieceRecord> = emptyList(),
    val drillResults: List<DrillResultRecord> = emptyList(),
    val lessons: List<LessonProgressRecord> = emptyList(),
    val audioRefs: List<AudioRefRecord> = emptyList(),
)

/** Something that can hand over and replace the full dataset (Room in the app, in-memory in tests). */
interface BackupStore {
    suspend fun snapshot(): BackupData
    suspend fun replaceAll(data: BackupData)
}

class BackupVersionException(val found: Int, val reason: Reason) : Exception("Backup schema version $found: $reason") {
    enum class Reason { TOO_NEW, TOO_OLD, MISSING }
}

/**
 * JSON encoding of [BackupData] with a version check and a migration chain.
 *
 * Migration policy: every schema change bumps [CURRENT_VERSION] and adds one step to [migrations]
 * that rewrites the JSON object of version N into version N+1. Importing applies the steps in order,
 * so any version from [MIN_SUPPORTED] up can be read. Files newer than the app are refused (never
 * guessed). Unknown fields are ignored; fields added later get their defaults.
 */
object BackupCodec {
    const val CURRENT_VERSION = 1
    const val MIN_SUPPORTED = 1

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    /** Step N -> N+1, keyed by N. Empty while the schema is at version 1. */
    val migrations: MutableMap<Int, (JsonObject) -> JsonObject> = mutableMapOf()

    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data.copy(schemaVersion = CURRENT_VERSION))

    fun decode(text: String): BackupData {
        var obj = json.parseToJsonElement(text).jsonObject
        val version = obj["schemaVersion"]?.jsonPrimitive?.int ?: throw BackupVersionException(-1, BackupVersionException.Reason.MISSING)
        if (version > CURRENT_VERSION) throw BackupVersionException(version, BackupVersionException.Reason.TOO_NEW)
        if (version < MIN_SUPPORTED) throw BackupVersionException(version, BackupVersionException.Reason.TOO_OLD)
        obj = migrate(obj, version, CURRENT_VERSION)
        return json.decodeFromJsonElement(BackupData.serializer(), obj)
    }

    fun migrate(obj: JsonObject, from: Int, to: Int): JsonObject {
        var o = obj
        for (v in from until to) {
            val step = migrations[v] ?: error("No migration from schema $v")
            o = step(o)
            o = JsonObject(o + ("schemaVersion" to JsonPrimitive(v + 1)))
        }
        return o
    }
}

/**
 * The backup `.zip`: `backup.json`, optional `recordings/` and `imports/` folders.
 */
object BackupZip {
    const val JSON_ENTRY = "backup.json"
    const val RECORDINGS_DIR = "recordings/"
    const val IMPORTS_DIR = "imports/"

    /** Writes the zip. [files] maps entry names (with folder prefix) to stream openers. */
    fun write(out: OutputStream, data: BackupData, files: Map<String, () -> InputStream>) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(BackupCodec.encode(data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (name, open) ->
                require(name.startsWith(RECORDINGS_DIR) || name.startsWith(IMPORTS_DIR)) { "Unexpected entry $name" }
                zip.putNextEntry(ZipEntry(name))
                open().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads and validates the zip. File entries are passed to [onFile] (name, stream) as they come;
     * the data is decoded (and its version checked) at the end. Path traversal entries are refused.
     */
    fun read(input: InputStream, onFile: (String, InputStream) -> Unit): BackupData {
        var text: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                require(!name.contains("..") && !name.startsWith("/")) { "Unsafe entry $name" }
                when {
                    name == JSON_ENTRY -> text = zip.readBytes().toString(Charsets.UTF_8)
                    e.isDirectory -> Unit
                    name.startsWith(RECORDINGS_DIR) || name.startsWith(IMPORTS_DIR) -> onFile(name, zip)
                }
                zip.closeEntry()
            }
        }
        return BackupCodec.decode(text ?: throw BackupVersionException(-1, BackupVersionException.Reason.MISSING))
    }
}
