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
import dev.chromastudio.client.index.Query;
import dev.chromastudio.client.index.Target;
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
import dev.chromastudio.core.text.Plural;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    static List<Object> poolKey() {
        MatchState.follow();
        return List.of(MatchState.filters().state(), BlockIndex.get().version(), MatchState.revision());
    }

    static List<PaletteEntry> closest(List<Oklch> targets) {
        BlockIndex index = BlockIndex.get();
        Set<String> pool = new HashSet<>();
        for (Entry e : MatchState.filters().candidates(index, PaletteBlocks.face(), PaletteBlocks.scale(), PaletteBlocks.tint())) pool.add(e.id());
        Query q = PaletteBlocks.query().limit(Integer.MAX_VALUE);
        MatchState.filters().apply(q);
        List<PaletteEntry> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Oklch target : targets) {
            for (BlockIndex.Match m : index.closest(Target.of(Color.ofOklch(target)), q)) {
                if (!pool.contains(m.entry().id()) || !used.add(m.entry().id())) continue;
                out.add(PaletteBlocks.of(m.entry()));
                break;
            }
        }
        return out;
    }

    static final class Name extends Dialog {
        private static final int FIELD_Y = 12, WIDTH = 280;
        private final String initial;
        private final Component action;
        private final Predicate<String> taken;
        private final Consumer<String> done;
        private EditBox field;
        private String text;
        private boolean warned;
        private final boolean messageBelow;
        private int messageW;

        Name(Component title, Component action, String initial, Predicate<String> taken, Consumer<String> done) {
            this(title, action, initial, taken, done, messageBelow(action));
        }

        private Name(Component title, Component action, String initial, Predicate<String> taken, Consumer<String> done, boolean messageBelow) {
            super(title, WIDTH, messageBelow ? 81 : 70);
            this.messageBelow = messageBelow;
            this.initial = initial;
            this.action = action;
            this.taken = taken;
            this.done = done;
            text = initial;
        }

        private static int buttonW(Component action) { return Math.max(Widgets.buttonWidth(action), Widgets.buttonWidth(CommonComponents.GUI_CANCEL)); }

        private static boolean messageBelow(Component action) {
            int beside = WIDTH - 10 - 2 * buttonW(action) - 8;
            return Math.max(Widgets.tw(tr("palettes.chromastudio.name_needed")), Widgets.tw(tr("palettes.chromastudio.name_taken"))) > beside;
        }

        @Override
        protected void build(Bounds in) {
            boolean first = field == null;
            field = widgets.field(in.x(), in.y() + FIELD_Y, in.w(), text, s -> {
                text = s;
                warned = false;
            });
            field.setMaxLength(Integer.MAX_VALUE);
            field.setInvertHighlightedTextColor(false);
            field.setValue(text);
            if (first) {
                field.moveCursorToStart(true);
                field.setFocused(true);
                widgets.setFocused(field);
            }
            int y = in.bottom() - Widgets.H, w = buttonW(action);
            int cancelX = in.right() - 2 * w - 4;
            widgets.button(in.right() - w, y, w, action, this::confirm);
            widgets.button(cancelX, y, w, CommonComponents.GUI_CANCEL, this::close);
            messageW = messageBelow ? in.w() : cancelX - 4 - in.x();
        }

        @Override
        boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
            boolean handled = super.mouseClicked(e, doubled);
            if (tab.dialogOpen() && problem() != null) widgets.setFocused(field);
            return handled;
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            g.text(PanelTab.font(), tr("palettes.chromastudio.name_hint"), in.x(), in.y() + 1, Theme.DIM, false);
            Component problem = problem();
            if (problem == null) return;
            g.outline(field.getX() - 1, field.getY() - 1, field.getWidth() + 2, field.getHeight() + 2, Theme.BAD);
            int y = messageBelow ? field.getBottom() + 4 : in.bottom() - Widgets.H + 4;
            g.text(PanelTab.font(), PanelTab.fit(problem.getString(), messageW), in.x(), y, Theme.BAD, false);
            if (Widgets.tw(problem) > messageW && mouseX >= in.x() && mouseX < in.x() + messageW && mouseY >= y && mouseY < y + 9)
                g.setTooltipForNextFrame(PanelTab.font(), Tooltip.splitTooltip(Minecraft.getInstance(), problem), mouseX, mouseY);
        }

        private @Nullable Component problem() {
            String name = text.strip();
            if (name.isEmpty()) return warned ? tr("palettes.chromastudio.name_needed") : null;
            return !text.equals(initial) && taken.test(name) ? tr("palettes.chromastudio.name_taken") : null;
        }

        @Override
        protected boolean confirm() {
            warned = true;
            if (problem() != null) return true;
            close();
            if (!text.equals(initial)) done.accept(text.strip());
            return true;
        }
    }

    static final class Confirm extends Dialog {
        record Row(@Nullable Component label, @Nullable List<Integer> colours, Component detail) {}

        private static final int W = 240, NAME_W = W - 20, TIP_LINE = 10, TIP_MARGIN = 12, LINE = 11, NAME_LINE = 10, ROW = 11, SWATCH_W = 48, BUTTON_GAP = 6;
        private static final Component WARNING = tr("palettes.chromastudio.irreversible");
        private final Component question, action;
        private final String name;
        private final List<String> names;
        private final List<Row> rows;
        private final Runnable done;
        private final @Nullable Runnable back;
        private Button no, yes;

        Confirm(Component title, Component question, String name, List<Row> rows, Component action, Runnable done, @Nullable Runnable back) {
            this(title, question, name.replace('\n', ' '), nameLines(name.replace('\n', ' ')), rows, action, done, back);
        }

        private Confirm(Component title, Component question, String name, List<String> names, List<Row> rows, Component action, Runnable done, @Nullable Runnable back) {
            super(title, W, TITLE_H + 7 + (lines(question) + lines(WARNING)) * LINE + 2 + cardHeight(names, rows) + 7 + Widgets.H);
            this.question = question;
            this.name = name;
            this.names = names;
            this.rows = rows;
            this.action = action;
            this.done = done;
            this.back = back;
        }

        private static int lines(Component text) { return PanelTab.font().split(text, W - 10).size(); }

        private static List<String> wrapped(String name) {
            return PanelTab.font().getSplitter().splitLines(name, NAME_W, Style.EMPTY).stream().map(FormattedText::getString).toList();
        }

        private static List<String> nameLines(String name) {
            List<String> lines = wrapped(name);
            if (lines.size() < 2) return lines;
            if (lines.size() == 2) return balanced(name, lines);
            String rest = name.substring(Math.min(name.length(), lines.get(0).length())).strip();
            return List.of(lines.get(0), PanelTab.fit(rest, NAME_W));
        }

        private static List<String> balanced(String name, List<String> lines) {
            var font = PanelTab.font();
            List<String> best = lines;
            int bestW = Math.max(font.width(lines.get(0)), font.width(lines.get(1)));
            boolean words = name.indexOf(' ') > 0;
            for (int i = 1; i < name.length(); i++) {
                if (words ? name.charAt(i) != ' ' : Character.isLowSurrogate(name.charAt(i))) continue;
                String a = name.substring(0, i), b = name.substring(words ? i + 1 : i);
                int w = Math.max(font.width(a), font.width(b));
                if (w <= NAME_W && w < bestW) {
                    best = List.of(a, b);
                    bestW = w;
                }
            }
            return best;
        }

        private static int cardHeight(List<String> names, List<Row> rows) { return 5 + names.size() * NAME_LINE + rows.size() * ROW + 3; }

        @Override
        protected void build(Bounds in) {
            Component cancel = CommonComponents.GUI_CANCEL;
            int noW = Widgets.buttonWidth(cancel), yesW = Widgets.buttonWidth(action), y = in.bottom() - Widgets.H;
            no = widgets.button(in.right() - noW, y, cancel, this::cancel);
            yes = widgets.button(in.right() - noW - BUTTON_GAP - yesW, y, action.copy().withColor(Theme.FIELD_BAD), () -> {
                close();
                done.run();
            });
            widgets.setFocused(no);
        }

        @Override
        boolean keyPressed(KeyEvent e) {
            if (e.isCycleFocus() || e.isLeft() || e.isRight() || e.isUp() || e.isDown()) {
                widgets.setFocused(widgets.getFocused() == no ? yes : no);
                return true;
            }
            return super.keyPressed(e);
        }

        @Override
        void cancel() {
            if (back == null) close();
            else back.run();
        }

        private static int paragraph(GuiGraphicsExtractor g, Component text, Bounds in, int y, int colour) {
            for (FormattedCharSequence line : PanelTab.font().split(text, in.w())) {
                g.text(PanelTab.font(), line, in.x(), y, colour, false);
                y += LINE;
            }
            return y;
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            var font = PanelTab.font();
            int top = paragraph(g, question, in, in.y(), Theme.TEXT);
            top = paragraph(g, WARNING, in, top, Theme.FAIR);
            Bounds card = new Bounds(in.x(), top + 2, in.w(), cardHeight(names, rows));
            g.fill(card.x(), card.y(), card.right(), card.bottom(), Theme.WELL);
            g.outline(card.x(), card.y(), card.w(), card.h(), Theme.BORDER);
            int x = card.x() + 5, y = card.y() + 5;
            for (String line : names) {
                g.text(font, line, x, y, Theme.STRONG, false);
                y += NAME_LINE;
            }
            int labelW = 0;
            for (Row row : rows) if (row.label() != null) labelW = Math.max(labelW, font.width(row.label()) + 6);
            for (Row row : rows) {
                int rx = x;
                if (row.label() != null) g.text(font, row.label(), rx, y + 1, Theme.DIM, false);
                rx += labelW;
                if (row.colours() != null) {
                    SavedGradients.swatch(g, row.colours(), rx, y, SWATCH_W, 9);
                    rx += SWATCH_W + 5;
                }
                g.text(font, row.detail(), rx, y + 1, Theme.DIM, false);
                y += ROW;
            }
            if (card.contains(mouseX, mouseY) && names.size() < wrapped(name).size()) {
                List<FormattedCharSequence> tip = Tooltip.splitTooltip(Minecraft.getInstance(), Component.literal(name));
                g.setTooltipForNextFrame(font, tip.subList(0, Math.min(tip.size(), (g.guiHeight() - TIP_MARGIN) / TIP_LINE)), mouseX, mouseY);
            }
        }
    }

    static final class Import extends Dialog {
        private static final int LABEL_H = 11;

        private final Consumer<Palette> done;
        private String text = "";
        private EditBox field;
        private @Nullable Palette shared;
        private @Nullable Component problem;
        private Component message = Component.empty();
        private int messageColour = Theme.DIM;
        private boolean refocus;
        private Button go;

        Import(Consumer<Palette> done) {
            super(tr("palettes.chromastudio.import.title"), 280, 88);
            this.done = done;
        }

        @Override
        protected void build(Bounds in) {
            boolean first = field == null;
            field = widgets.search(in.x(), in.y() + LABEL_H, in.w(), tr("palettes.chromastudio.import.hint"), _ -> {
                String s = field.getValue();
                boolean pasted = s.length() > text.length() + 1;
                text = s;
                if (pasted) field.moveCursorToStart(false);
                preview();
            });
            field.setMaxLength(Short.MAX_VALUE);
            if (first) focusField();
            Component browse = tr("palettes.chromastudio.import.browse"), paste = tr("palettes.chromastudio.import.paste"), action = tr("palettes.chromastudio.import");
            int y = field.getY() + PanelTab.ROW;
            Button chooser = widgets.button(in.x(), y, browse, () -> {
                FileDialog.choose("JSON", "json", path -> field.setValue(path.toString()), () -> say(tr("palettes.chromastudio.no_dialog"), Theme.FAIR));
                refocus = true;
            });
            widgets.button(chooser.getRight() + PanelTab.GAP, y, paste, () -> {
                field.setValue(Minecraft.getInstance().keyboardHandler.getClipboard().strip());
                field.moveCursorToStart(false);
                refocus = true;
            });
            go = widgets.button(in.right() - Widgets.buttonWidth(action), y, action, this::confirm);
            field.setValue(text);
        }

        private void focusField() {
            field.setFocused(true);
            widgets.setFocused(field);
        }

        @Override
        void tick() {
            if (refocus) focusField();
            refocus = false;
        }

        private void say(Component text, int colour) {
            message = text;
            messageColour = colour;
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            g.text(PanelTab.font(), PanelTab.fit(tr("palettes.chromastudio.import.label").getString(), in.w()), in.x(), in.y(), Theme.SOFT, false);
            String mark = messageColour == Theme.GOOD ? "\u2714 " : messageColour == Theme.BAD ? "\u2716 " : messageColour == Theme.FAIR ? "! " : "";
            if (!mark.isEmpty()) g.outline(field.getX() - 1, field.getY() - 1, field.getWidth() + 2, field.getHeight() + 2, messageColour);
            if (go.active) g.outline(go.getX() - 1, go.getY() - 1, go.getWidth() + 2, go.getHeight() + 2, shared != null ? Theme.GOOD : Theme.SOFT);
            int messageY = go.getBottom() + 4;
            List<FormattedCharSequence> lines = PanelTab.font().split(Component.literal(mark).append(message), in.w());
            for (int i = 0; i < Math.min(STATUS_LINES, lines.size()); i++) g.text(PanelTab.font(), lines.get(i), in.x(), messageY + i * 9, messageColour, false);
            if (lines.size() > STATUS_LINES && mouseY >= messageY && mouseY < messageY + STATUS_LINES * 9 && mouseX >= in.x() && mouseX < in.right())
                g.setTooltipForNextFrame(PanelTab.font(), Tooltip.splitTooltip(Minecraft.getInstance(), message), mouseX, mouseY);
        }

        private static boolean looksLikePath(String s) {
            return s.contains("/") || s.contains("\\") || s.toLowerCase(Locale.ROOT).endsWith(".json");
        }

        private static String foundPrefix(String s) {
            int colon = s.indexOf(':');
            return colon > 0 && colon < ShareString.PREFIX.length() + 4 ? s.substring(0, colon + 1) : s.substring(0, Math.min(s.length(), ShareString.PREFIX.length()));
        }

        private void preview() {
            String s = text.strip();
            shared = null;
            problem = null;
            if (s.isEmpty()) say(tr("palettes.chromastudio.import.help"), Theme.DIM);
            else if (s.startsWith("chroma")) {
                try {
                    shared = ShareString.decode(s);
                    int n = shared.entries.size();
                    say(tr(Plural.key("palettes.chromastudio.import.ready", n), PaletteStore.get().unique(shared.name), n), Theme.GOOD);
                } catch (UnreadablePalette e) {
                    problem = e.reason() == UnreadablePalette.Reason.NOT_SHARE
                            ? tr("palettes.chromastudio.import.prefix", foundPrefix(s), ShareString.PREFIX)
                            : tr("palettes.chromastudio.import.bad", PaletteStore.why(e));
                    say(problem, Theme.BAD);
                }
            } else if (looksLikePath(s)) {
                say(tr("palettes.chromastudio.import.file"), Theme.DIM);
            } else {
                problem = tr("palettes.chromastudio.import.unknown", ShareString.PREFIX);
                say(problem, Theme.FAIR);
            }
            Component why = s.isEmpty() ? tr("palettes.chromastudio.import.empty") : problem;
            go.active = why == null;
            go.setTooltip(why == null ? null : Tooltip.create(why));
        }

        @Override
        protected boolean confirm() {
            String s = text.strip();
            if (s.isEmpty()) {
                say(tr("palettes.chromastudio.import.empty"), Theme.FAIR);
                return true;
            }
            if (problem != null) {
                say(problem, Theme.BAD);
                return true;
            }
            try {
                Palette p = shared != null ? PaletteStore.get().importShare(s) : PaletteStore.get().importJson(Files.readString(Path.of(s)));
                close();
                done.accept(p);
            } catch (UnreadablePalette e) {
                say(tr("palettes.chromastudio.import.bad", PaletteStore.why(e)), Theme.BAD);
            } catch (InvalidPathException _) {
                say(tr("palettes.chromastudio.import.unknown", ShareString.PREFIX), Theme.BAD);
            } catch (IOException _) {
                say(tr("palettes.chromastudio.import.no_file", s), Theme.BAD);
            }
            return true;
        }
    }

    static final class Generate extends Dialog {
        static final int WIDTH = 250, HEIGHT = TITLE_H + 3 + 3 * PanelTab.ROW + 84 + PanelTab.ROW + 6;
        private static final int CHIP = 4;
        private static final double GREY_CHROMA = 0.03;
        private static Harmony harmony = Harmony.ANALOGOUS;

        private record Key(String hex, Harmony harmony, int count, long salt, List<Object> pool) {}

        private final Consumer<Palette> done;
        private String hex;
        private int count = ModSettings.generateCount;
        private long salt = new Random().nextLong();
        private int labelW;
        private Key shownFor;
        private List<PaletteEntry> shown = List.of();
        private Button go;

        Generate(Color seed, Consumer<Palette> done) {
            super(tr("palettes.chromastudio.generate.title"), WIDTH, HEIGHT);
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

        private List<PaletteEntry> preview() {
            Color seed = seed();
            Key key = new Key(hex, harmony, count, salt, poolKey());
            if (!key.equals(shownFor)) {
                shownFor = key;
                shown = new ArrayList<>(seed == null ? List.of() : closest(Generator.targets(seed.oklch(), harmony, count, new Random(salt))));
                shown.sort(Comparator.comparingDouble(Generate::hue));
            }
            return shown;
        }

        private static double hue(PaletteEntry e) {
            Color c = PaletteBlocks.colour(e);
            if (c == null) return -2;
            Oklch o = c.oklch();
            return o.c() < GREY_CHROMA ? o.l() - 1 : o.h();
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.generate.seed", "palettes.chromastudio.generate.harmony", "palettes.chromastudio.generate.count");
            int x = in.x() + labelW, y = in.y();
            Component hand = tr("palettes.chromastudio.generate.in_hand"), random = tr("palettes.chromastudio.generate.random");
            int handW = Widgets.buttonWidth(hand), randomW = Widgets.buttonWidth(random);
            EditBox field = widgets.field(x + Widgets.H + 2, y, Math.max(40, in.right() - x - Widgets.H - 2 * PanelTab.GAP - 2 - handW - randomW), hex, s -> hex = s);
            field.setMaxLength(7);
            widgets.button(in.right() - randomW, y, random, () -> {
                Random r = new Random();
                harmony = Harmony.values()[r.nextInt(Harmony.values().length)];
                hex = Color.ofOklch(new Oklch(0.35 + r.nextDouble() * 0.45, 0.04 + r.nextDouble() * 0.14, r.nextDouble() * 360)).hex();
                salt = r.nextLong();
                refresh();
            });
            Button handButton = widgets.button(in.right() - randomW - PanelTab.GAP - handW, y, hand, () -> {
                Color c = inHand();
                if (c != null) field.setValue(c.hex());
            });
            if (inHand() == null) Widgets.disable(handButton, tr("palettes.chromastudio.generate.in_hand.none"));
            y += PanelTab.ROW;
            widgets.cycler(x, y, in.right() - x, null, List.of(Harmony.values()), harmony, PaletteDialogs::option, h -> harmony = h);
            y += PanelTab.ROW;
            widgets.spin(x, y, 40, count, 1, ModSettings.min("generateCount"), ModSettings.max("generateCount"), true, v -> count = (int) v);
            Component go = tr("palettes.chromastudio.generate.go");
            int goW = Widgets.buttonWidth(go);
            this.go = widgets.button(in.right() - goW, in.bottom() - Widgets.H, goW, go, this::confirm);
            int cancelW = Widgets.buttonWidth(CommonComponents.GUI_CANCEL);
            widgets.button(in.right() - goW - PanelTab.GAP - 2 - cancelW, in.bottom() - Widgets.H, cancelW, CommonComponents.GUI_CANCEL, this::close);
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
            g.text(PanelTab.font(), tr("widget.chromastudio.range", Widgets.number(ModSettings.min("generateCount"), true), Widgets.number(ModSettings.max("generateCount"), true)),
                    x + 44, in.y() + 2 * PanelTab.ROW + 4, Theme.DIM, false);
            Bounds well = new Bounds(in.x(), in.y() + 3 * PanelTab.ROW, in.w(), in.h() - 4 * PanelTab.ROW - 2);
            g.fill(well.x(), well.y(), well.right(), well.bottom(), Theme.WELL);
            List<PaletteEntry> entries = preview();
            Component problem = seed == null ? tr("palettes.chromastudio.generate.bad_hex") : entries.isEmpty() ? tr("palettes.chromastudio.generate.none") : null;
            Component footer = entries.size() < count ? tr("palettes.chromastudio.count", entries.size()) : Component.empty();
            int footerColour = Theme.DIM;
            if (problem != null) {
                if (go.active) Widgets.disable(go, problem);
                List<FormattedCharSequence> lines = PanelTab.font().split(problem, well.w() - 8);
                int top = well.y() + (well.h() - lines.size() * 9) / 2;
                for (int i = 0; i < lines.size(); i++)
                    g.text(PanelTab.font(), lines.get(i), well.x() + (well.w() - PanelTab.font().width(lines.get(i))) / 2, top + i * 9, Theme.BAD, false);
                return;
            }
            if (!go.active) {
                go.active = true;
                go.setTooltip(null);
            }
            int n = entries.size(), icon = 32, pad = 2, gap = 2;
            if (fits(n, icon, pad, gap, well) == 0) {
                icon = 16;
                if (fits(n, icon, pad, gap, well) == 0) {
                    pad = 1;
                    gap = 1;
                }
            }
            int tileW = icon + 2 * pad, tileH = tileW + CHIP, pitchX = tileW + gap, pitchY = tileH + gap;
            int rows = Math.max(fits(n, icon, pad, gap, well), icon == 32 && n >= 4 ? 2 : 1), cols = (n + rows - 1) / rows;
            int left = well.x() + (well.w() - cols * pitchX + gap) / 2, top = well.y() + (well.h() - rows * pitchY + gap) / 2;
            for (int i = 0; i < n; i++) {
                int tx = left + (i % cols) * pitchX, ty = top + (i / cols) * pitchY;
                PaletteEntry e = entries.get(i);
                boolean over = mouseX >= tx && mouseX < tx + tileW && mouseY >= ty && mouseY < ty + tileH;
                g.fill(tx, ty, tx + tileW, ty + tileH, over ? Theme.HOVER : Theme.BAR);
                Color colour = PaletteBlocks.colour(e);
                if (colour != null) g.fill(tx, ty + tileW, tx + tileW, ty + tileH, 0xFF000000 | colour.rgb());
                if (over) {
                    g.outline(tx, ty, tileW, tileH, Theme.TEXT);
                    footer = PaletteBlocks.name(e);
                    footerColour = Theme.TEXT;
                }
                g.pose().pushMatrix();
                g.pose().translate(tx + pad, ty + pad);
                g.pose().scale(icon / 16f);
                Widgets.blockIcon(g, PaletteBlocks.icon(e), 0, 0);
                g.pose().popMatrix();
            }
            g.outline(go.getX() - 1, go.getY() - 1, go.getWidth() + 2, go.getHeight() + 2, Theme.ACCENT);
            int cancelW = Widgets.buttonWidth(CommonComponents.GUI_CANCEL);
            g.text(PanelTab.font(), PanelTab.fit(footer.getString(), in.w() - go.getWidth() - cancelW - 8), in.x(), in.bottom() - Widgets.H + 4, footerColour, false);
        }

        private static int fits(int n, int icon, int pad, int gap, Bounds well) {
            int tileW = icon + 2 * pad, cols = (well.w() + gap) / (tileW + gap), rows = (n + cols - 1) / cols;
            return rows * (tileW + CHIP + gap) - gap <= well.h() ? rows : 0;
        }

        @Override
        protected boolean confirm() {
            Color seed = seed();
            List<PaletteEntry> entries = preview();
            if (seed == null || entries.isEmpty()) return true;
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

        static final int WIDTH = 300, SCOPE_LINES = 1, SIZE_LINES = 2, NEXT_LINES = 3, RECEIPT_LINES = 2, RULE = 7, CALLOUT = 6, BULLET = 8;

        private static Script script = Script.RECOLOUR;
        private static AxiomScript.Axis axis = AxiomScript.Axis.Y;
        private static Recolour.Mode match = Recolour.Mode.SHADING;
        private static String fromKey = "";

        private final Palette palette;
        private final String gradientName;
        private final List<PaletteEntry> steps;
        private AxiomBridge.Script made;
        private Component problem;
        private final int body;
        private boolean copied;
        private Lines lines;
        private Widgets.ScrollList detailList;

        Axiom(Palette palette) { this(palette, null, null, body(palette, null, null)); }

        Axiom(String gradientName, List<PaletteEntry> steps) { this(null, gradientName, List.copyOf(steps), body(null, gradientName, steps)); }

        private Axiom(@Nullable Palette palette, @Nullable String gradientName, @Nullable List<PaletteEntry> steps, int body) {
            super(tr("axiom.chromastudio.title"), WIDTH, height(palette == null ? 1 : 3, body));
            this.palette = palette;
            this.gradientName = gradientName;
            this.steps = steps;
            this.body = body;
        }

        private static int body(@Nullable Palette palette, @Nullable String gradientName, @Nullable List<PaletteEntry> steps) {
            if (palette == null) return bodyLines(gradient(gradientName, steps));
            return Math.max(bodyLines(script(Script.RECOLOUR, palette)), bodyLines(script(Script.SCATTER, palette)));
        }

        private static int height(int rows, int body) {
            return TITLE_H + 7 + rows * PanelTab.ROW + PanelTab.GAP + body + PanelTab.GAP + Widgets.H;
        }

        private static int bodyLines(Made m) {
            Lines l = lines(PanelTab.font().getSplitter(), WIDTH - 10, Integer.MAX_VALUE / 2, m.script(), m.problem());
            return l.top() + l.details().size() * 9;
        }

        private record Made(AxiomBridge.@Nullable Script script, @Nullable Component problem) {}

        private static Made script(Script kind, Palette palette) {
            try {
                return new Made(kind == Script.SCATTER ? AxiomBridge.scatterScript(palette) : AxiomBridge.recolourScript(palette, from(palette), match), null);
            } catch (AxiomBridge.Refused r) {
                return new Made(null, r.reason());
            }
        }

        private static Made gradient(String name, List<PaletteEntry> steps) {
            try {
                var player = Minecraft.getInstance().player;
                return new Made(AxiomBridge.gradientScript(name, steps, axis, player == null ? BlockPos.ZERO : player.blockPosition()), null);
            } catch (AxiomBridge.Refused r) {
                return new Made(null, r.reason());
            }
        }

        record Detail(FormattedText text, boolean first) {}

        record Lines(Fitted scope, Fitted size, Fitted next, List<Detail> details, Fitted problem) {
            int top() { return problem.height() + scope.height() + size.height() + next.height() + (details.isEmpty() ? 0 : RULE); }
        }

        static Lines lines(StringSplitter splitter, int width, int room, AxiomBridge.@Nullable Script made, @Nullable Component problem) {
            Fitted none = new Fitted(List.of(), false);
            if (made == null) return new Lines(none, none, none, List.of(), fit(splitter, problem, width, Math.max(1, room / 9)));
            Fitted scope = fit(splitter, made.scope(), width, SCOPE_LINES), size = fit(splitter, made.size(), width - CALLOUT, SIZE_LINES);
            Fitted next = fit(splitter, made.nextStep(), width - CALLOUT, NEXT_LINES);
            List<Detail> details = new ArrayList<>();
            for (Component d : made.details()) {
                List<FormattedText> split = splitter.splitLines(d, width - BULLET - Widgets.BAR_W - 2, Style.EMPTY);
                for (FormattedText line : split) details.add(new Detail(line, line == split.getFirst()));
            }
            return new Lines(scope, size, next, details, none);
        }

        private void make() {
            Made m = palette == null ? gradient(gradientName, steps) : script(script, palette);
            made = m.script();
            problem = m.problem();
        }

        private void changed() {
            copied = false;
            int now = body(palette, gradientName, steps);
            if (now == body) refresh();
            else tab.open(new Axiom(palette, gradientName, steps, now));
        }

        private static @Nullable Palette from(Palette palette) {
            for (Palette p : PaletteStore.get().all()) if (p != palette && key(p).equals(fromKey)) return p;
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

        private int bodyTop(Bounds in) { return in.y() + rows() * PanelTab.ROW + PanelTab.GAP; }

        @Override
        protected void build(Bounds in) {
            make();
            lines = lines(PanelTab.font().getSplitter(), in.w(), footerY(in) - PanelTab.GAP - bodyTop(in), made, problem);
            int detailsY = bodyTop(in) + lines.top();
            detailList = new Widgets.ScrollList(new Bounds(in.x(), detailsY, in.w(), Math.max(0, footerY(in) - PanelTab.GAP - detailsY)), 9, () -> lines.details().size());
            controls(in);
            Component copyLabel = tr("palettes.chromastudio.copy");
            Button copy = widgets.button(in.right() - Widgets.buttonWidth(copyLabel), footerY(in), copyLabel, () -> {
                AxiomBridge.copy(made);
                copied = true;
            });
            if (made == null) Widgets.disable(copy, problem);
        }

        private void controls(Bounds in) {
            int x, y = in.y();
            if (palette == null) {
                x = in.x() + labelWidth("axiom.chromastudio.axis");
                widgets.cycler(x, y, 40, null, List.of(AxiomScript.Axis.values()), axis, a -> Component.literal(a.name()),
                        a -> { axis = a; changed(); }, tr("axiom.chromastudio.axis.tip"));
                return;
            }
            x = in.x() + labelWidth("axiom.chromastudio.script", "axiom.chromastudio.from", "axiom.chromastudio.match");
            int w = in.right() - x;
            widgets.cycler(x, y, w, null, List.of(Script.values()), script, s -> tr("axiom.chromastudio.script." + s.name().toLowerCase(Locale.ROOT)),
                    s -> { script = s; changed(); }, tr("axiom.chromastudio.script.tip"));
            y += PanelTab.ROW;
            Palette from = Objects.requireNonNullElse(from(palette), palette);
            Function<Palette, String> name = p -> p == palette ? tr("axiom.chromastudio.from.any").getString() : AxiomBridge.fromName(p);
            boolean fitted = Export.middle(name.apply(from), w - 10).equals(name.apply(from));
            var fromButton = widgets.cycler(x, y, w, null, fromChoices(), from, p -> Component.literal(Export.middle(name.apply(p), w - 10)),
                    p -> { fromKey = p == palette ? "" : key(p); changed(); },
                    fitted ? tr("axiom.chromastudio.from.tip") : screenTip(name.apply(from)));
            y += PanelTab.ROW;
            var matchButton = widgets.cycler(x, y, w, null, List.of(Recolour.Mode.values()), match,
                    m -> tr("axiom.chromastudio.match." + m.name().toLowerCase(Locale.ROOT)), m -> { match = m; changed(); }, tr("axiom.chromastudio.match.tip"));
            if (script != Script.RECOLOUR) {
                Widgets.disable(fromButton, tr("axiom.chromastudio.script.recolour_only"));
                Widgets.disable(matchButton, tr("axiom.chromastudio.script.recolour_only"));
            }
        }

        private static int footerY(Bounds in) { return in.bottom() - Widgets.H; }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            if (palette == null) label(g, "axiom.chromastudio.axis", in.x(), in.y());
            else {
                label(g, "axiom.chromastudio.script", in.x(), in.y());
                label(g, "axiom.chromastudio.from", in.x(), in.y() + PanelTab.ROW);
                label(g, "axiom.chromastudio.match", in.x(), in.y() + 2 * PanelTab.ROW);
            }
            if (copied) {
                Fitted receipt = fit(PanelTab.font().getSplitter(), AxiomBridge.named("axiom.chromastudio.copied"),
                        in.w() - Widgets.buttonWidth(tr("palettes.chromastudio.copy")) - PanelTab.GAP, RECEIPT_LINES);
                drawLines(g, receipt, in.x(), footerY(in) + (Widgets.H - receipt.height()) / 2 + 1, Theme.GOOD);
            }
            int top = bodyTop(in);
            if (made == null) {
                cutTip(g, lines.problem(), List.of(problem), in.x(), top, in.w(), drawLines(g, lines.problem(), in.x(), top, Theme.FAIR), mouseX, mouseY);
                return;
            }
            int y = drawLines(g, lines.scope(), in.x(), top, Theme.TEXT);
            cutTip(g, lines.scope(), List.of(made.scope()), in.x(), top, in.w(), y, mouseX, mouseY);
            if (made.warning() == null) y = drawLines(g, lines.size(), in.x(), y, Theme.DIM);
            else {
                int calloutY = y;
                y = drawLines(g, lines.size(), in.x() + CALLOUT, y, Theme.FAIR);
                y = drawLines(g, lines.next(), in.x() + CALLOUT, y, Theme.TEXT);
                g.fill(in.x(), calloutY, in.x() + 2, y - 1, Theme.FAIR);
            }
            if (lines.details().isEmpty()) return;
            g.horizontalLine(in.x(), in.right() - 1, y + 3, Theme.BORDER);
            detailList.render(g, mouseX, mouseY, (gg, i, x, rowY, _, _, _) -> {
                Detail d = lines.details().get(i);
                if (d.first()) gg.fill(x + 2, rowY + 3, x + 4, rowY + 5, Theme.DIM);
                gg.text(PanelTab.font(), Language.getInstance().getVisualOrder(d.text()), x + BULLET, rowY, Theme.DIM, false);
            });
        }

        @Override
        boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
            return detailList != null && detailList.mouseClicked(e) || super.mouseClicked(e, doubled);
        }

        @Override
        boolean mouseScrolled(double x, double y, double h, double v) {
            return detailList != null && detailList.mouseScrolled(x, y, v) || super.mouseScrolled(x, y, h, v);
        }

        @Override
        boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
            return detailList != null && detailList.mouseDragged(e) || super.mouseDragged(e, dx, dy);
        }

        @Override
        boolean mouseReleased(MouseButtonEvent e) {
            return detailList != null && detailList.mouseReleased() || super.mouseReleased(e);
        }

        private static void cutTip(GuiGraphicsExtractor g, Fitted shown, List<Component> full, int x, int top, int w, int bottom, int mouseX, int mouseY) {
            if (shown.cut() && over(mouseX, mouseY, x, top, w, bottom - top)) tip(g, full, mouseX, mouseY);
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
            Object[] args = Arrays.copyOf(before, before.length + 1);
            args[before.length] = n;
            return tr(Plural.key(key, n), args);
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
            int x = in.x(), y = in.y() + HEADING, w = in.w();
            if (AxiomBridge.isPresent()) {
                w = (in.w() - PanelTab.GAP) / 2;
                CycleButton<Boolean> axiomButton = widgets.toggle(x, y, w, tr("palettes.chromastudio.hotbar.axiom"), HotbarFill.usesAxiom(), v -> {
                    ModSettings.hotbarAxiom = v;
                    ModSettings.save();
                    result = null;
                    refresh();
                }, tr("palettes.chromastudio.hotbar.axiom.tip"));
                Optional<Component> noAxiom = AxiomBridge.hotbarsUnavailable();
                if (noAxiom.isPresent()) Widgets.disable(axiomButton, noAxiom.get());
                else if (fits != null) Widgets.disable(axiomButton, fits);
                x += w + PanelTab.GAP;
            }
            List<OverflowMode> modes = List.of(OverflowMode.values());
            Function<OverflowMode, Component> names = m -> tr("option.chromastudio.overflowmode." + m.name().toLowerCase(Locale.ROOT));
            CycleButton<OverflowMode> modeButton = widgets.cycler(x, y, w, tr("palettes.chromastudio.hotbar.boxes"), modes,
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
