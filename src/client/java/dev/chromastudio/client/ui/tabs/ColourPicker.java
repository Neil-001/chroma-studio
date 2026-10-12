package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * The Match tab's pickers: a saturation and value square with a hue bar, and three sliders for OKLCH lightness,
 * chroma and hue. Each slider is drawn as the colours it would give, with the part outside sRGB greyed and a tick
 * where the gamut ends.
 */
final class ColourPicker implements AutoCloseable {
    private static final int SQUARE_PX = 64, SLIDER_PX = 128;
    private static final int OUT_OF_GAMUT = 0xFF3A3F47;
    private static final int NONE = -1, SQUARE = 0, HUE_BAR = 1, SLIDER = 2;
    static final int SLIDER_H = 9, LABEL_W = 9, VALUE_W = 26;
    private static final String[] LETTERS = {"L", "C", "H"};

    final TargetModel model;
    /** Runs when the player moves a picker. The argument is true once they let go. */
    private final Consumer<Boolean> onChange;

    private Bounds square = new Bounds(0, 0, 0, 0), hueBar = square;
    private final Bounds[] sliders = {square, square, square};

    private Pixels squarePixels, huePixels;
    private final Pixels[] sliderPixels = new Pixels[3];
    private float paintedHue = -1;
    private Oklch paintedFor;
    /** Where along each slider the colours leave or come back into sRGB, 0 to 1. */
    private final List<List<Double>> edges = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private int dragging = NONE;

    ColourPicker(Color start, Consumer<Boolean> onChange) {
        model = new TargetModel(start);
        this.onChange = onChange;
    }

    /** Lays the square and its hue bar out in {@code top}, and the three sliders in the first three rows of {@code below}. */
    void layout(Bounds top, Bounds below) {
        hueBar = new Bounds(top.right() - 8, top.y(), 8, top.h());
        square = new Bounds(top.x(), top.y(), top.w() - 10, top.h());
        for (int i = 0; i < 3; i++) {
            sliders[i] = new Bounds(below.x() + LABEL_W, below.y() + i * (SLIDER_H + 2), below.w() - LABEL_W - VALUE_W, SLIDER_H);
        }
    }

    boolean dragging() { return dragging != NONE; }

    void render(GuiGraphicsExtractor g, Font font, float opacity) {
        paint();
        float[] hsv = model.hsv();
        squarePixels.draw(g, square.x(), square.y(), square.w(), square.h(), opacity);
        huePixels.draw(g, hueBar.x(), hueBar.y(), hueBar.w(), hueBar.h(), opacity);
        // The square's handle: a ring, dark outside and light inside, so it shows on any colour.
        int hx = square.x() + Math.round(hsv[1] * (square.w() - 1)), hy = square.y() + Math.round((1 - hsv[2]) * (square.h() - 1));
        g.outline(hx - 3, hy - 3, 7, 7, 0xFF000000);
        g.outline(hx - 2, hy - 2, 5, 5, 0xFFFFFFFF);
        int by = hueBar.y() + Math.round(hsv[0] / 360f * (hueBar.h() - 1));
        g.fill(hueBar.x() - 1, by - 1, hueBar.right() + 1, by + 2, 0xFF000000);
        g.fill(hueBar.x() - 1, by, hueBar.right() + 1, by + 1, 0xFFFFFFFF);

        Oklch lch = model.lch();
        double[] at = {lch.l(), lch.c() / TargetModel.CHROMA_MAX, lch.h() / 360};
        String[] values = {String.format(Locale.ROOT, "%.2f", lch.l()), String.format(Locale.ROOT, "%.3f", lch.c()).substring(1),
                Math.round(lch.h()) % 360 + "°"};
        for (int i = 0; i < 3; i++) {
            Bounds s = sliders[i];
            g.text(font, LETTERS[i], s.x() - LABEL_W, s.y() + 1, ARGB.multiplyAlpha(0xFF9AA5B3, opacity), false);
            sliderPixels[i].draw(g, s.x(), s.y(), s.w(), s.h(), opacity);
            for (double edge : edges.get(i)) {
                int ex = s.x() + (int) Math.round(edge * (s.w() - 1));
                g.fill(ex, s.y(), ex + 1, s.bottom(), 0xFFFFFFFF);
            }
            int kx = s.x() + (int) Math.round(at[i] * (s.w() - 1));
            g.fill(kx - 1, s.y() - 1, kx + 2, s.bottom() + 1, 0xFF000000);
            g.fill(kx, s.y() - 1, kx + 1, s.bottom() + 1, 0xFFFFFFFF);
            g.text(font, values[i], s.right() + VALUE_W - font.width(values[i]), s.y() + 1, ARGB.multiplyAlpha(0xFFE4E9EF, opacity), false);
        }
    }

