Palettes and gradients: their files and share strings, generation, analysis, recolouring, shade steps, and the plans and scripts of the tools in Axiom's editor.

## Axiom's Script Brush

`AxiomScript` writes Lua for the player to paste. What it leans on, as Axiom 6.1 has it:

- The brush compiles a script once a stroke and runs it for each position with the globals `x`, `y` and `z` set. Every other global survives between positions, so each script builds its tables once, on its first run, in globals of its own name. `scatter` keeps its blocks in `chroma_skipped`, apart from the gradient script's, because a script run in the same brush would otherwise find the other's table.
- `blocks.stone` is a block's id and `blocks.create.casing` one of another mod's, either nil for a block the game lacks. A script returns a block, or nil to leave the position alone.
- `$int(Name, default, min, max)$` and its kin anywhere in the text become controls in the brush's window. So `AxiomScript.safe` and `AxiomScript.quote` keep every `$` from a name out of the script.
- Axiom's `setBlock` doesn't ask the tool masks. The recolour script sets a door's other half itself, which may lie past the edge of the stroke, so when a mask cuts a door in two neither half is left behind.
- Caves are filled with cave air, so the Paint air box of the scatter and gradient scripts covers cave air as well as air. The scripts also name void air, but Axiom 6.1.5's Script Brush doesn't call a script for a position that holds void air, so neither script fills void air in Axiom.

Length:

- A server lets a player without import rights run a script of at most `AxiomScript.SERVER_LIMIT` characters, as Axiom counts them: without whitespace and the keywords in `NOT_COUNTED`. Servers count comments too, so the scatter and gradient scripts carry only their title. A plain block of the game is named as `blocks.name`, and the blocks a script leaves alone are one string of names, which counts only by its letters. Only another mod's block, or one with properties, needs the function that finds it.
- The game copies text through a buffer of 64 KiB. A recolour of every block runs to a thousand rows or more, so `AxiomScript.recolour` writes each block it puts down once, in a list, and the rows as one string of `from=number` pairs.
- A recolour script without a door row leaves the door code out.

What a script leaves alone:

- Scripts ask no block tags. A tag is a world's data, which its data packs may change, and a script may run in another world than the one it was made in.
- Whether a block has a property doesn't depend on its state, so the scripts ask `getBlockProperty` of the block's id, from `getBlock`.
- `scatter` matches the palette brush's Scatter: it fills air unless Paint air is cleared, and leaves alone either half of a door or tall plant, a block with water in it, and every block `kept` covers.
- `gradient` matches the gradient tool, but a script can't ask a block's shape or whether it holds anything. So it goes by what it can ask: no collision, a property in `KEPT_BY_PROPERTY` or `kept`, or a name in `kept`. Unlike the tool, it paints any solid block the brush reaches, not only one build. Every step must be a full block with nothing inside, which `AxiomScripts.gradient` sees to.
- `recolour` changes a block with water in it only into one that keeps the water, as every tool does.

## Plans and sending

- `QueuedChanges` holds the one rule for sending a tool's changes. `check` works the plan out again, by the same steps, from the world, the tool masks and the tool's options as they are at that moment, and the plan may be sent only if that gives exactly the same. Whatever could change a decision then refuses the lot without having to be named. Nothing is ever sent in part.
- A plan holds the positions it left alone as well as those it changes, each with the block it saw there. A tool that doesn't look at the block it replaces, as Scatter doesn't, would make the same plan over a chest placed under a held stroke, so what was seen has to count.
- `check` reads where the player is, the index's version and whether the editor is on again after the plan is made. Making the plan can take a while on a big build, and the block index is rebuilt on its own thread and may be published meanwhile.
- `QueuedChanges` compares blocks with `equals`. The game's block states are each one object, so that is identity.

## Recolour

`Recolour` decides the blocks for both the Script Brush scripts and the palette brush, so the two give the same blocks. `Mode.SHADING` exists because picking each block's closest colour alone makes most of a build one block when the palette is small or far from the build's colours, and its shading is lost.

- A block the palette already has stays as it is.
- The main material is the shade there is most of, but the build's darkest or lightest shade only when it is more than half the build; otherwise the largest shade between them. So a house's walls, not its dark roof, are its main material. When the weights don't tell one shade apart, as when every block in the game may change and each counts once, there is no main material unless the build is one shade. Then, with at least three shades and no more shades than eligible palette blocks, the largest shade between the darkest and lightest goes on the palette's main block, when the blocks the palette already has leave room for it. With more shades than blocks, the shades are shared out by weight.
- When the palette has the main material, every other material keeps its difference from it in lightness and in warmth. So a grey trim on a stone brick wall takes the palette's next grey rather than its yellow accent.
- When the palette lacks it, the main material goes on the palette's main block, and every other material keeps its place from darkest to lightest, with the build's darkest and lightest on the palette's.
- The order from darkest to lightest never turns round.
- See-through blocks are kept apart from solid ones, so windows stay windows.
- `Recolour.SHARE` is one just-noticeable step in OKLab. Materials a builder mixed on purpose, such as cobblestone and andesite, then keep blocks of their own while the palette has a near one for each.
- Two neighbouring shades may share a block to put the walls on a heavier one, as a builder puts stone walls on stone bricks, not on planks. Each sharing pair adds a table as large as the first, so beyond one pair the shades either side are shared out by weight instead.
- The cost tables in `Recolour` keep only the places where each row's running minimum drops. Adding the same fit to each can round two costs to the same total, which must let the earlier block win, so the first place whose total rounds to the least is found by halving.
- A look with weight 0 is in the build without adding to how much there is, as a stair is when its material's full block already counts it. When no look of a class weighs anything, each counts as one.
- A shade's amount is the square root of its looks' weights, so a build's bulk material counts for more without the rest counting for nothing.

