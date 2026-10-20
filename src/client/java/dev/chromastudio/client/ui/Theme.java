package dev.chromastudio.client.ui;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ThemeChoice;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklab;
import dev.chromastudio.core.color.Oklch;

/**
 * Every colour the window, its tabs and the settings screen draw with, worked out from one base colour: the window's
 * own background. The fields are ARGB and change when the theme setting does, so read them while drawing and never
 * keep one. {@link #sync} brings them up to date; whatever starts a frame of the mod's drawing calls it.
 *
 * <p>Each colour is written below as it is on the default base. On another base it sits as far along the way from the
 * base to the text colour as it does there, by OKLab lightness, so a part that is lighter than a dark window is darker
 * than a light one. Text and marks are then moved further from the base, and backgrounds that carry text closer to it,
 * until the text reads.
 *
 * <p>What isn't here stays as it is on every theme: a colour that is data, such as a swatch, and the black and white
 * marks drawn over one.
 */
public final class Theme {
    private Theme() {}

    /** The bases of the ready-made themes, packed 0xRRGGBB. */
    public static final int DEFAULT_BASE = 0x101317, WARM_BASE = 0x1C1612, VANILLA_BASE = 0xC6C6C6, DARK_BASE = 0x535353;
    /** The inventory's own darker border, which the vanilla theme draws its frames with. */
    private static final int VANILLA_BORDER = 0x555555;
    /** A base lighter than this, in OKLab, gets dark text. Black and white read equally well on it. */
    static final double LIGHT_FROM = 0.5635;
    /** The contrast, as WCAG counts it, that text gets against the window, and that marks and muted text get. */
    static final double TEXT_CONTRAST = 7, MARK_CONTRAST = 4.5;
    private static final int DEFAULT_TEXT = 0xE4E9EF;
    private static final double DEFAULT_BASE_L = Color.ofRgb(DEFAULT_BASE).oklab().l(), DEFAULT_TEXT_L = Color.ofRgb(DEFAULT_TEXT).oklab().l();

    /** The dimming behind a dialog. Dark on every theme, so the dialog stands out. */
    public static final int SCRIM = 0x90000000;
    /** A chip that's on, plain and under the cursor, and the text on it. The blue is dark on every theme, so the text is white on every one. */
    public static final int LIT = 0xFF2F5D8A, LIT_HOVER = 0xFF3F78B0, ON_LIT = 0xFFFFFFFF;
    /** Text a field can't take. A field is the game's own, black on every theme. */
    public static final int FIELD_BAD = 0xFFFF6B6B;
    /** For lines in the game's own tooltips, which are dark whatever the window is. */
    public static final int TIP_DIM = 0x9AA5B3, TIP_GREYED = 0x4E5560, TIP_TINT = 0x6FC45A, TIP_SWAPPED = 0xFFB84D;

    // Backgrounds, from the window itself up to the parts that stand furthest out of it.
    /** The window's fill, slightly see-through, and the same colour as a backing for text over a picture. */
    public static int WINDOW, BACKING;
    public static int WELL, PANEL, BAR, HOVER, SELECTED;
    /** A chip, a toolbar button and the small drawn buttons, each as it is plain, under the cursor and greyed out. */
    public static int CHIP, CHIP_OFF, TOOL, TOOL_OFF, TOOL_LIT, CONTROL, CONTROL_HOVER, CONTROL_OFF;
    /** The close button under the cursor. */
    public static int DANGER;

    public static int BORDER;

    // Text, from the strongest to the faintest.
    public static int STRONG, TEXT, SOFT, DIM, GREYED, CHIP_GREYED;

    // Marks that mean something by their hue.
    /** The selected tab's underline, and what else is picked out in amber. */
    public static int ACCENT;
    /** Links, and what else is picked out in blue. */
    public static int HIGHLIGHT;
    public static int GOOD, FAIR, BAD, INFO;

    private static ThemeChoice shownTheme;
    private static String shownCustom;
    /** The working of one derivation: the base, the text colour, and which way the text lies from the base. */
    private static Oklab base, ink;
    private static int baseRgb;
    private static boolean dark, asWritten;

    static { sync(); }

