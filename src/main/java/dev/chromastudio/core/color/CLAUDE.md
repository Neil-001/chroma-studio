## Minecraft colours

- Minecraft scales a texture before tinting it and applies tint and shading to stored channel values. `Color.tinted`, `Distribution.tinted`, `TextureStats.tinted` and `Shading` match that arithmetic. Average colours in linear light, as colours blend at a distance.
- Keep the first six `Face` constants in Minecraft `Direction` order. Client code converts by ordinal.
- `MapColors.BASE` comes from Minecraft 26.3's `MapColor` fields in id order. Id 0 is transparent on maps, so leave it out of matches.
- `TextureStats` uses full-scale alpha for stats. A faint pixel can contribute even if its thumbnail alpha rounds to zero. Choose or combine the animation frames before calling `TextureStats.of`.
- `Kmeans` uses a fixed seed so cached and rebuilt indexes agree. `FlatNearest` must match single-colour `Distribution.emd` in OKLab.

## Transport

- `Distribution.transport` uses exact comparisons for Dijkstra and clamps negative rounding to zero. A predecessor settles before its successor. A tolerance on ties can create a predecessor cycle.
- Treat the source and sink as having their own potentials so their edges have nonnegative reduced costs.
- Allow any positive residual weight or flow. A floor can move tiny weights the wrong way or leave them unmoved. Subtracting the bottleneck empties an edge exactly, but weights below half an ulp of a larger weight can vanish on subtraction.
- `Distribution.transport` has no proved path bound for real weights and throws at `(n + m) · n · m` paths if unfinished. Four million fuzzed inputs with tied and near-tied costs and weights down to 2⁻⁶⁰ used at most 29. Balance and reduced-cost checks certify the plan within `OPTIMAL_SLACK`.
- `Distribution.emd` can fall back to the independent plan, a finite upper bound. Infinite costs would give `Gradient.assign` NaN prices and it would never finish. The default handler rethrows for tests. `ChromaStudioClient.init` logs the first failure of the session.

## Calibration and sources

- `Badge.GRAINY_FROM` and `BUSY_FROM` use Minecraft 26.3 spreads at 16 texels. The thresholds split concrete (0.002 to 0.005) from stone (0.037) and cobblestone (0.099) from bookshelf (0.167). Spread still puts some wool (0.023 to 0.054), gravel (0.058) and granite (0.074) in the grainy band.
- `Color.GAMUT_SLACK` covers matrix rounding and stays under half an 8-bit step. The monochromatic `Harmony` limits avoid lightnesses where nearly every hue clips. `Viewing.PIXELS_PER_RESOLVED_TEXEL` uses four pixels as the point where neighbours cease to be distinct at a normal monitor distance.
- `Oklab` uses [Björn Ottosson's matrices](https://bottosson.github.io/posts/oklab/) and takes XYZ with a D65 white of Y = 1. `Metric.ciede2000` follows Sharma, Wu and Dalal (2005) with all three weights at 1.
