package dev.chromastudio.client.action;

import dev.chromastudio.core.export.ClipboardSize;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class Clipboard {
    private Clipboard() {}

    public static Optional<Component> copy(String text) {
        int bytes = ClipboardSize.bytes(text);
        if (bytes > ClipboardSize.MAX_BYTES)
            return Optional.of(Component.translatable("clipboard.chromastudio.too_large",
                    ClipboardSize.kilobytes(bytes), ClipboardSize.MAX_BYTES / 1024));
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        return Optional.empty();
    }

    public static void toast(Component why) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("clipboard.chromastudio.not_copied"), why);
    }
}
