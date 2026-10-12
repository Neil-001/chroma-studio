package dev.chromastudio.core.color;

/** The base colours the game draws maps with, indexed by map colour id. */
public final class MapColors {
    // Read from the static fields of net.minecraft.world.level.material.MapColor in Minecraft 26.3, in id order
    // (the id and col fields of each). Id 0 is NONE, which a map leaves transparent.
    private static final int[] BASE = {
            0x000000, 0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7, 0x007C00,
            0xFFFFFF, 0xA4A8B8, 0x976D4D, 0x707070, 0x4040FF, 0x8F7748, 0xFFFCF5, 0xD87F33,
            0xB24CD8, 0x6699D8, 0xE5E533, 0x7FCC19, 0xF27FA5, 0x4C4C4C, 0x999999, 0x4C7F99,
            0x7F3FB2, 0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919, 0xFAEE4D, 0x5CDBD5,
            0x4A80FF, 0x00D93A, 0x815631, 0x700200, 0xD1B1A1, 0x9F5224, 0x95576C, 0x706C8A,
            0xBA8524, 0x677535, 0xA04D4E, 0x392923, 0x876B62, 0x575C5C, 0x7A4958, 0x4C3E5C,
            0x4C3223, 0x4C522A, 0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D, 0x167E86,
            0x3A8E8C, 0x562C3E, 0x14B485, 0x646464, 0xD8AF93, 0x7FA796};
    private static final Oklab[] OKLAB = new Oklab[BASE.length];

    static {
        for (int i = 0; i < BASE.length; i++) OKLAB[i] = Color.ofRgb(BASE[i]).oklab();
    }

    private MapColors() {}

    /** How many there are, NONE included. */
    public static int count() { return BASE.length; }

    /** The base colour with this id, as 0xRRGGBB. */
    public static int rgb(int id) { return BASE[id]; }

    /** The id of the base colour closest to a colour in OKLab. Never NONE, because no block shows as it. */
    public static int nearest(Color c) {
        Oklab target = c.oklab();
        int best = 1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 1; i < OKLAB.length; i++) {
            double d = target.distance(OKLAB[i]);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    static Oklab oklab(int id) { return OKLAB[id]; }
}
