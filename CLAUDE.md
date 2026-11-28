# Chroma Studio

Chroma Studio is a client-only Fabric and NeoForge mod for Minecraft Java 26.3.

## Build and test

Use JDK 25 and run `./gradlew build` after every change. Javac warnings fail the build, and IDE warnings stay at zero. Use `_` for unused lambda and catch parameters.

- Add `-Dchroma.timing=true` to `./gradlew :fabric:test` for timing tests. Run `GameIndexTest` when changing what the index lists or how it reads models. Inspect real models with `/chroma debug <block id>`.
- To try JEI or REI in a dev client, pass `-Pviewer=jei` or `-Pviewer=rei` and the newer loader API version from `.github/workflows/build.yml`. For a virtual display, unset `WAYLAND_DISPLAY` and set `SDL_VIDEODRIVER=x11`.
- Error Prone runs in every compile, and its findings fail the build. The root `build.gradle` lists the checks that are off. Fix a new finding instead of adding its check to that list.
- Leave Gradle's configuration cache off. It freezes the `sourceRuleLinks` test input, so a broken `AGENTS.md` link would pass.
- Test new core logic. Check the source of reference values before changing them. Verify visual fixes preserve other behaviour, for example by comparing pixels.

## Boundaries

- Read a package's `CLAUDE.md` before editing it. Record conventions and decisions the code can't explain. Name the class and keep the note to a sentence or two. Pair each file with an `AGENTS.md` symlink. Only the exceptions in `SourceRulesTest` may have source comments. `SourceRulesTest` also fails a source tree that names a package the rules below keep out of it.
- Keep `dev.chromastudio.core` independent of Minecraft. Route shared client hooks through `ClientPlatform` and wire them into both loaders. Keep loader imports in their own source trees and mixin configs in the metadata of the loader that needs them. Neither jar may add server entrypoints, payloads or registry entries.
- Only `dev.chromastudio.api` follows semantic versioning. Changing a signature or its meaning requires a new major version. `ApiSignaturesTest` records the signatures and fails when one changes.
- Reference Mod Menu, Litematica, MaLiLib, JEI and REI only from classes the optional mod loads or after a `ClientPlatform.isModLoaded` check. Isolate optional repositories with `exclusiveContent` so an outage can't break unrelated dependency lookups.
- Both loaders need Architectury for REI's signatures. NeoForge compiles against Fabric's Litematica jars because Forgematica keeps the same classes. Its compile-only Fabric Loader dependency resolves REI's `@Environment` annotation.
- Axiom is optional and Fabric-only. Enter `dev.chromastudio.client.axiom` through `AxiomBridge` and keep Axiom references there. Compile against the pinned API and ImGui binding in `libs/` and exclude both from the jars. Guard reflection outside the API and show the player why a call failed. The Axiom jar belongs only in `run/fabric/mods/` for testing.

## Interface and player actions

- Use Minecraft's unobfuscated names. Draw blocks through `BlockModelResolver` and `BlockModelRenderState`. Request the cursor every frame and use `InputConstants` for mouse buttons. Keep the F1 check in `Hud.render` because NeoForge draws HUD layers even with the HUD hidden.
- Grey out inapplicable controls. Explain why and how to enable them in the tooltip. Hide Axiom features unless `AxiomBridge.isPresent`. Use `AxiomPanel` in the editor and `Widgets` for cyclers, spinners and centred text. Use `Widgets.wheelNeedsClick` in scrolling lists.
- Size buttons to their text and make room for new controls at 427 by 240 GUI pixels. Use `Theme` colours except for data and black or white swatch marks. Tooltips and fields stay dark on every theme and use `Theme.TIP_*` and `Theme.FIELD_BAD`.
- Use sentence case. Name an unlabelled control in one to three words. Other tooltips give the value and range, a swatch detail or a prerequisite the player can't see. Aim for 12 words and exceed 20 only with a reason. Status lines say what happened and what to do next. Limit Axiom panel instructions to one line except for refusals, irreversible actions or possible data loss.
- Put player-visible strings in `assets/chromastudio/lang/en_us.json` and update all nine translations together. New settings need a label and a `.tip` for search.
- Giving, dyeing and filling the hotbar require creative mode or operator rights. Use `Give.stack` for items and `ClientPlatform.sendChatCommand` for commands.
- Only tools used in Axiom's editor may change the world. Send changes through Axiom for permission checks and undo, with one history entry per stroke. Outside Axiom, the capture outline is the only in-world preview.
- Never silently overwrite an export. Structure import to a CAD, online accounts, server components and Cloth Config are out of scope.

## Changes and releases

Use conventional commit prefixes and short PR bodies. Update the [wiki](https://github.com/Neil-001/chroma-studio/wiki) for player-visible changes. Read `listing/README.md` before editing `listing/`.

Develop on `main` for the newest Minecraft version. Port applicable changes to each supported `<line>/stable` branch and record version-specific differences in its `CLAUDE.md`.

1. Open a PR to bump `mod_version` and prepend `# <version>` to `CHANGELOG.md`. Use `-beta.1` or `-alpha.1` for prereleases. Stable branches use the changelog from `main` with any line-specific notes.
2. All three sites publish the changelog verbatim. Open with a short paragraph for players. List merged PRs under `## Features` and `## Fixes`, ending each bullet with its PR number, such as `(#21)`. Omit CI, refactors and docs.
3. After merge, run `gh workflow run release.yml --ref main -f dry_run=true` and check the jars and notes in the summary. Use `-f branches=26.2/stable` for one stable line or `-f branches=all` for all. Publish with the same branches and omit `dry_run`.
4. Run `listing.yml` for description, gallery or icon changes. Update CurseForge by hand using `listing/README.md`.

Keep shared release files in sync with Curve Generator. If a CurseForge upload or its marker fails, check the files page before retrying. To upload the same version again, delete its `curseforge-<version>` artifact first.
