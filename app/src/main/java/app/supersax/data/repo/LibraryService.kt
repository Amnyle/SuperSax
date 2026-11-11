package app.supersax.data.repo

import app.supersax.data.assets.ContentRepository
import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.PieceStage
import app.supersax.domain.generator.Adaptation
import app.supersax.domain.library.LibraryMatcher
import app.supersax.domain.library.Piece
import app.supersax.domain.library.PieceFile
import app.supersax.domain.library.PieceMatch
import app.supersax.domain.planner.PieceChoice
import app.supersax.domain.program.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/** Bundled pieces plus the owner's MusicXML imports, matched to the player. */
class LibraryService(
    private val content: ContentRepository,
    private val library: LibraryRepository,
    private val practice: PracticeRepository,
    scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val bundled: List<Piece> get() = (content as app.supersax.AppContent).bundledPieces

    /** All pieces: bundled ones first, then imports (MusicXML parsed into the same format). */
    val pieces: StateFlow<List<Piece>> = library.userPieces.map { users ->
        bundled + users.mapNotNull { u -> u.pieceJson?.let { runCatching { Piece.from(json.decodeFromString(PieceFile.serializer(), it)) }.getOrNull() } }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun matches(): Flow<List<PieceMatch>> = combine(pieces, practice.player, library.progress) { ps, player, progress ->
        match(ps, player, progress)
    }

    fun match(ps: List<Piece>, player: PlayerState, progress: List<PieceProgressRecord>): List<PieceMatch> {
        val next = Adaptation.nextUnlock(player, content.generatorConfig.adaptation)
            ?: content.generatorConfig.adaptation.unlockOrder.firstOrNull { k -> player.scales.none { it.scaleKey == k } }
        return ps.map { LibraryMatcher.match(it, player, progress, next) }
    }

    suspend fun choose(player: PlayerState): PieceChoice {
        val progress = library.progressNow()
        val all = pieces.value.ifEmpty { pieces.first { it.isNotEmpty() } }
        return LibraryMatcher.choose(match(all, player, progress).filter { it.piece.hasNotes }, progress)
    }

    fun piece(id: String?): Piece? = id?.let { i -> pieces.value.firstOrNull { it.id == i } }

    /** Records a stage for a piece or a section, keeping "last played" up to date. */
    suspend fun setStage(pieceId: String, sectionId: String, stage: PieceStage, nowMs: Long, evidence: String? = null) {
        val existing = library.progressNow().firstOrNull { it.pieceId == pieceId && it.sectionId == sectionId }
        library.saveProgress(PieceProgressRecord(pieceId, sectionId, stage, nowMs, maxOf(nowMs, existing?.lastPlayedAtMs ?: 0),
            evidence ?: existing?.evidenceRecordingId))
        // A piece being worked on is at least "learning".
        if (sectionId.isNotEmpty()) {
            val whole = library.progressNow().firstOrNull { it.pieceId == pieceId && it.sectionId.isEmpty() }
            if (whole == null) library.saveProgress(PieceProgressRecord(pieceId, "", PieceStage.LEARNING, nowMs, nowMs))
        }
    }

    suspend fun markPlayed(pieceId: String, nowMs: Long) {
        val existing = library.progressNow().firstOrNull { it.pieceId == pieceId && it.sectionId.isEmpty() } ?: return
        library.saveProgress(existing.copy(lastPlayedAtMs = nowMs))
    }
}
