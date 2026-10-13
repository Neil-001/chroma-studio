# Chroma Studio

A client-only Fabric and NeoForge mod for Minecraft Java 26.3. It finds the blocks closest to a colour, builds palettes and gradients from them, and exports them as schematics. It has one window with the tabs Match, Gradient, Palettes and Explore, an eyedropper, a `/chroma` command and a public API. It places nothing in the world and has no server side.

## Commands

Needs JDK 25. Versions are in `gradle.properties`. Run build and test after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests. It compiles with `-Xlint:all,-classfile` and must stay free of warnings. The IDE's own, such as unused imports, count too: name a lambda or catch parameter that isn't used `_`.
- `./gradlew :fabric:test` runs the JUnit tests for the core and for client classes that don't need the game running, such as `ModSettings`. Only Fabric hosts them. `GameIndexTest` loads the game's registries and vanilla assets, but no client, so no models. `-Dchroma.timing=true` also runs the timing tests.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. The Fabric one runs with Litematica and MaLiLib, to try the bridge, and Mod Menu, to try the settings screen from it. `-Pviewer=jei` or `-Pviewer=rei` adds that recipe viewer to either dev client; both need a newer loader API than the builds' minimum, so add `-Pfabric_version=0.161.0+26.3` or `-Pneoforge_version=26.3.0.48-beta`, as CI uses. There is no `runServer`. The game opens its window on a Wayland desktop whenever there is one, whatever `DISPLAY` says. To run it on a virtual display, unset `WAYLAND_DISPLAY` and set `SDL_VIDEODRIVER=x11`.

Both jars target 26.3 only. Minecraft is unobfuscated, so use its own names without mappings. Builds use the minimum supported Fabric API and NeoForge versions; CI also compiles against newer ones.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric also compiles `src/test`. Fabric splits common and client source sets with Loom's `splitEnvironmentSourceSets()`; NeoForge combines them in one jar. `src/main/java` holds only `core`, `src/main/resources` the assets, and `src/client` everything else. Most classes say what they do in a comment at the top, so read that before changing one.

- `dev/chromastudio/core/` must have no Minecraft imports.
  - `core/color` is the colour maths, and what a texture is summed up as: `TextureStats`, `Distribution`, `Badge`, `Scale`, `Viewing` and `Shading`.
  - `core/palette` has `Palette`, `ShareString`, `PaletteCodec` for the JSON, `Ramp` and `Gradient`, `Generator`, `Analyzer` and `WorldEditPattern`.
  - `core/export` has `Arrangement`, which lays a palette out, and `LitematicBits`.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code uses `ClientPlatform`; changes to that contract must go into both loaders. `ClientPlatform.modelParts` and `particleMaterial` read a block's model for the index, because NeoForge deprecates the game's methods that take no world. Each loader also has these hooks, so a new one goes into both:
  - `ChromaStudioClient.init` at client start, which loads the settings, creates the keys and registers the tabs and `ExportCommand`. The loader then registers `Keys.ALL` and `Keys.CATEGORY`, and calls `ChromaStudioClient.tick` at the end of every client tick.
  - The HUD layer goes to `Hud.render`, the client command event to `ChromaCommand.build()`, the item tooltip event to `Tooltips.append`, and the settings entry (Mod Menu's entrypoint, NeoForge's `IConfigScreenFactory`) to `SettingsScreen`.
  - Every system message goes to `HotbarFill.hidesFeedback`, and stays out of chat when it returns true: NeoForge from `ClientChatReceivedEvent.System`, Fabric from `ClientReceiveMessageEvents.ALLOW_GAME`.
  - The reload listener that `BlockIndex.install` gives it, and `BlockIndex.worldJoined` when the player joins.
  - Its extra world geometry goes to `Capture.render`, and it registers `DioramaPip` as a picture-in-picture renderer.
  - JEI finds `JeiHook` on Fabric through the `jei_mod_plugin` entrypoint and on NeoForge by its annotation.
