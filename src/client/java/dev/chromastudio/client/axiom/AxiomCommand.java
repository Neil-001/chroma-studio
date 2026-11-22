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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class AxiomCommand {
    private AxiomCommand() {}

    static void install() {
        ChromaCommand.SUBCOMMANDS.add(new ChromaCommand.Subcommand() {
            @Override
            public <S> void attach(LiteralArgumentBuilder<S> root) {
                LiteralArgumentBuilder<S> axiom = LiteralArgumentBuilder.<S>literal("axiom").executes(_ -> usage(USAGE));
                axiom.then(LiteralArgumentBuilder.<S>literal("block").executes(_ -> block()));
                axiom.then(LiteralArgumentBuilder.<S>literal("recolour").executes(_ -> usage(List.of("recolour")))
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .executes(ctx -> copy(() -> AxiomScripts.recolour(palette(ctx, "palette"), null, Recolour.Mode.SHADING)))));
                axiom.then(LiteralArgumentBuilder.<S>literal("remap").executes(_ -> usage(List.of("remap")))
                        .then(RequiredArgumentBuilder.<S, String>argument("from", StringArgumentType.string())
                                .then(RequiredArgumentBuilder.<S, String>argument("to", StringArgumentType.greedyString())
                                        .executes(ctx -> copy(() -> AxiomScripts.recolour(palette(ctx, "to"), palette(ctx, "from"), Recolour.Mode.SHADING))))));
                axiom.then(LiteralArgumentBuilder.<S>literal("scatter").executes(_ -> usage(List.of("scatter")))
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .executes(ctx -> copy(() -> AxiomScripts.scatter(palette(ctx, "palette"))))));
                LiteralArgumentBuilder<S> gradient = LiteralArgumentBuilder.<S>literal("gradient").executes(_ -> usage(List.of("gradient")));
                for (AxiomScript.Axis axis : AxiomScript.Axis.values()) {
                    gradient.then(LiteralArgumentBuilder.<S>literal(axis.name().toLowerCase(Locale.ROOT)).executes(_ -> usage(List.of("gradient")))
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

    private static int block() {
        AxiomBridge.Taken result = AxiomBridge.takeActiveBlock();
        ChromaCommand.say(result.message());
        if (result.taken()) ChromaStudioClient.openWindowSoon("match");
        return result.taken() ? 1 : 0;
    }

    private interface Maker {
        Script make() throws Refused;
    }

    private static int copy(Maker maker) {
        Optional<Component> why = AxiomBridge.whyNot();
        if (why.isPresent()) {
            ChromaCommand.say(why.get());
            return 0;
        }
        try {
            Script script = maker.make();
            AxiomScripts.copy(script);
            ChromaCommand.say(copied(script));
            return 1;
        } catch (Refused r) {
            ChromaCommand.say(r.reason());
            return 0;
        }
    }

    private static Component copied(Script script) {
        MutableComponent said = Component.translatable("axiom.chromastudio.copied").append("\n").append(script.scope());
        if (script.warning() != null) said.append("\n").append(script.warning()).append("\n").append(script.nextStep());
        return said;
    }

    private static final List<String> USAGE = List.of("block", "recolour", "remap", "scatter", "gradient");

    private static int usage(List<String> commands) {
        MutableComponent said = Component.empty();
        for (String c : commands) {
            if (c != commands.getFirst()) said.append("\n");
            said.append("/chroma axiom ").append(Component.translatable("command.chromastudio.axiom.usage." + c));
        }
        ChromaCommand.say(AxiomBridge.whyNot().orElse(said));
        return 0;
    }

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
