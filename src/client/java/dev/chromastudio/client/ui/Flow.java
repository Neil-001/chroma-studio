package dev.chromastudio.client.ui;

public final class Flow {
    private Flow() {}

    public record Spot(int x, int row) {}

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

    public static int rows(Spot[] spots) { return spots.length == 0 ? 1 : spots[spots.length - 1].row() + 1; }
}
