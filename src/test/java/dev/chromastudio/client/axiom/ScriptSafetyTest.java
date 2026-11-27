package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.QueuedChanges;
import dev.chromastudio.core.palette.Recolour;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

class ScriptSafetyTest {
    private static final Pattern CONTROL = Pattern.compile("\\$(int|float|boolean)\\(([^,]+), ([^,)]+)[^)]*\\)\\$");

    private static FakeAxiom axiom;
    private static final List<Palette> made = new ArrayList<>();

    @BeforeAll
    static void loadGame() {
        axiom = FakeAxiom.install();
        axiom.client();
        String[] sprites = {"block/stone", "block/oak_planks", "block/red_wool", "block/blue_wool", "block/yellow_concrete", "block/deepslate",
                "block/glass", "block/white_wool", "block/sandstone", "block/terracotta"};
        Map<String, String> all = new LinkedHashMap<>();
        int i = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            if (b.defaultBlockState().isAir()) continue;
            all.put(BuiltInRegistries.BLOCK.getKey(b).getPath(), sprites[i++ % sprites.length]);
        }
        TestIndex.index(all);
    }

    @AfterAll
    static void uninstall() {
        for (Palette p : made) PaletteStore.get().delete(p);
        FakeAxiom.uninstall();
    }

    private static List<BlockState> everyState() {
        List<BlockState> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) out.addAll(b.getStateDefinition().getPossibleStates());
        return out;
    }

    private static Palette palette(String name, String... ids) {
        List<PaletteEntry> entries = new ArrayList<>();
        for (String id : ids) entries.add(new PaletteEntry("minecraft:" + id, ""));
        Palette p = PaletteStore.get().create(name, entries);
        made.add(p);
        return p;
    }

    @Test
    void theGradientScriptLeavesEveryBlockTheGradientToolWouldLeave() throws Exception {
        Method paintable = GradientTool.class.getDeclaredMethod("paintable", BlockGetter.class, BlockPos.class, BlockState.class);
        paintable.setAccessible(true);
        Predicate<BlockState> toolPaints = s -> {
            try {
                return FoundBuild.member(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s) && (boolean) paintable.invoke(null, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, s);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        };
        String script = AxiomScripts.gradient("Walls", List.of(new PaletteEntry("minecraft:stone", ""), new PaletteEntry("minecraft:andesite", ""),
                new PaletteEntry("minecraft:deepslate", "")), AxiomScript.Axis.Y, BlockPos.ZERO).text();
        check("gradient", script, s -> s.isAir() || toolPaints.test(s));
    }

    @Test
    void theScatterBrushAndItsScriptLeaveEveryBlockThatHoldsSomething() throws Exception {
        Palette p = palette("Script safety scatter", "stone_bricks", "cobblestone", "andesite");
        PaletteBrushTool tool = new PaletteBrushTool();
        Method keys = PaletteBrushTool.class.getDeclaredMethod("keys", List.class);
        keys.setAccessible(true);
        FakeAxiom.set(PaletteBrushTool.class, tool, "mode", PaletteBrushTool.class.getDeclaredField("mode").getType().getEnumConstants()[0]);
        FakeAxiom.set(PaletteBrushTool.class, tool, "paletteKey", ((List<?>) keys.invoke(null, List.of(p))).get(0));
        Method painterNow = PaletteBrushTool.class.getDeclaredMethod("painterNow", net.minecraft.world.level.Level.class, BlockPos.class, long.class, int.class);
        painterNow.setAccessible(true);
        int size = AxiomPanel.radius((int[]) FakeAxiom.get(PaletteBrushTool.class, tool, "radius"));
        BrushStroke.Painter painter = (BrushStroke.Painter) painterNow.invoke(tool, axiom.level, null, 1L, size);
        assertTrue(painter != null, "Scatter made no painter");
        Map<BlockState, Boolean> brush = new HashMap<>();
        List<BlockState> wrong = new ArrayList<>();
        for (BlockState s : everyState()) {
            boolean[] put = {false};
            painter.paint(0, 0, 0, s, (_, _, _) -> true, (x, y, z, _) -> put[0] |= x == 0 && y == 0 && z == 0);
            brush.put(s, put[0]);
            if (put[0] && (holdsSomething(s) || waterlogged(s))) wrong.add(s);
        }
        String script = AxiomScripts.scatter(p).text();
        assertAll(() -> assertTrue(wrong.isEmpty(), "Scatter paints over " + wrong.size() + " states that hold something, of " + blocks(wrong)),
                () -> check("scatter", script, s -> brush.get(s) && !holdsSomething(s) && !waterlogged(s)));
    }

    @Test
    void theRecolourAndRemapScriptsChangeOnlyWhatTheBrushWould() throws Exception {
        Palette desert = palette("Script safety desert", "sandstone", "smooth_sandstone", "cut_sandstone", "terracotta", "orange_terracotta",
                "sandstone_stairs");
        Palette from = palette("Script safety house", "stone_bricks", "oak_planks", "oak_stairs", "oak_door", "glass", "chest", "barrel",
                "brewing_stand", "player_head", "jukebox", "spawner", "oak_sign", "flower_pot");
        for (Palette source : new Palette[] {null, from}) {
            RecolourPlan plan = RecolourPlan.of(desert, source, Recolour.Mode.SHADING);
            check(source == null ? "recolour" : "remap", AxiomScripts.recolour(desert, source, Recolour.Mode.SHADING).text(),
                    s -> plan.apply(s) != null && !holdsSomething(s));
        }
    }

    private static boolean holdsSomething(BlockState s) {
        Block b = s.getBlock();
        return b instanceof EntityBlock || b instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR || b instanceof CandleCakeBlock
                || b instanceof AbstractCauldronBlock && b != Blocks.CAULDRON || b instanceof ComposterBlock && s.getValue(ComposterBlock.LEVEL) > 0;
    }

    private static boolean waterlogged(BlockState s) {
        return s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);
    }

    @Test
    void everyToolLeavesTheWaterInABlockOrKeepsIt() throws Exception {
        Palette glass = palette("Script safety glass", "glass");
        RecolourPlan plan = RecolourPlan.of(glass, null, Recolour.Mode.SHADING);
        List<BlockState> wrong = new ArrayList<>();
        for (BlockState s : everyState()) {
            BlockState out = plan.apply(s);
            if (out != null && waterlogged(s) && !waterlogged(out)) wrong.add(s);
        }
        assertTrue(wrong.isEmpty(), "A recolour takes the water out of " + blocks(wrong));
        check("glass recolour", AxiomScripts.recolour(glass, null, Recolour.Mode.SHADING).text(), s -> plan.apply(s) != null && !holdsSomething(s));
        BlockState stone = Blocks.STONE.defaultBlockState(), grate = TestIndex.block("copper_grate").defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        for (int x = 0; x <= 4; x++) axiom.level.put(x, 0, 0, x == 0 || x == 4 ? stone : grate);
        try {
            BlockPos first = BlockPos.ZERO, second = new BlockPos(4, 0, 0);
            QueuedChanges<BlockState> made = AxiomChanges.queue(axiom.level);
            GradientTool.Laid laid = GradientTool.lay(axiom.level, GradientTool.search(axiom.level, first, second, first, false, 64),
                    List.of(Blocks.ANDESITE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()), false, 0, AxiomMask.now(axiom.level), made);
            for (QueuedChanges.Change<BlockState> c : made.changes()) assertFalse(c.was() == grate, "The gradient tool paints over " + c);
            assertTrue(laid.leftAlone() >= 3, "covered " + laid.covered() + ", left " + laid.leftAlone());
        } finally {
            for (int x = 0; x <= 4; x++) axiom.level.put(x, 0, 0, Blocks.AIR.defaultBlockState());
        }
    }

    private static String blocks(List<BlockState> wrong) {
        return wrong.stream().map(s -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath()).distinct().toList().toString();
    }

    @Test
    void noScriptAsksABlockTag() throws Exception {
        Palette p = palette("Script safety tags", "stone_bricks", "oak_planks");
        List<String> scripts = List.of(AxiomScripts.scatter(p).text(), AxiomScripts.gradient("Walls", List.of(new PaletteEntry("minecraft:stone", ""),
                new PaletteEntry("minecraft:deepslate", "")), AxiomScript.Axis.Y, BlockPos.ZERO).text(), AxiomScripts.recolour(p, null, Recolour.Mode.SHADING).text());
        for (String script : scripts) assertFalse(script.contains("isBlockTagged"), script);
    }

    private static void check(String name, String script, Predicate<BlockState> allowed) {
        for (boolean air : new boolean[] {false, true}) {
            Brush brush = new Brush(script, air);
            List<BlockState> wrong = new ArrayList<>(), unfilled = new ArrayList<>();
            int replaced = 0;
            for (BlockState s : everyState()) {
                Map<BlockPos, BlockState> writes = brush.run(s);
                replaced += writes.size();
                if (air && s.isAir() && !writes.containsKey(BlockPos.ZERO)) unfilled.add(s);
                for (Map.Entry<BlockPos, BlockState> w : writes.entrySet()) {
                    BlockState there = brush.world.get(w.getKey());
                    if (!allowed.test(there) || !air && there.isAir() || waterlogged(there) && !waterlogged(w.getValue())) wrong.add(there);
                }
            }
            assertTrue(replaced > 0, name + " replaced nothing, so it proves nothing");
            assertTrue(wrong.isEmpty(), "The " + name + " script (Paint air " + air + ", " + AxiomScript.counted(script) + " characters) replaces "
                    + wrong.size() + " states its tool leaves alone, of " + blocks(wrong));
            if (script.contains("$boolean(Paint air")) assertTrue(unfilled.isEmpty(), "The " + name + " script leaves " + blocks(unfilled) + " unfilled with Paint air ticked");
        }
    }

    private static final class Brush {
        final Globals g = JsePlatform.standardGlobals();
        final LuaValue chunk;
        final Map<BlockPos, BlockState> world = new HashMap<>();
        final Map<BlockPos, BlockState> writes = new LinkedHashMap<>();

        Brush(String script, boolean paintAir) {
            Matcher m = CONTROL.matcher(script);
            StringBuilder resolved = new StringBuilder();
            while (m.find()) {
                String value = m.group(2).equals("Paint air") ? String.valueOf(paintAir) : m.group(3).strip();
                m.appendReplacement(resolved, Matcher.quoteReplacement(value));
            }
            m.appendTail(resolved);
            assertFalse(resolved.toString().contains("$"), "A control was left unresolved");
            LuaTable blocks = new LuaTable();
            for (Block b : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(b);
                LuaTable t = blocks;
                if (!id.getNamespace().equals("minecraft")) {
                    if (blocks.get(id.getNamespace()).isnil()) blocks.set(id.getNamespace(), new LuaTable());
                    t = (LuaTable) blocks.get(id.getNamespace());
                }
                t.set(id.getPath(), BuiltInRegistries.BLOCK.getId(b));
            }
            g.set("blocks", blocks);
            g.set("getBlock", new ThreeArgFunction() {
                @Override
                public LuaValue call(LuaValue x, LuaValue y, LuaValue z) { return valueOf(BuiltInRegistries.BLOCK.getId(at(x, y, z).getBlock())); }
            });
            g.set("getBlockState", new ThreeArgFunction() {
                @Override
                public LuaValue call(LuaValue x, LuaValue y, LuaValue z) { return valueOf(id(at(x, y, z))); }
            });
            g.set("isSolid", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue b) { return valueOf(!state(b).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()); }
            });
            g.set("isBlockTagged", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue b, LuaValue tag) {
                    TagKey<Block> key = TagKey.create(Registries.BLOCK, Identifier.parse(tag.tojstring().toLowerCase(Locale.ROOT)));
                    if (BuiltInRegistries.BLOCK.get(key).isEmpty()) throw new LuaError("Unknown block tag: " + tag);
                    return valueOf(state(b).is(key));
                }
            });
            g.set("getBlockProperty", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue b, LuaValue name) {
                    BlockState s = state(b);
                    Property<?> p = s.getBlock().getStateDefinition().getProperty(name.tojstring().trim().toLowerCase(Locale.ROOT));
                    return p == null ? NIL : valueOf(value(s, p));
                }
            });
            g.set("withBlockProperty", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue b, LuaValue pair) {
                    String[] kv = pair.tojstring().split("=", 2);
                    BlockState s = state(b);
                    Property<?> p = s.getBlock().getStateDefinition().getProperty(kv[0].trim());
                    return valueOf(id(p == null ? s : with(s, p, kv[1].trim())));
                }
            });
            g.set("setBlock", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs a) {
                    writes.put(new BlockPos(a.checkint(1), a.checkint(2), a.checkint(3)), state(a.arg(4)));
                    return NONE;
                }
            });
            chunk = g.load(resolved.toString(), "script");
        }

        Map<BlockPos, BlockState> run(BlockState s) {
            world.clear();
            writes.clear();
            world.put(BlockPos.ZERO, s);
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                boolean lower = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
                world.put(new BlockPos(0, lower ? 1 : -1, 0), s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER));
            }
            g.set("x", 0);
            g.set("y", 0);
            g.set("z", 0);
            LuaValue out = chunk.call();
            if (!out.isnil()) writes.put(BlockPos.ZERO, state(out));
            return new LinkedHashMap<>(writes);
        }

        private BlockState at(LuaValue x, LuaValue y, LuaValue z) {
            return world.getOrDefault(new BlockPos(x.checkint(), y.checkint(), z.checkint()), Blocks.AIR.defaultBlockState());
        }

        private static int id(BlockState s) { return -Block.BLOCK_STATE_REGISTRY.getId(s) - 1; }

        private static BlockState state(LuaValue v) {
            int id = v.checkint();
            return id >= 0 ? BuiltInRegistries.BLOCK.byId(id).defaultBlockState() : Block.BLOCK_STATE_REGISTRY.byId(-id - 1);
        }

        private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }

        private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String value) {
            return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
        }
    }
}
