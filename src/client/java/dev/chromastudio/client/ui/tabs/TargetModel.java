package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklch;
import java.util.Locale;

/**
 * The colour the Match tab's pickers and fields all show. Whichever one the player moves sets the colour, and the
 * rest follow. The picker a change came from keeps its own figures, so its handle stays where it was put: a grey has
 * no hue of its own, and an OKLCH colour outside sRGB is shown clipped.
 */
public final class TargetModel {
    /** The most chroma the slider reaches. sRGB's own most is about 0.32. */
    public static final double CHROMA_MAX = 0.4;
    /** The hue slider's far end, just short of the full turn that is the same hue as its start. */
    public static final double HUE_MAX = 359.99;
    /** Less chroma than this is a grey, whose hue is noise. */
    private static final double GREY_CHROMA = 1e-4;

    private Color colour;
    private float[] hsv;
    private Oklch lch;

    public TargetModel(Color colour) { setColour(colour); }

    public Color colour() { return colour; }

    /** Hue 0 to 360, saturation and value 0 to 1. */
    public float[] hsv() { return hsv.clone(); }

    /** As the sliders stand, which may be outside sRGB. */
    public Oklch lch() { return lch; }

    public void setColour(Color c) {
        colour = c;
        hsv = hsvOf(c);
        lch = lchOf(c);
    }

    public void setHsv(float h, float s, float v) {
        hsv = new float[] {Math.clamp(h, 0, 360), Math.clamp(s, 0, 1), Math.clamp(v, 0, 1)};
        colour = Color.fromHsv(hsv[0], hsv[1], hsv[2]);
        lch = lchOf(colour);
    }

    public void setLch(Oklch to) {
        // The record turns a hue of 360 into 0, which would send the handle from one end to the other.
        lch = new Oklch(Math.clamp(to.l(), 0, 1), Math.clamp(to.c(), 0, CHROMA_MAX), Math.clamp(to.h(), 0, HUE_MAX));
        colour = Color.ofOklch(lch);
        hsv = hsvOf(colour);
    }

    /** A grey has no hue and black no saturation either, so those keep the hue the pickers last showed. */
    private float[] hsvOf(Color c) {
        float[] next = c.hsv();
        if (hsv != null && (next[1] == 0 || next[2] == 0)) next[0] = hsv[0];
        return next;
    }

    private Oklch lchOf(Color c) {
        Oklch next = c.oklch();
        return lch != null && next.c() < GREY_CHROMA ? next.withHue(lch.h()) : next;
    }

    public String hex() { return colour.hex(); }

    /** Red, green and blue, 0 to 255, as {@code 255,136,0}: the field is too narrow for spaces. */
    public String rgbText() { return colour.red() + "," + colour.green() + "," + colour.blue(); }

    /** Hue in degrees, then saturation and lightness in percent, as {@code 32,100,50}. */
    public String hslText() {
        float[] hsl = colour.hsl();
        return Math.round(hsl[0]) % 360 + "," + Math.round(hsl[1] * 100) + "," + Math.round(hsl[2] * 100);
    }

    /** Takes a typed hex code, with or without the {@code #}. Returns false, changing nothing, when it isn't one. */
    public boolean typeHex(String text) {
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{6}")) return false;
        setColour(Color.ofRgb(Integer.parseInt(s, 16)));
        return true;
    }

    /** Takes three typed numbers, 0 to 255. Returns false, changing nothing, for anything else. */
    public boolean typeRgb(String text) {
        double[] n = numbers(text);
        if (n == null || !within(n[0], 255) || !within(n[1], 255) || !within(n[2], 255)) return false;
        setColour(Color.ofRgb((int) Math.round(n[0]) << 16 | (int) Math.round(n[1]) << 8 | (int) Math.round(n[2])));
        return true;
    }

    /** Takes a typed hue, 0 to 360, and a saturation and lightness, 0 to 100. Returns false, changing nothing, for anything else. */
    public boolean typeHsl(String text) {
        double[] n = numbers(text);
        if (n == null || !within(n[0], 360) || !within(n[1], 100) || !within(n[2], 100)) return false;
        setColour(Color.fromHsl((float) n[0], (float) (n[1] / 100), (float) (n[2] / 100)));
        return true;
    }

    private static boolean within(double v, double max) { return v >= 0 && v <= max; }

    /** Three numbers set apart by commas, spaces or both, each with an optional {@code %} or {@code °}. Null for anything else. */
    private static double[] numbers(String text) {
        String[] parts = text.trim().toLowerCase(Locale.ROOT).split("[\\s,;/]+");
        if (parts.length != 3) return null;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            String p = parts[i].replaceAll("[%°]$", "");
            if (!p.matches("\\d{1,3}(\\.\\d{1,3})?")) return null;
            out[i] = Double.parseDouble(p);
        }
        return out;
    }
}
