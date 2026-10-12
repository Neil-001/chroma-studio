package dev.chromastudio.client.index;

import dev.chromastudio.client.ChromaCommand;
import dev.chromastudio.client.ChromaStudioClient;
import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Distribution;
import dev.chromastudio.core.color.Face;
import dev.chromastudio.core.color.Scale;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Every block's colours, and the search for the ones closest to a target. It is built on its own thread whenever
 * the resources reload, and until the first build is done it is empty.
 */
public final class BlockIndex {
    private static final BlockIndex INSTANCE = new BlockIndex();
    private static final Identifier OPERATOR_TAB = Identifier.withDefaultNamespace("op_blocks");

    public static BlockIndex get() { return INSTANCE; }

    /** A finished build. The client thread reads it while the next one is made. */
    private record Built(List<List<Entry>> blocks, Map<Block, List<Entry>> byBlock, Map<BlockState, Entry> byState,
                         Set<String> namespaces, Map<String, List<Entry>> materials) {}

    public record Match(Entry entry, Face face, double distance) {}

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Chroma Studio index");
        t.setDaemon(true);
        return t;
    });
    // Each build takes the next number, and gives up when it sees a later one has been asked for.
    private final AtomicInteger asked = new AtomicInteger();
    private volatile Built built;
    // The build and the world's registries the creative tabs were last read for. Client thread only.
    private Built listedBuild;
    private HolderLookup.Provider listedFor;
    // The same registries under another identity, to fill the tabs from. The game refills its tabs when it is
    // asked with registries that aren't the very ones it last used, and only then feeds its creative search.
    // Asked with the world's own, it could find nothing changed after us and leave that search empty.
    private HolderLookup.Provider tabRegistries;
    private volatile float progress;
    private volatile boolean building;
    private volatile List<String> mods = List.of();

    private BlockIndex() {}

    /**
     * Hooks the index up. Each loader's client entrypoint calls this once and registers what it returns as a client
     * resource reload listener. {@code mods} names every loaded mod with its version; they key the cache. It also
     * adds {@code /chroma debug}.
     */
    public static PreparableReloadListener install(Collection<String> mods) {
        INSTANCE.mods = List.copyOf(mods);
        ChromaCommand.SUBCOMMANDS.add(DebugCommand::attach);
        return (ResourceManagerReloadListener) _ -> {
            if (!INSTANCE.ready() || IndexOptions.current().rebuildOnReload()) INSTANCE.start(true);
        };
    }

    /** False until the first build finishes. It stays true while a later build runs, and the earlier one answers. */
    public boolean ready() { return built != null; }

    /** Whether a build is running. */
    public boolean building() { return building; }

    /** How far the running build is, 0 to 1. With none running, 1 when ready. */
    public float progress() { return building ? progress : ready() ? 1 : 0; }

    /** Builds again in the background, reading every image afresh in case a pack changed in place. */
    public void rebuild() { start(false); }

    private void start(boolean useCache) {
        int number = asked.incrementAndGet();
        building = true;
        progress = 0;
        worker.execute(() -> {
            try {
                if (number == asked.get()) build(number, useCache);
            } catch (Exception e) {
                ChromaStudioClient.LOGGER.error("Couldn't build the block index", e);
            } finally {
                if (number == asked.get()) building = false;
            }
        });
    }

    private void build(int number, boolean useCache) throws Exception {
        long started = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        IndexOptions options = IndexOptions.current();
        // The models and the rest are read on the client thread, so they all come from the same reload.
        record Scene(BlockStateModelSet models, FluidStateModelSet fluids, BlockColors colours, ResourceManager resources, List<String> packs) {}
        Scene scene = mc.submit(() -> new Scene(mc.getModelManager().getBlockStateModelSet(), mc.getModelManager().getFluidStateModelSet(),
                mc.getBlockColors(), mc.getResourceManager(), List.copyOf(mc.getResourcePackRepository().getSelectedIds()))).get(1, TimeUnit.MINUTES);
        long waited = System.nanoTime() - started;
        Path cache = TextureCache.file(TextureCache.folder(options), scene.packs, mods, options);
        IndexBuilder builder = new IndexBuilder(scene.models, scene.fluids, scene.colours, scene.resources,
                useCache ? TextureCache.load(cache) : new HashMap<>(), options);
        int cached = builder.frames.size();

        List<List<Entry>> blocks = new ArrayList<>();
        Map<BlockState, Entry> byState = new IdentityHashMap<>();
        int done = 0, total = BuiltInRegistries.BLOCK.size(), entryCount = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (number != asked.get()) return;
            progress = (float) done++ / total;
            if (block.defaultBlockState().isAir()) continue;
            List<Entry> entries;
            try {
                entries = builder.entries(block, technical(block, null), byState);
            } catch (RuntimeException e) {
                // One mod's odd model shouldn't cost the player every other block.
                ChromaStudioClient.LOGGER.warn("Left {} out of the block index", BuiltInRegistries.BLOCK.getKey(block), e);
                continue;
            }
            blocks.add(entries);
            entryCount += entries.size();
        }

        if (number != asked.get()) return;
        adopt(blocks, byState);
        mc.execute(this::current);

        if (builder.imagesRead > 0) TextureCache.save(cache, builder.frames);
        TextureCache.prune(cache);
        ChromaStudioClient.LOGGER.info("Indexed {} blocks as {} entries in {} ms, after {} ms waiting for the client thread: {} textures, {} from the cache and {} images read in {} ms, statistics in {} ms",
                blocks.size(), entryCount, (System.nanoTime() - started - waited) / 1_000_000, waited / 1_000_000, builder.frames.size(), cached,
                builder.imagesRead, builder.readNanos / 1_000_000, builder.statsNanos / 1_000_000);
    }

    /** Makes a build's entries the ones the index answers from. Each list is one block's states, the default first. */
    void adopt(List<List<Entry>> blocks, Map<BlockState, Entry> byState) {
        Map<Block, List<Entry>> byBlock = new IdentityHashMap<>();
        Set<String> namespaces = new TreeSet<>();
        Map<String, List<Entry>> materials = new HashMap<>();
        for (List<Entry> entries : blocks) {
            Entry first = entries.getFirst();
            byBlock.put(first.block(), entries);
            namespaces.add(first.id().substring(0, first.id().indexOf(':')));
            materials.computeIfAbsent(MaterialSets.base(first.id()), _ -> new ArrayList<>()).add(first);
        }
        Comparator<Entry> inSet = Comparator.comparingInt(e -> MaterialSets.order(e.id()));
        for (List<Entry> set : materials.values()) set.sort(inSet.thenComparing(Entry::id));
        built = new Built(List.copyOf(blocks), byBlock, byState, Collections.unmodifiableSet(namespaces), materials);
    }

    /**
     * Reads the creative tabs for the world the player has just joined. Each loader calls this when the player
     * joins, while the loading screen still hides the moment it takes. The client thread only.
     */
    public void worldJoined() { current(); }

    /**
     * The build to answer from, or null before the first. On the client thread in a world it first reads the
     * creative tabs, if they haven't been read for this build and this world. Item stacks can't be made before a
     * world has loaded, so the tabs can't be filled any sooner.
     */
    private Built current() {
        Built b = built;
        Minecraft mc = Minecraft.getInstance();
        if (b == null || mc == null || !mc.isSameThread()) return b;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return b;
        HolderLookup.Provider registries = connection.registryAccess();
        if (listedBuild == b && listedFor == registries) return b;
        if (listedFor != registries) tabRegistries = HolderLookup.Provider.create(registries.listRegistries());
        listedBuild = b;
        listedFor = registries;
        try {
            long started = System.nanoTime();
            Set<Block> listed = listedBlocks(tabRegistries);
            for (List<Entry> states : b.blocks) for (Entry e : states) e.technical(technical(e.block(), listed));
            ChromaStudioClient.LOGGER.info("Read the creative tabs in {} ms: {} blocks are listed", (System.nanoTime() - started) / 1_000_000, listed.size());
        } catch (RuntimeException e) {
            ChromaStudioClient.LOGGER.warn("Couldn't read the creative tabs, so every block with an item counts as listed", e);
        }
        return b;
    }

    /**
     * Whether a block is technical: one with no item in a creative tab other than Operator Utilities. Fluids are
     * never technical. Until the tabs have been read, {@code listed} is null and any block with an item counts.
     */
    private static boolean technical(Block block, Set<Block> listed) {
        if (block instanceof LiquidBlock) return false;
        return listed == null ? !Item.BY_BLOCK.containsKey(block) : !listed.contains(block);
    }

    /**
     * The blocks with an item in a creative tab other than Operator Utilities. The tabs are filled by the game's
     * own rebuild with every feature and the operator items on, so modded tabs and experimental blocks count.
     */
    private static Set<Block> listedBlocks(HolderLookup.Provider registries) {
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.REGISTRY.allFlags(), true, registries);
        Set<Block> listed = new HashSet<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            // The search tab repeats every other tab, the operator one included.
            if (tab.getType() != CreativeModeTab.Type.CATEGORY || OPERATOR_TAB.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab))) continue;
            for (ItemStack stack : tab.getDisplayItems()) if (stack.getItem() instanceof BlockItem item) listed.add(item.getBlock());
        }
        return listed;
    }

    /**
     * Every entry, by the settings: technical blocks only when they are on, and one entry a block, its default
     * state, unless states are listed separately. Empty until the index is ready.
     */
    public List<Entry> entries() {
        Built b = current();
        if (b == null) return List.of();
        IndexOptions options = IndexOptions.current();
        List<Entry> out = new ArrayList<>();
        for (List<Entry> states : b.blocks) {
            if (states.getFirst().technical() && !options.technical()) continue;
            if (options.statesSeparately()) out.addAll(states);
            else out.add(states.getFirst());
        }
        return out;
    }

    /** A block's entries, the default state first. Empty when the block isn't indexed. */
    public List<Entry> statesOf(Block block) {
        Built b = current();
        return b == null ? List.of() : b.byBlock.getOrDefault(block, List.of());
    }

    /**
     * The entries closest to a target, nearest first, and by id among equals. A block gives its best state only,
     * unless the query asks for states separately.
     */
    public List<Match> closest(Target target, Query q) {
        Built b = current();
        if (b == null) return List.of();
        // Many faces share a texture. Each look is scored once, for each shading it can have.
        Map<Sampled, double[]> scores = new IdentityHashMap<>();
        Distribution wanted = target.distribution(q.scale);
        List<Match> matches = new ArrayList<>();
        for (List<Entry> states : b.blocks) {
            Match best = null;
            for (Entry e : states) {
                if (!q.keeps(e)) continue;
                Match m = match(e, wanted, q, scores);
                if (m == null) continue;
                if (q.statesSeparately) matches.add(m);
                else if (best == null || m.distance < best.distance) best = m;
            }
            if (best != null) matches.add(best);
        }
        matches.sort(Comparator.comparingDouble(Match::distance).thenComparing(m -> m.entry.id()).thenComparing(m -> m.entry.properties()));
        return matches.size() > q.limit ? List.copyOf(matches.subList(0, Math.max(0, q.limit))) : matches;
    }

    /** An entry's best face under the query, or null when no face passes the filters. */
    private static Match match(Entry e, Distribution wanted, Query q, Map<Sampled, double[]> scores) {
        Match best = null;
        for (Face f : Entry.BY_PREFERENCE) {
            if (q.face != Face.ANY && q.face != f) continue;
            Sampled face = e.sampled(f, q.tint);
            if (!q.keeps(face)) continue;
            double[] byFace = scores.computeIfAbsent(face, _ -> {
                double[] fresh = new double[Entry.SIDES.length];
                Arrays.fill(fresh, Double.NaN);
                return fresh;
            });
            // Without shading every face of a look scores the same, so one slot does for all.
            int slot = q.shading ? f.ordinal() : 0;
            if (Double.isNaN(byFace[slot])) byFace[slot] = wanted.emd(face.distribution(q.scale, q.shading ? f : null), q.metric);
            if (best == null || byFace[slot] < best.distance) best = new Match(e, f, byFace[slot]);
        }
        return best;
    }

    /** The mod namespaces that have blocks in the index, in order. */
    public Set<String> namespaces() {
        Built b = current();
        return b == null ? Set.of() : b.namespaces;
    }

    /**
     * The blocks of the same material as an entry, such as oak's planks, log, slab, stairs and fence: full blocks
     * first, then by shape. Technical ones are left out unless the setting lists them.
     */
    public List<Entry> materialSet(Entry e) {
        Built b = current();
        if (b == null) return List.of();
        boolean technical = IndexOptions.current().technical();
        return b.materials.getOrDefault(MaterialSets.base(e.id()), List.of()).stream().filter(m -> technical || !m.technical()).toList();
    }

    /**
     * The average colour of a block state's face, for the public API. A state the index folded into another that
     * shows the same textures answers from that one's, with its own tint. {@link Face#ANY} gives the average of all six. Empty when the block
     * isn't indexed.
     */
    public Optional<Color> colourOf(BlockState state, Face face, TintContext tint) {
        Built b = current();
        if (b == null) return Optional.empty();
        Entry e = b.byState.get(state);
        if (e == null && state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)) {
            e = b.byState.get(state.setValue(BlockStateProperties.WATERLOGGED, false));
        }
        if (e == null) {
            List<Entry> states = b.byBlock.get(state.getBlock());
            if (states == null) return Optional.empty();
            e = states.getFirst();
        }
        if (face != Face.ANY) return Optional.of(e.mean(face, state, tint));
        List<Color> means = new ArrayList<>();
        for (Face f : Entry.SIDES) means.add(e.mean(f, state, tint));
        return Optional.of(Distribution.of(means, Collections.nCopies(means.size(), 1.0)).mean());
    }
}
