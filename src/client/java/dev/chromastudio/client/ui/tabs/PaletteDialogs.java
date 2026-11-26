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
import dev.chromastudio.client.index.TintContext;
import dev.chromastudio.client.ui.Bounds;
import dev.chromastudio.client.ui.Theme;
import dev.chromastudio.client.ui.Widgets;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Harmony;
import dev.chromastudio.core.color.Oklch;
import dev.chromastudio.core.export.Arrangement;
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
import java.nio.file.NoSuchFileException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import net.minecraft.world.item.Items;
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
        Minecraft mc = Minecraft.getInstance();
        Optional<BlockPos> tintPosition = PaletteBlocks.tint() == TintContext.HERE && mc.player != null
                ? Optional.of(mc.player.blockPosition())
                : Optional.empty();
        return List.of(MatchState.filters().state(), BlockIndex.get().version(), MatchState.revision(),
                PaletteBlocks.scale(), tintPosition);
    }

    static List<PaletteEntry> closest(List<Oklch> targets) {
        BlockIndex index = BlockIndex.get();
        MatchFilters filters = MatchState.filters();
        Set<Entry> pool = new HashSet<>(filters.candidates(index, PaletteBlocks.face(), PaletteBlocks.scale(), PaletteBlocks.tint()));
        Query q = PaletteBlocks.query().limit(Integer.MAX_VALUE);
        filters.apply(q);
        q.statesSeparately = true;
        List<PaletteEntry> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Oklch target : targets) {
            if (used.size() == pool.size()) break;
            for (BlockIndex.Match m : index.closest(Target.of(Color.ofOklch(target)), q)) {
                Entry entry = m.entry();
                if (!pool.contains(entry) || !used.add(entry.id())) continue;
                out.add(PaletteBlocks.of(entry));
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
        private @Nullable Component goRefused;
        private @Nullable Key drawn;

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
            goRefused = null;
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
            drawn = shownFor;
            Component problem = seed == null ? tr("palettes.chromastudio.generate.bad_hex") : entries.isEmpty() ? tr("palettes.chromastudio.generate.none") : null;
            Component footer = entries.size() < count ? tr("palettes.chromastudio.count", entries.size()) : Component.empty();
            int footerColour = Theme.DIM;
            if (problem != null) {
                if (!problem.equals(goRefused)) {
                    goRefused = problem;
                    Widgets.disable(go, problem);
                }
                List<FormattedCharSequence> lines = PanelTab.font().split(problem, well.w() - 8);
                int top = well.y() + (well.h() - lines.size() * 9) / 2;
                for (int i = 0; i < lines.size(); i++)
                    g.text(PanelTab.font(), lines.get(i), well.x() + (well.w() - PanelTab.font().width(lines.get(i))) / 2, top + i * 9, Theme.BAD, false);
                return;
            }
            if (goRefused != null) {
                goRefused = null;
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
                ItemStack stack = PaletteBlocks.icon(e);
                Entry entry = stack.isEmpty() ? PaletteBlocks.entry(e) : null;
                if (entry != null) Thumbnails.draw(g, entry, PaletteBlocks.face(), PaletteBlocks.scale(), PaletteBlocks.tint(), 0, 0, 16, 1f);
                else Widgets.blockIcon(g, stack, 0, 0);
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
            if (seed == null || entries.isEmpty() || !shownFor.equals(drawn)) return true;
            close();
            done.accept(PaletteStore.get().create(tr("palettes.chromastudio.generate.name", option(harmony), seed.hex()).getString(), entries));
            return true;
        }
    }

    static final class Image extends Dialog {
        private static final int DEFAULT_COUNT = 6, MIN_COUNT = 2, MAX_COUNT = 32, DEBOUNCE_TICKS = 8, THUMB_W = 80, THUMB_H = 60, LIST_ROW = 17,
                PREVIEW_H = 134, STRIP_H = 8;
        private static final ExecutorService READER = Executors.newSingleThreadExecutor(task -> {
            Thread t = new Thread(task, "Chroma Studio image");
            t.setDaemon(true);
            return t;
        });

        private enum State { EMPTY, NO_DIALOG, READING, MISSING, UNREADABLE, TRANSPARENT, NO_BLOCKS, READY }

        private record Outcome(ImageImport.@Nullable Sample sample, List<Color> colours, State failure) {}

        private final Consumer<Palette> done;
        private String path = "", name = "";
        private boolean named;
        private int count = DEFAULT_COUNT;
        private EditBox field, nameField, sizeField;
        private Button create;
        private int labelW;
        private int wait = -1, generation;
        private boolean reading;
        private State state = State.EMPTY;
        private ImageImport.@Nullable Sample sample;
        private String samplePath = "";
        private List<Color> colours = List.of();
        private List<PaletteEntry> entries = List.of();
        private int matches, drawn;
        private List<Object> matchedWith = List.of();
        private final Widgets.ScrollList list = new Widgets.ScrollList(new Bounds(0, 0, 0, 0), LIST_ROW, () -> entries.size());

        Image(Consumer<Palette> done) {
            super(tr("palettes.chromastudio.image.title"), 340, TITLE_H + 7 + 3 * PanelTab.ROW + PREVIEW_H);
            this.done = done;
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.image.file", "palettes.chromastudio.image.count", "palettes.chromastudio.image.name");
            Component browse = tr("palettes.chromastudio.browse"), go = tr("palettes.chromastudio.image.go");
            int browseW = Widgets.buttonWidth(browse), x = in.x() + labelW;
            boolean first = field == null;
            field = widgets.search(x, in.y(), in.right() - x - browseW - PanelTab.GAP, tr("palettes.chromastudio.image.hint"), this::edited);
            field.setMaxLength(1024);
            field.setValue(path);
            if (first) {
                field.setFocused(true);
                widgets.setFocused(field);
            }
            widgets.button(in.right() - browseW, in.y(), browse, () -> ImageImport.choose(file -> {
                samplePath = "";
                generation++;
                field.setValue(file.toString());
                widgets.setFocused(field);
                wait = 0;
            }, () -> {
                if (path.isBlank()) state = State.NO_DIALOG;
            }));
            int y = in.y() + PanelTab.ROW, bottom = in.bottom() - Widgets.H;
            sizeField = widgets.spin(x, y, 40, count, 1, MIN_COUNT, MAX_COUNT, true, v -> counted((int) v));
            int goW = Widgets.buttonWidth(go), cancelW = Widgets.buttonWidth(CommonComponents.GUI_CANCEL);
            create = widgets.button(in.right() - goW, bottom, go, this::confirm);
            widgets.button(in.right() - goW - PanelTab.GAP - cancelW, bottom, CommonComponents.GUI_CANCEL, this::close);
            nameField = widgets.search(x, bottom, in.right() - goW - cancelW - 2 * PanelTab.GAP - x - PanelTab.GAP, tr("palettes.chromastudio.name_hint"), s -> {
                if (s.equals(name)) return;
                name = s;
                named = !s.isBlank();
            });
            nameField.setMaxLength(Integer.MAX_VALUE);
            nameField.setValue(name);
            showCreate();
        }

        private void edited(String s) {
            if (s.equals(path)) return;
            path = s;
            generation++;
            wait = s.isBlank() ? -1 : DEBOUNCE_TICKS;
            if (s.isBlank()) state = State.EMPTY;
            showCreate();
        }

        private void counted(int value) {
            if (value == count) return;
            count = value;
            if (state != State.EMPTY && state != State.NO_DIALOG && wait < 0) read();
        }

        private void showPath() {
            field.setTextColor(state == State.MISSING || state == State.UNREADABLE ? Theme.FIELD_BAD : EditBox.DEFAULT_TEXT_COLOR);
        }

        private void showCreate() {
            if (field != null) showPath();
            if (create == null) return;
            boolean sized = sizeShown();
            create.active = state == State.READY && wait < 0 && !reading && sized;
            String why = state == State.NO_BLOCKS ? "palettes.chromastudio.image.no_blocks"
                    : state == State.READY && !sized ? "palettes.chromastudio.image.size.need" : "palettes.chromastudio.image.go.need";
            create.setTooltip(create.active ? null : Tooltip.create(tr(why, MIN_COUNT, MAX_COUNT)));
        }

        private boolean sizeShown() {
            try {
                return sizeField == null || Integer.parseInt(sizeField.getValue().strip()) == count;
            } catch (NumberFormatException _) {
                return false;
            }
        }

        @Override
        void tick() {
            if (wait > 0) wait--;
            else if (wait == 0) {
                if (state != State.READY) state = State.READING;
                wait = -1;
                read();
            } else if (sample != null && (state == State.READY || state == State.NO_BLOCKS) && !matchedWith.equals(poolKey())) match();
            if (create != null && create.active != (state == State.READY && wait < 0 && !reading && sizeShown())) showCreate();
        }

        private void read() {
            String s = path.strip();
            int gen = ++generation, k = count;
            ImageImport.Sample known = s.equals(samplePath) ? sample : null;
            if (known == null) state = State.READING;
            reading = true;
            showCreate();
            READER.execute(() -> {
                Outcome out;
                try {
                    ImageImport.Sample got = known != null ? known : ImageImport.read(Path.of(s), THUMB_W, THUMB_H);
                    List<Color> found = ImageImport.colours(got, k);
                    out = new Outcome(got, found, found.isEmpty() ? State.TRANSPARENT : State.READY);
                } catch (NoSuchFileException | InvalidPathException _) {
                    out = new Outcome(null, List.of(), State.MISSING);
                } catch (IOException | RuntimeException _) {
                    out = new Outcome(null, List.of(), State.UNREADABLE);
                }
                Outcome result = out;
                Minecraft.getInstance().execute(() -> finish(gen, s, result));
            });
        }

        private void finish(int gen, String s, Outcome out) {
            if (gen != generation) return;
            reading = false;
            sample = out.sample();
            samplePath = sample == null ? "" : s;
            colours = out.colours();
            entries = List.of();
            state = out.failure();
            if (state == State.READY) match();
            if (sample != null && !named && nameField != null) {
                nameField.setValue(stem());
                named = false;
            }
            showCreate();
        }

        private void match() {
            matchedWith = poolKey();
            List<Oklch> targets = new ArrayList<>();
            for (Color c : colours) targets.add(c.oklch());
            entries = closest(targets);
            matches++;
            state = entries.isEmpty() ? State.NO_BLOCKS : State.READY;
            showCreate();
        }

        private String stem() {
            String file = Path.of(samplePath).getFileName().toString();
            int dot = file.lastIndexOf('.');
            return dot > 0 ? file.substring(0, dot) : file;
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            label(g, "palettes.chromastudio.image.file", in.x(), in.y());
            label(g, "palettes.chromastudio.image.count", in.x(), in.y() + PanelTab.ROW);
            label(g, "palettes.chromastudio.image.name", in.x(), in.bottom() - Widgets.H);
            g.text(PanelTab.font(), tr("palettes.chromastudio.image.range", MIN_COUNT, MAX_COUNT), in.x() + labelW + 44, in.y() + PanelTab.ROW + 4, Theme.DIM, false);
            MatchFilters filters = MatchState.filters();
            Component active = tr("palettes.chromastudio.image.filters", filters.besidesKind() + (filters.kind == null ? 0 : 1));
            int fx = in.right() - Widgets.tw(active), fy = in.y() + PanelTab.ROW + 4;
            g.text(PanelTab.font(), active, fx, fy, Theme.DIM, false);
            if (mouseX >= fx && mouseX < in.right() && mouseY >= fy - 4 && mouseY < fy + 12)
                g.setTooltipForNextFrame(PanelTab.font(), Tooltip.splitTooltip(Minecraft.getInstance(), tr("palettes.chromastudio.image.filters.tip")), mouseX, mouseY);
            int top = in.y() + 2 * PanelTab.ROW, x = in.x(), right = in.right(), bottom = in.bottom() - PanelTab.ROW;
            g.fill(x, top, right, bottom, Theme.WELL);
            g.outline(x, top, right - x, bottom - top, Theme.BORDER);
            list.area = new Bounds(0, 0, 0, 0);
            boolean ready = state == State.READY && sample != null;
            if (!ready && (state != State.READING || sample == null)) {
                Component text = tr(switch (state) {
                    case EMPTY, READY -> "palettes.chromastudio.image.choose";
                    case NO_DIALOG -> "palettes.chromastudio.no_dialog";
                    case READING -> "palettes.chromastudio.image.reading";
                    case MISSING -> "palettes.chromastudio.image.missing";
                    case UNREADABLE -> "palettes.chromastudio.image.unreadable";
                    case TRANSPARENT -> "palettes.chromastudio.image.transparent";
                    case NO_BLOCKS -> "palettes.chromastudio.image.no_blocks";
                });
                int colour = switch (state) {
                    case NO_DIALOG -> Theme.FAIR;
                    case MISSING, UNREADABLE, TRANSPARENT, NO_BLOCKS -> Theme.BAD;
                    default -> Theme.DIM;
                };
                List<FormattedCharSequence> split = PanelTab.font().split(text, Math.min(right - x - 24, 220));
                int iconY = top + (bottom - top - 22 - split.size() * 9) / 2;
                Widgets.blockIcon(g, new ItemStack(Items.PAINTING), (x + right) / 2 - 8, iconY);
                for (int i = 0; i < split.size(); i++)
                    g.text(PanelTab.font(), split.get(i), (x + right - PanelTab.font().width(split.get(i))) / 2, iconY + 22 + i * 9, colour, false);
                return;
            }
            int tx = x + 4 + (THUMB_W - sample.thumbW()) / 2, ty = top + 4 + (THUMB_H - sample.thumbH()) / 2;
            int[] thumb = sample.thumb();
            for (int j = 0; j < sample.thumbH(); j++)
                for (int i = 0; i < sample.thumbW(); i++) g.fill(tx + i, ty + j, tx + i + 1, ty + j + 1, thumb[j * sample.thumbW() + i]);
            g.outline(tx - 1, ty - 1, sample.thumbW() + 2, sample.thumbH() + 2, Theme.BORDER);
            int infoY = top + THUMB_H + 9;
            g.text(PanelTab.font(), PanelTab.fit(Path.of(samplePath).getFileName().toString(), THUMB_W + 2), x + 4, infoY, Theme.TEXT, false);
            Component size = state == State.READING ? tr("palettes.chromastudio.image.reading") : tr("palettes.chromastudio.image.details", sample.width(), sample.height());
            g.text(PanelTab.font(), PanelTab.fit(size.getString(), THUMB_W + 2), x + 4, infoY + 11, Theme.DIM, false);
            if (!ready) return;
            drawn = matches;
            int lx = x + 4 + THUMB_W + 6, lw = right - 3 - lx, n = entries.size();
            boolean paired = n <= colours.size();
            int stripHover = mouseY >= top + 3 && mouseY < top + 5 + STRIP_H && mouseX >= lx && mouseX < lx + lw ? (mouseX - lx) * n / lw : -1;
            for (int i = 0; i < n; i++) {
                Color c = paired ? colours.get(i) : PaletteBlocks.colour(entries.get(i));
                int sx = lx + lw * i / n, ex = lx + lw * (i + 1) / n;
                if (c != null) g.fill(sx, top + 4, ex, top + 4 + STRIP_H, 0xFF000000 | c.rgb());
                if (i == stripHover) g.outline(sx, top + 4, ex - sx, STRIP_H, Theme.STRONG);
            }
            g.outline(lx - 1, top + 3, lw + 2, STRIP_H + 2, Theme.BORDER);
            list.area = new Bounds(lx, top + STRIP_H + 7, lw, bottom - top - STRIP_H - 10);
            if (stripHover >= 0) list.reveal(stripHover);
            int[] hovered = {stripHover};
            list.render(g, mouseX, mouseY, (g2, i, rx, ry, rw, _, over) -> {
                PaletteEntry e = entries.get(i);
                Color source = paired ? colours.get(i) : null, block = PaletteBlocks.colour(e);
                double distance = source == null || block == null ? -1 : source.oklab().distance(block.oklab());
                Component match = distance < 0 ? Component.empty() : tr("palettes.chromastudio.image.match." + match(distance));
                int matchW = PanelTab.font().width(match), nameW = rw - 36 - matchW - 6;
                if (over || i == stripHover) {
                    g2.fill(rx, ry, rx + rw, ry + LIST_ROW, Theme.HOVER);
                    if (over) hovered[0] = i;
                }
                g2.fill(rx + 1, ry, rx + 13, ry + 16, 0xFF000000);
                if (source != null) g2.fill(rx + 2, ry + 1, rx + 7, ry + 15, 0xFF000000 | source.rgb());
                if (block != null) g2.fill(rx + 7, ry + 1, rx + 12, ry + 15, 0xFF000000 | block.rgb());
                Widgets.blockIcon(g2, PaletteBlocks.icon(e), rx + 15, ry);
                String blockName = PaletteBlocks.name(e).getString(), shown = PanelTab.fit(blockName + (e.properties().isEmpty() ? "" : " " + e.properties()), nameW);
                String plain = shown.length() > blockName.length() ? blockName : shown;
                g2.text(PanelTab.font(), plain, rx + 34, ry + 4, Theme.TEXT, false);
                if (shown.length() > plain.length()) g2.text(PanelTab.font(), shown.substring(plain.length()), rx + 34 + PanelTab.font().width(plain), ry + 4, Theme.DIM, false);
                int quality = distance <= GradientTab.GOOD_UP_TO ? Theme.GOOD : distance <= GradientTab.FAIR_UP_TO ? Theme.FAIR : Theme.BAD;
                g2.text(PanelTab.font(), match, rx + rw - matchW - 3, ry + 4, quality, false);
            });
            List<Component> notes = new ArrayList<>();
            if (hovered[0] >= 0) {
                PaletteEntry e = entries.get(hovered[0]);
                Color source = paired ? colours.get(hovered[0]) : null, block = PaletteBlocks.colour(e);
                if (source != null) notes.add(tr("palettes.chromastudio.image.source", source.hex()));
                if (block != null) notes.add(tr("palettes.chromastudio.image.block", block.hex()));
                if (source != null && block != null)
                    notes.add(tr("palettes.chromastudio.image.delta", String.format(Locale.ROOT, "%.3f", source.oklab().distance(block.oklab()))));
            } else {
                notes.add(tr("palettes.chromastudio.count", entries.size()));
                for (String key : List.of("image", "block", "hover")) notes.add(tr("palettes.chromastudio.image.legend." + key));
            }
            for (int i = 0; i < notes.size() && infoY + 31 + i * 10 <= bottom; i++)
                g.text(PanelTab.font(), PanelTab.fit(notes.get(i).getString(), THUMB_W + 2), x + 4, infoY + 22 + i * 10, hovered[0] >= 0 ? Theme.TEXT : Theme.DIM, false);
        }

        private static String match(double distance) { return distance <= GradientTab.GOOD_UP_TO ? "close" : distance <= GradientTab.FAIR_UP_TO ? "fair" : "loose"; }

        @Override
        boolean mouseScrolled(double x, double y, double h, double v) { return list.mouseScrolled(x, y, v) || super.mouseScrolled(x, y, h, v); }

        @Override
        boolean mouseClicked(MouseButtonEvent e, boolean doubled) { return list.mouseClicked(e) || super.mouseClicked(e, doubled); }

        @Override
        boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return list.mouseDragged(e) || super.mouseDragged(e, dx, dy); }

        @Override
        boolean mouseReleased(MouseButtonEvent e) { return list.mouseReleased() || super.mouseReleased(e); }

        @Override
        protected boolean confirm() {
            if (state == State.READY && !matchedWith.equals(poolKey())) {
                match();
                return true;
            }
            if (state != State.READY || wait >= 0 || reading || drawn != matches || !sizeShown()) {
                if (wait > 0) wait = 0;
                return true;
            }
            String title = name.isBlank() ? stem() : name.strip();
            close();
            done.accept(PaletteStore.get().create(title, entries));
            return true;
        }
    }

    static final class Export extends Dialog {
        private final Palette palette;
        private Layout layout;
        private SchematicExporter.Format format = SchematicExporter.Format.LITEMATIC;
        private int height, patch = ModSettings.patchSize, width = LayoutOptions.DIORAMA_WIDTH;
        private boolean gap = ModSettings.exportGap;
        private long seed;
        private int labelW;
        private final int[] sizeX = new int[3];
        private @Nullable Component placeWhy, fullStatus;
        private @Nullable Arrangement shape;
        private int[] colours = new int[0];
        private List<PaletteEntry> kept = List.of();
        private int actionsX;
        private Bounds preview = new Bounds(0, 0, 0, 0), art = preview;
        private boolean coloured;
        private static final int SPIN_W = 40, PREVIEW_H = 44, CELL_MAX = 16;
        private static final String NOT_USED = "\u2014";
        private static final String[] SIZES = {"palettes.chromastudio.export.height", "palettes.chromastudio.export.width", "palettes.chromastudio.export.patch"};

        Export(Palette palette, Layout layout, long seed) {
            super(palette.name == null || palette.name.isBlank() ? tr("palettes.chromastudio.export.title") : tr("palettes.chromastudio.export.title.named", palette.name), 340, 173);
            this.palette = palette;
            this.layout = layout;
            this.seed = seed;
            height = layout == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT;
        }

        @Override
        protected int statusLines() { return 3; }

        @Override
        protected boolean statusFromTop() { return true; }

        @Override
        protected Component statusTip() { return fullStatus != null ? fullStatus : status; }

        private boolean diorama() { return layout == Layout.NOISE_CLUSTERS || layout == Layout.HEIGHT_GRADIENT || layout == Layout.DITHER; }

        private boolean[] sizesApply() { return new boolean[] {layout == Layout.WALL || diorama(), diorama(), layout == Layout.PATCH_GRID}; }

        private static Component layoutName(Layout l) { return tr("option.chromastudio.layoutchoice." + l.name().toLowerCase(Locale.ROOT)); }

        private void spinner(int x, int y, double value, double min, double max, boolean applies, String key, DoubleConsumer set) {
            EditBox f = widgets.spin(x, y, SPIN_W, value, 1, min, max, true, set);
            if (applies) f.setTooltip(Tooltip.create(tr(key + ".tip", (int) min, (int) max)));
            else Widgets.disable(f, tr(key + ".na")).setValue(key.equals(SIZES[2]) ? NOT_USED : tr("palettes.chromastudio.export.auto").getString());
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.export.layout", "palettes.chromastudio.export.format", SIZES[0]);
            int secondW = labelWidth(SIZES[1], "palettes.chromastudio.export.gap"), thirdW = labelWidth(SIZES[2]);
            int spare = Math.max(0, (in.w() - labelW - secondW - thirdW - 3 * SPIN_W) / 2);
            sizeX[0] = in.x();
            sizeX[1] = in.x() + labelW + SPIN_W + spare;
            sizeX[2] = sizeX[1] + secondW + SPIN_W + spare;
            int x = in.x() + labelW, y = in.y();
            widgets.cycler(x, y, in.right() - x, null, List.of(Layout.values()), layout, Export::layoutName, l -> {
                boolean wasDefault = height == (layout == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT);
                layout = l;
                if (wasDefault) height = l == Layout.WALL ? LayoutOptions.WALL_HEIGHT : LayoutOptions.DIORAMA_HEIGHT;
                refresh();
            });
            y += PanelTab.ROW;
            widgets.cycler(x, y, Math.min(70, sizeX[1] - 3 * PanelTab.GAP - x), null, List.of(SchematicExporter.Format.values()), format,
                    f -> Component.literal("." + f.extension), f -> {
                        format = f;
                        status = fullStatus = null;
                        refresh();
                    });
            AbstractWidget gapToggle = widgets.cycler(sizeX[1] + secondW, y, SPIN_W, null, List.of(true, false), gap, v -> diorama() ? Component.literal(NOT_USED) : CommonComponents.optionStatus(v), v -> { gap = v; arrange(); },
                    diorama() ? null : tr("palettes.chromastudio.export.gap.tip"));
            if (diorama()) Widgets.disable(gapToggle, tr("palettes.chromastudio.export.gap.na"));
            y += PanelTab.ROW;
            boolean[] applies = sizesApply();
            spinner(sizeX[0] + labelW, y, height, 1, 256, applies[0], SIZES[0], v -> { height = (int) v; arrange(); });
            spinner(sizeX[1] + secondW, y, width, 1, 256, applies[1], SIZES[1], v -> { width = (int) v; arrange(); });
            spinner(sizeX[2] + thirdW, y, patch, ModSettings.min("patchSize"), ModSettings.max("patchSize"), applies[2], SIZES[2], v -> { patch = (int) v; arrange(); });

            Component export = tr("palettes.chromastudio.export.go"), place = tr("palettes.chromastudio.export.place");
            int placeW = Widgets.buttonWidth(place), exportW = Widgets.buttonWidth(export);
            y += PanelTab.ROW;
            preview = art = new Bounds(in.x(), y, in.w(), PREVIEW_H);
            if (diorama()) {
                Component reroll = tr("palettes.chromastudio.diorama.reroll");
                int rerollW = Widgets.buttonWidth(reroll);
                art = new Bounds(in.x(), y, in.w() - rerollW - 2 * PanelTab.GAP, PREVIEW_H);
                widgets.button(art.right(), y + PanelTab.GAP, reroll, () -> {
                    seed = new Random().nextLong();
                    arrange();
                });
            }
            y += PREVIEW_H + PanelTab.GAP;
            Button placeButton = widgets.button(in.right() - placeW, y, place, () -> run(true));
            placeButton.setTooltip(Tooltip.create(tr("palettes.chromastudio.export.place.tip")));
            placeWhy = !LitematicaBridge.isAvailable() ? tr("palettes.chromastudio.export.place.no_litematica")
                    : format != SchematicExporter.Format.LITEMATIC ? tr("palettes.chromastudio.export.place.nbt")
                    : Minecraft.getInstance().level == null ? tr("export.chromastudio.litematica.no_world") : null;
            if (placeWhy != null) Widgets.disable(placeButton, placeWhy).setTooltip(null);
            actionsX = in.right() - placeW - PanelTab.GAP - exportW;
            widgets.button(actionsX, y, export, () -> run(false));
            arrange();
        }

        private void arrange() {
            kept = palette.entries.stream().filter(e -> SchematicExporter.stateFor(e) != null).toList();
            shape = kept.isEmpty() ? null : Arrangement.of(kept.stream().map(PaletteEntry::weight).toList(), new LayoutOptions(layout, height, patch, width, gap, seed));
            coloured = BlockIndex.get().ready();
            colours = kept.stream().mapToInt(e -> {
                Color c = PaletteBlocks.colour(e);
                return c == null ? Theme.GREYED : 0xFF000000 | c.rgb();
            }).toArray();
        }

        private int cell(int px, int py, double scale) {
            return shape.at(Math.min(shape.sizeX() - 1, (int) (px / scale)), shape.sizeY() - 1 - Math.min(shape.sizeY() - 1, (int) (py / scale)), 0);
        }

        private void drawPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            if (!coloured && BlockIndex.get().ready()) arrange();
            g.fill(preview.x(), preview.y(), preview.right(), preview.bottom(), Theme.WELL);
            if (shape == null) return;
            double fit = Math.min(CELL_MAX, Math.min((art.w() - 4.0) / shape.sizeX(), (art.h() - 4.0) / shape.sizeY()));
            double scale = fit >= 1 ? Math.floor(fit) : fit;
            int w = Math.max(1, (int) Math.round(shape.sizeX() * scale)), h = Math.max(1, (int) Math.round(shape.sizeY() * scale));
            int left = art.x() + (art.w() - w) / 2, top = art.y() + (art.h() - h) / 2;
            if (mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < top + h) {
                int cell = cell(mouseX - left, mouseY - top, scale);
                if (cell != Arrangement.AIR) tip(g, List.of(PaletteBlocks.name(kept.get(cell))), mouseX, mouseY);
            }
            for (int py = 0; py < h; py++) {
                int runStart = 0, runColour = 0;
                for (int px = 0; px <= w; px++) {
                    int colour = 0;
                    if (px < w) {
                        int cell = cell(px, py, scale);
                        colour = cell == Arrangement.AIR ? 0 : colours[cell];
                    }
                    if (px == w || colour != runColour) {
                        if (runColour != 0) g.fill(left + runStart, top + py, left + px, top + py + 1, runColour);
                        runStart = px;
                        runColour = colour;
                    }
                }
            }
        }

        @Override
        protected void draw(GuiGraphicsExtractor g, Bounds in, int mouseX, int mouseY) {
            int y = in.y();
            label(g, "palettes.chromastudio.export.layout", in.x(), y);
            y += PanelTab.ROW;
            label(g, "palettes.chromastudio.export.format", in.x(), y);
            fieldLabel(g, "palettes.chromastudio.export.gap", sizeX[1], y, !diorama());
            y += PanelTab.ROW;
            boolean[] applies = sizesApply();
            for (int i = 0; i < SIZES.length; i++) fieldLabel(g, SIZES[i], sizeX[i], y, applies[i]);
            drawPreview(g, mouseX, mouseY);
            if (shape != null) g.text(PanelTab.font(), PanelTab.fit(tr("palettes.chromastudio.export.size", shape.sizeX(), shape.sizeY()).getString(), actionsX - in.x() - PanelTab.GAP), in.x(), preview.bottom() + PanelTab.GAP + 4, Theme.SOFT, false);
            if (status == null && placeWhy != null) {
                List<FormattedCharSequence> lines = PanelTab.font().split(placeWhy, in.w());
                int top = in.bottom() - statusLines() * 9;
                for (int i = 0; i < Math.min(statusLines(), lines.size()); i++) g.text(PanelTab.font(), lines.get(i), in.x(), top + i * 9, Theme.FAIR, false);
            }
        }

        private static void fieldLabel(GuiGraphicsExtractor g, String key, int x, int y, boolean applies) {
            g.text(PanelTab.font(), tr(key), x, y + 4, applies ? Theme.DIM : Theme.GREYED, false);
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
            fullStatus = null;
            LayoutOptions options = new LayoutOptions(layout, height, patch, width, gap, seed);
            SchematicExporter.Result result;
            try {
                result = SchematicExporter.export(palette, options, format);
            } catch (SchematicExporter.NoBlocksException _) {
                fail(tr("command.chromastudio.export.empty"));
                return;
            } catch (IOException e) {
                fail(tr("command.chromastudio.export.failed", String.valueOf(e.getMessage())));
                return;
            }
            String path = shortPath(result.file()), skipped = String.join(", ", result.skippedIds());
            Component placed = null;
            if (place) {
                BlockPos origin = LitematicaBridge.originFor(ModSettings.litematicaPlacement);
                String name = palette.name == null || palette.name.isBlank() ? result.file().getFileName().toString() : palette.name;
                Optional<Component> problem = LitematicaBridge.place(result.file(), origin, name);
                placed = problem.orElseGet(() -> tr("command.chromastudio.place.done", name, origin.getX(), origin.getY(), origin.getZ()))
                        .copy().withColor(problem.isPresent() ? Theme.BAD : Theme.GOOD);
            }
            int room = box.w() - 10;
            Component shown = result(middle(path, room - Widgets.tw(tr("command.chromastudio.export.done", ""))), placed,
                    skipped.isEmpty() ? null : PanelTab.fit(tr("palettes.chromastudio.export.skipped", skipped).getString(), room));
            Component full = result(path, placed, skipped.isEmpty() ? null : PanelTab.fit(tr("palettes.chromastudio.export.skipped", skipped).getString(), 4 * room));
            status(shown, Theme.GOOD);
            if (!full.getString().equals(shown.getString())) fullStatus = full;
        }

        private Component result(String path, @Nullable Component placed, @Nullable String skipped) {
            MutableComponent done = tr("command.chromastudio.export.done", Component.literal(path).withColor(Theme.TEXT));
            if (placed != null) done.append("\n").append(placed);
            else if (placeWhy != null) done.append("\n").append(placeWhy.copy().withColor(Theme.FAIR));
            if (skipped != null) done.append("\n").append(Component.literal(skipped).withColor(Theme.FAIR));
            if (format == SchematicExporter.Format.LITEMATIC && AxiomBridge.whyNot().isEmpty())
                done.append("\n").append(AxiomBridge.named("palettes.chromastudio.export.axiom").withColor(Theme.DIM));
            return done;
        }

        private void fail(Component failure) {
            Component line = Component.literal(PanelTab.fit(failure.getString(), box.w() - 10));
            status(placeWhy == null ? line : line.copy().append("\n").append(placeWhy.copy().withColor(Theme.FAIR)), Theme.BAD);
            if (!line.getString().equals(failure.getString())) fullStatus = placeWhy == null ? failure : failure.copy().append("\n").append(placeWhy.copy().withColor(Theme.FAIR));
        }
    }

    static final class WorldEdit extends Dialog {
        private static String which = WorldEditPattern.COMMANDS.getFirst(), from = "";

        private static final int WIDTH = 320, LINE = 10, INDENT = 6;

        private final Palette palette;
        private int labelW;
        private Button copy, send;
        private List<List<WorldEditPattern.Piece>> scrolled = List.of();
        private final Widgets.ScrollList scroller = new Widgets.ScrollList(new Bounds(0, 0, 0, 0), LINE, () -> scrolled.size());
        private @Nullable Component note;
        private int noteColour;

        WorldEdit(Palette palette) {
            super(tr("palettes.chromastudio.worldedit.title"), WIDTH, height(palette));
            this.palette = palette;
        }

        private static int height(Palette palette) {
            int lines = palette.entries.isEmpty() ? 1 : wrap(WorldEditPattern.pieces("replace", palette, from.isBlank() ? "stone,andesite" : from), WIDTH - 16).size() + 1;
            return TITLE_H + 7 + 2 * PanelTab.ROW + LINE + Math.clamp(lines, 2, 8) * LINE + 6 + 2 * PanelTab.GAP + Widgets.H;
        }

        private @Nullable List<WorldEditPattern.Piece> pieces() {
            try {
                return WorldEditPattern.pieces(which, palette, from);
            } catch (IllegalArgumentException _) {
                return null;
            }
        }

        private @Nullable String command() {
            try {
                return WorldEditPattern.command(which, palette, from);
            } catch (IllegalArgumentException _) {
                return null;
            }
        }

        private void note(Component text, int colour) {
            note = text;
            noteColour = colour;
        }

        @Override
        protected void build(Bounds in) {
            labelW = labelWidth("palettes.chromastudio.worldedit.from");
            int count = WorldEditPattern.COMMANDS.size(), x = in.x();
            for (int i = 0; i < count; i++) {
                String c = WorldEditPattern.COMMANDS.get(i);
                int right = in.x() + (in.w() + PanelTab.GAP) * (i + 1) / count - PanelTab.GAP;
                Chip chip = widgets.add(new Chip(x, in.y(), Component.literal("//" + c), () -> which.equals(c), () -> {
                    if (which.equals(c)) return;
                    which = c;
                    note = null;
                    refresh();
                }));
                chip.setWidth(right - x);
                x = right + PanelTab.GAP;
            }
            Component copyLabel = tr("palettes.chromastudio.copy"), sendLabel = tr("palettes.chromastudio.worldedit.send");
            int sendW = Widgets.buttonWidth(sendLabel), copyW = Widgets.buttonWidth(copyLabel), buttonY = in.bottom() - Widgets.H;
            send = widgets.button(in.right() - sendW, buttonY, sendLabel, () -> {
                ChromaStudioClient.platform.sendChatCommand(command().substring(1));
                note(tr("palettes.chromastudio.worldedit.sent"), Theme.GOOD);
            });
            copy = widgets.button(in.right() - sendW - PanelTab.GAP - copyW, buttonY, copyLabel, () -> {
                Clipboard.copy(command()).ifPresentOrElse(why -> note(why, Theme.BAD),
                        () -> note(tr("palettes.chromastudio.worldedit.copied"), Theme.GOOD));
            });
            if (which.equals("replace")) {
                int fieldX = in.x() + labelW;
                EditBox field = widgets.search(fieldX, in.y() + PanelTab.ROW, in.right() - fieldX,
                        Component.literal(PanelTab.fit(tr("palettes.chromastudio.worldedit.from.hint").getString(), in.right() - fieldX - 8)).withColor(Theme.TIP_DIM), s -> {
                            if (!s.equals(from)) note = null;
                            from = s;
                            enable();
                        });
                field.setValue(from);
            }
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
            int fieldY = in.y() + PanelTab.ROW, fieldX = in.x() + labelW, fieldW = in.right() - fieldX;
            boolean replace = which.equals("replace");
            g.text(PanelTab.font(), tr("palettes.chromastudio.worldedit.from"), in.x(), fieldY + 4, replace ? Theme.SOFT : Theme.DIM, false);
            if (!replace) {
                Component na = tr("palettes.chromastudio.worldedit.from.na");
                g.fill(fieldX, fieldY, in.right(), fieldY + Widgets.H, 0xFF000000);
                g.outline(fieldX, fieldY, fieldW, Widgets.H, 0xFF000000 | Theme.TIP_GREYED);
                g.text(PanelTab.font(), PanelTab.fit(na.getString(), fieldW - 8), fieldX + 4, fieldY + 4, 0xFF000000 | Theme.TIP_DIM, false);
                if (over(mouseX, mouseY, fieldX, fieldY, fieldW, Widgets.H)) g.setTooltipForNextFrame(PanelTab.font(), PanelTab.font().split(na, 200), mouseX, mouseY);
            }
            int captionY = in.y() + 2 * PanelTab.ROW;
            g.text(PanelTab.font(), tr("palettes.chromastudio.worldedit.preview"), in.x(), captionY + 1, Theme.DIM, false);
            int top = captionY + LINE, bottom = in.bottom() - Widgets.H - 2 * PanelTab.GAP;
            g.fill(in.x(), top, in.right(), bottom, Theme.WELL);
            int fit = Math.max(1, (bottom - top - 4) / LINE);
            List<WorldEditPattern.Piece> pieces = pieces();
            if (pieces == null) {
                scrolled = List.of();
                List<FormattedCharSequence> lines = PanelTab.font().split(tr("palettes.chromastudio.worldedit.from.needed"), in.w() - 6);
                for (int i = 0; i < Math.min(fit, lines.size()); i++) g.text(PanelTab.font(), lines.get(i), in.x() + 3, top + 3 + i * LINE, Theme.FAIR, false);
                if (replace) g.outline(fieldX - 1, fieldY - 1, fieldW + 2, Widgets.H + 2, Theme.FIELD_BAD);
            } else {
                List<List<WorldEditPattern.Piece>> lines = wrap(pieces, in.w() - 6);
                Fitted full = lines.size() > fit ? fit(PanelTab.font().getSplitter(), Component.literal(command()), g.guiWidth() - 16, (g.guiHeight() - 12) / LINE) : null;
                if (full != null && full.cut()) {
                    scrolled = wrap(pieces, in.w() - 6 - Widgets.BAR_W - 2);
                    scroller.area = new Bounds(in.x(), top + 3, in.w(), bottom - top - 4);
                    scroller.render(g, mouseX, mouseY, (gg, i, x, y, _, _, _) -> drawLine(gg, scrolled.get(i), x + 3 + (i > 0 ? INDENT : 0), y));
                } else {
                    scrolled = List.of();
                    for (int i = 0; i < Math.min(fit, lines.size()); i++) drawLine(g, lines.get(i), in.x() + 3 + (i > 0 ? INDENT : 0), top + 3 + i * LINE);
                    if (full != null) {
                        g.text(PanelTab.font(), "...", in.right() - 12, bottom - 10, Theme.DIM, false);
                        if (over(mouseX, mouseY, in.x(), top, in.w(), bottom - top))
                            g.setTooltipForNextFrame(PanelTab.font(), full.lines().stream().map(Language.getInstance()::getVisualOrder).toList(), mouseX, mouseY);
                    }
                }
            }
            if (note != null) {
                int noteW = copy.getX() - PanelTab.GAP - in.x();
                Fitted shown = fit(PanelTab.font().getSplitter(), note, noteW, 1);
                drawLines(g, shown, in.x(), in.bottom() - Widgets.H + 4, noteColour);
                if (shown.cut() && over(mouseX, mouseY, in.x(), in.bottom() - Widgets.H, noteW, Widgets.H))
                    g.setTooltipForNextFrame(PanelTab.font(), PanelTab.font().split(note, 200), mouseX, mouseY);
            }
        }

        private static void drawLine(GuiGraphicsExtractor g, List<WorldEditPattern.Piece> line, int x, int y) {
            for (WorldEditPattern.Piece piece : line) {
                g.text(PanelTab.font(), piece.text(), x, y, colour(piece.kind()), false);
                x += PanelTab.font().width(piece.text());
            }
        }

        @Override
        boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
            return !scrolled.isEmpty() && scroller.mouseClicked(e) || super.mouseClicked(e, doubled);
        }

        @Override
        boolean mouseScrolled(double x, double y, double h, double v) {
            return !scrolled.isEmpty() && scroller.mouseScrolled(x, y, v) || super.mouseScrolled(x, y, h, v);
        }

        @Override
        boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
            return scroller.mouseDragged(e) || super.mouseDragged(e, dx, dy);
        }

        @Override
        boolean mouseReleased(MouseButtonEvent e) {
            return scroller.mouseReleased() || super.mouseReleased(e);
        }

        private static int colour(WorldEditPattern.Kind kind) {
            return switch (kind) {
                case COMMAND -> Theme.STRONG;
                case FROM -> Theme.INFO;
                case BLOCK -> Theme.TEXT;
                case SHARE, SEPARATOR -> Theme.DIM;
            };
        }

        private static List<List<WorldEditPattern.Piece>> wrap(List<WorldEditPattern.Piece> pieces, int width) {
            List<List<WorldEditPattern.Piece>> lines = new ArrayList<>();
            lines.add(new ArrayList<>());
            int used = 0, start = 0;
            for (int i = 0; i < pieces.size(); i++) {
                if (pieces.get(i).kind() != WorldEditPattern.Kind.SEPARATOR && i < pieces.size() - 1) continue;
                List<WorldEditPattern.Piece> word = pieces.subList(start, i + 1);
                start = i + 1;
                int w = 0;
                for (WorldEditPattern.Piece piece : word) w += PanelTab.font().width(piece.text());
                if (used > 0 && used + w > width) {
                    lines.add(new ArrayList<>());
                    used = INDENT;
                }
                for (WorldEditPattern.Piece piece : word) {
                    List<String> parts = w <= width - INDENT ? List.of(piece.text()) : piece.text().codePoints().mapToObj(Character::toString).toList();
                    for (String part : parts) {
                        int pw = PanelTab.font().width(part);
                        if (used > 0 && used + pw > width) {
                            lines.add(new ArrayList<>());
                            used = INDENT;
                        }
                        lines.getLast().add(new WorldEditPattern.Piece(piece.kind(), part));
                        used += pw;
                    }
                }
            }
            return lines;
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
