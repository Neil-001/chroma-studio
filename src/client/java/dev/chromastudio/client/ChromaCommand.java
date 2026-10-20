package dev.chromastudio.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.chromastudio.client.ui.ChromaScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The client-side {@code /chroma} command. Each loader registers {@link #build()} with its own client dispatcher; the
 * command's source type differs between them and nothing here reads it.
 */
public final class ChromaCommand {
    private ChromaCommand() {}

    /** Adds a subcommand, such as {@code capture}, to the root. A literal wins over the colour-or-block argument. */
    public interface Subcommand {
        <S> void attach(LiteralArgumentBuilder<S> root);
    }

    /** Subcommands other tasks add before the player joins a world, which is when the loaders build the command. */
    public static final List<Subcommand> SUBCOMMANDS = new ArrayList<>();

    public static <S> LiteralArgumentBuilder<S> build() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("chroma");
        for (Subcommand sub : SUBCOMMANDS) sub.attach(root);
        return root.then(RequiredArgumentBuilder.<S, String>argument("target", StringArgumentType.greedyString()).executes(ctx -> {
            Object target = parse(StringArgumentType.getString(ctx, "target"),
                    id -> BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)));
            if (target instanceof Component problem) {
                say(problem);
                return 0;
            }
            ChromaScreen.pendingTarget = target;
            ChromaStudioClient.openWindowSoon("match");
            return 1;
        }));
    }

    /** Prints a line in the player's own chat. */
    public static void say(Component message) { Minecraft.getInstance().gui.getChat().addMessage(message); }

    /**
     * What the player asked for: an Integer for a colour, packed 0xRRGGBB; a String for a block, as its full id; or a
     * Component saying what's wrong. A leading {@code #} always means a colour. Without one, a block's id wins over a
     * colour, so a block whose name happens to be six hex letters can still be asked for.
     */
    static Object parse(String text, Predicate<String> blockExists) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) {
            Integer rgb = hex(s.substring(1));
            return rgb != null ? rgb : Component.translatable("command.chromastudio.bad_hex", text.trim());
        }
        String id = s.contains(":") ? s : "minecraft:" + s;
        boolean wellFormed = id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
        if (wellFormed && blockExists.test(id)) return id;
        Integer rgb = hex(s);
        if (rgb != null) return rgb;
        return Component.translatable("command.chromastudio.unknown_block", text.trim());
    }

    /** Six hex digits, or three as shorthand ({@code f80} is {@code ff8800}). Null for anything else. */
    private static Integer hex(String s) {
        if (!s.matches("[0-9a-f]{6}|[0-9a-f]{3}")) return null;
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        return Integer.parseInt(s, 16);
    }
}
