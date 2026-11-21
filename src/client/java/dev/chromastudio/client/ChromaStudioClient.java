package dev.chromastudio.client;

import dev.chromastudio.client.action.Capture;
import dev.chromastudio.client.action.Eyedropper;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.axiom.AxiomBridge;
import dev.chromastudio.client.export.ExportCommand;
import dev.chromastudio.client.ui.ChromaScreen;
import dev.chromastudio.client.ui.tabs.ExploreTab;
import dev.chromastudio.client.ui.tabs.GradientTab;
import dev.chromastudio.client.ui.tabs.MatchTab;
import dev.chromastudio.client.ui.tabs.PalettesTab;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChromaStudioClient {
    private ChromaStudioClient() {}

    public static final String MOD_ID = "chromastudio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ClientPlatform platform;

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
        AxiomBridge.register();
    }

    private static String openNext;

    public static void tick(Minecraft mc) {
        if (openNext != null) {
            String tab = openNext;
            openNext = null;
            ChromaScreen.open(tab);
        }
        while (Keys.OPEN.consumeClick()) if (mc.gui.screen() == null) openWindow();
        while (Keys.PIN.consumeClick()) if (mc.gui.screen() == null) ChromaScreen.togglePin();
        while (Keys.EYEDROPPER.consumeClick()) if (mc.gui.screen() == null) Keys.onEyedropper.run();
        while (Keys.CAPTURE.consumeClick()) if (mc.gui.screen() == null) Keys.onCapture.run();
        Capture.tick(mc);
        HotbarFill.tick();
        AxiomBridge.tick(mc);
    }

    public static void openWindow() { ChromaScreen.open(null); }

    public static void openWindowSoon(String tabId) { openNext = tabId; }
}