- The mod is client-only. Fabric's `environment` is `client` with only a `client` entrypoint. NeoForge's one `@Mod` class has `dist = Dist.CLIENT`, and the mod registers no payloads, so a server never checks for it. Don't add a server entrypoint, a payload or a registry entry.
- There are no mixins. A change that needs one adds the config file to the loader that needs it and lists it in that loader's metadata.
- `client/index/` is the block index, described below.
- `client/ui/` has `ChromaScreen`, the window, with `Window` for its position and size, `Hud`, `Tooltips` and `SettingsScreen`, whose dialogs are `Popup`s. A tab or screen builds its controls through a `Widgets` group.
- `Theme` holds every colour the window, its tabs and the settings screen draw with, worked out from the `theme` setting's base colour. Its fields change with the setting, so read them while drawing. `ThemeTest` checks that text reads on every theme.
- `client/ui/tabs/` has the four tabs, their panels and dialogs, and `MatchState`. `Reorder` is the dragging of a gradient's stops, and `PaletteSearch` the Palettes tab's search.
- `client/action/` is what the mod does for the player in the game: `Eyedropper`, `ScreenPicker`, `Give`, `HotbarFill`, `Dye`, `Capture`, `ImageImport` and `RecipeViewers`. `PaletteBlocks` turns palette entries into block states and index entries.
- `client/export/` has `SchematicExporter`, `LitematicaBridge` and `ExportCommand`, which is `/chroma export` and `/chroma place`.
- `ModSettings` holds the mod's own options, and its comment says how to add one. A setting also needs a label and a `.tip` in the language file, which the settings screen's search reads. One that names a core enum (`metric`, `easing`, `hueArc`) is a string holding the constant's name. The screen's Undo goes back to a `ModSettings.snapshot()` taken when it opened. `ModSettings.read` turns the boolean `hotbarOverflow` of an older file into `hotbarOverflowMode`.
- `PaletteStore` keeps the player's palettes and reads the bundled presets. A new preset is a JSON file in `assets/chromastudio/presets/`, its name in `PaletteStore.PRESETS` and a `preset.chromastudio.<file>` key.
- A new `/chroma` subcommand goes in through `ChromaCommand.SUBCOMMANDS`. `/chroma debug <block id>` prints what the index holds for a block.
- `dev.chromastudio.api` is the only package under semantic versioning. Changing a signature or what a method means needs a new major version.
- Mod Menu, Litematica, MaLiLib, JEI and REI are optional and only compiled against. Only a class that the other mod loads itself, or one entered after `ClientPlatform.isModLoaded`, may refer to them. Keep every Litematica call in `LitematicaBridge.Calls`, and JEI's and REI's each in its own inner class of `RecipeViewers`.
- NeoForge compiles against Litematica's Fabric jars, because Forgematica keeps the package and class names and registers the id `litematica` too. Its compile path also has Fabric Loader's jar, only so the compiler can read the `@Environment` annotation on REI's classes; nothing may import from it.
- `en_us.json` is written by hand. The nine other language files are generated, each with a `_comment` key saying so. `LangTest` fails when one lacks a key, has one English doesn't, or fills in different placeholders, so a new English key needs its nine translations in the same change.
- `listing/README.md` says what the listing's files are for. `listing/description.md` is Modrinth's page and the source; `listing/art/curseforge.py` writes `listing/curseforge.html` from it for CurseForge, and CI fails when the two differ. Both sites load the pictures from `main`.

## Block index

`BlockIndex` holds every block's colours and searches them. It builds on its own thread and answers from the last finished build meanwhile. The client thread only hands over the models and reads the creative tabs.

1. The index lists every block with an item in a creative tab other than Operator Utilities, modded included, plus water and lava as fluids. The technical setting adds the rest.
2. `IndexBuilder` walks a block's states and reads each face's textures off its model. States with the same textures on every face are one `Entry`, and a block keeps at most `statesCap` of them.
3. `Textures` brings each sprite down to one 16 by 16 frame, and `TextureCache` keeps those on disk. It holds pixels, not statistics, which take about 0.1 s to work out again.
4. `TextureStats` sums each face up at 16, 8, 4, 2 and 1 texels per side, each scale as a `Distribution` in OKLab.
5. Statistics are cached untinted, and `Sampled` multiplies the tint in at query time.

`BlockIndex.closest(target, query)` scores a face by the earth mover's distance between the target's `Distribution` and the face's, at the scale `Viewing` gives the viewing distance. A flat colour is a one-point distribution, so that reduces to the mean per-pixel ΔE. `IndexOptions.current()` is a build's view of the settings, and `BlockIndex.version()` changes with every finished build.

