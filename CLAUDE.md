# Chroma Studio

A client-only Fabric and NeoForge mod for Minecraft Java 1.21 and 1.21.1. It finds the blocks closest to a colour, builds palettes and gradients from them, and exports them as schematics. It has one window with the tabs Match, Gradient, Palettes and Explore, an eyedropper, a `/chroma` command and a public API. It places nothing in the world and has no server side.

## Commands

Needs JDK 21 or newer; the code compiles for Java 21. Versions are in `gradle.properties`. Run build and test after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests. It compiles with `-Xlint:all,-classfile` and must stay free of warnings. The IDE's own, such as unused imports, count too. Java 21 has no unnamed `_` parameters, so call an unused catch parameter `ignored` and give an unused lambda parameter a plain name.
- `./gradlew :fabric:test` runs the JUnit tests for the core and for client classes that don't need the game running, such as `ModSettings`. Only Fabric hosts them. `GameIndexTest` loads the game's registries and vanilla assets, but no client, so no models. `-Dchroma.timing=true` also runs the timing tests.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. The Fabric one runs with Litematica and MaLiLib, to try the bridge, and Mod Menu, to try the settings screen from it. `-Pviewer=jei` or `-Pviewer=rei` adds that recipe viewer to either dev client; both need a newer loader API than the builds' minimum, so add `-Pfabric_version=0.116.17+1.21.1` or `-Pneoforge_version=21.1.264`, as CI uses. There is no `runServer`. The game opens its window on a Wayland desktop whenever there is one, whatever `DISPLAY` says. To run it on a virtual display, unset `WAYLAND_DISPLAY`.

Both jars build against 1.21.1. The Fabric jar runs on 1.21 and 1.21.1, and the NeoForge jar on 1.21.1, the only version NeoForge 21.1 supports. This branch is the 1.21.11 branch ported back to 1.21.1, which is obfuscated: use Mojang's names, which Fabric's `fabric-loom-remap` plugin maps with `loom.officialMojangMappings()` and remaps the jar from, and which NeoForge runs on as they are. Builds use the minimum supported Fabric API and NeoForge versions; CI also compiles against newer ones. Fabric API's "+1.21" builds refuse to load on 1.21.1, so the build uses 0.101.2+1.21.1, the first for 1.21.1, and `fabric.mod.json` accepts 0.100.1, the first for 1.21, which has everything the mod uses. The lines under "Minecraft rules" say what else this version changes.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric also compiles `src/test`. Fabric splits common and client source sets with Loom's `splitEnvironmentSourceSets()`; NeoForge combines them in one jar. `src/main/java` holds only `core`, `src/main/resources` the assets, and `src/client` everything else. Most classes say what they do in a comment at the top, so read that before changing one.

- `dev/chromastudio/core/` must have no Minecraft imports.
  - `core/color` is the colour maths, and what a texture is summed up as: `TextureStats`, `Distribution`, `Badge`, `Scale`, `Viewing` and `Shading`.
  - `core/palette` has `Palette`, `ShareString`, `PaletteCodec` for the JSON, `Ramp` and `Gradient`, `Generator`, `Analyzer` and `WorldEditPattern`.
  - `core/export` has `Arrangement`, which lays a palette out, and `LitematicBits`.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code uses `ClientPlatform`; changes to that contract must go into both loaders. `ClientPlatform.quads`, `particleIcon` and `renderBlock` read and draw a block's model, because NeoForge deprecates the game's methods that take no model data. Each loader also has these hooks, so a new one goes into both:
  - `ChromaStudioClient.init` at client start, which loads the settings, creates the keys and registers the tabs and `ExportCommand`. The loader then registers `Keys.ALL` and `Keys.CATEGORY`, and calls `ChromaStudioClient.tick` at the end of every client tick.
  - The HUD layer goes to `Hud.render`, the client command event to `ChromaCommand.build()`, the item tooltip event to `Tooltips.append`, and the settings entry (Mod Menu's entrypoint, NeoForge's `IConfigScreenFactory`) to `SettingsScreen`.
  - Every system message goes to `HotbarFill.hidesFeedback`, and stays out of chat when it returns true: NeoForge from `ClientChatReceivedEvent.System`, Fabric from `ClientReceiveMessageEvents.ALLOW_GAME`.
  - The reload listener that `BlockIndex.install` gives it, and `BlockIndex.worldJoined` when the player joins.
  - `Capture.render` is called after the game has drawn the entities. 1.21.1 has no picture-in-picture renderers, so `DioramaPip` draws its wall straight into the screen and nothing registers it.
  - JEI finds `JeiHook` on Fabric through the `jei_mod_plugin` entrypoint and on NeoForge by its annotation.
