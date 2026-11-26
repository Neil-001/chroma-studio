package dev.chromastudio.core.color;

public record HueWindow(double start, double width) {
    public HueWindow {
        start = wrap(start);
        width = Math.clamp(width, Double.MIN_NORMAL, 1);
    }

    public static HueWindow around(double centre, double width) { return new HueWindow(centre - width / 2, width); }

    public static double wrap(double turns) {
        double w = turns - Math.floor(turns);
        return w >= 1 ? 0 : w;
    }

    public double offset(double hue) { return wrap(hue - start); }
}
