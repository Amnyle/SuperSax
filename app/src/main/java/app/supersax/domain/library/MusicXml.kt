package app.supersax.domain.library

import app.supersax.domain.common.LocalizedText
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.Interval
import app.supersax.domain.pitch.Letter
import app.supersax.domain.pitch.SpelledPitch
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Imports the first part of a (partwise) MusicXML file as a single-voice score: only the first note
 * of a chord is kept, grace notes and other voices are skipped, rests and ties are kept. If the part declares a
 * `<transpose>` (an alto part is written a major sixth up), pitches and key are converted to concert.
 */
object MusicXmlImport {

    class ImportException(message: String) : Exception(message)

    fun parse(input: InputStream, id: String, fallbackTitle: String): PieceFile {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // No external entities or DTD fetching (no network, no XXE).
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(input)
        val root = doc.documentElement
        if (root.tagName != "score-partwise") throw ImportException("Only partwise MusicXML is supported")
        val title = root.child("work")?.child("work-title")?.text() ?: root.child("movement-title")?.text() ?: fallbackTitle
        val composer = root.child("identification")?.children("creator")?.firstOrNull { it.getAttribute("type") == "composer" }?.text() ?: ""
        val part = root.children("part").firstOrNull() ?: throw ImportException("No part")

        var divisions = 1
        var fifths = 0
        var mode = "major"
        var beats = 4
        var beatType = 4
        var tempo: Int? = null
        var toConcert: Interval? = null
        val measures = mutableListOf<String>()

        part.children("measure").forEach { m ->
            val tokens = mutableListOf<String>()
            m.childElements().forEach { el ->
                when (el.tagName) {
                    "attributes" -> {
                        el.child("divisions")?.text()?.toIntOrNull()?.let { divisions = it }
                        el.child("key")?.let { k ->
                            fifths = k.child("fifths")?.text()?.toIntOrNull() ?: fifths
                            mode = k.child("mode")?.text() ?: mode
                        }
                        el.child("time")?.let { t ->
                            beats = t.child("beats")?.text()?.toIntOrNull() ?: beats
                            beatType = t.child("beat-type")?.text()?.toIntOrNull() ?: beatType
                        }
                        el.child("transpose")?.let { tr ->
                            val chromatic = tr.child("chromatic")?.text()?.toIntOrNull() ?: 0
                            val diatonic = tr.child("diatonic")?.text()?.toIntOrNull() ?: 0
                            val octave = tr.child("octave-change")?.text()?.toIntOrNull() ?: 0
                            toConcert = Interval(diatonic + 7 * octave, chromatic + 12 * octave)
                        }
                    }
                    "direction" -> {
                        el.getElementsByTagName("sound").let { if (it.length > 0) (it.item(0) as Element).getAttribute("tempo").toDoubleOrNull()?.let { t -> tempo = t.toInt() } }
                        if (tempo == null) el.getElementsByTagName("per-minute").let { if (it.length > 0) it.item(0).textContent.trim().toDoubleOrNull()?.let { t -> tempo = t.toInt() } }
                    }
                    "sound" -> el.getAttribute("tempo").toDoubleOrNull()?.let { tempo = it.toInt() }
                    "note" -> {
                        if (el.child("grace") != null || el.child("chord") != null) return@forEach
                        val dur = el.child("duration")?.text()?.toIntOrNull() ?: return@forEach
                        val frac = Fraction(dur, divisions * 4).reduced()
                        val tieStart = el.children("tie").any { it.getAttribute("type") == "start" }
                        val token = if (el.child("rest") != null) "r" else {
                            val p = el.child("pitch") ?: return@forEach
                            val step = Letter.valueOf(p.child("step")!!.text())
                            val alter = p.child("alter")?.text()?.toDoubleOrNull()?.toInt() ?: 0
                            val octave = p.child("octave")!!.text().toInt()
                            var sp = SpelledPitch(step, alter, octave)
                            toConcert?.let { sp = sp.transpose(it) }
                            sp.toString()
                        }
                        tokens += "$token:$frac" + if (tieStart && token != "r") "~" else ""
                    }
                    "backup", "forward" -> Unit // single voice: other voices are ignored
                }
            }
            if (tokens.isNotEmpty()) measures += tokens.joinToString(" ")
        }
        if (measures.isEmpty()) throw ImportException("No notes")
        val concertFifths = toConcert?.let { fifths + Instrument.fifthsOfInterval(it) } ?: fifths
        val firstLen = Score.parseMeasure(measures.first()).length
        val pickup = firstLen < Fraction(beats, beatType)
        return PieceFile(
            id = id, title = LocalizedText(title, title), composer = composer.ifBlank { "?" },
            source = "MusicXML import", licence = "user file", credit = "Imported by the owner",
            tags = listOf("import"), reviewed = true, bundled = true,
            key = normaliseFifths(concertFifths), mode = if (mode == "minor") "minor" else "major", time = "$beats/$beatType",
            tempo = tempo ?: 80, pickup = pickup, measures = measures,
            sections = listOf(PieceSection("all", LocalizedText("Whole piece", "Morceau entier"), 1, measures.size)),
        )
    }

    private fun normaliseFifths(f: Int) = when { f > 7 -> f - 12; f < -7 -> f + 12; else -> f }

    private fun Element.childElements(): List<Element> {
        val out = mutableListOf<Element>()
        val nl = childNodes
        for (i in 0 until nl.length) (nl.item(i) as? Element)?.let { out += it }
        return out
    }
    private fun Element.children(tag: String) = childElements().filter { it.tagName == tag }
    private fun Element.child(tag: String) = childElements().firstOrNull { it.tagName == tag }
    private fun Element.text() = textContent.trim()
}
