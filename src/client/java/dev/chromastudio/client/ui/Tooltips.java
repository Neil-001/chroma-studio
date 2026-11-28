package dev.chromastudio.client.ui;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.TooltipMode;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Metric;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class Tooltips {
    private Tooltips() {}

    private static final int GREY = 0xA8A8A8;
    private static final Set<Block> UNREADABLE = ConcurrentHashMap.newKeySet();

    public static void append(ItemStack stack, List<Component> lines) {
        if (!wanted() || !(stack.getItem() instanceof BlockItem item)) return;
        BlockIndex index = BlockIndex.get();
        List<Entry> states = index.statesOf(item.getBlock());
        if (states.isEmpty()) return;
        add(item.getBlock(), () -> lines(states.getFirst()), lines);
    }

    static boolean add(Block block, Supplier<List<Component>> made, List<Component> lines) {
        try {
            lines.addAll(made.get());
            return false;
        } catch (RuntimeException e) {
            if (!UNREADABLE.add(block)) return false;
            ChromaStudioClient.LOGGER.warn("Couldn't add the colour to the tooltip of {}", BuiltInRegistries.BLOCK.getKey(block), e);
            return true;
        }
    }

    static List<Component> lines(Entry entry) {
        List<Component> out = new ArrayList<>();
        MatchState.follow();
        TintContext tint = MatchState.tint();
        Scale scale = MatchState.scale();
        Metric metric = MatchState.metric();
        Target target = MatchState.target();
        Face face = entry.bestFace(target, scale, metric, tint);
        TextureStats stats = entry.face(face, tint);
        Color colour = stats.mean(scale);

        MutableComponent first = Component.empty();
        if (ModSettings.tooltipSwatch) first.append(Component.literal("\u25A0 ").withColor(colour.rgb()));
        if (ModSettings.tooltipHex) first.append(Component.literal(colour.hex()).withColor(GREY));
        if (ModSettings.tooltipSwatch || ModSettings.tooltipHex) out.add(first);

        Component kind = Component.translatable("kind.chromastudio." + entry.kind().name().toLowerCase(Locale.ROOT));
        Component badge = Component.translatable("badge.chromastudio." + stats.badge().name().toLowerCase(Locale.ROOT));
        if (ModSettings.tooltipKind && ModSettings.tooltipBadge) out.add(Component.translatable("tooltip.chromastudio.kind_badge", kind, badge).withColor(GREY));
        else if (ModSettings.tooltipKind) out.add(kind.copy().withColor(GREY));
        else if (ModSettings.tooltipBadge) out.add(badge.copy().withColor(GREY));

        if (ModSettings.tooltipDeltaE) {
            double d = target.distribution(scale).emd(stats.distribution(scale), metric);
            String shown = String.format(Locale.ROOT, metric.justNoticeable() < 1 ? "%.3f" : "%.1f", d);
            out.add(Component.translatable("tooltip.chromastudio.delta_e", shown, Component.literal(MatchState.colour().hex()).withColor(MatchState.colour().rgb()))
                    .withColor(GREY));
        }
        return out;
    }

    private static boolean wanted() {
        return ModSettings.tooltipMode == TooltipMode.ALWAYS
                || ModSettings.tooltipMode == TooltipMode.SHIFT && Minecraft.getInstance().hasShiftDown();
    }
}
