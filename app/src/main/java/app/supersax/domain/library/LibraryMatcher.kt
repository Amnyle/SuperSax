package app.supersax.domain.library

import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.PieceStage
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.planner.PieceChoice
import app.supersax.domain.program.PlayerState

enum class PieceState { LOCKED, STRETCH, AVAILABLE, LEARNING, PLAYABLE, CLEAN_AT_TEMPO }

/** Why a piece is locked or a stretch. Semitone counts are how far the range is exceeded. */
sealed interface Missing {
    data class RangeLow(val semitones: Int, val writtenMidi: Int) : Missing
    data class RangeHigh(val semitones: Int, val writtenMidi: Int) : Missing
    data class Scale(val scaleKey: String, val isNextUnlock: Boolean) : Missing
    data object NoNotes : Missing
}

data class PieceMatch(val piece: Piece, val state: PieceState, val missing: List<Missing>)

/**
 * Matches pieces to the player.
 *
 * A piece unlocks when its written range fits the player's range and the scale of its key (or its
 * relative) has a clean pair. It is a **stretch** when exactly one requirement is missing by a small
 * margin: the range exceeded by at most [STRETCH_SEMITONES], or the key's scale being the next one to
 * unlock. A recorded stage (learning, playable, clean at tempo) always wins.
 */
object LibraryMatcher {
    const val STRETCH_SEMITONES = 2

    fun match(
        piece: Piece,
        player: PlayerState,
        progress: List<PieceProgressRecord>,
        nextUnlock: String?,
        instrument: Instrument = Instrument.ALTO_SAX,
    ): PieceMatch {
        val stage = progress.filter { it.pieceId == piece.id && it.sectionId.isEmpty() }.maxByOrNull { it.updatedAtMs }?.stage
        val missing = missing(piece, player, nextUnlock, instrument)
        val state = when (stage) {
            PieceStage.CLEAN_AT_TEMPO -> PieceState.CLEAN_AT_TEMPO
            PieceStage.PLAYABLE -> PieceState.PLAYABLE
            PieceStage.LEARNING -> PieceState.LEARNING
            else -> when {
                missing.isEmpty() -> PieceState.AVAILABLE
                missing.size == 1 && isSmall(missing.single()) -> PieceState.STRETCH
                else -> PieceState.LOCKED
            }
        }
        return PieceMatch(piece, state, missing)
    }

    private fun isSmall(m: Missing) = when (m) {
        is Missing.RangeLow -> m.semitones <= STRETCH_SEMITONES
        is Missing.RangeHigh -> m.semitones <= STRETCH_SEMITONES
        is Missing.Scale -> m.isNextUnlock
        Missing.NoNotes -> false
    }

    fun missing(piece: Piece, player: PlayerState, nextUnlock: String?, instrument: Instrument = Instrument.ALTO_SAX): List<Missing> {
        if (!piece.hasNotes) return listOf(Missing.NoNotes)
        val out = mutableListOf<Missing>()
        val low = instrument.concertToWrittenMidi(piece.lowMidi!!)
        val high = instrument.concertToWrittenMidi(piece.highMidi!!)
        if (low < player.writtenLowMidi) out += Missing.RangeLow(player.writtenLowMidi - low, low)
        if (high > player.writtenHighMidi) out += Missing.RangeHigh(high - player.writtenHighMidi, high)
        val clean = player.scales.filter { it.bestCleanBpm > 0 }.map { it.scaleKey }.toSet()
        val key = piece.scaleKey
        val relative = piece.relativeScaleKey
        if (key != null && key !in clean && relative !in clean) {
            out += Missing.Scale(key, nextUnlock != null && (nextUnlock == key || nextUnlock == relative))
        }
        return out
    }

    /**
     * Today's pieces: "something new" continues the piece being learned (most recently touched); if
     * none, the easiest Available piece not yet started, then the easiest Stretch. "Something fun" is
     * the Playable or Clean piece played least recently. The section is the first one not yet playable.
     */
    fun choose(matches: List<PieceMatch>, progress: List<PieceProgressRecord>): PieceChoice {
        val started = progress.map { it.pieceId }.toSet()
        val learning = matches.filter { it.state == PieceState.LEARNING }
            .maxByOrNull { m -> progress.filter { it.pieceId == m.piece.id }.maxOfOrNull { it.updatedAtMs } ?: 0 }
        val fresh = matches.filter { it.piece.id !in started }
        val newPiece = learning
            ?: fresh.filter { it.state == PieceState.AVAILABLE }.minWithOrNull(compareBy({ it.piece.difficulty }, { it.piece.id }))
            ?: fresh.filter { it.state == PieceState.STRETCH }.minWithOrNull(compareBy({ it.piece.difficulty }, { it.piece.id }))
        val section = newPiece?.piece?.file?.sections?.firstOrNull { s ->
            val st = progress.firstOrNull { it.pieceId == newPiece.piece.id && it.sectionId == s.id }?.stage
            st == null || st < PieceStage.PLAYABLE
        }
        val fun_ = matches.filter { it.state == PieceState.PLAYABLE || it.state == PieceState.CLEAN_AT_TEMPO }
            .minWithOrNull(compareBy({ m -> progress.filter { it.pieceId == m.piece.id }.maxOfOrNull { it.lastPlayedAtMs } ?: 0L }, { it.piece.id }))
        return PieceChoice(newPiece?.piece?.id, section?.id, fun_?.piece?.id)
    }
}
