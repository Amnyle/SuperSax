package app.saxpractice.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.saxpractice.AppContainer
import app.saxpractice.R
import app.saxpractice.domain.common.DrillKind
import app.saxpractice.domain.common.DrillResultRecord
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.ui.components.BigButton
import java.util.UUID

/** Saves a finished phone drill: the drill result (Musicianship) and a phone session in the log. */
suspend fun AppContainer.savePhoneDrill(kind: DrillKind, level: Int, questions: Int, correct: Int, meanMs: Long, startedAtMs: Long, detail: String = "") {
    val now = clock.nowMs()
    progress.saveDrill(DrillResultRecord(UUID.randomUUID().toString(), now, kind, level, questions, correct, meanMs, detail))
    val sec = ((now - startedAtMs) / 1000).toInt().coerceAtLeast(1)
    practice.saveSession(SessionRecord(UUID.randomUUID().toString(), startedAtMs, now, null, null, null, phone = true, plannedSec = sec, playedSec = sec, note = kind.name))
}

/** The last level reached in a drill kind, from stored results. */
fun lastLevel(results: List<DrillResultRecord>, kind: DrillKind, default: Int = 1): Int =
    results.filter { it.kind == kind }.maxByOrNull { it.atMs }?.detail?.substringAfter("next=", "")?.toIntOrNull() ?: default

/** The calm end screen of every finite drill. */
@Composable
fun DoneForToday(correct: Int, total: Int, meanMs: Long?, levelBefore: Int?, levelAfter: Int?, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.done_for_today), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.drill_score, correct, total), style = MaterialTheme.typography.titleLarge)
        if (meanMs != null) Text(stringResource(R.string.drill_mean_time, meanMs / 1000.0), style = MaterialTheme.typography.bodyLarge)
        if (levelBefore != null && levelAfter != null) Text(
            when {
                levelAfter > levelBefore -> stringResource(R.string.level_up, levelAfter)
                levelAfter < levelBefore -> stringResource(R.string.level_down, levelAfter)
                else -> stringResource(R.string.level_same, levelAfter)
            },
            textAlign = TextAlign.Center,
        )
        Text(stringResource(R.string.done_put_phone_down), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        BigButton(stringResource(R.string.close), onClose)
    }
}