    /** Paints again whatever the colour has changed: the square when the hue moved, the sliders when anything did. */
    private void paint() {
        if (squarePixels == null) {
            squarePixels = new Pixels(SQUARE_PX, SQUARE_PX);
            huePixels = new Pixels(1, SQUARE_PX);
            for (int i = 0; i < 3; i++) sliderPixels[i] = new Pixels(SLIDER_PX, 1);
            int[] hues = new int[SQUARE_PX];
            for (int y = 0; y < SQUARE_PX; y++) hues[y] = 0xFF000000 | Color.fromHsv(y * 360f / (SQUARE_PX - 1), 1, 1).rgb();
            huePixels.set(hues);
            paintedHue = -1;
            paintedFor = null;
        }
        float hue = model.hsv()[0];
        if (hue != paintedHue) {
            paintedHue = hue;
            int[] px = new int[SQUARE_PX * SQUARE_PX];
            for (int y = 0; y < SQUARE_PX; y++)
                for (int x = 0; x < SQUARE_PX; x++)
                    px[y * SQUARE_PX + x] = 0xFF000000 | Color.fromHsv(hue, x / (float) (SQUARE_PX - 1), 1 - y / (float) (SQUARE_PX - 1)).rgb();
            squarePixels.set(px);
        }
        Oklch lch = model.lch();
        if (!lch.equals(paintedFor)) {
            paintedFor = lch;
            for (int i = 0; i < 3; i++) {
                int[] px = new int[SLIDER_PX];
                edges.get(i).clear();
                boolean before = false;
                for (int x = 0; x < SLIDER_PX; x++) {
                    Oklch c = along(lch, i, x / (double) (SLIDER_PX - 1));
                    boolean in = c.inGamut();
                    px[x] = in ? 0xFF000000 | Color.ofOklch(c).rgb() : OUT_OF_GAMUT;
                    if (x > 0 && in != before) edges.get(i).add((x - 0.5) / (SLIDER_PX - 1));
                    before = in;
                }
                sliderPixels[i].set(px);
            }
        }
    }

    /** The colour a slider gives at a position along it, 0 to 1, with the other two as they are. */
    private static Oklch along(Oklch lch, int slider, double t) {
        return switch (slider) {
            case 0 -> lch.withL(t);
            case 1 -> lch.withC(t * TargetModel.CHROMA_MAX);
            default -> lch.withHue(t * TargetModel.HUE_MAX);
        };
    }

    boolean mouseClicked(double mx, double my) {
        dragging = NONE;
        if (square.contains(mx, my)) dragging = SQUARE;
        else if (hueBar.contains(mx, my)) dragging = HUE_BAR;
        else for (int i = 0; i < 3; i++) if (grown(sliders[i]).contains(mx, my)) dragging = SLIDER + i;
        if (dragging == NONE) return false;
        moveTo(mx, my);
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (dragging == NONE) return false;
        moveTo(mx, my);
        return true;
    }

    boolean mouseReleased() {
        if (dragging == NONE) return false;
        dragging = NONE;
        onChange.accept(true);
        return true;
    }

    /** Steps the slider under the cursor by a hundredth of its length. */
    boolean mouseScrolled(double mx, double my, double v) {
        for (int i = 0; i < 3; i++) {
            if (!grown(sliders[i]).contains(mx, my)) continue;
            Oklch lch = model.lch();
            double at = switch (i) { case 0 -> lch.l(); case 1 -> lch.c() / TargetModel.CHROMA_MAX; default -> lch.h() / 360; };
            model.setLch(along(lch, i, Math.clamp(at + Math.signum(v) * 0.01, 0, 1)));
            onChange.accept(true);
            return true;
        }
        return false;
    }

    /** A slider's bar with a pixel more above and below, so it is easier to take hold of. */
    private static Bounds grown(Bounds s) { return new Bounds(s.x(), s.y() - 1, s.w(), s.h() + 2); }

    private void moveTo(double mx, double my) {
        float[] hsv = model.hsv();
        if (dragging == SQUARE) {
            model.setHsv(hsv[0], (float) fraction(mx, square.x(), square.w()), 1 - (float) fraction(my, square.y(), square.h()));
        } else if (dragging == HUE_BAR) {
            model.setHsv((float) fraction(my, hueBar.y(), hueBar.h()) * 360, hsv[1], hsv[2]);
        } else {
            Bounds s = sliders[dragging - SLIDER];
            model.setLch(along(model.lch(), dragging - SLIDER, fraction(mx, s.x(), s.w())));
        }
        onChange.accept(false);
    }

    private static double fraction(double at, int start, int length) { return Math.clamp((at - start) / Math.max(1, length - 1), 0, 1); }

    @Override
    public void close() {
        if (squarePixels == null) return;
        squarePixels.close();
        huePixels.close();
        for (Pixels p : sliderPixels) p.close();
        squarePixels = null;
    }
}
