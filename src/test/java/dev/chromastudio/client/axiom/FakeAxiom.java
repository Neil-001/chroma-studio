package dev.chromastudio.client.axiom;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.axiom.editor.EditorUI;
import com.moulberry.axiom.editor.EditorWindowType;
import com.moulberry.axiom.editor.windows.global_mask.ToolMaskWindow;
import com.moulberry.axiom.mask.MaskElement;
import com.moulberry.axiom.mask.MaskManager;
import com.moulberry.axiom.tools.ToolManager;
import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.funcinterfaces.TriIntConsumer;
import com.moulberry.axiomclientapi.pathers.BallShape;
import com.moulberry.axiomclientapi.pathers.ToolPatherPoint;
import com.moulberry.axiomclientapi.pathers.ToolPatherUnique;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolPatherProvider;
import com.moulberry.axiomclientapi.service.ToolService;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.index.TestIndex;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

/**
 * Axiom as Chroma Studio's tools see it, for tests: its services, which record what reaches them, its editor and tool
 * masks through the stand-ins in {@code com.moulberry.axiom}, a world of a few blocks, and a frame count the test moves
 * on. {@link #install} puts it in place and forgets everything a previous test left.
 */
final class FakeAxiom {
    /** The world the tools see: air but for the blocks put in it. */
    static final class Level extends ClientLevel {
        private Map<BlockPos, BlockState> blocks;

        // Never run: the world is made without its constructor, which would need a whole client.
        private Level() { super(null, null, null, null, 0, 0, null, false, 0, 0); }

        private Map<BlockPos, BlockState> blocks() {
            if (blocks == null) blocks = new HashMap<>();
            return blocks;
        }

        void put(int x, int y, int z, BlockState s) { blocks().put(new BlockPos(x, y, z), s); }

        @Override
        public BlockState getBlockState(BlockPos pos) { return blocks().getOrDefault(pos.immutable(), Blocks.AIR.defaultBlockState()); }
    }

    final Level level = make(Level.class);
    final Tools tools = new Tools();
    /** The positions the brush's ball reaches next frame. */
    final List<BlockPos> reach = new ArrayList<>();
    /** The frame Axiom's editor is drawing. */
    int frame = 1;
    /** What each tool Axiom was handed is, as Axiom would call it. */
    final List<CustomTool> registered = new ArrayList<>();
    final IAxiomWorldRenderContext rc = new RenderContext();

    static FakeAxiom install() {
        TestIndex.loadGame();
        FakeAxiom a = new FakeAxiom();
        ChromaStudioClient.platform = new Platform();
        AxiomBridge.forget();
        AxiomMask.INTERNALS.forget();
        AxiomMask.WINDOW.forget();
        AxiomEditor.INTERNALS.forget();
        AxiomIcons.INTERNALS.forget();
        AxiomTools.regions = new Regions();
        AxiomTools.pathers = a.new Pathers();
        AxiomTools.tools = a.tools;
        AxiomTools.level = () -> a.level;
        AxiomTools.frames = () -> a.frame;
        EditorUI.enabled = true;
        EditorUI.active = true;
        EditorUI.throwOnDisable = null;
        MaskManager.mask = _ -> true;
        MaskManager.mode = new int[] {0};
        EditorWindowType.TOOL_MASKS.open = true;
        ToolMaskWindow.luaExecutionError = null;
        ToolManager.tools = new ArrayList<>();
        ToolManager.keybindMap = new HashMap<>();
        return a;
    }

    /** Takes the fakes away again, so other tests find the game as it was. */
    static void uninstall() {
        ChromaStudioClient.platform = null;
        AxiomBridge.forget();
        setStatic(Minecraft.class, "instance", null);
    }

    /**
     * Puts a game client in place whose world is this one, in English, as the tools read the world, the language and
     * the window from it. Made without its constructor, like the world, so it holds nothing else.
     */
    void client() {
        Minecraft mc = make(Minecraft.class);
        mc.level = level;
        LanguageManager language = make(LanguageManager.class);
        set(LanguageManager.class, language, "currentCode", "en_us");
        set(Minecraft.class, mc, "languageManager", language);
        // A 720-pixel window at the default field of view, which a swatch's colour is read for.
        Window window = make(Window.class);
        set(Window.class, window, "height", 720);
        set(Minecraft.class, mc, "window", window);
        OptionInstance<?> fov = make(OptionInstance.class);
        set(OptionInstance.class, fov, "value", 70);
        Options options = make(Options.class);
        set(Options.class, options, "fov", fov);
        set(Minecraft.class, mc, "options", options);
        setStatic(Minecraft.class, "instance", mc);
    }

