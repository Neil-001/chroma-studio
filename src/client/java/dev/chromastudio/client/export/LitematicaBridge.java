package dev.chromastudio.client.export;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.PlacementChoice;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class LitematicaBridge {
    private LitematicaBridge() {}

    private static final String MOD_ID = "litematica";

    public static boolean isAvailable() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    public static Path schematicsDir() {
        Path usual = ChromaStudioClient.platform.gameDir().resolve("schematics");
        if (!isAvailable()) return usual;
        try {
            Path configured = Calls.schematicsDir();
            return configured != null ? configured : usual;
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Litematica didn't give its schematics folder, so the game's is used", t);
            return usual;
        }
    }

    public static BlockPos originFor(PlacementChoice choice) {
        Minecraft mc = Minecraft.getInstance();
        if (choice == PlacementChoice.LOOKING_AT && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        return mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
    }

    public static Optional<Component> place(Path file, BlockPos origin, String name) {
        if (!isAvailable()) return Optional.of(Component.translatable("export.chromastudio.litematica.missing"));
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return mc.submit(() -> place(file, origin, name)).join();
        if (mc.level == null) return Optional.of(Component.translatable("export.chromastudio.litematica.no_world"));
        try {
            return Calls.place(file, origin, name) ? Optional.empty()
                    : Optional.of(Component.translatable("export.chromastudio.litematica.unreadable", file.getFileName().toString()));
        } catch (Throwable t) {
            ChromaStudioClient.LOGGER.warn("Litematica couldn't place {}", file, t);
            String why = t.getMessage() == null || t.getMessage().isBlank() ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + t.getMessage();
            return Optional.of(Component.translatable("export.chromastudio.litematica.failed", why));
        }
    }

    private static final class Calls {
        static Path schematicsDir() { return DataManager.getSchematicsBaseDirectory().toPath(); }

        static boolean place(Path file, BlockPos origin, String name) {
            LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file.toFile());
            if (schematic == null) return false;
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            manager.addSchematicPlacement(placement, true);
            manager.setSelectedSchematicPlacement(placement);
            return true;
        }
    }
}