- The mod is client-only. Fabric's `environment` is `client` with only a `client` entrypoint. NeoForge's one `@Mod` class has `dist = Dist.CLIENT`, and the mod registers no payloads, so a server never checks for it. Don't add a server entrypoint, a payload or a registry entry.
- There are no mixins. A change that needs one adds the config file to the loader that needs it and lists it in that loader's metadata.
- `client/index/` is the block index, described below.
- `client/ui/` has `ChromaScreen`, the window, with `Window` for its position and size, `Hud`, `Tooltips` and `SettingsScreen`, whose dialogs are `Popup`s. A tab or screen builds its controls through a `Widgets` group. `Cursors` is the mouse cursor, through GLFW.
- `client/input/` has the mod's own `KeyEvent`, `MouseButtonEvent`, `CharacterEvent` and `InputWithModifiers`, which newer versions of the game provide. 1.21.1 hands input over as plain numbers, so `ChromaScreen`, `SettingsScreen` and `Widgets` override the game's methods and make these for the tabs, dialogs and controls, which take them as on `main`. `MouseButtonEvent.doubled` counts double clicks, which the game doesn't say.
- `ARGB` is the mod's own, with the few methods of the game's later class that it uses, and `Abgr` reads and writes a `NativeImage` in ARGB, which keeps its pixels as ABGR on this version.
- `Theme` holds every colour the window, its tabs and the settings screen draw with, worked out from the `theme` setting's base colour. Its fields change with the setting, so read them while drawing. `ThemeTest` checks that text reads on every theme.
- `client/ui/tabs/` has the four tabs, their panels and dialogs, and `MatchState`. `Reorder` is the dragging of a gradient's stops, and `PaletteSearch` the Palettes tab's search.
- `client/action/` is what the mod does for the player in the game: `Eyedropper`, `ScreenPicker`, `Give`, `HotbarFill`, `Dye`, `Capture`, `ImageImport` and `RecipeViewers`. `PaletteBlocks` turns palette entries into block states and index entries.
- `client/export/` has `SchematicExporter`, `LitematicaBridge` and `ExportCommand`, which is `/chroma export` and `/chroma place`.
- `ModSettings` holds the mod's own options, and its comment says how to add one. A setting also needs a label and a `.tip` in the language file, which the settings screen's search reads. One that names a core enum (`metric`, `easing`, `hueArc`) is a string holding the constant's name. The screen's Undo goes back to a `ModSettings.snapshot()` taken when it opened. `ModSettings.read` turns the boolean `hotbarOverflow` of an older file into `hotbarOverflowMode`.
- `PaletteStore` keeps the player's palettes and reads the bundled presets. A new preset is a JSON file in `assets/chromastudio/presets/`, its name in `PaletteStore.PRESETS` and a `preset.chromastudio.<file>` key.
- A new `/chroma` subcommand goes in through `ChromaCommand.SUBCOMMANDS`. `/chroma debug <block id>` prints what the index holds for a block.
- `dev.chromastudio.api` is the only package under semantic versioning. Changing a signature or what a method means needs a new major version.
- Mod Menu, Litematica, MaLiLib, JEI and REI are optional and only compiled against. Litematica's and MaLiLib's 1.21 builds are the ones for 1.21.1. NeoForge compiles the bridge against Forgematica and MaFgLib, their NeoForge ports, from Modrinth's maven: Litematica's own Fabric jars carry intermediary names on this version. Litematica takes files here, so the bridge turns its paths into them. Only a class that the other mod loads itself, or one entered after `ClientPlatform.isModLoaded`, may refer to them. Keep every Litematica call in `LitematicaBridge.Calls`, and JEI's and REI's each in its own inner class of `RecipeViewers`.
- Forgematica keeps Litematica's package and class names and registers the id `litematica` too, so the bridge is one class for both loaders. NeoForge's compile path also has Fabric Loader's jar, only so the compiler can read the `@Environment` annotation on REI's classes; nothing may import from it.
- `en_us.json` is written by hand. The nine other language files are generated, each with a `_comment` key saying so. `LangTest` fails when one lacks a key, has one English doesn't, or fills in different placeholders, so a new English key needs its nine translations in the same change.
- `listing/README.md` says what the listing's files are for. `listing/description.md` is Modrinth's page and the source; `listing/art/curseforge.py` writes `listing/curseforge.html` from it for CurseForge, and CI fails when the two differ. Both sites load the pictures from `main`.

