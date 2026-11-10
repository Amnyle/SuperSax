package app.saxpractice.ui.review

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.LocaleHelper
import app.saxpractice.R
import app.saxpractice.domain.goals.GoalEvaluator
import app.saxpractice.domain.review.WeeklyReviewBuilder
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class WeeklyReviewViewModel(private val c: AppContainer) : ViewModel() {
    val weekOffset = MutableStateFlow(0)
    private val zone = ZoneId.systemDefault()

    val review = combine(weekOffset, c.practice.activeProgram, c.practice.sessions, c.progress.holds, c.progress.snapshots) { off, p, s, h, snaps ->
        val today = LocalDate.now(zone)
        val start = GoalEvaluator.weekStart(today).minusWeeks(off.toLong())
        WeeklyReviewBuilder.build(start, today, zone, p, s, h, snaps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Weekly review: plain facts (planned vs actual), copy or share as text. */
@Composable
fun WeeklyReviewScreen(onBack: () -> Unit) {
    val vm = appViewModel { WeeklyReviewViewModel(it) }
    val review by vm.review.collectAsStateWithLifecycle()
    val offset by vm.weekOffset.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val naming = app.saxpractice.ui.nav.LocalSettings.current.noteNaming
    var copied by remember { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.weekly_review), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.weekOffset.value = offset + 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.previous_week)) }
                if (offset > 0) TextButton(onClick = { vm.weekOffset.value = offset - 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.next_week)) }
            }
            val r = review ?: return@ScrollColumn
            val text = WeeklyReviewBuilder.text(r, LocaleHelper.effective(context), naming)
            SectionCard {
                text.lines().forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Sax Practice", text))
                    copied = true
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_text)) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.share)) }
            }
            if (copied) Text(stringResource(R.string.copied))
            Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
