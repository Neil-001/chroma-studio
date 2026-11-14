package dev.chromastudio.client.ui;

/**
 * Places a row of controls left to right, and starts another row under it wherever the next one would pass the right
 * edge, as a line of words wraps. A translation can make a row of buttons too long for the window at its smallest.
 */
public final class Flow {
    private Flow() {}

    /** Where a control goes: its left edge, and its row, counting from 0 for the first. */
    public record Spot(int x, int row) {}

    /**
     * Where each control goes, given their widths, the edges of the room and the gap between two controls. A row always
     * takes its first control, even one wider than the room, so nothing is ever left out.
     */
    public static Spot[] place(int[] widths, int left, int right, int gap) {
        Spot[] spots = new Spot[widths.length];
        int x = left, row = 0;
        for (int i = 0; i < widths.length; i++) {
            if (x > left && x + widths[i] > right) {
                x = left;
                row++;
            }
            spots[i] = new Spot(x, row);
            x += widths[i] + gap;
        }
        return spots;
    }

    /** How many rows the controls take, at least one. */
    public static int rows(Spot[] spots) { return spots.length == 0 ? 1 : spots[spots.length - 1].row() + 1; }
}
