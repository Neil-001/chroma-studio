package dev.chromastudio.core.color;

/** How many texels per side a block face is seen at. */
public enum Scale {
    T16(16), T8(8), T4(4), T2(2), T1(1);

    public final int texels;

    Scale(int texels) {
        this.texels = texels;
    }

    /** The next coarser scale. T1 is the coarsest and gives itself. */
    public Scale next() {
        return this == T1 ? T1 : values()[ordinal() + 1];
    }
}
