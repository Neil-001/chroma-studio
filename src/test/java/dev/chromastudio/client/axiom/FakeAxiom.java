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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class FakeAxiom {
    static final class Level extends ClientLevel {
        private Map<BlockPos, BlockState> blocks;

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
    final List<BlockPos> reach = new ArrayList<>();
    int frame = 1;
    final List<CustomTool> registered = new ArrayList<>();
    final WorldRender rc = new WorldRender(null, 0, new PoseStack(), new Matrix4f());

    void render(CustomTool tool) { tool.render(rc.camera(), 0, rc.nanos(), rc.poseStack(), rc.projection()); }

    static FakeAxiom install() {
        TestIndex.loadGame();
        FakeAxiom a = new FakeAxiom();
        ChromaStudioClient.platform = new Platform();
        AxiomBridge.forget();
        AxiomMask.INTERNALS.forget();
        AxiomMask.WINDOW.forget();
        AxiomMask.SCRIPT.forget();
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
        MaskManager.mask = ignored -> true;
        MaskManager.mode = new int[] {0};
        EditorWindowType.TOOL_MASKS.open = true;
        ToolMaskWindow.luaExecutionError = null;
        set(ToolMaskWindow.class, null, "lastLuaCompile", 0L);
        MaskManager.useLuaScript = false;
        MaskManager.luaScript = null;
        ToolManager.tools = new ArrayList<>();
        ToolManager.keybindMap = new HashMap<>();
        return a;
    }

    static void uninstall() {
        ChromaStudioClient.platform = null;
        AxiomBridge.forget();
        setStatic(Minecraft.class, "instance", null);
    }

    void client() {
        Minecraft mc = make(Minecraft.class);
        mc.level = level;
        LanguageManager language = make(LanguageManager.class);
        set(LanguageManager.class, language, "currentCode", "en_us");
        set(Minecraft.class, mc, "languageManager", language);
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

    static void set(Class<?> type, Object on, String name, Object value) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            f.set(on, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

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

    CustomTool register(CustomTool tool, char icon) {
        AxiomTools.add(registered::add, tool, icon);
        return registered.getLast();
    }

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

    static Component refusal(String why) { return Component.translatable("axiom.chromastudio.refused." + why); }

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

    static final class Tools implements ToolService {
        boolean down;
        final List<Map<BlockPos, BlockState>> pushed = new ArrayList<>();
        Throwable failPush;
        Throwable failRelease;
        RuntimeException failActiveBlock;
        BlockState activeBlock = Blocks.STONE.defaultBlockState();
        BlockPos hit;
        final Set<String> overriding = new HashSet<>();
        final Map<BlockPos, BlockState> overrides = new HashMap<>();

        @Override
        public void pushBlockRegionChange(BlockRegion region) {
            if (failPush instanceof Error e) throw e;
            if (failPush instanceof RuntimeException e) throw e;
            pushed.add(new LinkedHashMap<>(((Blocks3) region).blocks));
        }

        @Override
        public BlockHitResult raycastBlock() { return hit == null ? null : new BlockHitResult(Vec3.atCenterOf(hit), net.minecraft.core.Direction.UP, hit, false); }

        @Override
        public BlockState getActiveBlock() {
            if (failActiveBlock != null) throw failActiveBlock;
            return activeBlock;
        }

        @Override
        public Vec3 getLookDirection() { return Vec3.ZERO; }

        @Override
        public boolean isMouseDown(int button) { return down && button == BrushStroke.USE_TOOL; }

        @Override
        public ForkJoinPool getSharedPoolThreadExecutor() { return ForkJoinPool.commonPool(); }

        @Override
        public ExecutorService getSharedSingleThreadExecutor() { throw new UnsupportedOperationException(); }

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
        public void render(Camera camera, Vec3 at, PoseStack pose, Matrix4f projection, float opacity, float pulse) {}

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
        public void render(Camera camera, Vec3 at, PoseStack pose, Matrix4f projection, long nanos, int effects) {}

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

    private static final class Platform implements ClientPlatform {
        @Override
        public Path configDir() { return Path.of("build", "fake-axiom"); }

        @Override
        public Path gameDir() { return Path.of("build", "fake-axiom"); }

        @Override
        public boolean isModLoaded(String id) { return id.equals("axiom"); }

        @Override
        public boolean isFabric() { return true; }

        @Override
        public void modelParts(BlockStateModel model, BlockState state, RandomSource random, List<BlockModelPart> out) {}

        @Override
        public TextureAtlasSprite particleIcon(BlockStateModel model, BlockState state) { return null; }

        @Override
        public void submitPicture(GuiGraphics g, PictureInPictureRenderState state) {}

        @Override
        public void sendChatCommand(String command) {}
    }
}