    /** Sets a field of an object, a private or final one included, as a tool's panel would set it. */
    static void set(Class<?> type, Object on, String name, Object value) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            f.set(on, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Reads a field of an object, a private one included. */
    static Object get(Class<?> type, Object on, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(on);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setStatic(Class<?> type, String name, Object value) { set(type, null, name, value); }

    /** Hands a tool to Axiom as Chroma Studio's own are, and returns what Axiom then holds and calls. */
    CustomTool register(CustomTool tool, char icon) {
        AxiomTools.add(registered::add, tool, icon);
        return registered.getLast();
    }

    /**
     * A tool mask whose Lua script fails at the positions {@code failsAt} names, as Axiom runs one: a fresh context
     * compiles the script, which clears the last error, and a context whose script has failed answers no from then on.
     * Every position asked is added to {@code asked}.
     */
    static MaskElement lua(List<Integer> asked, IntPredicate failsAt) {
        return c -> {
            if (!c.compiled) {
                c.compiled = true;
                ToolMaskWindow.luaExecutionError = null;
            }
            asked.add(c.x);
            if (c.broken) return false;
            if (failsAt.test(c.x)) {
                ToolMaskWindow.luaExecutionError = new String("attempt to compare nil with number");
                c.broken = true;
                return false;
            }
            return true;
        };
    }

    /** What the player reads when a tool sent nothing, for a refusal by its name. */
    static Component refusal(String why) { return Component.translatable("axiom.chromastudio.refused." + why); }

    /** What the HUD's readout shows, or null. */
    static Component readout() {
        try {
            Field f = dev.chromastudio.client.ui.Hud.class.getDeclaredField("line1");
            f.setAccessible(true);
            return (Component) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static void clearReadout() {
        try {
            Field f = dev.chromastudio.client.ui.Hud.class.getDeclaredField("line1");
            f.setAccessible(true);
            f.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Makes an object without running its constructor, as the game's world can't be built without a client. */
    private static <T> T make(Class<T> type) {
        try {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field f = unsafe.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Method allocate = unsafe.getMethod("allocateInstance", Class.class);
            return type.cast(allocate.invoke(f.get(null), type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Axiom's tool service: whether the button is held, and what was sent and shown. */
    static final class Tools implements ToolService {
        boolean down;
        /** Each change sent, as the blocks it held. */
        final List<Map<BlockPos, BlockState>> pushed = new ArrayList<>();
        /** Thrown when a change is sent, as by an Axiom that lacks the method. */
        Throwable failPush;
        /** Thrown once, the next time a preview lets go of the world's look. */
        Throwable failRelease;
        /** The block under the mouse, or null. */
        BlockPos hit;
        /** The previews holding the world's look, by name. */
        final Set<String> overriding = new HashSet<>();
        final Map<BlockPos, BlockState> overrides = new HashMap<>();

        @Override
        public void pushBlockRegionChange(BlockRegion region) {
            if (failPush instanceof Error e) throw e;
            if (failPush instanceof RuntimeException e) throw e;
            // Copied into a hash map: an immutable copy probes badly on the clustered hashes of a big box's positions.
            pushed.add(new LinkedHashMap<>(((Blocks3) region).blocks));
        }

        @Override
        public BlockHitResult raycastBlock() { return hit == null ? null : new BlockHitResult(Vec3.atCenterOf(hit), net.minecraft.core.Direction.UP, hit, false); }

        @Override
        public BlockState getActiveBlock() { return Blocks.STONE.defaultBlockState(); }

        @Override
        public Vec3 getLookDirection() { return Vec3.ZERO; }

        @Override
        public boolean isMouseDown(int button) { return down && button == BrushStroke.USE_TOOL; }

        @Override
        public ForkJoinPool getSharedPoolThreadExecutor() { return ForkJoinPool.commonPool(); }

        @Override
        public ExecutorService getSharedSingleThreadExecutor() { throw new UnsupportedOperationException(); }

        /** As Axiom's: one holder at a time, and an {@code Error} for anyone else who asks. */
        @Override
        public void acquireChunkRenderOverrider(String id) {
            if (!overriding.isEmpty()) throw new Error("Chunk render overrider is already locked by " + overriding.iterator().next());
            overriding.add(id);
        }

        @Override
        public void releaseChunkRenderOverrider(String id) {
            Throwable fail = failRelease;
            failRelease = null;
            if (fail instanceof Error e) throw e;
            if (fail instanceof RuntimeException e) throw e;
            if (!overriding.contains(id)) throw new Error("Chunk render overrider isn't locked by " + id);
            overriding.remove(id);
            overrides.clear();
        }

        @Override
        public void setBlockRenderOverride(int x, int y, int z, BlockState state) { overrides.put(new BlockPos(x, y, z), state); }
    }

    private static final class Blocks3 implements BlockRegion {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        @Override
        public void clear() { blocks.clear(); }

        @Override
        public void render(IAxiomWorldRenderContext rc, Vec3 at, float opacity, float pulse) {}

        @Override
        public void addBlockIfNotPresent(int x, int y, int z, BlockState s) { blocks.putIfAbsent(new BlockPos(x, y, z), s); }

        @Override
        public void addBlock(int x, int y, int z, BlockState s) { blocks.put(new BlockPos(x, y, z), s); }

        @Override
        public BlockPos min() { return BlockPos.ZERO; }

        @Override
        public BlockPos max() { return BlockPos.ZERO; }

        @Override
        public boolean isEmpty() { return blocks.isEmpty(); }

        @Override
        public int count() { return blocks.size(); }
    }

    private static final class Positions implements BooleanRegion {
        final Set<BlockPos> in = new HashSet<>();

        @Override
        public void render(IAxiomWorldRenderContext rc, Vec3 at, int effects) {}

        @Override
        public void forEach(TriIntConsumer each) { for (BlockPos p : in) each.accept(p.getX(), p.getY(), p.getZ()); }

        @Override
        public boolean add(int x, int y, int z) { return in.add(new BlockPos(x, y, z)); }

        @Override
        public void close() {}

        @Override
        public void clear() { in.clear(); }

        @Override
        public boolean contains(int x, int y, int z) { return in.contains(new BlockPos(x, y, z)); }
    }

    private static final class Regions implements RegionProvider {
        @Override
        public BlockRegion createBlock() { return new Blocks3(); }

        @Override
        public BooleanRegion createBoolean() { return new Positions(); }
    }

    /** A brush's ball, which reaches each position in {@link #reach} the next frame it's drawn. */
    private final class Pathers implements ToolPatherProvider {
        @Override
        public ToolPatherPoint createPoint(boolean unique) { throw new UnsupportedOperationException(); }

        @Override
        public ToolPatherUnique createUnique(int radius, BallShape shape) {
            return each -> {
                for (BlockPos p : reach) each.accept(p.getX(), p.getY(), p.getZ());
                reach.clear();
                return true;
            };
        }
    }

    private static final class RenderContext implements IAxiomWorldRenderContext {
        @Override
        public PoseStack poseStack() { return new PoseStack(); }

        @Override
        public float partialTick() { return 0; }

        @Override
        public long nanos() { return 0; }

        @Override
        public Vec3 position() { return Vec3.ZERO; }

        @Override
        public BlockPos blockPosition() { return BlockPos.ZERO; }

        @Override
        public float xRot() { return 0; }

        @Override
        public float yRot() { return 0; }

        @Override
        public Quaternionfc rotation() { return null; }

        @Override
        public Matrix4fc projection() { return null; }
    }

    /** A loader with Axiom loaded. */
    private static final class Platform implements ClientPlatform {
        @Override
        public Path configDir() { return Path.of("build", "fake-axiom"); }

        @Override
        public Path gameDir() { return Path.of("build", "fake-axiom"); }

        @Override
        public boolean isModLoaded(String id) { return id.equals("axiom"); }

        @Override
        public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockStateModelPart> out) {}

        @Override
        public Material.Baked particleMaterial(BlockStateModel model, BlockState state) { return null; }

        @Override
        public void sendChatCommand(String command) {}
    }
}