    /** Takes the colours from the settings again, if the theme has changed since the last call. */
    public static void sync() {
        if (ModSettings.theme == shownTheme && ModSettings.customColour.equals(shownCustom)) return;
        shownTheme = ModSettings.theme;
        shownCustom = ModSettings.customColour;
        derive(base(shownTheme, shownCustom), shownTheme == ThemeChoice.VANILLA ? VANILLA_BORDER : -1, shownTheme == ThemeChoice.DEFAULT);
    }

    /** The base colour a theme builds on, packed 0xRRGGBB. */
    public static int base(ThemeChoice theme, String custom) {
        return switch (theme) {
            case DEFAULT -> DEFAULT_BASE;
            case WARM -> WARM_BASE;
            case VANILLA -> VANILLA_BASE;
            case DARK -> DARK_BASE;
            case CUSTOM -> {
                try {
                    yield Color.fromHex(custom).rgb();
                } catch (IllegalArgumentException ignored) {
                    yield DEFAULT_BASE;
                }
            }
        };
    }

    /**
     * Works every colour out from a base. {@code border} replaces the frame colour, or is -1 for the worked-out one.
     * With {@code asWritten} the colours are exactly the ones written here, which is right only on the default base.
     */
    static void derive(int rgb, int border, boolean asWritten) {
        Theme.asWritten = asWritten;
        baseRgb = rgb & 0xFFFFFF;
        base = Color.ofRgb(baseRgb).oklab();
        dark = base.l() < LIGHT_FROM;
        // Lightness and contrast part ways on a strong colour, a violet most of all. Where even white or black wouldn't
        // read on the side lightness picks, the other side does.
        if (contrast(inkAt(dark ? 1 : 0), baseRgb) < MARK_CONTRAST) dark = !dark;
        // Near-white or near-black, in the base's own tint, and all the way to white or black when the base is a
        // middling one that near enough wouldn't read on.
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

        // Each with the backgrounds it is drawn on: a chip's label, and the muted text of rows and the title bar.
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

    /** The base's own tint at another lightness. */
    private static int inkAt(double l) { return Color.of(new Oklab(l, base.a(), base.b())).rgb(); }

    /** How far from the default base to the default text a colour written here lies, by lightness. Under 0 is the far side of the base. */
    private static double at(int written) { return (Color.ofRgb(written).oklab().l() - DEFAULT_BASE_L) / (DEFAULT_TEXT_L - DEFAULT_BASE_L); }

    /** The colour that far from this base to this text. {@code tinted} keeps the written colour's own hue and strength. */
    private static int place(int written, double t, boolean tinted) {
        if (asWritten) return written;
        double l = Math.clamp(base.l() + t * (ink.l() - base.l()), 0, 1);
        Oklab own = Color.ofRgb(written).oklab();
        return Color.of(tinted ? new Oklab(l, own.a(), own.b()) : new Oklab(l, base.a() + t * (ink.a() - base.a()), base.b() + t * (ink.b() - base.b()))).rgb();
    }

    /** A background that carries text: taken back towards the base, and past it if need be, until the text reads on it. */
    private static int surface(int written, boolean tinted) {
        double t = at(written);
        int c = place(written, t, tinted);
        while (!asWritten && t > -1 && contrast(c, TEXT) < MARK_CONTRAST) c = place(written, t -= 0.02, tinted);
        return opaque(c);
    }

    /**
     * Text fainter than the main text: taken further from the base until it reads on the window and on each of the
     * backgrounds given. At the far end it is the main text itself, which reads on all of them.
     */
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

    /** A mark known by its hue: it keeps the hue and strength, and moves to the text's side of the base until it reads. */
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

    /** Whether text is light, and so can have the game's drop shadow. Under dark text a shadow is only a smear. */
    public static boolean shadowed() { return dark; }

    /** A wash of the strongest text colour, for lighting a row up under the cursor. {@code alpha} is 0 to 255. */
    public static int veil(int alpha) { return alpha << 24 | STRONG & 0xFFFFFF; }

    /** A wash away from the text colour, for setting an area back. {@code alpha} is 0 to 255. */
    public static int sink(int alpha) { return alpha << 24 | (dark ? 0x000000 : 0xFFFFFF); }

    /** The contrast between two colours as WCAG counts it, from 1 for the same colour to 21 for black on white. Alpha is ignored. */
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
