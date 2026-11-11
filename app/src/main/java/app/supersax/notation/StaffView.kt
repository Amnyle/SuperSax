package app.supersax.notation

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import app.supersax.R
import app.supersax.domain.library.Score

/** The Bravura typeface (SIL OFL), loaded once. */
@Composable
fun rememberBravura(): Typeface {
    val context = LocalContext.current
    return remember { ResourcesCompat.getFont(context, R.font.bravura) ?: Typeface.DEFAULT }
}

/**
 * Draws a single-voice score on treble staves with Compose Canvas, wrapping into systems.
 * Pass the score already transposed for display (written for alto by default).
 */
@Composable
fun StaffView(
    score: Score,
    modifier: Modifier = Modifier,
    staffSpace: Dp = 9.dp,
    highlightIndex: Int = -1,
    showTime: Boolean = true,
    description: String = "",
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlight: Color = MaterialTheme.colorScheme.primary,
) {
    val typeface = rememberBravura()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacePx = with(density) { staffSpace.toPx() }
        val widthSpaces = with(density) { maxWidth.toPx() } / spacePx
        val systems = remember(score, widthSpaces, showTime) { StaffLayout.layout(score, widthSpaces, showTime) }
        val gap = 1.5f
        val heightSpaces = systems.sumOf { (it.bottom - it.top + gap).toDouble() }.toFloat()
        Canvas(
            Modifier.fillMaxWidth().height(with(density) { (heightSpaces * spacePx).toDp() })
                .semantics { if (description.isNotEmpty()) contentDescription = description },
        ) {
            var offsetY = 0f
            systems.forEach { sys ->
                val originY = offsetY - sys.top
                drawSystem(sys, spacePx, originY, typeface, color, highlight, highlightIndex)
                offsetY += sys.bottom - sys.top + gap
            }
        }
    }
}

private fun DrawScope.drawSystem(sys: StaffSystem, sp: Float, originY: Float, typeface: Typeface, color: Color, highlight: Color, highlightIndex: Int) {
    fun px(x: Float) = x * sp
    fun py(y: Float) = (originY + y) * sp
    val right = minOf(size.width, sys.items.filterIsInstance<StaffItem.Line>().maxOfOrNull { px(it.x2) } ?: size.width)
    for (line in 0..4) {
        drawLine(color, Offset(0f, py(line.toFloat())), Offset(right, py(line.toFloat())), strokeWidth = 0.13f * sp)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 4 * sp }
    sys.items.forEach { item ->
        val c = if (highlightIndex >= 0 && itemIndex(item) == highlightIndex) highlight else color
        when (item) {
            is StaffItem.Glyph -> {
                paint.color = c.toArgb()
                drawContext.canvas.nativeCanvas.drawText(item.text, px(item.x), py(item.y), paint)
            }
            is StaffItem.Line -> drawLine(c, Offset(px(item.x1), py(item.y1)), Offset(px(item.x2), py(item.y2)), strokeWidth = item.thickness * sp, cap = StrokeCap.Butt)
            is StaffItem.Tie -> {
                val dir = if (item.above) -1 else 1
                val y = py(item.y + dir * 0.6f)
                val path = Path().apply {
                    moveTo(px(item.x1), y)
                    quadraticTo((px(item.x1) + px(item.x2)) / 2, y + dir * 0.9f * sp, px(item.x2), y)
                }
                drawPath(path, c, style = Stroke(width = 0.14f * sp))
            }
        }
    }
}

private fun itemIndex(i: StaffItem) = when (i) {
    is StaffItem.Glyph -> i.noteIndex
    is StaffItem.Line -> i.noteIndex
    is StaffItem.Tie -> -1
}
