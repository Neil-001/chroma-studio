package dev.chromastudio.client.action;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.PaletteSink;
import dev.chromastudio.client.ui.Hud;
import dev.chromastudio.core.palette.PaletteEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Makes a palette from the blocks in a box of the world. Armed from the Palettes tab or by the capture key, it takes
 * the next two presses of the eyedropper or capture key as the box's corners, outlining the box meanwhile.
 * {@code /chroma capture} does the same from six numbers.
 */
public final class Capture {
    private Capture() {}

    /** The most blocks a box may hold. Counting happens in one go on the client thread, and this keeps it brief. */
    public static final long MAX_VOLUME = 4_000_000;
    private static final int OUTLINE = 0xFFFFFFFF;

    private static boolean armed;
    private static BlockPos first;
    /** The world the capture was armed in. Coordinates picked there mean nothing in another. */
    private static ClientLevel armedIn;
    /** What the eyedropper key did before the capture borrowed it. */
    private static Runnable eyedropper;

    /** Gives the capture key its action and adds {@code /chroma capture}. Call once at client start. */
    public static void install() {
        Keys.onCapture = () -> {
            if (armed) corner();
            else arm();
        };
        ChromaCommand.SUBCOMMANDS.add(new ChromaCommand.Subcommand() {
            @Override
            public <S> void attach(LiteralArgumentBuilder<S> root) {
                RequiredArgumentBuilder<S, Integer> last = RequiredArgumentBuilder.<S, Integer>argument("z2", IntegerArgumentType.integer()).executes(Capture::command);
                for (String name : List.of("y2", "x2", "z1", "y1", "x1")) {
                    last = RequiredArgumentBuilder.<S, Integer>argument(name, IntegerArgumentType.integer()).then(last);
                }
                root.then(LiteralArgumentBuilder.<S>literal("capture")
                        .executes(context -> { ChromaCommand.say(Component.translatable("command.chromastudio.capture.usage")); return 0; })
                        .then(last));
            }
        });
    }

