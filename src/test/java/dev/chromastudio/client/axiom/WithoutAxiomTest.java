package dev.chromastudio.client.axiom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ClientPlatform;
import dev.chromastudio.client.index.TestIndex;
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WithoutAxiomTest {
    private static final String PACKAGE = "dev.chromastudio.client.axiom.";

    @BeforeAll
    static void loadGame() {
        TestIndex.loadGame();
        TestIndex.index(Map.of("stone", "block/stone", "oak_planks", "block/oak_planks", "oak_stairs", "block/oak_planks", "lantern", "block/lantern"));
    }

    private static final class NoAxiom extends ClassLoader {
        NoAxiom() { super(WithoutAxiomTest.class.getClassLoader()); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.moulberry.") || name.startsWith("imgui.")) throw new ClassNotFoundException(name);
            if (!name.startsWith(PACKAGE) || name.endsWith("Test")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) throw new ClassNotFoundException(name);
                        byte[] bytes = in.readAllBytes();
                        c = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }

    private static Object call(String method, Class<?>[] types, Object... args) throws Exception {
        Class<?> scripts = Class.forName(PACKAGE + "AxiomScripts", true, new NoAxiom());
        Method m = scripts.getDeclaredMethod(method, types);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception refused && refused.getClass().getSimpleName().equals("Refused")) return refused;
            throw e;
        }
    }

    private static Palette palette(String... ids) {
        Palette p = new Palette("Test");
        List<PaletteEntry> entries = new ArrayList<>();
        for (String id : ids) entries.add(new PaletteEntry("minecraft:" + id, ""));
        p.entries.addAll(entries);
        return p;
    }

    @Test
    void theAxiomClassesAreHidden() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.moulberry.axiomclientapi.CustomTool", false, new NoAxiom()));
        assertThrows(NoClassDefFoundError.class, () -> Class.forName(PACKAGE + "GradientTool", true, new NoAxiom()));
    }

    @Test
    void everyScriptIsMadeWithoutAxiomsClasses() throws Exception {
        Object gradient = call("gradient", new Class<?>[] {String.class, List.class, AxiomScript.Axis.class, BlockPos.class},
                "Test", palette("stone", "oak_planks").entries, AxiomScript.Axis.Y, BlockPos.ZERO);
        assertEquals("Script", gradient.getClass().getSimpleName(), String.valueOf(gradient));
        Object scatter = call("scatter", new Class<?>[] {Palette.class}, palette("stone", "oak_planks"));
        assertEquals("Script", scatter.getClass().getSimpleName(), String.valueOf(scatter));
        Object recolour = call("recolour", new Class<?>[] {Palette.class, Palette.class, Recolour.Mode.class}, palette("oak_planks"), null, Recolour.Mode.SHADING);
        assertFalse(recolour instanceof Throwable t && !t.getClass().getSimpleName().equals("Refused"), String.valueOf(recolour));
    }

    private record Platform(boolean isFabric, boolean axiom) implements ClientPlatform {
        @Override public Path configDir() { return Path.of("build", "without-axiom"); }
        @Override public Path gameDir() { return configDir(); }
        @Override public boolean isModLoaded(String id) { return axiom && id.equals("axiom"); }
        @Override public List<BakedQuad> quads(BakedModel model, BlockState state, Direction side, RandomSource random) { return List.of(); }
        @Override public TextureAtlasSprite particleIcon(BakedModel model, BlockState state) { return null; }
        @Override public void renderBlock(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) { }
        @Override public void sendChatCommand(String command) { }
    }

    @Test
    void theFeaturesShowOnlyWithAxiomOnFabric() {
        ClientPlatform was = ChromaStudioClient.platform;
        try {
            for (boolean fabric : List.of(true, false)) for (boolean axiom : List.of(true, false)) {
                ChromaStudioClient.platform = new Platform(fabric, axiom);
                assertEquals(fabric && axiom, AxiomBridge.isPresent(), "Fabric " + fabric + ", Axiom " + axiom);
            }
            for (boolean fabric : List.of(true, false)) {
                ChromaStudioClient.platform = new Platform(fabric, !fabric);
                int before = ChromaCommand.SUBCOMMANDS.size();
                AxiomBridge.register();
                assertEquals(before, ChromaCommand.SUBCOMMANDS.size(), "/chroma axiom registered with Fabric " + fabric);
            }
        } finally {
            ChromaStudioClient.platform = was;
        }
    }
}
