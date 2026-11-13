package dev.chromastudio.client;

import dev.chromastudio.core.palette.GradientCodec;
import dev.chromastudio.core.palette.StoredGradient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The gradients the player saved from the Gradient tab, all in one file, {@code config/chromastudio/gradients.json},
 * by name with no two alike but for case. The public API reads it from other threads, so every method is synchronized.
 */
public final class GradientStore {
    private static GradientStore instance;

    /** The store over the game's config folder, loaded on first use. */
    public static synchronized GradientStore get() {
        if (instance == null) instance = new GradientStore(ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("gradients.json"));
        return instance;
    }

    private final Path file;
    private final List<StoredGradient> all = new ArrayList<>();
    private boolean loaded;
    /** Whether the file was there but couldn't be read. It is kept under another name before anything is written over it. */
    private boolean unreadable;

    public GradientStore(Path file) { this.file = file; }

    private void load() {
        if (loaded) return;
        loaded = true;
        all.clear();
        if (!Files.exists(file)) return;
        try {
            all.addAll(GradientCodec.fromJson(Files.readString(file)));
        } catch (IOException | RuntimeException e) {
            unreadable = true;
            ChromaStudioClient.LOGGER.warn("Couldn't read the saved gradients in {}", file, e);
        }
        all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
    }

    /** Reads the file afresh. */
    public synchronized void reload() {
        loaded = false;
        load();
    }

    /** The saved gradients, by name. */
    public synchronized List<StoredGradient> all() {
        load();
        return List.copyOf(all);
    }

    /** The gradient saved under a name, whatever its case, or null. */
    public synchronized StoredGradient get(String name) {
        load();
        for (StoredGradient g : all) if (g.name().equalsIgnoreCase(name.strip())) return g;
        return null;
    }

    /**
     * Saves a gradient, in place of one with the same name whatever its case. Returns false when the file couldn't be
     * written; the gradient is in the list either way.
     */
    public synchronized boolean put(StoredGradient g) {
        load();
        all.removeIf(old -> old.name().equalsIgnoreCase(g.name()));
        all.add(g);
        all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return save();
    }

    /** Deletes the gradient saved under a name. Returns false when there is none or the file couldn't be written. */
    public synchronized boolean delete(String name) {
        load();
        return all.removeIf(g -> g.name().equalsIgnoreCase(name.strip())) && save();
    }

    private boolean save() {
        try {
            Files.createDirectories(file.getParent());
            if (unreadable) {
                // A file a later version wrote, or one edited by hand and broken, isn't thrown away.
                Path aside = file.resolveSibling(file.getFileName() + ".unreadable");
                for (int n = 2; Files.exists(aside); n++) aside = file.resolveSibling(file.getFileName() + ".unreadable-" + n);
                Files.move(file, aside);
                ChromaStudioClient.LOGGER.warn("Kept the saved gradients that couldn't be read as {}", aside.getFileName());
                unreadable = false;
            }
            // Atomically, so a crash never leaves half a file.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GradientCodec.toJson(all), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't save the gradients to {}", file, e);
            return false;
        }
    }
}
