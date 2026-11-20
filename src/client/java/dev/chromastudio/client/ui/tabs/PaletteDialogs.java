package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.OverflowMode;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.FileDialog;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.ImageImport;
import dev.chromastudio.client.action.PaletteBlocks;
import dev.chromastudio.client.axiom.AxiomBridge;
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
import dev.chromastudio.core.palette.AxiomScript;
import dev.chromastudio.core.palette.Generator;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.chromastudio.core.palette.Recolour;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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
    private static void label(GuiGraphicsExtractor g, String key, int x, int y) {
        g.text(PanelTab.font(), tr(key), x, y + 4, Theme.DIM, false);
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
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            g.textWithWordWrap(PanelTab.font(), question, in.x(), in.y(), in.w(), Theme.TEXT, false);
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
            } catch (IOException _) {
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
            } catch (IllegalArgumentException _) {
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
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            label(g, "palettes.chromastudio.generate.seed", in.x(), in.y());
            label(g, "palettes.chromastudio.generate.harmony", in.x(), in.y() + PanelTab.ROW);
            label(g, "palettes.chromastudio.generate.count", in.x(), in.y() + 2 * PanelTab.ROW);
            Color seed = seed();
            int x = in.x() + labelW;
            if (seed != null) Widgets.swatch(g, x, in.y(), Widgets.H, Widgets.H, seed.rgb());
            else g.outline(x, in.y(), Widgets.H, Widgets.H, Theme.BAD);
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
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
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
            } catch (IOException | InvalidPathException _) {
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
        /** Where else the file can go, shown under the dialog's controls once it's written: Axiom's editor loads it. */
        private Component hint;
        private int labelW;

        /** {@code seed} places the diorama layouts, so an export from the Diorama panel comes out as it shows. */
        Export(Palette palette, Layout layout, long seed) {
            super(tr("palettes.chromastudio.export.title"), 290, 150);
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
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
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
            // The hint sits over the warning when there are both, in the room the dialog keeps for it.
            if (skipped != null) g.text(PanelTab.font(), PanelTab.fit(skipped.getString(), in.w()), in.x(), in.bottom() - STATUS_H - 9, Theme.FAIR, false);
            if (hint != null) g.text(PanelTab.font(), PanelTab.fit(hint.getString(), in.w()), in.x(), in.bottom() - STATUS_H - (skipped != null ? 18 : 9), Theme.DIM, false);
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
            hint = null;
            LayoutOptions options = new LayoutOptions(layout, height, patch, width, gap, seed);
            SchematicExporter.Result result;
            try {
                result = SchematicExporter.export(palette, options, format);
            } catch (SchematicExporter.NoBlocksException _) {
                status(tr("command.chromastudio.export.empty"), Theme.BAD);
                return;
            } catch (IOException e) {
                status(tr("command.chromastudio.export.failed", String.valueOf(e.getMessage())), Theme.BAD);
                return;
            }
            if (!result.skippedIds().isEmpty()) skipped = tr("palettes.chromastudio.export.skipped", String.join(", ", result.skippedIds()));
            // Axiom's File > Import Schematic reads .litematic files, but not .nbt ones.
            if (format == SchematicExporter.Format.LITEMATIC && AxiomBridge.whyNot().isEmpty()) hint = tr("palettes.chromastudio.export.axiom");
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
            } catch (IllegalArgumentException _) {
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
                Clipboard.copy(command()).ifPresentOrElse(why -> status(why, Theme.BAD),
                        () -> status(tr("palettes.chromastudio.worldedit.copied"), Theme.GOOD));
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
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            label(g, "palettes.chromastudio.worldedit.command", in.x(), in.y());
            label(g, "palettes.chromastudio.worldedit.from", in.x(), in.y() + PanelTab.ROW);
            int top = in.y() + 2 * PanelTab.ROW, bottom = in.bottom() - Widgets.H - STATUS_H - PanelTab.GAP;
            g.fill(in.x(), top, in.right(), bottom, Theme.WELL);
            String command = command();
            Component shown = command != null ? Component.literal(command) : tr("palettes.chromastudio.worldedit.from.needed");
            List<FormattedCharSequence> lines = PanelTab.font().split(shown, in.w() - 6);
            int fit = Math.max(1, (bottom - top - 4) / 9);
            for (int i = 0; i < Math.min(fit, lines.size()); i++) {
                g.text(PanelTab.font(), lines.get(i), in.x() + 3, top + 3 + i * 9, command != null ? Theme.TEXT : Theme.FAIR, false);
            }
            // A long palette's command runs past the box. Copy still takes all of it, and pointing at the box shows it.
            if (lines.size() > fit) {
                g.text(PanelTab.font(), "...", in.right() - 12, bottom - 10, Theme.DIM, false);
                if (mouseX >= in.x() && mouseX < in.right() && mouseY >= top && mouseY < bottom) g.setTooltipForNextFrame(PanelTab.font(), lines, mouseX, mouseY);
            }
        }
    }

    /**
     * Builds a script for Axiom's Script Brush, to copy: from a palette, a recolour into it or a scatter of it, and
     * from a gradient, a brush that paints it along an axis. The script is made afresh whenever a choice changes, so
     * the summary under the controls always says what Copy would give, and how long the script is.
     */
    static final class Axiom extends Dialog {
        private enum Script { RECOLOUR, SCATTER }

        private static Script script = Script.RECOLOUR;
        private static AxiomScript.Axis axis = AxiomScript.Axis.Y;
        private static Recolour.Mode match = Recolour.Mode.SHADING;
        // The palette a recolour changes blocks from, so a choice outlives the dialog: a preset by its id and the
        // player's own by name, as a palette of their own may share a preset's shown name. Empty for any block.
        private static String fromKey = "";

        private final Palette palette;
        private final String gradientName;
        private final List<PaletteEntry> steps;
        private AxiomBridge.Script made;
        private Component problem;
        private int labelW;

        /** A recolour or scatter of a palette. */
        Axiom(Palette palette) {
            // Room for the summary, the script's size and the note on servers' limit, which is the part a player most needs.
            super(tr("axiom.chromastudio.title"), 300, 140 + PanelTab.ROW + 18);
            this.palette = palette;
            this.gradientName = null;
            this.steps = null;
        }

        /** A gradient along an axis. */
        Axiom(String gradientName, List<PaletteEntry> steps) {
            super(tr("axiom.chromastudio.title"), 300, 100);
            this.palette = null;
            this.gradientName = gradientName;
            this.steps = List.copyOf(steps);
        }

        private void make() {
            made = null;
            problem = null;
            try {
                if (palette == null) made = AxiomBridge.gradientScript(gradientName, steps, axis);
                else if (script == Script.SCATTER) made = AxiomBridge.scatterScript(palette);
                else made = AxiomBridge.recolourScript(palette, from(), match);
            } catch (AxiomBridge.Refused r) {
                problem = r.reason();
            }
        }

        /** The palette a recolour changes blocks from, or null for any block. */
        private Palette from() {
            // The palette itself stands for any block, so a remembered name that happens to be its own isn't a choice.
            for (Palette p : fromChoices()) if (p != palette && key(p).equals(fromKey)) return p;
            return null;
        }

        private static String key(Palette p) {
            String preset = PaletteStore.get().presetId(p);
            return preset != null ? "preset:" + preset : "own:" + p.name;
        }

        /** Any block, then every other palette. A list can't hold null, so the palette itself stands for any block. */
        private List<Palette> fromChoices() {
            List<Palette> out = new ArrayList<>();
            out.add(palette);
            for (Palette p : PaletteStore.get().all()) if (p != palette) out.add(p);
            return out;
        }

        @Override
        protected void build(Bounds in) {
            make();
            int x, y = in.y();
            if (palette == null) {
                labelW = labelWidth("axiom.chromastudio.axis");
                x = in.x() + labelW;
                widgets.cycler(x, y, 40, null, List.of(AxiomScript.Axis.values()), axis, a -> Component.literal(a.name().toLowerCase(Locale.ROOT)),
                        a -> { axis = a; refresh(); }, tr("axiom.chromastudio.axis.tip"));
            } else {
                labelW = labelWidth("axiom.chromastudio.script", "axiom.chromastudio.from", "axiom.chromastudio.match");
                x = in.x() + labelW;
                widgets.cycler(x, y, in.right() - x, null, List.of(Script.values()), script, s -> tr("axiom.chromastudio.script." + s.name().toLowerCase(Locale.ROOT)),
                        s -> { script = s; refresh(); }, tr("axiom.chromastudio.script.tip"));
                y += PanelTab.ROW;
                Palette from = from();
                var fromButton = widgets.cycler(x, y, in.right() - x, null, fromChoices(), from == null ? palette : from,
                        p -> p == palette ? tr("axiom.chromastudio.from.any") : Component.literal(AxiomBridge.fromName(p)),
                        p -> { fromKey = p == palette ? "" : key(p); refresh(); }, tr("axiom.chromastudio.from.tip"));
                y += PanelTab.ROW;
                var matchButton = widgets.cycler(x, y, in.right() - x, null, List.of(Recolour.Mode.values()), match,
                        m -> tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT)), m -> { match = m; refresh(); }, tr("axiom.chromastudio.match.tip"));
                if (script != Script.RECOLOUR) {
                    Widgets.disable(fromButton, tr("axiom.chromastudio.from.na"));
                    Widgets.disable(matchButton, tr("axiom.chromastudio.from.na"));
                }
            }
            Component copyLabel = tr("palettes.chromastudio.copy");
            Button copy = widgets.button(in.right() - Widgets.buttonWidth(copyLabel), in.bottom() - Widgets.H - STATUS_H, copyLabel, () -> {
                AxiomBridge.copy(made);
                status(tr("axiom.chromastudio.copied"), Theme.GOOD);
            });
            if (made == null) Widgets.disable(copy, problem);
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            if (palette == null) label(g, "axiom.chromastudio.axis", in.x(), in.y());
            else {
                label(g, "axiom.chromastudio.script", in.x(), in.y());
                label(g, "axiom.chromastudio.from", in.x(), in.y() + PanelTab.ROW);
                label(g, "axiom.chromastudio.match", in.x(), in.y() + 2 * PanelTab.ROW);
            }
            int top = in.y() + (palette == null ? 1 : 3) * PanelTab.ROW;
            Component said = made != null ? made.summary() : problem;
            List<FormattedCharSequence> lines = PanelTab.font().split(said, in.w());
            int fit = Math.max(1, (in.bottom() - Widgets.H - STATUS_H - PanelTab.GAP - top) / 9);
            for (int i = 0; i < Math.min(fit, lines.size()); i++) g.text(PanelTab.font(), lines.get(i), in.x(), top + i * 9, made != null ? Theme.TEXT : Theme.FAIR, false);
            if (lines.size() > fit && mouseX >= in.x() && mouseX < in.right() && mouseY >= top && mouseY < top + fit * 9)
                g.setTooltipForNextFrame(PanelTab.font(), lines, mouseX, mouseY);
        }
    }

    /** Fills the hotbar from a list of entries, and undoes it. */
    static final class Hotbar extends Dialog {
        private final List<PaletteEntry> entries;
        /** How many of the entries a fill puts somewhere, which is what its counts and hotbars go by. */
        private final int blocks;
        /** What the last fill or undo did. It takes the description's place, so it has the room to be read whole in any language. */
        private Component result;
        /** Whether the description stays under the result, as after a fill refused because where it goes had just changed. */
        private boolean describe;
        /** What {@link #state} was when the controls were laid out. */
        private List<Object> built = List.of();
        /** Whether the dialog has been drawn since it was laid out, so the player has seen what Fill does now. */
        private boolean shown;

        Hotbar(List<PaletteEntry> entries) {
            super(tr("palettes.chromastudio.hotbar.title"), 270, 124 + Widgets.H + PanelTab.GAP);
            this.entries = List.copyOf(entries);
            this.blocks = HotbarFill.holdable(entries);
        }

        @Override
        protected void build(Bounds in) {
            Optional<HotbarFill.AxiomPreview> preview = HotbarFill.axiomPreview(blocks);
            built = state();
            shown = false;
            Component fill = tr("palettes.chromastudio.hotbar.fill"), undo = tr("palettes.chromastudio.hotbar.undo"), boxes = tr("palettes.chromastudio.hotbar.boxes");
            int y = in.bottom() - Widgets.H, undoW = Widgets.buttonWidth(undo);
            // The mode is the settings' own, shown and changed here, so the dialog always says which one a fill goes by.
            // It takes only the room its widest name needs; Fill is the point of the dialog, so Fill gets the rest.
            List<OverflowMode> modes = List.of(OverflowMode.values());
            Function<OverflowMode, Component> names = m -> tr("option.chromastudio.overflowmode." + m.name().toLowerCase(Locale.ROOT));
            int modeW = modes.stream().mapToInt(m -> Widgets.buttonWidth(CommonComponents.optionNameValue(boxes, names.apply(m)))).max().orElse(0);
            modeW = Math.min(modeW, in.w() - undoW - Widgets.buttonWidth(fill) - 2 * PanelTab.GAP);   // a long translation never squeezes Fill below its text
            CycleButton<OverflowMode> modeButton = widgets.cycler(in.x(), y, modeW, boxes, modes, ModSettings.hotbarOverflowMode, names, m -> {
                        ModSettings.hotbarOverflowMode = m;
                        ModSettings.save();
                        result = null;
                    }, tr("settings.chromastudio.hotbarOverflowMode.tip"));
            // Axiom's hotbars take the blocks past the ninth instead of boxes, so while they do the boxes' mode has no say.
            if (preview.isPresent()) Widgets.disable(modeButton, tr("hotbar.chromastudio.axiom.instead"));
            // A row of its own over the others, greyed with the reason when Axiom is missing or can't take them now.
            Component axiom = tr("palettes.chromastudio.hotbar.axiom");
            int axiomW = Math.min(in.w(), Math.max(Widgets.buttonWidth(CommonComponents.optionNameValue(axiom, CommonComponents.OPTION_ON)),
                    Widgets.buttonWidth(CommonComponents.optionNameValue(axiom, CommonComponents.OPTION_OFF))));
            // It reads what the fill does now: greyed out, Axiom's hotbars take nothing, whatever the setting says.
            CycleButton<Boolean> axiomButton = widgets.toggle(in.x(), y - Widgets.H - PanelTab.GAP, axiomW, axiom, HotbarFill.usesAxiom(), v -> {
                ModSettings.hotbarAxiom = v;
                ModSettings.save();
                result = null;
                refresh();
            }, tr("settings.chromastudio.hotbarAxiom.tip"));
            AxiomBridge.hotbarsUnavailable().ifPresent(why -> Widgets.disable(axiomButton, why));
            int fillX = in.x() + modeW + PanelTab.GAP, fillW = in.right() - undoW - PanelTab.GAP - fillX;
            Button fillButton = widgets.button(fillX, y, fillW, fill, () -> {
                // Fill goes the way the dialog says, or not at all: a click can come before the tick that lays it out again.
                // A second click queued behind one that changed the dialog waits until the player has seen the change.
                if (!shown) return;
                result = HotbarFill.fill(entries, (Boolean) built.getFirst());
                // When Fill refused because Axiom's hotbars came or went, the player is asked to read what it does now.
                describe = !state().getFirst().equals(built.getFirst());
                refresh();
            });
            HotbarFill.whyNoFill().ifPresent(why -> Widgets.disable(fillButton, why));
            Button undoButton = widgets.button(in.right() - undoW, y, undo, () -> {
                result = HotbarFill.undo();
                describe = false;
                refresh();
            });
            HotbarFill.whyNoUndo().ifPresent(why -> Widgets.disable(undoButton, why));
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            shown = true;
            // Read every frame, so what it says is what a click on Fill would do, even after a fill or undo just now.
            Optional<HotbarFill.AxiomPreview> preview = HotbarFill.axiomPreview(blocks);
            boolean replaces = preview.filter(p -> p.replaced() > 0).isPresent();
            int over = Math.max(0, blocks - 9);
            Component text = over == 0 ? tr("palettes.chromastudio.hotbar.about", blocks)
                    : preview.map(p -> about(over, p))
                            .orElseGet(() -> tr("palettes.chromastudio.hotbar.about." + ModSettings.hotbarOverflowMode.name().toLowerCase(Locale.ROOT), over));
            // The result takes the description's place. A warning that the next fill replaces what the player keeps in
            // Axiom's hotbars keeps rows of its own at the bottom, in the warning colour, however long the result.
            int fit = Math.max(1, (in.bottom() - 2 * Widgets.H - 2 * PanelTab.GAP - in.y()) / 9);
            List<FormattedCharSequence> warning = result != null && !replaces && !describe ? List.of() : PanelTab.font().split(text, in.w());
            List<FormattedCharSequence> said = result != null ? PanelTab.font().split(result, in.w()) : List.of();
            int warningRows = result == null ? fit : Math.min(warning.size(), fit - 2);
            int saidRows = result == null ? 0 : Math.min(said.size(), Math.max(1, fit - (warningRows > 0 ? warningRows + 1 : 0)));
            for (int i = 0; i < saidRows; i++) g.text(PanelTab.font(), said.get(i), in.x(), in.y() + i * 9, Theme.TEXT, false);
            int warningTop = in.y() + (saidRows > 0 ? (saidRows + 1) * 9 : 0);
            int colour = replaces ? Theme.FAIR : Theme.DIM;
            for (int i = 0; i < Math.min(warningRows, warning.size()); i++) g.text(PanelTab.font(), warning.get(i), in.x(), warningTop + i * 9, colour, false);
            // Whatever was cut is shown whole while the pointer is over the text.
            if ((said.size() > saidRows || warning.size() > warningRows) && mouseX >= in.x() && mouseX < in.right() && mouseY >= in.y() && mouseY < in.y() + fit * 9) {
                List<FormattedCharSequence> all = new ArrayList<>(said);
                if (!said.isEmpty() && !warning.isEmpty()) all.add(FormattedCharSequence.EMPTY);
                all.addAll(warning);
                g.setTooltipForNextFrame(PanelTab.font(), all, mouseX, mouseY);
            }
        }

        /**
         * Lays the controls out again when Axiom's hotbars come or go while the dialog is open, as when a call into
         * them fails, so the controls and the description say what Fill will do before it is clicked.
         */
        @Override
        void tick() {
            if (state().equals(built)) return;
            result = null;
            describe = false;
            refresh();
        }

        /**
         * What the controls show that can change while the dialog is open, as when a call into Axiom fails or the
         * player's game mode changes: whether a fill would use Axiom's hotbars, and why the toggle, Fill and Undo can't
         * be used, if they can't. The first is what Fill goes by.
         */
        private static List<Object> state() {
            return List.of(HotbarFill.usesAxiom(), reason(AxiomBridge.hotbarsUnavailable()), reason(HotbarFill.whyNoFill()), reason(HotbarFill.whyNoUndo()));
        }

        private static String reason(Optional<Component> why) { return why.map(Component::getString).orElse(""); }

        /** What a fill into Axiom's hotbars will do, and how many slots there it replaces, which the player may want to keep. */
        private static Component about(int over, HotbarFill.AxiomPreview p) {
            if (p.replaced() == 0) return tr("palettes.chromastudio.hotbar.about.axiom", over, p.hotbars());
            return p.replaced() == 1 ? tr("palettes.chromastudio.hotbar.about.axiom.replaces_one", over, p.hotbars())
                    : tr("palettes.chromastudio.hotbar.about.axiom.replaces", over, p.hotbars(), p.replaced());
        }
    }

    /** The share string of a palette, for the clipboard. */
    static String share(Palette p) { return ShareString.encode(p); }

    /** Whether the index can answer yet. The tools that match colours to blocks wait for it. */
    static boolean indexReady() { return BlockIndex.get().ready(); }
}
