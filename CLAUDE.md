# Chroma Studio

Chroma Studio is a client-only Fabric and NeoForge mod for Minecraft Java 1.21 and 1.21.1. This branch is the `1.21.11/stable` branch ported back to 1.21.1, and the Minecraft 1.21.1 section lists how it differs from `main`.

## Build and test

Use JDK 21 or newer and run `./gradlew build` after every change. The code compiles for Java 21, which is also what CI and the release workflow build with. Javac warnings fail the build, and IDE warnings stay at zero. Java 21 has no unnamed `_` variables, so name an unused catch parameter `ignored` and give an unused lambda parameter a plain name.

- Add `-Dchroma.timing=true` to `./gradlew :fabric:test` for timing tests. Run `GameIndexTest` when changing what the index lists or how it reads models. Inspect real models with `/chroma debug <block id>`.
- To try JEI or REI in a dev client, pass `-Pviewer=jei` or `-Pviewer=rei` and the newer loader API version from `.github/workflows/build.yml`. For a virtual display, unset `WAYLAND_DISPLAY`, since the game opens on a Wayland desktop whenever there is one.
- Error Prone runs in every compile, and its findings fail the build. The root `build.gradle` lists the checks that are off. Fix a new finding instead of adding its check to that list.
- Leave Gradle's configuration cache off. It freezes the `sourceRuleLinks` test input, so a broken `AGENTS.md` link would pass.
- Test new core logic. Check the source of reference values before changing them. Verify visual fixes preserve other behaviour, for example by comparing pixels.

## Boundaries

- Read a package's `CLAUDE.md` before editing it. Record conventions and decisions the code can't explain. Name the class and keep the note to a sentence or two. Pair each file with an `AGENTS.md` symlink. Only the exceptions in `SourceRulesTest` may have source comments. `SourceRulesTest` also fails a source tree that names a package the rules below keep out of it.
- Keep `dev.chromastudio.core` independent of Minecraft. Route shared client hooks through `ClientPlatform` and wire them into both loaders. Keep loader imports in their own source trees and mixin configs in the metadata of the loader that needs them. Neither jar may add server entrypoints, payloads or registry entries.
- Only `dev.chromastudio.api` follows semantic versioning. Changing a signature or its meaning requires a new major version. `ApiSignaturesTest` records the signatures and fails when one changes.
- Reference Mod Menu, Litematica, MaLiLib, JEI and REI only from classes the optional mod loads or after a `ClientPlatform.isModLoaded` check. Isolate optional repositories with `exclusiveContent` so an outage can't break unrelated dependency lookups.
- Both loaders need Architectury for REI's signatures. NeoForge compiles against Forgematica and MaFgLib from Modrinth's maven, because Litematica's own Fabric jars carry intermediary names on 1.21.1. They keep Litematica's classes and the `litematica` mod ID. NeoForge's compile-only Fabric Loader dependency resolves REI's `@Environment` annotation.
- Axiom is optional and Fabric-only. Enter `dev.chromastudio.client.axiom` through `AxiomBridge` and keep Axiom references there. Compile against the pinned API and ImGui binding in `libs/` and exclude both from the jars. `libs/README.md` says why this line pins API 1.0.6.1 in Mojang's names. Guard reflection outside the API and show the player why a call failed. The Axiom jar belongs only in `run/fabric/mods/` for testing.

## Interface and player actions

- Use Mojang's names, as the Minecraft 1.21.1 section describes. Request the cursor every frame and use `InputConstants` for mouse buttons. Keep the F1 check in `Hud.render` because NeoForge draws HUD layers even with the HUD hidden.
- Grey out inapplicable controls. Explain why and how to enable them in the tooltip. Hide Axiom features unless `AxiomBridge.isPresent`. Use `AxiomPanel` in the editor and `Widgets` for cyclers, spinners and centred text. Use `Widgets.wheelNeedsClick` in scrolling lists.
- Size buttons to their text and make room for new controls at 427 by 240 GUI pixels. Use `Theme` colours except for data and black or white swatch marks. Tooltips and fields stay dark on every theme and use `Theme.TIP_*` and `Theme.FIELD_BAD`.
- Use sentence case. Name an unlabelled control in one to three words. Other tooltips give the value and range, a swatch detail or a prerequisite the player can't see. Aim for 12 words and exceed 20 only with a reason. Status lines say what happened and what to do next. Limit Axiom panel instructions to one line except for refusals, irreversible actions or possible data loss.
- Put player-visible strings in `assets/chromastudio/lang/en_us.json` and update all nine translations together. New settings need a label and a `.tip` for search.
- Giving, dyeing and filling the hotbar require creative mode or operator rights. Use `Give.stack` for items and `ClientPlatform.sendChatCommand` for commands.
- Only tools used in Axiom's editor may change the world. Send changes through Axiom for permission checks and undo, with one history entry per stroke. Outside Axiom, the capture outline is the only in-world preview.
- Never silently overwrite an export. Structure import to a CAD, online accounts, server components and Cloth Config are out of scope.

## Minecraft 1.21.1

