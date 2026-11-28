package dev.chromastudio.client.ui;

import dev.chromastudio.client.ModSettings;

public final class Window {
    public static final int MIN_W = ModSettings.WINDOW_MIN_W, MIN_H = ModSettings.WINDOW_MIN_H;
    public static final int FORCE_W = 427, FORCE_H = 240;
    public static final int TITLE_H = 16;
    private static final int EDGE = 3, CORNER = 8;

    public enum Grip {
        NONE, MOVE, N, S, E, W, NE, NW, SE, SW;

        boolean north() { return this == N || this == NE || this == NW; }
        boolean south() { return this == S || this == SE || this == SW; }
        boolean east() { return this == E || this == NE || this == SE; }
        boolean west() { return this == W || this == NW || this == SW; }
    }

    private int x, y, w, h;
    private boolean fullscreen;

    private Grip grip = Grip.NONE;
    private double grabX, grabY;
    private Bounds grabbed;

    public Window() {
        boolean remember = ModSettings.rememberWindow;
        x = remember ? ModSettings.windowX : ModSettings.CENTRED;
        y = remember ? ModSettings.windowY : ModSettings.CENTRED;
        w = remember ? ModSettings.windowW : MIN_W;
        h = remember ? ModSettings.windowH : MIN_H;
        fullscreen = remember && ModSettings.fullscreen;
    }

    public static boolean forced(int screenW, int screenH) { return screenW < FORCE_W || screenH < FORCE_H; }

    public boolean fullscreen(int screenW, int screenH) { return fullscreen || forced(screenW, screenH); }

    public Bounds frame(int screenW, int screenH) {
        if (fullscreen(screenW, screenH)) return new Bounds(0, 0, screenW, screenH);
        int fw = within(w, MIN_W, screenW), fh = within(h, MIN_H, screenH);
        int fx = x == ModSettings.CENTRED ? (screenW - fw) / 2 : within(x, 0, screenW - fw);
        int fy = y == ModSettings.CENTRED ? (screenH - fh) / 2 : within(y, 0, screenH - fh);
        return new Bounds(fx, fy, fw, fh);
    }

    public Bounds content(int screenW, int screenH) {
        Bounds f = frame(screenW, screenH);
        return fullscreen(screenW, screenH) ? new Bounds(0, TITLE_H, f.w(), f.h() - TITLE_H)
                : new Bounds(f.x() + 1, f.y() + TITLE_H, f.w() - 2, f.h() - TITLE_H - 1);
    }

    public Grip gripAt(double mx, double my, int screenW, int screenH) {
        if (fullscreen(screenW, screenH)) return Grip.NONE;
        Bounds f = frame(screenW, screenH);
        if (!f.contains(mx, my)) return Grip.NONE;
        double left = mx - f.x(), right = f.right() - mx, top = my - f.y(), bottom = f.bottom() - my;
        boolean n = top < EDGE, s = bottom <= EDGE, w = left < EDGE, e = right <= EDGE;
        if ((n || w) && top < CORNER && left < CORNER) return Grip.NW;
        if ((n || e) && top < CORNER && right <= CORNER) return Grip.NE;
        if ((s || w) && bottom <= CORNER && left < CORNER) return Grip.SW;
        if ((s || e) && bottom <= CORNER && right <= CORNER) return Grip.SE;
        if (n) return Grip.N;
        if (s) return Grip.S;
        if (w) return Grip.W;
        if (e) return Grip.E;
        return top < TITLE_H ? Grip.MOVE : Grip.NONE;
    }

    public void grab(Grip grip, double mx, double my, int screenW, int screenH) {
        this.grip = grip;
        grabX = mx;
        grabY = my;
        grabbed = frame(screenW, screenH);
    }

    public boolean dragging() { return grip != Grip.NONE; }

    public Grip grip() { return grip; }

    public boolean dragTo(double mx, double my, int screenW, int screenH) {
        if (grip == Grip.NONE) return false;
        int dx = (int) Math.round(mx - grabX), dy = (int) Math.round(my - grabY);
        int nx = grabbed.x(), ny = grabbed.y(), nw = grabbed.w(), nh = grabbed.h();
        if (grip == Grip.MOVE) {
            nx = within(nx + dx, 0, screenW - nw);
            ny = within(ny + dy, 0, screenH - nh);
        }
        if (grip.east()) nw = within(nw + dx, MIN_W, screenW - nx);
        if (grip.south()) nh = within(nh + dy, MIN_H, screenH - ny);
        if (grip.west()) {
            nx = within(nx + dx, 0, grabbed.right() - MIN_W);
            nw = grabbed.right() - nx;
        }
        if (grip.north()) {
            ny = within(ny + dy, 0, grabbed.bottom() - MIN_H);
            nh = grabbed.bottom() - ny;
        }
        boolean changed = nx != x || ny != y || nw != w || nh != h;
        x = nx; y = ny; w = nw; h = nh;
        return changed;
    }

    private static int within(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public void release() { grip = Grip.NONE; }

    public void toggleFullscreen() { fullscreen = !fullscreen; }

    public void store() {
        if (!ModSettings.rememberWindow) return;
        ModSettings.windowX = x;
        ModSettings.windowY = y;
        ModSettings.windowW = w;
        ModSettings.windowH = h;
        ModSettings.fullscreen = fullscreen;
    }
}