## Block index

`BlockIndex` holds every block's colours and searches them. It builds on its own thread and answers from the last finished build meanwhile. The client thread only hands over the models and reads the creative tabs.

1. The index lists every block with an item in a creative tab other than Operator Utilities, modded included, plus water and lava as fluids. The technical setting adds the rest.
2. `IndexBuilder` walks a block's states and reads each face's textures off its model. States with the same textures on every face are one `Entry`, and a block keeps at most `statesCap` of them.
3. `Textures` brings each sprite down to one 16 by 16 frame, and `TextureCache` keeps those on disk. It holds pixels, not statistics, which take about 0.1 s to work out again.
4. `TextureStats` sums each face up at 16, 8, 4, 2 and 1 texels per side, each scale as a `Distribution` in OKLab.
5. Statistics are cached untinted, and `Sampled` multiplies the tint in at query time. 1.21.1 has no `BlockTintSource`: a `TintSource` is the block colours asked for one tint index, with the state, world and position, as the game asks them for a quad.
6. 1.21.1 has no fluid models. Water and lava are read by their sprite names, `block/water_still` and the rest, with water tinted through the block colours; a modded fluid is read like a block, from its particle texture.
7. 1.21.1 has no shelves or pale moss carpets, so `Kind` has no rules for them, and its plants are `BushBlock`s. The shelf kind stays in the filters and never matches.

`BlockIndex.closest(target, query)` scores a face by the earth mover's distance between the target's `Distribution` and the face's, at the scale `Viewing` gives the viewing distance. A flat colour is a one-point distribution, so that reduces to the mean per-pixel ΔE. `IndexOptions.current()` is a build's view of the settings, and `BlockIndex.version()` changes with every finished build.

An entry whose block has a block entity and an empty model, such as a chest, a banner, a shulker box or a head, is `blockEntityRendered()`. Its faces hold only its particle texture, so `Query` leaves it out unless `blockEntityRendered` is set. A bell counts too, by its class, because its model is only the posts and bar it hangs from. The Gradient tab uses those only with that filter on, and never a block that has no block entity and whose model draws nothing (`!modelDraws()`), such as a barrier.

`GameIndexTest` checks the index against the game's own blocks. Run it after touching what the index lists or how it reads a model.

## Window and tabs

`ChromaScreen` owns the window: the title bar, the tabs registered with `registerTab`, and input, which goes to the selected `Tab` inside its `Bounds`.

- `MatchState` is what is being matched, for the Match and Explore tabs, `Tooltips` and the eyedropper. It belongs to the client thread, and the tabs watch `MatchState.revision()`.
- `MatchState.filters()` is the one set of filters. Match, Explore and Gradient all keep to it, and `FilterControls` builds the More… controls for Match and Gradient alike. A gradient's candidates are `MatchFilters.candidates`: one state a block, read on one face (north for Side, top for Any) without shading.
- A click on a Gradient step opens the swap list, the next-best candidates for that step's colour which no step uses. The one chosen is pinned to the step in `GradientTab.PINS`, and `Gradient.solve` shares the other blocks out around the pins. A pin lasts through other steps' swaps and goes when the stops, a control, a filter, a matching setting or the index changes.
- Match searches and Gradient solves run on a worker thread and keep only the newest answer. The Gradient tab reads its candidates on the client thread first.
- Tabs hand work to each other through `ui.Handoff`, and `/chroma` through `ChromaScreen.pendingTarget`. Additions to a palette go to `PaletteSink.current()`, which is the `PaletteStore`.
- A pinned window is drawn by `Hud.render` and takes no input.
- `Capture` borrows `Keys.onEyedropper` while it is armed and gives it back after.
- `DioramaPanel` lays its wall out with `Arrangement` and the export's own sizes, so exporting from it writes the wall that shows. `DioramaPip` draws the 3D wall, and with `dioramaPerspective` swaps the flat projection the game gives a picture-in-picture for its own.

