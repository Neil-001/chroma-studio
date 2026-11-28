## Axiom's Script Brush

- Axiom 6.1 compiles once per stroke. Coordinates change at each position, but other globals persist, so give each `AxiomScript` its own table names.
- Axiom reads dollar-delimited controls anywhere in Lua source. Keep player text out of that syntax through `AxiomScript.safe` and `quote`. Missing blocks resolve to nil and leave the position alone.
- Axiom's `setBlock` bypasses masks. The recolour script sets the other half of a door even outside the stroke so a mask can't separate the halves.
- Paint air covers cave air and void air, though Axiom 6.1.5 never calls the script for void air.
- Servers limit script length for players without import rights. In 6.1.5, Axiom counts comments and strips whitespace and `NOT_COUNTED` keywords even inside words. Keep generated source short so large palettes fit.
- Use block names and properties for script exclusions. Tags can vary between the world where a script was made and the world where it runs. A property exists on every state of its block.

## Plans

- `QueuedChanges.check` allows a plan only if recomputing it from the current world, masks and options gives exactly what the player saw. Include unchanged positions and their original states so a chest placed under a held stroke can invalidate it. Send the whole plan or none.
- Check the origin again after recomputing. The index can publish a build from its own thread during the check.

## Recolour

- `Recolour.Mode.SHADING` preserves lightness order. Closest-colour matching alone can turn a build into one material when the palette is small or its colours are far away.
- The darkest or lightest shade needs more than half the weight to be the main material. Otherwise prefer an interior shade so a large dark roof doesn't decide the walls. Use a kept main material as the reference for lightness and warmth. Otherwise use the palette's main block and lightness range.
- `Recolour.SHARE` is one just-noticeable OKLab step. Similar mixed materials, such as cobblestone and andesite, can keep distinct shades when the palette has room.
- Each neighbouring pair allowed to share adds a cost table as large as the original, so `Recolour` searches one pair.
- `Recolour.matched` stores running-minimum drops. Adding the same fit can round distinct costs to a tie, so find the earliest minimum to match an uncompressed table.
- Zero weight allows a shape without counting its material twice. When every look of a class has zero weight, count each once. Take the square root of shade weights so bulk materials have influence without swamping the rest.

## Shade steps

- `ShadeSteps` pairs lighter and darker steps for reversibility. A closer allowed step can beat a leftover partner that is too far away. A plain block never gains moss, cracks, carving or infestation.
- Sparse palettes need longer steps than the full index, such as sandstone to yellow terracotta in Desert. Keep the grey-to-colour, material and decoration checks.
- The limits come from Minecraft 26.3. Orange and red terracotta differ by 0.035 in OKLab hue. Red concrete and red terracotta differ by 0.03 in chroma. Stone bricks to tuff bricks changes chroma by 0.01. Polished blackstone bricks to nether bricks changes it by 0.021 and crosses from grey to colour. Concrete and planks differ by more than 0.04 in spread. Sandstone differs in hue from yellow terracotta, red sandstone and prismarine by 0.025, 0.064 and 0.088.
- `OTHER_FAMILY` and `OTHER_SHAPES` favour tuff bricks over tuff as a step from stone bricks. `SHORTEST` and `LONGEST` exclude changes too faint to see or too large to read as a shade.

## Gradients and sampling

- `Gradient` can restrict each of n steps to its n nearest candidates without losing an optimum. At least one is free whenever a step has a farther block. Keep the sort stable so tied costs preserve input order.
- `GradientLine.STRAIGHTEN` lets clicks on different corners of a tower make level bands without cutting off the ends. `EDGE` prevents rounding from excluding centres on the boundary.
- `GradientLine.dither` uses blue noise to avoid random clumps and shifts each tile to avoid repeating patterns.
- `BuildSearch` joins edges and corners so diagonal builds stay together. Its cap bounds the cost when a build joins the terrain.
- `WeightedPick` is stable for a seed and position so a second pass during the same stroke picks the same block.
- `Analyzer.Finding.blocks` indexes the colours passed to `analyse`. The client leaves out blocks it can't measure, so map the indices through the list it built.

## Saved files and commands

- `PaletteCodec` and `GradientCodec` require a supported version. Within it, default missing or mistyped fields and ignore unknown ones so hand-edited files still load. `StoredGradient` keeps easing and arc names as text so unknown values survive a load.
- `UnreadablePalette` carries a reason and arguments for translation, with keys checked by `LangTest`. `GradientCodec` errors only reach the log.
- `WorldEditPattern` avoids zero percentages, which WorldEdit never places. With more than 100 entries, use the weights directly because whole percentages can't all be positive.
