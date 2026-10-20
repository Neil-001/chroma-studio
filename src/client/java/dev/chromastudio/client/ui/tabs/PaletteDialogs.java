package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.OverflowMode;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.FileDialog;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.ImageImport;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.export.LitematicaBridge;
import dev.chromastudio.client.export.SchematicExporter;
import dev.chromastudio.client.index.BlockIndex;
import dev.chromastudio.client.index.Entry;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.export.Layout;
import dev.chromastudio.core.export.LayoutOptions;
import dev.chromastudio.core.palette.Generator;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.ShareString;
import dev.chromastudio.core.palette.WorldEditPattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** The small dialogs the Palettes and Gradient tabs open. */
final class PaletteDialogs {
    private PaletteDialogs() {}

    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    private static Component option(Enum<?> value) {
        return tr("option.chromastudio." + value.getClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + value.name().toLowerCase(Locale.ROOT));
    }

    /** Draws a label in the dialog's left column, level with a control on the same row. */
    private static void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(PanelTab.font(), tr(key), x, y + 4, Theme.DIM, false);
    }

    private static int labelWidth(String... keys) {
        int w = 0;
        for (String key : keys) w = Math.max(w, Widgets.tw(tr(key)));
        return w + 6;
    }

    /** Asks for a name. */
    static final class Name extends Dialog {
        private final String initial;
        private final Component action;
        private final Consumer<String> done;
        private EditBox field;
        private String text;

        Name(Component title, Component action, String initial, Consumer<String> done) {
            super(title, 200, 70);
            this.initial = initial;
            this.action = action;
            this.done = done;
            text = initial;
        }

        @Override
        protected void build(Bounds in) {
            boolean first = field == null;
            field = widgets.search(in.x(), in.y(), in.w(), tr("palettes.chromastudio.name_hint"), s -> text = s);
            // A name may be any length. Only the file's name is cut short.
            field.setMaxLength(Integer.MAX_VALUE);
            field.setValue(text);
            if (first) {
                field.setFocused(true);
                widgets.setFocused(field);
            }
            int w = Widgets.buttonWidth(action);
            widgets.button(in.right() - w, in.y() + PanelTab.ROW, action, this::confirm);
        }

        @Override
        protected boolean confirm() {
            if (text.isBlank()) {
                status(tr("palettes.chromastudio.name_needed"), Theme.FAIR);
                return true;
            }
            close();
            done.accept(text.strip());
            return true;
        }
    }

    /** Asks before something that can't be undone. */
    static final class Confirm extends Dialog {
        private final Component question, action;
        private final Runnable done;

        Confirm(Component title, Component question, Component action, Runnable done) {
            super(title, 220, 70);
            this.question = question;
            this.action = action;
            this.done = done;
        }

        @Override
        protected void build(Bounds in) {
            int w = Widgets.buttonWidth(action);
            widgets.button(in.right() - w, in.bottom() - Widgets.H, action, () -> {
                close();
                done.run();
            });
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            g.drawWordWrap(PanelTab.font(), question, in.x(), in.y(), in.w(), Theme.TEXT);
        }
    }

    /** Takes a share string pasted into the field, or a JSON file by its path or from the file dialog. */
    static final class Import extends Dialog {
        private final Consumer<Palette> done;
        private String text = "";
        private EditBox field;

        Import(Consumer<Palette> done) {
            super(tr("palettes.chromastudio.import.title"), 280, 86);
            this.done = done;
        }

        @Override
        protected void build(Bounds in) {
            boolean first = field == null;
            field = widgets.search(in.x(), in.y(), in.w(), tr("palettes.chromastudio.import.hint"), s -> text = s);
            field.setMaxLength(Short.MAX_VALUE);
            field.setValue(text);
            if (first) {
                field.setFocused(true);
                widgets.setFocused(field);
            }
            Component browse = tr("palettes.chromastudio.browse"), go = tr("palettes.chromastudio.import");
            int y = in.y() + PanelTab.ROW;
            widgets.button(in.x(), y, browse, () -> FileDialog.choose("JSON", "json", path -> field.setValue(path.toString()),
                    () -> status(tr("palettes.chromastudio.no_dialog"), Theme.FAIR))).setTooltip(Tooltip.create(tr("palettes.chromastudio.import.browse.tip")));
            widgets.button(in.right() - Widgets.buttonWidth(go), y, go, this::confirm);
        }

        @Override
        protected boolean confirm() {
            String s = text.strip();
            if (s.isEmpty()) {
                status(tr("palettes.chromastudio.import.empty"), Theme.FAIR);
                return true;
            }
            try {
                Palette p;
                // Anything that looks like a share string is one; the rest is a file's path.
                if (s.startsWith("chroma")) p = PaletteStore.get().importShare(s);
                else p = PaletteStore.get().importJson(Files.readString(Path.of(s)));
                close();
                done.accept(p);
            } catch (IllegalArgumentException e) {
                // The codecs' own messages are in English only; the line before them is translated.
                status(tr("palettes.chromastudio.import.bad", e instanceof InvalidPathException ? s : String.valueOf(e.getMessage())), Theme.BAD);
            } catch (IOException ignored) {
                status(tr("palettes.chromastudio.import.no_file", s), Theme.BAD);
            }
            return true;
        }
    }

    /** Makes a palette from a seed colour, a harmony and a count. */
    static final class Generate extends Dialog {
        private static Harmony harmony = Harmony.ANALOGOUS;

        private final Consumer<Palette> done;
        private String hex;
        private int count = ModSettings.generateCount;
        private int labelW;

        /** {@code seed} is the colour to start from: the one the Match tab is matching. A button takes the block in hand's instead. */
        Generate(Color seed, Consumer<Palette> done) {
            super(tr("palettes.chromastudio.generate.title"), 250, 106);
            this.done = done;
            hex = seed.hex();
        }

        /** The colour of the block the player holds, or null when they hold none the index knows. */
        private static Color inHand() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return null;
            ItemStack held = mc.player.getMainHandItem();
            if (!(held.getItem() instanceof BlockItem item)) return null;
            Entry entry = PaletteBlocks.entry(item.getBlock().defaultBlockState());
            return entry == null ? null : PaletteBlocks.stats(entry).mean(PaletteBlocks.scale());
        }

        private Color seed() {
            try {
                return Color.fromHex(hex.strip());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.generate.seed", "palettes.chromastudio.generate.harmony", "palettes.chromastudio.generate.count");
            int x = in.x() + labelW, y = in.y();
            Component hand = tr("palettes.chromastudio.generate.in_hand");
            int handW = Widgets.buttonWidth(hand);
            EditBox field = widgets.field(x + Widgets.H + 2, y, Math.max(40, in.right() - x - Widgets.H - 4 - handW), hex, s -> hex = s);
            field.setMaxLength(7);
            Button handButton = widgets.button(in.right() - handW, y, hand, () -> {
                Color c = inHand();
                if (c != null) field.setValue(c.hex());
            });
            if (inHand() == null) Widgets.disable(handButton, tr("palettes.chromastudio.generate.in_hand.none"));
            y += PanelTab.ROW;
            widgets.cycler(x, y, in.right() - x, null, List.of(Harmony.values()), harmony, PaletteDialogs::option, h -> harmony = h);
            y += PanelTab.ROW;
            widgets.spin(x, y, 40, count, 1, ModSettings.min("generateCount"), ModSettings.max("generateCount"), true, v -> count = (int) v);
            Component roll = tr("palettes.chromastudio.generate.roll"), go = tr("palettes.chromastudio.generate.go");
            int goW = Widgets.buttonWidth(go), rollW = Widgets.buttonWidth(roll);
            widgets.button(in.right() - goW, y, go, this::confirm);
            widgets.button(in.right() - goW - PanelTab.GAP - rollW, y, roll, () -> {
                Random random = new Random();
                harmony = Harmony.values()[random.nextInt(Harmony.values().length)];
                hex = Color.ofOklch(new Oklch(0.35 + random.nextDouble() * 0.45, 0.04 + random.nextDouble() * 0.14, random.nextDouble() * 360)).hex();
                confirm();
            }).setTooltip(Tooltip.create(tr("palettes.chromastudio.generate.roll.tip")));
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            label(g, "palettes.chromastudio.generate.seed", in.x(), in.y());
            label(g, "palettes.chromastudio.generate.harmony", in.x(), in.y() + PanelTab.ROW);
            label(g, "palettes.chromastudio.generate.count", in.x(), in.y() + 2 * PanelTab.ROW);
            Color seed = seed();
            int x = in.x() + labelW;
            if (seed != null) Widgets.swatch(g, x, in.y(), Widgets.H, Widgets.H, seed.rgb());
            else g.renderOutline(x, in.y(), Widgets.H, Widgets.H, Theme.BAD);
        }

        @Override
        protected boolean confirm() {
            Color seed = seed();
            if (seed == null) {
                status(tr("palettes.chromastudio.generate.bad_hex"), Theme.BAD);
                return true;
            }
            List<PaletteEntry> entries = PaletteBlocks.closest(Generator.targets(seed.oklch(), harmony, count, new Random()));
            if (entries.isEmpty()) {
                status(tr("palettes.chromastudio.no_matches"), Theme.BAD);
                return true;
            }
            close();
            done.accept(PaletteStore.get().create(tr("palettes.chromastudio.generate.name", option(harmony), seed.hex()).getString(), entries));
            return true;
        }
    }

    /** Makes a palette from an image's main colours. */
    static final class Image extends Dialog {
        private static final int DEFAULT_COUNT = 6;

        private final Consumer<Palette> done;
        private String path = "";
        private int count = DEFAULT_COUNT;
        private EditBox field;
        private int labelW;

        Image(Consumer<Palette> done) {
            super(tr("palettes.chromastudio.image.title"), 280, 90);
            this.done = done;
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.image.file", "palettes.chromastudio.image.count");
            Component browse = tr("palettes.chromastudio.browse"), go = tr("palettes.chromastudio.import");
            int browseW = Widgets.buttonWidth(browse), x = in.x() + labelW;
            boolean first = field == null;
            field = widgets.search(x, in.y(), in.right() - x - browseW - PanelTab.GAP, tr("palettes.chromastudio.image.hint"), s -> path = s);
            field.setMaxLength(1024);
            field.setValue(path);
            if (first) {
                field.setFocused(true);
                widgets.setFocused(field);
            }
            widgets.button(in.right() - browseW, in.y(), browse, () -> ImageImport.choose(file -> field.setValue(file.toString()),
                    () -> status(tr("palettes.chromastudio.no_dialog"), Theme.FAIR)));
            int y = in.y() + PanelTab.ROW;
            widgets.spin(x, y, 40, count, 1, 2, 32, true, v -> count = (int) v);
            widgets.button(in.right() - Widgets.buttonWidth(go), y, go, this::confirm);
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            label(g, "palettes.chromastudio.image.file", in.x(), in.y());
            label(g, "palettes.chromastudio.image.count", in.x(), in.y() + PanelTab.ROW);
        }

        @Override
        protected boolean confirm() {
            String s = path.strip();
            if (s.isEmpty()) {
                status(tr("palettes.chromastudio.image.empty"), Theme.FAIR);
                return true;
            }
            try {
                Path file = Path.of(s);
                List<Color> colours = ImageImport.colours(file, count);
                if (colours.isEmpty()) {
                    status(tr("palettes.chromastudio.image.transparent"), Theme.BAD);
                    return true;
                }
                List<Oklch> targets = new ArrayList<>();
                for (Color c : colours) targets.add(c.oklch());
                List<PaletteEntry> entries = PaletteBlocks.closest(targets);
                if (entries.isEmpty()) {
                    status(tr("palettes.chromastudio.no_matches"), Theme.BAD);
                    return true;
                }
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                close();
                done.accept(PaletteStore.get().create(dot > 0 ? name.substring(0, dot) : name, entries));
            } catch (IOException | InvalidPathException ignored) {
                status(tr("palettes.chromastudio.image.unreadable", s), Theme.BAD);
            }
            return true;
        }
    }

    /** Writes a palette as a schematic, and can hand it to Litematica. */
    static final class Export extends Dialog {
        private final Palette palette;
        private Layout layout;
        private SchematicExporter.Format format = SchematicExporter.Format.LITEMATIC;
        private int height, patch = ModSettings.patchSize, width = LayoutOptions.DIORAMA_WIDTH;
        private boolean gap = ModSettings.exportGap;
        private final long seed;
        private Component skipped;
        private int labelW;

        /** {@code seed} places the diorama layouts, so an export from the Diorama panel comes out as it shows. */
        Export(Palette palette, Layout layout, long seed) {
            super(tr("palettes.chromastudio.export.title"), 290, 140);
            this.palette = palette;
            this.layout = layout;
            this.seed = seed;
            height = layout == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT;
        }

        private boolean diorama() { return layout == Layout.NOISE_CLUSTERS || layout == Layout.HEIGHT_GRADIENT || layout == Layout.DITHER; }

        private static Component layoutName(Layout l) { return tr("option.chromastudio.layoutchoice." + l.name().toLowerCase(Locale.ROOT)); }

        private void spinner(int x, int y, double value, double min, double max, boolean applies, String why, DoubleConsumer set) {
            EditBox f = widgets.spin(x, y, 40, value, 1, min, max, true, set);
            if (!applies) Widgets.disable(f, tr(why));
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.export.layout", "palettes.chromastudio.export.format", "palettes.chromastudio.export.height");
            int x = in.x() + labelW, y = in.y(), half = in.x() + in.w() / 2;
            int secondW = labelWidth("palettes.chromastudio.export.width", "palettes.chromastudio.export.patch", "palettes.chromastudio.export.gap");
            widgets.cycler(x, y, in.right() - x, null, List.of(Layout.values()), layout, Export::layoutName, l -> {
                // A wall and a diorama are different heights by default, so the height follows the layout until it's typed.
                boolean wasDefault = height == (layout == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT);
                layout = l;
                if (wasDefault) height = l == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT;
                refresh();
            });
            y += PanelTab.ROW;
            widgets.cycler(x, y, 70, null, List.of(SchematicExporter.Format.values()), format,
                    f -> Component.literal("." + f.extension), f -> { format = f; refresh(); });
            AbstractWidget gapToggle = widgets.cycler(half + secondW, y, 40, null, List.of(true, false), gap, CommonComponents::optionStatus, v -> gap = v);
            if (diorama()) Widgets.disable(gapToggle, tr("palettes.chromastudio.export.gap.na"));
            y += PanelTab.ROW;
            spinner(x, y, height, 1, 256, layout == Layout.WALL || diorama(), "palettes.chromastudio.export.height.na", v -> height = (int) v);
            spinner(half + secondW, y, width, 1, 256, diorama(), "palettes.chromastudio.export.width.na", v -> width = (int) v);
            y += PanelTab.ROW;
            spinner(half + secondW, y, patch, ModSettings.min("patchSize"), ModSettings.max("patchSize"), layout == Layout.PATCH_GRID, "palettes.chromastudio.export.patch.na", v -> patch = (int) v);

            Component export = tr("palettes.chromastudio.export.go"), place = tr("palettes.chromastudio.export.place");
            int placeW = Widgets.buttonWidth(place), exportW = Widgets.buttonWidth(export);
            y += PanelTab.ROW;
            Button placeButton = widgets.button(in.right() - placeW, y, place, () -> run(true));
            placeButton.setTooltip(Tooltip.create(tr("palettes.chromastudio.export.place.tip")));
            if (!LitematicaBridge.isAvailable()) Widgets.disable(placeButton, tr("palettes.chromastudio.export.place.no_litematica"));
            else if (format != SchematicExporter.Format.LITEMATIC) Widgets.disable(placeButton, tr("palettes.chromastudio.export.place.nbt"));
            else if (Minecraft.getInstance().level == null) Widgets.disable(placeButton, tr("export.chromastudio.litematica.no_world"));
            widgets.button(in.right() - placeW - PanelTab.GAP - exportW, y, export, () -> run(false));
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            int half = in.x() + in.w() / 2, y = in.y();
            label(g, "palettes.chromastudio.export.layout", in.x(), y);
            y += PanelTab.ROW;
            label(g, "palettes.chromastudio.export.format", in.x(), y);
            label(g, "palettes.chromastudio.export.gap", half, y);
            y += PanelTab.ROW;
            label(g, "palettes.chromastudio.export.height", in.x(), y);
            label(g, "palettes.chromastudio.export.width", half, y);
            y += PanelTab.ROW;
            label(g, "palettes.chromastudio.export.patch", half, y);
            if (skipped != null) g.drawString(PanelTab.font(), PanelTab.fit(skipped.getString(), in.w()), in.x(), in.bottom() - STATUS_H - 9, Theme.FAIR, false);
        }

        @Override
        protected boolean confirm() {
            run(false);
            return true;
        }

        /** A file inside the game's folder is named from there, which is short enough to read in the dialog. */
        private static String shortPath(Path file) {
            Path game = ChromaStudioClient.platform.gameDir().toAbsolutePath().normalize(), full = file.toAbsolutePath().normalize();
            return full.startsWith(game) ? game.relativize(full).toString() : full.toString();
        }

        /** Text cut to a width by leaving out its middle, so a file's folder and the end of its name both show. */
        static String middle(String text, int width) {
            if (PanelTab.font().width(text) <= width) return text;
            String dots = "...";
            int room = Math.max(0, width - PanelTab.font().width(dots)), tail = room * 3 / 5;
            String end = PanelTab.font().plainSubstrByWidth(text, tail, true);
            return PanelTab.font().plainSubstrByWidth(text, room - PanelTab.font().width(end)) + dots + end;
        }

        private void run(boolean place) {
            skipped = null;
            LayoutOptions options = new LayoutOptions(layout, height, patch, width, gap, seed);
            SchematicExporter.Result result;
            try {
                result = SchematicExporter.export(palette, options, format);
            } catch (SchematicExporter.NoBlocksException ignored) {
                status(tr("command.chromastudio.export.empty"), Theme.BAD);
                return;
            } catch (IOException e) {
                status(tr("command.chromastudio.export.failed", String.valueOf(e.getMessage())), Theme.BAD);
                return;
            }
            if (!result.skippedIds().isEmpty()) skipped = tr("palettes.chromastudio.export.skipped", String.join(", ", result.skippedIds()));
            // A path has no spaces to wrap at. The name is what tells two exports apart, and its end holds the layout and the number.
            status(tr("command.chromastudio.export.done", middle(shortPath(result.file()), box.w() - 10)), Theme.GOOD);
            if (!place) return;
            BlockPos origin = LitematicaBridge.originFor(ModSettings.litematicaPlacement);
            String name = palette.name == null || palette.name.isBlank() ? result.file().getFileName().toString() : palette.name;
            Optional<Component> problem = LitematicaBridge.place(result.file(), origin, name);
            if (problem.isPresent()) status(problem.get(), Theme.BAD);
            else status(tr("command.chromastudio.place.done", name, origin.getX(), origin.getY(), origin.getZ()), Theme.GOOD);
        }
    }

    /** Builds a WorldEdit command from the palette, to copy or to send as chat. */
    static final class WorldEdit extends Dialog {
        private static String which = WorldEditPattern.COMMANDS.getFirst(), from = "";

        private final Palette palette;
        private int labelW;
        private Button copy, send;

        WorldEdit(Palette palette) {
            super(tr("palettes.chromastudio.worldedit.title"), 320, 170);
            this.palette = palette;
        }

        /** The command, or null when it can't be built yet. */
        private String command() {
            try {
                return WorldEditPattern.command(which, palette, from);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.worldedit.command", "palettes.chromastudio.worldedit.from");
            int x = in.x() + labelW, y = in.y();
            widgets.cycler(x, y, 80, null, WorldEditPattern.COMMANDS, which, c -> Component.literal("//" + c), c -> { which = c; refresh(); });
            y += PanelTab.ROW;
            Component copyLabel = tr("palettes.chromastudio.copy"), sendLabel = tr("palettes.chromastudio.worldedit.send");
            int sendW = Widgets.buttonWidth(sendLabel), copyW = Widgets.buttonWidth(copyLabel);
            send = widgets.button(in.right() - sendW, in.bottom() - Widgets.H - STATUS_H, sendLabel, () -> {
                // Chat commands go without their first slash, so a WorldEdit command keeps its second.
                ChromaStudioClient.platform.sendChatCommand(command().substring(1));
                status(tr("palettes.chromastudio.worldedit.sent"), Theme.GOOD);
            });
            copy = widgets.button(in.right() - sendW - PanelTab.GAP - copyW, in.bottom() - Widgets.H - STATUS_H, copyLabel, () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(command());
                status(tr("palettes.chromastudio.worldedit.copied"), Theme.GOOD);
            });
            // The field comes last: giving it its text tells its responder, which greys the buttons.
            EditBox field = widgets.search(x, y, in.right() - x, tr("palettes.chromastudio.worldedit.from.hint"), s -> { from = s; enable(); });
            field.setValue(from);
            if (!which.equals("replace")) Widgets.disable(field, tr("palettes.chromastudio.worldedit.from.na"));
            enable();
        }

        private void enable() {
            boolean ready = command() != null, inWorld = Minecraft.getInstance().player != null;
            copy.active = ready;
            send.active = ready && inWorld;
            Component why = !ready ? tr("palettes.chromastudio.worldedit.from.needed") : !inWorld ? tr("palettes.chromastudio.no_world") : tr("palettes.chromastudio.worldedit.send.tip");
            send.setTooltip(Tooltip.create(why));
            copy.setTooltip(ready ? null : Tooltip.create(why));
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            label(g, "palettes.chromastudio.worldedit.command", in.x(), in.y());
            label(g, "palettes.chromastudio.worldedit.from", in.x(), in.y() + PanelTab.ROW);
            int top = in.y() + 2 * PanelTab.ROW, bottom = in.bottom() - Widgets.H - STATUS_H - PanelTab.GAP;
            g.fill(in.x(), top, in.right(), bottom, Theme.WELL);
            String command = command();
            Component shown = command != null ? Component.literal(command) : tr("palettes.chromastudio.worldedit.from.needed");
            List<FormattedCharSequence> lines = PanelTab.font().split(shown, in.w() - 6);
            int fit = Math.max(1, (bottom - top - 4) / 9);
            for (int i = 0; i < Math.min(fit, lines.size()); i++) {
                g.drawString(PanelTab.font(), lines.get(i), in.x() + 3, top + 3 + i * 9, command != null ? Theme.TEXT : Theme.FAIR, false);
            }
            // A long palette's command runs past the box. Copy still takes all of it, and pointing at the box shows it.
            if (lines.size() > fit) {
                g.drawString(PanelTab.font(), "...", in.right() - 12, bottom - 10, Theme.DIM, false);
                if (mouseX >= in.x() && mouseX < in.right() && mouseY >= top && mouseY < bottom) g.renderTooltip(PanelTab.font(), lines, mouseX, mouseY);
            }
        }
    }

    /** Fills the hotbar from a list of entries, and undoes it. */
    static final class Hotbar extends Dialog {
        private final List<PaletteEntry> entries;
        /** What the last fill or undo did. It takes the description's place, so it has the room to be read whole in any language. */
        private Component result;

        Hotbar(List<PaletteEntry> entries) {
            super(tr("palettes.chromastudio.hotbar.title"), 270, 124);
            this.entries = List.copyOf(entries);
        }

        @Override
        protected void build(Bounds in) {
            Component fill = tr("palettes.chromastudio.hotbar.fill"), undo = tr("palettes.chromastudio.hotbar.undo"), boxes = tr("palettes.chromastudio.hotbar.boxes");
            int y = in.bottom() - Widgets.H, undoW = Widgets.buttonWidth(undo);
            // The mode is the settings' own, shown and changed here, so the dialog always says which one a fill goes by.
            // It takes only the room its widest name needs; Fill is the point of the dialog, so Fill gets the rest.
            List<OverflowMode> modes = List.of(OverflowMode.values());
            Function<OverflowMode, Component> names = m -> tr("option.chromastudio.overflowmode." + m.name().toLowerCase(Locale.ROOT));
            int modeW = modes.stream().mapToInt(m -> Widgets.buttonWidth(CommonComponents.optionNameValue(boxes, names.apply(m)))).max().orElse(0);
            modeW = Math.min(modeW, in.w() - undoW - Widgets.buttonWidth(fill) - 2 * PanelTab.GAP);   // a long translation never squeezes Fill below its text
            widgets.cycler(in.x(), y, modeW, boxes, modes, ModSettings.hotbarOverflowMode, names, m -> {
                        ModSettings.hotbarOverflowMode = m;
                        ModSettings.save();
                        result = null;
                    }, tr("settings.chromastudio.hotbarOverflowMode.tip"));
            int fillX = in.x() + modeW + PanelTab.GAP, fillW = in.right() - undoW - PanelTab.GAP - fillX;
            Button fillButton = widgets.button(fillX, y, fillW, fill, () -> {
                result = HotbarFill.fill(entries);
                refresh();
            });
            HotbarFill.unavailable().ifPresent(why -> Widgets.disable(fillButton, why));
            Button undoButton = widgets.button(in.right() - undoW, y, undo, () -> {
                result = HotbarFill.undo();
                refresh();
            });
            if (!HotbarFill.canUndo()) Widgets.disable(undoButton, HotbarFill.unavailable().orElse(tr("hotbar.chromastudio.nothing_to_undo")));
        }

        @Override
        protected void draw(GuiGraphics g, Bounds in, int mouseX, int mouseY) {
            int over = Math.max(0, entries.size() - 9);
            Component text = over == 0 ? tr("palettes.chromastudio.hotbar.about", entries.size())
                    : tr("palettes.chromastudio.hotbar.about." + ModSettings.hotbarOverflowMode.name().toLowerCase(Locale.ROOT), over);
            g.drawWordWrap(PanelTab.font(), result != null ? result : text, in.x(), in.y(), in.w(), result != null ? Theme.TEXT : Theme.DIM);
        }
    }

    /** The share string of a palette, for the clipboard. */
    static String share(Palette p) { return ShareString.encode(p); }

    /** Whether the index can answer yet. The tools that match colours to blocks wait for it. */
    static boolean indexReady() { return BlockIndex.get().ready(); }
}
