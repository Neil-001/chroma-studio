package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import net.minecraft.world.level.block.state.BlockState;

public final class Target {
    private final Color colour;
    private final Distribution flat;
    private final TextureStats texture;

    private Target(Color colour, TextureStats texture) {
        this.colour = colour;
        this.flat = Distribution.of(colour);
        this.texture = texture;
    }

    public static Target of(Color c) { return new Target(c, null); }

    public static Target of(Entry e, Face f, TintContext tint) {
        TextureStats stats = e.face(f, tint);
        return new Target(stats.mean(Scale.T1), stats);
    }

    public static Target of(Entry e, BlockState as, Face f, TintContext tint) {
        TextureStats stats = e.layers(f).sample(as, tint).stats;
        return new Target(stats.mean(Scale.T1), stats);
    }

    public Distribution distribution(Scale s) { return texture == null ? flat : texture.distribution(s); }

    public Color colour() { return colour; }

    public boolean flat() { return texture == null; }
}
