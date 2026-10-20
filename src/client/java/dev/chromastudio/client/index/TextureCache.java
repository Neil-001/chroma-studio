package dev.chromastudio.client.index;

import dev.chromastudio.client.ChromaStudioClient;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** The textures' frames on disk, so a later start doesn't read every image again. */
final class TextureCache {
    private TextureCache() {}

    // Raise this when the frames are worked out differently, so old files stop being read.
    private static final int FORMAT = 2;
    private static final int MAGIC = 0x43485258;
    private static final String PREFIX = "index-", SUFFIX = ".bin";

    static Path folder(IndexOptions options) {
        if (!options.cacheFolder().isBlank()) return Path.of(options.cacheFolder().strip());
        return ChromaStudioClient.platform.configDir().resolve("chromastudio").resolve("cache");
    }

    /**
     * The file for one set of resource packs and mods. The packs count in their order, as a later one overrides an
     * earlier one. A pack changed in place keeps its file: Refresh reads the images again.
     */
    static Path file(Path folder, Collection<String> packs, Collection<String> mods, IndexOptions options) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String text = "format " + FORMAT + "\naverage " + options.animatedAverage()
                    + "\npacks\n" + String.join("\n", packs) + "\nmods\n" + String.join("\n", mods.stream().sorted().toList());
            String hash = HexFormat.of().formatHex(sha.digest(text.getBytes(StandardCharsets.UTF_8)), 0, 8);
            return folder.resolve(PREFIX + hash + SUFFIX);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The frames in a file by sprite name. Empty when the file is missing or isn't one of ours. */
    static Map<String, Textures.Frame> load(Path file) {
        Map<String, Textures.Frame> frames = new HashMap<>();
        if (!Files.isRegularFile(file)) return frames;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) return frames;
            int count = in.readInt();
            for (int n = 0; n < count; n++) {
                String sprite = in.readUTF();
                boolean animated = in.readBoolean();
                int[] rgb = new int[Textures.SIDE * Textures.SIDE];
                float[] alpha = new float[rgb.length];
                for (int i = 0; i < rgb.length; i++) {
                    rgb[i] = in.readInt();
                    alpha[i] = in.readFloat();
                }
                frames.put(sprite, new Textures.Frame(rgb, alpha, animated));
            }
        } catch (IOException | RuntimeException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't read the index cache {}, so the textures are read again", file.getFileName(), e);
            frames.clear();
        }
        return frames;
    }

    /** Writes the file atomically. */
    static void save(Path file, Map<String, Textures.Frame> frames) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT);
                out.writeInt(frames.size());
                for (Map.Entry<String, Textures.Frame> e : frames.entrySet()) {
                    out.writeUTF(e.getKey());
                    out.writeBoolean(e.getValue().animated());
                    for (int i = 0; i < e.getValue().rgb().length; i++) {
                        out.writeInt(e.getValue().rgb()[i]);
                        out.writeFloat(e.getValue().alpha()[i]);
                    }
                }
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't save the index cache", e);
        }
    }

    /** Deletes the files for other sets of packs and mods: only the current one is ever read. */
    static void prune(Path file) {
        if (!Files.isDirectory(file.getParent())) return;
        try (DirectoryStream<Path> others = Files.newDirectoryStream(file.getParent(), PREFIX + "*" + SUFFIX)) {
            for (Path other : others) if (!other.equals(file)) Files.deleteIfExists(other);
        } catch (IOException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't clear out old index caches", e);
        }
    }
}
