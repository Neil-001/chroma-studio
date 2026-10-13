package dev.chromastudio.client.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * What the mod draws over the world: a two-line readout, a strip naming the hotbar's slots, and the pinned window.
 * Each loader calls {@link #render} from its HUD hook.
 */
public final class Hud {
    private Hud() {}

    /** How long the strip stays, and how long either takes to fade at the end. */
    private static final long STRIP_MS = 5000, FADE_MS = 600;
    /** The bottom of the lowest thing drawn, above the hotbar, the health and food rows and the held item's name. */
    private static final int ABOVE_HOTBAR = 70;

    private static Component line1, line2;
    private static long readoutUntil;
    private static List<ItemStack> slots = List.of();
    private static Component caption;
    private static long stripUntil;

    /** Shows two lines above the hotbar for a number of seconds. A new readout replaces the old one. */
    public static void readout(Component line1, Component line2, int seconds) {
        Hud.line1 = line1;
        Hud.line2 = line2;
        readoutUntil = Util.getMillis() + seconds * 1000L;
    }

    /**
     * Shows up to nine items above the hotbar, each over its slot, with their names listed above. It fades after a few
     * seconds. An empty stack leaves its slot blank.
     */
    public static void strip(List<ItemStack> slots) { strip(slots, null); }

    /** The same, with a line over the names, or null for none. */
    public static void strip(List<ItemStack> slots, Component caption) {
        Hud.slots = List.copyOf(slots.subList(0, Math.min(9, slots.size())));
        Hud.caption = caption;
        stripUntil = Util.getMillis() + STRIP_MS;
    }

    private static float fade(long until, long now) { return Math.clamp((until - now) / (float) FADE_MS, 0, 1); }

    /** Draws everything. Nothing shows with the HUD hidden (F1). */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        ChromaScreen.renderPinned(g);
        Font font = mc.font;
        long now = Util.getMillis();
        int centre = g.guiWidth() / 2, y = g.guiHeight() - ABOVE_HOTBAR;

        float strip = fade(stripUntil, now);
        if (strip > 0.05f && !slots.isEmpty()) {
            y -= 18;
            g.fill(centre - 91, y - 1, centre + 91, y + 17, ARGB.multiplyAlpha(0x90000000, strip));
            for (int i = 0; i < slots.size(); i++) Widgets.blockIcon(g, slots.get(i), centre - 88 + i * 20, y);
            // The names don't fit over 20-pixel slots, so they're listed above, numbered as the slots are.
            for (int i = slots.size() - 1; i >= 0; i--) {
                if (slots.get(i).isEmpty()) continue;
                y -= font.lineHeight;
                Component name = Component.translatable("hud.chromastudio.slot", i + 1, slots.get(i).getHoverName());
                g.fill(centre - 91, y - 1, centre + 91, y + font.lineHeight - 1, ARGB.multiplyAlpha(0x90000000, strip));
                g.centeredText(font, name, centre, y, ARGB.multiplyAlpha(0xFFFFFFFF, strip));
            }
            if (caption != null) {
                y -= font.lineHeight;
                g.fill(centre - 91, y - 1, centre + 91, y + font.lineHeight - 1, ARGB.multiplyAlpha(0x90000000, strip));
                g.centeredText(font, caption, centre, y, ARGB.multiplyAlpha(0xFFC8CED6, strip));
            }
            y -= 4;
        }

        float readout = fade(readoutUntil, now);
        if (readout > 0.05f && line1 != null) {
            int w = Math.max(font.width(line1), font.width(line2)) + 8;
            y -= 2 * font.lineHeight + 4;
            g.fill(centre - w / 2, y - 2, centre + w / 2, y + 2 * font.lineHeight + 2, ARGB.multiplyAlpha(0x90000000, readout));
            g.centeredText(font, line1, centre, y, ARGB.multiplyAlpha(0xFFFFFFFF, readout));
            g.centeredText(font, line2, centre, y + font.lineHeight + 1, ARGB.multiplyAlpha(0xFFC8CED6, readout));
        }
    }
}
