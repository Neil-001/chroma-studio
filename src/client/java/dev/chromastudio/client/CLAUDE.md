## Commands and loaders

- `ChromaCommand.build` and `Subcommand.attach` are generic because Fabric and NeoForge supply different command sources. Register subcommands before joining a world, when the loaders build the command.
- `ChromaCommand.parse` gives block IDs precedence over hex colours unless a leading `#` forces a colour.
- `ChromaStudioClient.openWindowSoon` opens the window on the next tick so chat can close or Axiom can finish drawing.
- NeoForge deprecates worldless model methods, so `ClientPlatform` supplies an empty world for indexing.

## Settings and stores

- Give each `ModSettings` field a default and a group, with bounds for numeric settings. Field names double as file keys. Save after changes.
- Hold the `GradientStore` lock during its private `load` and `save` because `ChromaStudioApi` reads the store from other threads.
- Preserve unreadable store files, including files from newer versions. `GradientStore` moves them aside and `PaletteStore` reserves their names.

## Export

- Forgematica uses the `litematica` mod ID, and its placements belong on the client thread.
- `SchematicExporter` writes Litematica version 6, which newer versions can upgrade. Block order is x fastest, then z, then y.
- `SchematicExporter.writeNew` uses `CREATE_NEW` to avoid following a link or reusing a leftover file while retaining normal file permissions.
- `SchematicExporter.structure` omits gaps so loading an `.nbt` leaves those blocks alone.
