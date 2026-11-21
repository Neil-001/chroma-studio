package dev.chromastudio.client.ui;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ThemeChoice;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklab;
import dev.chromastudio.core.color.Oklch;

public final class Theme {
    private Theme() {}

    public static final int DEFAULT_BASE = 0x101317, WARM_BASE = 0x1C1612, VANILLA_BASE = 0xC6C6C6, DARK_BASE = 0x535353;
    private static final int VANILLA_BORDER = 0x555555;
    static final double LIGHT_FROM = 0.5635;
    static final double TEXT_CONTRAST = 7, MARK_CONTRAST = 4.5;
    private static final int DEFAULT_TEXT = 0xE4E9EF;
    private static final double DEFAULT_BASE_L = Color.ofRgb(DEFAULT_BASE).oklab().l(), DEFAULT_TEXT_L = Color.ofRgb(DEFAULT_TEXT).oklab().l();

    public static final int SCRIM = 0x90000000;
    public static final int LIT = 0xFF2F5D8A, LIT_HOVER = 0xFF3F78B0, ON_LIT = 0xFFFFFFFF;
    public static final int FIELD_BAD = 0xFFFF6B6B;
    public static final int TIP_DIM = 0x9AA5B3, TIP_GREYED = 0x4E5560, TIP_TINT = 0x6FC45A, TIP_SWAPPED = 0xFFB84D;

    public static int WINDOW, BACKING;
    public static int WELL, PANEL, BAR, HOVER, SELECTED;
    public static int CHIP, CHIP_OFF, TOOL, TOOL_OFF, TOOL_LIT, CONTROL, CONTROL_HOVER, CONTROL_OFF;
    public static int DANGER;

    public static int BORDER;

    public static int STRONG, TEXT, SOFT, DIM, GREYED, CHIP_GREYED;

    public static int ACCENT;
    public static int HIGHLIGHT;
    public static int GOOD, FAIR, BAD, INFO;

    private static ThemeChoice shownTheme;
    private static String shownCustom;
    private static Oklab base, ink;
    private static int baseRgb;
    private static boolean dark, asWritten;

    static { sync(); }

    public static void sync() {
        if (ModSettings.theme == shownTheme && ModSettings.customColour.equals(shownCustom)) return;
        shownTheme = ModSettings.theme;
        shownCustom = ModSettings.customColour;
        derive(base(shownTheme, shownCustom), shownTheme == ThemeChoice.VANILLA ? VANILLA_BORDER : -1, shownTheme == ThemeChoice.DEFAULT);
    }

    public static int base(ThemeChoice theme, String custom) {
        return switch (theme) {
            case DEFAULT -> DEFAULT_BASE;
            case WARM -> WARM_BASE;
            case VANILLA -> VANILLA_BASE;
            case DARK -> DARK_BASE;
            case CUSTOM -> {
                try {
                    yield Color.fromHex(custom).rgb();
                } catch (IllegalArgumentException _) {
                    yield DEFAULT_BASE;
                }
            }
        };
    }

