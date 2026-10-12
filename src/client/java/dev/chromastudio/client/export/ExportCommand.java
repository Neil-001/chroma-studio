package dev.chromastudio.client.export;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.export.SchematicExporter.Format;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.export.LayoutOptions;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.ShareString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /chroma export <share string> [layout] [format] [place]} writes a palette as a schematic, and
 * {@code /chroma place <file name>} hands one of the export folder's files to Litematica.
 */
public final class ExportCommand {
    private ExportCommand() {}

    private static final String PLACE = "place";

    /** Adds both subcommands to {@code /chroma}. Call once, before the player joins a world. */
    public static void register() {
        ChromaCommand.SUBCOMMANDS.add(new ChromaCommand.Subcommand() {
            @Override
            public <S> void attach(LiteralArgumentBuilder<S> root) {
                root.then(LiteralArgumentBuilder.<S>literal("export")
                        .executes(_ -> { ChromaCommand.say(Component.translatable("command.chromastudio.export.usage")); return 0; })
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .suggests((_, builder) -> suggestOptions(builder))
                                .executes(ctx -> export(StringArgumentType.getString(ctx, "palette")))));
                root.then(LiteralArgumentBuilder.<S>literal(PLACE)
                        .executes(_ -> { ChromaCommand.say(Component.translatable("command.chromastudio.place.usage")); return 0; })
                        .then(RequiredArgumentBuilder.<S, String>argument("file", StringArgumentType.greedyString())
                                .suggests((_, builder) -> suggestFiles(builder))
                                .executes(ctx -> place(StringArgumentType.getString(ctx, "file")))));
            }
        });
    }

    /** What {@code /chroma export} was asked for. A null layout or format means the settings' own. */
    record Request(String share, Layout layout, Format format, boolean place) {}

    /**
     * Splits the command's text into the share string and the words after it, or says what's wrong. A palette's
     * name may hold spaces, so the words are taken off the end for as long as they are a layout, a format or
     * {@code place}, and what is left is the share string. One whose name ends in such a word goes in double
     * quotes, and then every word after the closing quote has to be one of those.
     */
    static Object parse(String text) {
        String rest = text.strip();
        String quoted = null;
        if (rest.startsWith("\"")) {
            int close = rest.indexOf('"', 1);
            if (close < 0) return Component.translatable("command.chromastudio.export.unclosed");
            quoted = rest.substring(1, close);
            rest = rest.substring(close + 1).strip();
        }
        List<String> words = new ArrayList<>(rest.isEmpty() ? List.of() : List.of(rest.split("\\s+")));
        Layout layout = null;
        Format format = null;
        boolean place = false;
        while (!words.isEmpty()) {
            String word = words.get(words.size() - 1).toLowerCase(Locale.ROOT);
            Layout asLayout = layout(word);
            Format asFormat = format(word);
            if (asLayout != null && layout == null) layout = asLayout;
            else if (asFormat != null && format == null) format = asFormat;
            else if (word.equals(PLACE) && !place) place = true;
            else break;
            words.remove(words.size() - 1);
        }
        if (quoted != null && !words.isEmpty()) return Component.translatable("command.chromastudio.export.unknown_option", String.join(" ", words));
        String share = quoted != null ? quoted : rest.substring(0, endOf(rest, words.size()));
        if (share.isBlank()) return Component.translatable("command.chromastudio.export.usage");
        return new Request(share, layout, format, place);
    }

    /** Where the first {@code count} words of {@code text} end, keeping the spaces between them as typed. */
    private static int endOf(String text, int count) {
        int at = 0;
        for (int i = 0; i < count; i++) {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
            while (at < text.length() && !Character.isWhitespace(text.charAt(at))) at++;
        }
        return at;
    }

    /** A layout by its name with underscores or hyphens, such as {@code patch_grid} or {@code patch-grid}. */
    private static Layout layout(String word) {
        for (Layout l : Layout.values()) if (l.name().equalsIgnoreCase(word.replace('-', '_'))) return l;
        return null;
    }

    private static Format format(String word) {
        for (Format f : Format.values()) if (f.extension.equals(word)) return f;
        return null;
    }

    private static int export(String text) {
        Object parsed = parse(text);
        if (parsed instanceof Component problem) {
            ChromaCommand.say(problem);
            return 0;
        }
        Request request = (Request) parsed;
        Palette palette;
        try {
            palette = ShareString.decode(request.share());
        } catch (IllegalArgumentException e) {
            ChromaCommand.say(Component.translatable("command.chromastudio.export.bad_share", e.getMessage()));
            return 0;
        }
        Layout layout = request.layout() != null ? request.layout() : Layout.valueOf(ModSettings.defaultLayout.name());
        // A new seed each time, so asking again rerolls a diorama layout.
        LayoutOptions options = LayoutOptions.of(layout, ModSettings.patchSize, ModSettings.exportGap, System.nanoTime());
        SchematicExporter.Result result;
        try {
            result = SchematicExporter.export(palette, options, request.format() != null ? request.format() : Format.LITEMATIC);
        } catch (SchematicExporter.NoBlocksException e) {
            ChromaCommand.say(Component.translatable("command.chromastudio.export.empty"));
            saySkipped(e.skippedIds());
            return 0;
        } catch (IOException e) {
            ChromaCommand.say(Component.translatable("command.chromastudio.export.failed", String.valueOf(e.getMessage())));
            return 0;
        }
        ChromaCommand.say(Component.translatable("command.chromastudio.export.done", result.file().toString()));
        saySkipped(result.skippedIds());
        if (request.place()) placeFile(result.file(), palette.name == null || palette.name.isBlank() ? baseName(result.file()) : palette.name);
        return 1;
    }

    private static void saySkipped(List<String> ids) {
        if (!ids.isEmpty()) ChromaCommand.say(Component.translatable("command.chromastudio.export.skipped", String.join(", ", ids)));
    }

    private static int place(String name) {
        Path dir, file;
        try {
            dir = SchematicExporter.directory();
            file = find(dir, name.strip());
        } catch (IOException | RuntimeException e) {
            ChromaCommand.say(Component.translatable("command.chromastudio.export.failed", String.valueOf(e.getMessage())));
            return 0;
        }
        if (file == null) {
            ChromaCommand.say(Component.translatable("command.chromastudio.place.no_file", name.strip(), dir.toString()));
            return 0;
        }
        return placeFile(file, baseName(file)) ? 1 : 0;
    }

    /** The named file in the export folder, with or without its extension. Null when there's none, or the name leads out of the folder. */
    static Path find(Path dir, String name) {
        if (name.isEmpty()) return null;
        for (String suffix : List.of("", "." + Format.LITEMATIC.extension, "." + Format.NBT.extension)) {
            Path file = dir.resolve(name + suffix).normalize();
            if (file.startsWith(dir.normalize()) && Files.isRegularFile(file)) return file;
        }
        return null;
    }

    private static boolean placeFile(Path file, String name) {
        BlockPos origin = LitematicaBridge.originFor(ModSettings.litematicaPlacement);
        Optional<Component> problem = LitematicaBridge.place(file, origin, name);
        ChromaCommand.say(problem.orElseGet(() -> Component.translatable("command.chromastudio.place.done", name, origin.getX(), origin.getY(), origin.getZ())));
        return problem.isEmpty();
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Offers the layouts, the formats and {@code place} for the word being typed, once the share string is in. */
    private static CompletableFuture<Suggestions> suggestOptions(SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        int space = Math.max(typed.lastIndexOf(' '), typed.lastIndexOf('\t'));
        if (space < 0) return builder.buildFuture();
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + space + 1);
        String start = word.getRemainingLowerCase();
        Stream.concat(Stream.of(Layout.values()).map(l -> l.name().toLowerCase(Locale.ROOT)), Stream.concat(Stream.of(Format.values()).map(f -> f.extension), Stream.of(PLACE)))
                .filter(option -> option.startsWith(start)).forEach(word::suggest);
        return word.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFiles(SuggestionsBuilder builder) {
        String start = builder.getRemainingLowerCase();
        try (Stream<Path> files = Files.list(SchematicExporter.directory())) {
            files.filter(Files::isRegularFile).map(f -> f.getFileName().toString())
                    .filter(n -> format(n.substring(n.lastIndexOf('.') + 1)) != null && n.toLowerCase(Locale.ROOT).startsWith(start))
                    .sorted().forEach(builder::suggest);
        } catch (IOException | RuntimeException _) {
            // No folder yet, or one that can't be read: there's nothing to offer.
        }
        return builder.buildFuture();
    }
}
