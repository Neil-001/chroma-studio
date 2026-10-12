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

/**
 * Hands an exported file to Litematica, when it's there. Litematica is optional and its version isn't checked, so
 * every call into it goes through {@link Calls}, which the game only loads once the mod is known to be present, and
 * anything a call throws, a missing class or method included, is caught here and never reaches the caller.
 */
public final class LitematicaBridge {
    private LitematicaBridge() {}

    /** Forgematica, the NeoForge port, registers this id too. */
    private static final String MOD_ID = "litematica";

    public static boolean isAvailable() {
        return ChromaStudioClient.platform != null && ChromaStudioClient.platform.isModLoaded(MOD_ID);
    }

    /** Litematica's configured schematics folder, or the game's {@code schematics} folder when it can't say. */
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

    /** Where a placement goes: the block the player looks at within reach, or failing that, where the player stands. */
    public static BlockPos originFor(PlacementChoice choice) {
        Minecraft mc = Minecraft.getInstance();
        if (choice == PlacementChoice.LOOKING_AT && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        return mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
    }

    /**
     * Loads the file into Litematica and makes it the selected placement at {@code origin}. Returns what went wrong,
     * or nothing when it worked. The file is left as it is either way.
     */
    public static Optional<Component> place(Path file, BlockPos origin, String name) {
        if (!isAvailable()) return Optional.of(Component.translatable("export.chromastudio.litematica.missing"));
        Minecraft mc = Minecraft.getInstance();
        // Litematica's placements belong to the client thread.
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

    /** The only code that names Litematica's classes. Enter it only after {@link #isAvailable}, inside a try. */
    private static final class Calls {
        static Path schematicsDir() { return DataManager.getSchematicsBaseDirectory(); }

        /** False when Litematica couldn't read the file. */
        static boolean place(Path file, BlockPos origin, String name) {
            LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file);
            if (schematic == null) return false;
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            manager.addSchematicPlacement(placement, true);
            manager.setSelectedSchematicPlacement(placement);
            return true;
        }
    }
}
