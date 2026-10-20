package dev.chromastudio.client.index;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** {@code /chroma debug <block id>}: prints what the index holds for a block, to check it against the game. */
public final class DebugCommand {
    private DebugCommand() {}

    /** Adds {@code debug <block id>} under the {@code /chroma} root. The source type differs by loader and isn't read. */
    public static <S> void attach(LiteralArgumentBuilder<S> root) {
        root.then(LiteralArgumentBuilder.<S>literal("debug")
                .then(RequiredArgumentBuilder.<S, String>argument("block", StringArgumentType.greedyString())
                        .executes(context -> run(StringArgumentType.getString(context, "block")))));
    }

    private static int run(String text) {
        String name = text.trim().toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(name);
        Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) return say(Component.translatable("command.chromastudio.debug.unknown_block", text.trim()));
        if (!BlockIndex.get().ready()) return say(Component.translatable("command.chromastudio.debug.not_ready"));
        List<Entry> entries = BlockIndex.get().statesOf(block.get());
        if (entries.isEmpty()) return say(Component.translatable("command.chromastudio.debug.not_indexed", id.toString()));
        for (Entry e : entries) {
            for (String line : describe(e, TintContext.HERE)) {
                say(Component.literal(line));
                ChromaStudioClient.LOGGER.info("[debug] {}", line);
            }
        }
        return entries.size();
    }

    private static int say(Component message) {
        ChromaCommand.say(message);
        return 0;
    }

    /** An entry as lines of text: its kind and flags, then each face's textures and figures. */
    public static List<String> describe(Entry e, TintContext tint) {
        List<String> lines = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        if (e.fullCube()) flags.add("full cube");
        if (e.solid()) flags.add("solid");
        if (e.opaque()) flags.add("opaque");
        if (e.sameTextureAllRound()) flags.add("same texture all round");
        if (e.tinted()) flags.add("tinted");
        if (e.animated()) flags.add("animated");
        if (e.lightSource()) flags.add("light source");
        if (e.technical()) flags.add("technical");
        if (e.creativeOnly()) flags.add("creative only");
        if (e.blockEntityRendered()) flags.add("drawn by a block entity");
        lines.add(e + ": kind " + e.kind() + ", " + (flags.isEmpty() ? "no flags" : String.join(", ", flags)));
        for (Face f : Entry.SIDES) {
            TextureStats stats = e.face(f, tint);
            int colour = e.tint(f, tint);
            lines.add(String.format(Locale.ROOT, "  %-5s %s: mean %s at 16 (%s), %s at 1, spread %.3f %s, coverage %.2f, tint %s",
                    f.name().toLowerCase(Locale.ROOT), String.join(" + ", e.textures(f)),
                    stats.mean(Scale.T16).hex(), colours(stats.distribution(Scale.T16)), stats.mean(Scale.T1).hex(),
                    stats.spread(), stats.badge(), stats.coverage(),
                    colour == TintContext.NO_TINT ? "none" : Color.ofRgb(colour).hex()));
        }
        return lines;
    }

    /** A distribution's colours with their shares, as {@code #7D7D7D 72% #6B6B6B 28%}. */
    private static String colours(Distribution d) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < d.colours().size(); i++) {
            parts.add(d.colours().get(i).hex() + " " + Math.round(d.weights().get(i) * 100) + "%");
        }
        return String.join(" ", parts);
    }
}
