package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Scale;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A face's texture as it looks at a scale, drawn as a small picture. Each is painted once and kept, by entry, face,
 * scale and tint, until enough newer ones have come along or the index is built again.
 */
final class Thumbnails {
    private Thumbnails() {}

    private static final int KEPT = 512;

    private record Key(Entry entry, Face face, Scale scale, int tint) {}

    // In order of last use, so the one dropped is the one longest unseen.
    private static final Map<Key, Pixels> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int builtFor = -1;

    static void draw(GuiGraphicsExtractor g, Entry entry, Face face, Scale scale, TintContext tint, int x, int y, int size, float opacity) {
        int version = BlockIndex.get().version();
        if (version != builtFor) {
            clear();
            builtFor = version;
        }
        Key key = new Key(entry, face, scale, entry.tinted() ? entry.tint(face, tint) : TintContext.NO_TINT);
        Pixels pixels = CACHE.get(key);
        if (pixels == null) {
            pixels = new Pixels(scale.texels, scale.texels);
            pixels.set(entry.face(face, tint).pixels(scale));
            CACHE.put(key, pixels);
            Iterator<Pixels> oldest = CACHE.values().iterator();
            while (CACHE.size() > KEPT) {
                oldest.next().close();
                oldest.remove();
            }
        }
        pixels.draw(g, x, y, size, size, opacity);
    }

    static void clear() {
        CACHE.values().forEach(Pixels::close);
        CACHE.clear();
    }
}
