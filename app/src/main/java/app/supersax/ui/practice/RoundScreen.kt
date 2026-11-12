package app.supersax.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoundUi(
    val loading: Boolean = true,
    val items: List<DrillItem> = emptyList(),
    val index: Int = 0,
    val correct: Int = 0,
    /** null while the current question is unanswered */
    val last: Boolean? = null,
    val times: List<Long> = emptyList(),
    val done: Boolean = false,
) {
    val meanMs: Long get() = if (times.isEmpty()) 0 else times.average().toLong()
}

/** A finite round of questions; subclasses build the items and save the result. */
abstract class RoundViewModel(protected val c: AppContainer) : ViewModel() {
    protected val _ui = MutableStateFlow(RoundUi())
    val ui: StateFlow<RoundUi> = _ui.asStateFlow()
    protected val startedAt = c.clock.nowMs()
    private var shownAt = 0L

    protected fun begin(items: List<DrillItem>) {
        _ui.value = RoundUi(loading = false, items = items, done = items.isEmpty())
        shownAt = c.clock.elapsedMs()
    }

    fun answer(ok: Boolean) {
        val u = _ui.value
        if (u.last != null || u.done) return
        _ui.value = u.copy(last = ok, correct = u.correct + if (ok) 1 else 0, times = u.times + (c.clock.elapsedMs() - shownAt))
    }

    fun next() {
        val u = _ui.value
        if (u.last == null) return
        if (u.index + 1 < u.items.size) {
            _ui.value = u.copy(index = u.index + 1, last = null)
            shownAt = c.clock.elapsedMs()
        } else {
            val finished = u.copy(done = true)
            _ui.value = finished
            viewModelScope.launch { onFinished(finished) }
        }
    }

    protected abstract suspend fun onFinished(u: RoundUi)
}

/** Full-screen round: progress at the top, the question, a feedback panel with Continue, then [result]. */
@Composable
fun RoundScreen(vm: RoundViewModel, ui: RoundUi, onClose: () -> Unit, result: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        if (ui.loading) return@Column
        if (ui.done) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { result() }
            return@Column
        }
        val progress by animateFloatAsState((ui.index + if (ui.last != null) 1f else 0f) / ui.items.size, label = "progress")
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Sax.colors.textSecondary) }
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.weight(1f).height(12.dp).clip(CircleShape),
                color = Sax.colors.accent, trackColor = Sax.colors.elevated, drawStopIndicator = {},
            )
            Spacer(Modifier.width(12.dp))
            Text("${ui.index + 1}/${ui.items.size}", style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
        }
        val item = ui.items[ui.index]
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DrillItemView(item, key = ui.index, enabled = ui.last == null, onAnswer = vm::answer)
        }
        AnimatedVisibility(ui.last != null, enter = slideInVertically { it } + fadeIn()) {
            val ok = ui.last == true
            FeedbackPanel(ok, item, vm::next)
        }
    }
}

@Composable
private fun FeedbackPanel(ok: Boolean, item: DrillItem, onNext: () -> Unit) {
    val tone = if (ok) Sax.colors.good else MaterialTheme.colorScheme.error
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(tone.copy(alpha = 0.14f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = tone, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(when {
                ok -> stringResource(R.string.feedback_right)
                item is DrillItem.Rhythm -> stringResource(R.string.rhythm_wrong)
                else -> stringResource(R.string.it_was, expectedText(item))
            },
                style = MaterialTheme.typography.titleLarge, color = tone)
        }
        explanationText(item)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (!ok) FeedbackExtra(item)
        Button(
            onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = tone, contentColor = Color.White),
        ) { Text(stringResource(R.string.continue_), style = MaterialTheme.typography.titleMedium) }
    }
}

/** Three stars, filled up to [stars]. */
@Composable
fun StarRow(stars: Int, size: Int = 44) {
    val desc = stringResource(R.string.stars_n, stars)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = desc }) {
        repeat(3) { i ->
            Icon(Icons.Rounded.Star, null, tint = if (i < stars) Sax.colors.accent else Sax.colors.elevated, modifier = Modifier.size(size.dp))
        }
    }
}

/** The end of a level round: stars, score, what opened, and what to do next. */
@Composable
fun LevelResult(
    levelName: String, stars: Int, correct: Int, total: Int, unlockedNext: Boolean, isLast: Boolean, nextOpen: Boolean,
    onNext: () -> Unit, onRetry: () -> Unit, onLevels: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StarRow(stars, 64)
        Text(levelName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.drill_score, correct, total), style = MaterialTheme.typography.titleLarge, color = Sax.colors.textSecondary)
        val message = when {
            unlockedNext -> stringResource(R.string.level_unlocked_next)
            stars >= 3 && isLast -> stringResource(R.string.level_all_mastered)
            stars >= 3 -> stringResource(R.string.level_mastered)
            stars < 2 -> stringResource(R.string.level_need_two)
            else -> stringResource(R.string.level_try_three)
        }
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        val amber = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)
        if (nextOpen && !isLast) Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = amber) {
            Text(stringResource(R.string.level_next), style = MaterialTheme.typography.titleMedium)
        }
        if (nextOpen && !isLast) OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.level_retry)) }
        else Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = amber) {
            Text(stringResource(R.string.level_retry), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onLevels, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.level_back)) }
    }
}
