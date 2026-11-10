package app.saxpractice.domain

import app.saxpractice.domain.backup.BackupCodec
import app.saxpractice.domain.backup.BackupData
import app.saxpractice.domain.backup.BackupStore
import app.saxpractice.domain.backup.BackupVersionException
import app.saxpractice.domain.backup.BackupZip
import app.saxpractice.domain.backup.SettingsRecord
import app.saxpractice.domain.common.BlockLogRecord
import app.saxpractice.domain.common.GoalRecord
import app.saxpractice.domain.common.GoalType
import app.saxpractice.domain.common.RunRecord
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.domain.common.StoredProgram
import app.saxpractice.domain.common.TunerHoldRecord
import app.saxpractice.domain.pitch.NoteNaming
import app.saxpractice.domain.program.BlockKind
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.domain.program.PlayerState
import app.saxpractice.domain.program.ScaleProgress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupTest {
    private class MemoryStore(var data: BackupData = BackupData()) : BackupStore {
        override suspend fun snapshot() = data
        override suspend fun replaceAll(data: BackupData) { this.data = data }
    }

    private val sample = BackupData(
        exportedAtMs = 42,
        settings = SettingsRecord(language = "fr", noteNaming = NoteNaming.SOLFEGE, a4Hz = 442.0, onboardingDone = true),
        player = PlayerState(scales = listOf(ScaleProgress("MAJOR:Eb", 75, 1, 70)), holdTargetSec = 12),
        programs = listOf(StoredProgram("p1", "{\"id\":\"p1\"}", true, 5)),
        sessions = listOf(SessionRecord("s1", 1000, 2000, "p1", "d1", 1, false, 1200, 1100, 3, "ok",
            blocks = listOf(BlockLogRecord(0, BlockKind.SCALES, ExerciseType.SCALE, "scale-runs", 300, 280, scaleKey = "MAJOR:Eb", bpm = 75)),
            runs = listOf(RunRecord("MAJOR:Eb", 75, true)))),
        goals = listOf(GoalRecord("g1", GoalType.TEMPO, "Eb at 90", 10, scaleKey = "MAJOR:Eb", bpm = 90)),
        holds = listOf(TunerHoldRecord("h1", 5, 63, 10.5, 0.8, 10.0, -3.0, -12.0, -1.1, -20.0, 5.0, listOf(1f, -2f, -3f))),
    )

    @Test fun roundTripExportWipeImport() = runTest {
        val store = MemoryStore(sample)
        val recording = "fake-audio".toByteArray()
        val out = ByteArrayOutputStream()
        BackupZip.write(out, store.snapshot(), mapOf("recordings/r1.m4a" to { ByteArrayInputStream(recording) }))
        store.replaceAll(BackupData()) // wipe
        val files = mutableMapOf<String, ByteArray>()
        val restored = BackupZip.read(ByteArrayInputStream(out.toByteArray())) { name, s -> files[name] = s.readBytes() }
        store.replaceAll(restored)
        assertEquals(sample, store.snapshot())
        assertEquals("fake-audio", String(files.getValue("recordings/r1.m4a")))
    }

    @Test fun refusesNewerVersion() {
        val text = BackupCodec.encode(sample)
        val obj = BackupCodec.json.parseToJsonElement(text).jsonObject
        val newer = JsonObject(obj + ("schemaVersion" to JsonPrimitive(BackupCodec.CURRENT_VERSION + 1))).toString()
        val e = assertFailsWith<BackupVersionException> { BackupCodec.decode(newer) }
        assertEquals(BackupVersionException.Reason.TOO_NEW, e.reason)
        val missing = JsonObject(obj - "schemaVersion").toString()
        assertEquals(BackupVersionException.Reason.MISSING, assertFailsWith<BackupVersionException> { BackupCodec.decode(missing) }.reason)
        assertFailsWith<BackupVersionException> { BackupZip.read(ByteArrayInputStream(ByteArrayOutputStream().also { java.util.zip.ZipOutputStream(it).close() }.toByteArray())) { _, _ -> } }
    }

    @Test fun migrationPolicyAppliesStepsInOrder() {
        // Simulate a future schema 2 that renamed "goals" to "targets" and is read back by a v2 app.
        val v1 = BackupCodec.json.parseToJsonElement(BackupCodec.encode(sample)).jsonObject
        val steps = mapOf<Int, (JsonObject) -> JsonObject>(1 to { o -> JsonObject(o - "goals" + ("targets" to o.getValue("goals"))) })
        BackupCodec.migrations.putAll(steps)
        try {
            val migrated = BackupCodec.migrate(v1, 1, 2)
            assertEquals(2, (migrated["schemaVersion"] as JsonPrimitive).content.toInt())
            assertEquals(v1["goals"], migrated["targets"])
        } finally {
            BackupCodec.migrations.clear()
        }
        assertFailsWith<IllegalStateException> { BackupCodec.migrate(v1, 1, 2) }
    }

    @Test fun unknownFieldsAreIgnored() {
        val obj = BackupCodec.json.parseToJsonElement(BackupCodec.encode(sample)).jsonObject
        val extra = JsonObject(obj + ("somethingNew" to JsonPrimitive("x"))).toString()
        assertEquals(sample, BackupCodec.decode(extra))
    }
}
