package dev.chromastudio.client.action;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.GradientStore;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Kind;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.FlatNearest;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.StoredGradient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Makes Lua scripts for Axiom's Script Brush from palettes and gradients, and copies them, through the Palettes and
 * Gradient tabs and {@code /chroma axiom}. The text comes from {@link AxiomScript}; this class picks the blocks.
 */
public final class AxiomScripts {
    private AxiomScripts() {}

    /** The kinds a recolour changes. Each block is matched only among blocks of its own kind, so a stair stays a stair. */
    private static final Set<Kind> RECOLOURED = Set.of(Kind.FULL, Kind.SLAB, Kind.STAIRS, Kind.WALL, Kind.FENCE, Kind.FENCE_GATE,
            Kind.TRAPDOOR, Kind.DOOR, Kind.PANE, Kind.CARPET, Kind.GLASS, Kind.LIGHT);

    /**
     * The longest script, in bytes of UTF-8, that is copied. The game hands the clipboard its text through LWJGL's
     * stack of 64 KiB, and text longer than what's left of it crashes the game, so a longer script is refused.
     */
    static final int CLIPBOARD_BYTES = 40_000;

    /** A script and what to tell the player about it. */
    public record Made(String script, Component summary) {}

    /**
     * A recolour into a palette: every block the brush may find, or only those of {@code from}, matched to the
     * palette's blocks of the same kind, which includes the stairs, slabs and walls of the palette's materials.
     * Blocks with a block entity, such as chests and signs, and blocks whose kind the palette has none of are left
     * alone. Refused, with the reason, when nothing would change.
     */
    public static Made recolour(Palette to, @Nullable Palette from) throws Refused {
        if (!BlockIndex.get().ready()) throw new Refused(Component.translatable("palettes.chromastudio.index_building"));
        BlockIndex index = BlockIndex.get();
        Face face = PaletteBlocks.face();
        TintContext tint = PaletteBlocks.tint();

        Map<Kind, Map<BlockState, PaletteEntry>> targets = new EnumMap<>(Kind.class);
        List<Entry> own = new ArrayList<>();
        for (PaletteEntry pe : to.entries) {
            BlockState state = PaletteBlocks.state(pe);
            Entry e = state == null ? null : PaletteBlocks.entry(state);
            if (e == null) continue;
            own.add(e);
            targets.computeIfAbsent(e.kind(), _ -> new LinkedHashMap<>()).putIfAbsent(state, pe);
        }
        if (own.isEmpty()) throw new Refused(Component.translatable("axiom.chromastudio.no_blocks"));
        // The palette's materials lend it their other shapes: a palette of stone bricks recolours stairs into stone
        // brick stairs. Full blocks come from the palette alone where it has any, so planks don't turn into logs.
        for (Entry e : own) {
            List<Entry> set = index.materialSet(e);
            for (Entry m : set) {
                if (m.kind() == Kind.FULL) continue;
                targets.computeIfAbsent(m.kind(), _ -> new LinkedHashMap<>()).putIfAbsent(m.state(), PaletteBlocks.of(m));
            }
            if (!targets.containsKey(Kind.FULL) && !set.isEmpty() && set.getFirst().kind() == Kind.FULL) {
                targets.computeIfAbsent(Kind.FULL, _ -> new LinkedHashMap<>()).putIfAbsent(set.getFirst().state(), PaletteBlocks.of(set.getFirst()));
            }
        }
        Map<Kind, FlatNearest> nearest = new EnumMap<>(Kind.class);
        Map<Kind, List<BlockState>> order = new EnumMap<>(Kind.class);
        targets.forEach((kind, blocks) -> {
            List<Distribution> looks = new ArrayList<>();
            for (BlockState s : blocks.keySet()) looks.add(PaletteBlocks.entry(s).face(face, s, tint).distribution(Scale.T8));
            nearest.put(kind, new FlatNearest(looks));
            order.put(kind, List.copyOf(blocks.keySet()));
        });

        List<AxiomScript.Remap> rows = new ArrayList<>();
        int unshaped = 0;
        for (Entry source : sources(from)) {
            Block block = source.block();
            if (block instanceof EntityBlock || source.technical() || !RECOLOURED.contains(source.kind())) continue;
            Map<BlockState, PaletteEntry> same = targets.get(source.kind());
            if (same == null) {
                unshaped++;
                continue;
            }
            Optional<Color> colour = index.colourOf(source.state(), face, tint);
            if (colour.isEmpty()) continue;
            BlockState target = order.get(source.kind()).get(nearest.get(source.kind()).nearest(colour.get()));
            PaletteEntry chosen = same.get(target);
            if (target.getBlock() == block && chosen.properties().isEmpty()) continue;
            rows.add(new AxiomScript.Remap(source.id(), chosen, shared(block, target.getBlock())));
        }
        if (rows.isEmpty()) throw new Refused(Component.translatable("axiom.chromastudio.nothing_to_change"));
        rows.sort(Comparator.comparing(AxiomScript.Remap::from));
        String title = from == null ? to.name : from.name + " to " + to.name;
        return made(AxiomScript.recolour(title, rows), Component.translatable("axiom.chromastudio.recolour.done", rows.size(), unshaped));
    }

