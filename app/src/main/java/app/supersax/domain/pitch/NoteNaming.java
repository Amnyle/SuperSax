package app.supersax.domain.pitch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Note-name system, independent of the UI language. */
public enum NoteNaming {
    LETTERS, SOLFEGE;

    public static final List<String> SOLFEGE_NAMES = Collections.unmodifiableList(Arrays.asList("Do", "Ré", "Mi", "Fa", "Sol", "La", "Si"));

    public String letterName(Letter letter) {
        return this == LETTERS ? letter.name() : SOLFEGE_NAMES.get(letter.getIndex());
    }

    /** "E♭", "Mi♭". */
    public String format(PitchClass pc) { return letterName(pc.getLetter()) + accidentalSymbol(pc.getAlter()); }

    /** "E♭4", or without octave. */
    public String format(SpelledPitch p, boolean withOctave) {
        return format(p.getPitchClass()) + (withOctave ? String.valueOf(p.getOctave()) : "");
    }

    public String format(SpelledPitch p) { return format(p, true); }

    public static String accidentalSymbol(int alter) {
        switch (alter) {
            case 0: return "";
            case 1: return "♯";
            case 2: return "𝄪";
            case -1: return "♭";
            case -2: return "𝄫";
            default: return (alter > 0 ? "♯" : "♭").repeat(Math.abs(alter));
        }
    }

    /**
     * Parses a typed answer in either naming system (letters or solfège, with b/#/♭/♯), ignoring case and
     * accents. Returns null if it is not a note name.
     */
    public static PitchClass parseAny(String input) {
        String s = input.trim().toLowerCase().replace("é", "e").replace("♭", "b").replace("♯", "#").replace(" ", "");
        if (s.isEmpty()) return null;
        String[][] solfege = {{"do", "C"}, {"re", "D"}, {"mi", "E"}, {"fa", "F"}, {"sol", "G"}, {"la", "A"}, {"si", "B"}, {"ut", "C"}};
        Letter letter = null;
        String rest = null;
        for (String[] e : solfege) {
            if (s.startsWith(e[0])) { letter = Letter.valueOf(e[1]); rest = s.substring(e[0].length()); break; }
        }
        if (letter == null) {
            char c = Character.toUpperCase(s.charAt(0));
            if (c < 'A' || c > 'G') return null;
            letter = Letter.valueOf(String.valueOf(c));
            rest = s.substring(1);
        }
        rest = rest.replace("dièse", "#").replace("diese", "#").replace("bémol", "b").replace("bemol", "b");
        int acc;
        switch (rest) {
            case "": acc = 0; break;
            case "#": acc = 1; break;
            case "##": case "x": acc = 2; break;
            case "b": acc = -1; break;
            case "bb": acc = -2; break;
            default: return null;
        }
        return new PitchClass(letter, acc);
    }
}
