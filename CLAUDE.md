# Chroma Studio

A client-only Fabric and NeoForge mod for Minecraft Java 26.3. It finds the blocks closest to a colour, builds palettes and gradients from them, and exports them as schematics. It has one window with the tabs Match, Gradient, Palettes and Explore, an eyedropper, a `/chroma` command and a public API. It places nothing in the world and has no server side.

The mod is being built towards its first release, 1.0.0. The Spec section says what it does when finished; a package that holds only a `package-info.java` hasn't been written yet.

## Commands

Needs JDK 25. Versions are in `gradle.properties`. Run build and test after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests. It compiles with `-Xlint:all,-classfile` and must stay free of warnings. The IDE's own, such as unused imports, count too: name a lambda or catch parameter that isn't used `_`.
- `./gradlew :fabric:test` runs the JUnit tests for the core and for client classes that don't need the game running, such as `ModSettings`. Only Fabric hosts them. `GameIndexTest` loads the game's registries and vanilla assets, but no client, so no models. `-Dchroma.timing=true` also runs the timing tests.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. The Fabric one runs with Litematica and MaLiLib, to try the bridge, and Mod Menu, to try the settings screen from it. `-Pviewer=jei` or `-Pviewer=rei` adds that recipe viewer to either dev client; both need a newer loader API than the builds' minimum, so add `-Pfabric_version=0.161.0+26.3` or `-Pneoforge_version=26.3.0.48-beta`, as CI uses. There is no `runServer`. The game opens its window on a Wayland desktop whenever there is one, whatever `DISPLAY` says. To run it on a virtual display, unset `WAYLAND_DISPLAY` and set `SDL_VIDEODRIVER=x11`.

Both jars target 26.3 only. Minecraft is unobfuscated, so use its own names without mappings. Builds use the minimum supported Fabric API and NeoForge versions; CI also compiles against newer ones.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric also compiles `src/test`. Fabric splits common and client source sets with Loom's `splitEnvironmentSourceSets()`; NeoForge combines them in one jar. `src/main/java` holds only `core`, and `src/main/resources` the assets.

| Package | Holds | Source set |
| --- | --- | --- |
| `dev.chromastudio.core.color` | Colour maths: spaces, metrics, harmonies, distributions, texture statistics | `src/main` |
| `dev.chromastudio.core.palette` | Palettes, share strings, gradients, generation, analysis | `src/main` |
| `dev.chromastudio.core.export` | Schematic bit packing and layouts | `src/main` |
| `dev.chromastudio.client` | `ChromaStudioClient`, `ClientPlatform`, `ModSettings`, `Keys`, `ChromaCommand`, `PaletteSink`, `PaletteStore` and the settings' enums | `src/client` |
| `dev.chromastudio.client.index` | The block index | `src/client` |
| `dev.chromastudio.client.ui` | The window, widgets, HUD, tooltips and settings screen | `src/client` |
| `dev.chromastudio.client.ui.tabs` | The tabs themselves, their panels and dialogs, and the match state they share | `src/client` |
| `dev.chromastudio.client.action` | Eyedropper, screen picker, giving items, hotbar fill, dyeing, capture, recipe lookup | `src/client` |
| `dev.chromastudio.client.export` | File writers and the Litematica bridge | `src/client` |
| `dev.chromastudio.api` | The public API | `src/client` |

