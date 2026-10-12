package dev.chromastudio.client;

import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteCodec;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.ShareString;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The player's palettes, one JSON file each in {@code config/chromastudio/palettes/}, and the bundled presets, which
 * are read-only. It also keeps which palette is selected, and is the {@link PaletteSink} other tabs add blocks through.
 */
public final class PaletteStore implements PaletteSink {
    /** The bundled presets' file names under {@code assets/chromastudio/presets/}, in the order they're listed. */
    public static final List<String> PRESETS = List.of("medieval", "gothic", "rustic_wood", "modern_clean", "desert", "nether", "end",
            "ocean", "cherry", "industrial");
    private static final int MAX_FILE_NAME = 60;

    private static PaletteStore instance;

    /** The store over the game's config folder, loaded on first use. */
    public static PaletteStore get() {
        if (instance == null) instance = new PaletteStore(ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("palettes"));
        return instance;
    }

    private final Path dir;
    private final List<Palette> own = new ArrayList<>(), presets = new ArrayList<>();
    /** Where each of the player's palettes is saved. One that couldn't be saved yet has no file. */
    private final Map<Palette, Path> files = new IdentityHashMap<>();
    private final Map<Palette, String> presetIds = new IdentityHashMap<>();
    private Palette selected;
    private boolean loaded;

    public PaletteStore(Path dir) { this.dir = dir; }

    public Path dir() { return dir; }

    private void load() {
        if (!loaded) reload();
    }

