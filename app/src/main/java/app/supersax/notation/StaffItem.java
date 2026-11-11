package app.supersax.notation;

/** A drawable element, in staff spaces. {@code noteIndex} links glyphs to the score note (for highlighting). */
public abstract class StaffItem {
    private StaffItem() {}

    public static final class Glyph extends StaffItem {
        private final String text;
        private final float x, y;
        private final int noteIndex;
        public Glyph(String text, float x, float y, int noteIndex) { this.text = text; this.x = x; this.y = y; this.noteIndex = noteIndex; }
        public Glyph(String text, float x, float y) { this(text, x, y, -1); }
        public String getText() { return text; }
        public float getX() { return x; }
        public float getY() { return y; }
        public int getNoteIndex() { return noteIndex; }
    }

    public static final class Line extends StaffItem {
        private final float x1, y1, x2, y2, thickness;
        private final int noteIndex;
        public Line(float x1, float y1, float x2, float y2, float thickness, int noteIndex) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.thickness = thickness; this.noteIndex = noteIndex;
        }
        public Line(float x1, float y1, float x2, float y2, float thickness) { this(x1, y1, x2, y2, thickness, -1); }
        public float getX1() { return x1; }
        public float getY1() { return y1; }
        public float getX2() { return x2; }
        public float getY2() { return y2; }
        public float getThickness() { return thickness; }
        public int getNoteIndex() { return noteIndex; }
        @Override public String toString() { return "Line(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ", " + thickness + ", " + noteIndex + ")"; }
    }

    public static final class Tie extends StaffItem {
        private final float x1, x2, y;
        private final boolean above;
        public Tie(float x1, float x2, float y, boolean above) { this.x1 = x1; this.x2 = x2; this.y = y; this.above = above; }
        public float getX1() { return x1; }
        public float getX2() { return x2; }
        public float getY() { return y; }
        public boolean getAbove() { return above; }
    }

    /** Index of the score note this item belongs to, or -1. */
    public int noteIndexOrNone() {
        if (this instanceof Glyph) return ((Glyph) this).getNoteIndex();
        if (this instanceof Line) return ((Line) this).getNoteIndex();
        return -1;
    }
}
