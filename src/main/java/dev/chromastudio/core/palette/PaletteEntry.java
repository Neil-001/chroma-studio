package dev.chromastudio.core.palette;

/**
 * One block state in a palette. {@code blockId} is the full id, such as {@code minecraft:oak_log};
 * {@code properties} is empty or a list like {@code axis=y,lit=true}; {@code weight} is at least 1 and says how
 * often the block turns up next to the others.
 */
public record PaletteEntry(String blockId, String properties, int weight) {
    static final String DEFAULT_NAMESPACE = "minecraft:";

    public PaletteEntry {
        if (blockId == null || blockId.isBlank()) throw new IllegalArgumentException("An entry needs a block id");
        if (properties == null) properties = "";
        if (weight < 1) throw new IllegalArgumentException("A weight must be at least 1, not " + weight);
    }

    /** An entry of weight 1. */
    public PaletteEntry(String blockId, String properties) {
        this(blockId, properties, 1);
    }

    public PaletteEntry withWeight(int weight) {
        return new PaletteEntry(blockId, properties, weight);
    }

    /** {@code minecraft:oak_log[axis=y]}, or just the id when there are no properties. */
    public String idWithProperties() {
        return withProperties(blockId);
    }

    /** As {@link #idWithProperties}, with the namespace left off for the game's own blocks, as commands allow. */
    public String shortIdWithProperties() {
        return withProperties(shortId());
    }

    String shortId() {
        return blockId.startsWith(DEFAULT_NAMESPACE) ? blockId.substring(DEFAULT_NAMESPACE.length()) : blockId;
    }

    String withProperties(String id) {
        return properties.isEmpty() ? id : id + "[" + properties + "]";
    }
}
