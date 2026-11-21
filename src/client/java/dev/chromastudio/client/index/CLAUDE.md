The block index: every block's colours per face, read from the game's models and textures, and the search for the closest ones.

## Threads

- `BlockIndex.Built` holds a build's version beside its blocks, so a caller reading the volatile `built` never sees one build's version with another build's blocks.
- `TintContext.HERE` reads the world the client has open, so use it on the client thread only. `TintContext.at` keeps hold of the world it was made in, so a search on another thread reads the same place throughout. Make it on the client thread.
- A build reads the models, block colours, resources and selected packs in one task on the client thread, so they all come from the same reload.
- A build takes one `IndexOptions` snapshot, so the texture cache's key and the builder go by the same settings.
- `Sampled` fills its shaded distributions without a lock, because a race only repeats the work.

## Creative tabs

- `BlockIndex.current` reads the creative tabs only on the client thread once the player is connected, because the tabs are filled with the world's registries. Each loader calls `BlockIndex.worldJoined` when the player joins, so the loading screen hides the moment the read takes.
- `BlockIndex` fills the creative tabs from `tabRegistries`, a copy of the world's registries under another identity. The game refills its tabs and feeds its creative search only when its features, operator rights or registries differ from the last fill. A provider it has never seen makes it refill even when nothing else differs. Asked with the world's own registries, it could find nothing changed and leave that search empty.
- The game's own rebuild fills the tabs with every feature and the operator items on, so modded tabs and experimental blocks count as listed.
- `BlockIndex.listedBlocks` skips every tab that isn't a category, because the search tab repeats every other tab, the operator one included.
- `CreativeOnly` is kept by hand because the game has no flag for blocks a survival player can't get or can't place as they are. Add a block when no survival player can hold it, such as one with no item or one that can't be mined, even if a creative tab lists it. Infested blocks are found by class, so modded ones count.

## Models and states

- `IndexBuilder.Look` compares models by their texture names alone, not by the areas their quads cover, so a stair's states, which cover different areas with the same textures, fold into one entry.
- `IndexBuilder` skips waterlogged states other than the default, because water doesn't change a block's textures and those states would crowd out the ones that do.
- States that share their textures share an `Entry`, but a tint may still tell them apart, as redstone dust's power does. So `Entry.face` and `Entry.mean` with a `BlockState`, and `Target`, take the tint from the state asked about.
- `Entry.blockEntityRendered` counts a bell as drawn by its block entity, because a bell's model is only the frame it hangs in. A block drawn by its block entity has faces that hold only the texture it breaks into, a guess at how it looks.
- `Entry.BY_PREFERENCE` lists the top face first, and the search keeps the first of equal faces, so a block that looks the same all round is matched by its top, the face a builder most often sees.
- `Kind.BY_CLASS` is matched in order, and the first class a block is an instance of decides, so a modded block that extends one of them gets its kind too. Put a subclass that shouldn't take its parent's kind above the parent. Stained glass panes extend the iron bars, so `IronBarsBlock` covers glass panes. Copper grates share glass's class, so `WaterloggedTransparentBlock` goes on to the later rules.
- `MaterialSets.SUFFIXES` is in the order a set lists its shapes after its full blocks. A suffix that ends another must come after it, so `_wall_hanging_sign` is tried before `_hanging_sign`, and that before `_sign`.

## Modded blocks and NeoForge

- A modded collision shape or tint source may need a real world and throw without one. `IndexBuilder.solid` then counts the block as solid, `Kind.fullCube` as not a full cube, and `Tints.colour` as untinted.
- NeoForge deprecates `FluidModel.tintSource` for its own fluid tint source, which is still a block tint source, and `getLightEmission` for a form that takes a world and a position, which the index doesn't have. So `IndexBuilder.tintSource` and `Kind.lightEmission` call the deprecated methods.

## Tints

- `Tints` asks every biome for its tint at one fixed spot, because a biome's colour can vary with x and z, as a swamp's grass does.
- `FaceLayers` clears a face's tint cache once it holds `KEPT` tints, because walking between biomes makes a new tint context every few blocks.

## Textures and the cache

- A texture's stats are always worked out from its `Textures.Frame`, so a texture read from the cache gives the same numbers as one read from its image.
- `Textures.Frame.stats` hands a cell that isn't simply full or empty to `TextureStats` as 8 by 8 pixels, as many of them filled as the cell's alpha asks for. `TextureStats` takes 8-bit alpha and counts none under its cutoff, and a cell of a large texture can be far less filled: one texel in 64 is 4 in 255. This keeps coverage and the colours' weights true to within a thousandth.
- Raise `TextureCache.FORMAT` whenever `Textures` works out frames differently, so old cache files stop being read.
- `TextureCache.file` keys the cache by the selected packs in their order, as a later pack overrides an earlier one. A pack changed in place keeps its file, and Refresh reads the images again.
