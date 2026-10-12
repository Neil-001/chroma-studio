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
import dev.chromastudio.client.index.Entry;
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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
                        .executes(_ -> { ChromaCommand.say(Component.translatable("command.chromastudio.capture.usage")); return 0; })
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

    /** Call every client tick. Keeps the prompt up while armed, and cancels on Escape, which opens the pause menu, or on leaving the world. */
    public static void tick(Minecraft mc) {
        if (!armed) return;
        if (mc.player == null) disarm();
        else if (mc.gui.screen() instanceof PauseScreen) cancel();
        else prompt();
    }

    /**
     * Outlines the box being picked: the first corner to the block aimed at, or just the block aimed at before the
     * first corner. Each loader calls this where it collects the world's extra geometry; {@code cam} is the camera.
     */
    public static void render(SubmitNodeCollector out, PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (!armed || mc.player == null) return;
        BlockPos a = aimed(mc), b = first == null ? a : first;
        int x = Math.min(a.getX(), b.getX()), y = Math.min(a.getY(), b.getY()), z = Math.min(a.getZ(), b.getZ());
        int sx = Math.abs(a.getX() - b.getX()) + 1, sy = Math.abs(a.getY() - b.getY()) + 1, sz = Math.abs(a.getZ() - b.getZ()) + 1;
        pose.pushPose();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        out.submitShapeOutline(pose, Shapes.create(0, 0, 0, sx, sy, sz), RenderTypes.lines(), OUTLINE,
                mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth, true);
        pose.popPose();
    }

    /** What a capture came to: a line to show the player, and whether a palette was made. */
    public record Result(Component message, boolean made) {}

    /**
     * Counts the block states in the loaded chunks of the box between two corners, air left out, and makes a palette
     * of the commonest, each weighted by how often it turned up against the rarest kept.
     */
    public static Result capture(BlockPos a, BlockPos b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        PaletteSink sink = PaletteSink.current();
        if (level == null || sink == null) return new Result(Component.translatable("give.chromastudio.no_world"), false);
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.max(level.getMinY(), Math.min(a.getY(), b.getY())), z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()), y1 = Math.min(level.getMaxY(), Math.max(a.getY(), b.getY())), z1 = Math.max(a.getZ(), b.getZ());
        long volume = (x1 - x0 + 1L) * Math.max(0, y1 - y0 + 1L) * (z1 - z0 + 1L);
        if (volume > MAX_VOLUME) return new Result(Component.translatable("capture.chromastudio.too_big", volume, MAX_VOLUME), false);

        Map<BlockState, PaletteEntry> entries = new IdentityHashMap<>();
        Map<PaletteEntry, Integer> counts = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
                for (int y = y0; y <= y1; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;
                    counts.merge(entries.computeIfAbsent(state, Capture::entryFor), 1, Integer::sum);
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

    /** The palette entry a state counts as: the index's entry for it, so states that look alike count together. */
    private static PaletteEntry entryFor(BlockState state) {
        Entry entry = PaletteBlocks.entry(state);
        return entry != null ? PaletteBlocks.of(entry) : new PaletteEntry(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), "");
    }

    /** Whether a capture could start: only in a world. */
    public static Optional<Component> unavailable() {
        return Minecraft.getInstance().player == null ? Optional.of(Component.translatable("give.chromastudio.no_world")) : Optional.empty();
    }
}
