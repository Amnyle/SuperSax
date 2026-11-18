package app.supersax.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.audio.MelodyNote
import app.supersax.domain.library.Fraction
import app.supersax.domain.library.Measure
import app.supersax.domain.library.Score
import app.supersax.domain.library.ScoreNote
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.scale.ScaleGenerator
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import app.supersax.notation.StaffView
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax

/** Degree label of a note [semitones] above the tonic, measured against the major scale (1, ♭3, ♯4…). */
fun degreeLabel(semitones: Int): String = when (Math.floorMod(semitones, 12)) {
    0 -> "1"; 1 -> "♭2"; 2 -> "2"; 3 -> "♭3"; 4 -> "3"; 5 -> "4"; 6 -> "♯4"
    7 -> "5"; 8 -> "♭6"; 9 -> "6"; 10 -> "♭7"; else -> "7"
}

@Composable
private fun scaleExplanation(t: ScaleType): String = stringResource(
    when (t) {
        ScaleType.MAJOR -> R.string.sn_major
        ScaleType.NATURAL_MINOR -> R.string.sn_minor
        ScaleType.HARMONIC_MINOR -> R.string.sn_harmonic_minor
        ScaleType.DORIAN -> R.string.sn_dorian
        ScaleType.PHRYGIAN -> R.string.sn_phrygian
        ScaleType.LYDIAN -> R.string.sn_lydian
        ScaleType.MIXOLYDIAN -> R.string.sn_mixolydian
        ScaleType.LOCRIAN -> R.string.sn_locrian
        ScaleType.MAJOR_ARPEGGIO -> R.string.sn_major_arpeggio
        ScaleType.MINOR_ARPEGGIO -> R.string.sn_minor_arpeggio
        ScaleType.MAJOR_PENTATONIC -> R.string.sn_major_pentatonic
        ScaleType.MINOR_PENTATONIC -> R.string.sn_minor_pentatonic
    },
)

/**
 * The notes of the scale to play, written for the player's instrument: what kind of scale it is, a table of
 * degrees and note names (tonic to octave), the notes on the staff and a Listen button. Open by default; it can be
 * folded once the scale is known.
 */
@Composable
fun ScaleNotesCard(scaleKey: String, bpm: Int) {
    val id = runCatching { ScaleId.parse(scaleKey) }.getOrNull() ?: return
    val settings = LocalSettings.current
    val c = LocalContainer.current
    val naming = settings.noteNaming
    val written = ScaleId(id.writtenTonic(settings.instrument), id.type)
    val notes: List<SpelledPitch> = ScaleGenerator.oneOctave(written, 4)
    var open by rememberSaveable(scaleKey) { mutableStateOf(true) }
    val out by c.audio.state.collectAsStateWithLifecycle()
    SectionCard(stringResource(R.string.sn_title, scaleLabel(scaleKey))) {
        if (!open) {
            TextButton(onClick = { open = true }) { Text(stringResource(R.string.sn_show)) }
            return@SectionCard
        }
        Text(scaleExplanation(id.type), style = MaterialTheme.typography.bodyMedium)
        // The table: degree above, note below, from the tonic to the octave.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            notes.forEach { p ->
                val semis = p.midi - notes.first().midi
                val tonic = semis % 12 == 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (tonic) Sax.colors.accentSoft else Sax.colors.elevated).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(degreeLabel(semis), style = MaterialTheme.typography.labelMedium, color = Sax.colors.textSecondary)
                    Text(naming.format(p.pitchClass), style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (tonic) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        val score = notes.map { ScoreNote(it, Fraction(1, 4)) }
        StaffView(Score(0, 4, 4, 60, score.chunked(4).map { Measure(it) }), staffSpace = 9.dp, showTime = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                if (out.melodyPlaying) c.audio.stopMelody()
                else {
                    // Up and back down, at the block's tempo (sounding pitch).
                    val up = notes.map { MelodyNote(settings.instrument.writtenToConcertMidi(it.midi), 1.0) }
                    c.audio.playMelody(up + up.dropLast(1).reversed(), bpm.coerceIn(40, 160))
                }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(if (out.melodyPlaying) R.string.stop else R.string.listen)) }
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.sn_hide)) }
        }
    }
}
