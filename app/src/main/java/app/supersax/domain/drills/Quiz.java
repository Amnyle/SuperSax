package app.supersax.domain.drills;

import app.supersax.domain.pitch.NoteNaming;
import app.supersax.domain.pitch.PitchClass;
import app.supersax.domain.pitch.SpelledPitch;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

/** Pure answer checking for lesson quizzes (free-form answers, never multiple choice). */
public final class Quiz {
    private Quiz() {}

    /** Lower case, accents removed, punctuation dropped, spaces collapsed. */
    public static String normalise(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT).replace("♯", "#").replace("♭", "b")
            .replaceAll("[^a-z0-9#]+", " ").trim().replaceAll("\\s+", " ");
    }

    public static boolean check(QuizQuestion q, Answer a, String language) {
        switch (q.getType()) {
            case TEXT: {
                if (!(a instanceof Answer.Text)) return false;
                List<String> accepted = new ArrayList<>();
                if (q.getAccept() != null) {
                    for (String x : q.getAccept().getEn()) accepted.add(normalise(x));
                    for (String x : q.getAccept().getFr()) accepted.add(normalise(x));
                }
                return accepted.contains(normalise(((Answer.Text) a).getValue()));
            }
            case NUMBER: {
                Integer expected = intAnswer(q);
                return a instanceof Answer.Number && expected != null && ((Answer.Number) a).getValue() == expected;
            }
            case NOTE: return a instanceof Answer.Note && ((Answer.Note) a).getValue().equals(noteAnswer(q));
            case NOTES: {
                if (!(a instanceof Answer.Notes)) return false;
                List<PitchClass> given = ((Answer.Notes) a).getValues();
                List<PitchClass> expected = notesAnswer(q);
                if (q.getOrdered()) return given.equals(expected);
                return new HashSet<>(given).equals(new HashSet<>(expected)) && given.size() == expected.size();
            }
            default: return a instanceof Answer.StaffNote && ((Answer.StaffNote) a).getValue().equals(staffAnswer(q));
        }
    }

    private static @Nullable String content(@Nullable JsonElement e) { return e instanceof JsonPrimitive ? ((JsonPrimitive) e).getContent() : null; }

    public static @Nullable Integer intAnswer(QuizQuestion q) {
        String c = content(q.getAnswer());
        if (c == null) return null;
        try { return Integer.parseInt(c.trim()); } catch (NumberFormatException e) { return null; }
    }

    public static @Nullable PitchClass noteAnswer(QuizQuestion q) {
        String c = content(q.getAnswer());
        return c == null ? null : PitchClass.parse(c);
    }

    public static List<PitchClass> notesAnswer(QuizQuestion q) {
        List<PitchClass> out = new ArrayList<>();
        if (q.getAnswer() instanceof JsonArray) for (JsonElement e : (JsonArray) q.getAnswer()) out.add(PitchClass.parse(content(e)));
        return out;
    }

    public static @Nullable SpelledPitch staffAnswer(QuizQuestion q) {
        String c = content(q.getAnswer());
        return c == null ? null : SpelledPitch.parse(c);
    }

    /** The expected answer as text for the "it was …" feedback. */
    public static String expectedText(QuizQuestion q, String language, NoteNaming naming) {
        switch (q.getType()) {
            case TEXT: {
                if (q.getAccept() == null) return "";
                List<String> l = language.startsWith("fr") ? q.getAccept().getFr() : q.getAccept().getEn();
                return l.isEmpty() ? "" : l.get(0);
            }
            case NUMBER: { Integer v = intAnswer(q); return v == null ? "" : v.toString(); }
            case NOTE: { PitchClass p = noteAnswer(q); return p == null ? "" : naming.format(p); }
            case NOTES: {
                StringBuilder b = new StringBuilder();
                for (PitchClass p : notesAnswer(q)) { if (b.length() > 0) b.append(' '); b.append(naming.format(p)); }
                return b.toString();
            }
            default: { SpelledPitch p = staffAnswer(q); return p == null ? "" : naming.format(p); }
        }
    }
}
