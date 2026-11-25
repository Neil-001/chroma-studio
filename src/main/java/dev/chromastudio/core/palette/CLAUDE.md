## Axiom's Script Brush

- Axiom 6.1 compiles once per stroke and sets `x`, `y` and `z` at each position. Other globals persist. Give each `AxiomScript` its own global table names so one script can't pick up another's tables.
- Axiom reads dollar-delimited controls anywhere in Lua source. Keep player text out of that syntax through `AxiomScript.safe` and `quote`. `blocks.stone` looks up a vanilla block and `blocks.create.casing` a modded one. Missing blocks resolve to nil, which leaves the position alone when returned.
- Axiom's `setBlock` bypasses masks. The recolour script sets the other half of a door even outside the stroke so a mask can't separate the halves.
- Paint air covers cave air as well as air. The scripts also include void air, but Axiom 6.1.5 never calls the script for a position containing it.
- Servers limit script length for players without import rights. In 6.1.5, Axiom counts comments and strips whitespace and `NOT_COUNTED` keywords even inside words. Keep generated source short. Recolour also shares destination entries and packs mappings into a string so large palettes fit the clipboard.
- Use block names and properties for script exclusions. Tags can vary between the world where a script was made and the world where it runs. A property exists on every state of its block, so checking the default state is enough to detect it.

## Plans

- `QueuedChanges.check` allows a plan only if recomputing it from the current world, masks and options gives exactly what the player saw. Include unchanged positions and their original states so a chest placed under a held stroke can invalidate it. Send the whole plan or none.
- Check the origin again after recomputing. The index can publish a build from its own thread during the check.

## Recolour

- `Recolour.Mode.SHADING` preserves the lightness order. Closest-colour matching alone can turn a build into one material if the palette is small or its colours are far away.
- The darkest or lightest shade needs more than half the weight to be the main material. Otherwise prefer an interior shade so a large dark roof doesn't decide the walls. Uniform weights usually give no main material, but an interior shade can anchor an assignment if there are enough palette blocks and the kept blocks leave room.
- A kept main material gives the other shades a reference for lightness and warmth. Without it, place the main material on the palette's main block and spread the other shades across its lightness range.
- `Recolour.SHARE` is one just-noticeable OKLab step. Similar materials the builder mixed, such as cobblestone and andesite, can then keep distinct shades when the palette has room.
- Each neighbouring pair allowed to share adds a cost table as large as the original. `Recolour` searches one such pair to fit the main shade and distributes the shades on either side by weight.
- `Recolour.matched` stores running-minimum drops. Adding the same fit can round distinct costs to a tie. Find the earliest minimum to preserve the same answer as an uncompressed table.
- Zero weight allows a shape in the build without counting its material twice. When every look of a class has zero weight, count each once. Take the square root of shade weights so bulk materials have more influence without swamping the rest.

## Shade steps

- `ShadeSteps` pairs lighter and darker steps so a matched pair can be reversed. A closer allowed step can beat a leftover partner that is too far away, at the cost of that round trip. A plain block never gains moss, cracks, carving or infestation.
- A sparse palette needs longer steps than the full index, such as sandstone to yellow terracotta in Desert. Keep the grey-to-colour, material and decoration checks.
- The limits use Minecraft 26.3 measurements. Orange to red terracotta differs by 0.035 in OKLab hue, and red concrete to red terracotta by 0.03 in chroma. Stone bricks to tuff bricks changes chroma by 0.01, while polished blackstone bricks to nether bricks changes it by 0.021 and crosses from grey to colour. Concrete and planks differ by more than 0.04 in spread. Sandstone's hue differences to yellow terracotta, red sandstone and prismarine are 0.025, 0.064 and 0.088.
- `OTHER_FAMILY` and `OTHER_SHAPES` favour tuff bricks over tuff as a step from stone bricks. `SHORTEST` and `LONGEST` exclude changes too faint to see or too large to read as a shade.

## Gradients and sampling

- `Gradient` can restrict a solution for n steps to each step's n nearest candidates without losing an optimum. At least one of those n is free whenever a step has a farther block. Keep the sort stable so tied costs preserve input order.
- `GradientLine.STRAIGHTEN` lets clicks on different corners of a tower make level bands. Tilting an almost vertical line would cut the end corners off. `EDGE` keeps centres on the boundary from falling out through rounding.
- `GradientLine.dither` uses deterministically generated blue noise to avoid the clumps of independent random draws. Shift each tile so the same pattern doesn't repeat across a wall.
- `BuildSample` keeps a removed kind out while any sampled position still has it. Forget it when the last position goes so it can be sampled again.
- `BuildSearch` joins edges and corners so diagonal builds stay together. Its search cap bounds the cost when a build joins the terrain. Section bit sets avoid boxing and hashing every position.
- `WeightedPick` is stable for a seed and position so a second pass during the same stroke picks the same block.

## Saved files and commands

- `PaletteCodec` and `GradientCodec` require a supported version. Within that version, default missing or mistyped fields and ignore unknown ones so hand-edited files still load. `StoredGradient` keeps easing and arc names as text so unknown values survive a load.
- `ShareString` escapes slashes in block ids as `%2F` because slash ends an entry and ids can't contain percent signs.
- `UnreadablePalette` carries a reason and arguments so the client can translate it, with keys checked by `LangTest`. `GradientCodec` errors only reach the log.
- `WorldEditPattern` avoids zero percentages, which WorldEdit never places. With more than 100 entries, use the weights directly because whole percentages can't all be positive.
