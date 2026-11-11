package app.supersax.notation;

import java.util.List;

/** One line of music. {@code top} and {@code bottom} bound everything drawn (ledger notes may go beyond the staff). */
public final class StaffSystem {
    private final float width, top, bottom;
    private final List<StaffItem> items;

    public StaffSystem(float width, List<StaffItem> items, float top, float bottom) {
        this.width = width; this.items = items; this.top = top; this.bottom = bottom;
    }

    public float getWidth() { return width; }
    public List<StaffItem> getItems() { return items; }
    public float getTop() { return top; }
    public float getBottom() { return bottom; }
}
