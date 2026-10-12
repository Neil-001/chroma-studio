package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The system's own dialog for opening a file. It is SDL's, which the game brings with it; the game doesn't ship
 * tinyfd. Every caller also has a field to type a path into, for systems where no dialog comes up.
 */
public final class FileDialog {
    private FileDialog() {}

    /** Kept while the dialog is open, so the native side still has something to call. */
    private static SDL_DialogFileCallback callback;

    /**
     * Opens the dialog and hands the chosen file to {@code picked} on the client thread. Cancelling calls nothing.
     * {@code extensions} are separated by semicolons, without dots. Returns false when no dialog could be opened.
     */
    public static boolean choose(String filterName, String extensions, Consumer<Path> picked) {
        Minecraft mc = Minecraft.getInstance();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (callback != null) callback.free();
            callback = SDL_DialogFileCallback.create((_, files, _) -> {
                // A null list is an error and an empty one a cancel: either way there's nothing to open.
                long first = files == 0 ? 0 : MemoryUtil.memGetAddress(files);
                if (first == 0) return;
                String path = MemoryUtil.memUTF8(first);
                mc.execute(() -> {
                    try {
                        picked.accept(Path.of(path));
                    } catch (InvalidPathException e) {
                        ChromaStudioClient.LOGGER.warn("The file dialog gave a path that can't be used: {}", path, e);
                    }
                });
            });
            SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.malloc(1, stack);
            filters.get(0).set(stack.UTF8(filterName), stack.UTF8(extensions));
            SDLDialog.SDL_ShowOpenFileDialog(callback, 0, mc.getWindow().handle(), filters, (CharSequence) null, false);
            return true;
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Couldn't open a file dialog", t);
            return false;
        }
    }
}
