package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
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

    /**
     * The callbacks whose dialogs have answered, to be freed when the next dialog opens. One can't be freed from
     * inside its own call, nor before its dialog answers, however long the player leaves it open.
     */
    private static final List<SDL_DialogFileCallback> ANSWERED = new ArrayList<>();

    /**
     * Opens the dialog and hands the chosen file to {@code picked} on the client thread. Cancelling calls nothing.
     * {@code extensions} are separated by semicolons, without dots. When no dialog could be opened, {@code failed}
     * runs instead, at once or a moment later on the client thread, as the system may only find out after trying.
     */
    public static void choose(String filterName, String extensions, Consumer<Path> picked, Runnable failed) {
        Minecraft mc = Minecraft.getInstance();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            synchronized (ANSWERED) {
                ANSWERED.forEach(SDL_DialogFileCallback::free);
                ANSWERED.clear();
            }
            SDL_DialogFileCallback[] self = new SDL_DialogFileCallback[1];
            SDL_DialogFileCallback callback = self[0] = SDL_DialogFileCallback.create((_, files, _) -> {
                synchronized (ANSWERED) {
                    ANSWERED.add(self[0]);
                }
                // A null list is an error: the system has no dialog to show. An empty one is a cancel.
                if (files == 0) {
                    ChromaStudioClient.LOGGER.warn("The system couldn't show a file dialog: {}", SDLError.SDL_GetError());
                    mc.execute(failed);
                    return;
                }
                long first = MemoryUtil.memGetAddress(files);
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
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Couldn't open a file dialog", t);
            failed.run();
        }
    }
}