## Minecraft rules

- A block's model comes from `BlockModelShaper` as a `BakedModel`, whose quads are asked for by side with a random, so each call gets the same seed; a quad holds its `getSprite()` and `getTintIndex()`, and its vertices packed as ints. `DioramaPip` draws its wall straight into the screen with `renderBlock`, at the GUI's resolution, as the game draws items: with its own depth, which it clears in the picture's area, and with perspective through a projection of its own that undoes the GUI's view matrix.
- The GUI is drawn through `GuiGraphics`, with `render` and `renderBackground` on a screen, `renderWidget` on a widget, and `drawString`, `drawCenteredString`, `drawWordWrap`, `renderOutline` and `renderFakeItem` for the drawing itself. The game calls only `render`, so `ChromaScreen.render` draws its background itself. A tooltip asked for while drawing goes to `DeferredTooltips`, which draws it once the screen has finished, as one drawn at once would be clipped by a scissor in force. A blit's alpha goes through `g.setColor`, with blending enabled around it, which the plain blit leaves as it finds it. The open screen is `mc.screen`, set with `mc.setScreen`.
- A state's properties and values come as a map, `BlockState.getValues()`, their types apart; `IndexBuilder.valueName` names a value.
- Screens and widgets take 1.21.1's plain input arguments, such as `keyPressed(int key, int scancode, int modifiers)` and `mouseClicked(double, double, int)`, and buttons `onPress()`; see `client/input/`. The key category is a translation key, `Keys.CATEGORY`.
- The screen picker reads its pixels straight off the frame being drawn, whose world is already in, with `GlStateManager._readPixels`.
- A tint comes from `BlockColors.getColor(state, getter, pos, tintIndex)`, with a `BlockAndTintGetter` whose `getBlockTint` answers `resolver.getColor(biome, x, z)`. 1.21.1 has no empty one to borrow from, so `Tints.OneBiome` is empty by hand.
- `NativeImage.getPixelRGBA` and `setPixelRGBA` take ABGR. Go through `Abgr`, which takes and returns ARGB.
- The game darkens a block's faces by fixed factors: top 1.0, north and south 0.8, east and west 0.6, bottom 0.5. `Eyedropper` reads the index, not the screen, so its colour is the face unshaded.
- The creative tabs can't be filled before a world has loaded. Until `BlockIndex.worldJoined`, every block with an item counts as listed.
- Signs and beds have no block models here, only a particle texture, so they are matched as blocks a block entity draws, like chests. 26.2 gave them models.
- The game ships the `#minecraft:dyeable` item tag here, with the items `Dye` lists itself. 26.x dropped it for `crafting_dye` recipes, which a client isn't sent.
- The game lays a frame's GUI out before it draws the world, so the frame `ScreenPicker` can read is the last one shown.
- The game ships tinyfd, so `FileDialog` is tinyfd's, on its own thread because the dialog holds up the one that shows it. Asked with the title `tinyfd_query` it shows nothing and says whether it has a window to show, which is how a missing dialog is told from a cancel. Every caller has a path field too.
- The game has no way to ask for a mouse cursor, so `Cursors` sets one through GLFW. Ask for a shape each frame with `Cursors.request` and apply it when the screen has drawn; a frame that asks for none gets the arrow back, and so does a closed screen. Where the system has no such shape, the arrow stays.
- Mouse buttons count from 0 here. Compare with `InputConstants.MOUSE_BUTTON_LEFT` and `MOUSE_BUTTON_RIGHT`, never with a number.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Use cyclers for options, so right-click steps backwards, and spinners for numbers.
- The mouse wheel steps a spinner while the cursor is over it, and a cycler too: a notch down is a click and a notch up a right-click. `Widgets` does both for every control it makes, so make them through it. The settings list and the Palettes tab's entries are the exceptions, through `Widgets.wheelNeedsClick`: there the wheel only steps a spinner the player has clicked into and scrolls the list otherwise, so scrolling past a control doesn't change it.
- Size buttons with the text's width. Nothing may overlap at 427 by 240 GUI pixels, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Take colours from `Theme`, never a hex literal, apart from a colour that is data, such as a swatch, and the black and white marks drawn over one. The game's own tooltips and text fields stay dark on every theme, so text in them uses `Theme.TIP_*` and `Theme.FIELD_BAD`. Centre text with `Widgets.centred`, which leaves the drop shadow off dark text.
- Use plain, sentence case UI copy without jargon.
- Every user-visible string goes through a translation key in `assets/chromastudio/lang/en_us.json`.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels.
- Test new core logic. Change a test's reference numbers only on purpose.
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
- `Capture` only counts loaded chunks, and no exported layout has a side past 256 blocks. Its outline is drawn straight to the game's buffers after the entities, so water in front of it hides it.
- 1.21.1's server refuses a creative player's drops that come too fast, as newer ones do, but the client keeps no count of them here, so `HotbarFill` keeps its own.
- `RecipeViewers` calls EMI by name through reflection, as on `main`, and that path hasn't been run.
- Hiding command replies is a best effort. When it isn't certain a line answers the mod's command, the line shows.
- Left out on purpose: placement, world editing, structure import to a CAD, online accounts, server components, in-world previews beyond the capture outline, and Cloth Config.

