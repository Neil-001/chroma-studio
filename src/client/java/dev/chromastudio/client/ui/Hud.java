package dev.chromastudio.client.ui;

import dev.chromastudio.client.action.ScreenPicker;
import dev.chromastudio.client.axiom.AxiomBridge;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

public final class Hud {
    private Hud() {}

    public enum Kind {
        PROMPT(0xFFFFFFFF, ""), DONE(0xFF000000 | Theme.TIP_TINT, "\u2714 "), PROBLEM(Theme.FIELD_BAD, "\u2716 ");

        final int accent;
        final String mark;

        Kind(int accent, String mark) {
            this.accent = accent;
            this.mark = mark;
        }

        Component marked(Component title) {
            return mark.isEmpty() ? title : Component.empty().append(Component.literal(mark).withColor(accent & 0xFFFFFF)).append(title);
        }
    }

    private static final long STRIP_MS = 5000, FADE_MS = 600, MISSED_MS = 250;
    private static final int ABOVE_HOTBAR = 70;
    private static final int MARGIN = 6, PAD = 4, ACCENT = 3, ICON = 16, MIN_WIDTH = 210;
    private static final int BACK = 0xB0000000, SUBTLE = 0xFFC8CED6;

    private static final String OVERFLOW_MODE = "hotbar.chromastudio.mode";

    private static Component line1, line2;
    private static Kind kind = Kind.PROMPT;
    private static ItemStack icon = ItemStack.EMPTY;
    private static int swatch = -1;
    private static long readoutUntil;
    private static List<ItemStack> slots = List.of();
    private static Component caption;
    private static long stripUntil;
    private static long lastFrame;

    public static void readout(Component line1, Component line2, int seconds) {
        readout(Kind.PROMPT, line1, line2, seconds);
    }

    public static void readout(Kind kind, Component line1, Component line2, int seconds) {
        readout(kind, ItemStack.EMPTY, -1, line1, line2, seconds);
    }

    public static void readout(Kind kind, ItemStack icon, int swatch, Component line1, Component line2, int seconds) {
        Hud.kind = kind;
        Hud.icon = icon;
        Hud.swatch = swatch;
        Hud.line1 = line1;
        Hud.line2 = line2;
        readoutUntil = Util.getMillis() + seconds * 1000L;
    }

    public static Component key(KeyMapping key) {
        return Component.literal("[").append(key.getTranslatedKeyMessage()).append("]").withColor(0xFFFFFF);
    }

    public static void strip(List<ItemStack> slots) { strip(slots, null); }

    public static void strip(List<ItemStack> slots, Component caption) {
        Hud.slots = List.copyOf(slots.subList(0, Math.min(9, slots.size())));
        Hud.caption = caption != null && caption.getContents() instanceof TranslatableContents t && t.getKey().equals(OVERFLOW_MODE) ? null : caption;
        stripUntil = Util.getMillis() + STRIP_MS;
    }

    private static float fade(long until, long now) { return Math.clamp((until - now) / (float) FADE_MS, 0, 1); }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        long gap = lastFrame > 0 ? now - lastFrame : 0;
        long covered = mc.gui.screen() != null || gap > MISSED_MS ? gap : 0;
        lastFrame = now;
        if (covered > 0 && !ScreenPicker.armed()) {
            readoutUntil += covered;
            stripUntil += covered;
        }
        if (mc.gui.hud.isHidden()) return;
        ChromaScreen.renderPinned(g);
        Font font = mc.font;

        boolean low = AxiomBridge.editorOpen();
        int y = low ? g.guiHeight() - ABOVE_HOTBAR : ScreenPicker.armed() ? MARGIN + banner(font) + 3 : MARGIN;
        float readout = fade(readoutUntil, now);
        if (readout > 0.05f && line1 != null) y = panel(g, font, y, low, kind, icon, swatch, line1, line2, readout);

