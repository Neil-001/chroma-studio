package dev.chromastudio.client.axiom;

import static dev.chromastudio.client.axiom.AxiomTools.tr;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.Eyedropper;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.client.ui.tabs.MatchState;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Face;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiCol;
import imgui.moulberry92.flag.ImGuiColorEditFlags;
import imgui.moulberry92.flag.ImGuiHoveredFlags;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class EyedropperTool implements CustomTool, GuardedTool.PutAway {
    private static final int RECENT = 5;
    private static final long NOTE_MS = 2500;
    private static final int CLOSEST = 3;

    private record Picked(Color colour, String name, Face face, Entry entry, BlockState state, BlockPos pos) {
        String detail() { return tr("axiom.chromastudio.eyedropper.detail", Eyedropper.faceName(face), colour.hex()); }

        boolean same(Picked o) { return entry == o.entry && state == o.state && colour.equals(o.colour); }
    }

    private record Near(Color colour, String name) {}

    private record Look(BlockPos pos, Face face, BlockState state, int version, @Nullable Picked picked, String name) {}

    private @Nullable Picked last;
    private final List<Picked> recent = new ArrayList<>();
    private @Nullable Look hovered;
    private boolean overPanel;
    private @Nullable String missed, missedName;
    private long missedUntil, copiedUntil;
    private @Nullable Runnable later;
    private List<Near> closest = List.of();
    private int searches;
    private WeakReference<ClientLevel> seenIn = new WeakReference<>(null);
    private final BooleanRegion one = AxiomTools.regions.createBoolean();

    EyedropperTool() {
        one.add(0, 0, 0);
    }

    @Override
    public String name() { return tr("axiom.chromastudio.eyedropper"); }

    @Override
    public void reset() { hovered = null; }

    @Override
    public void putAway() { hovered = null; }

    private void sync() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == seenIn.get()) return;
        seenIn = new WeakReference<>(level);
        last = null;
        recent.clear();
        closest = List.of();
        hovered = null;
        missed = null;
        searches++;
    }

    @Override
    public boolean callUseTool() {
        if (AxiomTools.off()) return false;
        sync();
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            miss(null, tr("axiom.chromastudio.eyedropper.missed"));
            return false;
        }
        if (Eyedropper.read(hit, false) && MatchState.entry() != null) {
            pick(new Picked(MatchState.colour(), MatchState.entry().name().getString(), MatchState.entryFace(),
                    MatchState.entry(), MatchState.entryState(), hit.getBlockPos().immutable()));
        } else {
            Look look = look(hit);
            if (look == null) miss(null, tr("axiom.chromastudio.not_ready"));
            else miss(look.name(), tr("axiom.chromastudio.eyedropper.no_colour"));
        }
        return true;
    }

    private void miss(@Nullable String name, String why) {
        missedName = name;
        missed = why;
        missedUntil = Util.getMillis() + NOTE_MS;
    }

    private void pick(Picked p) {
        last = p;
        missed = null;
        copiedUntil = Util.getMillis() + NOTE_MS;
        recent.removeIf(p::same);
        recent.addFirst(p);
        if (recent.size() > RECENT) recent.removeLast();
        search(p);
    }

    private void search(Picked p) {
        int run = ++searches;
        closest = List.of();
        BlockIndex index = BlockIndex.get();
        Target target = MatchState.target();
        Query query = MatchState.query(Integer.MAX_VALUE);
        int version = index.version();
        Minecraft mc = Minecraft.getInstance();
        ForkJoinPool.commonPool().execute(() -> {
            List<BlockIndex.Match> found = index.closest(target, query);
            mc.execute(() -> show(p, run, index, version, query, found));
        });
    }

    private void show(Picked p, int run, BlockIndex index, int version, Query query, List<BlockIndex.Match> found) {
        if (run != searches || index.version() != version) return;
        List<Near> near = new ArrayList<>();
        Set<String> shown = new HashSet<>(Set.of(index.material(p.entry())));
        for (BlockIndex.Match m : found) {
            if (near.size() == CLOSEST) break;
            BlockState state = m.entry().state();
            if (!shown.add(index.material(m.entry()))) continue;
            index.colourOf(state, m.face(), query.tint).ifPresent(c -> near.add(new Near(c, m.entry().name().getString())));
        }
        closest = List.copyOf(near);
    }

    private void pickAgain(Picked p) {
        MatchState.setEntry(p.entry(), p.state(), p.face(), TintContext.at(p.pos()));
        MatchState.remember();
        Clipboard.copy(p.colour().hex());
        pick(p);
    }

    @Override
    public void render(IAxiomWorldRenderContext rc) {
        if (AxiomTools.off()) return;
        sync();
        BlockHitResult hit = AxiomTools.tools.raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            if (!overPanel) hovered = null;
            return;
        }
        hovered = look(hit);
        one.render(rc, Vec3.atLowerCornerOf(hit.getBlockPos()), Effects.OUTLINE);
    }

    private @Nullable Look look(BlockHitResult hit) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockIndex index = BlockIndex.get();
        if (level == null || !index.ready()) return null;
        BlockPos pos = hit.getBlockPos();
        Face face = Face.values()[hit.getDirection().ordinal()];
        BlockState block = level.getBlockState(pos), state = block;
        Look old = hovered;
        if (old != null && old.pos().equals(pos) && old.face() == face && old.state() == block && old.version() == index.version()) return old;
        Entry entry = index.entryOf(state);
        FluidState fluid = level.getFluidState(pos);
        if (entry == null && !fluid.isEmpty()) {
            state = fluid.createLegacyBlock();
            entry = index.entryOf(state);
        }
        String name = (entry == null ? state.getBlock().getName() : entry.name()).getString();
        Picked picked = null;
        if (entry != null) {
            Color colour = index.colourOf(state, face, TintContext.at(pos)).orElse(null);
            if (colour != null) picked = new Picked(colour, name, face, entry, state, pos.immutable());
        }
        return new Look(pos.immutable(), face, block, index.version(), picked, name);
    }

    @Override
    public void displayImguiOptions() {
        overPanel = ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows);
        sync();
        Optional<Component> off = AxiomBridge.whyNot();
        if (off.isPresent()) {
            AxiomPanel.wrapped(off.get());
            return;
        }
        try {
            options();
        } finally {
            Runnable run = later;
            later = null;
            if (run != null) run.run();
        }
    }

    private void options() {
        AxiomPanel.heading(tr("axiom.chromastudio.eyedropper.picked"));
        Picked p = last;
        if (p == null) {
            row("picked", null, pickedSize(), () -> AxiomPanel.wrapped(tr("axiom.chromastudio.eyedropper.how")));
        } else {
            row("picked", p.colour(), pickedSize(), () -> {
                lines(p);
                if (AxiomPanel.button(tr("axiom.chromastudio.eyedropper.copy"), true, "")) later = () -> {
                    Clipboard.copy(p.colour().hex());
                    copiedUntil = Util.getMillis() + NOTE_MS;
                };
                if (Util.getMillis() < copiedUntil) {
                    String copied = tr("axiom.chromastudio.eyedropper.copied");
                    float right = ImGui.getItemRectMaxX() + ImGui.getStyle().getItemSpacingX() + ImGui.calcTextSizeX(copied);
                    if (right <= ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()) {
                        AxiomPanel.sameLine();
                        ImGui.alignTextToFramePadding();
                    }
                    AxiomPanel.text(copied);
                }
            });
            if (!closest.isEmpty()) {
                AxiomPanel.text(tr("axiom.chromastudio.eyedropper.closest"));
                for (int i = 0; i < closest.size(); i++) {
                    Near n = closest.get(i);
                    AxiomPanel.swatch("near" + i, new AxiomPanel.Swatch(n.colour(), ""));
                    AxiomPanel.sameLine();
                    ImGui.alignTextToFramePadding();
                    String hex = "  " + n.colour().hex();
                    AxiomPanel.text(AxiomPanel.fit(n.name(), ImGui.getContentRegionAvailX() - ImGui.calcTextSizeX(hex)) + hex);
                }
            }
        }
        String label = tr("axiom.chromastudio.eyedropper.match");
        String fitted = AxiomPanel.fit(label, ImGui.getContentRegionAvailX() - ImGui.getStyle().getFramePaddingX() * 2);
        String why = tr(p == null ? "axiom.chromastudio.eyedropper.match.none" : "axiom.chromastudio.eyedropper.match.tip");
        if (AxiomPanel.button(fitted + "###match", p != null, fitted.equals(label) ? why : label + "\n" + why) && p != null) later = () -> {
            MatchState.setEntry(p.entry(), p.state(), p.face(), TintContext.at(p.pos()));
            ChromaStudioClient.openWindowSoon("match");
        };

        AxiomPanel.heading(tr("axiom.chromastudio.eyedropper.pointing"));
        Look look = hovered;
        if (missed != null && Util.getMillis() < missedUntil) {
            String said = missed, name = missedName;
            row("pointing", null, pointingSize(), () -> {
                if (name == null) AxiomPanel.wrapped(said);
                else named(name, said);
            });
        } else if (!BlockIndex.get().ready()) {
            row("pointing", null, pointingSize(), () -> AxiomPanel.wrapped(tr("axiom.chromastudio.not_ready")));
        } else if (look == null) {
            row("pointing", null, pointingSize(), () -> AxiomPanel.wrapped(tr("axiom.chromastudio.eyedropper.point")));
        } else if (look.picked() == null) {
            row("pointing", null, pointingSize(), () -> named(look.name(), tr("axiom.chromastudio.eyedropper.no_colour")));
        } else {
            Picked h = look.picked();
            row("pointing", h.colour(), pointingSize(), () -> lines(h));
        }
        recent();
    }

    private void recent() {
        AxiomPanel.heading(tr("axiom.chromastudio.eyedropper.recent"));
        if (recent.isEmpty()) {
            AxiomPanel.wrapped(tr("axiom.chromastudio.eyedropper.recent.empty"));
            return;
        }
        AxiomPanel.wrapped(tr("axiom.chromastudio.eyedropper.recent.hint"));
        float size = ImGui.getFrameHeight();
        for (int i = 0; i < recent.size(); i++) {
            Picked r = recent.get(i);
            Color c = r.colour();
            boolean clicked = ImGui.colorButton("##recent" + i, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip, size, size);
            AxiomPanel.tip(c.hex());
            AxiomPanel.sameLine();
            String hex = "  " + c.hex();
            String label = AxiomPanel.fit(r.name(), ImGui.getContentRegionAvailX() - ImGui.calcTextSizeX(hex));
            clicked |= ImGui.selectable(label + hex + "##recentRow" + i, false, 0, 0, size);
            AxiomPanel.tip(label.equals(r.name()) ? r.detail() : r.name() + "\n" + r.detail());
            if (clicked) later = () -> pickAgain(r);
        }
    }

    private static void named(String name, String message) {
        float width = ImGui.getContentRegionAvailX();
        String fitted = AxiomPanel.fit(name, width);
        AxiomPanel.text(fitted);
        if (!fitted.equals(name)) AxiomPanel.tip(name);
        AxiomPanel.text(AxiomPanel.fit(message, width));
    }

    private static void lines(Picked p) {
        float width = ImGui.getContentRegionAvailX();
        String name = AxiomPanel.fit(p.name(), width), detail = AxiomPanel.fit(p.detail(), width);
        String whole = name.equals(p.name()) && detail.equals(p.detail()) ? "" : p.name() + "\n" + p.detail();
        AxiomPanel.text(name);
        AxiomPanel.tip(whole);
        AxiomPanel.text(detail);
        AxiomPanel.tip(whole);
    }

    private static float pickedSize() { return ImGui.getTextLineHeightWithSpacing() * 2 + ImGui.getFrameHeightWithSpacing(); }

    private static float pointingSize() { return ImGui.getTextLineHeightWithSpacing() * 3; }

    private static void row(String id, @Nullable Color c, float size, Runnable text) {
        if (c == null) {
            float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
            ImGui.dummy(size, size);
            int edge = ImGui.getColorU32(ImGuiCol.TextDisabled);
            ImGui.getWindowDrawList().addRect(x, y, x + size, y + size, edge);
            ImGui.getWindowDrawList().addLine(x + size, y, x, y + size, edge);
        } else {
            ImGui.colorButton("##" + id, c.red() / 255f, c.green() / 255f, c.blue() / 255f, 1f, ImGuiColorEditFlags.NoTooltip, size, size);
        }
        AxiomPanel.sameLine();
        ImGui.beginGroup();
        text.run();
        ImGui.endGroup();
    }
}
