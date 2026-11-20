package dev.chromastudio.client.axiom;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.axiom.AxiomBridge.Refused;
import dev.chromastudio.client.axiom.AxiomBridge.Script;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.Recolour;
import dev.chromastudio.core.palette.StoredGradient;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * {@code /chroma axiom} and everything under it. Brigadier merges literals of the same name, so the whole tree is
 * built here, and a new Axiom command is one more child of {@code axiom}. The command is there on every client,
 * NeoForge's included; without Axiom each subcommand answers why it can't run. Like {@link AxiomBridge}, this class
 * names no Axiom class.
 */
final class AxiomCommand {
    private AxiomCommand() {}

    static void install() {
        ChromaCommand.SUBCOMMANDS.add(new ChromaCommand.Subcommand() {
            @Override
            public <S> void attach(LiteralArgumentBuilder<S> root) {
                LiteralArgumentBuilder<S> axiom = LiteralArgumentBuilder.<S>literal("axiom").executes(_ -> usage());
                axiom.then(LiteralArgumentBuilder.<S>literal("block").executes(_ -> block()));
                axiom.then(LiteralArgumentBuilder.<S>literal("recolour").executes(_ -> usage())
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .executes(ctx -> copy(() -> AxiomScripts.recolour(palette(ctx, "palette"), null, Recolour.Mode.SHADING)))));
                axiom.then(LiteralArgumentBuilder.<S>literal("remap").executes(_ -> usage())
                        .then(RequiredArgumentBuilder.<S, String>argument("from", StringArgumentType.string())
                                .then(RequiredArgumentBuilder.<S, String>argument("to", StringArgumentType.greedyString())
                                        .executes(ctx -> copy(() -> AxiomScripts.recolour(palette(ctx, "to"), palette(ctx, "from"), Recolour.Mode.SHADING))))));
                axiom.then(LiteralArgumentBuilder.<S>literal("scatter").executes(_ -> usage())
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .executes(ctx -> copy(() -> AxiomScripts.scatter(palette(ctx, "palette"))))));
                LiteralArgumentBuilder<S> gradient = LiteralArgumentBuilder.<S>literal("gradient").executes(_ -> usage());
                for (AxiomScript.Axis axis : AxiomScript.Axis.values()) {
                    gradient.then(LiteralArgumentBuilder.<S>literal(axis.name().toLowerCase(Locale.ROOT)).executes(_ -> usage())
                            .then(RequiredArgumentBuilder.<S, String>argument("gradient", StringArgumentType.greedyString())
                                    .executes(ctx -> copy(() -> {
                                        StoredGradient g = savedGradient(ctx);
                                        return AxiomScripts.gradient(g.name(), g.blocks().stream().map(StoredGradient.Step::block).toList(), axis);
                                    }))));
                }
                root.then(axiom.then(gradient));
            }
        });
    }

    /** Makes Axiom's active block the Match target, and opens the window on it. */
    private static int block() {
        AxiomBridge.Taken result = AxiomBridge.takeActiveBlock();
        ChromaCommand.say(result.message());
        if (result.taken()) ChromaStudioClient.openWindowSoon("match");
        return result.taken() ? 1 : 0;
    }

    private interface Maker {
        Script make() throws Refused;
    }

    /** Makes a script and copies it for the Script Brush. */
    private static int copy(Maker maker) {
        Optional<Component> why = AxiomBridge.whyNot();
        if (why.isPresent()) {
            ChromaCommand.say(why.get());
            return 0;
        }
        try {
            Script script = maker.make();
            AxiomScripts.copy(script);
            ChromaCommand.say(Component.translatable("command.chromastudio.axiom.copied", script.summary()));
            return 1;
        } catch (Refused r) {
            ChromaCommand.say(r.reason());
            return 0;
        }
    }

    /** How to use the command, or, without Axiom, why it can't be used at all. */
    private static int usage() {
        ChromaCommand.say(AxiomBridge.whyNot().orElse(Component.translatable("command.chromastudio.axiom.usage")));
        return 0;
    }

    /** A palette by its name, as the Palettes tab shows it or as its file has it, ignoring case. */
    private static <S> Palette palette(CommandContext<S> ctx, String argument) throws Refused {
        String name = StringArgumentType.getString(ctx, argument).strip();
        for (Palette p : PaletteStore.get().all()) if (name.equalsIgnoreCase(PaletteStore.get().shownName(p)) || name.equalsIgnoreCase(p.name)) return p;
        throw new Refused(Component.translatable("command.chromastudio.axiom.no_palette", name));
    }

    private static <S> StoredGradient savedGradient(CommandContext<S> ctx) throws Refused {
        String name = StringArgumentType.getString(ctx, "gradient").strip();
        StoredGradient g = GradientStore.get().get(name);
        if (g == null) throw new Refused(Component.translatable("command.chromastudio.axiom.no_gradient", name));
        return g;
    }
}
