package dev.chromastudio.core.palette;

public record PaletteEntry(String blockId, String properties, int weight) {
    static final String DEFAULT_NAMESPACE = "minecraft:";

    public PaletteEntry {
        if (blockId == null || blockId.isBlank()) throw new IllegalArgumentException("An entry needs a block id");
        if (properties == null) properties = "";
        if (weight < 1) throw new IllegalArgumentException("A weight must be at least 1, not " + weight);
    }

    public PaletteEntry(String blockId, String properties) {
        this(blockId, properties, 1);
    }

    public PaletteEntry withWeight(int weight) {
        return new PaletteEntry(blockId, properties, weight);
    }

    public String idWithProperties() {
        return withProperties(blockId);
    }

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
