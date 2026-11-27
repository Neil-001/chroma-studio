## Clipboard and file dialogs

- Send text through `Clipboard.copy` and show refusals. Exceeding LWJGL's clipboard stack space crashes the game.
- `FileDialog` uses SDL extensions without dots, separated as in `png;jpg`. Keep path entry available when no dialog opens.
- SDL returns a null file list on error and an empty list on cancellation. Keep the `FileDialog` callback alive until it returns.

## Reading colours

- `Eyedropper.press` uses the pointer in Axiom's editor because Axiom hides the crosshair. It reads the clipboard back because the game logs refused writes and carries on.
- `ScreenPicker` reads the last frame, including its GUI, so hide overlays and wait for the frame to settle before picking. The main frame's `RGBA8_UNORM` rows run bottom to top.
- At screen edges the pointer can be off-centre in the loupe.
- Keep the reticle clear of sampled pixels. Skip preview reads over the previous frame's reticle or loupe. Bounds use the integer GUI scale for drawing and framebuffer ratio for reads, which can differ.
- `PaletteBlocks.tint` uses the player's location for biome ranges because they have no single tint.

## Capture and items

- In `Capture`, even the horizontal area can overflow a `long`. Bound the sides before multiplying them and the area before multiplying by height. Iterate by offset so a box ending at `Integer.MAX_VALUE` terminates.
- `Give.commandItem` skips components without a codec because commands can't set network-only components.
- `Dye` lists vanilla dyeable items by name because Minecraft 26.3 has no `dyeable` tag and the server doesn't send its `crafting_dye` recipes. Keep reading the tag for mods and data packs.

## Hotbar fill

- `HotbarFill.fill` plans against an inventory copy because operator commands execute later. `roomFor` mirrors `/give` stack order so Undo can find changed slots. Creative drops obey the game's drop throttler.
- Honour the confirmed `intoAxiom` choice even if Axiom hotbars become unavailable while the dialog is open.
- Refuse new fills while a partial fill has slots left to undo. Another fill would replace the undo record.
- `CommandReplies` can suppress a matching success reply to a command the player types during its reply window.
- `HotbarFill` records the Axiom hotbar number for Undo across swaps. Axiom Undo requires creative mode for immediate readback. Operators can undo ordinary fills through commands.

## Recipe viewers

- Let JEI load `JeiHook` and keep JEI types out of `RecipeViewers.jeiRuntime` so it can load without JEI.
- `RecipeViewers.Emi` uses reflection because EMI has no 26.3 release to compile against. This path has yet to run against EMI itself.
