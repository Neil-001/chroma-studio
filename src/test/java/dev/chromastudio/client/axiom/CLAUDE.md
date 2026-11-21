Tests for the Axiom package, run against a stand-in Axiom (`FakeAxiom` and the stand-ins in `com.moulberry`).

- `FakeAxiom` makes `ClientLevel`, `Minecraft` and `Window` without running their constructors, which would need a whole client.
- `FakeAxiom.client` sets a 720-pixel window and a field of view of 70, because `PaletteBlocks` reads a swatch's colour for the viewing scale they give.
- `FakeAxiom.Tools` copies each region pushed into a `LinkedHashMap`, as an immutable copy probes badly on the clustered hashes of a big box's positions.
- Tests without a world have no block tags bound, so grass, dirt and sand don't count as the ground in them. The tests use gravel for the ground, and the tagged blocks show only in the game.
- `ScriptSafetyTest` runs the real Lua as Axiom 6.1.5 runs it: compiled once, then called for each position with `x`, `y` and `z` set and every other global kept. Only Axiom's functions are stood in for, each as its bytecode has it: `getBlock` gives the block's registry id, and `getBlockState` the state's id negated, less one. `isSolid` asks whether the state that id names, a block's default state, has any collision shape. `getBlockProperty` gives a property's value by name as a string, or nil when the block hasn't it. `isBlockTagged` raises an error for a tag the game doesn't know.
- `ScriptSafetyTest` checks each script against its tool's own code, not a shared rule, so a script and its tool can't share a wrong assumption.
- `RecolourCasesTest` holds recolours a builder tried in the game and found wrong, read with the game's own textures. Each case in `AxiomCommitTest` is a way changes could get past the check before sending. Keep their cases and their expected answers.
