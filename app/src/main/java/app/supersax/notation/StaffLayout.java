package app.supersax.notation;

import app.supersax.domain.library.Fraction;
import app.supersax.domain.library.Measure;
import app.supersax.domain.library.Score;
import app.supersax.domain.library.ScoreNote;
import app.supersax.domain.pitch.KeySignature;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Pure layout of a single-voice score into systems for a given width (treble clef, key signature, time
 * signature on the first system, accidentals with measure memory, ledger lines, stems and flags, dots,
 * rests, ties and bar lines). All units are staff spaces.
 */
public final class StaffLayout {
    public static final float HEAD_WIDTH = 1.18f;
    public static final float STEM_LENGTH = 3.5f;
    private static final float CLEF_ADVANCE = 3.6f;
    private static final float ACC_ADVANCE = 1.05f;
    private static final float TIME_ADVANCE = 2.6f;
    private static final List<Fraction> REPRESENTABLE = Collections.unmodifiableList(Arrays.asList(
        new Fraction(1, 1), new Fraction(3, 4), new Fraction(1, 2), new Fraction(3, 8), new Fraction(1, 4),
        new Fraction(3, 16), new Fraction(1, 8), new Fraction(1, 16), new Fraction(1, 32)));

    private StaffLayout() {}

    /** Splits a duration into drawable values, tied together (5/8 = 1/2 + 1/8). */
    public static List<Fraction> decompose(Fraction d) {
        List<Fraction> out = new ArrayList<>();
        Fraction left = d;
        while (left.compareTo(Fraction.ZERO) > 0) {
            Fraction v = null;
            for (Fraction r : REPRESENTABLE) if (r.compareTo(left) <= 0) { v = r; break; }
            if (v == null) break;
            out.add(v);
            left = left.minus(v);
        }
        return out;
    }

    /** A drawable note with the index of its source note in the score. */
    private static final class Placed {
        final ScoreNote note;
        final int source;
        Placed(ScoreNote note, int source) { this.note = note; this.source = source; }
    }

    /** One measure ready to draw: notes, accidental to show per note (or null), natural width. */
    private static final class Prepared {
        final List<Placed> notes;
        final List<Integer> acc;
        final float width;
        Prepared(List<Placed> notes, List<Integer> acc, float width) { this.notes = notes; this.acc = acc; this.width = width; }
    }

    /** Expands notes with non-drawable durations into tied drawable notes, keeping the source index. */
    private static List<Placed> expand(Measure m, int startIndex) {
        List<Placed> out = new ArrayList<>();
        for (int i = 0; i < m.getNotes().size(); i++) {
            ScoreNote n = m.getNotes().get(i);
            List<Fraction> parts = decompose(n.getDuration());
            for (int k = 0; k < parts.size(); k++) {
                boolean tie = k < parts.size() - 1 ? n.getPitch() != null : n.getTie();
                out.add(new Placed(n.withDurationAndTie(parts.get(k), tie), startIndex + i));
            }
        }
        return out;
    }

    private static float noteWidth(ScoreNote n, boolean accidental) {
        return 1.5f + 4.0f * (float) Math.sqrt(n.getDuration().getValue()) + (accidental ? ACC_ADVANCE + 0.2f : 0f);
    }

    private static float headerWidth(int keyFifths, boolean withTime) {
        return 0.6f + CLEF_ADVANCE + Math.abs(keyFifths) * ACC_ADVANCE + 0.6f + (withTime ? TIME_ADVANCE : 0f);
    }

    private static String digits(int v) {
        StringBuilder b = new StringBuilder();
        for (char ch : String.valueOf(v).toCharArray()) b.append(Smufl.timeDigit(ch));
        return b.toString();
    }

