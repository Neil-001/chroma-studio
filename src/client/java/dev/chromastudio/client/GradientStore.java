package dev.chromastudio.client;

import dev.chromastudio.core.palette.GradientCodec;
import dev.chromastudio.core.palette.StoredGradient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GradientStore {
    private static final Comparator<StoredGradient> BY_NAME = (a, b) -> a.name().compareToIgnoreCase(b.name());
    private static GradientStore instance;

    public static synchronized GradientStore get() {
        if (instance == null) instance = new GradientStore(ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("gradients.json"));
        return instance;
    }

    private final Path file;
    private final List<StoredGradient> all = new ArrayList<>();
    private boolean loaded;
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
        all.sort(BY_NAME);
    }

    public synchronized void reload() {
        loaded = false;
        load();
    }

    public synchronized List<StoredGradient> all() {
        load();
        return List.copyOf(all);
    }

    public synchronized StoredGradient get(String name) {
        load();
        for (StoredGradient g : all) if (g.name().equalsIgnoreCase(name.strip())) return g;
        return null;
    }

    public synchronized boolean put(StoredGradient g) {
        load();
        all.removeIf(old -> old.name().equalsIgnoreCase(g.name()));
        all.add(g);
        all.sort(BY_NAME);
        return save();
    }

    public synchronized boolean delete(String name) {
        load();
        return all.removeIf(g -> g.name().equalsIgnoreCase(name.strip())) && save();
    }

    private boolean save() {
        try {
            Files.createDirectories(file.getParent());
            if (unreadable) {
                Path aside = file.resolveSibling(file.getFileName() + ".unreadable");
                for (int n = 2; Files.exists(aside); n++) aside = file.resolveSibling(file.getFileName() + ".unreadable-" + n);
                Files.move(file, aside);
                ChromaStudioClient.LOGGER.warn("Kept the saved gradients that couldn't be read as {}", aside.getFileName());
                unreadable = false;
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GradientCodec.toJson(all), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't save the gradients to {}", file, e);
            return false;
        }
    }
}
