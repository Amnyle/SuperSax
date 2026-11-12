package app.supersax.domain

import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.DrillLesson
import app.supersax.domain.drills.Levels
import app.supersax.domain.library.Score
import app.supersax.domain.pitch.NoteNaming
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DrillLessonsTest {
    private val lessons = Json.decodeFromString(MapSerializer(String.serializer(), ListSerializer(DrillLesson.serializer())),
        File("src/main/assets/drill_lessons/lessons.json").readText())

    @Test fun oneLessonPerLevelWithValidExamplesAndNotes() {
        for (k in listOf(DrillKind.READING, DrillKind.SCALE_RECOGNITION, DrillKind.FINGERING, DrillKind.EAR, DrillKind.RHYTHM)) {
            val list = lessons[k.name]!!
            assertEquals(Levels.count(k, 0), list.size, "$k")
            list.forEach { l ->
                l.example?.forEach { Score.parseMeasure(it) }
                (l.points + listOfNotNull(l.tip, l.title)).forEach { p ->
                    for (text in listOf(p.en, p.fr)) assertFalse(DrillLesson.notes(text, NoteNaming.SOLFEGE).contains('{'), text)
                }
            }
        }
    }
}
