package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Query.Tri;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Badge;
import dev.chromastudio.core.color.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The filters behind "More…", as rows of controls. The Match tab and the Gradient tab both open them, and both on
 * {@link MatchState#filters()}, so a filter set in one is set in the other.
 */
final class FilterControls {
    private FilterControls() {}

    private static final int ROW = 18, GAP = 2, TEXT = 0xFFE4E9EF, DIM = 0xFF9AA5B3;
    private static final String ALL = "all";

    private static MutableComponent tr(String key, Object... args) { return Component.translatable("match.chromastudio." + key, args); }

    private static Component lower(String prefix, Enum<?> value) {
        return Component.translatable(prefix + value.name().toLowerCase(Locale.ROOT));
    }

    /** A filter's name, cut to the room left of its control. A translation can be twice the English, and the tooltip then has the whole of it. */
    private static void filterLabel(Widgets w, int x, int y, int room, Component name) {
        String fitted = MatchTab.ellipsis(Minecraft.getInstance().font, name.getString(), room);
        AbstractWidget label = w.label(x, y + 4, Component.literal(fitted), TEXT);
        if (!fitted.equals(name.getString())) label.setTooltip(Tooltip.create(name));
    }

    /**
     * Adds the filters to a group, which then scrolls inside {@code body}. With {@code basics} the three the Match
     * tab keeps on its filter row lead, for a tab that has no such row. {@code afterReset} runs after "Reset filters",
     * for the caller to lay out what else shows a filter.
     */
    static void build(Widgets w, Bounds body, boolean basics, Runnable afterReset) {
        MatchFilters f = MatchState.filters();
        int x = body.x() + 4, cw = Math.min(120, body.w() / 2), cx = body.right() - cw - 8, y = body.y() + 2;
        List<Tri> tris = List.of(Tri.values());
        Function<Tri, Component> triName = t -> lower("match.chromastudio.filter.tri.", t);

        if (basics) {
            filterLabel(w, x, y, cx - x - 3, tr("filter.cube"));
            w.toggle(cx, y, cw, null, f.fullCube, v -> { f.fullCube = v; MatchState.changed(); }, tr("filter.cube.tip"));
            y += ROW;
            filterLabel(w, x, y, cx - x - 3, tr("filter.solid"));
            w.toggle(cx, y, cw, null, f.solid, v -> { f.solid = v; MatchState.changed(); }, tr("filter.solid.tip"));
            y += ROW;
            filterLabel(w, x, y, cx - x - 3, tr("filter.opaque"));
            w.toggle(cx, y, cw, null, f.opaque, v -> { f.opaque = v; MatchState.changed(); }, tr("filter.opaque.tip"));
            y += ROW;
        }
        filterLabel(w, x, y, cx - x - 3, tr("filter.same_texture"));
        w.toggle(cx, y, cw, null, f.sameTexture, v -> { f.sameTexture = v; MatchState.changed(); }, tr("filter.same_texture.tip"));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.tinted"));
        w.cycler(cx, y, cw, null, tris, f.tinted, triName, v -> { f.tinted = v; MatchState.changed(); }, tr("filter.tinted.tip"));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.creative_only"));
        w.cycler(cx, y, cw, null, tris, f.creativeOnly, triName, v -> { f.creativeOnly = v; MatchState.changed(); }, tr("filter.creative_only.tip"));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.light"));
        w.cycler(cx, y, cw, null, tris, f.lightSource, triName, v -> { f.lightSource = v; MatchState.changed(); }, tr("filter.light.tip"));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.technical"));
        w.toggle(cx, y, cw, null, f.technical, v -> { f.technical = v; MatchState.changed(); }, tr("filter.technical.tip"));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.block_entity"));
        w.toggle(cx, y, cw, null, f.blockEntityRendered, v -> { f.blockEntityRendered = v; MatchState.changed(); }, tr("filter.block_entity.tip"));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.tone"));
        List<String> tones = new ArrayList<>(List.of(ALL));
        for (Tone t : Tone.values()) tones.add(t.name());
        w.cycler(cx, y, cw, null, tones, f.tone == null ? ALL : f.tone.name(), t -> Component.translatable("tone.chromastudio." + t.toLowerCase(Locale.ROOT)), t -> {
            f.tone = t.equals(ALL) ? null : Tone.valueOf(t);
            MatchState.changed();
        }, tr("filter.tone.tip"));
        y += ROW;
        int sw = (cw - GAP) / 2;
        filterLabel(w, x, y, cx - x - 3, tr("filter.lightness"));
        w.spin(cx, y, sw, f.lightnessMin, 0.05, 0, 1, false, v -> { f.lightnessMin = v; MatchState.changed(); }).setTooltip(Tooltip.create(tr("filter.lightness.tip")));
        w.spin(cx + sw + GAP, y, cw - sw - GAP, f.lightnessMax, 0.05, 0, 1, false, v -> { f.lightnessMax = v; MatchState.changed(); }).setTooltip(Tooltip.create(tr("filter.lightness.tip")));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.chroma"));
        w.spin(cx, y, sw, f.chromaMin, 0.02, 0, TargetModel.CHROMA_MAX, false, v -> { f.chromaMin = v; MatchState.changed(); }).setTooltip(Tooltip.create(tr("filter.chroma.tip")));
        w.spin(cx + sw + GAP, y, cw - sw - GAP, f.chromaMax, 0.02, 0, TargetModel.CHROMA_MAX, false, v -> { f.chromaMax = v; MatchState.changed(); }).setTooltip(Tooltip.create(tr("filter.chroma.tip")));
        y += ROW;
        filterLabel(w, x, y, cx - x - 3, tr("filter.badge"));
        int bx = cx;
        for (Badge badge : Badge.values()) {
            Component label = lower("badge.chromastudio.", badge);
            w.add(new Chip(bx, y, label, () -> f.badges.contains(badge), () -> {
                if (!f.badges.remove(badge)) f.badges.add(badge);
                MatchState.changed();
            })).setTooltip(Tooltip.create(tr("filter.badge.tip")));
            bx += Chip.width(label) + GAP;
        }
        y += ROW;
        Component reset = tr("filter.reset");
        w.button(cx, y, reset, () -> {
            MatchState.resetFilters();
            afterReset.run();
        }).setTooltip(Tooltip.create(tr("filter.reset.tip")));
        y += ROW + 2;
        w.label(x, y + 4, tr("filter.mods"), DIM);
        y += 14;
        for (String namespace : BlockIndex.get().namespaces()) {
            w.label(x, y + 4, Component.literal(namespace), TEXT);
            w.cycler(cx, y, cw, null, tris, f.namespaces.getOrDefault(namespace, Tri.ANY), triName, v -> {
                if (v == Tri.ANY) f.namespaces.remove(namespace);
                else f.namespaces.put(namespace, v);
                MatchState.changed();
            }, tr("filter.mods.tip"));
            y += ROW;
        }
        w.viewport(body, y - body.y() + 2);
    }
}