- `dev/chromastudio/core/` must have no Minecraft imports.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code uses `ClientPlatform`; changes to that contract must go into both loaders. `ClientPlatform.modelParts` and `particleMaterial` read a block's model, because NeoForge deprecates the game's methods that take no world.
- The mod is client-only. Fabric's `environment` is `client` with only a `client` entrypoint. NeoForge's one `@Mod` class has `dist = Dist.CLIENT`, and the mod registers no payloads, so a server never checks for it. Don't add a server entrypoint, a payload or a registry entry.
- `ChromaStudioClient.init` loads the settings and creates the keys in `Keys`. Each loader then registers `Keys.ALL` and `Keys.CATEGORY` in its own way and calls `ChromaStudioClient.tick` at the end of every client tick. `Keys.onEyedropper` and `Keys.onCapture` are what those two keys run.
- Each loader also hooks these to shared code: its HUD layer to `Hud.render`, its client command event to `ChromaCommand.build()`, its item tooltip event to `Tooltips.append`, and its settings entry (Mod Menu's entrypoint, NeoForge's `IConfigScreenFactory`) to `SettingsScreen`. Another task adds a `/chroma` subcommand through `ChromaCommand.SUBCOMMANDS`.
- `ModSettings` holds the mod's own options as static fields, loaded at client start and saved atomically to `config/chromastudio/settings.json`. A missing, mistyped or out-of-range value gets its default. `ModSettingsTest` covers it. A new setting is a field with its default, a name in `ModSettings.GROUPS`, limits if it's a number, and a label and a `.tip` in the language file; the file, the reset and the settings screen's row all follow from those. A setting that names a core enum (`metric`, `easing`, `hueArc`) is a string holding the constant's name.
- `ChromaScreen` owns the window: the title bar, the tabs registered with `registerTab`, and input, which goes to the selected `Tab` inside its `Bounds`. `Window` holds the position, size and mode, and the drag and resize rules; `WindowTest` covers it. A pinned window is drawn by `Hud.render` and takes no input. `pendingTarget` carries what `/chroma` asked for to the Match tab.
- A tab or screen builds its controls through a `Widgets` group, which also draws them and takes their input. `Widgets.ScrollList` is the scrolling list.
- Export is in two halves. `core.export.Arrangement` says which palette entry sits at each cell of a layout, upright and one block deep, with no side past 256; `client.export.SchematicExporter` turns the entries into block states, skipping and listing the ones this game doesn't have, and writes the `.litematic` or `.nbt` under a name that's free. `SchematicExporterTest` loads the game's blocks to read the files back, the `.nbt` through `StructureTemplate.load`. `LitematicaBridge` names Litematica's classes only in its inner class `Calls`, entered after `isAvailable()` and inside a `try` that catches `Throwable`; keep every new Litematica call there. `ExportCommand` adds `/chroma export` and `/chroma place`, and `ChromaStudioClient.init` registers it.
- `PaletteStore` keeps the player's palettes, one JSON file each in `config/chromastudio/palettes/`, named after the palette and written atomically, and reads the bundled presets named in `PaletteStore.PRESETS` from `assets/chromastudio/presets/`. It holds the selected palette and is the `PaletteSink`. `PaletteStoreTest` covers it on a temporary folder. A new preset is a JSON file, its name in `PRESETS` and a `preset.chromastudio.<file>` key.
- The Palettes and Gradient tabs are in `client.ui.tabs`, registered by `ChromaStudioClient.init`. Both extend `PanelTab`, a `Widgets` group with at most one `Dialog` over it; the dialogs both tabs open are in `PaletteDialogs`. In the Palettes tab, `AnalysePanel` and `DioramaPanel` take the entries' place while open. The Gradient tab reads its candidates on the client thread and solves on a worker, keeping only the newest answer. Tabs leave things for each other in `Handoff`.
- `PaletteBlocks` turns palette entries into block states and index entries, and reads the settings the palette tools match by. `HotbarFill`, `Capture`, `ImageImport` and `FileDialog` are in `client.action`. `Capture` borrows `Keys.onEyedropper` while it is armed and gives it back after. `FileDialog` is SDL's own, as the game doesn't ship tinyfd, and every caller has a path field too.
- Each loader also hooks its extra world geometry to `Capture.render`, for the capture outline, and registers `DioramaPip`, the picture-in-picture renderer that draws the 3D diorama with the game's block models. The game has no `BlockRenderDispatcher` now; a block is drawn through `BlockModelResolver` and `BlockModelRenderState`.
- Mouse buttons count from 1: compare with `InputConstants.MOUSE_BUTTON_LEFT` and `MOUSE_BUTTON_RIGHT`, never with a number.
- `BlockIndex` builds on its own thread, started by the reload listener that `BlockIndex.install` gives each loader, and answers from the last finished build meanwhile. The client thread only hands over the models and reads the creative tabs. The tabs can't be filled before a world has loaded, so each loader calls `BlockIndex.worldJoined` when the player joins, and until then every block with an item counts as listed. `IndexOptions.current()` is the index's view of its settings. `/chroma debug <block id>` prints what the index holds for a block.
- `MatchState` holds what is being matched: the target (a flat colour or a block's face) and the filters, for the session. The face, metric, viewing distance, biome and shading are the settings themselves, which the Match tab's controls write. `MatchState.changed()` bumps a revision the tabs watch; `query(limit)` and `target()` are what a search takes. The Match tab, the Explore tab, `Tooltips` and the eyedropper all read it, on the client thread.
- `MatchTab` lays everything out from its `Bounds`: the target on the left, and on the right three rows of controls that wrap when they don't fit, with the results in what is left. Searches run on one worker thread; `Debounce` starts at most one a frame and drops an answer a later search has overtaken. More filters, the biome list, Find biome and Dye open as panels over the results. `ExploreTab` plots what `BlockIndex.closest` returns for the same query with no limit, worked out again only when `MatchState.revision()` or `BlockIndex.version()` changes.
- Tabs hand work to each other through `ui.Handoff`, and `/chroma` through `ChromaScreen.pendingTarget`; the Match tab takes both whenever it draws. A result's add button goes to `PaletteSink.current()`, which is null until the Palettes tab sets it.
- `Give.stack` is the one way an item reaches the player: the creative slot packet in creative, `/give` or `/item replace` through `ClientPlatform.sendChatCommand` for an operator in survival, and otherwise the reason it can't.
- `Eyedropper.press` is `Keys.onEyedropper`. It reads the index, not the screen, so its colour is the face unshaded. `ScreenPicker` does read the screen: the game lays a frame's GUI out before it draws the world, so the frame that can be read is the last one shown, and `ChromaScreen` draws nothing but the picker's magnifier while it is armed.
- `RecipeViewers.show` opens JEI or REI, each named only in its own inner class, entered after `ClientPlatform.isModLoaded`. JEI hands its runtime to `JeiHook`, which Fabric lists under the `jei_mod_plugin` entrypoint and NeoForge finds by its annotation. EMI has no 26.3 release, so it is called by name through reflection and hasn't been run. NeoForge's compile path also has Fabric Loader's jar, only so the compiler can read the `@Environment` annotation on REI's classes; nothing may import from it.
- There are no mixins yet. A task that needs one adds the config file to the loader that needs it and lists it in that loader's metadata.
- Mod Menu, Litematica, MaLiLib, JEI and REI are optional and only compiled against. NeoForge compiles against Litematica's Fabric jars, because Forgematica keeps the package and class names. Only a class that the other mod loads itself, or one entered after `ClientPlatform.isModLoaded`, may refer to them.
- `dev.chromastudio.api` is the only package under semantic versioning.
- `listing/description.md` is the description on both Modrinth and CurseForge, used as it is. Its images are in `listing/media/`, loaded from `main`, and the gallery screenshots in `listing/gallery/`.

## Spec

### Engine

- **Index.** Every block with an item in a creative tab other than Operator Utilities, modded included, plus water and lava as fluids. A technical toggle adds the rest. A hand-kept creative-only list (bedrock, reinforced deepslate, end portal frame, spawner, trial spawner, vault, petrified oak slab, infested blocks and the like) is tagged and filterable.
- **Entries.** One per block, with states deduped by the set of textures on each face, at most eight per block. Collapsed by default to one row per block scored by its best state, naming the state's properties when it isn't the default ("Oak log, axis=x"). A setting expands to one row per state.
- **Faces.** The six faces and "any", the best of six. Each face has an alpha-weighted average and a coverage figure. Animated textures are averaged over frames weighted by mcmeta frame time, and flagged.
- **Scales.** 16, 8, 4, 2 and 1 texels per side, box-averaged. The pixels at each scale are summarised as dominant colours with coverage, in OKLab.
- **Badge.** Spread is the RMS ΔE of pixels from their mean in OKLab at full scale. It gives flat under 0.02, busy from 0.13 and grainy between, set from the vanilla textures so concrete and terracotta are flat, stone, planks and cobblestone grainy, and bookshelves busy. Spread can't order every texture as the eye does: wool, gravel and granite come out grainy. `Badge` lists the measurements.
- **Matching.** Earth mover's distance between the target's and the entry's distributions at the chosen scale. A flat colour is a one-point distribution, so it reduces to the mean per-pixel ΔE.
- **Metrics.** OKLab (default, Euclidean), CIEDE2000, CIE76, sRGB Euclidean and map colour (nearest `MapColor` base colour).
- **Shading.** A toggle, off by default. On, faces are darkened as the game lights them (top 1.0, north and south 0.8, east and west 0.6, bottom 0.5) and the eyedropper divides that out.
- **Viewing distance.** A slider in blocks, mapped to a scale through contrast sensitivity from the window height and field of view. At 1080p and 70° vertical FOV: 12 blocks is 16 texels, 24 is 8, 48 is 4, 96 is 2, and far is 1. The default is 24 blocks.
- **Biome tint.** Through `BlockTintSource.colorInWorld(state, getter, pos)` with a `BlockAndTintGetter` whose `getBlockTint` answers `resolver.getColor(biome, x, z)` for the chosen biome. Modes: here (the player's position in the real level, so the game's biome blending applies), a chosen biome from the registry (searchable, modded included), and all biomes, which gives a range. Tinted entries carry a badge and show the range as a strip. Palettes and gradients evaluate in the chosen biome. A reverse search finds the biome that brings a tinted block closest to a target. Untinted statistics are cached and the tint multiplies at query time.
- **Cache.** Built in the background on resource reload with a progress bar, stored under `config/chromastudio/cache/` keyed by the loaded packs and mod list, with a Refresh button.
- **Material sets.** From registry path suffixes (`_slab`, `_stairs`, `_wall`, `_fence`, `_fence_gate`, `_trapdoor`, `_door`, `_button`, `_pressure_plate`, `_sign`, `_hanging_sign`, `_planks`, `_log`, `_wood`) plus a table of irregulars (stone bricks, cobblestone, bricks, nether bricks, quartz, purpur, prismarine, sandstone and the like). A row expands to its set, and one click makes the set a palette.
- **Harmonies.** In OKLCH hue, keeping L and C: similar ±15°, analogous ±30°, complementary, split-complementary (150°, 210°), triadic, tetradic (60°, 180°, 240°), square, and monochromatic (same hue, lightness steps).
- **Gradients.** Two or more stops and 2 to 32 steps, interpolated in OKLCH along the shortest hue arc by default, the longest as an option. Easing is linear, smooth (smoothstep), ease-in or ease-out. Steps are assigned globally, as an exact minimum-cost assignment (`Gradient`), to minimise total ΔE with no block repeated. An option keeps texture spread similar across steps by adding a spread-difference term. The quality badge is the mean ΔE in OKLab: green up to 0.02, amber up to 0.06, red beyond.

### Window and features

- **Window.** One screen, `ChromaScreen`, with tabs Match, Gradient, Palettes and Explore. It drags by the title bar and resizes from edges and corners, down to 400 by 220 GUI pixels, and is forced fullscreen when the screen is under 427 by 240. The title bar holds the tabs and the fullscreen, pin and close buttons. The settings cog sits outside the window at the top-right of the screen. Windowed, there is no blur and no dim, the world shows and the game doesn't pause. Fullscreen has the vanilla blurred background. Position, size and mode are remembered in the settings.
- **Pin.** Closes the screen and keeps drawing the current tab read-only at a set opacity at the window's position. The open key brings it back.
- **Match.** On the left, the target: an HSV square and hue bar, OKLCH sliders drawn as gradients with the sRGB gamut boundary marked and out-of-gamut greyed, hex, RGB and HSL fields, recent colours, and the eyedropper, screen picker and dye buttons. On the right, the controls row (face, metric, viewing distance, biome), the filter row and the results: a list by default with a grid toggle, twelve rows by default, 3 to 60 in the settings. A row shows icon, name, state note, ΔE, texture badge, kind, swatch and the matched face's texture downsampled to the current scale. Click gives the block (creative or operator), Shift-click copies the id, right-click seeds a gradient, an add button sends it to the current palette, and `R` opens the recipe viewer. A row expands to its material set.
- **Filters.** Kind, full cube, solid (collidable), opaque, same texture on every face, tinted, mod namespace include and exclude, technical, creative-only, light sources, tone (warm, cool, neutral), lightness and chroma ranges, and texture badge.
- **Eyedropper** on `X`. It reads the face hit, the state as placed and the biome at the block, shows a HUD readout for a few seconds (name, state, hex, face) and copies the hex. Shift and `X` opens the window on it.
- **Screen picker.** A button in the window, then a click anywhere. The window steps aside while aiming, and the click reads the frame under the cursor as a 3 by 3 average, with a small magnifier beside it. An item slot in the window takes the block in hand on a click, and the wheel over it steps through the hotbar.
- **Image to palette.** The system's file dialog through SDL, which the game ships, with a path field as the fallback. Transparent pixels are ignored. K-means in OKLab, six colours by default, matched under the current filters.
- **Hex to dye.** Leather armour, leather horse armour and wolf armour, plus any item a `#minecraft:dyeable` tag lists, creative or operator only, greyed out otherwise. 26.3 has no such tag of its own: what can be dyed is set by `crafting_dye` recipes, which a client isn't sent.
- **Gradient tab.** A stops row, steps, easing, hue arc, the spread toggle, a preview strip of the ideal colours with the chosen blocks, and the badge. Actions: fill hotbar, copy list, export, save as palette, swap ends.
- **Palettes tab.** On the left, the list of palettes, own ones first and bundled presets below, with new, rename, delete, import and share. On the right, the selected palette's entries with up and down arrows, a weight spinner and remove. The toolbar has Generate, Capture, Image, Analyse, Diorama, Export, WorldEdit and Hotbar. Analyse and Diorama replace the entries area until closed; the rest open small dialogs. The toolbar shows icons with tooltips at the minimum width and labels when there's room.
- **Palette model.** Ordered block-state entries, each with a weight (default 1), plus a name, tags and notes, of any length. One JSON file per palette in `config/chromastudio/palettes/`, written atomically. The share string is `chroma1:stone,andesite*3,oak_log[axis=y]/Name`: the namespace is dropped for `minecraft`, the weight follows `*`, properties go in brackets and an optional name follows `/`. A slash inside a block id is written `%2F`, so the first `/` always starts the name. JSON import and export. About ten bundled presets are read-only JSON in the resources, and can be copied.
- **Generate.** From a seed colour or the block in hand, a harmony and a lightness ramp, six colours by default, the closest blocks under the current filters. Random roll picks a random seed and harmony.
- **Capture.** Armed from Palettes, then the eyedropper key on two blocks. The box is outlined in the world until captured or cancelled. It counts block states in loaded chunks inside the box, air excluded, and keeps the top twelve by default. `/chroma capture x1 y1 z1 x2 y2 z2` does the same.
- **Analysis** in OKLCH: lightness range and gaps, hue spread against the nearest harmony, chroma balance, accent contrast and texture mix. Each finding has a severity and a Fix that opens Match on the suggested target and filters. The palette is plotted on the Explore scatter.
- **Diorama.** A flat tiled wall from face textures, and a 3D wall drawn with the game's block renderer that orbits with the mouse. The distributions are noise clusters, height gradient and random dither, each with a reroll. Weights are respected.
- **Export.** `.litematic` and vanilla `.nbt` structure files. Layouts: strip (one high), wall (a chosen height, default 3), patch grid (each entry an n by n square, default 3) and the three diorama distributions (a chosen width and height), with an optional one-block air gap between entries. Block states are kept. Files go to Litematica's configured schematics directory when it's loaded (`DataManager.getSchematicsBaseDirectory()`), else `<game>/schematics/`. They're named `chroma-<palette>-<layout>.<ext>`, with a numbered suffix when the name exists, and never silently overwritten. The metadata has the name, the player as author and the share string as description.
- **Place in Litematica.** `SchematicHolder.getInstance().getOrLoad(path)`, `SchematicPlacement.createFor(schematic, origin, name, true, true)`, `DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true)` and then `setSelectedSchematicPlacement`, on the client thread. The origin is the block the player looks at within reach, else the player's position. It's greyed out with a reason when no mod with id `litematica` is loaded; Forgematica registers that id too. One bridge class, entered only after the mod-id check, wraps every call so that any `Throwable` shows a message and leaves the file in place. No version is pinned.
- **WorldEdit.** A pattern string from weights and properties (`70%stone,30%andesite[...]`), and a builder for `//set`, `//replace`, `//walls`, `//outline` and `//overlay` with the pattern, to copy or send as chat.
- **Hotbar fill.** In creative, through `ServerboundSetCreativeModeSlotPacket`: the first nine entries, the rest in a shulker box. For operators in survival, `/item replace entity @s hotbar.N with …` and `/give` for the shulker box. Undo restores what was there, both ways. A strip above the hotbar names each slot's block and fades after a few seconds.
- **Tooltips** in any inventory: swatch, hex, kind, badge and ΔE to the current target. Shown on Shift by default; the settings also offer always and never.
- **Explore.** Every entry plotted with OKLCH hue across and lightness up, with a chroma range filter. Scroll zooms, drag pans, hover shows the icon and name, and a click sets the target. Filters apply.
- **Keys.** `C` opens the window, `X` is the eyedropper and `R` looks up a recipe inside the window. Pin, fullscreen and capture start unbound.
- **Commands**, all client-side: `/chroma <hex>`, `/chroma <block id>`, `/chroma capture x1 y1 z1 x2 y2 z2`, `/chroma export <share string> [layout] [format] [place]` `/chroma place <file name>` and, for checking the index, `/chroma debug <block id>`. A share string whose name ends in a layout, a format or `place` goes in double quotes.
- **Settings.** A screen from the cog, from Mod Menu on Fabric and from the mod list on NeoForge. Groups: keys and window (window memory, start tab, pin opacity); matching (default face, shading, metric, result count, viewing distance, default filters, show ΔE); index (technical blocks, states separately, states cap, animated averaging, cache folder, rebuild on reload); biome (default mode, chosen biome); gradient (default steps, easing, hue arc, spread matching); palettes (generate count, capture count, hotbar overflow, export folder override, default layout, patch size, gap); tooltips and HUD (tooltip mode, fields shown, readout duration); integrations (recipe key, Litematica placement position).
- **Left out on purpose:** placement, world editing, structure import to a CAD, online accounts, server components, and in-world previews beyond the capture outline.

### Other decisions

- Optional integrations beyond Litematica: JEI, REI and EMI for the recipe lookup. JEI and REI are compiled against only; EMI is called by name until it has a 26.3 release. No Cloth Config.
- Languages: `en_us` by hand. `zh_cn`, `ru_ru`, `pt_br`, `de_de`, `es_es`, `fr_fr`, `ja_jp`, `ko_kr` and `pl_pl` are generated, and say so in a `_comment` key.
- The public API gives the colour of a block state per face and biome, the closest entries to a colour, and palette reads.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Every user-visible string goes through a translation key in `assets/chromastudio/lang/en_us.json`.
- Use plain, sentence case UI copy without jargon.
- Size buttons with the text's width. Use cyclers for options, so right-click steps backwards, and spinners for numbers. The mouse wheel steps a spinner while the cursor is over it. In a list that scrolls, such as the settings, it only steps a spinner the player has clicked into and scrolls the list otherwise, so scrolling past a number doesn't change it.
- Nothing may overlap at 427 by 240 GUI pixels, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels.
- Test new core logic. Change a test's reference numbers only on purpose.
- `NativeImage.getPixel` and `setPixel` take ARGB.
- Comments say why, in plain English.
- Commits follow the conventional style (`feat:`, `fix:`, `docs:`). Pull requests have a short body.

## Giving blocks and sending commands

The mod never places or changes blocks in the world. What it does to the game goes through the player's own inventory or chat:

- Giving a block, filling the hotbar and dyeing need creative mode or operator rights, and are greyed out with the reason otherwise.
- Commands go through `ClientPlatform.sendChatCommand`, without the slash, as if the player had typed them. It does nothing outside a world.
- Exported files are never silently overwritten.

## Releasing

`main` is the only branch, and it releases. Run `.github/workflows/release.yml` from `main` to publish to Modrinth, CurseForge and GitHub. With no `branches` input it releases `main`. It still accepts `*/stable` branches, for when an older Minecraft line is kept up beside `main`, and `branches=all` means every one of those.

1. Open a PR bumping `mod_version` in `gradle.properties` and adding `# <version>` at the top of `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` suffixes for prereleases.
2. After merge, run `gh workflow run release.yml -f dry_run=true` and check the summary's jars and notes.
3. Run `gh workflow run release.yml` to publish.

Tags use `<version>+mc<line>`, such as `1.0.0+mc26.3`. For `main` the line is `minecraft_version` in `gradle.properties`; a stable branch's is its name without `/stable`. The Minecraft and loader ranges each site shows come from the jars' metadata. A branch whose tag exists is skipped. The workflow creates the tag last, after publishing. The two jars have distinct identifiers, `1.0.0+mc26.3-fabric` and `1.0.0+mc26.3-neoforge`; GitHub holds both in one release.

After a partial failure, re-run failed jobs. Modrinth skips existing versions; CurseForge doesn't. Check CurseForge for each failed job's jar before retrying, and retry only if it's absent. Don't start a fresh run with CurseForge in `targets` after a partial release. To limit sites, pass `-f targets=curseforge,github`, for example.

The `CHANGELOG.md` section goes to all three sites as written. GitHub's copy also gets a link comparing against the line's previous tag. Start with a short paragraph about what changes for players. List merged PRs under `## Features` and `## Fixes`, one bullet per PR ending in `(#21)`, for example. Rewrite titles for players and omit CI, refactors and docs.

Project IDs are repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`; tokens are secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.
