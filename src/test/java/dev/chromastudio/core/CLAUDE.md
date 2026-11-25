## Reference data

- `ColorTest.oklabMatchesOttossonsTable` uses the examples in Ottosson's OKLab post, rounded there to three decimals. The OKLab primaries and red in OKLCH come from CSS Color 4 and Ottosson's converter. The Lab primaries in `ColorTest` and `MetricTest` come from Bruce Lindbloom. The XYZ of red comes from the standard sRGB matrix.
- `MetricTest` takes its ΔE 2000 pairs from the supplementary data for Sharma, Wu and Dalal (2005).
- `TextureStatsTest.badgeThresholds` uses Minecraft 26.3 measurements of black concrete, stone, oak planks, cobblestone and bookshelf.
- `RecolourTest` uses linear-light means of vanilla texture PNGs. `DESERT` and `WEIGHTS` follow the Desert and Nether presets. `NETHER` omits magma and shroomlight, and `WEIGHTS` leaves crimson planks at 1.
- `ShadeStepsTest.BLOCKS` uses Minecraft 26.3 index values. Its `DESERT` uses the faces the brush averages and omits falling sand.

## Scenarios

- `ShadeStepsTest.commonBlocksRoundTripAtEveryStepSize` allows exceptions when multiple blocks need the same partner. Terracotta and pink terracotta share red terracotta. Oak and jungle share spruce at larger steps. At 0.100 and 0.105 cobblestone darkens to cobbled deepslate and lightens back to tuff.
- `RecolourTest.aHouseWithMoreRoofThanWallStillPutsItsWallsOnAMainBlock` uses an in-game build with 98 dark roof blocks and 64 blocks of wall. The largest shade alone would choose the roof.
- `HotbarWritesTest.aSetThatQuietlyDoesNothingIsCaughtByReadingBack` models an Axiom write to the active hotbar outside creative.
- `LineBreaksTest` preserves two lines ImGui wrapped badly. It split the number from its counter in the gradient note and orphaned a Japanese full stop in the eyedropper note.
