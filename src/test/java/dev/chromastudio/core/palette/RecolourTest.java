package dev.chromastudio.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.color.Color;
import dev.chromastudio.core.color.Oklab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class RecolourTest {
    private static final Map<String, String> CONCRETE = ordered(
            "red", "#8E2121", "orange", "#E06101", "yellow", "#F1AF15", "lime", "#5EA919", "green", "#495B24",
            "cyan", "#157788", "light_blue", "#2489C7", "blue", "#2D2F8F", "purple", "#64209C", "magenta", "#A9309F",
            "pink", "#D6658F", "white", "#CFD5D6", "light_gray", "#7D7D73", "gray", "#373A3E", "black", "#080A0F",
            "brown", "#603C20");
    private static final Map<String, String> DESERT = ordered(
            "sandstone", "#DBCFA2", "smooth_sandstone", "#E0D6AB", "cut_sandstone", "#DDD3A6", "chiseled_sandstone", "#DCD1A5",
            "sand", "#DBD0A4", "terracotta", "#985E44", "orange_terracotta", "#A25426", "yellow_terracotta", "#BA8523",
            "birch_planks", "#C2B17A", "red_sandstone", "#B9631F", "cut_red_sandstone", "#BA6420");
    private static final Map<String, String> NETHER = ordered(
            "nether_bricks", "#2E171B", "red_nether_bricks", "#4A080A", "netherrack", "#632828", "blackstone", "#2C272E",
            "polished_blackstone_bricks", "#332E37", "basalt", "#515154", "crimson_planks", "#693249", "crimson_stem", "#6C2A3B",
            "nether_wart_block", "#750403", "gilded_blackstone", "#48382C");
    private static final Map<String, String> HOUSE = ordered(
            "deepslate_tiles", "#393939", "deepslate_bricks", "#4B4B4B", "cobbled_deepslate", "#515153", "mossy_stone_bricks", "#767B6E",
            "cracked_stone_bricks", "#787778", "stone_bricks", "#7C7C7C", "cobblestone", "#848484", "polished_andesite", "#878988",
            "andesite", "#89898A", "smooth_stone", "#A0A0A0");

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return out;
    }

    private static final Map<String, Integer> WEIGHTS = Map.of("sandstone", 4, "smooth_sandstone", 3, "cut_sandstone", 2, "sand", 2,
            "terracotta", 2, "nether_bricks", 4, "red_nether_bricks", 3, "netherrack", 2, "blackstone", 2, "polished_blackstone_bricks", 2);

    private static List<Recolour.Look> looks(Map<String, String> colours) {
        return colours.entrySet().stream().map(e -> new Recolour.Look(Color.fromHex(e.getValue()), false, WEIGHTS.getOrDefault(e.getKey(), 1))).toList();
    }

    private static Recolour.Look solid(String hex) { return new Recolour.Look(Color.fromHex(hex), false); }

    private static Recolour.Look glass(String hex) { return new Recolour.Look(Color.fromHex(hex), true); }

    private static List<String> recolour(Map<String, String> build, Map<String, String> palette, Recolour.Mode mode) {
        List<Recolour.Look> b = looks(build);
        Recolour r = new Recolour(looks(palette), b, mode);
        List<String> names = List.copyOf(palette.keySet());
        return b.stream().map(l -> names.get(r.pick(l))).toList();
    }

    private static double lightness(String hex) { return Color.fromHex(hex).oklab().l(); }

    @Test
    void shadingKeepsARainbowFromCollapsingIntoOneBlock() {
        List<String> closest = recolour(CONCRETE, DESERT, Recolour.Mode.CLOSEST);
        List<String> shading = recolour(CONCRETE, DESERT, Recolour.Mode.SHADING);
        int before = new HashSet<>(closest).size(), after = new HashSet<>(shading).size();
        assertTrue(before <= 6, "closest colour gave " + before + ": " + closest);
        long most = closest.stream().filter(n -> n.equals("terracotta")).count();
        assertTrue(most >= 5, "closest colour sent " + most + " to terracotta: " + closest);
        assertTrue(after >= 8, after + ": " + shading);
        List<Recolour.Look> wider = new ArrayList<>();
        for (Map<String, String> m : List.of(CONCRETE, HOUSE, DESERT, NETHER)) for (String hex : m.values()) wider.add(solid(hex));
        Recolour r = new Recolour(looks(DESERT), wider, Recolour.Mode.SHADING);
        Set<Integer> picked = new HashSet<>();
        for (Recolour.Look l : looks(CONCRETE)) picked.add(r.pick(l));
        assertTrue(picked.size() >= 5 && picked.size() > before, picked.toString());
    }

    @Test
    void shadingSpreadsAStoneHouseOverEachPalette() {
        for (Map<String, String> palette : List.of(DESERT, NETHER)) {
            int closest = new HashSet<>(recolour(HOUSE, palette, Recolour.Mode.CLOSEST)).size();
            List<String> shading = recolour(HOUSE, palette, Recolour.Mode.SHADING);
            assertTrue(closest <= 2, "closest colour gave " + closest);
            assertTrue(new HashSet<>(shading).size() >= 6, shading.toString());
        }
    }

    @Test
    void shadingKeepsTheOrderFromDarkestToLightest() {
        for (Map<String, String> build : List.of(CONCRETE, HOUSE)) {
            for (Map<String, String> palette : List.of(DESERT, NETHER)) {
                List<String> keys = new ArrayList<>(build.keySet());
                keys.sort(Comparator.comparingDouble(k -> lightness(build.get(k))));
                List<String> out = recolour(build, palette, Recolour.Mode.SHADING);
                List<String> order = List.copyOf(build.keySet());
                double last = -1;
                for (String k : keys) {
                    double l = lightness(palette.get(out.get(order.indexOf(k))));
                    assertTrue(l >= last, k + " became darker than a darker block's new block: " + out);
                    last = l;
                }
                String darkest = palette.keySet().stream().min(Comparator.comparingDouble(k -> lightness(palette.get(k)))).orElseThrow();
                String lightest = palette.keySet().stream().max(Comparator.comparingDouble(k -> lightness(palette.get(k)))).orElseThrow();
                assertEquals(darkest, out.get(order.indexOf(keys.getFirst())));
                assertEquals(lightest, out.get(order.indexOf(keys.getLast())));
            }
        }
    }

    @Test
    void closestIsThePlainNearestColour() {
        Recolour r = new Recolour(looks(DESERT), List.of(), Recolour.Mode.CLOSEST);
        List<String> names = List.copyOf(DESERT.keySet());
        assertEquals("sandstone", names.get(r.pick(solid("#DBCFA2"))));
        assertEquals("orange_terracotta", names.get(r.pick(solid("#8E2121"))));
        assertEquals("terracotta", names.get(r.pick(solid("#373A3E"))));
        assertEquals("birch_planks", names.get(r.pick(solid("#F1AF15"))));
    }

    @Test
    void solidBlocksBecomeGlassOnlyWhenThePaletteHasNothingElse() {
        List<Recolour.Look> palette = List.of(solid("#DBCFA2"), glass("#334CB2"), solid("#985E44"));
        List<Recolour.Look> build = looks(CONCRETE);
        for (Recolour.Mode mode : Recolour.Mode.values()) {
            Recolour r = new Recolour(palette, build, mode);
            for (Recolour.Look l : build) assertTrue(r.pick(l) != 1, mode + " " + l);
            assertEquals(1, r.pick(glass("#B4D7DC")));
            assertEquals(1, r.pick(solid("#2D2F8F"), i -> i == 1));
        }
        Recolour noGlass = new Recolour(looks(DESERT), build, Recolour.Mode.SHADING);
        assertEquals(-1, noGlass.pick(glass("#B4D7DC")));
        Recolour allGlass = new Recolour(List.of(glass("#334CB2"), glass("#B4D7DC")), build, Recolour.Mode.SHADING);
        assertEquals(1, allGlass.pick(solid("#CFD5D6")));
        assertEquals(0, allGlass.pick(solid("#080A0F")));
    }

    @Test
    void aBlockOnlyBecomesWhatItsShapeAllows() {
        Set<String> stairs = Set.of("sandstone", "birch_planks", "red_sandstone");
        List<String> names = List.copyOf(DESERT.keySet());
        Recolour r = new Recolour(looks(DESERT), looks(HOUSE), Recolour.Mode.SHADING);
        int picked = r.pick(solid("#393939"), i -> stairs.contains(names.get(i)));
        assertEquals("red_sandstone", names.get(picked));
        assertEquals("orange_terracotta", names.get(r.pick(solid("#393939"))));
        assertEquals(-1, r.pick(solid("#393939"), _ -> false));
    }

    @Test
    void aPalettesWeightsSayHowMuchOfABigBuildEachBlockTakes() {
        List<Recolour.Look> palette = List.of(new Recolour.Look(Color.fromHex("#985E44"), false, 1),
                new Recolour.Look(Color.fromHex("#DBCFA2"), false, 8), new Recolour.Look(Color.fromHex("#E0D6AB"), false, 1));
        Recolour r = new Recolour(palette, looks(CONCRETE), Recolour.Mode.SHADING);
        long heavy = looks(CONCRETE).stream().filter(l -> r.pick(l) == 1).count();
        assertTrue(heavy >= 10, heavy + " of 16");
    }

    @Test
    void withEnoughPaletteBlocksEachPlainShadeKeepsItsOwn() {
        List<Recolour.Look> palette = List.of(new Recolour.Look(Color.fromHex("#202020"), false, 8),
                new Recolour.Look(Color.fromHex("#808080"), false, 1), new Recolour.Look(Color.fromHex("#E0E0E0"), false, 1));
        List<Recolour.Look> build = List.of(solid("#303030"), solid("#707070"), solid("#C0C0C0"));
        Recolour r = new Recolour(palette, build, Recolour.Mode.SHADING);
        assertEquals(List.of(0, 1, 2), build.stream().map(r::pick).toList());
        List<Recolour.Look> variant = List.of(solid("#303030"), solid("#707070"), solid("#C0C0C0"), solid("#C1C1C1"));
        Recolour v = new Recolour(palette, variant, Recolour.Mode.SHADING);
        assertEquals(List.of(0, 1, 2, 2), variant.stream().map(v::pick).toList());
        List<Recolour.Look> variants = List.of(solid("#303030"), solid("#707070"), solid("#727272"), solid("#C0C0C0"), solid("#C2C2C2"));
        Recolour w = new Recolour(palette, variants, Recolour.Mode.SHADING);
        assertEquals(List.of(0, 1, 1, 2, 2), variants.stream().map(w::pick).toList());
        List<Recolour.Look> heavy = List.of(new Recolour.Look(Color.fromHex("#202020"), false, 99),
                new Recolour.Look(Color.fromHex("#808080"), false, 1), new Recolour.Look(Color.fromHex("#E0E0E0"), false, 1));
        List<Recolour.Look> four = List.of(solid("#202020"), solid("#606060"), solid("#A0A0A0"), solid("#E0E0E0"));
        Recolour h = new Recolour(heavy, four, Recolour.Mode.SHADING);
        assertEquals(List.of(0, 0, 1, 2), four.stream().map(h::pick).toList());
        assertEquals(List.of(0, 0, 2), build.stream().map(l -> r.pick(l, i -> i != 1)).toList());
        List<Recolour.Look> close = List.of(solid("#999999"), solid("#9A9A9A"), solid("#FFFFFF"));
        Recolour c = new Recolour(palette, close, Recolour.Mode.SHADING);
        assertEquals(2, c.pick(close.get(2)));
        assertTrue(c.pick(close.get(0)) == c.pick(close.get(1)) || c.pick(close.get(0)) + 1 == c.pick(close.get(1)));
        assertTrue(new HashSet<>(recolour(HOUSE, DESERT, Recolour.Mode.SHADING)).size() >= 8);
    }

    private static final Map<String, Integer> HOUSE_COUNTS = Map.of("deepslate_tiles", 90, "deepslate_bricks", 20, "cobbled_deepslate", 5,
            "mossy_stone_bricks", 10, "cracked_stone_bricks", 30, "stone_bricks", 120, "cobblestone", 30, "polished_andesite", 10,
            "andesite", 20, "smooth_stone", 30);
    private static final Map<String, String> HOUSE_FAMILIES = Map.of("mossy_stone_bricks", "stone_brick", "cracked_stone_bricks", "stone_brick",
            "stone_bricks", "stone_brick", "deepslate_tiles", "deepslate_tile", "deepslate_bricks", "deepslate_brick");

    private static Map<String, String> recolourHouse(Map<String, String> palette) {
        List<Recolour.Look> build = new ArrayList<>();
        for (var e : HOUSE.entrySet()) {
            build.add(new Recolour.Look(Color.fromHex(e.getValue()), false, HOUSE_COUNTS.get(e.getKey()), HOUSE_FAMILIES.getOrDefault(e.getKey(), e.getKey())));
        }
        Recolour r = new Recolour(looks(palette), build, Recolour.Mode.SHADING);
        List<String> names = List.copyOf(palette.keySet()), kinds = List.copyOf(HOUSE.keySet());
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < build.size(); i++) out.put(kinds.get(i), names.get(r.pick(build.get(i))));
        return out;
    }

    @Test
    void aCountedHouseKeepsItsMaterialsApartAndItsWallsOnThePalettesMainBlock() {
        for (Map<String, String> palette : List.of(DESERT, NETHER)) {
            Map<String, String> out = recolourHouse(palette);
            String walls = out.get("stone_bricks");
            assertEquals(walls, out.get("cracked_stone_bricks"), out.toString());
            assertEquals(walls, out.get("mossy_stone_bricks"), out.toString());
            assertTrue(WEIGHTS.getOrDefault(walls, 1) >= 2, out.toString());
            assertEquals(3, Set.of(out.get("cobblestone"), out.get("andesite"), out.get("polished_andesite")).size(), out.toString());
            assertTrue(new HashSet<>(out.values()).size() >= 7, out.toString());
            List<String> byLightness = new ArrayList<>(HOUSE.keySet());
            byLightness.sort(Comparator.comparingDouble(k -> lightness(HOUSE.get(k))));
            double last = -1;
            for (String k : byLightness) {
                double l = lightness(palette.get(out.get(k)));
                assertTrue(l >= last, k + " became darker than a darker block's new block: " + out);
                last = l;
            }
        }
        assertEquals("sandstone", recolourHouse(DESERT).get("stone_bricks"));
    }

    @Test
    void pinningTheMainShadeNeverMakesShadesShareWhenThereIsRoom() {
        List<Recolour.Look> palette = new ArrayList<>();
        int[] paletteWeights = {1, 100, 1, 1, 1};
        for (int i = 0; i < 5; i++) palette.add(new Recolour.Look(Color.of(new Oklab(0.15 + 0.18 * i, 0, 0)), false, paletteWeights[i]));
        List<Recolour.Look> build = new ArrayList<>();
        int[] counts = {1, 1, 100, 1, 1};
        for (int i = 0; i < 5; i++) build.add(new Recolour.Look(Color.of(new Oklab(0.2 + 0.17 * i, 0, 0)), false, counts[i], "m" + i));
        Recolour r = new Recolour(palette, build, Recolour.Mode.SHADING);
        assertEquals(List.of(0, 1, 2, 3, 4), build.stream().map(r::pick).toList());
        int[] otherWeights = {2, 16, 64, 1, 2}, otherCounts = {1, 64, 1, 32, 32};
        List<Recolour.Look> otherPalette = new ArrayList<>(), otherBuild = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            otherPalette.add(new Recolour.Look(Color.of(new Oklab(0.15 + 0.18 * i, 0, 0)), false, otherWeights[i]));
            otherBuild.add(new Recolour.Look(Color.of(new Oklab(0.2 + 0.17 * i, 0, 0)), false, otherCounts[i], "m" + i));
        }
        Recolour o = new Recolour(otherPalette, otherBuild, Recolour.Mode.SHADING);
        assertEquals(List.of(0, 1, 2, 3, 4), otherBuild.stream().map(o::pick).toList());
    }

    @Test
    void variantsSharingAShadeCountAsOneMaterial() {
        Recolour.Shades shades = new Recolour.Shades(List.of(
                new Recolour.Look(Color.of(new Oklab(0.3, 0, 0)), false, 1, "dark"),
                new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 90, "wall"),
                new Recolour.Look(Color.of(new Oklab(0.7, 0, 0)), false, 20, "trim"),
                new Recolour.Look(Color.of(new Oklab(0.705, 0, 0)), false, 20, "trim"),
                new Recolour.Look(Color.of(new Oklab(0.71, 0, 0)), false, 20, "trim"),
                new Recolour.Look(Color.of(new Oklab(0.9, 0, 0)), false, 1, "light")));
        assertEquals(4, shades.count());
        assertTrue(shades.amount(1) > shades.amount(2), shades.amount(1) + " against " + shades.amount(2));
    }

    private static Oklab[] grey(double... lightness) { return Arrays.stream(lightness).mapToObj(l -> new Oklab(l, 0, 0)).toArray(Oklab[]::new); }

    private static List<Recolour.Look> looks(Oklab... colours) {
        return Arrays.stream(colours).map(c -> new Recolour.Look(Color.of(c), false)).toList();
    }

    @Test
    void shadesGroupVariantsThatCantBeToldApart() {
        Recolour.Shades shades = new Recolour.Shades(looks(grey(0.2, 0.4, 0.4, 0.41, 0.8)));
        assertEquals(3, shades.count());
        assertEquals(List.of(0, 1, 1, 2), Arrays.stream(grey(0.2, 0.4, 0.41, 0.8)).map(c -> shades.of(Color.of(c).oklab(), null)).toList());
        assertEquals(2, shades.of(new Oklab(0.75, 0, 0), null));
        assertEquals(2, new Recolour.Shades(looks(new Oklab(0.5, 0.15, 0), new Oklab(0.5, -0.15, 0))).count());
        assertEquals(0, new Recolour.Shades(List.of()).count());
    }

    @Test
    void aHouseWithMoreRoofThanWallStillPutsItsWallsOnAMainBlock() {
        Map<String, Integer> counts = Map.of("deepslate_tiles", 98, "deepslate_bricks", 20, "cobbled_deepslate", 1, "mossy_stone_bricks", 1,
                "cracked_stone_bricks", 10, "stone_bricks", 53, "cobblestone", 30, "polished_andesite", 63, "andesite", 9, "smooth_stone", 28);
        List<Recolour.Look> build = new ArrayList<>();
        for (var e : HOUSE.entrySet()) {
            build.add(new Recolour.Look(Color.fromHex(e.getValue()), false, counts.get(e.getKey()), HOUSE_FAMILIES.getOrDefault(e.getKey(), e.getKey())));
        }
        for (Map<String, String> palette : List.of(DESERT, NETHER)) {
            Recolour r = new Recolour(looks(palette), build, Recolour.Mode.SHADING);
            List<String> names = List.copyOf(palette.keySet()), kinds = List.copyOf(HOUSE.keySet());
            Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < build.size(); i++) out.put(kinds.get(i), names.get(r.pick(build.get(i))));
            assertTrue(WEIGHTS.getOrDefault(out.get("stone_bricks"), 1) >= 2, out.toString());
            assertTrue(new HashSet<>(out.values()).size() >= 6, out.toString());
        }
    }

    private static List<Recolour.Look> greys(double[] lightness, int[] weights) {
        List<Recolour.Look> out = new ArrayList<>();
        for (int i = 0; i < lightness.length; i++) out.add(new Recolour.Look(Color.of(new Oklab(lightness[i], 0, 0)), false, weights[i], "m" + lightness[i]));
        return out;
    }

    private static List<Integer> picks(Recolour r, List<Recolour.Look> build) { return build.stream().map(r::pick).toList(); }

    private static void inOrder(List<Recolour.Look> palette, List<Recolour.Look> build, List<Integer> picked) {
        for (int i = 0; i < build.size(); i++) for (int j = 0; j < build.size(); j++) {
            if (build.get(i).colour().oklab().l() < build.get(j).colour().oklab().l()) {
                assertTrue(palette.get(picked.get(i)).colour().oklab().l() <= palette.get(picked.get(j)).colour().oklab().l(), picked.toString());
            }
        }
    }

    @Test
    void theMainMaterialTakesThePalettesMainBlockWhereverItSitsInTheBuild() {
        List<Recolour.Look> palette = greys(new double[] {0.2, 0.35, 0.5, 0.65, 0.8}, new int[] {1, 1, 6, 5, 1});
        double[][] builds = {{0.4}, {0.4, 0.6}, {0.4, 0.6}, {0.3, 0.45, 0.6}, {0.3, 0.45, 0.6}, {0.3, 0.45, 0.6}, {0.25, 0.4, 0.55, 0.7}};
        int[][] counts = {{900}, {900, 20}, {20, 900}, {900, 20, 20}, {20, 900, 20}, {20, 20, 900}, {5, 30, 30, 900}};
        for (int b = 0; b < builds.length; b++) {
            List<Recolour.Look> build = greys(builds[b], counts[b]);
            List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
            int main = 0;
            for (int k = 1; k < counts[b].length; k++) if (counts[b][k] > counts[b][main]) main = k;
            assertTrue(palette.get(picked.get(main)).weight() >= 5, b + ": " + picked);
            inOrder(palette, build, picked);
            if (build.size() <= 3) assertEquals(build.size(), new HashSet<>(picked).size(), b + ": " + picked);
            if (main != 0) assertEquals(0, picked.getFirst(), b + ": " + picked);
            if (main != build.size() - 1) assertEquals(4, picked.getLast(), b + ": " + picked);
        }
    }

    @Test
    void aBlockThePaletteHasStaysAsItIs() {
        List<Recolour.Look> palette = List.of(new Recolour.Look(Color.of(new Oklab(0.3, 0, 0)), false, 5, "a", "a"),
                new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 1, "b", "b"), new Recolour.Look(Color.of(new Oklab(0.7, 0, 0)), false, 1, "c", "c"));
        List<Recolour.Look> build = List.of(new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 900, "b", "b"),
                new Recolour.Look(Color.of(new Oklab(0.56, 0, 0)), false, 20, "x", "x"), new Recolour.Look(Color.of(new Oklab(0.7, 0, 0)), false, 30, "c", "c"));
        Recolour r = new Recolour(palette, build, Recolour.Mode.SHADING);
        assertEquals(List.of(1, 1, 2), picks(r, build));
        assertEquals(0, r.pick(new Recolour.Look(Color.of(new Oklab(0.3, 0, 0)), false, 1, "a", "a")));
        assertEquals(1, r.pick(new Recolour.Look(Color.of(new Oklab(0.7, 0, 0)), false, 30, "c", "c"), i -> i != 2));
    }

    @Test
    void otherMaterialsKeepTheirDifferenceFromTheMainOne() {
        List<Recolour.Look> palette = List.of(new Recolour.Look(Color.of(new Oklab(0.4, 0, 0)), false, 1),
                new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 5, null, "wall"), new Recolour.Look(Color.of(new Oklab(0.6, 0.02, 0.12)), false, 1),
                new Recolour.Look(Color.of(new Oklab(0.63, 0, 0)), false, 1), new Recolour.Look(Color.of(new Oklab(0.85, 0, 0)), false, 1));
        List<Recolour.Look> build = List.of(new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 900, "wall", "wall"),
                new Recolour.Look(Color.of(new Oklab(0.6, 0, 0)), false, 60, "trim", "trim"));
        assertEquals(List.of(1, 3), picks(new Recolour(palette, build, Recolour.Mode.SHADING), build));
        List<Recolour.Look> desert = looks(DESERT);
        List<Recolour.Look> house = greys(new double[] {0.35, 0.6, 0.7}, new int[] {40, 900, 40});
        List<String> names = List.copyOf(DESERT.keySet());
        List<Integer> picked = picks(new Recolour(desert, house, Recolour.Mode.SHADING), house);
        assertEquals("sandstone", names.get(picked.get(1)), picked.toString());
        assertTrue(lightness(DESERT.get(names.get(picked.get(0)))) < lightness(DESERT.get("sandstone")) - 0.1, picked.toString());
        assertTrue(lightness(DESERT.get(names.get(picked.get(2)))) > lightness(DESERT.get("sandstone")), picked.toString());
    }

    @Test
    void looksThatWeighNothingCountAsOneEach() {
        List<Recolour.Look> palette = greys(new double[] {0.2, 0.5, 0.9}, new int[] {1, 1, 1});
        List<Recolour.Look> build = greys(new double[] {0.25, 0.85}, new int[] {0, 0});
        assertEquals(List.of(0, 2), picks(new Recolour(palette, build, Recolour.Mode.SHADING), build));
    }

    @Test
    void theMainMaterialTakesAMainBlockWhenTheBuildHasMoreKindsThanThePalette() {
        List<Recolour.Look> palette = greys(new double[] {0.15, 0.55, 0.9}, new int[] {1, 5, 1});
        List<Recolour.Look> build = greys(new double[] {0.3, 0.45, 0.6, 0.75}, new int[] {900, 5, 50, 5});
        List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
        assertEquals(1, picked.getFirst(), picked.toString());
        inOrder(palette, build, picked);
    }

    @Test
    void aBlockThePaletteKeepsNeverTurnsTheOrderRound() {
        List<Recolour.Look> palette = List.of(new Recolour.Look(Color.of(new Oklab(0.59, 0, 0)), false, 1, "stone_brick", "stone_bricks"),
                new Recolour.Look(Color.of(new Oklab(0.71, 0, 0)), false, 5), new Recolour.Look(Color.of(new Oklab(0.9, 0, 0)), false, 1));
        List<Recolour.Look> build = List.of(new Recolour.Look(Color.of(new Oklab(0.35, 0, 0)), false, 90, "concrete", "gray_concrete"),
                new Recolour.Look(Color.of(new Oklab(0.59, 0, 0)), false, 10, "stone_brick", "stone_bricks"));
        List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
        assertEquals(0, picked.get(1), picked.toString());
        inOrder(palette, build, picked);
    }

    private static Recolour.Look grey(double l, int weight, String block) {
        return new Recolour.Look(Color.of(new Oklab(l, 0, 0)), false, weight, block, block);
    }

    @Test
    void theMainBlockIsChosenBetweenTheBlocksThePaletteKeeps() {
        List<Recolour.Look> palette = List.of(grey(0.15, 1, "black"), grey(0.55, 5, "stone_bricks"), grey(0.65, 1, "andesite"), grey(0.9, 9, "white"));
        List<Recolour.Look> build = List.of(grey(0.35, 900, "dark"), grey(0.65, 10, "andesite"), grey(0.75, 5, "a"), grey(0.8, 5, "b"), grey(0.85, 5, "c"));
        List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
        assertEquals(1, picked.getFirst(), picked.toString());
        assertEquals(2, picked.get(1), picked.toString());
        inOrder(palette, build, picked);
    }

    @Test
    void theMainBlockHoldsWhenKeptBlocksMakeShadesShare() {
        List<Recolour.Look> palette = List.of(grey(0.15, 1, "black"), grey(0.55, 5, "stone_bricks"), grey(0.65, 1, "andesite"), grey(0.75, 9, "light"),
                grey(0.9, 1, "white"));
        List<Recolour.Look> build = List.of(grey(0.25, 5, "a"), grey(0.35, 5, "b"), grey(0.5, 900, "wall"), grey(0.65, 5, "andesite"), grey(0.85, 5, "c"));
        List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
        assertEquals(1, picked.get(2), picked.toString());
        assertEquals(2, picked.get(3), picked.toString());
        inOrder(palette, build, picked);
    }

    @Test
    void aMainBlockThatMakesManyShadesShareNeedsLittleMemory() {
        int n = 600;
        List<Recolour.Look> palette = new ArrayList<>(), build = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double l = 0.1 + 0.8 * i / n;
            palette.add(new Recolour.Look(Color.of(new Oklab(l, 0, 0)), false, i == 1 ? 100 : 1, "p" + i, i == 2 ? "kept" : "p" + i));
            build.add(new Recolour.Look(Color.of(new Oklab(l + 0.0005, 0, 0)), false, i == n - 2 ? 100_000 : 1, "b" + i, i == n - 1 ? "kept" : "b" + i));
        }
        List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
        assertEquals(1, picked.get(n - 2));
        assertEquals(2, picked.get(n - 1));
        inOrder(palette, build, picked);
    }

    @Test
    void everyBlockThePaletteKeepsInOneShadeBoundsTheOthers() {
        List<Recolour.Look> palette = List.of(new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 5, "brick", "brick"), grey(0.52, 1, "between"),
                new Recolour.Look(Color.of(new Oklab(0.55, 0, 0)), false, 1, "brick", "cracked_brick"), grey(0.8, 1, "light"));
        List<Recolour.Look> build = List.of(new Recolour.Look(Color.of(new Oklab(0.5, 0, 0)), false, 900, "brick", "brick"),
                new Recolour.Look(Color.of(new Oklab(0.55, 0, 0)), false, 50, "brick", "cracked_brick"), grey(0.7, 50, "trim"));
        List<Integer> picked = picks(new Recolour(palette, build, Recolour.Mode.SHADING), build);
        assertEquals(List.of(0, 2), picked.subList(0, 2), picked.toString());
        inOrder(palette, build, picked);
    }

    @Test
    void countsScaledAlikeGiveTheSameBlocks() {
        for (long seed = 1; seed <= 20; seed++) {
            List<Recolour.Look> build = randomLooks(12, seed), palette = randomLooks(9, seed + 100);
            List<Recolour.Look> scaled = build.stream().map(l -> new Recolour.Look(l.colour(), false, l.weight() * 7, l.family())).toList();
            Recolour r = new Recolour(palette, build, Recolour.Mode.SHADING), s = new Recolour(palette, scaled, Recolour.Mode.SHADING);
            assertEquals(picks(r, build), picks(s, scaled), "seed " + seed);
        }
    }

    private static List<Recolour.Look> randomLooks(int count, long seed) {
        Random r = new Random(seed);
        List<Recolour.Look> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new Recolour.Look(Color.ofRgb(r.nextInt(0x1000000)), false, 1 + r.nextInt(50), "family " + seed + " " + i));
        return out;
    }

    @Test
    @EnabledIfSystemProperty(named = "chroma.timing", matches = "true")
    void aBigRecolourIsWorkedOutWithinAFrameOrTwo() {
        for (int[] size : new int[][] {{750, 1000}, {1000, 300}, {300, 64}}) {
            List<Recolour.Look> build = randomLooks(size[0], 1), palette = randomLooks(size[1], 2);
            double ms = Double.POSITIVE_INFINITY;
            for (int run = 0; run < 5; run++) {
                long start = System.nanoTime();
                Recolour recolour = new Recolour(palette, build, Recolour.Mode.SHADING);
                for (Recolour.Look l : build) recolour.pick(l);
                ms = Math.min(ms, (System.nanoTime() - start) / 1e6);
            }
            System.out.printf("recolour: %d build kinds into %d palette blocks: %.1f ms%n", size[0], size[1], ms);
            assertTrue(ms < 50, size[0] + " into " + size[1] + " took " + ms + " ms");
        }
    }
}
