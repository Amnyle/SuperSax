package app.saxpractice.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.saxpractice.domain.drills.SaxKey
import kotlin.math.hypot

/** Schematic position of a key: centre (fractions of width/height), size, and whether it is a round finger key. */
private data class KeyShape(val x: Float, val y: Float, val w: Float, val h: Float, val round: Boolean)

private val SHAPES = mapOf(
    // Seen from the player: left hand on top, the player's left on the left.
    SaxKey.FRONT_F to KeyShape(0.45f, 0.05f, 0.10f, 0.03f, false),
    SaxKey.OCT to KeyShape(0.10f, 0.13f, 0.08f, 0.05f, false),
    SaxKey.PALM_D to KeyShape(0.24f, 0.05f, 0.10f, 0.035f, false),
    SaxKey.PALM_EB to KeyShape(0.22f, 0.10f, 0.10f, 0.035f, false),
    SaxKey.PALM_F to KeyShape(0.24f, 0.15f, 0.10f, 0.035f, false),
    SaxKey.L1 to KeyShape(0.45f, 0.13f, 0.13f, 0.065f, true),
    SaxKey.BIS to KeyShape(0.45f, 0.195f, 0.06f, 0.025f, true),
    SaxKey.L2 to KeyShape(0.45f, 0.26f, 0.13f, 0.065f, true),
    SaxKey.L3 to KeyShape(0.45f, 0.36f, 0.13f, 0.065f, true),
    SaxKey.G_SHARP to KeyShape(0.24f, 0.40f, 0.10f, 0.035f, false),
    SaxKey.LOW_C_SHARP to KeyShape(0.24f, 0.455f, 0.10f, 0.035f, false),
    SaxKey.LOW_B to KeyShape(0.18f, 0.51f, 0.08f, 0.035f, false),
    SaxKey.LOW_BB to KeyShape(0.29f, 0.51f, 0.08f, 0.035f, false),
    SaxKey.SIDE_E to KeyShape(0.72f, 0.55f, 0.08f, 0.04f, false),
    SaxKey.SIDE_C to KeyShape(0.72f, 0.61f, 0.08f, 0.04f, false),
    SaxKey.SIDE_BB to KeyShape(0.72f, 0.67f, 0.08f, 0.04f, false),
    SaxKey.HIGH_FSHARP to KeyShape(0.84f, 0.61f, 0.07f, 0.04f, false),
    SaxKey.R1 to KeyShape(0.45f, 0.60f, 0.13f, 0.065f, true),
    SaxKey.R2 to KeyShape(0.45f, 0.70f, 0.13f, 0.065f, true),
    SaxKey.R3 to KeyShape(0.45f, 0.80f, 0.13f, 0.065f, true),
    SaxKey.LOW_EB to KeyShape(0.68f, 0.88f, 0.10f, 0.035f, false),
    SaxKey.LOW_C to KeyShape(0.68f, 0.94f, 0.10f, 0.035f, false),
)

/**
 * A schematic of the alto keys drawn natively: left hand on top (octave key, palm keys, front F,
 * 1-bis-2-3, G♯ and the low C♯/B/B♭ pinky keys), right hand below (side keys, 4-5-6, low E♭/C).
 * Pressed keys are filled; when [onToggle] is set, tapping a key toggles it.
 */
@Composable
fun FingeringDiagram(pressed: Set<SaxKey>, description: String, onToggle: ((SaxKey) -> Unit)? = null) {
    val outline = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.surfaceVariant
    var mod = Modifier.width(220.dp).height(420.dp).semantics { contentDescription = description }
    if (onToggle != null) mod = mod.pointerInput(Unit) {
        detectTapGestures { p ->
            val hit = SHAPES.minByOrNull { (_, s) -> hypot(p.x - s.x * size.width, p.y - s.y * size.height) }
            hit?.let { (k, s) ->
                val d = hypot(p.x - s.x * size.width, p.y - s.y * size.height)
                if (d < maxOf(s.w * size.width, s.h * size.height) * 0.9f + 12) onToggle(k)
            }
        }
    }
    Canvas(mod) {
        // The body tube.
        drawRoundRect(body, Offset(size.width * 0.32f, 0f), Size(size.width * 0.26f, size.height), CornerRadius(30f, 30f))
        SHAPES.forEach { (k, s) ->
            val c = Offset(s.x * size.width, s.y * size.height)
            val on = k in pressed
            if (s.round) {
                val r = s.w * size.width / 2
                if (on) drawCircle(fill, r, c)
                drawCircle(outline, r, c, style = Stroke(3f))
            } else {
                val sz = Size(s.w * size.width, s.h * size.height)
                val tl = Offset(c.x - sz.width / 2, c.y - sz.height / 2)
                if (on) drawRoundRect(fill, tl, sz, CornerRadius(12f, 12f))
                drawRoundRect(outline, tl, sz, CornerRadius(12f, 12f), style = Stroke(3f))
            }
        }
    }
}
