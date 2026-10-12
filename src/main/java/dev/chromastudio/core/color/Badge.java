package dev.chromastudio.core.color;

/** How busy a texture looks, from its spread. */
public enum Badge {
    FLAT, GRAINY, BUSY;

    // Set from Minecraft 26.3's own textures. Spread is at 16 texels; the other columns are the spread after one
    // halving, and the share of the second largest colour cluster with its OKLab distance from the largest.
    //
    //                      spread   at 8     second cluster
    //   concrete (all)     0.002 to 0.005    none
    //   terracotta (all)   0.004 to 0.008    none
    //   wool (all)         0.023 to 0.054    0.013 to 0.031   0.25 at 0.03 to 0.08
    //   stone              0.037    0.019    0.28 at 0.04
    //   gravel             0.058    0.028    0.24 at 0.08
    //   granite            0.074    0.036    0.35 at 0.08
    //   oak planks         0.086    0.051    0.25 at 0.08
    //   cobblestone        0.099    0.065    0.29 at 0.12
    //   bookshelf          0.167    0.101    0.24 at 0.20
    //
    // Each threshold is about midway between the anchors it parts: concrete from stone, and cobblestone from
    // bookshelf. Wool looks flat and gravel and granite busy, but they land in GRAINY, because none of these
    // figures tells them apart: stone sits inside wool's range in every column, and gravel and granite sit below
    // planks and cobblestone in every column.
    public static final double GRAINY_FROM = 0.02, BUSY_FROM = 0.13;

    public static Badge of(double spread) {
        if (spread < GRAINY_FROM) return FLAT;
        return spread < BUSY_FROM ? GRAINY : BUSY;
    }
}