## Shade steps

- `ShadeSteps` pairs blocks into ladders, closest pairs first, each block with at most one lighter and one darker partner, so a paired step lighter then darker returns to where it started. A block whose partner is taken or not allowed steps to the closest allowed block instead, and that step may not undo exactly. Axiom's shade brush asks it for each block it touches.
- A plain block never steps to a mossy, cracked, chiseled or infested one, so a wall brushed lighter never grows moss. A decorated block may still step to a plain one.
- Among a palette's blocks a step reaches further, because a palette rarely has a block one small step from each of its others: Desert's next block darker than sandstone is yellow terracotta, a long step that is flatter and more orange. It still never turns a grey to a colour, stone to wood, or a plain block to a decorated one.
- The limits are measured on Minecraft 26.3's blocks, in OKLab:
  - `MAX_HUE` and `CHROMA_SHARE`: orange to red terracotta is about 0.035 in hue, red concrete to red terracotta 0.03 in chroma, and stone bricks to tuff bricks 0.01. A grey must not take on colour: polished blackstone bricks to nether bricks is 0.021, and white terracotta to smooth stone 0.044.
  - `MAX_SPREAD`: a flat block such as concrete and a textured one such as planks are more than 0.04 apart in spread.
  - `OTHER_FAMILY` and `OTHER_SHAPES`: tuff bricks are a nearer step from stone bricks than tuff is, and cracked stone bricks are a worn stone brick rather than the next material along.
  - `SHORTEST` and `LONGEST`: below half the step asked for the change can hardly be seen, and past two and a half times it is a jump, not a shade.
  - `PALETTE_MAX_HUE` and `PALETTE_CHROMA_SHARE`: sandstone to yellow terracotta is 0.025 in hue and 0.063 in chroma, and to red sandstone 0.064 and 0.075, while sandstone to prismarine, a tan to a teal, is 0.088 in hue.
- `ShadeSteps.next` takes the closest step over a partner that it beats by far. Such a partner was what was left once better pairs were made, and the step to it would look wrong.

## Gradients

- `Gradient` solves over each step's `NEAREST` closest candidates, and at least as many as there are steps, which loses nothing: some best assignment gives every step one of its n nearest, since a step given anything further always has one of those n going spare to swap for. It keeps thousands of candidates down to a few hundred.
- `Gradient` sorts with a stable sort, so equal costs keep list order and the pool and answer are the same on every run.
- `GradientLine.STRAIGHTEN` is about 20 degrees. Clicking the top and bottom of a tower rarely hits the same column: on a tower 16 high, clicking the top at one corner and the bottom at the other leans a quarter. A leaning line tilts the planes between steps and cuts the build's corners off at the ends. Builders draw a diagonal they mean at 30 degrees or more.
- `GradientLine.dither` uses a blue noise table, not random numbers, which clump into blots of one step and holes in the other. The table is a cube tiled over the world, each tile shifted by its own amount so a long wall doesn't repeat. It is made the same way on every machine when first needed rather than shipped.
- `GradientLine.EDGE` lets a block's centre sit up to 1e-6 past an end plane or the reach and still count, so rounding doesn't decide a block that sits exactly there and the ends give the same answer either way round.

## Build sampling and search

- A kind the player removes from a `BuildSample` stays out until it is put back or the sample is cleared, so sampling on doesn't bring it back unasked. Once it stands nowhere sampled it is forgotten, so it can't stay out unseen, and sampling it later counts it again.
- `BuildSearch` counts blocks touching at an edge or a corner as joined, so a staircase of blocks or a thin diagonal spire holds together. It stops at a cap so a build that runs on into the landscape can't hold up the game.
- `BuildSearch` marks positions in a bit set of 16 by 16 by 16 sections. A set of boxed positions spends most of the search's time making and hashing them, and this is several times quicker.
- `WeightedPick` gives the same item for the same seed and position, so a brush that passes over a block twice in one stroke doesn't change its mind.

## Saved files and strings

- `PaletteCodec` and `GradientCodec` refuse a file without their version, or with another, rather than guess at it. Within a version they give a missing or mistyped field its default and ignore unknown fields, so a file edited by hand still loads.
- `StoredGradient` keeps `easing` and `arc` as text, so a file from a later version with a new one still loads. `MIN_STEPS` and `MAX_STEPS` match the limits of the `gradientSteps` setting in `ModSettings`.
- A share string's entries end at a slash, so `ShareString` writes a slash in a block id's path as `%2F`. Block ids can't hold a percent sign, so it can't be mistaken for anything else.
- `ShareString` and `PaletteCodec` refuse with an `UnreadablePalette`, which carries a `Reason` and its arguments instead of an English message, because the core can't translate. The client shows it as the text under `palettes.chromastudio.unreadable.` and the reason's name in lower case, and `LangTest` checks that every reason has one.

## WorldEdit

- `WorldEditPattern` gives no entry 0%, which WorldEdit would never place. Past a hundred entries whole percentages can't do that, so the weights themselves stand in, which WorldEdit reads the same way.

## Analysis

- `Analyzer.KEYS` lists every key a finding can have, so `LangTest` can check the language files against it. The filter tokens are mapped to filter presets by the window.
