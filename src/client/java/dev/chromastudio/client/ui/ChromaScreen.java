package dev.chromastudio.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.chromastudio.client.GearPlace;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.ThemeChoice;
import dev.chromastudio.client.TitleButtons;
import dev.chromastudio.client.action.ScreenPicker;
import dev.chromastudio.client.axiom.AxiomBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.sdl.SDLMouse;

public final class ChromaScreen extends Screen {
    public static Object pendingTarget;

    private static final int BUTTON_W = 20, BUTTON_W_MIN = 16, TAB_PAD = 12, TAB_PAD_MIN = 6, SETTINGS_GAP = 5, COG = 16, COG_MARGIN = 2;

    private record Registered(String id, Supplier<Tab> factory, int order) {}
    private static final List<Registered> REGISTRY = new ArrayList<>();
    private static String lastTab;
    private static ChromaScreen pinned;

    static {
        String[] ids = {"match", "gradient", "palettes", "explore"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            registerTab(id, () -> new PlaceholderTab(id), i * 10);
        }
    }

    public static void registerTab(String id, Supplier<Tab> factory, int order) {
        REGISTRY.removeIf(r -> r.id.equals(id));
        REGISTRY.add(new Registered(id, factory, order));
        REGISTRY.sort(Comparator.comparingInt(Registered::order));
    }

    public static List<String> tabIds() { return REGISTRY.stream().map(Registered::id).toList(); }

    public static void open(String tabId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ChromaScreen screen = current();
        if (screen == null) {
            screen = pinned != null ? pinned : new ChromaScreen();
            pinned = null;
            AxiomBridge.leaveEditorForWindow();
            mc.gui.setScreen(screen);
        }
        if (tabId != null) screen.select(tabId);
    }

    public static ChromaScreen current() { return Minecraft.getInstance().gui.screen() instanceof ChromaScreen s ? s : null; }

    public static boolean isPinned() { return pinned != null; }

    public static void togglePin() {
        ChromaScreen screen = current();
        if (screen != null) screen.pin();
        else if (pinned != null) {
            pinned.tabs.values().forEach(Tab::closed);
            pinned = null;
        } else if (Minecraft.getInstance().player != null) pinned = new ChromaScreen();
    }

    private Window window = new Window();
    private final Map<String, Tab> tabs = new HashMap<>();
    private String selected;
    private Bounds laidOut;
    private Tab laidOutTab;
    private boolean tabHasMouse;
    private boolean pinning;

    @SuppressWarnings("StaticAssignmentInConstructor")
    private ChromaScreen() {
        super(Component.translatable("screen.chromastudio.title"));
        selected = tab(lastTab) != null ? lastTab : tab(ModSettings.startTab) != null ? ModSettings.startTab : REGISTRY.getFirst().id;
        lastTab = selected;
    }

    public Tab tab(String id) {
        if (id == null) return null;
        Tab made = tabs.get(id);
        if (made != null) return made;
        for (Registered r : REGISTRY)
            if (r.id.equals(id)) {
                Tab tab = r.factory.get();
                tabs.put(id, tab);
                return tab;
            }
        return null;
    }

    public void select(String id) {
        if (tab(id) == null) return;
        selected = id;
        lastTab = id;
        tabHasMouse = false;
        layout(false);
    }

    private Tab tab() { return tab(selected); }
    private boolean fullscreen() { return window.fullscreen(width, height); }
    private Bounds frame() { return window.frame(width, height); }
    private Bounds content() { return window.content(width, height); }

    private void layout(boolean force) {
        Bounds b = content();
        Tab tab = tab();
        if (!force && b.equals(laidOut) && tab == laidOutTab) return;
        laidOut = b;
        laidOutTab = tab;
        tab.init(b);
    }

    @Override
    protected void init() { layout(true); }

    @Override
    protected void repositionElements() { layout(true); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (fullscreen() && !ScreenPicker.armed()) super.extractBackground(g, mouseX, mouseY, delta);
        else minecraft.gui.hud.extractDeferredSubtitles();
    }

    private Bounds cog() { return new Bounds(width - COG - COG_MARGIN, COG_MARGIN, COG, COG); }

    private static boolean gearInBar(boolean fullscreen) { return fullscreen || ModSettings.gearPlace == GearPlace.TITLE_BAR; }

