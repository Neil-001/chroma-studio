package dev.chromastudio.core.color;

public enum Scale {
    T16(16), T8(8), T4(4), T2(2), T1(1);

    public final int texels;

    Scale(int texels) {
        this.texels = texels;
    }

    public Scale next() {
        return this == T1 ? T1 : values()[ordinal() + 1];
    }
}
