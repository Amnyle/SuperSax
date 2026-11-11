package app.supersax.ui.rank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.RankEventKind
import app.supersax.domain.common.RankEventRecord
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.program.Pillar
import app.supersax.domain.rank.PillarResult
import app.supersax.domain.rank.RankResult
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.components.pillarLabel
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class RankViewModel(private val c: AppContainer) : ViewModel() {
    val result = c.rank.result.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val events = c.progress.rankEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A self-reported range note (flagged as such; altissimo tiers ignore it). */
    fun claimNote(writtenMidi: Int) = viewModelScope.launch {
        c.progress.addRankEvent(RankEventRecord(UUID.randomUUID().toString(), c.clock.nowMs(), RankEventKind.RANGE_NOTE, writtenMidi = writtenMidi, verified = false))
    }
}

/** Rank: staircase, radar with the limiter, and what each pillar needs next. No notifications, no sharing. */
@Composable
fun RankScreen(onBack: () -> Unit) {
    val vm = appViewModel { RankViewModel(it) }
    val r by vm.result.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Pillar?>(null) }
    ScreenScaffold(stringResource(R.string.rank), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            val result = r ?: return@ScrollColumn
            val name = rankName(result.overallTier, result.division)
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.rank_average, result.average), style = MaterialTheme.typography.bodyMedium)
            RankStaircase(result.overallTier, (1..8).map { tierName(it) }, stringResource(R.string.rank_staircase_desc, name))
            val labels = Pillar.entries.associateWith { pillarLabel(it) }
            RankRadar(result.pillars.mapValues { it.value.tier }, result.limiter, labels,
                stringResource(R.string.rank_radar_desc, labels.getValue(result.limiter)))
            Text(stringResource(R.string.rank_limiter, labels.getValue(result.limiter)), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error)
            RADAR_ORDER.forEach { p ->
                val pr = result.pillars.getValue(p)
                SectionCard(modifier = Modifier.clickable { open = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(labels.getValue(p), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(tierName(pr.tier))
                    }
                    if (p == Pillar.MUSICIANSHIP && pr.rawTier > pr.tier) {
                        Text(stringResource(R.string.rank_cap_note, tierName(pr.rawTier), tierName(pr.tier)), style = MaterialTheme.typography.bodySmall)
                    }
                    pr.next.firstOrNull { !it.met }?.let { Text(requirementText(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            val self = events.count { !it.verified && (it.kind == RankEventKind.CLEAN_PAIR || it.kind == RankEventKind.RANGE_NOTE) }
            val verified = events.count { it.verified }
            Text(stringResource(R.string.rank_evidence, verified, self), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.rank_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    open?.let { p -> r?.let { PillarDialog(it, it.pillars.getValue(p), vm) { open = null } } }
}

@Composable
private fun PillarDialog(result: RankResult, pr: PillarResult, vm: RankViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${pillarLabel(pr.pillar)} · ${tierName(pr.tier)}") },
        text = {
            Column {
                Text(stringResource(pillarHelp(pr.pillar)), style = MaterialTheme.typography.bodyMedium)
                if (pr.next.isEmpty()) Text(stringResource(R.string.rank_top), style = MaterialTheme.typography.titleSmall)
                else {
                    Text(stringResource(R.string.rank_next_tier, tierName(pr.rawTier + 1)), style = MaterialTheme.typography.titleSmall)
                    pr.next.forEach { req -> Text((if (req.met) "✓ " else "• ") + requirementText(req), style = MaterialTheme.typography.bodyMedium) }
                }
                if (pr.pillar == Pillar.MUSICIANSHIP) Text(stringResource(R.string.rank_cap_rule, result.musicianshipCap), style = MaterialTheme.typography.bodySmall)
                if (pr.pillar == Pillar.RANGE) RangeClaim(vm)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun RangeClaim(vm: RankViewModel) {
    var note by remember { mutableIntStateOf(86) }
    val naming = LocalSettings.current.noteNaming
    Text(stringResource(R.string.range_claim_hint), style = MaterialTheme.typography.bodySmall)
    Stepper(stringResource(R.string.range_claim_note), note, { note = it }, 56..90) { naming.format(SpelledPitch.fromMidi(it)) }
    OutlinedButton(onClick = { vm.claimNote(note) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.range_claim)) }
}

private fun pillarHelp(p: Pillar) = when (p) {
    Pillar.CONSISTENCY -> R.string.help_consistency
    Pillar.SOUND -> R.string.help_sound
    Pillar.TECHNIQUE -> R.string.help_technique
    Pillar.RANGE -> R.string.help_range
    Pillar.REPERTOIRE -> R.string.help_repertoire
    Pillar.MUSICIANSHIP -> R.string.help_musicianship
}