    private static int cogReserve(Bounds frame, int screenW, boolean fullscreen) {
        if (gearInBar(fullscreen)) return 0;
        boolean under = frame.y() < COG + 2 * COG_MARGIN && frame.right() > screenW - COG - 2 * COG_MARGIN;
        return under ? frame.right() - (screenW - COG - 2 * COG_MARGIN) : 0;
    }

    private static Bounds button(Bounds frame, int screenW, boolean fullscreen, int which) {
        int fromRight = ModSettings.titleButtons == TitleButtons.FLIPPED ? which : switch (which) {
            case PIN -> FULLSCREEN;
            case FULLSCREEN -> PIN;
            default -> which;
        };
        int gap = which == SETTINGS ? SETTINGS_GAP : 0, w = buttonW(frame, screenW, fullscreen);
        return new Bounds(frame.right() - cogReserve(frame, screenW, fullscreen) - (fromRight + 1) * w - gap, frame.y(), w, Window.TITLE_H);
    }

    private static int buttonsLeft(Bounds frame, int screenW, boolean fullscreen, int w) {
        return frame.right() - cogReserve(frame, screenW, fullscreen) - (gearInBar(fullscreen) ? 4 * w + SETTINGS_GAP : 3 * w);
    }

    private static int tabText() {
        Font font = Minecraft.getInstance().font;
        return REGISTRY.stream().mapToInt(r -> font.width(Component.translatable("tab.chromastudio." + r.id))).sum();
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static int buttonW(Bounds frame, int screenW, boolean fullscreen) {
        int tabs = frame.x() + 1 + tabText() + REGISTRY.size() * TAB_PAD_MIN;
        return tabs <= buttonsLeft(frame, screenW, fullscreen, BUTTON_W) ? BUTTON_W : BUTTON_W_MIN;
    }

    private static final int CLOSE = 0, PIN = 1, FULLSCREEN = 2, SETTINGS = 3;

    private static List<Bounds> tabRects(Bounds frame, int screenW, boolean fullscreen, boolean buttons, Font font) {
        List<Bounds> rects = new ArrayList<>();
        int x = frame.x() + 1;
        int end = buttons ? buttonsLeft(frame, screenW, fullscreen, buttonW(frame, screenW, fullscreen)) : frame.right() - 1;
        int space = end - x, room = space - tabText();
        int pad = Math.clamp(room / REGISTRY.size(), TAB_PAD_MIN, TAB_PAD);
        boolean squeezed = room < REGISTRY.size() * TAB_PAD_MIN;
        for (Registered r : REGISTRY) {
            int w = squeezed ? Math.max(0, space / REGISTRY.size()) : font.width(Component.translatable("tab.chromastudio." + r.id)) + pad;
            rects.add(new Bounds(x, frame.y(), w, Window.TITLE_H));
            x += w;
        }
        return rects;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (ScreenPicker.armed()) {
            ScreenPicker.render(g, mouseX, mouseY);
            g.requestCursor(CursorTypes.CROSSHAIR);
            return;
        }
        Theme.sync();
        Bounds frame = frame(), content = content();
        boolean free = !window.dragging() && !tabHasMouse;
        Window.Grip grip = window.dragging() ? window.grip() : free ? window.gripAt(mouseX, mouseY, width, height) : Window.Grip.NONE;
        boolean pointing = free && (grip == Window.Grip.NONE || grip == Window.Grip.MOVE);
        Component tip = chrome(g, frame, fullscreen(), 1, selected, true, pointing ? mouseX : -1, pointing ? mouseY : -1);
        int tipY = frame.y() + Window.TITLE_H + 14;

        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        tab().render(g, content, mouseX, mouseY, delta);
        g.disableScissor();

        boolean overCog = false;
        if (!gearInBar(fullscreen())) {
            Bounds cog = cog();
            overCog = free && cog.contains(mouseX, mouseY);
            cog(g, cog, overCog ? Theme.TEXT : Theme.DIM);
            if (overCog) {
                tip = Component.translatable("window.chromastudio.settings");
                tipY = cog.bottom() + 14;
            }
        }

        if (tip != null) {
            List<FormattedCharSequence> lines = Tooltip.splitTooltip(minecraft, tip);
            g.setTooltipForNextFrame(font, lines, mouseX, tipY);
        }
        CursorType cursor = cursor(grip);
        if (cursor != null && !overCog && (window.dragging() || grip != Window.Grip.MOVE || tip == null && !overTab(frame, mouseX, mouseY))) g.requestCursor(cursor);
    }

    private static final class Diagonal {
        static final CursorType NWSE = CursorType.createStandardCursor(SDLMouse.SDL_SYSTEM_CURSOR_NWSE_RESIZE, "resize_nwse", CursorTypes.ARROW);
        static final CursorType NESW = CursorType.createStandardCursor(SDLMouse.SDL_SYSTEM_CURSOR_NESW_RESIZE, "resize_nesw", CursorTypes.ARROW);
    }

    private static CursorType cursor(Window.Grip grip) {
        return switch (grip) {
            case N, S -> CursorTypes.RESIZE_NS;
            case E, W -> CursorTypes.RESIZE_EW;
            case NW, SE -> Diagonal.NWSE;
            case NE, SW -> Diagonal.NESW;
            case MOVE -> CursorTypes.RESIZE_ALL;
            default -> null;
        };
    }

    private static Component chrome(GuiGraphicsExtractor g, Bounds f, boolean fullscreen, float opacity, String selected, boolean buttons,
                                    int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = g.guiWidth(), screenH = g.guiHeight();
        if (!fullscreen) {
            g.fill(f.x(), f.y(), f.right(), f.bottom(), ARGB.multiplyAlpha(Theme.WINDOW, opacity));
            g.outline(f.x(), f.y(), f.w(), f.h(), ARGB.multiplyAlpha(Theme.BORDER, opacity));
        } else if (ModSettings.theme != ThemeChoice.DEFAULT) {
            g.fill(f.x(), f.y(), f.right(), f.bottom(), ARGB.multiplyAlpha(Theme.WINDOW, opacity));
        } else if (buttons) {
            g.fill(f.x(), f.y(), f.right(), f.bottom(), Theme.SCRIM);
        }
        g.fill(f.x() + (fullscreen ? 0 : 1), f.y() + (fullscreen ? 0 : 1), f.right() - (fullscreen ? 0 : 1), f.y() + Window.TITLE_H,
                ARGB.multiplyAlpha(Theme.BAR, opacity));
        g.fill(f.x() + (fullscreen ? 0 : 1), f.y() + Window.TITLE_H - 1, f.right() - (fullscreen ? 0 : 1), f.y() + Window.TITLE_H,
                ARGB.multiplyAlpha(Theme.BORDER, opacity));

        List<Bounds> rects = tabRects(f, screenW, fullscreen, buttons, font);
        for (int i = 0; i < rects.size(); i++) {
            Bounds r = rects.get(i);
            String id = REGISTRY.get(i).id;
            boolean on = id.equals(selected), over = r.contains(mouseX, mouseY);
            if (on) {
                g.fill(r.x(), r.y() + 1, r.right(), r.bottom(), ARGB.multiplyAlpha(Theme.SELECTED, opacity));
                g.fill(r.x(), r.bottom() - 2, r.right(), r.bottom(), ARGB.multiplyAlpha(Theme.ACCENT, opacity));
            } else if (over) g.fill(r.x(), r.y() + 1, r.right(), r.bottom(), ARGB.multiplyAlpha(Theme.CONTROL_HOVER, opacity));
            String label = fit(font, Component.translatable("tab.chromastudio." + id).getString(), r.w() - 4);
            g.text(font, label, r.x() + (r.w() - font.width(label)) / 2, r.y() + 4, ARGB.multiplyAlpha(on || over ? Theme.TEXT : Theme.SOFT, opacity), false);
        }

        if (!buttons) return null;
        if (!fullscreen) grip(g, f, Theme.DIM);
        boolean inBar = gearInBar(fullscreen);
        if (inBar) {
            Bounds settings = button(f, screenW, fullscreen, SETTINGS);
            g.fill(settings.right() + SETTINGS_GAP / 2, f.y() + 4, settings.right() + SETTINGS_GAP / 2 + 1, f.y() + Window.TITLE_H - 4, Theme.BORDER);
        }
        Component tip = null;
        boolean forced = Window.forced(screenW, screenH), inWorld = mc.level != null;
        for (int which = CLOSE; which <= (inBar ? SETTINGS : FULLSCREEN); which++) {
            Bounds b = button(f, screenW, fullscreen, which);
            boolean usable = which == FULLSCREEN ? !forced : which != PIN || inWorld;
            boolean over = b.contains(mouseX, mouseY);
            if (over && usable) g.fill(b.x(), b.y() + 1, b.right(), b.bottom(), ARGB.multiplyAlpha(which == CLOSE ? Theme.DANGER : Theme.CONTROL, opacity));
            int c = ARGB.multiplyAlpha(!usable ? Theme.GREYED : over ? Theme.TEXT : Theme.SOFT, opacity);
            int cx = b.x() + b.w() / 2, cy = b.y() + b.h() / 2;
            switch (which) {
                case CLOSE -> {
                    for (int i = -3; i <= 2; i++) {
                        g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
                        g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
                    }
                }
                case PIN -> {
                    g.fill(cx - 2, cy - 5, cx + 2, cy - 4, c);
                    g.fill(cx - 1, cy - 4, cx + 1, cy - 1, c);
                    g.fill(cx - 3, cy - 1, cx + 3, cy + 1, c);
                    g.fill(cx, cy + 1, cx + 1, cy + 5, c);
                }
                case SETTINGS -> gear(g, cx, cy, c, ARGB.multiplyAlpha(over ? Theme.CONTROL : Theme.BAR, opacity));
                default -> {
                    if (fullscreen) {
                        g.outline(cx - 2, cy - 4, 6, 6, c);
                        g.fill(cx - 4, cy - 2, cx + 2, cy + 4, ARGB.multiplyAlpha(Theme.BAR, opacity));
                        g.outline(cx - 4, cy - 2, 6, 6, c);
                    } else {
                        g.outline(cx - 4, cy - 4, 8, 8, c);
                        g.fill(cx - 4, cy - 4, cx + 4, cy - 2, c);
                    }
                }
            }
            if (over) tip = Component.translatable(switch (which) {
                case CLOSE -> "window.chromastudio.close";
                case PIN -> inWorld ? "window.chromastudio.pin" : "window.chromastudio.pin.no_world";
                case SETTINGS -> "window.chromastudio.settings";
                default -> forced ? "window.chromastudio.fullscreen.forced" : fullscreen ? "window.chromastudio.windowed" : "window.chromastudio.fullscreen";
            });
        }
        return tip;
    }

    private static void gear(GuiGraphicsExtractor g, int cx, int cy, int colour, int hole) {
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, colour);
        g.fill(cx - 1, cy - 5, cx + 1, cy + 5, colour);
        g.fill(cx - 5, cy - 1, cx + 5, cy + 1, colour);
        g.fill(cx - 4, cy - 4, cx - 2, cy - 2, colour);
        g.fill(cx + 2, cy - 4, cx + 4, cy - 2, colour);
        g.fill(cx - 4, cy + 2, cx - 2, cy + 4, colour);
        g.fill(cx + 2, cy + 2, cx + 4, cy + 4, colour);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, hole);
    }

    private static void cog(GuiGraphicsExtractor g, Bounds b, int colour) {
        int cx = b.x() + b.w() / 2, cy = b.y() + b.h() / 2;
        g.fill(b.x(), b.y(), b.right(), b.bottom(), Theme.BAR);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, colour);
        g.fill(cx - 1, cy - 6, cx + 1, cy + 6, colour);
        g.fill(cx - 6, cy - 1, cx + 6, cy + 1, colour);
        g.fill(cx - 5, cy - 5, cx - 3, cy - 3, colour);
        g.fill(cx + 3, cy - 5, cx + 5, cy - 3, colour);
        g.fill(cx - 5, cy + 3, cx - 3, cy + 5, colour);
        g.fill(cx + 3, cy + 3, cx + 5, cy + 5, colour);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, Theme.BAR);
    }

    private static void grip(GuiGraphicsExtractor g, Bounds f, int colour) {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j <= i; j++) {
                int x = f.right() - 3 - 3 * j, y = f.bottom() - 3 - 3 * (i - j);
                g.fill(x - 1, y - 1, x, y, colour);
            }
    }

    private boolean overTab(Bounds frame, int mx, int my) {
        for (Bounds r : tabRects(frame, width, fullscreen(), true, font)) if (r.contains(mx, my)) return true;
        return false;
    }

    static void renderPinned(GuiGraphicsExtractor g) {
        ChromaScreen s = pinned;
        if (s == null || current() != null) return;
        Theme.sync();
        s.width = g.guiWidth();
        s.height = g.guiHeight();
        s.layout(false);
        float opacity = (float) ModSettings.pinOpacity;
        chrome(g, s.frame(), s.fullscreen(), opacity, s.selected, false, -1, -1);
        Bounds content = s.content();
        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        s.tab().renderPinned(g, content, opacity);
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (ScreenPicker.armed()) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) ScreenPicker.click(mx, my);
            else ScreenPicker.cancel();
            return true;
        }
        Bounds frame = frame();
        tabHasMouse = false;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            boolean fullscreen = fullscreen(), inBar = gearInBar(fullscreen);
            if (!inBar && cog().contains(mx, my)) {
                minecraft.gui.setScreen(new SettingsScreen(this));
                return true;
            }
            Window.Grip grip = window.gripAt(mx, my, width, height);
            if (grip != Window.Grip.NONE && grip != Window.Grip.MOVE) {
                window.grab(grip, mx, my, width, height);
                return true;
            }
            if (inBar && button(frame, width, fullscreen, SETTINGS).contains(mx, my)) {
                minecraft.gui.setScreen(new SettingsScreen(this));
                return true;
            }
            if (button(frame, width, fullscreen, CLOSE).contains(mx, my)) {
                onClose();
                return true;
            }
            if (button(frame, width, fullscreen, PIN).contains(mx, my)) {
                if (minecraft.level != null) pin();
                return true;
            }
            if (button(frame, width, fullscreen, FULLSCREEN).contains(mx, my)) {
                toggleFullscreen();
                return true;
            }
            List<Bounds> rects = tabRects(frame, width, fullscreen, true, font);
            for (int i = 0; i < rects.size(); i++)
                if (rects.get(i).contains(mx, my)) {
                    select(REGISTRY.get(i).id);
                    return true;
                }
            if (grip == Window.Grip.MOVE) {
                window.grab(grip, mx, my, width, height);
                return true;
            }
        }
        tabHasMouse = content().contains(mx, my) && tab().mouseClicked(event, doubled);
        return tabHasMouse || windowKey(key -> key.matchesMouse(event));
    }

    private boolean windowKey(Predicate<KeyMapping> pressed) {
        if (pressed.test(Keys.OPEN)) onClose();
        else if (pressed.test(Keys.PIN)) {
            if (minecraft.level != null) pin();
        } else if (pressed.test(Keys.FULLSCREEN)) toggleFullscreen();
        else return false;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (window.dragging()) {
            if (window.dragTo(event.x(), event.y(), width, height)) layout(false);
            return true;
        }
        return tabHasMouse && tab().mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (window.dragging()) {
            window.release();
            store();
            return true;
        }
        boolean had = tabHasMouse;
        tabHasMouse = false;
        return (had || content().contains(event.x(), event.y())) && tab().mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        return content().contains(mx, my) && tab().mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ScreenPicker.armed()) {
            if (event.isEscape()) ScreenPicker.cancel();
            return true;
        }
        Tab tab = tab();
        if (event.isEscape()) {
            if (!tab.dismiss()) onClose();
            return true;
        }
        if (tab.keyPressed(event)) return true;
        return !tab.typing() && windowKey(key -> key.matches(event));
    }

    @Override
    public boolean charTyped(CharacterEvent event) { return tab().charTyped(event); }

    public void toggleFullscreen() {
        if (Window.forced(width, height)) return;
        window.toggleFullscreen();
        store();
        layout(false);
    }

    public void pin() {
        pinning = true;
        pinned = this;
        minecraft.gui.setScreen(null);
    }

    void reloadWindow() { window = new Window(); }

    private void store() {
        window.store();
        ModSettings.save();
    }

    @Override
    public void tick() { tab().tick(); }

    @Override
    public void removed() {
        ScreenPicker.cancel();
        window.release();
        tabHasMouse = false;
        store();
        if (!pinning) tabs.values().forEach(Tab::closed);
        pinning = false;
        super.removed();
    }
}
