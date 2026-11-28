package dev.chromastudio.core.color;

public enum Metric {
    OKLAB(0.02),
    CIEDE2000(1.0),
    CIE76(2.3),
    SRGB(8),
    MAP_COLOR(0.02);

    private static final double POW_25_7 = Math.pow(25, 7);

    private final double justNoticeable;

    Metric(double justNoticeable) {
        this.justNoticeable = justNoticeable;
    }

    public double distance(Color a, Color b) {
        return switch (this) {
            case OKLAB -> a.oklab().distance(b.oklab());
            case CIEDE2000 -> ciede2000(a.lab(), b.lab());
            case CIE76 -> cie76(a.lab(), b.lab());
            case SRGB -> {
                int dr = a.red() - b.red(), dg = a.green() - b.green(), db = a.blue() - b.blue();
                yield Math.sqrt(dr * dr + dg * dg + db * db);
            }
            case MAP_COLOR -> MapColors.oklab(MapColors.nearest(a)).distance(MapColors.oklab(MapColors.nearest(b)));
        };
    }

    public double justNoticeable() { return justNoticeable; }

    public static double cie76(Lab x, Lab y) {
        double dl = x.l() - y.l(), da = x.a() - y.a(), db = x.b() - y.b();
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    public static double ciede2000(Lab x, Lab y) {
        double c1 = Math.hypot(x.a(), x.b()), c2 = Math.hypot(y.a(), y.b());
        double meanC7 = Math.pow((c1 + c2) / 2, 7);
        double g = 0.5 * (1 - Math.sqrt(meanC7 / (meanC7 + POW_25_7)));
        double a1 = (1 + g) * x.a(), a2 = (1 + g) * y.a();
        double c1p = Math.hypot(a1, x.b()), c2p = Math.hypot(a2, y.b());
        double h1 = hue(a1, x.b()), h2 = hue(a2, y.b());
        boolean grey = c1p * c2p == 0;

        double dL = y.l() - x.l();
        double dC = c2p - c1p;
        double dh = grey ? 0 : h2 - h1;
        if (dh > 180) dh -= 360;
        else if (dh < -180) dh += 360;
        double dH = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dh / 2));

        double meanL = (x.l() + y.l()) / 2;
        double meanCp = (c1p + c2p) / 2;
        double meanH = h1 + h2;
        if (!grey) {
            if (Math.abs(h1 - h2) <= 180) meanH /= 2;
            else if (meanH < 360) meanH = (meanH + 360) / 2;
            else meanH = (meanH - 360) / 2;
        }

        double t = 1 - 0.17 * cosDegrees(meanH - 30) + 0.24 * cosDegrees(2 * meanH)
                + 0.32 * cosDegrees(3 * meanH + 6) - 0.20 * cosDegrees(4 * meanH - 63);
        double turn = 30 * Math.exp(-Math.pow((meanH - 275) / 25, 2));
        double meanCp7 = Math.pow(meanCp, 7);
        double rc = 2 * Math.sqrt(meanCp7 / (meanCp7 + POW_25_7));
        double offL = (meanL - 50) * (meanL - 50);
        double sL = 1 + 0.015 * offL / Math.sqrt(20 + offL);
        double sC = 1 + 0.045 * meanCp;
        double sH = 1 + 0.015 * meanCp * t;
        double rT = -Math.sin(Math.toRadians(2 * turn)) * rc;

        double l = dL / sL, c = dC / sC, h = dH / sH;
        return Math.sqrt(l * l + c * c + h * h + rT * c * h);
    }

    private static double hue(double a, double b) {
        if (a == 0 && b == 0) return 0;
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }

    private static double cosDegrees(double d) {
        return Math.cos(Math.toRadians(d));
    }
}
