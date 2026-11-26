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
import dev.chromastudio.client.ui.Theme;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class Capture {
    private Capture() {}

    public static final long MAX_VOLUME = 4_000_000;
    private static final int OUTLINE = 0xFFFFFFFF, SHADOW = 0xFF000000, FIRST = 0xFF000000 | Theme.TIP_PROMPT;
    private static final double HALO = 0.012, INSET = 0.016;

    private static boolean armed;
    private static BlockPos first;
    private static ClientLevel armedIn;
    private static Runnable eyedropper;

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
        ChromaCommand.say(result.detail == null ? result.message : result.message.copy().append(" ").append(result.detail));
        if (result.made) ChromaStudioClient.openWindowSoon("palettes");
        return result.made ? 1 : 0;
    }

    public static boolean armed() { return armed; }

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
        Hud.readout(Hud.Kind.PROMPT, Component.translatable("capture.chromastudio.cancelled"), null, 2);
    }

    private static void disarm() {
        armed = false;
        first = null;
        armedIn = null;
        Keys.onEyedropper = eyedropper;
        eyedropper = null;
    }

    private static void prompt() {
        Minecraft mc = Minecraft.getInstance();
        Component key = Hud.key(Keys.EYEDROPPER);
        Component keys = Component.translatable(!aimsAtBlock(mc) ? "capture.chromastudio.keys.feet"
                : first == null ? "capture.chromastudio.keys" : "capture.chromastudio.keys.second", key);
        Component step;
        if (first == null) step = Component.translatable("capture.chromastudio.first");
        else {
            int[] size = size(mc.level, first, aimed(mc));
            step = Component.translatable("capture.chromastudio.second.size", size[0], size[1], size[2]);
        }
        Hud.readout(step, keys, 2);
    }

    private static int[] size(ClientLevel level, BlockPos a, BlockPos b) {
        int y0 = Math.max(level.getMinY(), Math.min(a.getY(), b.getY())), y1 = Math.min(level.getMaxY(), Math.max(a.getY(), b.getY()));
        return new int[] {Math.abs(a.getX() - b.getX()) + 1, Math.max(0, y1 - y0 + 1), Math.abs(a.getZ() - b.getZ()) + 1};
    }

    private static boolean aimsAtBlock(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
    }

    private static BlockPos aimed(Minecraft mc) {
        if (aimsAtBlock(mc)) return ((BlockHitResult) mc.hitResult).getBlockPos();
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
        Hud.readout(result.made ? Hud.Kind.DONE : Hud.Kind.PROBLEM, result.message, result.detail, 4);
        if (result.made) ChromaStudioClient.openWindow();
    }

    public static void tick(Minecraft mc) {
        if (!armed) return;
        if (mc.player == null) disarm();
        else if (mc.level != armedIn || mc.gui.screen() instanceof PauseScreen) cancel();
        else prompt();
    }

    public static void render(SubmitNodeCollector out, PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (!armed || mc.player == null) return;
        BlockPos a = aimed(mc), b = first == null ? a : first;
        int x = Math.min(a.getX(), b.getX()), y = Math.min(a.getY(), b.getY()), z = Math.min(a.getZ(), b.getZ());
        int sx = Math.abs(a.getX() - b.getX()) + 1, sy = Math.abs(a.getY() - b.getY()) + 1, sz = Math.abs(a.getZ() - b.getZ()) + 1;
        pose.pushPose();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        float width = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        outline(out, pose, sx, sy, sz, OUTLINE, width, width * 1.5f);
        if (first != null) {
            pose.translate(first.getX() - x, first.getY() - y, first.getZ() - z);
            outline(out, pose, 1, 1, 1, FIRST, width, width * 2);
        }
        pose.popPose();
    }

    private static void outline(SubmitNodeCollector out, PoseStack pose, int sx, int sy, int sz, int colour, float width, float lineWidth) {
        out.submitShapeOutline(pose, Shapes.create(-HALO, -HALO, -HALO, sx + HALO, sy + HALO, sz + HALO), RenderTypes.lines(), SHADOW, width * 3, true);
        out.submitShapeOutline(pose, Shapes.create(-INSET, -INSET, -INSET, sx + INSET, sy + INSET, sz + INSET), RenderTypes.lines(), colour, lineWidth, true);
    }

    public record Result(Component message, Component detail, boolean made) {
        Result(Component message) { this(message, null, false); }
    }

    public static Result capture(BlockPos a, BlockPos b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        PaletteSink sink = PaletteSink.current();
        if (level == null || sink == null) return new Result(Component.translatable("palettes.chromastudio.no_world"));
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.max(level.getMinY(), Math.min(a.getY(), b.getY())), z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()), y1 = Math.min(level.getMaxY(), Math.max(a.getY(), b.getY())), z1 = Math.max(a.getZ(), b.getZ());
        if (y1 < y0) return new Result(Component.translatable("capture.chromastudio.empty"));
        long sx = (long) x1 - x0 + 1, sy = (long) y1 - y0 + 1, sz = (long) z1 - z0 + 1;
        if (sx > MAX_VOLUME || sz > MAX_VOLUME || sx * sz > MAX_VOLUME || sx * sz * sy > MAX_VOLUME) {
            return new Result(Component.translatable("capture.chromastudio.too_big", MAX_VOLUME));
        }

        Map<BlockState, PaletteEntry> entries = new IdentityHashMap<>();
        Map<PaletteEntry, Integer> counts = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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
        if (counts.isEmpty()) return new Result(Component.translatable("capture.chromastudio.empty"));

        List<Map.Entry<PaletteEntry, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Map.Entry.<PaletteEntry, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(e -> e.getKey().idWithProperties()));
        ranked = ranked.subList(0, Math.min(ModSettings.captureCount, ranked.size()));
        int rarest = ranked.getLast().getValue();
        List<PaletteEntry> kept = new ArrayList<>();
        for (var e : ranked) kept.add(e.getKey().withWeight(Math.max(1, Math.round(e.getValue() / (float) rarest))));

        String name = Component.translatable("capture.chromastudio.name", x0, y0, z0, x1, y1, z1).getString();
        if (!sink.createPalette(name, kept)) return new Result(Component.translatable("capture.chromastudio.failed"));
        Component done = kept.size() == counts.size() ? Component.translatable("capture.chromastudio.done.all", kept.size())
                : Component.translatable("capture.chromastudio.done", kept.size(), counts.size());
        return new Result(done,
                Component.translatable("capture.chromastudio.saved", sx, sy, sz, Hud.key(Keys.OPEN)), true);
    }

    public static Optional<Component> unavailable() {
        return Minecraft.getInstance().player == null ? Optional.of(Component.translatable("palettes.chromastudio.no_world")) : Optional.empty();
    }
}
