package dev.chromastudio.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.action.ScreenPicker;
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

/**
 * The mod's window: a title bar with the tabs and the fullscreen, pin and close buttons, over the selected tab. The
 * title bar drags it and its edges resize it; {@link Window} keeps where it is. Windowed, the world shows around it and
 * the game carries on. Pinned, the screen closes and the tab is drawn over the world until the open key brings it back.
 */
public final class ChromaScreen extends Screen {
    /**
     * What {@code /chroma} asked for, for the Match tab to take when it opens: an {@link Integer} for a colour, packed
     * 0xRRGGBB, or a {@link String} for a block's full id such as {@code minecraft:oak_planks}. Null when nothing is
     * waiting; the tab that takes it sets it back to null.
     */
    public static Object pendingTarget;

    private static final int BUTTON_W = 16, COG = 16, COG_MARGIN = 2;
    private static final int PANEL = 0xF0101317, BAR = 0xFF1B2027, BORDER = 0xFF3A424D, HOVER = 0xFF2C343E,
            TEXT = 0xFFE4E9EF, DIM = 0xFF9AA5B3, GREYED = 0xFF4E5560, ACCENT = 0xFFFFB84D;

    private record Registered(String id, Supplier<Tab> factory, int order) {}
    private static final List<Registered> REGISTRY = new ArrayList<>();
    /** The tab the window last showed this session, or null before its first opening. */
    private static String lastTab;
    /** The window while it's pinned, or null. */
    private static ChromaScreen pinned;

    static {
        String[] ids = {"match", "gradient", "palettes", "explore"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            registerTab(id, () -> new PlaceholderTab(id), i * 10);
        }
    }

    /**
     * Adds a tab, or replaces the one with the same id. Tabs sit in the title bar by {@code order}, lowest first; the
     * built-in four are at 0, 10, 20 and 30. The factory runs once for each window that shows the tab.
     */
    public static void registerTab(String id, Supplier<Tab> factory, int order) {
        REGISTRY.removeIf(r -> r.id.equals(id));
        REGISTRY.add(new Registered(id, factory, order));
        REGISTRY.sort(Comparator.comparingInt(Registered::order));
    }

    /** Every tab's id, in title bar order. */
    public static List<String> tabIds() { return REGISTRY.stream().map(Registered::id).toList(); }

    /**
     * Opens the window on a tab, or switches to it when the window is already open. A null id means the tab last
     * shown, or the start tab from the settings the first time. A pinned window comes back as it was. Does nothing
     * outside a world.
     */
    public static void open(String tabId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ChromaScreen screen = current();
        if (screen == null) {
            screen = pinned != null ? pinned : new ChromaScreen();
            pinned = null;
            mc.gui.setScreen(screen);
        }
        if (tabId != null) screen.select(tabId);
    }

    /** The window, when it is the screen that's open, or null. */
    public static ChromaScreen current() { return Minecraft.getInstance().gui.screen() instanceof ChromaScreen s ? s : null; }

    public static boolean isPinned() { return pinned != null; }

    /**
     * Pins the open window; or, from the world, pins the window as it would open, or takes a pinned window away
     * without opening it.
     */
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
    /** The area and tab of the last {@link Tab#init}, so a tab is only laid out again when either changes. */
    private Bounds laidOut;
    private Tab laidOutTab;
    /** Whether the tab took the last click, so it gets the drags and the release wherever they happen. */
    private boolean tabHasMouse;
    /** Set while the screen closes to become pinned, so the tabs aren't told it closed. */
    private boolean pinning;

    private ChromaScreen() {
        super(Component.translatable("screen.chromastudio.title"));
        selected = tab(lastTab) != null ? lastTab : tab(ModSettings.startTab) != null ? ModSettings.startTab : REGISTRY.getFirst().id;
        lastTab = selected;
    }

    /** The tab with this id, made on first use, or null when no such tab is registered. */
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

    /** Switches to a tab. An unknown id changes nothing. */
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

    /** Lays the selected tab out when its area or the tab itself has changed, or always with {@code force}. */
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