    /** A brush that paints the palette's blocks at random by weight. */
    public static Made scatter(Palette p) throws Refused {
        if (p.entries.isEmpty()) throw new Refused(Component.translatable("palettes.chromastudio.need_entries"));
        return made(AxiomScript.scatter(p), Component.translatable("axiom.chromastudio.scatter.done", p.entries.size()));
    }

    /**
     * A brush that paints a gradient's steps along an axis, starting where the player stands and running two blocks
     * a step, which the brush's own controls then change.
     */
    public static Made gradient(String name, List<PaletteEntry> steps, AxiomScript.Axis axis) throws Refused {
        if (steps.size() < 2) throw new Refused(Component.translatable("axiom.chromastudio.gradient.too_short"));
        Minecraft mc = Minecraft.getInstance();
        BlockPos at = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        int start = switch (axis) {
            case X -> at.getX();
            case Y -> at.getY();
            case Z -> at.getZ();
        };
        return made(AxiomScript.gradient(name, steps, axis, start, steps.size() * 2),
                Component.translatable("axiom.chromastudio.gradient.done", steps.size(), axis.name().toLowerCase(Locale.ROOT)));
    }

    /** Puts a script on the clipboard. */
    public static void copy(Made made) { Minecraft.getInstance().keyboardHandler.setClipboard(made.script()); }

    /** Why a script couldn't be made, to show the player. */
    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;
        private final transient Component reason;

        Refused(Component reason) {
            super(reason.getString());
            this.reason = reason;
        }

