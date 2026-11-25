# Chroma Studio

Chroma Studio is a client-only Fabric and NeoForge mod for Minecraft Java 26.3. It matches colours to blocks and makes palettes, gradients and schematics. With Axiom installed it adds tools to Axiom's editor.

## Build and test

Use JDK 25. Run `./gradlew build` after every change. It builds both jars and runs the Fabric tests. Keep compiler and IDE warnings at zero. Use `_` for unused lambda and catch parameters. `-Xlint:all,-classfile` skips warnings in dependency class files.

- Run `./gradlew :fabric:test` for tests alone. Add `-Dchroma.timing=true` for timing tests.
- Run `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient` for a dev client. Add `-Pviewer=jei` or `-Pviewer=rei` and the newer loader API version from `.github/workflows/build.yml` to try a recipe viewer.
- For a virtual display, unset `WAYLAND_DISPLAY` and set `SDL_VIDEODRIVER=x11`. SDL otherwise prefers Wayland even with `DISPLAY` set.
- Run `GameIndexTest` after changing what the index lists or how it reads models. Use `/chroma debug <block id>` in a dev client to inspect what the index read.

## Boundaries

- Read a package's `CLAUDE.md` before editing it. Record decisions the code can't explain in a sentence or two that names the class. Pair each `CLAUDE.md` with an `AGENTS.md` symlink. Keep source files free of comments except those listed in `SourceRulesTest`, which also checks the links.
- Keep `dev.chromastudio.core` independent of Minecraft. Shared client code uses `ClientPlatform` for loader calls. Wire new hooks into both loaders and keep loader imports under their own `src/` directories.
- Keep both jars client-only. Add no server entrypoints, payloads or registry entries. If a change needs a mixin, list its config only in the metadata of the loader that needs it.
- Only `dev.chromastudio.api` follows semantic versioning. A change to a signature or its meaning needs a new major version.
- Mod Menu, Litematica, MaLiLib, JEI and REI are optional. Refer to them only from classes the other mod loads or after a `ClientPlatform.isModLoaded` check.
- Both builds need Architectury for REI's signatures. NeoForge compiles the Litematica bridge against the Fabric jars because Forgematica keeps the same classes. NeoForge's compile-only Fabric Loader dependency resolves REI's `@Environment` annotation. Keep Fabric Loader imports out of NeoForge and shared code. Keep optional dependencies scoped to their own repositories with `exclusiveContent` so another repository's outage can't fail the build.
- Axiom is optional and Fabric-only. Keep Axiom references in `dev.chromastudio.client.axiom` and enter through `AxiomBridge`. Compile against the pinned client API and ImGui binding in `libs/` and keep both out of the built jars. Use guarded reflection with a player-visible reason for failures outside the API. Put Axiom's jar in `run/fabric/mods/` to try it. Keep that jar out of commits, builds and releases.

## Minecraft and UI

- Minecraft is unobfuscated. Use its names. Draw blocks through `BlockModelResolver` and `BlockModelRenderState`. For world tint, `BlockTintSource.colorInWorld` needs a `BlockAndTintGetter` whose `getBlockTint` calls the resolver with the biome, x and z.
- Request the cursor each frame with `g.requestCursor`. Use `CursorType.createStandardCursor` for shapes missing from `CursorTypes`. Compare mouse buttons with `InputConstants.MOUSE_BUTTON_LEFT` and `MOUSE_BUTTON_RIGHT`.
- `NativeImage.getPixel` and `setPixel` use ARGB. NeoForge draws HUD layers even when F1 hides the HUD, so keep the check in `Hud.render`.
- Grey out inapplicable controls and say why and how to enable them in the tooltip. Build cyclers and spinners through `Widgets`. Use `Widgets.wheelNeedsClick` in scrolling lists. Use `AxiomPanel` for tools in Axiom's editor.
- Size buttons to their text and keep controls from overlapping at 427 by 240 GUI pixels. Take space from existing controls when adding one. Centre text with `Widgets.centred`.
- Take colours from `Theme` except when the colour is data or a black or white mark on a swatch. The game's tooltips and fields stay dark on every theme. Use `Theme.TIP_*` and `Theme.FIELD_BAD` there.
- Use plain sentence case. Name an unlabelled control in one to three words. Other tooltips give a value and range, a swatch detail or a prerequisite the player can't see. Aim for 12 words and go past 20 only with a reason. A status line says what happened and what to do next. Give Axiom panels at most one line of instruction unless there's a refusal, an irreversible consequence or a risk of lost data.
- Put every player-visible string in `assets/chromastudio/lang/en_us.json` and update all nine translations in the same change. New settings need a label and a `.tip` for search.
- Verify that visual fixes preserve other behaviour, for example by comparing pixels. Test new core logic and check the source before changing reference values.

## Player actions

- Giving, dyeing and filling the hotbar require creative mode or operator rights. Grey them out with a reason otherwise. Give items through `Give.stack` and send commands through `ClientPlatform.sendChatCommand`.
- Only tools the player uses in Axiom's editor may change the world. Send those changes through Axiom for its permissions, server checks and undo, with one history entry per stroke.
- Never silently overwrite an export. Outside Axiom, the only in-world preview is the capture outline. Structure import to a CAD, online accounts, server components and Cloth Config are out of scope.

## Changes and releases

Use conventional commit prefixes and short PR bodies. Update the [wiki](https://github.com/Neil-001/chroma-studio/wiki) for changes players will notice. Read `listing/README.md` before editing `listing/`.

Develop on `main` for the newest Minecraft version. Port applicable changes to each supported `<line>/stable` branch and record version-specific differences in its `CLAUDE.md`.

1. Open a PR to bump `mod_version` and add `# <version>` to the top of `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` for prereleases. For a stable branch, use the changelog from `main` with any note for that line.
2. After merge, run `gh workflow run release.yml --ref main -f dry_run=true`. Check the jars and notes in the summary. Pass `-f branches=26.2/stable` for one stable branch or `-f branches=all` for all of them.
3. Run the workflow again with the same branches and without `dry_run` to publish. Run `listing.yml` for description, gallery or icon changes. Update the changed parts of the CurseForge listing by hand using `listing/README.md`.

The changelog goes to all three sites as written. Start with a short paragraph for players, then list merged PRs under `## Features` and `## Fixes`. End each bullet with its PR number, such as `(#21)`. Omit CI, refactors and docs.

The release workflow and `.github/scripts/` describe the inputs, tags and retry behaviour. Keep the shared release files in sync with Curve Generator. If a CurseForge upload or its marker failed, check the files page before retrying. If you intend to upload the same version again, delete its `curseforge-<version>` artifact first.
