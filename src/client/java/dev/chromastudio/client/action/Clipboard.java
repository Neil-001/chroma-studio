package dev.chromastudio.client.action;

import dev.chromastudio.core.export.ClipboardSize;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The one way the mod puts text on the clipboard. Text past {@link ClipboardSize#MAX_BYTES} would crash the game,
 * so it isn't copied, and the caller shows the player why.
 */
public final class Clipboard {
    private Clipboard() {}

    /** Copies the text, or says why it can't be: it's too large. */
    public static Optional<Component> copy(String text) {
        int bytes = ClipboardSize.bytes(text);
        if (bytes > ClipboardSize.MAX_BYTES)
            return Optional.of(Component.translatable("clipboard.chromastudio.too_large",
                    ClipboardSize.kilobytes(bytes), ClipboardSize.MAX_BYTES / 1024));
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        return Optional.empty();
    }

    /** Shows why something wasn't copied, where there's no status line of its own, such as a text field. */
    public static void toast(Component why) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("clipboard.chromastudio.not_copied"), why);
    }
}
