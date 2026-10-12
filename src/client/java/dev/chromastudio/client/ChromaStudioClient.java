package dev.chromastudio.client;

import dev.chromastudio.client.ui.ChromaScreen;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The client side that doesn't depend on the mod loader. The loader's client entrypoint, in {@code dev.chromastudio.client.fabric} or {@code dev.chromastudio.client.neoforge}, hooks it up. */
public final class ChromaStudioClient {
    private ChromaStudioClient() {}

    public static final String MOD_ID = "chromastudio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ClientPlatform platform;

    /** Loads the settings and creates the keys. The loader then registers {@link Keys#ALL} and {@link Keys#CATEGORY}. */
    public static void init(ClientPlatform loader) {
        platform = loader;
        ModSettings.load();
        Keys.create();
    }

    /** Call at the end of every client tick. */
    public static void tick(Minecraft mc) {
        while (Keys.OPEN.consumeClick()) if (mc.gui.screen() == null) openWindow();
    }

    public static void openWindow() {
        Minecraft.getInstance().gui.setScreen(new ChromaScreen());
    }
}