    /** Lays out the score (draw it as given: pass a written score for the alto view). */
    public static List<StaffSystem> layout(Score score, float fullWidth, boolean showTime) {
        // Keep the final bar line clear of the right edge.
        float width = fullWidth - 0.6f;
        KeySignature key = new KeySignature(score.getKeyFifths());
        List<Prepared> measures = new ArrayList<>();
        int idx = 0;
        for (Measure m : score.getMeasures()) {
            List<Placed> notes = expand(m, idx);
            idx += m.getNotes().size();
            Map<String, Integer> state = new HashMap<>();
            List<Integer> acc = new ArrayList<>();
            float w = 1.0f;
            for (Placed pl : notes) {
                SpelledPitch p = pl.note.getPitch();
                Integer show = null;
                if (p != null) {
                    String k = p.getLetter().name() + p.getOctave();
                    int cur = state.containsKey(k) ? state.get(k) : key.alterFor(p.getLetter());
                    if (p.getAlter() != cur) { state.put(k, p.getAlter()); show = p.getAlter(); }
                }
                acc.add(show);
                w += noteWidth(pl.note, show != null);
            }
            measures.add(new Prepared(notes, acc, w));
        }
        // Greedy line breaking.
        List<List<Integer>> systems = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        float used = headerWidth(score.getKeyFifths(), showTime);
        for (int i = 0; i < measures.size(); i++) {
            float w = measures.get(i).width;
            if (!current.isEmpty() && used + w > width) {
                systems.add(current); current = new ArrayList<>(); used = headerWidth(score.getKeyFifths(), false);
            }
            current.add(i); used += w;
        }
        if (!current.isEmpty()) systems.add(current);
        if (systems.isEmpty()) systems.add(Collections.emptyList());

        List<StaffSystem> out = new ArrayList<>();
        for (int si = 0; si < systems.size(); si++) {
            List<Integer> ms = systems.get(si);
            List<StaffItem> items = new ArrayList<>();
            boolean withTime = showTime && si == 0;
            float x = 0.6f;
            items.add(new StaffItem.Glyph(Smufl.G_CLEF, x, 3f));
            x += CLEF_ADVANCE;
            List<Integer> steps = key.getFifths() >= 0 ? StaffGeometry.SHARP_STEPS : StaffGeometry.FLAT_STEPS;
            for (int k = 0; k < Math.abs(key.getFifths()); k++) {
                items.add(new StaffItem.Glyph(key.getFifths() > 0 ? Smufl.SHARP : Smufl.FLAT, x, StaffGeometry.y(steps.get(k))));
                x += ACC_ADVANCE;
            }
            x += 0.6f;
            if (withTime) {
                items.add(new StaffItem.Glyph(digits(score.getTimeNum()), x, 1f));
                items.add(new StaffItem.Glyph(digits(score.getTimeDen()), x, 3f));
                x += TIME_ADVANCE;
            }
            float natural = 0;
            for (int mi : ms) natural += measures.get(mi).width;
            float stretch = ms.isEmpty() ? 1f : Math.max(0.6f, Math.min(si == systems.size() - 1 ? 1.25f : 3f, (width - x) / natural));
            float top = -1f, bottom = 5f;
            List<float[]> heads = new ArrayList<>(); // x, y of each head, for ties
            List<Integer> tieFrom = new ArrayList<>();
            for (int mi : ms) {
                Prepared pm = measures.get(mi);
                for (int k = 0; k < pm.notes.size(); k++) {
                    ScoreNote n = pm.notes.get(k).note;
                    int src = pm.notes.get(k).source;
                    Integer a = pm.acc.get(k);
                    float w = noteWidth(n, a != null) * stretch;
                    float nx = x + 0.4f * stretch;
                    if (a != null) {
                        items.add(new StaffItem.Glyph(Smufl.accidental(a), nx, StaffGeometry.y(StaffGeometry.step(n.getPitch())), src));
                        nx += ACC_ADVANCE + 0.2f;
                    }
                    if (n.getPitch() == null) {
                        items.add(restGlyph(n.getDuration(), nx, src));
                        heads.add(new float[]{nx, 2f});
                    } else {
                        int step = StaffGeometry.step(n.getPitch());
                        float y = StaffGeometry.y(step);
                        top = Math.min(top, y - 1.5f); bottom = Math.max(bottom, y + 1.5f);
                        for (int ls : StaffGeometry.ledgerSteps(step)) {
                            float ly = StaffGeometry.y(ls);
                            items.add(new StaffItem.Line(nx - 0.4f, ly, nx + HEAD_WIDTH + 0.4f, ly, 0.16f, src));
                        }
                        Fraction base = baseValue(n.getDuration());
                        items.add(new StaffItem.Glyph(headFor(base), nx, y, src));
                        if (base.compareTo(new Fraction(1, 1)) < 0) {
                            boolean up = step < 4;
                            String flag = flagFor(base, up);
                            if (up) {
                                float sx = nx + HEAD_WIDTH - 0.06f;
                                items.add(new StaffItem.Line(sx, y - 0.2f, sx, y - STEM_LENGTH, 0.12f, src));
                                if (flag != null) items.add(new StaffItem.Glyph(flag, sx - 0.06f, y - STEM_LENGTH, src));
                                top = Math.min(top, y - STEM_LENGTH - 0.5f);
                            } else {
                                float sx = nx + 0.06f;
                                items.add(new StaffItem.Line(sx, y + 0.2f, sx, y + STEM_LENGTH, 0.12f, src));
                                if (flag != null) items.add(new StaffItem.Glyph(flag, sx - 0.06f, y + STEM_LENGTH, src));
                                bottom = Math.max(bottom, y + STEM_LENGTH + 0.5f);
                            }
                        }
                        if (isDotted(n.getDuration())) {
                            float dy = step % 2 == 0 ? y - 0.5f : y;
                            items.add(new StaffItem.Glyph(Smufl.DOT, nx + HEAD_WIDTH + 0.35f, dy, src));
                        }
                        if (!tieFrom.isEmpty() && tieFrom.get(tieFrom.size() - 1) == heads.size() - 1) {
                            float[] prev = heads.get(heads.size() - 1);
                            items.add(new StaffItem.Tie(prev[0] + HEAD_WIDTH, nx, prev[1], step >= 4));
                        }
                        heads.add(new float[]{nx, y});
                        if (n.getTie()) tieFrom.add(heads.size() - 1);
                    }
                    x += w;
                }
                x += 1.0f * stretch;
                items.add(new StaffItem.Line(x, 0f, x, 4f, mi == score.getMeasures().size() - 1 ? 0.5f : 0.16f));
            }
            out.add(new StaffSystem(width, items, top, bottom));
        }
        return out;
    }