An entry whose block has a block entity and an empty model, such as a chest, a banner, a shulker box or a head, is `blockEntityRendered()`. Its faces hold only its particle texture, so `Query` leaves it out unless `blockEntityRendered` is set. A bell counts too, by its class, because its model is only the posts and bar it hangs from. The Gradient tab uses those only with that filter on, and never a block that has no block entity and whose model draws nothing (`!modelDraws()`), such as a barrier.

`GameIndexTest` checks the index against the game's own blocks. Run it after touching what the index lists or how it reads a model.

## Window and tabs

`ChromaScreen` owns the window: the title bar, the tabs registered with `registerTab`, and input, which goes to the selected `Tab` inside its `Bounds`.

- `MatchState` is what is being matched, for the Match and Explore tabs, `Tooltips` and the eyedropper. It belongs to the client thread, and the tabs watch `MatchState.revision()`.
- `MatchState.filters()` is the one set of filters. Match, Explore and Gradient all keep to it, and `FilterControls` builds the More… controls for Match and Gradient alike. A gradient's candidates are `MatchFilters.candidates`: one state a block, read on one face (north for Side, top for Any) without shading.
- A click on a Match row opens the swap list, the next-best blocks of that row's kind. A swap lasts until the target, a filter, a matching setting or the index changes. The hand button gives the block.
- Match searches and Gradient solves run on a worker thread and keep only the newest answer. The Gradient tab reads its candidates on the client thread first.
- Tabs hand work to each other through `ui.Handoff`, and `/chroma` through `ChromaScreen.pendingTarget`. Additions to a palette go to `PaletteSink.current()`, which is the `PaletteStore`.
- A pinned window is drawn by `Hud.render` and takes no input.
- `Capture` borrows `Keys.onEyedropper` while it is armed and gives it back after.
- `DioramaPanel` lays its wall out with `Arrangement` and the export's own sizes, so exporting from it writes the wall that shows. `DioramaPip` draws the 3D wall, and with `dioramaPerspective` swaps the flat projection the game gives a picture-in-picture for its own.

## Minecraft rules

