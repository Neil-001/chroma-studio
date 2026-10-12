package dev.chromastudio.client.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.chromastudio.client.ui.SettingsScreen;

/** Gives Mod Menu the settings screen. Only Mod Menu loads this class, so the mod runs without it. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() { return SettingsScreen::new; }
}