    public static List<StaffSystem> layout(Score score, float fullWidth) { return layout(score, fullWidth, true); }

    private static boolean isDotted(Fraction d) { return d.getNum() == 3; }

    private static Fraction baseValue(Fraction d) { return isDotted(d) ? new Fraction(d.getNum() * 2 / 3, d.getDen()) : d; }

    private static String headFor(Fraction base) {
        if (base.compareTo(new Fraction(1, 1)) >= 0) return Smufl.HEAD_WHOLE;
        if (base.compareTo(new Fraction(1, 2)) >= 0) return Smufl.HEAD_HALF;
        return Smufl.HEAD_BLACK;
    }

    private static @Nullable String flagFor(Fraction base, boolean up) {
        if (base.equals(new Fraction(1, 8))) return up ? Smufl.FLAG_8TH_UP : Smufl.FLAG_8TH_DOWN;
        if (base.equals(new Fraction(1, 16)) || base.equals(new Fraction(1, 32))) return up ? Smufl.FLAG_16TH_UP : Smufl.FLAG_16TH_DOWN;
        return null;
    }

    private static StaffItem.Glyph restGlyph(Fraction d, float x, int index) {
        Fraction base = baseValue(d);
        if (base.compareTo(new Fraction(1, 1)) >= 0) return new StaffItem.Glyph(Smufl.REST_WHOLE, x, 1f, index);
        if (base.compareTo(new Fraction(1, 2)) >= 0) return new StaffItem.Glyph(Smufl.REST_HALF, x, 2f, index);
        if (base.compareTo(new Fraction(1, 4)) >= 0) return new StaffItem.Glyph(Smufl.REST_QUARTER, x, 2f, index);
        if (base.compareTo(new Fraction(1, 8)) >= 0) return new StaffItem.Glyph(Smufl.REST_8TH, x, 2f, index);
        return new StaffItem.Glyph(Smufl.REST_16TH, x, 2f, index);
    }

    /** A one-note "score" for drills: one whole note (or none) in the given key. */
    public static Score singleNote(@Nullable SpelledPitch pitch, int keyFifths) {
        List<ScoreNote> notes = new ArrayList<>();
        if (pitch != null) notes.add(new ScoreNote(pitch, new Fraction(1, 1)));
        return new Score(keyFifths, 4, 4, 60, Collections.singletonList(new Measure(notes)));
    }

    public static Score singleNote(@Nullable SpelledPitch pitch) { return singleNote(pitch, 0); }
}
