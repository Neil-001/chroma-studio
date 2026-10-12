package dev.chromastudio.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chromastudio.client.Keys;
import dev.chromastudio.client.ModSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings: one scrolling list, grouped under headings, each with its own Reset. A change applies straight
 * away and is saved when the screen closes. The rows come from {@link ModSettings#GROUPS}, so a new setting gets one
 * by being declared there, with its label and tooltip in the language file.
 */
public final class SettingsScreen extends Screen {
    private static final int TOP = 24, BOTTOM = 26, ROW = 20, HEADING = 22, CONTROL_W = 130, M = 12;
    private static final int TEXT = 0xFFC8CED6, HEADING_TEXT = 0xFFFFFFFF, BAD = 0xFFFF6B6B;
    private static final String UNBOUND = "key.keyboard.unknown";

    private final Screen parent;
    /** The rows. The Done and key binds buttons below them are the screen's own. */
    private final Widgets list = new Widgets();
    /** The setting whose key button is waiting for a key press, or null. */
    private String listening;

    /** {@code parent} is the screen to go back to, or null to return to the game. */
    public SettingsScreen(Screen parent) {
        super(Component.translatable("settings.chromastudio.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list.clear();
        int colW = Math.min(340, width - 2 * M), left = (width - colW) / 2, y = TOP + 2;
        for (var group : ModSettings.GROUPS.entrySet()) {
            String id = group.getKey();
            list.label(left, y + 8, Component.translatable("settings.chromastudio.group." + id), HEADING_TEXT);
            Component reset = Component.translatable("settings.chromastudio.reset");
            list.button(left + colW - Widgets.buttonWidth(reset), y + 4, reset, () -> {
                ModSettings.reset(id);
                if (parent instanceof ChromaScreen window) window.reloadWindow();
                rebuildWidgets();
            }).setTooltip(Tooltip.create(Component.translatable("settings.chromastudio.reset.tip")));
            y += HEADING;
            for (String name : group.getValue()) {
                if (ModSettings.WINDOW_MEMORY.contains(name)) continue;
                list.label(left + 4, y + 4, Component.translatable("settings.chromastudio." + name), TEXT);
                control(name, left + colW - CONTROL_W, y)
                        .setTooltip(Tooltip.create(Component.translatable("settings.chromastudio." + name + ".tip")));
                y += ROW;
            }
            y += 4;
        }
        list.viewport(new Bounds(0, TOP, width, height - TOP - BOTTOM), y - TOP);

        // Bottom bar: each button as wide as its text needs.
        int by = height - 22;
        Component keys = Component.translatable("settings.chromastudio.keys"), done = Component.translatable("gui.done");
        addRenderableWidget(Button.builder(keys, _ -> openKeys()).bounds(left, by, Widgets.buttonWidth(keys) + 6, 18)
                .tooltip(Tooltip.create(Component.translatable("settings.chromastudio.keys.tip"))).build());
        int doneW = Widgets.buttonWidth(done) + 14;
        addRenderableWidget(Button.builder(done, _ -> onClose()).bounds(left + colW - doneW, by, doneW, 18).build());
    }

    /** The control for one setting, chosen by the setting's type. */
    private AbstractWidget control(String name, int x, int y) {
        Class<?> type = ModSettings.type(name);
        Object value = ModSettings.get(name);
        if (type == boolean.class) return list.toggle(x, y, CONTROL_W, null, (Boolean) value, v -> ModSettings.set(name, v));
        if (type == int.class || type == double.class) {
            boolean whole = type == int.class;
            return list.spin(x, y, CONTROL_W, ((Number) value).doubleValue(), whole ? 1 : 0.05, ModSettings.min(name), ModSettings.max(name),
                    whole, v -> ModSettings.set(name, v));
        }
        if (type.isEnum()) {
            String prefix = "option.chromastudio." + type.getSimpleName().toLowerCase(Locale.ROOT) + ".";
            return list.cycler(x, y, CONTROL_W, null, List.of((Object[]) type.getEnumConstants()), value,
                    v -> Component.translatable(prefix + ((Enum<?>) v).name().toLowerCase(Locale.ROOT)), v -> ModSettings.set(name, v));
        }
        String text = (String) value;
        if (ModSettings.names(name) != null)
            return list.cycler(x, y, CONTROL_W, null, ModSettings.names(name), text,
                    v -> Component.translatable("option.chromastudio." + name.toLowerCase(Locale.ROOT) + "." + v.toLowerCase(Locale.ROOT)),
                    v -> ModSettings.set(name, v));
        if (name.equals("startTab")) {
            List<String> ids = new ArrayList<>(ChromaScreen.tabIds());
            if (!ids.contains(text)) ids.add(text);   // a tab from a mod that's gone: keep it listed rather than change it silently
            return list.cycler(x, y, CONTROL_W, null, ids, text, v -> Component.translatable("tab.chromastudio." + v), v -> ModSettings.set(name, v));
        }
        if (name.equals("recipeKey")) {
            Component shown = name.equals(listening) ? Component.translatable("settings.chromastudio.press_a_key") : keyName(text);
            return list.button(x, y, CONTROL_W, shown, () -> {
                listening = name;
                rebuildWidgets();
            });
        }
        // Free text. Text the setting can't hold shows red and changes nothing.
        EditBox[] box = new EditBox[1];
        box[0] = list.field(x, y, CONTROL_W, text, v -> box[0].setTextColor(ModSettings.set(name, v) ? EditBox.DEFAULT_TEXT_COLOR : BAD));
        return box[0];
    }

    /** A key as the game shows it. A name the game doesn't know, from a hand-edited file, shows as it is. */
    private static Component keyName(String name) {
        try {
            return InputConstants.getKey(name).getDisplayName();
        } catch (RuntimeException _) {
            return Component.literal(name);
        }
    }

    /** Opens the game's key binds screen. It has no way to show one category, so this scrolls its list to the mod's heading. */
    private void openKeys() {
        KeyBindsScreen keys = new KeyBindsScreen(this, minecraft.options);
        minecraft.gui.setScreen(keys);
        String heading = Keys.CATEGORY.label().getString();
        for (GuiEventListener child : keys.children()) {
            if (!(child instanceof KeyBindsList binds)) continue;
            int y = 0;
            for (KeyBindsList.Entry e : binds.children()) {
                if (e instanceof KeyBindsList.CategoryEntry && e.children().stream()
                        .anyMatch(w -> w instanceof AbstractWidget name && name.getMessage().getString().equals(heading))) {
                    binds.setScrollAmount(y);
                    return;
                }
                y += e.getHeight();
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        g.fill(0, TOP - 1, width, TOP, 0x40FFFFFF);
        g.fill(0, height - BOTTOM, width, height - BOTTOM + 1, 0x40FFFFFF);
        list.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (listening != null) {   // a click anywhere calls the key press off
            listening = null;
            rebuildWidgets();
        }
        if (list.mouseClicked(event, doubled)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) { return list.mouseReleased(event) || super.mouseReleased(event); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return list.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) { return list.mouseScrolled(mx, my, h, v) || super.mouseScrolled(mx, my, h, v); }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            // Escape leaves the key unbound, as in the game's own key binds screen.
            ModSettings.set(listening, event.isEscape() ? UNBOUND : InputConstants.getKey(event).getName());
            listening = null;
            rebuildWidgets();
            return true;
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return list.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) { return list.charTyped(event) || super.charTyped(event); }

    @Override
    public void removed() {
        ModSettings.save();
        super.removed();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
