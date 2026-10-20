package dev.chromastudio.client;

import com.mojang.blaze3d.platform.NativeImage;

/** 1.21.1's {@link NativeImage} keeps pixels as ABGR. These take and return ARGB, as the rest of the mod uses. */
public final class Abgr {
    private Abgr() {}

    public static int get(NativeImage image, int x, int y) { return swapRedBlue(image.getPixelRGBA(x, y)); }

    public static void set(NativeImage image, int x, int y, int argb) { image.setPixelRGBA(x, y, swapRedBlue(argb)); }

    private static int swapRedBlue(int c) { return c & 0xFF00FF00 | c >> 16 & 0xFF | (c & 0xFF) << 16; }
}
