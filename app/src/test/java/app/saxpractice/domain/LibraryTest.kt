package app.saxpractice.domain

import app.saxpractice.domain.common.LocalizedText
import app.saxpractice.domain.common.PieceProgressRecord
import app.saxpractice.domain.common.PieceStage
import app.saxpractice.domain.library.Difficulty
import app.saxpractice.domain.library.LibraryMatcher
import app.saxpractice.domain.library.Missing
import app.saxpractice.domain.library.MusicXmlImport
import app.saxpractice.domain.library.Piece
import app.saxpractice.domain.library.PieceFile
import app.saxpractice.domain.library.PieceSection
import app.saxpractice.domain.library.PieceState
import app.saxpractice.domain.library.Score
import app.saxpractice.domain.program.PlayerState
import app.saxpractice.domain.program.ScaleProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {
    private fun piece(id: String, key: Int, time: String, tempo: Int, measures: List<String>, mode: String = "major", sections: List<PieceSection> = emptyList()) =
        Piece.from(PieceFile(id, LocalizedText(id, id), "x", "x", "CC0", "x", key = key, mode = mode, time = time, tempo = tempo, measures = measures, sections = sections))

    // Concert Eb major = written C major, half and quarter notes, slow.
    private val easy = piece("easy", -3, "4/4", 66, List(8) { "Eb4:1/2 F4:1/4 G4:1/4" })
    // Concert Bb major = written G major, eighths, moderate.
    private val medium = piece("medium", -2, "4/4", 92, List(16) { "Bb3:1/8 C4:1/8 D4:1/8 Eb4:1/8 F4:3/8 G4:1/8" })
    // Concert E major = written Db major, sixteenths, wide range, syncopation, fast.
    private val hard = piece("hard", 4, "4/4", 132, List(32) { "E3:1/16 G#3:1/16 B3:1/16 E4:1/16 G#4:1/8 B4:1/4~ B4:1/16 D#5:1/16 F#5:1/16 A5:1/16 Bn4:1/8" })

    @Test fun difficultyOrdering() {
        assertTrue(easy.difficulty < medium.difficulty, "${easy.difficulty} < ${medium.difficulty}")
        assertTrue(medium.difficulty < hard.difficulty, "${medium.difficulty} < ${hard.difficulty}")
        assertTrue(easy.difficulty in 1..2, "easy ${easy.difficulty} ${Difficulty.features(easy.score!!)}")
        assertTrue(hard.difficulty >= 8, "hard ${hard.difficulty} ${Difficulty.features(hard.score!!)}")
        assertEquals(29, easy.durationSec) // 32 beats at 66
        assertEquals("MAJOR:Eb", easy.scaleKey); assertEquals("NATURAL_MINOR:C", easy.relativeScaleKey)
    }

    private val player = PlayerState(
        scales = listOf(ScaleProgress("MAJOR:Eb", 70, bestCleanBpm = 70), ScaleProgress("MAJOR:Bb", 60, bestCleanBpm = 0)),
        writtenLowMidi = 62, writtenHighMidi = 84,
    )

    @Test fun matchingStates() {
        assertEquals(PieceState.AVAILABLE, LibraryMatcher.match(easy, player, emptyList(), null).state)
        // Written range of medium is G4..E5 (fits); the B♭ scale is not clean: stretch only if it is the next to unlock.
        assertEquals(PieceState.STRETCH, LibraryMatcher.match(medium, player, emptyList(), "MAJOR:Bb").state)
        val m = LibraryMatcher.match(medium, player.copy(writtenLowMidi = 70), emptyList(), "MAJOR:Bb")
        assertEquals(PieceState.LOCKED, m.state) // range (G4 under B♭4) and scale both missing
        assertTrue(m.missing.any { it is Missing.RangeLow } && m.missing.any { it is Missing.Scale })
        val bbInRange = piece("bb", -2, "4/4", 80, List(4) { "F4:1/4 G4:1/4 A4:1/4 Bb4:1/4" })
        assertEquals(PieceState.STRETCH, LibraryMatcher.match(bbInRange, player, emptyList(), "MAJOR:Bb").state)
        assertEquals(PieceState.LOCKED, LibraryMatcher.match(bbInRange, player, emptyList(), "MAJOR:Ab").state)
        // Range exceeded by 2 semitones (written E6 for a player up to C6... 4) and by 1.
        val high2 = piece("h2", -3, "4/4", 80, listOf("Eb4:1/2 Bb4:1/4 Bb4:1/4", "Eb5:1/1")) // written C5..C6? concert Eb5 = written C6
        assertEquals(PieceState.AVAILABLE, LibraryMatcher.match(high2, player, emptyList(), null).state)
        val high1 = piece("h1", -3, "4/4", 80, listOf("Eb4:1/2 Bb4:1/4 Bb4:1/4", "F5:1/1")) // written D6: 2 over C6
        val mh = LibraryMatcher.match(high1, player, emptyList(), null)
        assertEquals(PieceState.STRETCH, mh.state); assertEquals(Missing.RangeHigh(2, 86), mh.missing.single())
        // Recorded stage wins.
        val prog = listOf(PieceProgressRecord("hard", "", PieceStage.LEARNING, 5))
        assertEquals(PieceState.LEARNING, LibraryMatcher.match(hard, player, prog, null).state)
        // Relative minor clean counts: a C minor piece (same signature as Eb) is available.
        val cMinor = piece("cm", -3, "3/4", 80, List(4) { "C4:1/4 Eb4:1/4 G4:1/4" }, mode = "minor")
        assertEquals(PieceState.AVAILABLE, LibraryMatcher.match(cMinor, player, emptyList(), null).state)
    }

    @Test fun choosingNewAndFun() {
        val s = listOf(PieceSection("a", LocalizedText("A", "A"), 1, 4), PieceSection("b", LocalizedText("B", "B"), 5, 8))
        val a = piece("a", -3, "4/4", 66, List(8) { "Eb4:1/1" }, sections = s)
        val b = piece("b", -3, "4/4", 66, List(8) { "Eb4:1/2 F4:1/2" })
        val c = piece("c", -3, "4/4", 66, List(8) { "Eb4:1/4 F4:1/4 G4:1/4 F4:1/4" })
        val progress = listOf(
            PieceProgressRecord("b", "", PieceStage.PLAYABLE, 10, lastPlayedAtMs = 500),
            PieceProgressRecord("c", "", PieceStage.CLEAN_AT_TEMPO, 10, lastPlayedAtMs = 100),
        )
        val matches = listOf(a, b, c).map { LibraryMatcher.match(it, player, progress, null) }
        val choice = LibraryMatcher.choose(matches, progress)
        assertEquals("a", choice.newPieceId); assertEquals("a", choice.newSectionId)
        assertEquals("c", choice.funPieceId) // played least recently
        // Learning continues before anything new; section a is playable so b comes next.
        val p2 = progress + PieceProgressRecord("a", "", PieceStage.LEARNING, 20) + PieceProgressRecord("a", "a", PieceStage.PLAYABLE, 20)
        val c2 = LibraryMatcher.choose(listOf(a, b, c).map { LibraryMatcher.match(it, player, p2, null) }, p2)
        assertEquals("a", c2.newPieceId); assertEquals("b", c2.newSectionId)
        assertNull(LibraryMatcher.choose(emptyList(), emptyList()).newPieceId)
    }

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <work><work-title>Little Test</work-title></work>
  <identification><creator type="composer">Someone</creator></identification>
  <part-list><score-part id="P1"><part-name>Alto Saxophone</part-name></score-part></part-list>
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>2</divisions>
        <key><fifths>0</fifths><mode>major</mode></key>
        <time><beats>3</beats><beat-type>4</beat-type></time>
        <transpose><diatonic>-5</diatonic><chromatic>-9</chromatic></transpose>
      </attributes>
      <direction><sound tempo="96"/></direction>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>2</duration><type>quarter</type></note>
      <note><pitch><step>D</step><octave>5</octave></pitch><duration>1</duration><type>eighth</type></note>
      <note><pitch><step>E</step><octave>5</octave></pitch><duration>1</duration><type>eighth</type></note>
      <note><chord/><pitch><step>G</step><octave>5</octave></pitch><duration>1</duration></note>
      <note><pitch><step>F</step><alter>1</alter><octave>5</octave></pitch><duration>2</duration><tie type="start"/></note>
    </measure>
    <measure number="2">
      <note><pitch><step>F</step><alter>1</alter><octave>5</octave></pitch><duration>2</duration><tie type="stop"/></note>
      <note><grace/><pitch><step>A</step><octave>5</octave></pitch></note>
      <note><rest/><duration>4</duration></note>
    </measure>
  </part>
</score-partwise>"""

    @Test fun musicXmlImport() {
        val f = MusicXmlImport.parse(xml.byteInputStream(), "imp", "fallback")
        assertEquals("Little Test", f.title.en); assertEquals("Someone", f.composer)
        assertEquals(-3, f.key) // written C major for alto = concert Eb major
        assertEquals("3/4", f.time); assertEquals(96, f.tempo)
        assertEquals(listOf("Eb4:1/4 F4:1/8 G4:1/8 A4:1/4~", "A4:1/4 r:1/2"), f.measures)
        val p = Piece.from(f)
        assertEquals("C5 D5 E5 F#5 F#5", p.score!!.toWritten().notes.mapNotNull { it.pitch }.joinToString(" "))
        assertEquals(false, f.pickup)
        // Round trip of the compact format.
        f.measures.forEach { assertEquals(it, Score.formatMeasure(Score.parseMeasure(it))) }
    }
}
