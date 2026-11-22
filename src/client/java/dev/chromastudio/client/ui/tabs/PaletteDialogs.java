package dev.chromastudio.client.ui.tabs;

import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.client.ModSettings;
import dev.chromastudio.client.OverflowMode;
import dev.chromastudio.client.PaletteStore;
import dev.chromastudio.client.action.Clipboard;
import dev.chromastudio.client.action.FileDialog;
import dev.chromastudio.client.action.HotbarFill;
import dev.chromastudio.client.action.HotbarPlan;
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
import dev.chromastudio.core.palette.UnreadablePalette;
import dev.chromastudio.core.palette.ShareString;
import dev.chromastudio.core.palette.WorldEditPattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class PaletteDialogs {
    private PaletteDialogs() {}

    private static MutableComponent tr(String key, Object... args) { return Component.translatable(key, args); }

    private static Component option(Enum<?> value) {
        return tr("option.chromastudio." + value.getClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static void label(GuiGraphicsExtractor g, String key, int x, int y) {
        g.text(PanelTab.font(), tr(key), x, y + 4, Theme.DIM, false);
    }

    private static int labelWidth(String... keys) {
        int w = 0;
        for (String key : keys) w = Math.max(w, Widgets.tw(tr(key)));
        return w + 6;
    }

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
                    () -> status(tr("palettes.chromastudio.no_dialog"), Theme.FAIR)));
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
                if (s.startsWith("chroma")) p = PaletteStore.get().importShare(s);
                else p = PaletteStore.get().importJson(Files.readString(Path.of(s)));
                close();
                done.accept(p);
            } catch (UnreadablePalette e) {
                status(tr("palettes.chromastudio.import.bad", PaletteStore.why(e)), Theme.BAD);
            } catch (InvalidPathException _) {
                status(tr("palettes.chromastudio.import.bad", s), Theme.BAD);
            } catch (IOException _) {
                status(tr("palettes.chromastudio.import.no_file", s), Theme.BAD);
            }
            return true;
        }
    }

    static final class Generate extends Dialog {
        private static Harmony harmony = Harmony.ANALOGOUS;

        private final Consumer<Palette> done;
        private String hex;
        private int count = ModSettings.generateCount;
        private int labelW;

        Generate(Color seed, Consumer<Palette> done) {
            super(tr("palettes.chromastudio.generate.title"), 250, 106);
            this.done = done;
            hex = seed.hex();
        }

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

    static final class Export extends Dialog {
        private final Palette palette;
        private Layout layout;
        private SchematicExporter.Format format = SchematicExporter.Format.LITEMATIC;
        private int height, patch = ModSettings.patchSize, width = LayoutOptions.DIORAMA_WIDTH;
        private boolean gap = ModSettings.exportGap;
        private final long seed;
        private Component skipped;
        private final List<Component> statusTip = new ArrayList<>();
        private int labelW;

        Export(Palette palette, Layout layout, long seed) {
            super(tr("palettes.chromastudio.export.title"), 290, 150);
            this.palette = palette;
            this.layout = layout;
            this.seed = seed;
            height = layout == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT;
        }

        @Override
        protected int statusLines() { return 3; }

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
            int statusH = statusLines() * 9;
            if (skipped != null) g.text(PanelTab.font(), PanelTab.fit(skipped.getString(), in.w()), in.x(), in.bottom() - statusH - 11, Theme.FAIR, false);
            if (!statusTip.isEmpty() && over(mouseX, mouseY, in.x(), in.bottom() - statusH, in.w(), statusH)) tip(g, statusTip, mouseX, mouseY);
        }

        @Override
        protected boolean confirm() {
            run(false);
            return true;
        }

        private static String shortPath(Path file) {
            Path game = ChromaStudioClient.platform.gameDir().toAbsolutePath().normalize(), full = file.toAbsolutePath().normalize();
            return full.startsWith(game) ? game.relativize(full).toString() : full.toString();
        }

        static String middle(String text, int width) {
            if (PanelTab.font().width(text) <= width) return text;
            String dots = "...";
            int room = Math.max(0, width - PanelTab.font().width(dots)), tail = room * 3 / 5;
            String end = PanelTab.font().plainSubstrByWidth(text, tail, true);
            return PanelTab.font().plainSubstrByWidth(text, room - PanelTab.font().width(end)) + dots + end;
        }

        private void run(boolean place) {
            skipped = null;
            statusTip.clear();
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
            int room = box.w() - 10 - Widgets.tw(tr("command.chromastudio.export.done", ""));
            String path = shortPath(result.file()), shown = middle(path, room);
            Component route = format == SchematicExporter.Format.LITEMATIC && AxiomBridge.whyNot().isEmpty() ? AxiomBridge.named("palettes.chromastudio.export.axiom").withColor(Theme.DIM) : null;
            MutableComponent done = tr("command.chromastudio.export.done", shown);
            if (route != null) done.append("\n").append(route);
            if (!shown.equals(path)) statusTip.add(Component.literal(path));
            if (!place) {
                status(done, Theme.GOOD);
                return;
            }
            BlockPos origin = LitematicaBridge.originFor(ModSettings.litematicaPlacement);
            String name = palette.name == null || palette.name.isBlank() ? result.file().getFileName().toString() : palette.name;
            Optional<Component> problem = LitematicaBridge.place(result.file(), origin, name);
            Component placed = problem.orElseGet(() -> tr("command.chromastudio.place.done", name, origin.getX(), origin.getY(), origin.getZ()));
            String flat = placed.getString().replace('\n', ' '), line = PanelTab.fit(flat, box.w() - 10);
            if (!line.equals(flat)) statusTip.add(placed);
            done.append("\n").append(Component.literal(line).withColor(problem.isPresent() ? Theme.BAD : Theme.GOOD));
            status(done, Theme.GOOD);
        }
    }

    static final class WorldEdit extends Dialog {
        private static String which = WorldEditPattern.COMMANDS.getFirst(), from = "";

        private final Palette palette;
        private int labelW;
        private Button copy, send;

        WorldEdit(Palette palette) {
            super(tr("palettes.chromastudio.worldedit.title"), 320, 170);
            this.palette = palette;
        }

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
                ChromaStudioClient.platform.sendChatCommand(command().substring(1));
                status(tr("palettes.chromastudio.worldedit.sent"), Theme.GOOD);
            });
            copy = widgets.button(in.right() - sendW - PanelTab.GAP - copyW, in.bottom() - Widgets.H - STATUS_H, copyLabel, () -> {
                Clipboard.copy(command()).ifPresentOrElse(why -> status(why, Theme.BAD),
                        () -> status(tr("palettes.chromastudio.worldedit.copied"), Theme.GOOD));
            });
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
            if (lines.size() > fit) {
                g.text(PanelTab.font(), "...", in.right() - 12, bottom - 10, Theme.DIM, false);
                if (mouseX >= in.x() && mouseX < in.right() && mouseY >= top && mouseY < bottom) g.setTooltipForNextFrame(PanelTab.font(), lines, mouseX, mouseY);
            }
        }
    }

    record Fitted(List<FormattedText> lines, boolean cut) {
        int height() { return lines.size() * 9; }
    }

    static Fitted fit(StringSplitter splitter, @Nullable Component text, int width, int most) {
        if (text == null) return new Fitted(List.of(), false);
        List<FormattedText> lines = splitter.splitLines(text, width, Style.EMPTY);
        if (lines.size() <= most) return new Fitted(lines, false);
        List<FormattedText> kept = new ArrayList<>(lines.subList(0, most - 1));
        String last = lines.get(most - 1).getString();
        kept.add(FormattedText.of(splitter.plainHeadByWidth(last, width - (int) Math.ceil(splitter.stringWidth("...")), Style.EMPTY) + "..."));
        return new Fitted(kept, true);
    }

    private static int drawLines(GuiGraphicsExtractor g, Fitted text, int x, int y, int colour) {
        for (FormattedText line : text.lines()) {
            g.text(PanelTab.font(), Language.getInstance().getVisualOrder(line), x, y, colour, false);
            y += 9;
        }
        return y;
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void tip(GuiGraphicsExtractor g, List<Component> lines, int mouseX, int mouseY) {
        MutableComponent all = Component.empty();
        for (Component line : lines) {
            if (line != lines.getFirst()) all.append("\n");
            all.append(line);
        }
        List<FormattedCharSequence> split = Tooltip.splitTooltip(Minecraft.getInstance(), all);
        int most = Math.max(1, (Minecraft.getInstance().getWindow().getGuiScaledHeight() - TIP_MARGIN) / 10);
        if (split.size() > most) {
            split = new ArrayList<>(split.subList(0, most - 1));
            split.add(Component.literal("...").getVisualOrderText());
        }
        g.setTooltipForNextFrame(PanelTab.font(), split, mouseX, mouseY);
    }

    private static final int TIP_MARGIN = 16, TIP_WIDTH = 170;

    private static Component screenTip(String text) {
        List<FormattedText> lines = PanelTab.font().getSplitter().splitLines(text, TIP_WIDTH, Style.EMPTY);
        int most = Math.max(1, (Minecraft.getInstance().getWindow().getGuiScaledHeight() - TIP_MARGIN) / 10);
        if (lines.size() > most) {
            lines = new ArrayList<>(lines.subList(0, most - 1));
            lines.add(FormattedText.of("..."));
        }
        MutableComponent out = Component.empty();
        for (FormattedText line : lines) {
            if (line != lines.getFirst()) out.append("\n");
            out.append(line.getString());
        }
        return out;
    }

    static final class Axiom extends Dialog {
        private enum Script { RECOLOUR, SCATTER }

        static final int WIDTH = 300, SCOPE_LINES = 1, WARNING_LINES = 2, BODY_LINES = 5;

        private static Script script = Script.RECOLOUR;
        private static AxiomScript.Axis axis = AxiomScript.Axis.Y;
        private static Recolour.Mode match = Recolour.Mode.SHADING;
        private static String fromKey = "";

        private final Palette palette;
        private final String gradientName;
        private final List<PaletteEntry> steps;
        private AxiomBridge.Script made;
        private Component problem;
        private int labelW;
        private Lines lines;

        Axiom(Palette palette) {
            super(tr("axiom.chromastudio.title"), WIDTH, height(3));
            this.palette = palette;
            this.gradientName = null;
            this.steps = null;
        }

        Axiom(String gradientName, List<PaletteEntry> steps) {
            super(tr("axiom.chromastudio.title"), WIDTH, height(1));
            this.palette = null;
            this.gradientName = gradientName;
            this.steps = List.copyOf(steps);
        }

        private static int height(int rows) {
            return TITLE_H + 7 + rows * PanelTab.ROW + PanelTab.GAP + BODY_LINES * 9 + 2 * PanelTab.GAP + Widgets.H + STATUS_H;
        }

        record Lines(Fitted scope, Fitted size, Fitted warning, Fitted problem, int detailsW) {}

        static Lines lines(StringSplitter splitter, int width, AxiomBridge.@Nullable Script made, @Nullable Component problem) {
            Fitted none = new Fitted(List.of(), false);
            if (made == null) return new Lines(none, none, none, fit(splitter, problem, width, BODY_LINES), 0);
            int detailsW = (int) Math.ceil(splitter.stringWidth(tr("axiom.chromastudio.script.details"))) + 2 * PanelTab.GAP;
            return new Lines(fit(splitter, made.scope(), width, SCOPE_LINES), fit(splitter, made.size(), width - detailsW, 1),
                    fit(splitter, made.warning(), width, WARNING_LINES), none, detailsW);
        }

        private void make() {
            made = null;
            problem = null;
            try {
                if (palette == null) {
                    var player = Minecraft.getInstance().player;
                    made = AxiomBridge.gradientScript(gradientName, steps, axis, player == null ? BlockPos.ZERO : player.blockPosition());
                }
                else if (script == Script.SCATTER) made = AxiomBridge.scatterScript(palette);
                else made = AxiomBridge.recolourScript(palette, from(), match);
            } catch (AxiomBridge.Refused r) {
                problem = r.reason();
            }
        }

        private Palette from() {
            for (Palette p : fromChoices()) if (p != palette && key(p).equals(fromKey)) return p;
            return null;
        }

        private static String key(Palette p) {
            String preset = PaletteStore.get().presetId(p);
            return preset != null ? "preset:" + preset : "own:" + p.name;
        }

        private List<Palette> fromChoices() {
            List<Palette> out = new ArrayList<>();
            out.add(palette);
            for (Palette p : PaletteStore.get().all()) if (p != palette) out.add(p);
            return out;
        }

        private int rows() { return palette == null ? 1 : 3; }

        @Override
        protected void build(Bounds in) {
            make();
            lines = lines(PanelTab.font().getSplitter(), in.w(), made, problem);
            int x, y = in.y();
            if (palette == null) {
                labelW = labelWidth("axiom.chromastudio.axis");
                x = in.x() + labelW;
                widgets.cycler(x, y, 40, null, List.of(AxiomScript.Axis.values()), axis, a -> Component.literal(a.name().toLowerCase(Locale.ROOT)),
                        a -> { axis = a; refresh(); }, tr("axiom.chromastudio.axis.tip"));
            } else {
                labelW = labelWidth("axiom.chromastudio.script", "axiom.chromastudio.from", "axiom.chromastudio.match");
                x = in.x() + labelW;
                int w = in.right() - x;
                widgets.cycler(x, y, w, null, List.of(Script.values()), script, s -> tr("axiom.chromastudio.script." + s.name().toLowerCase(Locale.ROOT)),
                        s -> { script = s; refresh(); }, tr("axiom.chromastudio.script.tip"));
                y += PanelTab.ROW;
                Palette from = from() == null ? palette : from();
                Function<Palette, String> name = p -> p == palette ? tr("axiom.chromastudio.from.any").getString() : AxiomBridge.fromName(p);
                boolean fitted = Export.middle(name.apply(from), w - 10).equals(name.apply(from));
                var fromButton = widgets.cycler(x, y, w, null, fromChoices(), from, p -> Component.literal(Export.middle(name.apply(p), w - 10)),
                        p -> { fromKey = p == palette ? "" : key(p); refresh(); },
                        fitted ? tr("axiom.chromastudio.from.tip") : screenTip(name.apply(from)));
                y += PanelTab.ROW;
                var matchButton = widgets.cycler(x, y, w, null, List.of(Recolour.Mode.values()), match,
                        m -> tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT)), m -> { match = m; refresh(); }, tr("axiom.chromastudio.match.tip"));
                if (script != Script.RECOLOUR) {
                    Widgets.disable(fromButton, tr("axiom.chromastudio.script.recolour_only"));
                    Widgets.disable(matchButton, tr("axiom.chromastudio.script.recolour_only"));
                }
            }
            Component copyLabel = tr("palettes.chromastudio.copy");
            Button copy = widgets.button(in.right() - Widgets.buttonWidth(copyLabel), copyY(in), copyLabel, () -> {
                AxiomBridge.copy(made);
                status(AxiomBridge.named("axiom.chromastudio.copied"), Theme.GOOD);
            });
            if (made == null) Widgets.disable(copy, problem);
        }

        private static int copyY(Bounds in) { return in.bottom() - Widgets.H - STATUS_H; }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            if (palette == null) label(g, "axiom.chromastudio.axis", in.x(), in.y());
            else {
                label(g, "axiom.chromastudio.script", in.x(), in.y());
                label(g, "axiom.chromastudio.from", in.x(), in.y() + PanelTab.ROW);
                label(g, "axiom.chromastudio.match", in.x(), in.y() + 2 * PanelTab.ROW);
            }
            int top = in.y() + rows() * PanelTab.ROW + PanelTab.GAP;
            if (made == null) {
                int bottom = drawLines(g, lines.problem(), in.x(), top, Theme.FAIR);
                if (lines.problem().cut() && over(mouseX, mouseY, in.x(), top, in.w(), bottom - top)) tip(g, List.of(problem), mouseX, mouseY);
                return;
            }
            int y = drawLines(g, lines.scope(), in.x(), top, Theme.TEXT);
            int sizeY = y;
            drawLines(g, lines.size(), in.x(), y, Theme.DIM);
            Component details = tr("axiom.chromastudio.script.details");
            int detailsX = in.right() - Widgets.tw(details);
            g.text(PanelTab.font(), details, detailsX, sizeY, Theme.DIM, false);
            g.horizontalLine(detailsX, in.right() - 1, sizeY + 9, Theme.DIM);
            if (over(mouseX, mouseY, in.x(), top, in.w(), sizeY + 10 - top)) {
                List<Component> all = new ArrayList<>();
                if (lines.scope().cut()) all.add(made.scope());
                if (lines.size().cut()) all.add(made.size());
                all.addAll(made.details());
                tip(g, all, mouseX, mouseY);
            }
            if (made.warning() == null) return;
            int warningY = copyY(in) - PanelTab.GAP - lines.warning().height();
            drawLines(g, lines.warning(), in.x(), warningY, Theme.FAIR);
            if (over(mouseX, mouseY, in.x(), warningY, in.w(), lines.warning().height())) {
                tip(g, lines.warning().cut() ? List.of(made.warning(), made.nextStep()) : List.of(made.nextStep()), mouseX, mouseY);
            }
        }
    }

    static final class Hotbar extends Dialog {
        static final int WIDTH = 300, HEADING = 11, DESTINATION_LINES = 3, WARNING_LINES = 2, RESULT_LINES = 3;

        private final List<PaletteEntry> entries;
        private final int blocks;
        private Component result;
        private List<Object> built = List.of();
        private boolean shown;

        Hotbar(List<PaletteEntry> entries) {
            super(tr("palettes.chromastudio.hotbar.title"), WIDTH, TITLE_H + 7 + HEADING + PanelTab.ROW + PanelTab.GAP + (DESTINATION_LINES + WARNING_LINES + RESULT_LINES) * 9
                    + 3 * PanelTab.GAP + Widgets.H);
            this.entries = List.copyOf(entries);
            this.blocks = HotbarFill.holdable(entries);
        }

        private static MutableComponent counted(String key, int n, Object... before) {
            if (n == 1) return tr(key + ".one", before);
            Object[] args = Arrays.copyOf(before, before.length + 1);
            args[before.length] = n;
            return tr(key, args);
        }

        static List<Component> destinations(int blocks, Optional<HotbarFill.AxiomPreview> preview, HotbarPlan plan) {
            List<Component> out = new ArrayList<>();
            out.add(counted("palettes.chromastudio.hotbar.to.hotbar", Math.min(HotbarFill.HOTBAR, blocks)));
            if (blocks <= HotbarFill.HOTBAR) return out;
            if (preview.isPresent()) {
                out.add(counted("palettes.chromastudio.hotbar.to.axiom", blocks - HotbarFill.HOTBAR, preview.get().hotbars()));
                return out;
            }
            if (plan.slotted() > 0) out.add(counted("palettes.chromastudio.hotbar.to.slots", plan.slotted()));
            if (plan.boxed() > 0) out.add(counted("palettes.chromastudio.hotbar.to.boxes", plan.boxed()));
            return out;
        }

        static int dropped(int blocks, HotbarPlan plan) {
            int dropped = Math.max(0, blocks - HotbarFill.HOTBAR) - plan.slotted() - plan.boxed();
            for (int i = plan.boxesSlotted(); i < plan.boxes().size(); i++) dropped += plan.boxes().get(i);
            return dropped;
        }

        static @Nullable Component warning(Optional<HotbarFill.AxiomPreview> preview, int dropped) {
            if (preview.isPresent()) {
                HotbarFill.AxiomPreview p = preview.get();
                return p.replaced() > 0 ? counted("palettes.chromastudio.hotbar.replaces", p.replaced(), p.hotbars()) : null;
            }
            return dropped > 0 ? counted("palettes.chromastudio.hotbar.drops", dropped) : null;
        }

        private HotbarPlan plan() {
            return HotbarPlan.of(ModSettings.hotbarOverflowMode, blocks - HotbarFill.HOTBAR, HotbarFill.freeSlots());
        }

        private int fillY(Bounds in) { return in.bottom() - RESULT_LINES * 9 - PanelTab.GAP - Widgets.H; }

        @Override
        protected void build(Bounds in) {
            Optional<HotbarFill.AxiomPreview> preview = HotbarFill.axiomPreview(blocks);
            built = state();
            shown = false;
            Component fits = blocks <= HotbarFill.HOTBAR ? tr("palettes.chromastudio.hotbar.fits") : null;
            int x = in.x(), y = in.y() + HEADING, w = (in.w() - PanelTab.GAP) / 2;
            CycleButton<Boolean> axiomButton = widgets.toggle(x, y, w, tr("palettes.chromastudio.hotbar.axiom"), HotbarFill.usesAxiom(), v -> {
                ModSettings.hotbarAxiom = v;
                ModSettings.save();
                result = null;
                refresh();
            }, tr("palettes.chromastudio.hotbar.axiom.tip"));
            Optional<Component> noAxiom = AxiomBridge.hotbarsUnavailable();
            if (noAxiom.isPresent()) Widgets.disable(axiomButton, noAxiom.get());
            else if (fits != null) Widgets.disable(axiomButton, fits);
            List<OverflowMode> modes = List.of(OverflowMode.values());
            Function<OverflowMode, Component> names = m -> tr("option.chromastudio.overflowmode." + m.name().toLowerCase(Locale.ROOT));
            CycleButton<OverflowMode> modeButton = widgets.cycler(x + w + PanelTab.GAP, y, w, tr("palettes.chromastudio.hotbar.boxes"), modes,
                    ModSettings.hotbarOverflowMode, names, m -> {
                        ModSettings.hotbarOverflowMode = m;
                        ModSettings.save();
                        result = null;
                        refresh();
                    }, tr("settings.chromastudio.hotbarOverflowMode.tip"));
            if (preview.isPresent()) Widgets.disable(modeButton, tr("hotbar.chromastudio.axiom.instead"));
            else if (fits != null) Widgets.disable(modeButton, fits);

            Component fill = tr("palettes.chromastudio.hotbar.fill"), undo = tr("palettes.chromastudio.hotbar.undo");
            int undoW = Widgets.buttonWidth(undo), fillW = Widgets.buttonWidth(fill);
            y = fillY(in);
            Button fillButton = widgets.button(in.right() - undoW - PanelTab.GAP - fillW, y, fill, () -> {
                if (!shown) return;
                result = HotbarFill.fill(entries, (Boolean) built.getFirst());
                refresh();
            });
            HotbarFill.whyNoFill().ifPresent(why -> Widgets.disable(fillButton, why));
            Button undoButton = widgets.button(in.right() - undoW, y, undo, () -> {
                result = HotbarFill.undo();
                refresh();
            });
            HotbarFill.whyNoUndo().ifPresent(why -> Widgets.disable(undoButton, why));
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            shown = true;
            g.text(PanelTab.font(), tr("palettes.chromastudio.hotbar.overflow"), in.x(), in.y(), Theme.DIM, false);
            Optional<HotbarFill.AxiomPreview> preview = HotbarFill.axiomPreview(blocks);
            StringSplitter splitter = PanelTab.font().getSplitter();
            int y = in.y() + HEADING + PanelTab.ROW + PanelTab.GAP;
            HotbarPlan plan = plan();
            List<Component> rows = destinations(blocks, preview, plan);
            for (Component row : rows) {
                Fitted line = fit(splitter, row, in.w(), 1);
                drawLines(g, line, in.x(), y, Theme.TEXT);
                if (line.cut() && over(mouseX, mouseY, in.x(), y, in.w(), 9)) tip(g, List.of(row), mouseX, mouseY);
                y += 9;
            }
            int fillY = fillY(in);
            Component warning = warning(preview, dropped(blocks, plan));
            Fitted warned = fit(splitter, warning, in.w(), WARNING_LINES);
            int warningY = fillY - PanelTab.GAP - warned.height();
            drawLines(g, warned, in.x(), warningY, Theme.FAIR);
            if (warned.cut() && over(mouseX, mouseY, in.x(), warningY, in.w(), warned.height())) tip(g, List.of(warning), mouseX, mouseY);
            Fitted said = fit(splitter, result, in.w(), RESULT_LINES);
            int saidY = fillY + Widgets.H + PanelTab.GAP;
            drawLines(g, said, in.x(), saidY, Theme.TEXT);
            if (said.cut() && over(mouseX, mouseY, in.x(), saidY, in.w(), said.height())) tip(g, List.of(result), mouseX, mouseY);
        }

        @Override
        void tick() {
            if (state().equals(built)) return;
            result = null;
            refresh();
        }

        private static List<Object> state() {
            return List.of(HotbarFill.usesAxiom(), reason(AxiomBridge.hotbarsUnavailable()), reason(HotbarFill.whyNoFill()), reason(HotbarFill.whyNoUndo()));
        }

        private static String reason(Optional<Component> why) { return why.map(Component::getString).orElse(""); }
    }

    static String share(Palette p) { return ShareString.encode(p); }

    static boolean indexReady() { return BlockIndex.get().ready(); }
}