## Releasing

`main` is for the newest Minecraft version. Each older line the mod still supports lives on a `<line>/stable` branch, such as `26.2/stable` or `1.21.1/stable`, ported from `main` and kept to its own Minecraft. Develop on `main`, and backport to a stable branch only what makes sense for its version. The 26.x branches build like `main`. The 1.21 branches compile for Java 21 with Loom's remap plugin and Mojang's names, and each branch's `CLAUDE.md` says what else differs there.

Run `.github/workflows/release.yml` from `main` to publish to Modrinth, CurseForge and GitHub. `branches` is `main` by default, a comma-separated list of `main` and `*/stable` branches, or `all` for every `*/stable` branch. It builds each branch with the Java level its `build.gradle` sets (`.github/scripts/release-java.sh`) and reads loader and Minecraft ranges from jar metadata.

1. Open a PR bumping `mod_version` in `gradle.properties` and adding `# <version>` at the top of `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` suffixes for prereleases. A stable branch gets the same two changes in a PR against it, with the changelog section as released on `main` and any note about that line.
2. After merge, run `gh workflow run release.yml -f dry_run=true` and check the summary's jars and notes. Add `-f branches=26.2/stable`, say, for a stable branch, or `-f branches=all` for every one.
3. Run `gh workflow run release.yml` with the same `branches` to publish.
4. Run `gh workflow run listing.yml` when the description, gallery or icon changed. It puts `listing/description.md`, the pictures `listing/gallery/README.md` lists and `listing/media/icon.png` on the Modrinth page (`.github/scripts/listing-modrinth.py`; a picture whose title is already there is kept unless `-f replace=true`). CurseForge has no API for its page: paste `listing/curseforge.html` and upload the gallery by hand.

Tags use `<version>+mc<line>`, such as `1.0.0+mc26.3`. For `main` the line is `minecraft_version` in `gradle.properties`; a stable branch's is its name without `/stable`. A branch whose tag exists is skipped, so `all` releases only the branches with a new version. The workflow creates the tag last, after publishing. The two jars have distinct identifiers, `1.0.0+mc26.3-fabric` and `1.0.0+mc26.3-neoforge`; GitHub holds both jars in one release.

After a partial failure, re-run failed jobs. Modrinth skips existing versions; CurseForge doesn't. Check CurseForge for each failed job's jar before retrying, and retry only if it's absent. Don't start a fresh run with CurseForge in `targets` after a partial release. To limit sites, pass `-f targets=curseforge,github`, for example.

The `CHANGELOG.md` section goes to all three sites as written. GitHub's copy also gets a link comparing against the line's previous tag. Start with a short paragraph about what changes for players. List merged PRs under `## Features` and `## Fixes`, one bullet per PR ending in `(#21)`, for example. Rewrite titles for players and omit CI, refactors and docs.

Project IDs are repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`; tokens are secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`.
