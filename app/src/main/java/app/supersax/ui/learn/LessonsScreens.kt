package app.supersax.ui.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.common.LessonProgressRecord
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.Lesson
import app.supersax.domain.drills.Quiz
import app.supersax.notation.StaffView
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.drills.DoneForToday
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LessonsViewModel(private val c: AppContainer) : ViewModel() {
    val lessons: List<Lesson> get() = c.content.lessons.sortedBy { it.order }
    val results = c.progress.drills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Lesson [order] is open when it is the first, or the lesson before it has at least 2 stars. */
fun lessonOpen(lessons: List<Lesson>, results: List<app.supersax.domain.common.DrillResultRecord>, lesson: Lesson): Boolean {
    val i = lessons.indexOfFirst { it.id == lesson.id }
    return i <= 0 || app.supersax.domain.drills.Levels.best(results, DrillKind.THEORY_QUIZ, lessons[i - 1].order) >= app.supersax.domain.drills.Levels.STARS_TO_UNLOCK
}

/** The list of theory lessons with completion and best quiz score. */
@Composable
fun LessonsScreen(onBack: () -> Unit, onLesson: (String) -> Unit) {
    val vm = appViewModel { LessonsViewModel(it) }
    val results by vm.results.collectAsStateWithLifecycle()
    val language = LocaleHelper.effective(LocalContext.current)
    ScreenScaffold(stringResource(R.string.lessons), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            val r = results ?: return@ScrollColumn
            Text(stringResource(R.string.levels_hint), style = MaterialTheme.typography.bodyMedium, color = app.supersax.ui.theme.Sax.colors.textSecondary)
            vm.lessons.forEach { l ->
                app.supersax.ui.practice.LevelRow(l.order, stringResource(R.string.minutes_short, l.minutes),
                    app.supersax.domain.drills.Levels.best(r, DrillKind.THEORY_QUIZ, l.order), lessonOpen(vm.lessons, r, l),
                    title = "${l.order}. ${l.title.get(language)}") { onLesson(l.id) }
            }
        }
    }
}

data class LessonUi(val reading: Boolean = true, val index: Int = 0, val correct: Int = 0, val last: Boolean? = null, val done: Boolean = false)

class LessonViewModel(private val c: AppContainer, val lessonId: String) : ViewModel() {
    val lesson: Lesson? = c.content.lessons.firstOrNull { it.id == lessonId }
    private val ordered = c.content.lessons.sortedBy { it.order }
    /** The lesson after this one, and whether this attempt opened it (2 stars for the first time). */
    val nextLesson: Lesson? = ordered.getOrNull(ordered.indexOfFirst { it.id == lessonId } + 1)
    private val _unlockedNext = MutableStateFlow(false)
    val unlockedNext = _unlockedNext.asStateFlow()
    private val _ui = MutableStateFlow(LessonUi())
    val ui = _ui.asStateFlow()
    private val startedAt = c.clock.nowMs()

    fun startQuiz() { _ui.value = LessonUi(reading = false) }

    fun answer(a: Answer, language: String) {
        val l = lesson ?: return
        val u = _ui.value
        if (u.last != null) return
        val ok = Quiz.check(l.quiz[u.index], a, language)
        _ui.value = u.copy(last = ok, correct = u.correct + if (ok) 1 else 0)
    }

    fun next() {
        val l = lesson ?: return
        val u = _ui.value
        if (u.index + 1 < l.quiz.size) { _ui.value = u.copy(index = u.index + 1, last = null); return }
        _ui.value = u.copy(done = true)
        val score = u.correct.toDouble() / l.quiz.size
        viewModelScope.launch {
            val previous = c.progress.lessons.first().firstOrNull { it.lessonId == l.id }
            // Keep the best score; count attempts.
            val bestBefore = app.supersax.domain.drills.Levels.best(c.progress.drills.first(), DrillKind.THEORY_QUIZ, l.order)
            _unlockedNext.value = nextLesson != null && bestBefore < app.supersax.domain.drills.Levels.STARS_TO_UNLOCK && app.supersax.domain.drills.Levels.stars(u.correct, l.quiz.size) >= app.supersax.domain.drills.Levels.STARS_TO_UNLOCK
            c.progress.saveLesson(LessonProgressRecord(l.id, c.clock.nowMs(), maxOf(score, previous?.quizScore ?: 0.0), (previous?.attempts ?: 0) + 1))
            c.savePhoneDrill(DrillKind.THEORY_QUIZ, l.order, l.quiz.size, u.correct, 0, startedAt, "lesson=${l.id}")
        }
    }
}

/** Reading (2–3 minutes), then one question at a time with free-form answers. */
@Composable
fun LessonScreen(lessonId: String, onBack: () -> Unit, onLesson: (String) -> Unit = {}) {
    val vm = appViewModel(key = "lesson-$lessonId") { LessonViewModel(it, lessonId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val language = LocaleHelper.effective(LocalContext.current)
    val naming = LocalSettings.current.noteNaming
    val l = vm.lesson ?: return
    ScreenScaffold(l.title.get(language), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            when {
                ui.done -> {
                    val unlocked by vm.unlockedNext.collectAsStateWithLifecycle()
                    val stars = app.supersax.domain.drills.Levels.stars(ui.correct, l.quiz.size)
                    val next = vm.nextLesson
                    app.supersax.ui.practice.LevelResult(
                        l.title.get(language), stars, ui.correct, l.quiz.size, unlocked, isLast = next == null,
                        nextOpen = next != null && (unlocked || stars >= app.supersax.domain.drills.Levels.STARS_TO_UNLOCK),
                        onNext = { next?.let { onLesson(it.id) } }, onRetry = vm::startQuiz, onLevels = onBack,
                    )
                }
                ui.reading -> {
                    l.sections.forEach { s ->
                        Text(s.heading.get(language), style = MaterialTheme.typography.titleLarge)
                        s.body.get(language).split("\n\n").forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        s.staff?.let { StaffView(it.toScore(), showTime = true) }
                    }
                    Button(onClick = vm::startQuiz, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.start_quiz, l.quiz.size)) }
                }
                else -> {
                    val q = l.quiz[ui.index]
                    Text(stringResource(R.string.question_of, ui.index + 1, l.quiz.size), style = MaterialTheme.typography.labelLarge)
                    Text(q.prompt.get(language), style = MaterialTheme.typography.titleMedium)
                    q.staff?.let { StaffView(it.toScore(), showTime = !it.free) }
                    AnswerInput(q.type, "${l.id}/${q.id}", ui.last == null) { vm.answer(it, language) }
                    ui.last?.let { ok ->
                        Text(if (ok) stringResource(R.string.correct) else stringResource(R.string.it_was, Quiz.expectedText(q, language, naming)),
                            color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                        Text(q.explanation.get(language), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = vm::next, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.next)) }
                    }
                }
            }
        }
    }
}
