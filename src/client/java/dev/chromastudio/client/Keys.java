package dev.chromastudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** The mod's keys. {@link ChromaStudioClient#init} creates them and each loader registers them in its own way. */
public final class Keys {
    private Keys() {}

    /** The heading for the keys in the Controls screen. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ChromaStudioClient.MOD_ID, "main"));
    public static KeyMapping OPEN;
    /** Every key, in the order the Controls screen lists them. */
    public static final List<KeyMapping> ALL = new ArrayList<>();

    private static KeyMapping key(String name, int code) {
        KeyMapping k = new KeyMapping("key.chromastudio." + name, InputConstants.Type.KEYBOARD, code, CATEGORY);
        ALL.add(k);
        return k;
    }

    static void create() {
        OPEN = key("open", InputConstants.KEY_C);
    }
}
