## State and layout

- Keep `MatchState` and `GradientTab.stepsOnShow` on the client thread. The list outlives the window for Axiom's gradient tool. Read candidate colours and world tints on the client thread before handing a solve to a worker.
- Settings can change without notifying a tab. Run `MatchState.follow` before building controls so they reflect those changes. It also resolves block targets again after index rebuilds, which replace the entries.
- Rebuilding widgets loses typed values and cursor positions. `GradientTab` rebuilds filter controls only for layout or filter changes. `PalettesTab` filters its list without rebuilding the search field.
- `PalettesTab.layoutEntries` guards `setValue` with `loading` because field responders would otherwise mark a palette changed while switching to it. Keep tags and notes unbounded so edits don't truncate imported text. Give presets their full text in a tooltip because disabled fields can't scroll.
- Call `MatchState.remember` after a colour is chosen so a slider drag doesn't fill recent colours.

## Gradient and Match

- `GradientTab.restore` keeps the saved blocks even when current settings would pick others. Resolve pins and steps from the same index build. Retry if a new build lands during restore and solve when a saved block is missing.
- Include exact pinned states in `GradientTab.solve` even if filters omit them. The quality badge measures OKLab ΔE because its thresholds are in OKLab.
- A stop press can survive pinning because pinning skips `Tab.closed`. Clear it on reopening as well as on close. Only a left release ends a stop press.
- `MatchFilters.candidates` excludes invisible models such as barriers because their item texture would pass as a block colour. Block entity models such as chests have their own filter.
- The top of the chroma slider means no limit because some textures exceed its range. `MatchTab` scales viewing distance logarithmically because each doubling halves texture detail.
- `TargetModel` keeps the values of the last picker moved so its handle stays put through gamut clipping and greys. `HUE_MAX` stops below 360 because `Oklch` wraps 360 to 0.

## Dialogs and drawing

- `PaletteDialogs.Export` shows the Axiom hint for `.litematic` because Axiom's Import Schematic doesn't read `.nbt`. `PaletteDialogs.WorldEdit` strips one slash because chat omits the command slash and WorldEdit's commands start with two.
- Build the `PaletteDialogs.WorldEdit` buttons before setting the From field, whose responder updates them. In `PaletteDialogs.Hotbar`, wait for the rebuilt dialog to draw before accepting Fill, so queued clicks can't confirm it unseen.
- `PanelTab.mouseClicked` gives both widget groups the click when there's no dialog so clicking one group ends typing in the other.
- `MatchTab.panel` and `GradientTab.drawOver` draw a rule under the filter panel title so the first scrolled row reads as part of the body.
- `DioramaPanel` shares arrangement defaults, filtered entries and seed with the exporter so the default export matches the wall shown.
- `DioramaPip.renderToTexture` creates a resolver because Fabric's is private. It flips the wall about z because GUI y runs down. `lookThroughALens` swaps near and far for the game's reversed depth. `FIT` leaves room for perspective to enlarge the near end.
- `ExploreTab.render` moves the icon to the right 4 pixels before the game moves the tooltip to the left. That margin keeps the icon clear of the tooltip near the screen edge.
- `Thumbnails.draw` treats pixels at or above the alpha cutoff as opaque so glass on the dark list still matches the colour it was ranked by.
