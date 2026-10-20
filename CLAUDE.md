# Chroma Studio

A client-only Fabric and NeoForge mod for Minecraft Java 1.21.11. It finds the blocks closest to a colour, builds palettes and gradients from them, and exports them as schematics. It has one window with the tabs Match, Gradient, Palettes and Explore, an eyedropper, a `/chroma` command and a public API. It places nothing in the world and has no server side.

## Commands

Needs JDK 21 or newer; the code compiles for Java 21. Versions are in `gradle.properties`. Run `./gradlew build` after every change.

- `./gradlew build` builds both jars in `fabric/build/libs/` and `neoforge/build/libs/` and runs the tests. It compiles with `-Xlint:all,-classfile` and must stay free of warnings. The IDE's own, such as unused imports, count too. Java 21 has no unnamed `_` parameters, so call an unused catch parameter `ignored` and give an unused lambda parameter a plain name.
- `./gradlew :fabric:test` runs the tests, which only Fabric hosts. `-Dchroma.timing=true` also runs the timing tests.
- `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` starts a dev client. The Fabric one has Litematica, MaLiLib and Mod Menu. `-Pviewer=jei` or `-Pviewer=rei` adds that recipe viewer to either; both need a newer loader API than the builds' minimum, so add `-Pfabric_version=0.141.6+1.21.11` or `-Pneoforge_version=21.11.48`, as CI uses. The game opens its window on a Wayland desktop whenever there is one, whatever `DISPLAY` says. To run it on a virtual display, unset `WAYLAND_DISPLAY`.

Both jars target 1.21.11 only. This branch is `main` ported back to 1.21.11, which is obfuscated: use Mojang's names, which Fabric's `fabric-loom-remap` plugin maps with `loom.officialMojangMappings()` and remaps the jar from, and which NeoForge runs on as they are. Builds use the minimum supported Fabric API and NeoForge versions; CI also compiles against newer ones. The lines under "Minecraft rules" say what else this version changes.

## Layout and boundaries

Both loaders compile the shared `src/main` and `src/client` code with their own entrypoints and metadata. Fabric splits common and client source sets with Loom's `splitEnvironmentSourceSets()` and also compiles `src/test`; NeoForge combines them in one jar. `src/main/java` holds only `core`, `src/main/resources` the assets, and `src/client` everything else.

Most classes say what they do in a comment at the top, and their fields and methods say the rest. Read those before changing a class, and keep them true. This file has only what no one class says.

- `dev.chromastudio.core` must have no Minecraft imports.
- Only `fabric/src/` and `neoforge/src/` may import their loader's classes. Shared client code goes through `ClientPlatform`. Each loader's client entrypoint hooks the shared code into its events, so a change to `ClientPlatform` or a new hook goes into both.
- The mod is client-only. Fabric's `environment` is `client` with only a `client` entrypoint. NeoForge's one `@Mod` class has `dist = Dist.CLIENT`, and the mod registers no payloads, so a server never checks for it. Don't add a server entrypoint, a payload or a registry entry.
- There are no mixins. A change that needs one adds the config file to the loader that needs it and lists it in that loader's metadata.
- `dev.chromastudio.api` is the only package under semantic versioning. Changing a signature or what a method means needs a new major version.
- Mod Menu, Litematica, MaLiLib, JEI and REI are optional and only compiled against. Only a class that the other mod loads itself, or one entered after `ClientPlatform.isModLoaded`, may refer to them.
- A new setting follows the comment on `ModSettings`, and needs a label and a `.tip` in the language file, which the settings screen's search reads.
- `en_us.json` is written by hand, and the nine other language files are generated from it. A new English key needs its nine translations in the same change, or `LangTest` fails.
- Read `listing/README.md` before changing anything in `listing/`.

## Block index

`BlockIndex` holds every block's colours and searches them. It builds on its own thread, and the client thread only hands it the models and reads the creative tabs. Run `GameIndexTest` after touching what the index lists or how it reads a model. In a dev client, `/chroma debug <block id>` prints what the index holds for a block.

## Minecraft rules

- A block's model comes from `BlockModelShaper`, as `BlockStateModel` with `BlockModelPart`s, and a quad holds its `sprite()` and `tintIndex()` itself. The diorama submits its blocks to the game's `FeatureRenderDispatcher` and has it draw them, as the game's own pictures do: a picture's `renderToTexture` gets only a pose, and the ways to draw a block straight into the buffers are deprecated.
- The GUI is drawn through `GuiGraphics`, with `render` and `renderBackground` on a screen, `renderWidget` and `renderContents` on a widget, and `drawString`, `drawCenteredString`, `drawWordWrap`, `renderOutline` and `renderFakeItem` for the drawing itself. The open screen is `mc.screen`, set with `mc.setScreen`.
- A state's properties and values come as a map, `BlockState.getValues()`, their types apart.
- A tint comes from `BlockColors.getColor(state, getter, pos, tintIndex)`, with a `BlockAndTintGetter` whose `getBlockTint` answers `resolver.getColor(biome, x, z)`.
- Signs and beds have no block models here, only a particle texture, so they are matched as blocks a block entity draws, like chests. 26.2 gave them models.
- The game runs on GLFW and ships tinyfd, which `FileDialog` uses. Ask for a mouse cursor each frame with `g.requestCursor`; a frame that asks for none gets the arrow back. `CursorType.createStandardCursor` makes a shape `CursorTypes` lacks, such as the diagonal arrows, from a GLFW shape, and falls back where the system has none.
- Mouse buttons count from 1. Compare with `InputConstants.MOUSE_BUTTON_LEFT` and `MOUSE_BUTTON_RIGHT`, never with a number.
- `NativeImage.getPixel` and `setPixel` take ARGB.