- The game has no `BlockRenderDispatcher`. A block is drawn through `BlockModelResolver` and `BlockModelRenderState`.
- A tint comes from `BlockTintSource.colorInWorld(state, getter, pos)`, with a `BlockAndTintGetter` whose `getBlockTint` answers `resolver.getColor(biome, x, z)`.
- The game darkens a block's faces by fixed factors: top 1.0, north and south 0.8, east and west 0.6, bottom 0.5. `Eyedropper` reads the index, not the screen, so its colour is the face unshaded.
- The creative tabs can't be filled before a world has loaded. Until `BlockIndex.worldJoined`, every block with an item counts as listed.
- Signs and beds have block models and are matched as any block.
- The game has no `#minecraft:dyeable` tag of its own. What can be dyed is set by `crafting_dye` recipes, which a client isn't sent.
- The game lays a frame's GUI out before it draws the world, so the frame `ScreenPicker` can read is the last one shown.
- The game ships SDL but not tinyfd, so `FileDialog` is SDL's, and every caller has a path field too.
- The mouse cursor is SDL's too, not GLFW's. Ask for one each frame with `g.requestCursor`; a frame that asks for none gets the arrow back. `CursorType.createStandardCursor` makes a shape `CursorTypes` lacks, such as the diagonal arrows, and falls back where the system has none.
- Mouse buttons count from 1. Compare with `InputConstants.MOUSE_BUTTON_LEFT` and `MOUSE_BUTTON_RIGHT`, never with a number.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Use cyclers for options, so right-click steps backwards, and spinners for numbers.
- The mouse wheel steps a spinner while the cursor is over it, and a cycler too: a notch down is a click and a notch up a right-click. `Widgets` does both for every control it makes, so make them through it. The settings list is the one exception, through `Widgets.wheelNeedsClick`: there the wheel only steps a spinner the player has clicked into and scrolls the list otherwise, so scrolling past a control doesn't change it.
- Size buttons with the text's width. Nothing may overlap at 427 by 240 GUI pixels, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Take colours from `Theme`, never a hex literal, apart from a colour that is data, such as a swatch, and the black and white marks drawn over one. The game's own tooltips and text fields stay dark on every theme, so text in them uses `Theme.TIP_*` and `Theme.FIELD_BAD`. Centre text with `Widgets.centred`, which leaves the drop shadow off dark text.
- Use plain, sentence case UI copy without jargon.
- Every user-visible string goes through a translation key in `assets/chromastudio/lang/en_us.json`.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels.
- Test new core logic. Change a test's reference numbers only on purpose.
- `NativeImage.getPixel` and `setPixel` take ARGB.
- Comments say why, in plain English.
- Commits follow the conventional style (`feat:`, `fix:`, `docs:`). Pull requests have a short body.
- The [wiki](https://github.com/Neil-001/chroma-studio/wiki) must be updated with any change a player would notice.

## Giving blocks and sending commands

The mod never places or changes blocks in the world. What it does to the game goes through the player's own inventory or chat:

- Giving a block, filling the hotbar and dyeing need creative mode or operator rights, and are greyed out with the reason otherwise.
- `Give.stack` is how a block's item reaches the player: the creative slot packet in creative, `/give` or `/item replace` for an operator in survival, and otherwise the reason it can't.
- A hotbar fill puts the blocks past the ninth where `HotbarPlan` says for `hotbarOverflowMode`: in free inventory slots, in shulker boxes, or on the ground. `HotbarFill` sets those slots itself, so Undo can empty them. It drops with the creative inventory's drop packet, which the server takes only so fast, or with `/give` for an operator in survival.
- Commands go through `ClientPlatform.sendChatCommand`, without the slash, as if the player had typed them. It does nothing outside a world.
- The game's replies to a hotbar fill's commands are kept out of chat, through `CommandReplies`. An error always shows.
- Exported files are never silently overwritten.

## Limits

- Spread, which `Badge` goes by, can't order every texture as the eye does: wool, gravel and granite come out grainy.
- A block a block entity draws is matched by its particle texture, so its colour is only a guess.
- The cache can't tell that a loaded pack's files were changed in place. Refresh on the settings screen reads every image again.
- `Capture` only counts loaded chunks, and no exported layout has a side past 256 blocks.
- EMI has no 26.3 release, so `RecipeViewers` calls it by name through reflection, and that path hasn't been run.
- Hiding command replies is a best effort. When it isn't certain a line answers the mod's command, the line shows.
- Left out on purpose: placement, world editing, structure import to a CAD, online accounts, server components, in-world previews beyond the capture outline, and Cloth Config.

## Releasing

Only `main` releases. Run `.github/workflows/release.yml` from `main` to publish to Modrinth, CurseForge and GitHub. It reads loader and Minecraft ranges from jar metadata. It also accepts `*/stable` branches, for an older Minecraft line kept up beside `main`, and `branches=all` means every one of those.

1. Open a PR bumping `mod_version` in `gradle.properties` and adding `# <version>` at the top of `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` suffixes for prereleases.
2. After merge, run `gh workflow run release.yml -f dry_run=true` and check the summary's jars and notes.
3. Run `gh workflow run release.yml` to publish.

Tags use `<version>+mc<line>`, such as `1.0.0+mc26.3`. For `main` the line is `minecraft_version` in `gradle.properties`; a stable branch's is its name without `/stable`. A branch whose tag exists is skipped. The workflow creates the tag last, after publishing. The two jars have distinct identifiers, `1.0.0+mc26.3-fabric` and `1.0.0+mc26.3-neoforge`; GitHub holds both jars in one release.

After a partial failure, re-run failed jobs. Modrinth skips existing versions; CurseForge doesn't. Check CurseForge for each failed job's jar before retrying, and retry only if it's absent. Don't start a fresh run with CurseForge in `targets` after a partial release. To limit sites, pass `-f targets=curseforge,github`, for example.

The `CHANGELOG.md` section goes to all three sites as written. GitHub's copy also gets a link comparing against the line's previous tag. Start with a short paragraph about what changes for players. List merged PRs under `## Features` and `## Fixes`, one bullet per PR ending in `(#21)`, for example. Rewrite titles for players and omit CI, refactors and docs.

Project IDs are repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`; tokens are secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.
