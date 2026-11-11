package app.supersax.domain.library

import app.supersax.domain.common.LocalizedText
import kotlinx.serialization.Serializable

/** A section of a piece (bar range, 1-based inclusive), used by song-milestone goals. */
@Serializable
data class PieceSection(val id: String, val title: LocalizedText, val from: Int, val to: Int)

/** A piece file as stored in `assets/pieces/` (see docs/PIECE_FORMAT.md). Pitches are concert. */
@Serializable
data class PieceFile @JvmOverloads constructor(
    val id: String,
    val title: LocalizedText,
    val composer: String,
    val source: String,
    val licence: String,
    val credit: String,
    val tags: List<String> = emptyList(),
    val reviewed: Boolean = false,
    val bundled: Boolean = true,
    val key: Int? = null,
    val mode: String = "major",
    val time: String? = null,
    val tempo: Int? = null,
    val pickup: Boolean = false,
    val measures: List<String> = emptyList(),
    val sections: List<PieceSection> = emptyList(),
)
