## Commands and loaders

- `ChromaCommand.build` and `Subcommand.attach` are generic because Fabric and NeoForge supply different command sources. Register subcommands before the player joins a world, when the loaders build the command. Brigadier tries matching literals before the greedy target argument.
- In `ChromaCommand.parse`, a leading `#` forces a colour. Otherwise a block id takes precedence so ids made of six hex digits still work.
- `ChromaStudioClient.openWindowSoon` waits a tick because the chat screen closes once a command runs. Axiom panels also use it to let ImGui finish drawing.
- NeoForge deprecates the worldless model methods. Its `ClientPlatform.modelParts` and `particleMaterial` implementations supply an empty world for indexing.

## Settings and stores

- Add a field with a default to `ModSettings`, list it in `GROUPS` and set numeric bounds in the static block. The field name is the file key. Call `ModSettings.save()` after changing a setting.
- `ModSettings.read` maps old `hotbarOverflow` booleans to `ALWAYS` or `NEVER` to preserve the player's choice.
- `ChromaStudioApi` reads `GradientStore` from other threads. Hold the store's lock when calling its private `load` and `save`.
- Preserve files the stores can't read, including files from newer versions. `GradientStore` moves them aside as `.unreadable` before saving. `PaletteStore` leaves them in place and reserves their names.

## Export

- Forgematica registers the same `litematica` mod id. Its placements, like Litematica's, belong on the client thread.
- `SchematicExporter` writes Litematica version 6, which newer Litematica can upgrade. A region's block order is x fastest, then z, then y.
- `SchematicExporter.write` reserves the destination before writing and moves the finished temporary file over its reservation. `writeNew` uses `CREATE_NEW` to avoid following a link or reusing a leftover file, with the normal file permissions a schematic should inherit.
- `SchematicExporter.structure` omits gaps so loading an `.nbt` leaves the blocks there alone.
