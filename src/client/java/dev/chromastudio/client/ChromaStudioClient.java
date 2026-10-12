package dev.chromastudio.client;

import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.Eyedropper;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.export.ExportCommand;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.tabs.ExploreTab;
import dev.chromastudio.client.ui.tabs.GradientTab;
import dev.chromastudio.client.ui.tabs.MatchTab;
import dev.chromastudio.client.ui.tabs.PalettesTab;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The client side that doesn't depend on the mod loader. The loader's client entrypoint, in {@code dev.chromastudio.client.fabric} or {@code dev.chromastudio.client.neoforge}, hooks it up. */
public final class ChromaStudioClient {
    private ChromaStudioClient() {}

    public static final String MOD_ID = "chromastudio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ClientPlatform platform;

    /** Loads the settings, creates the keys, adds the subcommands of {@code /chroma}, registers the four tabs and hooks up the eyedropper. The loader then registers {@link Keys#ALL} and {@link Keys#CATEGORY}. */
    public static void init(ClientPlatform loader) {
        platform = loader;
        ModSettings.load();
        Keys.create();
        ExportCommand.register();
        PaletteSink.setCurrent(PaletteStore.get());
        Keys.onEyedropper = Eyedropper::press;
        Capture.install();
        ChromaScreen.registerTab("match", MatchTab::new, 0);
        ChromaScreen.registerTab("gradient", GradientTab::new, 10);
        ChromaScreen.registerTab("palettes", PalettesTab::new, 20);
        ChromaScreen.registerTab("explore", ExploreTab::new, 30);
    }

    /** The tab to open the window on at the next tick, or null. */
    private static String openNext;

    /** Call at the end of every client tick. */
    public static void tick(Minecraft mc) {
        if (openNext != null) {
            String tab = openNext;
            openNext = null;
            ChromaScreen.open(tab);
        }
        while (Keys.OPEN.consumeClick()) if (mc.gui.screen() == null) openWindow();
        while (Keys.PIN.consumeClick()) if (mc.gui.screen() == null) ChromaScreen.togglePin();
        // The fullscreen key only means something inside the window, which sees the key press itself.
        while (Keys.FULLSCREEN.consumeClick()) { }
        while (Keys.EYEDROPPER.consumeClick()) if (mc.gui.screen() == null) Keys.onEyedropper.run();
        while (Keys.CAPTURE.consumeClick()) if (mc.gui.screen() == null) Keys.onCapture.run();
        Capture.tick(mc);
        HotbarFill.tick();
    }

    /** Opens the window on the tab it last showed, or brings a pinned one back. */
    public static void openWindow() { ChromaScreen.open(null); }

    /**
     * Opens the window on a tab at the next tick. A command needs this: the chat screen closes whatever is open once
     * the command has run.
     */
    public static void openWindowSoon(String tabId) { openNext = tabId; }
}
