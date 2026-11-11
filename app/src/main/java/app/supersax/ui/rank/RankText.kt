package app.supersax.ui.rank

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.rank.RankNames
import app.supersax.domain.rank.ReqKind
import app.supersax.domain.rank.Requirement
import app.supersax.domain.rank.ScaleFamily
import app.supersax.ui.nav.LocalSettings
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@Composable
fun tierName(tier: Int): String = RankNames.tier(tier, LocaleHelper.effective(LocalContext.current))

@Composable
fun rankName(tier: Int, division: Int): String = RankNames.full(tier, division, LocaleHelper.effective(LocalContext.current))

@Composable
private fun familyLabel(f: ScaleFamily?) = stringResource(
    when (f) {
        ScaleFamily.MINOR -> R.string.family_minor
        ScaleFamily.MODE -> R.string.family_mode
        ScaleFamily.ARPEGGIO -> R.string.family_arpeggio
        else -> R.string.family_major
    },
)

@Composable
private fun written(midi: Double): String =
    if (midi <= 0) "–" else LocalSettings.current.noteNaming.format(SpelledPitch.fromMidi(midi.roundToInt()))

/** One plain sentence per requirement, with where the player stands. */
@Composable
fun requirementText(r: Requirement): String {
    val t = r.target.roundToInt(); val c = r.current.roundToInt()
    return when (r.kind) {
        ReqKind.SCALES_AT_BPM -> stringResource(R.string.req_scales, c, t, familyLabel(r.family), (r.a ?: 0.0).roundToInt())
        ReqKind.LOWEST_NOTE -> stringResource(R.string.req_lowest, written(r.target), written(r.current))
        ReqKind.HIGHEST_NOTE -> stringResource(R.string.req_highest, written(r.target), written(r.current))
        ReqKind.HIGHEST_NOTE_VERIFIED -> stringResource(R.string.req_highest_verified, written(r.target), written(r.current))
        ReqKind.OVERTONE_MILESTONE -> stringResource(R.string.req_overtone)
        ReqKind.LONG_TONE_BLOCKS -> stringResource(R.string.req_long_tone_blocks, c, t)
        ReqKind.HOLD_NOTES -> stringResource(R.string.req_hold_notes, c, t, (r.a ?: 0.0).roundToInt(), ((r.c ?: 0.8) * 100).roundToInt(), (r.b ?: 0.0).roundToInt())
        ReqKind.HOLD_REGISTERS -> stringResource(R.string.req_hold_registers, c)
        ReqKind.ADHERENCE -> stringResource(R.string.req_adherence, c, t)
        ReqKind.SESSIONS_PER_WEEK -> stringResource(R.string.req_sessions_week, r.current, r.target)
        ReqKind.PIECE_DIFFICULTY -> stringResource(R.string.req_piece, t, c)
        ReqKind.READING_ROUND -> stringResource(R.string.req_reading, t, c, (r.a ?: 0.0).roundToInt()) +
            (r.b?.let { " " + stringResource(R.string.req_under_seconds, it / 1000) } ?: "")
        ReqKind.SCALE_ROUND -> stringResource(R.string.req_scale_round, t, c, (r.a ?: 0.0).roundToInt())
        ReqKind.LESSONS -> stringResource(R.string.req_lessons, c, t, (r.a ?: 0.0).roundToInt())
        ReqKind.ANY_ACTIVITY -> stringResource(R.string.req_any_activity)
    }
}
