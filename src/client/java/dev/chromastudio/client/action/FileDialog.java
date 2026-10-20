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

/**
 * The system's own dialog for opening a file. It is tinyfd's, which the game brings with it on this version. The
 * dialog holds up the thread that shows it, so it gets its own. Every caller also has a field to type a path into,
 * for systems where no dialog comes up.
 */
public final class FileDialog {
    private FileDialog() {}

    /**
     * Opens the dialog and hands the chosen file to {@code picked} on the client thread. Cancelling calls nothing.
     * {@code extensions} are separated by semicolons, without dots. When no dialog could be opened, {@code failed}
     * runs instead, at once or a moment later on the client thread, as the system may only find out after trying.
     */
    public static void choose(String filterName, String extensions, Consumer<Path> picked, Runnable failed) {
        Minecraft mc = Minecraft.getInstance();
        Thread thread = new Thread(() -> {
            String path;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // Asked with this title, tinyfd shows nothing and says whether it has a window to show; without one
                // it would ask on the console instead, which no player would see.
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
            if (path == null) return;   // cancelled
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