## Conventions

- Grey out inapplicable controls with a tooltip explaining why. Don't hide them.
- Use cyclers for options, so right-click steps backwards, and spinners for numbers. Make them through `Widgets`, which lets the mouse wheel step them. In a scrolling list, such as the settings or the Palettes tab's entries, use `Widgets.wheelNeedsClick`, so scrolling past a control doesn't change it.
- Size buttons with the text's width. Nothing may overlap at 427 by 240 GUI pixels, as in 1280×720 at GUI scale 3. New controls must take space from existing ones.
- Take colours from `Theme`, never a hex literal, apart from a colour that is data, such as a swatch, and the black and white marks drawn over one. The game's own tooltips and text fields stay dark on every theme, so text in them uses `Theme.TIP_*` and `Theme.FIELD_BAD`. Centre text with `Widgets.centred`.
- Use plain, sentence case UI copy without jargon.
- Every user-visible string goes through a translation key in `assets/chromastudio/lang/en_us.json`.
- Visual fixes must preserve other behaviour. Prove it, for example by comparing pixels.
- Test new core logic. Change a test's reference numbers only on purpose.
- Comments say why, in plain English.
- Commits follow the conventional style (`feat:`, `fix:`, `docs:`). Pull requests have a short body.
- The [wiki](https://github.com/Neil-001/chroma-studio/wiki) must be updated with any change a player would notice.

## Giving blocks and sending commands

The mod never places or changes blocks in the world. It acts only through the player's own inventory or chat:

- Giving a block, filling the hotbar and dyeing need creative mode or operator rights, and are greyed out with the reason otherwise. A block's item reaches the player through `Give.stack`.
- Commands go through `ClientPlatform.sendChatCommand`, as if the player had typed them.
- Exported files are never silently overwritten.

## Limits

- Spread, which `Badge` goes by, can't order every texture as the eye does: wool, gravel and granite come out grainy.
- `Capture` draws its outline straight to the game's buffers after the entities, so water in front of it hides it.
- Left out on purpose: placement, world editing, structure import to a CAD, online accounts, server components, in-world previews beyond the capture outline, and Cloth Config.

## Releasing

`main` is for the newest Minecraft version. Each older line the mod still supports lives on a `<line>/stable` branch, such as `26.2/stable` or `1.21.1/stable`, ported from `main` and kept to its own Minecraft. Develop on `main`, and backport to a stable branch only what makes sense for its version. Each branch's `CLAUDE.md` says how it differs from `main`.

Run `.github/workflows/release.yml` from `main` to publish to Modrinth, CurseForge and GitHub. Its `branches` input is `main` by default, a comma-separated list of `main` and `*/stable` branches, or `all` for every `*/stable` branch.

1. Open a PR bumping `mod_version` in `gradle.properties` and adding `# <version>` at the top of `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` suffixes for prereleases. A stable branch gets the same two changes in a PR against it, with the changelog section as released on `main` and any note about that line.
2. After merge, run `gh workflow run release.yml -f dry_run=true` and check the summary's jars and notes. Add `-f branches=26.2/stable`, say, for a stable branch, or `-f branches=all` for every one.
3. Run `gh workflow run release.yml` with the same `branches` to publish.
4. Run `gh workflow run listing.yml` when the description, gallery or icon changed. CurseForge has no API for its page: paste `listing/curseforge.html` and upload the gallery by hand.

Tags use `<version>+mc<line>`, such as `1.0.0+mc26.3`. A branch whose tag exists is skipped, so `all` releases only the branches with a new version. The workflow creates the tag last, after publishing.

After a partial failure, re-run failed jobs. Modrinth skips existing versions; CurseForge doesn't. Check CurseForge for each failed job's jar before retrying, and retry only if it's absent. Don't start a fresh run with CurseForge in `targets` after a partial release. To limit sites, pass `-f targets=curseforge,github`, for example.

The `CHANGELOG.md` section goes to all three sites as written. Start with a short paragraph about what changes for players. List merged PRs under `## Features` and `## Fixes`, one bullet per PR ending in `(#21)`, for example. Rewrite titles for players and omit CI, refactors and docs.
