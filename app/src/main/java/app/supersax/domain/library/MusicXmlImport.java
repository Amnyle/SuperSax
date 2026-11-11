package app.supersax.domain.library;

import app.supersax.domain.common.LocalizedText;
import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.Interval;
import app.supersax.domain.pitch.Letter;
import app.supersax.domain.pitch.SpelledPitch;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Imports the first part of a (partwise) MusicXML file as a single-voice score: only the first note of a
 * chord is kept, grace notes and other voices are skipped, rests and ties are kept. If the part declares
 * a {@code <transpose>} (an alto part is written a major sixth up), pitches and key are converted to concert.
 */
public final class MusicXmlImport {
    private MusicXmlImport() {}

    public static final class ImportException extends Exception {
        public ImportException(String message) { super(message); }
    }

    public static PieceFile parse(InputStream input, String id, String fallbackTitle) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // No external entities or DTD fetching (no network, no XXE).
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(input);
        Element root = doc.getDocumentElement();
        if (!root.getTagName().equals("score-partwise")) throw new ImportException("Only partwise MusicXML is supported");
        String title = text(child(child(root, "work"), "work-title"));
        if (title == null) title = text(child(root, "movement-title"));
        if (title == null) title = fallbackTitle;
        String composer = "";
        Element ident = child(root, "identification");
        if (ident != null) for (Element cr : children(ident, "creator")) if ("composer".equals(cr.getAttribute("type"))) { composer = text(cr); break; }
        List<Element> parts = children(root, "part");
        if (parts.isEmpty()) throw new ImportException("No part");

        int divisions = 1, fifths = 0, beats = 4, beatType = 4;
        String mode = "major";
        Integer tempo = null;
        Interval toConcert = null;
        List<String> measures = new ArrayList<>();

        for (Element m : children(parts.get(0), "measure")) {
            List<String> tokens = new ArrayList<>();
            for (Element el : childElements(m)) {
                switch (el.getTagName()) {
                    case "attributes": {
                        Integer d = intOf(child(el, "divisions"));
                        if (d != null) divisions = d;
                        Element k = child(el, "key");
                        if (k != null) {
                            Integer f = intOf(child(k, "fifths"));
                            if (f != null) fifths = f;
                            String md = text(child(k, "mode"));
                            if (md != null) mode = md;
                        }
                        Element t = child(el, "time");
                        if (t != null) {
                            Integer b = intOf(child(t, "beats")), bt = intOf(child(t, "beat-type"));
                            if (b != null) beats = b;
                            if (bt != null) beatType = bt;
                        }
                        Element tr = child(el, "transpose");
                        if (tr != null) {
                            int chromatic = orZero(intOf(child(tr, "chromatic")));
                            int diatonic = orZero(intOf(child(tr, "diatonic")));
                            int octave = orZero(intOf(child(tr, "octave-change")));
                            toConcert = new Interval(diatonic + 7 * octave, chromatic + 12 * octave);
                        }
                        break;
                    }
                    case "direction": {
                        NodeList sounds = el.getElementsByTagName("sound");
                        if (sounds.getLength() > 0) {
                            Double t = doubleOf(((Element) sounds.item(0)).getAttribute("tempo"));
                            if (t != null) tempo = (int) (double) t;
                        }
                        if (tempo == null) {
                            NodeList pm = el.getElementsByTagName("per-minute");
                            if (pm.getLength() > 0) {
                                Double t = doubleOf(pm.item(0).getTextContent().trim());
                                if (t != null) tempo = (int) (double) t;
                            }
                        }
                        break;
                    }
                    case "sound": {
                        Double t = doubleOf(el.getAttribute("tempo"));
                        if (t != null) tempo = (int) (double) t;
                        break;
                    }
                    case "note": {
                        if (child(el, "grace") != null || child(el, "chord") != null) break;
                        Integer dur = intOf(child(el, "duration"));
                        if (dur == null) break;
                        Fraction frac = new Fraction(dur, divisions * 4);
                        boolean tieStart = false;
                        for (Element tie : children(el, "tie")) if ("start".equals(tie.getAttribute("type"))) tieStart = true;
                        String token;
                        if (child(el, "rest") != null) token = "r";
                        else {
                            Element p = child(el, "pitch");
                            if (p == null) break;
                            Letter step = Letter.valueOf(text(child(p, "step")));
                            Double alterD = doubleOf(text(child(p, "alter")));
                            int alter = alterD != null ? (int) (double) alterD : 0;
                            int octave = Integer.parseInt(text(child(p, "octave")));
                            SpelledPitch sp = new SpelledPitch(step, alter, octave);
                            if (toConcert != null) sp = sp.transpose(toConcert);
                            token = sp.toString();
                        }
                        tokens.add(token + ":" + frac + (tieStart && !token.equals("r") ? "~" : ""));
                        break;
                    }
                    default: // backup/forward: other voices are ignored (single voice)
                }
            }
            if (!tokens.isEmpty()) measures.add(String.join(" ", tokens));
        }
        if (measures.isEmpty()) throw new ImportException("No notes");
        int concertFifths = toConcert != null ? fifths + Instrument.fifthsOfInterval(toConcert) : fifths;
        boolean pickup = Score.parseMeasure(measures.get(0)).getLength().compareTo(new Fraction(beats, beatType)) < 0;
        List<PieceSection> sections = Collections.singletonList(
            new PieceSection("all", new LocalizedText("Whole piece", "Morceau entier"), 1, measures.size()));
        return new PieceFile(id, new LocalizedText(title, title), composer.isEmpty() ? "?" : composer, "MusicXML import", "user file",
            "Imported by the owner", Collections.singletonList("import"), true, true, normaliseFifths(concertFifths),
            "minor".equals(mode) ? "minor" : "major", beats + "/" + beatType, tempo != null ? tempo : 80, pickup, measures, sections);
    }

    private static int normaliseFifths(int f) { return f > 7 ? f - 12 : f < -7 ? f + 12 : f; }

    private static int orZero(Integer v) { return v == null ? 0 : v; }

    private static List<Element> childElements(Element e) {
        List<Element> out = new ArrayList<>();
        NodeList nl = e.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) { Node n = nl.item(i); if (n instanceof Element) out.add((Element) n); }
        return out;
    }

    private static List<Element> children(Element e, String tag) {
        List<Element> out = new ArrayList<>();
        for (Element c : childElements(e)) if (c.getTagName().equals(tag)) out.add(c);
        return out;
    }

    private static Element child(Element e, String tag) {
        if (e == null) return null;
        for (Element c : childElements(e)) if (c.getTagName().equals(tag)) return c;
        return null;
    }

    private static String text(Element e) { return e == null ? null : e.getTextContent().trim(); }

    private static Integer intOf(Element e) {
        String t = text(e);
        if (t == null) return null;
        try { return Integer.parseInt(t); } catch (NumberFormatException ex) { return null; }
    }

    private static Double doubleOf(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
    }
}
