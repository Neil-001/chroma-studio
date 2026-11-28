package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.List;

public final class Palette {
    public String name;
    public List<String> tags = new ArrayList<>();
    public String notes = "";
    public final List<PaletteEntry> entries = new ArrayList<>();
    public boolean preset;

    public Palette() {
        this("");
    }

    public Palette(String name) {
        this.name = name;
    }

    public Palette copy() {
        Palette out = new Palette(name);
        out.tags.addAll(tags);
        out.notes = notes;
        out.entries.addAll(entries);
        return out;
    }
}
