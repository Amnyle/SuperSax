package app.supersax.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.supersax.R
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.Focus
import app.supersax.domain.program.Pillar
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import app.supersax.ui.nav.LocalSettings

/** Written name of a scale for the alto player, e.g. "C major" / "Do majeur" (stored concert E♭ major). */
@Composable
fun scaleLabel(key: String?): String {
    if (key == null) return stringResource(R.string.none)
    val id = runCatching { ScaleId.parse(key) }.getOrNull() ?: return key
    val s = LocalSettings.current
    return stringResource(R.string.scale_name_format, s.noteNaming.format(id.writtenTonic(s.instrument)), scaleTypeLabel(id.type))
}

@Composable
fun scaleTypeLabel(t: ScaleType): String = stringResource(
    when (t) {
        ScaleType.MAJOR -> R.string.scale_major
        ScaleType.NATURAL_MINOR -> R.string.scale_minor
        ScaleType.DORIAN -> R.string.scale_dorian
        ScaleType.PHRYGIAN -> R.string.scale_phrygian
        ScaleType.LYDIAN -> R.string.scale_lydian
        ScaleType.MIXOLYDIAN -> R.string.scale_mixolydian
        ScaleType.LOCRIAN -> R.string.scale_locrian
        ScaleType.MAJOR_ARPEGGIO -> R.string.scale_major_arpeggio
        ScaleType.MINOR_ARPEGGIO -> R.string.scale_minor_arpeggio
        ScaleType.MAJOR_PENTATONIC -> R.string.scale_major_pentatonic
        ScaleType.MINOR_PENTATONIC -> R.string.scale_minor_pentatonic
    },
)

/** Written note name for a concert MIDI number, in the chosen naming system. */
@Composable
fun writtenNoteLabel(concertMidi: Int, withOctave: Boolean = true): String {
    val s = LocalSettings.current
    return s.noteNaming.format(SpelledPitch.fromMidi(s.instrument.concertToWrittenMidi(concertMidi)), withOctave)
}

@Composable
fun concertNoteLabel(concertMidi: Int, withOctave: Boolean = true): String =
    LocalSettings.current.noteNaming.format(SpelledPitch.fromMidi(concertMidi), withOctave)

fun writtenLabel(naming: NoteNaming, instrument: Instrument, concertMidi: Int) =
    naming.format(SpelledPitch.fromMidi(instrument.concertToWrittenMidi(concertMidi)))

@Composable
fun blockKindLabel(k: BlockKind?): String = stringResource(
    when (k) {
        BlockKind.LONG_TONES -> R.string.block_long_tones
        BlockKind.SCALES -> R.string.block_scales
        BlockKind.NEW_MATERIAL -> R.string.block_new_material
        BlockKind.PLAY_FOR_FUN -> R.string.block_play_for_fun
        BlockKind.BREATHING -> R.string.block_breathing
        null -> R.string.rest
    },
)

@Composable
fun exerciseTypeLabel(t: ExerciseType?): String = stringResource(
    when (t) {
        ExerciseType.LONG_TONE -> R.string.ex_long_tone
        ExerciseType.RANGE_EXTENSION -> R.string.ex_range_extension
        ExerciseType.SCALE -> R.string.ex_scale
        ExerciseType.NEW_MATERIAL -> R.string.ex_new_material
        ExerciseType.PLAY_FOR_FUN -> R.string.ex_play_for_fun
        ExerciseType.BREATHING_PACER -> R.string.ex_breathing
        ExerciseType.READING -> R.string.ex_reading
        ExerciseType.SCALE_RECOGNITION -> R.string.ex_scale_recognition
        ExerciseType.THEORY_LESSON -> R.string.ex_theory
        ExerciseType.FINGERING -> R.string.ex_fingering
        null -> R.string.rest
    },
)

@Composable
fun focusLabel(f: Focus): String = stringResource(
    when (f) {
        Focus.TONE -> R.string.focus_tone
        Focus.TECHNIQUE -> R.string.focus_technique
        Focus.REPERTOIRE -> R.string.focus_repertoire
        Focus.READING -> R.string.focus_reading
        Focus.THEORY -> R.string.focus_theory
    },
)

@Composable
fun pillarLabel(p: Pillar): String = stringResource(
    when (p) {
        Pillar.CONSISTENCY -> R.string.pillar_consistency
        Pillar.SOUND -> R.string.pillar_sound
        Pillar.TECHNIQUE -> R.string.pillar_technique
        Pillar.RANGE -> R.string.pillar_range
        Pillar.REPERTOIRE -> R.string.pillar_repertoire
        Pillar.MUSICIANSHIP -> R.string.pillar_musicianship
    },
)

@Composable
fun dayLabel(isoDay: Int, short: Boolean = false): String {
    val full = listOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun)
    val abbr = listOf(R.string.day_mon_short, R.string.day_tue_short, R.string.day_wed_short, R.string.day_thu_short, R.string.day_fri_short, R.string.day_sat_short, R.string.day_sun_short)
    return stringResource((if (short) abbr else full)[isoDay - 1])
}
