package app.supersax.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.FingeringDrill
import app.supersax.domain.drills.FingeringEntry
import app.supersax.domain.drills.QuestionType
import app.supersax.domain.drills.SaxKey
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.StaffLayout
import app.supersax.notation.StaffView
import app.supersax.ui.components.ErrorBanner
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.drills.DoneForToday
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The full fingering chart (main and alternate fingerings for each written note). */
@Composable
fun FingeringChartScreen(onBack: () -> Unit) {
    val c = app.supersax.ui.nav.LocalContainer.current
    ScreenScaffold(stringResource(R.string.chart), onBack = onBack) { padding ->
        ScrollColumn(padding) { Chart(c.content.fingerings) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chart(chart: app.supersax.domain.drills.FingeringChart) {
    val naming = LocalSettings.current.noteNaming
    var selected by rememberSaveable { mutableStateOf("G4") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chart.entries.forEach { e ->
            FilterChip(selected = selected == e.note, onClick = { selected = e.note }, label = { Text(naming.format(SpelledPitch.parse(e.note))) })
        }
    }
    val e = chart.forNote(selected) ?: return
    if (!e.verified) ErrorBanner(stringResource(R.string.fing_unverified))
    StaffView(StaffLayout.singleNote(SpelledPitch.parse(e.note)), staffSpace = 10.dp, showTime = false)
    FingeringDiagram(e.keys, stringResource(R.string.fing_diagram_desc))
    e.alternates.forEach { alt ->
        Text(stringResource(R.string.fing_alternate), style = MaterialTheme.typography.titleSmall)
        FingeringDiagram(alt, stringResource(R.string.fing_diagram_desc))
    }
}
