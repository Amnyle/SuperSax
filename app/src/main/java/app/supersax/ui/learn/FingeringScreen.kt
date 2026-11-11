package app.supersax.ui.learn

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.clickable

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

/** Registers of the chart sheet (written notes). */
private enum class Register(val label: Int, val low: Int, val high: Int) {
    LOW(R.string.reg_low, 58, 64), MIDDLE(R.string.reg_middle, 65, 76), HIGH(R.string.reg_high, 77, 90), ALTISSIMO(R.string.reg_altissimo, 91, 127)
}

/** The fingering sheet: every written note of a register with its fingering; tap a note for the large view and alternates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FingeringChartScreen(onBack: () -> Unit) {
    val c = app.supersax.ui.nav.LocalContainer.current
    val chart = c.content.fingerings
    val naming = LocalSettings.current.noteNaming
    var register by rememberSaveable { mutableStateOf(Register.MIDDLE) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScaffold(stringResource(R.string.fing_chart_title), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Register.entries.forEach { r ->
                    FilterChip(selected = register == r, onClick = { register = r }, label = { Text(stringResource(r.label)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (register == Register.ALTISSIMO) ErrorBanner(stringResource(R.string.fing_unverified))
            val notes = chart.entries.filter { SpelledPitch.parse(it.note).midi in register.low..register.high }
            notes.chunked(2).forEach { row ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { e ->
                        app.supersax.ui.components.SectionCard(modifier = Modifier.weight(1f).clickable { open = e.note }) {
                            Text(naming.format(SpelledPitch.parse(e.note)), style = MaterialTheme.typography.titleLarge)
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                FingeringDiagram(e.keys, stringResource(R.string.fing_diagram_desc), width = 120.dp)
                            }
                            if (e.alternates.isNotEmpty()) Text(stringResource(R.string.fing_alternates_n, e.alternates.size),
                                style = MaterialTheme.typography.bodySmall, color = app.supersax.ui.theme.Sax.colors.textSecondary)
                        }
                    }
                    if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    open?.let { note ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.close)) } },
            title = { Text(naming.format(SpelledPitch.parse(note))) },
            text = { androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { Chart(chart, note) } },
        )
    }
}

@Composable
private fun Chart(chart: app.supersax.domain.drills.FingeringChart, selected: String) {
    val e = chart.forNote(selected) ?: return
    if (!e.verified) ErrorBanner(stringResource(R.string.fing_unverified))
    StaffView(StaffLayout.singleNote(SpelledPitch.parse(e.note)), staffSpace = 10.dp, showTime = false)
    FingeringDiagram(e.keys, stringResource(R.string.fing_diagram_desc))
    e.alternates.forEach { alt ->
        Text(stringResource(R.string.fing_alternate), style = MaterialTheme.typography.titleSmall)
        FingeringDiagram(alt, stringResource(R.string.fing_diagram_desc))
    }
}
