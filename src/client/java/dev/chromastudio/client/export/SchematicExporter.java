package dev.chromastudio.client.export;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.core.export.Arrangement;
import dev.chromastudio.core.export.LayoutOptions;
import dev.chromastudio.core.export.LitematicBits;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.ShareString;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public final class SchematicExporter {
    private SchematicExporter() {}

    public enum Format {
        LITEMATIC("litematic"), NBT("nbt");

        public final String extension;

        Format(String extension) { this.extension = extension; }
    }

    public record Result(Path file, List<String> skippedIds) {}

    public static final class NoBlocksException extends IOException {
        private static final long serialVersionUID = 1L;

        private final ArrayList<String> skippedIds;

        NoBlocksException(List<String> skippedIds) {
            super("The palette has no blocks this game knows");
            this.skippedIds = new ArrayList<>(skippedIds);
        }

        public List<String> skippedIds() { return List.copyOf(skippedIds); }
    }

    private static final int SCHEMATIC_VERSION = 6, SUB_VERSION = 1;
    private static final String REGION = "Main";
    private static final int MAX_NAME_LENGTH = 60;

    public static Result export(Palette p, LayoutOptions o) throws IOException {
        return export(p, o, Format.LITEMATIC);
    }

    public static Result export(Palette p, LayoutOptions o, Format format) throws IOException {
        return export(p, o, format, directory(), Minecraft.getInstance().getUser().getName());
    }

    public static Result export(Palette p, LayoutOptions o, Path dir, String author) throws IOException {
        return export(p, o, Format.LITEMATIC, dir, author);
    }

    public static Result export(Palette p, LayoutOptions o, Format format, Path dir, String author) throws IOException {
        List<BlockState> states = new ArrayList<>();
        List<PaletteEntry> kept = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (PaletteEntry e : p.entries) {
            BlockState state = stateFor(e);
            if (state == null) {
                skipped.add(e.idWithProperties());
                continue;
            }
            states.add(state);
            kept.add(e);
        }
        if (states.isEmpty()) throw new NoBlocksException(skipped);
        Arrangement a = Arrangement.of(kept.stream().map(PaletteEntry::weight).toList(), o);
        for (int i = a.placed(); i < states.size(); i++) skipped.add(kept.get(i).idWithProperties());

        String name = p.name == null || p.name.isBlank() ? fileName(p, o) : p.name;
        CompoundTag root = format == Format.NBT ? structure(a, states) : litematic(a, states, name, author, ShareString.encode(p));
        return new Result(write(root, dir, fileName(p, o), format.extension), List.copyOf(skipped));
    }

    public static Path directory() throws IOException {
        String folder = ModSettings.exportFolder.strip();
        if (folder.isEmpty()) return LitematicaBridge.schematicsDir();
        try {
            return ChromaStudioClient.platform.gameDir().resolve(folder).normalize();
        } catch (InvalidPathException e) {
            throw new IOException("\"" + folder + "\" isn't a folder's path", e);
        }
    }

    public static BlockState stateFor(PaletteEntry e) {
        Identifier id = Identifier.tryParse(e.blockId());
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) return null;
        BlockState state = block.defaultBlockState();
        if (e.properties().isBlank()) return state;
        for (String pair : e.properties().split(",")) {
            int eq = pair.indexOf('=');
            Property<?> property = eq < 0 ? null : block.getStateDefinition().getProperty(pair.substring(0, eq).strip());
            state = property == null ? null : withNamed(state, property, pair.substring(eq + 1).strip());
            if (state == null) return null;
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withNamed(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(null);
    }

    static String fileName(Palette p, LayoutOptions o) {
        StringBuilder safe = new StringBuilder();
        String name = p.name == null ? "" : p.name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < name.length() && safe.length() < MAX_NAME_LENGTH; ) {
            int c = name.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isLetterOrDigit(c)) safe.appendCodePoint(c);
            else if (!safe.isEmpty() && safe.charAt(safe.length() - 1) != '-') safe.append('-');
        }
        while (!safe.isEmpty() && safe.charAt(safe.length() - 1) == '-') safe.setLength(safe.length() - 1);
        if (safe.isEmpty()) safe.append("palette");
        return "chroma-" + safe + "-" + o.layout().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Path write(CompoundTag root, Path dir, String base, String extension) throws IOException {
        Files.createDirectories(dir);
        Path file = null;
        for (int n = 1; file == null; n++) file = claim(dir.resolve(base + (n == 1 ? "" : "-" + n) + "." + extension));
        Path temp = null;
        try {
            temp = writeNew(root, dir, base);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            if (temp != null) Files.deleteIfExists(temp);
            Files.deleteIfExists(file);
            throw e;
        }
        return file;
    }

    private static @Nullable Path claim(Path candidate) throws IOException {
        try {
            return Files.createFile(candidate);
        } catch (FileAlreadyExistsException _) {
            return null;
        }
    }

    private static Path writeNew(CompoundTag root, Path dir, String base) throws IOException {
        Path temp = null;
        while (temp == null) temp = writeNew(root, dir.resolve(base + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp"));
        return temp;
    }

    private static @Nullable Path writeNew(CompoundTag root, Path temp) throws IOException {
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            NbtIo.writeCompressed(root, out);
            return temp;
        } catch (FileAlreadyExistsException _) {
            return null;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static CompoundTag litematic(Arrangement a, List<BlockState> states, String name, String author, String description) {
        int sx = a.sizeX(), sy = a.sizeY(), sz = a.sizeZ();
        List<BlockState> palette = new ArrayList<>(List.of(Blocks.AIR.defaultBlockState()));
        Map<BlockState, Integer> index = new HashMap<>(Map.of(palette.get(0), 0));
        int[] values = new int[sx * sy * sz];
        int total = 0;
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    int entry = a.at(x, y, z);
                    if (entry < 0) continue;
                    BlockState state = states.get(entry);
                    values[(y * sz + z) * sx + x] = index.computeIfAbsent(state, s -> { palette.add(s); return palette.size() - 1; });
                    if (!state.isAir()) total++;
                }

        ListTag paletteNbt = new ListTag();
        for (BlockState state : palette) paletteNbt.add(NbtUtils.writeBlockState(state));

        CompoundTag region = new CompoundTag();
        region.put("Position", vec(0, 0, 0));
        region.put("Size", vec(sx, sy, sz));
        region.put("BlockStatePalette", paletteNbt);
        region.put("BlockStates", new LongArrayTag(LitematicBits.pack(values, LitematicBits.bitsFor(palette.size()))));
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        region.put("PendingFluidTicks", new ListTag());

        long now = System.currentTimeMillis();
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", name);
        meta.putString("Author", author);
        meta.putString("Description", description);
        meta.putInt("RegionCount", 1);
        meta.putInt("TotalVolume", values.length);
        meta.putInt("TotalBlocks", total);
        meta.putLong("TimeCreated", now);
        meta.putLong("TimeModified", now);
        meta.put("EnclosingSize", vec(sx, sy, sz));

        CompoundTag regions = new CompoundTag();
        regions.put(REGION, region);

        CompoundTag root = new CompoundTag();
        root.putInt("MinecraftDataVersion", dataVersion());
        root.putInt("Version", SCHEMATIC_VERSION);
        root.putInt("SubVersion", SUB_VERSION);
        root.put("Metadata", meta);
        root.put("Regions", regions);
        return root;
    }

    private static CompoundTag structure(Arrangement a, List<BlockState> states) {
        ListTag palette = new ListTag(), blocks = new ListTag();
        Map<BlockState, Integer> index = new HashMap<>();
        for (int y = 0; y < a.sizeY(); y++)
            for (int z = 0; z < a.sizeZ(); z++)
                for (int x = 0; x < a.sizeX(); x++) {
                    int entry = a.at(x, y, z);
                    if (entry < 0) continue;
                    int state = index.computeIfAbsent(states.get(entry), s -> { palette.add(NbtUtils.writeBlockState(s)); return palette.size() - 1; });
                    CompoundTag block = new CompoundTag();
                    block.put("pos", ints(x, y, z));
                    block.putInt("state", state);
                    blocks.add(block);
                }

        CompoundTag root = new CompoundTag();
        root.put("size", ints(a.sizeX(), a.sizeY(), a.sizeZ()));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", dataVersion());
        return root;
    }

    private static int dataVersion() { return SharedConstants.getCurrentVersion().dataVersion().version(); }

    private static CompoundTag vec(int x, int y, int z) {
        CompoundTag c = new CompoundTag();
        c.putInt("x", x); c.putInt("y", y); c.putInt("z", z);
        return c;
    }

    private static ListTag ints(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x)); list.add(IntTag.valueOf(y)); list.add(IntTag.valueOf(z));
        return list;
    }
}
