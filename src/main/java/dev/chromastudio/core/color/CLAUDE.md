Colour maths for the block index and the tools: colour spaces, distances, harmonies, distributions and texture statistics.

## How the game draws colours

- The game tints a texture by multiplying each 8-bit channel by the tint's channel over 255, after it has scaled the texture down. It shades a face by multiplying the stored channel values, not linear light. `Color.tinted`, `Distribution.tinted`, `TextureStats.tinted` and `Shading` do the same, or a computed colour would not match the screen.
- The first six constants of `Face` are in the order of Minecraft's `Direction`. Client code converts between them by ordinal.
- `MapColors.BASE` is read from the static fields of `net.minecraft.world.level.material.MapColor` in Minecraft 26.3, in id order. Id 0 is `NONE`, which a map leaves transparent, so `MapColors.nearest` never returns it: no block shows as it.

## Averages and clusters

- `TextureStats` and `Distribution.mean` average in linear light, as colours blend when seen from far away.
- `TextureStats.of` takes one frame. For an animated texture the caller first averages its frames or takes the first one, as the settings say.
- `TextureStats` keeps the exact alpha of each full-scale pixel for its statistics. A faint pixel's alpha can round to nothing in the 8-bit thumbnail while it still counts.
- `Distribution.of` divides the weights by the largest before summing them, so very large weights don't overflow.
- `Kmeans` uses a fixed seed, so one image always gives the same clusters and a cached index matches a rebuilt one.
- `FlatNearest` must answer as `Distribution.emd` in OKLab does. Against a single colour that is the weighted mean distance to the texture's colours, which is why it can keep them as plain numbers.

## Transport

- `Distribution.transport` finds each augmenting path with Dijkstra over costs reduced by potentials, with exact comparisons and rounding below zero clamped to zero. A node's predecessor is always settled before it, so the walk back along a path ends even when costs differ by less than rounding. Comparing costs with a tolerance would let nearly tied costs leave a cycle in the predecessors.
- Every path in `Distribution.transport` carries more than `EMPTY` of supply, so the loop ends after at most the total supply over `EMPTY` paths. Before it answers, it checks the potentials against the flow as a proof of optimality and throws if they disagree by more than `OPTIMAL_SLACK` of the largest cost.

## Threads

- `FlatNearest` never changes after it is made, so any number of threads may ask it at once.
- `Color` works out its OKLab and CIELAB values on first use without a lock. A race only repeats the work, since both values are immutable.

## Measured numbers

- `Badge.GRAINY_FROM` and `Badge.BUSY_FROM` were set from Minecraft 26.3's own textures. Each is about midway between the anchors it parts: concrete from stone, and cobblestone from bookshelf. Spread is at 16 texels; the other columns are the spread after one halving, and the share of the second largest colour cluster with its OKLab distance from the largest.

  ```
                       spread           at 8             second cluster
  concrete (all)       0.002 to 0.005                    none
  terracotta (all)     0.004 to 0.008                    none
  wool (all)           0.023 to 0.054   0.013 to 0.031   0.25 at 0.03 to 0.08
  stone                0.037            0.019            0.28 at 0.04
  gravel               0.058            0.028            0.24 at 0.08
  granite              0.074            0.036            0.35 at 0.08
  oak planks           0.086            0.051            0.25 at 0.08
  cobblestone          0.099            0.065            0.29 at 0.12
  bookshelf            0.167            0.101            0.24 at 0.20
  ```

  None of these figures tells wool from stone, or gravel and granite from planks and cobblestone.
- `Color.GAMUT_SLACK` lets linear light this far outside 0 to 1 count as in gamut. It covers the rounding in the published matrices and is under half an 8-bit step everywhere.
- `Harmony.MIN_LIGHTNESS` and `MAX_LIGHTNESS` bound the monochromatic steps because beyond them nearly every hue clips to black or white.
- `Viewing.PIXELS_PER_RESOLVED_TEXEL` is 4 because at a normal distance from a monitor that is about where the eye's contrast sensitivity stops telling neighbouring texels apart.

## Sources

- `Oklab` uses Björn Ottosson's published matrices (https://bottosson.github.io/posts/oklab/). `Oklab.ofXyz` takes XYZ with a D65 white of Y = 1, the form his reference table is given in.
- `Metric.ciede2000` follows Sharma, Wu and Dalal (2005) with all three weights at 1.
