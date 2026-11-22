Tests of the `dev.chromastudio.core` packages.

A reference number that a test works out from its own inputs is not listed here. The numbers below come from outside the tests, so change them only against their source.

## Reference data

- `ColorTest.oklabMatchesOttossonsTable` is the table of example pairs in Ottosson's OKLab post, XYZ in and OKLab out, which he rounds to three decimals.
- `ColorTest.oklabOfThePrimaries` has the sRGB primaries as CSS Color 4 and Ottosson's own converter give them. `ColorTest.oklchIsThePolarForm` has red as CSS Color 4 gives it in oklch, 62.8% 0.2577 29.23.
- `ColorTest.labOfThePrimaries` is from Bruce Lindbloom's tables, and the XYZ of red in `ColorTest.theTwoOklabRoutesAgree` from the standard sRGB matrix. `MetricTest` takes red and green in CIELAB from the same tables.
- `MetricTest`'s ΔE 2000 pairs are the test data published with Sharma, Wu and Dalal, "The CIEDE2000 color-difference formula: implementation notes, supplementary test data, and mathematical observations" (2005): L, a and b of each colour, then ΔE00.
- The spreads in `TextureStatsTest.badgeThresholds` were measured on Minecraft 26.3's black concrete, stone, oak planks, cobblestone and bookshelf. They are the anchors of `Badge`'s thresholds.
- `RecolourTest`'s colours are the mean colours of the vanilla blocks' textures in linear light, read from their PNGs. `DESERT` and `WEIGHTS` follow the bundled Desert and Nether presets. `NETHER` is the Nether preset without its magma block and shroomlight, and `WEIGHTS` leaves its crimson planks at 1.
- `ShadeStepsTest.BLOCKS` holds Minecraft 26.3's own blocks as the index reads them: the OKLab lightness, chroma and hue of each texture's mean, its spread, and whether its material comes in stairs or slabs. Its `DESERT` is the Desert preset's full blocks with the faces the brush averages. Sand falls, so it is left out.
- `ClipboardSizeTest`'s limit comes from LWJGL's 64 KiB stack, and the text needs a terminating zero too.
- `AxiomScriptTest` finds controls by the pattern Axiom 6.1 uses: anything between two dollar signs on one line.

## Scenarios

- `ShadeStepsTest.commonBlocksRoundTripAtEveryStepSize` accepts three round trips that don't return, each a block that shares its one neighbour that way with another block, which the closer pair keeps. Terracotta and pink terracotta both darken to red terracotta, which lightens to pink. Oak and jungle planks both darken to spruce at the larger steps. At 0.100 and 0.105, cobblestone darkens to cobbled deepslate, which lightens to tuff. The test permits this above 0.095.
- `RecolourTest.aHouseWithMoreRoofThanWallStillPutsItsWallsOnAMainBlock` uses the counts of the palette brush's stone house as it stands. Its dark roof is the largest part, 98 blocks against the walls' 64 of stone, cracked and mossy stone bricks, so the main material can't simply be the largest shade.
- `HotbarWritesTest.aSetThatQuietlyDoesNothingIsCaughtByReadingBack` copies Axiom's held hotbar outside creative mode: the call returns and the slot is unchanged.
- `LineBreaksTest`'s two Japanese lines are the gradient tool's note, which ImGui broke as "…ため、2" and "個の…", and an earlier wording of the eyedropper's note, which ImGui left a lone "。" of.
