package dev.chromastudio.client;

import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteCodec;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.ShareString;
import dev.chromastudio.core.palette.UnreadablePalette;
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
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class PaletteStore implements PaletteSink {
    public static final List<String> PRESETS = List.of("medieval", "gothic", "rustic_wood", "modern_clean", "desert", "nether", "end",
            "ocean", "cherry", "industrial");
    private static final int MAX_FILE_NAME = 60;

    private static PaletteStore instance;

    public static PaletteStore get() {
        if (instance == null) instance = new PaletteStore(ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("palettes"));
        return instance;
    }

    private final Path dir;
    private final List<Palette> own = new ArrayList<>(), presets = new ArrayList<>();
    private final Map<Palette, Path> files = new IdentityHashMap<>();
    private final Map<Palette, String> presetIds = new IdentityHashMap<>();
    private Palette selected;
    private boolean loaded;

    public PaletteStore(Path dir) { this.dir = dir; }

    public Path dir() { return dir; }

    private void load() {
        if (!loaded) reload();
    }

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

    public List<Palette> own() {
        load();
        return List.copyOf(own);
    }

    public List<Palette> presets() {
        load();
        return List.copyOf(presets);
    }

    public List<Palette> all() {
        load();
        List<Palette> out = new ArrayList<>(own);
        out.addAll(presets);
        return out;
    }

    public String fileName(Palette p) {
        Path file = files.get(p);
        return file == null ? null : file.getFileName().toString();
    }

    public boolean saved(Palette p) { return files.containsKey(p); }

    public String presetId(Palette p) { return presetIds.get(p); }

    public String shownName(Palette p) {
        String preset = presetId(p);
        return preset == null ? p.name : Component.translatableWithFallback("preset.chromastudio." + preset, p.name).getString();
    }

    public Palette selected() {
        load();
        return selected;
    }

    public void select(Palette p) {
        load();
        selected = p;
    }

    public String unique(String name) {
        load();
        String base = name == null || name.isBlank() ? "Palette" : name.strip();
        String candidate = base;
        for (int n = 2; used(candidate); n++) candidate = base + " (" + n + ")";
        return candidate;
    }

    private boolean used(String name) {
        for (Palette p : own) if (p.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    public boolean taken(String name, @Nullable Palette except) {
        load();
        for (Palette p : own) if (p != except && p.name.strip().equalsIgnoreCase(name.strip())) return true;
        return false;
    }

    public Palette create(String name, List<PaletteEntry> entries) {
        Palette p = new Palette(unique(name));
        p.entries.addAll(entries);
        return adopt(p);
    }

    public Optional<Palette> createSaved(String name, List<PaletteEntry> entries) {
        load();
        Palette p = new Palette(unique(name));
        p.entries.addAll(entries);
        try {
            Path file = free(p.name, null);
            write(file, PaletteCodec.toJson(p));
            files.put(p, file);
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't save the palette {}", p.name, e);
            return Optional.empty();
        }
        own.add(p);
        sort();
        selected = p;
        return Optional.of(p);
    }

    public Palette copy(Palette source) {
        Palette p = source.copy();
        p.name = unique(source.name);
        return adopt(p);
    }

    public Palette importJson(String json) { return adoptImported(PaletteCodec.fromJson(json)); }

    public Palette importShare(String share) { return adoptImported(ShareString.decode(share)); }

    public static Component why(UnreadablePalette e) {
        return Component.translatable("palettes.chromastudio.unreadable." + e.reason().name().toLowerCase(Locale.ROOT), e.args().toArray());
    }

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

    private Path free(String name, Path mine) {
        String base = fileBase(name);
        for (int n = 1; ; n++) {
            Path candidate = dir.resolve(base + (n == 1 ? "" : "-" + n) + ".json");
            if (candidate.equals(mine) || !Files.exists(candidate)) return candidate;
        }
    }

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

    private void write(Path file, String json) throws IOException {
        Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

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
