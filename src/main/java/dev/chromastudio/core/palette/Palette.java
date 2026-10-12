package dev.chromastudio.core.palette;

import java.util.ArrayList;
import java.util.List;

/** An ordered list of weighted block states with a name, tags and notes. */
public final class Palette {
    public String name;
    public List<String> tags = new ArrayList<>();
    public String notes = "";
    public final List<PaletteEntry> entries = new ArrayList<>();
    /** Set for the bundled palettes, which are read-only. It isn't stored in the palette's file or share string. */
    public boolean preset;

    public Palette() {
        this("");
    }

    public Palette(String name) {
        this.name = name;
    }

    /** A copy that can be edited, which is how a preset becomes the player's own. */
    public Palette copy() {
        Palette out = new Palette(name);
        out.tags.addAll(tags);
        out.notes = notes;
        out.entries.addAll(entries);
        return out;
    }
}
