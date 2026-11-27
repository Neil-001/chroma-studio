## Threads and creative tabs

- `BlockIndex.Built` publishes the version and entries together so readers see one build. Read models, colours, resources and selected packs in one client-thread task so they come from one reload. Use one `IndexOptions` snapshot for the texture cache key and the builder.
- `TintContext.HERE` reads the open world and belongs on the client thread. Create a `TintContext.at` there for a worker search so it holds one world and position throughout.
- `BlockIndex` reads creative tabs on the client thread after joining, when the world's registries are available. Each loader calls `worldJoined` so the loading screen covers the work.
- The game refills the creative tabs and its search only when features, operator rights or registry identity change. `BlockIndex.tabRegistries` copies the world's registries under a fresh identity so the game's search gets populated even when the other inputs match.
- `BlockIndex.listedBlocks` fills the tabs with every feature and operator items enabled so modded and experimental tabs count. Count only category tabs other than Operator Utilities. The search tab repeats operator items.
- Maintain `CreativeOnly` by hand. The game has no flag for blocks unobtainable or unplaceable as they are in survival, and creative tab membership doesn't tell you either.

## Models and states

- `IndexBuilder.Look` compares face textures and tint indexes so stairs of different orientations can share an entry. Waterlogging leaves those textures alone, so indexing each wet state would crowd out visible variants.
- One `Entry` can cover states with distinct tints, such as redstone at different power levels. Use the actual state for tinted queries.
- `Entry.blockEntityRendered` includes bells because their model only draws the frame. Particle textures approximate the appearance of block entity models.
- `Entry.BY_PREFERENCE` breaks ties in favour of the top, the face builders most often see.
- In `Kind.BY_CLASS`, put subclasses before their parents when they need their own kind. Stained panes inherit from iron bars. Copper grates share glass's `WaterloggedTransparentBlock` class and need light and shape checks.
- In `MaterialSets.SUFFIXES`, try the longer suffix first when one ends in another, as with `_wall_hanging_sign` and `_sign`.
- Modded shapes and tint sources can throw without a real world. `IndexBuilder.solid` then assumes solidity, `Kind.fullCube` declines to count a full cube, and `Tints.colour` uses no tint.
- NeoForge's replacements for `FluidModel.tintSource` and `getLightEmission` expect context the index lacks. Keep the vanilla queries in `IndexBuilder.tintSource` and `Kind.lightEmission`.

## Tints and textures

- `Tints` compares biomes at one position because tints such as swamp grass vary by x and z. `FaceLayers` bounds its tint cache because a walk can create a new tint context every few blocks.
- Derive stats from `Textures.Frame` on both cache hits and image reads. `Textures.Frame.stats` represents partial coverage as filled pixels in an 8 by 8 cell because `TextureStats` would discard low 8-bit alpha.
- Raise `TextureCache.FORMAT` when frame calculation changes. Pack order matters because later packs override earlier ones. Refresh bypasses the cache so it sees packs edited in place.