    static void derive(int rgb, int border, boolean asWritten) {
        Theme.asWritten = asWritten;
        baseRgb = rgb & 0xFFFFFF;
        base = Color.ofRgb(baseRgb).oklab();
        dark = base.l() < LIGHT_FROM;
        if (contrast(inkAt(dark ? 1 : 0), baseRgb) < MARK_CONTRAST) dark = !dark;
        double l = dark ? DEFAULT_TEXT_L : 0.2;
        while (contrast(inkAt(l), baseRgb) < TEXT_CONTRAST && l > 0 && l < 1) l = Math.clamp(l + (dark ? 0.01 : -0.01), 0, 1);
        ink = Color.ofRgb(inkAt(l)).oklab();
        TEXT = asWritten ? opaque(DEFAULT_TEXT) : opaque(inkAt(l));
        STRONG = dark ? 0xFFFFFFFF : 0xFF000000;

        WINDOW = 0xF0000000 | baseRgb;
        BACKING = 0x80000000 | baseRgb;
        WELL = surface(0x0B0D10, false);
        PANEL = surface(0x161A20, false);
        BAR = surface(0x1B2027, false);
        HOVER = surface(0x222830, false);
        SELECTED = surface(0x2C343E, false);
        CHIP = surface(0x262C34, false);
        CHIP_OFF = surface(0x1B1F25, false);
        TOOL = surface(0x2A3038, false);
        TOOL_OFF = surface(0x1A1D22, false);
        TOOL_LIT = surface(0x3F5670, true);
        CONTROL = surface(0x3A424D, false);
        CONTROL_HOVER = surface(0x55606E, false);
        CONTROL_OFF = surface(0x22262C, false);
        DANGER = surface(0xB3403A, true);

        BORDER = border >= 0 ? opaque(border) : opaque(place(0x3A424D, at(0x3A424D), false));

        SOFT = text(0xC8CED6, PANEL, CHIP, CONTROL);
        DIM = text(0x9AA5B3, WELL, PANEL, BAR, HOVER, SELECTED);
        GREYED = opaque(place(0x4E5560, at(0x4E5560), false));
        CHIP_GREYED = opaque(place(0x5A626E, at(0x5A626E), false));

        ACCENT = FAIR = mark(0xFFB84D);
        HIGHLIGHT = mark(0x8EC2F2);
        GOOD = mark(0x5BD06A);
        BAD = mark(0xE5534B);
        INFO = mark(0x6E8CB5);
    }

    private static int opaque(int rgb) { return 0xFF000000 | rgb; }

    private static int inkAt(double l) { return Color.of(new Oklab(l, base.a(), base.b())).rgb(); }

    private static double at(int written) { return (Color.ofRgb(written).oklab().l() - DEFAULT_BASE_L) / (DEFAULT_TEXT_L - DEFAULT_BASE_L); }

    private static int place(int written, double t, boolean tinted) {
        if (asWritten) return written;
        double l = Math.clamp(base.l() + t * (ink.l() - base.l()), 0, 1);
        Oklab own = Color.ofRgb(written).oklab();
        return Color.of(tinted ? new Oklab(l, own.a(), own.b()) : new Oklab(l, base.a() + t * (ink.a() - base.a()), base.b() + t * (ink.b() - base.b()))).rgb();
    }

    private static int surface(int written, boolean tinted) {
        double t = at(written);
        int c = place(written, t, tinted);
        while (!asWritten && t > -1 && contrast(c, TEXT) < MARK_CONTRAST) c = place(written, t -= 0.02, tinted);
        return opaque(c);
    }

    private static int text(int written, int... on) {
        double t = at(written);
        int c = place(written, t, false);
        while (!asWritten && t < 1 && !reads(c, on)) c = place(written, t = Math.min(1, t + 0.02), false);
        return opaque(c);
    }

    private static boolean reads(int c, int[] on) {
        if (contrast(c, baseRgb) < MARK_CONTRAST) return false;
        for (int background : on) if (contrast(c, background) < MARK_CONTRAST) return false;
        return true;
    }

    private static int mark(int written) {
        if (asWritten) return opaque(written);
        Oklch c = Color.ofRgb(written).oklch();
        int rgb = written;
        for (double l = c.l(); contrast(rgb, baseRgb) < MARK_CONTRAST && l > 0 && l < 1; ) {
            l = Math.clamp(l + (dark ? 0.02 : -0.02), 0, 1);
            rgb = Color.ofOklch(c.withL(l)).rgb();
        }
        return opaque(rgb);
    }

    public static boolean shadowed() { return dark; }

    public static int veil(int alpha) { return alpha << 24 | STRONG & 0xFFFFFF; }

    public static int sink(int alpha) { return alpha << 24 | (dark ? 0x000000 : 0xFFFFFF); }

    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