- Both jars build against 1.21.1. The Fabric jar runs on 1.21 and 1.21.1, and the NeoForge jar on 1.21.1, the only version NeoForge 21.1 supports.
- 1.21.1 is obfuscated. Fabric uses `fabric-loom-remap` 1.17 with `loom.officialMojangMappings()`, compiles against mods with `modCompileOnly` and remaps the jar to intermediary names. Loom 1.18 and later need Java 25 to run Gradle. NeoForge runs on Mojang's names as they are.
- The Fabric jar carries intermediary names, so a Fabric mod compiles against the API with `modCompileOnly`, and against NeoForge's jar with `compileOnly`. `ApiSignaturesTest` records the same signatures as on `main`. The game ships JetBrains' annotations but not JSpecify, so `@Nullable` comes from `org.jetbrains.annotations`, the public API's included.
- Fabric API's `+1.21` builds refuse to load on 1.21.1, so the build uses 0.101.2+1.21.1, the first for 1.21.1, and `fabric.mod.json` accepts 0.100.1, the first for 1.21, which has everything the mod uses. NeoForge builds against 21.1.1, its first stable 1.21.1 release. CI also compiles against the newer versions in `build.yml`. `litematica_minecraft` names the 1.21 builds of Litematica and MaLiLib, which also run on 1.21.1.
- 1.21.1 hands input over as plain numbers, such as `keyPressed(int, int, int)` and `mouseClicked(double, double, int)`. `ChromaScreen`, `SettingsScreen` and `Widgets` override those and make the `client/input` events that the tabs, dialogs and controls take as on `main`. A widget in a `Widgets` group still gets the game's own calls. Mouse buttons count from 0.
- The GUI draws through `GuiGraphics`, with `render` and `renderBackground` on a screen and `renderWidget` on a widget. The game calls only `render`, so `ChromaScreen` draws its own background. Text is drawn with `drawString`, `drawCenteredString`, `drawWordWrap`, `renderOutline` and `renderFakeItem`. `g.pose()` is a `PoseStack`, so a 2D translate or scale also takes a z. Buttons take `onPress()`, and the key category is a translation key, `Keys.CATEGORY`. The open screen is `mc.screen`, set with `mc.setScreen`.
- `GuiGraphics` draws a tooltip where it is asked for, so an open scissor would clip it. `DeferredTooltips` holds the last one asked for in a frame, and `ChromaScreen` draws it once everything else is drawn.
- 1.21.1's GUI has no way to ask for a mouse cursor, so `Cursors.request` asks GLFW for a shape each frame and `Cursors.apply` shows it, or the arrow when nothing asked.
- A blit's alpha goes through `g.setColor`. A plain blit leaves blending as it finds it, and a fill before it turns it off, so `Pixels.draw` enables blending around it. `NativeImage.getPixelRGBA` and `setPixelRGBA` take ABGR, so go through `Abgr`, which takes and returns ARGB. 1.21.1 has no `net.minecraft.util.ARGB`, so `dev.chromastudio.client.ARGB` has the part of it the mod uses.
- Models come from `BlockModelShaper` as `BakedModel`, whose quads are asked for by side with a random. Each call gets the same seed, so a model with variants picks the same one. A quad holds its `getSprite()` and `getTintIndex()`, and its vertices packed as eight ints each, position first.
- `BlockState.getValues()` is a map from property to value, with their types apart.
- Tints come from `BlockColors.getColor(state, getter, pos, tintIndex)` through the mod's `TintSource`, with a `BlockAndTintGetter` whose `getBlockTint` returns `resolver.getColor(biome, x, z)`.
- Signs and beds have no block models, only a particle texture, so the index treats them like chests, as blocks a block entity draws.
- `BlockTags.DIRT` holds the blocks of 26.x's `SUBSTRATE_OVERWORLD`.
- 1.21.1 has no shelves, pale moss carpets or copper bars, and its plants are `BushBlock`s. The shelf kind stays in the filters and never matches.
- The game runs on GLFW and ships tinyfd.
- Neither loader has a hook to submit lines after the world, so `Capture` draws its outline to the game's buffers after the entities. Water in front of the outline hides it. A line's width belongs to its render type, so `Capture.box` wraps `RenderType.lines()` in one that sets the width it needs.
- 1.21.1 has no picture-in-picture renderers, so `DioramaPip.draw` draws the wall straight into the screen and `ClientPlatform.renderBlock` draws each block.
- 1.21.1's server refuses a creative player's item drops that come faster than one per 20 of its ticks, and the client has no drop throttler to ask. A creative hotbar fill queues its drops and `HotbarFill.tick` sends one every 40 client ticks, which a server at half speed keeps up with. The queue waits while the game is paused or the player isn't in creative, and Undo or leaving the world drops it. A server further behind, or the creative inventory's own drops in between, can still cost a stack.

## Changes and releases

Use conventional commit prefixes and short PR bodies. Update the [wiki](https://github.com/Neil-001/chroma-studio/wiki) for player-visible changes. Read `listing/README.md` before editing `listing/`.

Develop on `main` for the newest Minecraft version. Port applicable changes to each supported `<line>/stable` branch and record version-specific differences in its `CLAUDE.md`.
1. Open a PR to bump `mod_version` and prepend `# <version>` to `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` for prereleases. Stable branches use the changelog from `main` with any line-specific notes.
2. All three sites publish the changelog verbatim. Open with a short paragraph for players. List merged PRs under `## Features` and `## Fixes`, ending each bullet with its PR number, such as `(#21)`. Omit CI, refactors and docs.
3. After merge, run `gh workflow run release.yml --ref main -f dry_run=true` and check the jars and notes in the summary. Use `-f branches=26.2/stable` for one stable line or `-f branches=all` for all. Publish with the same branches and omit `dry_run`.
4. Run `listing.yml` for description, gallery or icon changes. Update CurseForge by hand using `listing/README.md`.

Keep shared release files in sync with Curve Generator. On this branch `test_release.py` expects `release-java.sh` to read Java 21 from this branch, where `main` expects 25; the release workflow runs `main`'s scripts either way. If a CurseForge upload or its marker fails, check the files page before retrying. To upload the same version again, delete its `curseforge-<version>` artifact first.
