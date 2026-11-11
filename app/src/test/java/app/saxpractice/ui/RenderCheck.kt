package app.saxpractice.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.saxpractice.domain.drills.SaxKey
import app.saxpractice.domain.library.Score
import app.saxpractice.domain.program.Pillar
import app.saxpractice.notation.StaffView
import app.saxpractice.ui.audio.CentsNeedle
import app.saxpractice.ui.learn.FingeringDiagram
import app.saxpractice.ui.rank.RankRadar
import app.saxpractice.ui.rank.RankStaircase
import app.saxpractice.ui.theme.SaxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the hand-drawn widgets to PNG files (build/render-check) so they can be looked at. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderCheck {
    @get:Rule val rule = createComposeRule()

    private fun save(name: String) {
        val dir = File("build/render-check").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun staff() {
        val score = Score(1, 4, 4, 80, listOf(
            "Bb3:1/4 C4:1/4 F#4:1/4 G4:1/4", "A5:3/8 G5:1/8 F5:1/4~ F5:1/4", "r:1/4 E5:1/8 D5:1/8 C5:1/16 B4:1/16 A4:1/8 r:1/4",
            "Fn5:1/2 F#6:1/2", "Ebb5:1/4 C##5:1/4 r:1/2", "G4:1/1",
        ).map { Score.parseMeasure(it) })
        rule.setContent { SaxTheme { Column(Modifier.background(Color.White).width(400.dp)) { StaffView(score, staffSpace = 9.dp) } } }
        save("staff")
    }

    @Test fun widgets() {
        rule.setContent {
            SaxTheme {
                Column(Modifier.background(Color.White).width(400.dp)) {
                    FingeringDiagram(setOf(SaxKey.OCT, SaxKey.L1, SaxKey.L2, SaxKey.L3, SaxKey.R1, SaxKey.G_SHARP), "")
                    RankStaircase(3, listOf("Iron", "Bronze", "Silver", "Gold", "Platinum", "Emerald", "Diamond", "Master"), "")
                    RankRadar(mapOf(Pillar.SOUND to 2, Pillar.TECHNIQUE to 3, Pillar.RANGE to 4, Pillar.REPERTOIRE to 2, Pillar.CONSISTENCY to 5, Pillar.MUSICIANSHIP to 1),
                        Pillar.MUSICIANSHIP, Pillar.entries.associateWith { it.name.take(5) }, "")
                    CentsNeedle(12.0, 10.0, Modifier.width(300.dp).height(120.dp))
                }
            }
        }
        save("widgets")
    }
}