    /** The game window changed size, or the settings screen handed back: either may have changed what the tab should show. */
    @Override
    protected void repositionElements() { layout(true); }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Windowed, the world shows as it is: no blur and no dimming. Fullscreen has the game's usual blurred backdrop. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // The screen picker reads the frame, so while it aims nothing of the window may be in it, the blur included.
        if (fullscreen() && !ScreenPicker.armed()) super.extractBackground(g, mouseX, mouseY, delta);
        else minecraft.gui.hud.extractDeferredSubtitles();
    }

    // ---- Title bar layout, shared by drawing and clicking ----

    private Bounds cog() { return new Bounds(width - COG - COG_MARGIN, COG_MARGIN, COG, COG); }

    /** How far the title bar's buttons move in from the frame's right edge, so the cog is never over them. */
    private static int cogReserve(Bounds frame, int screenW) {
        boolean under = frame.y() < COG + 2 * COG_MARGIN && frame.right() > screenW - COG - 2 * COG_MARGIN;
        return under ? frame.right() - (screenW - COG - 2 * COG_MARGIN) : 0;
    }

    /** The close, pin and fullscreen buttons, in that order from the right. */
    private static Bounds button(Bounds frame, int screenW, int fromRight) {
        return new Bounds(frame.right() - cogReserve(frame, screenW) - (fromRight + 1) * BUTTON_W, frame.y(), BUTTON_W, Window.TITLE_H);
    }

    private static final int CLOSE = 0, PIN = 1, FULLSCREEN = 2;

    private static List<Bounds> tabRects(Bounds frame, Font font) {
        List<Bounds> rects = new ArrayList<>();
        int x = frame.x() + 1;
        for (Registered r : REGISTRY) {
            int w = font.width(Component.translatable("tab.chromastudio." + r.id)) + 12;
            rects.add(new Bounds(x, frame.y(), w, Window.TITLE_H));
            x += w;
        }
        return rects;
    }

    // ---- Drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (ScreenPicker.armed()) {
            ScreenPicker.render(g, mouseX, mouseY);
            g.requestCursor(CursorTypes.CROSSHAIR);
            return;
        }
        Bounds frame = frame(), content = content();
        // A drag in progress keeps hold of the cursor's meaning, wherever the cursor has got to.
        boolean free = !window.dragging() && !tabHasMouse;
        Component tip = chrome(g, frame, fullscreen(), 1, selected, true, free ? mouseX : -1, free ? mouseY : -1);

        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        tab().render(g, content, mouseX, mouseY, delta);
        g.disableScissor();

        Bounds cog = cog();
        boolean overCog = free && cog.contains(mouseX, mouseY);
        cog(g, cog, overCog ? TEXT : DIM);
        if (overCog) tip = Component.translatable("window.chromastudio.settings");

        if (tip != null) {
            // Wrapped, as widgets' own are. The game draws a tooltip a little above the cursor and doesn't mind the
            // screen's top, so one for a button up there is asked for lower down, where all its lines fit.
            List<FormattedCharSequence> lines = Tooltip.splitTooltip(minecraft, tip);
            g.setTooltipForNextFrame(font, lines, mouseX, Math.max(mouseY, 16 + (lines.size() - 1) * 2));
        }
        CursorType cursor = cursor(free ? window.gripAt(mouseX, mouseY, width, height) : Window.Grip.NONE);
        if (cursor != null && !overCog) g.requestCursor(cursor);
    }

    private static CursorType cursor(Window.Grip grip) {
        return switch (grip) {
            case N, S -> CursorTypes.RESIZE_NS;
            case E, W -> CursorTypes.RESIZE_EW;
            case NE, NW, SE, SW -> CursorTypes.RESIZE_ALL;
            default -> null;
        };
    }

    /**
     * Draws the window's frame and title bar with every alpha scaled by {@code opacity}, and returns the tooltip of
     * the title bar button under the cursor, if any. The pinned window uses it too, without the buttons: they'd
     * look as if they could be clicked.
     */
    private static Component chrome(GuiGraphicsExtractor g, Bounds f, boolean fullscreen, float opacity, String selected, boolean buttons,
                                    int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = g.guiWidth(), screenH = g.guiHeight();
        if (!fullscreen) {
            g.fill(f.x(), f.y(), f.right(), f.bottom(), ARGB.multiplyAlpha(PANEL, opacity));
            g.outline(f.x(), f.y(), f.w(), f.h(), ARGB.multiplyAlpha(BORDER, opacity));
        }
        g.fill(f.x() + (fullscreen ? 0 : 1), f.y() + (fullscreen ? 0 : 1), f.right() - (fullscreen ? 0 : 1), f.y() + Window.TITLE_H,
                ARGB.multiplyAlpha(BAR, opacity));

        List<Bounds> rects = tabRects(f, font);
        for (int i = 0; i < rects.size(); i++) {
            Bounds r = rects.get(i);
            String id = REGISTRY.get(i).id;
            boolean on = id.equals(selected), over = r.contains(mouseX, mouseY);
            if (over && !on) g.fill(r.x(), r.y() + 1, r.right(), r.bottom(), ARGB.multiplyAlpha(HOVER, opacity));
            g.text(font, Component.translatable("tab.chromastudio." + id), r.x() + 6, r.y() + 4, ARGB.multiplyAlpha(on ? TEXT : DIM, opacity), false);
            if (on) g.fill(r.x() + 3, r.bottom() - 2, r.right() - 3, r.bottom() - 1, ARGB.multiplyAlpha(ACCENT, opacity));
        }

        Component tip = null;
        if (!buttons) return tip;
        boolean forced = Window.forced(screenW, screenH), inWorld = mc.level != null;
        for (int which = CLOSE; which <= FULLSCREEN; which++) {
            Bounds b = button(f, screenW, which);
            boolean usable = which == FULLSCREEN ? !forced : which != PIN || inWorld;
            boolean over = b.contains(mouseX, mouseY);
            if (over && usable) g.fill(b.x(), b.y() + 1, b.right(), b.bottom(), ARGB.multiplyAlpha(which == CLOSE ? 0xFFB3403A : HOVER, opacity));
            int c = ARGB.multiplyAlpha(!usable ? GREYED : over ? TEXT : DIM, opacity);
            int cx = b.x() + b.w() / 2, cy = b.y() + b.h() / 2;
            switch (which) {
                case CLOSE -> {
                    for (int i = -3; i <= 2; i++) {   // two diagonals
                        g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
                        g.fill(cx + i, cy - 1 - i, cx + i + 1, cy - i, c);
                    }
                }
                case PIN -> {   // a drawing pin: a head, a collar and a point
                    g.fill(cx - 2, cy - 4, cx + 2, cy - 1, c);
                    g.fill(cx - 3, cy - 1, cx + 3, cy, c);
                    g.fill(cx - 1, cy, cx + 1, cy + 4, c);
                }
                default -> {
                    if (fullscreen) {   // two overlapping squares: back to a window
                        g.outline(cx - 2, cy - 4, 6, 6, c);
                        g.fill(cx - 4, cy - 2, cx + 2, cy + 4, ARGB.multiplyAlpha(BAR, opacity));
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
                default -> forced ? "window.chromastudio.fullscreen.forced" : fullscreen ? "window.chromastudio.windowed" : "window.chromastudio.fullscreen";
            });
        }
        return tip;
    }

    /** A small cog: a ring with a tooth on each side and corner. */
    private static void cog(GuiGraphicsExtractor g, Bounds b, int colour) {
        int cx = b.x() + b.w() / 2, cy = b.y() + b.h() / 2;
        // A dark plate behind it, so it shows against a bright sky as well as on the title bar.
        g.fill(b.x(), b.y(), b.right(), b.bottom(), BAR);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, colour);
        g.fill(cx - 1, cy - 6, cx + 1, cy + 6, colour);
        g.fill(cx - 6, cy - 1, cx + 6, cy + 1, colour);
        g.fill(cx - 5, cy - 5, cx - 3, cy - 3, colour);
        g.fill(cx + 3, cy - 5, cx + 5, cy - 3, colour);
        g.fill(cx - 5, cy + 3, cx - 3, cy + 5, colour);
        g.fill(cx + 3, cy + 3, cx + 5, cy + 5, colour);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, BAR);
    }

    /** Draws the pinned window over the world. {@link Hud#render} calls it. */
    static void renderPinned(GuiGraphicsExtractor g) {
        ChromaScreen s = pinned;
        if (s == null || current() != null) return;
        // The screen isn't open, so nothing else keeps its size up to date.
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

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (ScreenPicker.armed()) {
            // The click that picks, anywhere at all. Any other button calls it off.
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) ScreenPicker.click(mx, my);
            else ScreenPicker.cancel();
            return true;
        }
        Bounds frame = frame();
        tabHasMouse = false;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (cog().contains(mx, my)) {
                minecraft.gui.setScreen(new SettingsScreen(this));
                return true;
            }
            // The edges come first: the top three pixels of the title bar resize rather than press what's under them.
            Window.Grip grip = window.gripAt(mx, my, width, height);
            if (grip != Window.Grip.NONE && grip != Window.Grip.MOVE) {
                window.grab(grip, mx, my, width, height);
                return true;
            }
            if (button(frame, width, CLOSE).contains(mx, my)) {
                onClose();
                return true;
            }
            if (button(frame, width, PIN).contains(mx, my)) {
                if (minecraft.level != null) pin();
                return true;
            }
            if (button(frame, width, FULLSCREEN).contains(mx, my)) {
                toggleFullscreen();
                return true;
            }
            List<Bounds> rects = tabRects(frame, font);
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
        // A window key bound to a mouse button: the game doesn't count presses for key binds while a screen is open.
        return tabHasMouse || windowKey(key -> key.matchesMouse(event));
    }

    /** Does what the open, pin or fullscreen key does inside the window, if the press is one of them. */
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

    /** Switches between the window and fullscreen. Does nothing when the screen is too small for a window. */
    public void toggleFullscreen() {
        if (Window.forced(width, height)) return;
        window.toggleFullscreen();
        store();
        layout(false);
    }

    /** Closes the screen and keeps the selected tab drawn over the world, where it takes no input. */
    public void pin() {
        pinning = true;
        pinned = this;
        minecraft.gui.setScreen(null);
    }

    /** Takes the window's place and size from the settings again, after the settings screen reset them. */
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
