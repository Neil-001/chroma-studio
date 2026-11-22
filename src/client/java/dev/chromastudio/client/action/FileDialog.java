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

public final class FileDialog {
    private FileDialog() {}

    private static final List<SDL_DialogFileCallback> ANSWERED = new ArrayList<>();

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
            try {
                SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.malloc(1, stack);
                filters.get(0).set(stack.UTF8(filterName), stack.UTF8(extensions));
                SDLDialog.SDL_ShowOpenFileDialog(callback, 0, mc.getWindow().handle(), filters, (CharSequence) null, false);
            } catch (Throwable t) {
                synchronized (ANSWERED) {
                    ANSWERED.add(callback);
                }
                throw t;
            }
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Couldn't open a file dialog", t);
            failed.run();
        }
    }
}
