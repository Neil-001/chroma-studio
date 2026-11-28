package dev.chromastudio.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiSignaturesTest {
    private static final String SIGNATURES = """
            public boolean dev.chromastudio.api.BlockMatcher.isEmpty()
            public boolean dev.chromastudio.api.SavedGradient$Step.chosenByHand()
            public boolean dev.chromastudio.api.SavedGradient$Stop.isBlock()
            public double dev.chromastudio.api.BlockMatcher.distance(int,net.minecraft.world.level.block.state.BlockState)
            public final class dev.chromastudio.api.BlockMatcher
            public final class dev.chromastudio.api.ChromaStudioApi
            public final class dev.chromastudio.api.SavedGradient
            public int dev.chromastudio.api.BlockMatcher.indexVersion()
            public int dev.chromastudio.api.SavedGradient$Step.idealRgb()
            public int dev.chromastudio.api.SavedGradient$Stop.rgb()
            public int dev.chromastudio.api.SavedGradient.steps()
            public java.lang.String dev.chromastudio.api.SavedGradient$Step.blockId()
            public java.lang.String dev.chromastudio.api.SavedGradient$Stop.blockId()
            public java.lang.String dev.chromastudio.api.SavedGradient.name()
            public java.util.List<dev.chromastudio.api.SavedGradient$Step> dev.chromastudio.api.SavedGradient.blocks()
            public java.util.List<dev.chromastudio.api.SavedGradient$Stop> dev.chromastudio.api.SavedGradient.stops()
            public java.util.List<net.minecraft.world.level.block.state.BlockState> dev.chromastudio.api.BlockMatcher.blocks()
            public net.minecraft.world.level.block.state.BlockState dev.chromastudio.api.BlockMatcher.closest(int)
            public net.minecraft.world.level.block.state.BlockState dev.chromastudio.api.SavedGradient$Step.block()
            public net.minecraft.world.level.block.state.BlockState dev.chromastudio.api.SavedGradient$Stop.block()
            public static boolean dev.chromastudio.api.ChromaStudioApi.indexReady()
            public static boolean dev.chromastudio.api.ChromaStudioApi.openGradientTab(java.lang.String)
            public static dev.chromastudio.api.BlockMatcher dev.chromastudio.api.ChromaStudioApi.matcher(java.util.Collection<net.minecraft.world.level.block.state.BlockState>,net.minecraft.core.Direction,net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>)
            public static final class dev.chromastudio.api.SavedGradient$Step
            public static final class dev.chromastudio.api.SavedGradient$Stop
            public static int dev.chromastudio.api.ChromaStudioApi.apiVersion()
            public static int dev.chromastudio.api.ChromaStudioApi.indexVersion()
            public static java.util.List<dev.chromastudio.api.SavedGradient> dev.chromastudio.api.ChromaStudioApi.savedGradients()
            public static java.util.List<dev.chromastudio.core.palette.Palette> dev.chromastudio.api.ChromaStudioApi.palettes()
            public static java.util.List<net.minecraft.world.level.block.state.BlockState> dev.chromastudio.api.ChromaStudioApi.closest(int,int)
            public static java.util.Optional<java.lang.Integer> dev.chromastudio.api.ChromaStudioApi.colourOf(net.minecraft.world.level.block.state.BlockState,net.minecraft.core.Direction,net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>)
            public static net.minecraft.world.level.block.state.BlockState dev.chromastudio.api.ChromaStudioApi.blockState(dev.chromastudio.core.palette.PaletteEntry)
            """;

    @Test
    void thePublicApiIsTheRecordedOne() throws Exception {
        assertEquals(SIGNATURES, signatures(),
                "dev.chromastudio.api changed. Removing or changing a signature needs a new major version. "
                        + "Update SIGNATURES once mod_version is right.");
    }

    private static String signatures() throws IOException, URISyntaxException, ClassNotFoundException {
        ClassLoader loader = ChromaStudioApi.class.getClassLoader();
        List<String> found = new ArrayList<>();
        Path tests = Path.of(ApiSignaturesTest.class.getResource("ApiSignaturesTest.class").toURI()).getParent();
        for (URL classes : Collections.list(loader.getResources("dev/chromastudio/api"))) {
            Path directory = Path.of(classes.toURI());
            if (directory.equals(tests)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.class")) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    Class<?> type = Class.forName("dev.chromastudio.api." + name.substring(0, name.length() - ".class".length()), false, loader);
                    if (!exported(type)) continue;
                    found.add(declaration(type));
                    for (Field field : type.getDeclaredFields()) if (exported(field)) found.add(field.toGenericString());
                    for (Executable call : type.getDeclaredConstructors()) if (exported(call)) found.add(call.toGenericString());
                    for (Executable call : type.getDeclaredMethods()) if (exported(call)) found.add(call.toGenericString());
                }
            }
        }
        return found.stream().sorted().map(line -> line + "\n").collect(Collectors.joining());
    }

    private static String declaration(Class<?> type) {
        String parents = Stream.concat(Stream.ofNullable(type.getGenericSuperclass()).filter(parent -> parent != Object.class),
                Arrays.stream(type.getGenericInterfaces())).map(Type::getTypeName).collect(Collectors.joining(", "));
        return type.toGenericString() + (parents.isEmpty() ? "" : " : " + parents);
    }

    private static boolean exported(Class<?> type) {
        return exported(type.getModifiers()) && (type.getEnclosingClass() == null || exported(type.getEnclosingClass()));
    }

    private static boolean exported(Member member) {
        return exported(member.getModifiers()) && !member.isSynthetic();
    }

    private static boolean exported(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }
}
