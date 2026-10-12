package dev.chromastudio.client.ui;

import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.TooltipMode;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The mod's lines in item tooltips. Each loader calls {@link #append} from its item tooltip event. */
public final class Tooltips {
    private Tooltips() {}

    /** Adds the mod's lines for {@code stack} to {@code lines}, when the tooltip setting says to show them now. */
    public static void append(ItemStack stack, List<Component> lines) {
        if (!wanted()) return;
        // The match task adds its lines here: swatch, hex, kind, badge and ΔE to the current target, each behind its
        // ModSettings.tooltip* switch.
    }

    private static boolean wanted() {
        return ModSettings.tooltipMode == TooltipMode.ALWAYS
                || ModSettings.tooltipMode == TooltipMode.SHIFT && Minecraft.getInstance().hasShiftDown();
    }
}