    /** Reads the folder and the presets afresh. The selection is kept by name. */
    public void reload() {
        loaded = true;
        String was = selected == null ? null : selected.name;
        boolean wasPreset = selected != null && selected.preset;
        own.clear();
        presets.clear();
        files.clear();
        presetIds.clear();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> listed = Files.list(dir)) {
                // Files that can't be read are skipped and left alone: a new palette never takes their names.
                for (Path file : listed.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    try {
                        Palette p = PaletteCodec.fromJson(Files.readString(file));
                        if (p.name == null || p.name.isBlank()) p.name = baseName(file);
                        own.add(p);
                        files.put(p, file);
                    } catch (IOException | RuntimeException e) {
                        ChromaStudioClient.LOGGER.warn("Couldn't read the palette {}", file.getFileName(), e);
                    }
                }
            } catch (IOException e) {
                ChromaStudioClient.LOGGER.warn("Couldn't list the palettes folder", e);
            }
        }
        sort();
        for (String id : PRESETS) {
            try (InputStream in = PaletteStore.class.getResourceAsStream("/assets/chromastudio/presets/" + id + ".json")) {
                if (in == null) throw new IOException("It's missing from the jar");
                Palette p = PaletteCodec.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                p.preset = true;
                presets.add(p);
                presetIds.put(p, id);
            } catch (IOException | RuntimeException e) {
                ChromaStudioClient.LOGGER.warn("Couldn't read the preset {}", id, e);
            }
        }
        selected = null;
        if (was != null) for (Palette p : wasPreset ? presets : own) if (p.name.equals(was)) selected = p;
    }

    private void sort() { own.sort((a, b) -> a.name.compareToIgnoreCase(b.name)); }

    /** The player's own palettes, by name. */
    public List<Palette> own() {
        load();
        return List.copyOf(own);
    }

    public List<Palette> presets() {
        load();
        return List.copyOf(presets);
    }

    /** The player's own palettes, then the presets. */
    public List<Palette> all() {
        load();
        List<Palette> out = new ArrayList<>(own);
        out.addAll(presets);
        return out;
    }

    /** Whether a palette of the player's own has reached its file. False after a save that failed. */
    public boolean saved(Palette p) { return files.containsKey(p); }

    /** A preset's file name, which is also the end of its translation key, or null for a palette that isn't one. */
    public String presetId(Palette p) { return presetIds.get(p); }

    /** The palette the Palettes tab shows, or null. */
    public Palette selected() {
        load();
        return selected;
    }

    public void select(Palette p) {
        load();
        selected = p;
    }

    /** {@code name}, or "name (2)", "name (3)" and so on: whichever none of the player's palettes has yet. */
    public String unique(String name) {
        load();
        String base = name == null || name.isBlank() ? "Palette" : name.strip();
        String candidate = base;
        for (int n = 2; taken(candidate); n++) candidate = base + " (" + n + ")";
        return candidate;
    }

    private boolean taken(String name) {
        for (Palette p : own) if (p.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Makes a palette of the player's own under a name that's free, saves it and selects it. */
    public Palette create(String name, List<PaletteEntry> entries) {
        Palette p = new Palette(unique(name));
        p.entries.addAll(entries);
        return adopt(p);
    }

    /** A preset, or any palette, as a new one of the player's own. */
    public Palette copy(Palette source) {
        Palette p = source.copy();
        p.name = unique(source.name);
        return adopt(p);
    }

    /** Reads a palette from its JSON, as a new one. Throws IllegalArgumentException, saying why, when it can't be read. */
    public Palette importJson(String json) { return adoptImported(PaletteCodec.fromJson(json)); }

    /** Reads a palette from a share string, as a new one. Throws IllegalArgumentException, saying why, when it can't be read. */
    public Palette importShare(String share) { return adoptImported(ShareString.decode(share)); }

    private Palette adoptImported(Palette p) {
        p.preset = false;
        p.name = unique(p.name);
        return adopt(p);
    }

    private Palette adopt(Palette p) {
        own.add(p);
        sort();
        save(p);
        selected = p;
        return p;
    }

    /**
     * Writes a palette to its file, or to a new one named after it the first time. Returns false for a preset, or
     * when the file couldn't be written; the palette stays in the list either way.
     */
    public boolean save(Palette p) {
        load();
        if (p.preset || !own.contains(p)) return false;
        try {
            Path file = files.get(p);
            if (file == null) file = free(p.name, null);
            write(file, PaletteCodec.toJson(p));
            files.put(p, file);
            return true;
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't save the palette {}", p.name, e);
            return false;
        }
    }

    /** Gives a palette a new name and moves its file to match. Returns false, changing nothing, for a preset or a blank name. */
    public boolean rename(Palette p, String name) {
        load();
        if (p.preset || !own.contains(p) || name == null || name.isBlank()) return false;
        Path old = files.get(p);
        p.name = name.strip();
        sort();
        try {
            Path file = free(p.name, old);
            write(file, PaletteCodec.toJson(p));
            files.put(p, file);
            if (old != null && !old.equals(file)) Files.deleteIfExists(old);
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't move the palette {} to its new name", p.name, e);
        }
        return true;
    }

    /** Takes a palette off the list and deletes its file. Returns false for a preset. */
    public boolean delete(Palette p) {
        load();
        if (p.preset || !own.remove(p)) return false;
        if (selected == p) selected = null;
        Path file = files.remove(p);
        try {
            if (file != null) Files.deleteIfExists(file);
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't delete {}", file.getFileName(), e);
        }
        return true;
    }

    /** The first of {@code base.json}, {@code base-2.json} and so on that no file has, or that is {@code mine}. */
    private Path free(String name, Path mine) {
        String base = fileBase(name);
        for (int n = 1; ; n++) {
            Path candidate = dir.resolve(base + (n == 1 ? "" : "-" + n) + ".json");
            if (candidate.equals(mine) || !Files.exists(candidate)) return candidate;
        }
    }

    /** A name as a file name that's safe anywhere: lower case, with everything but letters and digits turned into single hyphens. */
    static String fileBase(String name) {
        StringBuilder safe = new StringBuilder();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && safe.length() < MAX_FILE_NAME; ) {
            int c = lower.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isLetterOrDigit(c)) safe.appendCodePoint(c);
            else if (!safe.isEmpty() && safe.charAt(safe.length() - 1) != '-') safe.append('-');
        }
        while (!safe.isEmpty() && safe.charAt(safe.length() - 1) == '-') safe.setLength(safe.length() - 1);
        return safe.isEmpty() ? "palette" : safe.toString();
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    /** Writes atomically: to a temporary file first, then over the real one, so a crash never leaves half a palette. */
    private void write(Path file, String json) throws IOException {
        Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException _) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---- PaletteSink ----

    /** Adds a block to the selected palette. With none selected, or a preset, which can't be changed, it starts a new one. */
    @Override
    public void add(String blockId, String properties) {
        load();
        PaletteEntry entry = new PaletteEntry(blockId, properties);
        if (selected == null || selected.preset) {
            create("Palette", List.of(entry));
            return;
        }
        selected.entries.add(entry);
        save(selected);
    }

    @Override
    public boolean createPalette(String name, List<PaletteEntry> entries) {
        create(name, entries);
        return true;
    }
}
