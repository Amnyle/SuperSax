package app.supersax.notation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A treble staff the player taps to place a note (lines and spaces from A3 to C7). The chosen
 * accidental is applied by the caller; [selectedStep] is drawn with ledger lines.
 */
@Composable
fun StaffInput(selectedStep: Int?, selectedAlter: Int, description: String, onStep: (Int) -> Unit) {
    val typeface = rememberBravura()
    val color = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val topSpaces = 6f // room above the staff (up to C7, step 19)
    val totalSpaces = 15f
    Canvas(
        Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val sp = size.height / totalSpaces
                    val yStaff = pos.y / sp - topSpaces
                    val step = ((4f - yStaff) * 2).roundToInt().coerceIn(-4, 19)
                    onStep(step)
                }
            },
    ) {
        val sp = size.height / totalSpaces
        fun py(y: Float) = (y + topSpaces) * sp
        for (l in 0..4) drawLine(color, Offset(0f, py(l.toFloat())), Offset(size.width, py(l.toFloat())), 0.13f * sp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 4 * sp; this.color = color.toArgb() }
        drawContext.canvas.nativeCanvas.drawText(Smufl.G_CLEF, 0.5f * sp, py(3f), paint)
        val step = selectedStep ?: return@Canvas
        val x = size.width / 2
        val y = StaffGeometry.y(step)
        StaffGeometry.ledgerSteps(step).forEach { ls ->
            val ly = py(StaffGeometry.y(ls))
            drawLine(color, Offset(x - 0.5f * sp, ly), Offset(x + 1.7f * sp, ly), 0.16f * sp)
        }
        paint.color = accent.toArgb()
        drawContext.canvas.nativeCanvas.drawText(Smufl.HEAD_WHOLE, x, py(y), paint)
        if (selectedAlter != 0) drawContext.canvas.nativeCanvas.drawText(Smufl.accidental(selectedAlter), x - 1.4f * sp, py(y), paint)
    }
}
