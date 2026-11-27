## Reference data

- `ColorTest` uses Ottosson's OKLab examples rounded to three decimals. The OKLab primaries and OKLCH red come from CSS Color 4 and Ottosson's converter. Lab primaries in `ColorTest` and `MetricTest` come from Bruce Lindbloom. XYZ red comes from the standard sRGB matrix.
- `MetricTest` takes its ΔE 2000 pairs from the supplementary data for Sharma, Wu and Dalal (2005).
- `TextureStatsTest.badgeThresholds` uses Minecraft 26.3 texture measurements. `RecolourTest` uses linear-light means of vanilla textures and weights adapted from the Desert and Nether presets.
- `ShadeStepsTest.BLOCKS` comes from the Minecraft 26.3 index. Its Desert fixture uses the faces the brush averages and omits falling sand. Some round trips have two valid starting blocks that share a partner.

## In-game cases

- The house in `RecolourTest` has more roof than wall. Taking the most common shade as the main material would let the roof decide the walls.
- `HotbarWritesTest` models Axiom silently ignoring active hotbar writes outside creative. `LineBreaksTest` uses text ImGui split between a number and its counter or before a Japanese full stop.
