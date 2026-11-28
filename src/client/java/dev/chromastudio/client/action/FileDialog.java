package dev.chromastudio.client.action;

import dev.chromastudio.client.ChromaStudioClient;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class FileDialog {
    private FileDialog() {}

    public static void choose(String filterName, String extensions, Consumer<Path> picked, Runnable failed) {
        Minecraft mc = Minecraft.getInstance();
        Thread thread = new Thread(() -> {
            String path;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (TinyFileDialogs.ntinyfd_openFileDialog(MemoryUtil.memAddress(stack.UTF8("tinyfd_query")),
                        MemoryUtil.NULL, 0, MemoryUtil.NULL, MemoryUtil.NULL, 0) == 0) {
                    ChromaStudioClient.LOGGER.warn("The system has no file dialog to show");
                    mc.execute(failed);
                    return;
                }
                String[] patterns = extensions.split(";");
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String pattern : patterns) filters.put(stack.UTF8("*." + pattern));
                filters.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog(filterName, (CharSequence) null, filters, filterName, false);
            } catch (Throwable t) {
                ChromaStudioClient.LOGGER.warn("Couldn't open a file dialog", t);
                mc.execute(failed);
                return;
            }
            if (path == null) return;
            mc.execute(() -> {
                try {
                    picked.accept(Path.of(path));
                } catch (InvalidPathException e) {
                    ChromaStudioClient.LOGGER.warn("The file dialog gave a path that can't be used: {}", path, e);
                }
            });
        }, "Chroma Studio file dialog");
        thread.setDaemon(true);
        thread.start();
    }
}
