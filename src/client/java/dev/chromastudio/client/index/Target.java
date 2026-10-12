package dev.chromastudio.client.index;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Scale;
import dev.chromastudio.core.color.TextureStats;
import net.minecraft.world.level.block.state.BlockState;

/** What the player is matching against: one flat colour, or the face of a block. */
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

    /** The face of an entry as it looks in a tint context. {@link Face#ANY} isn't a face. */
    public static Target of(Entry e, Face f, TintContext tint) {
        TextureStats stats = e.face(f, tint);
        return new Target(stats.mean(Scale.T1), stats);
    }

    /**
     * The face of an entry as another state of its block shows it. States that share their textures share an entry,
     * but a tint may still tell them apart, as redstone dust's does by its power.
     */
    public static Target of(Entry e, BlockState as, Face f, TintContext tint) {
        TextureStats stats = e.layers(f).sample(as, tint).stats;
        return new Target(stats.mean(Scale.T1), stats);
    }

    /** The colours to match at a scale: the one colour for a flat target, else the texture's at that scale. */
    public Distribution distribution(Scale s) { return texture == null ? flat : texture.distribution(s); }

    /** The flat colour, or the texture's average. */
    public Color colour() { return colour; }

    public boolean flat() { return texture == null; }
}