        public Component reason() { return reason; }
    }

    /** One entry a block for every block a recolour may find: the blocks of a palette, or every block the index holds. */
    private static List<Entry> sources(@Nullable Palette from) {
        List<Entry> all = new ArrayList<>();
        if (from == null) all.addAll(BlockIndex.get().entries());
        else for (PaletteEntry pe : from.entries) all.add(PaletteBlocks.entry(pe));
        // The table is keyed by block, so one state a block stands for all of them.
        Set<Block> seen = new LinkedHashSet<>();
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) if (e != null && seen.add(e.block())) out.add(e);
        return out;
    }

    /**
     * The properties a recolour carries from one block to the other: those both have by the same name, where the
     * new block takes every value the old one can have, such as a stair's facing, half and shape.
     */
    static List<String> shared(Block from, Block to) {
        List<String> out = new ArrayList<>();
        for (Property<?> p : from.getStateDefinition().getProperties()) {
            Property<?> q = to.getStateDefinition().getProperty(p.getName());
            if (q != null && names(q).containsAll(names(p))) out.add(p.getName());
        }
        return out;
    }

    private static <T extends Comparable<T>> Set<String> names(Property<T> p) {
        Set<String> out = new LinkedHashSet<>();
        for (T value : p.getPossibleValues()) out.add(p.getName(value));
        return out;
    }

    /**
     * A script with its summary, and a note when a server could refuse a script this long from a player without
     * import rights. Refused when it's too long to copy.
     */
    private static Made made(String script, Component summary) throws Refused {
        int bytes = script.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > CLIPBOARD_BYTES) throw new Refused(Component.translatable("axiom.chromastudio.too_long", (bytes + 1023) / 1024, CLIPBOARD_BYTES / 1024));
        if (AxiomScript.counted(script) <= AxiomScript.SERVER_LIMIT) return new Made(script, summary);
        return new Made(script, Component.empty().append(summary).append(" ").append(Component.translatable("axiom.chromastudio.long", AxiomScript.SERVER_LIMIT)));
    }

    /** Adds {@code /chroma axiom}. Call once at client start. */
    public static void install() {
        ChromaCommand.SUBCOMMANDS.add(new ChromaCommand.Subcommand() {
            @Override
            public <S> void attach(LiteralArgumentBuilder<S> root) {
                LiteralArgumentBuilder<S> axiom = LiteralArgumentBuilder.<S>literal("axiom")
                        .executes(_ -> usage());
                axiom.then(LiteralArgumentBuilder.<S>literal("recolour").executes(_ -> usage())
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .executes(ctx -> run(() -> recolour(palette(ctx, "palette"), null)))));
                axiom.then(LiteralArgumentBuilder.<S>literal("remap").executes(_ -> usage())
                        .then(RequiredArgumentBuilder.<S, String>argument("from", StringArgumentType.string())
                                .then(RequiredArgumentBuilder.<S, String>argument("to", StringArgumentType.greedyString())
                                        .executes(ctx -> run(() -> recolour(palette(ctx, "to"), palette(ctx, "from")))))));
                axiom.then(LiteralArgumentBuilder.<S>literal("scatter").executes(_ -> usage())
                        .then(RequiredArgumentBuilder.<S, String>argument("palette", StringArgumentType.greedyString())
                                .executes(ctx -> run(() -> scatter(palette(ctx, "palette"))))));
                LiteralArgumentBuilder<S> gradient = LiteralArgumentBuilder.<S>literal("gradient").executes(_ -> usage());
                for (AxiomScript.Axis axis : AxiomScript.Axis.values()) {
                    gradient.then(LiteralArgumentBuilder.<S>literal(axis.name().toLowerCase(Locale.ROOT)).executes(_ -> usage())
                            .then(RequiredArgumentBuilder.<S, String>argument("gradient", StringArgumentType.greedyString())
                                    .executes(ctx -> run(() -> {
                                        StoredGradient g = savedGradient(ctx);
                                        return gradient(g.name(), g.blocks().stream().map(StoredGradient.Step::block).toList(), axis);
                                    }))));
                }
                root.then(axiom.then(gradient));
            }
        });
    }

    private interface Maker {
        Made make() throws Refused;
    }

    private static int run(Maker maker) {
        try {
            Made made = maker.make();
            copy(made);
            ChromaCommand.say(Component.translatable("command.chromastudio.axiom.copied", made.summary()));
            return 1;
        } catch (Refused r) {
            ChromaCommand.say(r.reason());
            return 0;
        }
    }

    private static int usage() {
        ChromaCommand.say(Component.translatable("command.chromastudio.axiom.usage"));
        return 0;
    }

    /** A palette by its name, as the Palettes tab shows it or as its file has it, ignoring case. */
    private static <S> Palette palette(CommandContext<S> ctx, String argument) throws Refused {
        String name = StringArgumentType.getString(ctx, argument).strip();
        for (Palette p : PaletteStore.get().all()) if (name.equalsIgnoreCase(shownName(p)) || name.equalsIgnoreCase(p.name)) return p;
        throw new Refused(Component.translatable("command.chromastudio.axiom.no_palette", name));
    }

    /** A palette's name as the Palettes tab shows it: a preset's translated, any other as the player named it. */
    public static String shownName(Palette p) {
        String preset = PaletteStore.get().presetId(p);
        return preset == null ? p.name : Component.translatableWithFallback("preset.chromastudio." + preset, p.name).getString();
    }

    private static <S> StoredGradient savedGradient(CommandContext<S> ctx) throws Refused {
        String name = StringArgumentType.getString(ctx, "gradient").strip();
        StoredGradient g = GradientStore.get().get(name);
        if (g == null) throw new Refused(Component.translatable("command.chromastudio.axiom.no_gradient", name));
        return g;
    }
}