        float strip = fade(stripUntil, now);
        if (strip > 0.05f && !slots.isEmpty()) {
            int filled = 0;
            for (ItemStack slot : slots) if (!slot.isEmpty()) filled++;
            panel(g, font, y, low, Kind.DONE, ItemStack.EMPTY, -1, Component.translatable("hud.chromastudio.filled", filled), caption, strip);
        }
    }

    public static void banner(GuiGraphicsExtractor g, Component line1, Component line2) {
        panel(g, Minecraft.getInstance().font, MARGIN, false, Kind.PROMPT, ItemStack.EMPTY, -1, line1, line2, 1);
    }

    private static int banner(Font font) { return 2 * font.lineHeight + 1 + 2 * PAD; }

    private static int panel(GuiGraphicsExtractor g, Font font, int y, boolean upward, Kind kind, ItemStack icon, int swatch,
            Component line1, Component line2, float alpha) {
        boolean hasIcon = !icon.isEmpty(), hasSwatch = swatch >= 0;
        int lead = (hasIcon ? ICON + 2 : 0) + (hasSwatch ? ICON + 2 : 0);
        if (lead > 0) lead += 2;
        int room = Math.max(40, g.guiWidth() - 2 * MARGIN - 2 * PAD - ACCENT - lead);
        List<FormattedCharSequence> first = font.split(kind.marked(line1), room), second = line2 == null ? List.of() : font.split(line2, room);
        int textW = 0;
        for (FormattedCharSequence line : first) textW = Math.max(textW, font.width(line));
        for (FormattedCharSequence line : second) textW = Math.max(textW, font.width(line));
        int lh = font.lineHeight, lines = first.size() + second.size();
        int textH = lines * lh + (second.isEmpty() ? 0 : 1);
        int h = Math.max(textH, lead > 0 ? ICON : 0);
        int w = Math.min(Math.max(lead + textW, MIN_WIDTH), g.guiWidth() - 2 * MARGIN - 2 * PAD - ACCENT);
        int outerH = h + 2 * PAD;
        int x = (g.guiWidth() - (w + ACCENT + 2 * PAD)) / 2, top = upward ? y - outerH : y;
        panel(g, x, top, w, h, kind, alpha);
        int cx = x + ACCENT + PAD, cy = top + PAD;
        if (hasIcon) {
            if (alpha > 0.5f) Widgets.blockIcon(g, icon, cx, cy + (h - ICON) / 2);
            cx += ICON + 2;
        }
        if (hasSwatch) {
            int sy = cy + (h - ICON) / 2;
            g.fill(cx, sy, cx + ICON, sy + ICON, ARGB.multiplyAlpha(0xFFFFFFFF, alpha));
            g.fill(cx + 1, sy + 1, cx + ICON - 1, sy + ICON - 1, ARGB.multiplyAlpha(0xFF000000, alpha));
            g.fill(cx + 2, sy + 2, cx + ICON - 2, sy + ICON - 2, ARGB.multiplyAlpha(0xFF000000 | swatch, alpha));
            cx += ICON + 2;
        }
        if (lead > 0) cx += 2;
        int ty = cy + (h - textH) / 2;
        for (FormattedCharSequence line : first) {
            g.text(font, line, cx, ty, ARGB.multiplyAlpha(0xFFFFFFFF, alpha), true);
            ty += lh;
        }
        ty += 1;
        for (FormattedCharSequence line : second) {
            g.text(font, line, cx, ty, ARGB.multiplyAlpha(SUBTLE, alpha), true);
            ty += lh;
        }
        return upward ? top - 3 : top + outerH + 3;
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int contentW, int contentH, Kind kind, float alpha) {
        int right = x + ACCENT + 2 * PAD + contentW, bottom = y + 2 * PAD + contentH;
        g.fill(x, y, right, bottom, ARGB.multiplyAlpha(BACK, alpha));
        g.fill(x, y, x + ACCENT, bottom, ARGB.multiplyAlpha(kind.accent, alpha));
    }
}
