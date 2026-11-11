package app.saxpractice.ui.rank

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.saxpractice.domain.program.Pillar
import app.saxpractice.ui.theme.MasterAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Eight ascending bars, Iron to Master; the current tier highlighted, Master in the accent colour. */
@Composable
fun RankStaircase(tier: Int, labels: List<String>, description: String) {
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val active = MaterialTheme.colorScheme.primary
    val passed = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val text = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = description }) {
        val n = 8
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val labelSpace = 22.dp.toPx()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; color = text.toArgb()
        }
        for (i in 0 until n) {
            val t = i + 1
            val h = (size.height - labelSpace) * (0.25f + 0.75f * t / n)
            val x = i * (w + gap)
            val color = when {
                t == tier -> if (t == 8) MasterAccent else active
                t < tier -> passed
                t == 8 -> MasterAccent.copy(alpha = 0.35f)
                else -> muted
            }
            drawRoundRect(color, Offset(x, size.height - labelSpace - h), Size(w, h), CornerRadius(6f, 6f))
            drawContext.canvas.nativeCanvas.drawText(labels[i], x + w / 2, size.height - 4.dp.toPx(), paint)
        }
    }
}

/** Six-axis radar of pillar tiers (0..8), the limiter axis highlighted. Axis order is fixed. */
val RADAR_ORDER = listOf(Pillar.CONSISTENCY, Pillar.SOUND, Pillar.TECHNIQUE, Pillar.RANGE, Pillar.REPERTOIRE, Pillar.MUSICIANSHIP)

@Composable
fun RankRadar(tiers: Map<Pillar, Int>, limiter: Pillar, labels: Map<Pillar, String>, description: String) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val limiterColor = MaterialTheme.colorScheme.error
    val text = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription = description }) {
        val cx = size.width / 2; val cy = size.height / 2
        val r = minOf(size.width, size.height) / 2 - 36.dp.toPx()
        fun point(i: Int, v: Float): Offset {
            val a = -PI / 2 + i * 2 * PI / 6
            return Offset(cx + (r * v * cos(a)).toFloat(), cy + (r * v * sin(a)).toFloat())
        }
        for (ring in 1..4) {
            val path = Path()
            for (i in 0..6) { val p = point(i % 6, ring / 4f); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, grid, style = Stroke(1.5f))
        }
        RADAR_ORDER.forEachIndexed { i, _ -> drawLine(grid, Offset(cx, cy), point(i, 1f), 1.5f) }
        val shape = Path()
        RADAR_ORDER.forEachIndexed { i, p ->
            val pt = point(i, (tiers[p] ?: 0) / 8f)
            if (i == 0) shape.moveTo(pt.x, pt.y) else shape.lineTo(pt.x, pt.y)
        }
        shape.close()
        drawPath(shape, fill.copy(alpha = 0.25f))
        drawPath(shape, fill, style = Stroke(4f))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER }
        RADAR_ORDER.forEachIndexed { i, p ->
            val pt = point(i, (tiers[p] ?: 0) / 8f)
            drawCircle(if (p == limiter) limiterColor else fill, if (p == limiter) 9f else 6f, pt)
            val lp = point(i, 1.18f)
            paint.color = (if (p == limiter) limiterColor else text).toArgb()
            paint.isFakeBoldText = p == limiter
            drawContext.canvas.nativeCanvas.drawText("${labels[p]} ${tiers[p] ?: 0}", lp.x, lp.y + 4.dp.toPx(), paint)
        }
    }
}
