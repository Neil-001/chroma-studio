## State and matching

- Keep `MatchState` and `GradientTab.stepsOnShow` on the client thread. Axiom needs the steps after the window closes. Read candidate colours and world tints before handing a solve to a worker.
- Call `MatchState.follow` before building controls. Settings can change without notifying the tab, and an index rebuild replaces block entries. Record recent colours when the player finishes a pick so a drag adds one colour.
- Preserve focus, cursor position and typed text when filtering. In `PalettesTab`, loading a palette fires field responders and must leave it clean. Keep notes and tags unbounded so editing an imported palette preserves its text.
- `GradientTab.restore` keeps saved blocks across settings changes. Resolve pins and steps from one index build, retry across a rebuild and solve if a saved block is missing. Include exact pinned states even outside the filters. The quality thresholds assume OKLab ΔE.
- Pinning skips `Tab.closed`, so `GradientTab` must also clear a held stop on reopening.
- `MatchFilters` excludes invisible models because their item textures would count as block colours. Block entity models have a separate filter. The chroma slider's upper limit means unrestricted because some textures exceed its range.
- `TargetModel` retains the last picker's values through gamut clipping and greys so its handle stays put. Keep hue below 360 at the right edge because `Oklch` wraps 360 to zero.

## Layout and input

- Keep two result rows visible under the Match filter panel when space permits. At minimum size, drop cycler labels before wrapping a row. Pinned drawing can stop while F1 hides the HUD, so `MatchTab.renderPinned` must preserve the open panel.
- Use a specific face for `MatchTab`'s biome swatches. `Entry.layers` rejects `Face.ANY`.
- Keep dialog controls under the pointer when options or status text change. `PaletteDialogs.Axiom` needs room for either script mode. With Axiom installed, the Hotbar dialog reserves room for fills with and without Axiom hotbars and for a wrapped warning.
- `PanelTab` must let a click in one widget group end typing in the other. To restore field focus after a button click, defer it a tick because the game focuses the button after its action runs.

## Saving and exporting

- Only commit a preview the player has seen. `PaletteDialogs.Generate` keeps the seed used for the drawn preview. Image matching discards stale reads and matches again on the client thread when the candidate pool changes. Both dialogs must select an entry allowed by the current filters, even when an excluded state of the same block is closer.
- `PaletteDialogs.Hotbar` waits for the rebuilt dialog to draw before accepting Fill. A queued click could confirm an unseen change. `PaletteDialogs.Confirm` focuses Cancel so Enter can't delete or overwrite by accident.
- `SavedGradients` captures the gradient when replace confirmation opens, since another tab can change it. Keep the typed name on cancellation and rename the loaded gradient only after a successful write.
- Axiom's Import Schematic reads `.litematic` and rejects `.nbt`. `PaletteDialogs.WorldEdit` strips one slash because chat consumes the command slash and WorldEdit uses two.
- `DioramaPanel` and the exporter share filtered entries, arrangement and seed so the default export matches the preview. Reroll changes only the export.

## Drawing

- `DioramaPip` submits its blocks to the game's `FeatureRenderDispatcher`, as the game's entity pictures do, because 1.21.11 hands a picture only a pose and deprecates drawing a block straight into the buffers. 1.21.11 draws with OpenGL, whose depth runs from -1 to 1, so the lens is built with `zZeroToOne` false.
- `DioramaPip` swaps near and far for Minecraft's reversed depth. Keep the scale fixed while turning and allow room for perspective at the near end. Use `ITEMS_FLAT` because `ITEMS_3D` leaves the front dark. Both views need the flat texture for legend swatches.
- `PaletteDialogs.Image` draws the thumbnail with fills because `Dialog` has no close hook to release a texture.
- `ExploreTab` lets points slide off the left edge as hue wraps. Limit the margin to half the hidden arc so it can't pull a visible hue off the plot. Leave room beside the tooltip for the icon because Minecraft draws tooltips after the frame.
- `Thumbnails.draw` displays pixels at or above the alpha cutoff at full opacity so glass on a dark list matches its ranked colour.