    private static <S> int command(CommandContext<S> ctx) {
        BlockPos a = new BlockPos(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "y1"), IntegerArgumentType.getInteger(ctx, "z1"));
        BlockPos b = new BlockPos(IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "y2"), IntegerArgumentType.getInteger(ctx, "z2"));
        Result result = capture(a, b);
        ChromaCommand.say(result.message);
        if (result.made) ChromaStudioClient.openWindowSoon("palettes");
        return result.made ? 1 : 0;
    }

    public static boolean armed() { return armed; }

    /** Starts waiting for the two corners. The eyedropper key picks them until the capture is done or cancelled. */
    public static void arm() {
        if (armed || Minecraft.getInstance().player == null) return;
        armed = true;
        first = null;
        armedIn = Minecraft.getInstance().level;
        eyedropper = Keys.onEyedropper;
        Keys.onEyedropper = Capture::corner;
        prompt();
    }

    public static void cancel() {
        if (!armed) return;
        disarm();
        Hud.readout(Component.translatable("capture.chromastudio.cancelled"), Component.empty(), 2);
    }

    private static void disarm() {
        armed = false;
        first = null;
        armedIn = null;
        Keys.onEyedropper = eyedropper;
        eyedropper = null;
    }

    private static void prompt() {
        Component keys = Component.translatable("capture.chromastudio.keys", Keys.EYEDROPPER.getTranslatedKeyMessage());
        Hud.readout(Component.translatable(first == null ? "capture.chromastudio.first" : "capture.chromastudio.second"), keys, 2);
    }

    /** The block the player looks at within reach, or failing that where they stand, so a corner can be in mid-air. */
    private static BlockPos aimed(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) return hit.getBlockPos();
        return mc.player.blockPosition();
    }

    private static void corner() {
        Minecraft mc = Minecraft.getInstance();
        if (!armed || mc.player == null) return;
        BlockPos at = aimed(mc).immutable();
        if (first == null) {
            first = at;
            prompt();
            return;
        }
        BlockPos from = first;
        disarm();
        Result result = capture(from, at);
        Hud.readout(result.message, Component.empty(), 4);
        if (result.made) ChromaStudioClient.openWindow();
    }

    /**
     * Call every client tick. Keeps the prompt up while armed, and cancels on Escape, which opens the pause menu, on
     * leaving the world, and on going to another dimension.
     */
    public static void tick(Minecraft mc) {
        if (!armed) return;
        if (mc.player == null) disarm();
        else if (mc.level != armedIn || mc.screen instanceof PauseScreen) cancel();
        else prompt();
    }

    /**
     * Outlines the box being picked: the first corner to the block aimed at, or just the block aimed at before the
     * first corner. Each loader calls this after the game has drawn the entities; {@code cam} is the camera. The
     * lines go straight to the game's buffers, as 1.21.11 has no collector a mod can hand an outline to there.
     */
    public static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (!armed || mc.player == null) return;
        BlockPos a = aimed(mc), b = first == null ? a : first;
        int x = Math.min(a.getX(), b.getX()), y = Math.min(a.getY(), b.getY()), z = Math.min(a.getZ(), b.getZ());
        int sx = Math.abs(a.getX() - b.getX()) + 1, sy = Math.abs(a.getY() - b.getY()) + 1, sz = Math.abs(a.getZ() - b.getZ()) + 1;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        ShapeRenderer.renderShape(pose, buffers.getBuffer(RenderTypes.lines()), Shapes.create(0, 0, 0, sx, sy, sz), 0, 0, 0, OUTLINE,
                mc.getWindow().getAppropriateLineWidth());
        pose.popPose();
        buffers.endBatch(RenderTypes.lines());
    }

    /** What a capture came to: a line to show the player, and whether a palette was made. */
    public record Result(Component message, boolean made) {}

    /**
     * Counts the block states in the loaded chunks of the box between two corners, air left out, and makes a palette
     * of the commonest, each weighted by how often it turned up against the rarest kept. A state is kept as it stands
     * in the world, with every property that isn't its block's default.
     */
    public static Result capture(BlockPos a, BlockPos b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        PaletteSink sink = PaletteSink.current();
        if (level == null || sink == null) return new Result(Component.translatable("palettes.chromastudio.no_world"), false);
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.max(level.getMinY(), Math.min(a.getY(), b.getY())), z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()), y1 = Math.min(level.getMaxY(), Math.max(a.getY(), b.getY())), z1 = Math.max(a.getZ(), b.getZ());
        // A box wholly above or below the world holds nothing, however wide it is.
        if (y1 < y0) return new Result(Component.translatable("capture.chromastudio.empty"), false);
        // Each side is checked on its own first: three sides typed into the command can multiply past what a long holds.
        long sx = (long) x1 - x0 + 1, sy = (long) y1 - y0 + 1, sz = (long) z1 - z0 + 1;
        if (sx > MAX_VOLUME || sz > MAX_VOLUME || sx * sz > MAX_VOLUME || sx * sz * sy > MAX_VOLUME) {
            return new Result(Component.translatable("capture.chromastudio.too_big", MAX_VOLUME), false);
        }

        Map<BlockState, PaletteEntry> entries = new IdentityHashMap<>();
        Map<PaletteEntry, Integer> counts = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Counted by offset from the corner: a coordinate at the very end of the int range can't be stepped past.
        for (int dx = 0; dx < sx; dx++)
            for (int dz = 0; dz < sz; dz++) {
                int x = x0 + dx, z = z0 + dz;
                if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
                for (int y = y0; y <= y1; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;
                    counts.merge(entries.computeIfAbsent(state, PaletteBlocks::of), 1, Integer::sum);
                }
            }
        if (counts.isEmpty()) return new Result(Component.translatable("capture.chromastudio.empty"), false);

        List<Map.Entry<PaletteEntry, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Map.Entry.<PaletteEntry, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(e -> e.getKey().idWithProperties()));
        ranked = ranked.subList(0, Math.min(ModSettings.captureCount, ranked.size()));
        int rarest = ranked.getLast().getValue();
        List<PaletteEntry> kept = new ArrayList<>();
        for (var e : ranked) kept.add(e.getKey().withWeight(Math.max(1, Math.round(e.getValue() / (float) rarest))));

        String name = Component.translatable("capture.chromastudio.name", x0, y0, z0, x1, y1, z1).getString();
        if (!sink.createPalette(name, kept)) return new Result(Component.translatable("capture.chromastudio.failed"), false);
        return new Result(Component.translatable("capture.chromastudio.done", kept.size(), counts.size()), true);
    }

    /** Whether a capture could start: only in a world. */
    public static Optional<Component> unavailable() {
        return Minecraft.getInstance().player == null ? Optional.of(Component.translatable("palettes.chromastudio.no_world")) : Optional.empty();
    }
}
