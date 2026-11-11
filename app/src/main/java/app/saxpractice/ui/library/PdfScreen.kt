package app.saxpractice.ui.library

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saxpractice.R
import app.saxpractice.ui.components.ErrorBanner
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.nav.LocalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shows an imported PDF as it is, page by page, with Android's PdfRenderer. */
@Composable
fun PdfScreen(fileName: String, onBack: () -> Unit) {
    val c = LocalContainer.current
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(fileName) {
        val r = runCatching {
            withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(File(c.importsDir, fileName), ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd).use { renderer ->
                    (0 until renderer.pageCount).map { i ->
                        renderer.openPage(i).use { page ->
                            val w = 1400
                            val h = (w.toFloat() * page.height / page.width).toInt()
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        }
        pages = r.getOrNull(); failed = r.isFailure
    }
    ScreenScaffold(stringResource(R.string.pdf_score), onBack = onBack) { padding ->
        if (failed) { ErrorBanner(stringResource(R.string.pdf_failed)); return@ScreenScaffold }
        val list = pages ?: run { Text(stringResource(R.string.loading), Modifier.padding(padding).padding(16.dp)); return@ScreenScaffold }
        LazyColumn(Modifier.padding(padding)) {
            items(list) { bmp ->
                Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(bottom = 8.dp))
            }
        }
    }
}
