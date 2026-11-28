## Threads and creative tabs

- `BlockIndex.Built` publishes version and entries together. Read models, colours, resources and selected packs in one client-thread task, and use one `IndexOptions` snapshot for the cache key and builder.
- `TintContext.HERE` belongs on the client thread. Create `TintContext.at` there for a worker search to hold one world and position.
- `BlockIndex` reads creative tabs after joining, when world registries are available. Both loaders call `worldJoined` so the loading screen covers the work.
- `BlockIndex.tabRegistries` copies registries under a fresh identity to force the game's creative search to populate when its other inputs haven't changed.
- `BlockIndex.listedBlocks` enables every feature and operator items for modded and experimental tabs. Count category tabs except Operator Utilities. The search tab repeats operator items.
- Maintain `CreativeOnly` by hand. Neither a game flag nor creative tab membership identifies blocks unobtainable or unplaceable as they are in survival.

## Models and states

- `IndexBuilder.Look` groups states by face textures and tint indexes. Waterlogging leaves these unchanged, so indexing wet states would crowd out visible variants.
- An `Entry` can cover states with distinct tints. Use the actual state for tinted queries.
- `Entry.blockEntityRendered` includes bells because their model only draws the frame. Particle textures approximate block entity models.
- Put subclasses before parents in `Kind.BY_CLASS` when they need their own kind. Copper grates share glass's class and need light and shape checks.
- Put longer suffixes first in `MaterialSets.SUFFIXES` when one ends in another.
- Modded shapes and tint sources can throw without a real world. `IndexBuilder.solid` assumes solidity, `Kind.fullCube` declines to count a full cube, and `Tints.colour` uses no tint in that case.
- Keep vanilla queries in `IndexBuilder.tintSource` and `Kind.lightEmission` because NeoForge's replacements expect context the index lacks.
- 1.21.1 has no tint sources. `TintSource.of` asks `BlockColors` with the state, world, position and tint index, as the game does for a quad, and answers -1 for no tint. Water's tint comes from the same block colours.
- 1.21.1 has no fluid models. `IndexBuilder.vanillaFluid` knows water and lava, whose tops and bottoms show the still texture and whose sides show the flowing one. A modded fluid is read like a block.

## Tints and textures

- `Tints` compares biomes at one position because tints can vary by x and z. `FaceLayers` bounds its cache because walks can create many tint contexts.
- Derive stats from `Textures.Frame` on cache hits and image reads. `Frame.stats` represents partial coverage as filled pixels because `TextureStats` discards low 8-bit alpha.
- `Textures.read` takes animation metadata that lists no frames as every frame in turn, as the game does.
- Raise `TextureCache.FORMAT` when frame calculation changes. Refresh bypasses the cache to see packs edited in place.
