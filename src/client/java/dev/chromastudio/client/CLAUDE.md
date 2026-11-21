The client code that doesn't depend on the mod loader: the entrypoint, the loader contract, the settings, the stores and the `/chroma` command. Its `export` package writes schematics and hands them to Litematica.

## Command

- `ChromaCommand` adds subcommands such as `capture` as literals beside the greedy `target` argument. Brigadier tries a matching literal first, so a subcommand's name is never read as a colour or a block.
- Add a subcommand to `ChromaCommand.SUBCOMMANDS` before the player joins a world, because the loaders build the command then.
- In `ChromaCommand.parse`, a leading `#` always means a colour. Without one, a block id wins over a colour, so a block whose name is six hex letters can still be asked for.
- `ChromaStudioClient.openWindowSoon` opens the window at the next tick, because the chat screen closes whatever is open once a command has run.

## Loaders

- `ClientPlatform.modelParts` and `particleMaterial` are per loader because NeoForge deprecates the game's world-less model methods for ones that take a world and a position. The NeoForge client hands those an empty world, since the index works out colours without one.

## Settings and stores

- To add a setting, declare a static field in `ModSettings` with its default, name it in a group in `GROUPS`, and give a number its limits in the static block. The file, the reset and the settings screen all work from those, and the file's keys are the field names. Call `ModSettings.save()` after changing a setting.
- `ModSettings.read` also reads the boolean `hotbarOverflow` key that versions before `hotbarOverflowMode` wrote, true as `ALWAYS` and false as `NEVER`, so an old settings file keeps the player's choice.
- Every method of `GradientStore` is synchronized because `ChromaStudioApi` reads it from other threads.
- The stores never throw away a file they can't read, such as one a later version wrote or one broken by hand. `GradientStore` moves it aside as `.unreadable` before it saves, and `PaletteStore` skips it and gives no new palette its name.
- `LayoutChoice` keeps the constant names of `core.export.Layout`, because the code converts one to the other with `Layout.valueOf(name())`.

## Export

- `LitematicaBridge` looks for the mod id `litematica`, which Forgematica, the NeoForge port, registers too.
- Litematica's placements belong to the client thread, so `LitematicaBridge.place` moves itself there.
- `SchematicExporter` writes Litematica schematic version 6, because Litematica reads older versions and upgrades them.
- A Litematica region holds its blocks with x running fastest, then z, then y.
- `SchematicExporter.write` claims a free name by creating it empty, writes the blocks to a new temporary file, and moves that over the claimed name in one step. Neither another file nor a half-written one is ever at that name.
- `SchematicExporter.writeNew` makes its temporary file with `CREATE_NEW` and the default permissions. `Files.createTempFile` would make a file only its owner can read, and the move would carry that to the schematic. `CREATE_NEW` also means a leftover file or a link at that name is never written through.
- `SchematicExporter.structure` leaves the gaps out of a `.nbt` structure rather than writing air, so loading it into a world doesn't clear what stands in them.
- `Arrangement` only drops entries off the end, so the entries `SchematicExporter` reports as left out stay in the palette's order.
