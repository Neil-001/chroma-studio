## Clipboard and file dialogs

- Send text through `Clipboard.copy` and show its refusal to the player. Exceeding LWJGL's clipboard stack space crashes the game.
- SDL takes `FileDialog.choose` extensions without dots, separated as in `png;jpg`. Keep path entry available on systems where no dialog opens.
- SDL passes a null file list to `FileDialog` on error and an empty list on cancellation. Keep the callback alive until the dialog answers and the callback returns.

## Reading colours

- `Eyedropper.press` uses the pointer in Axiom's editor because Axiom hides the crosshair.
- `ScreenPicker` reads the last frame, including its GUI. Hide the window and wait `SETTLE` frames before accepting a click. The main frame's `RGBA8_UNORM` rows run from bottom to top.
- `ScreenPicker` reads a 9 by 9 loupe and picks the mean of the 3 by 3 under the pointer. The loupe shifts at the screen's edges, so `centre` takes the pointer's cell from the read's offset.
- `ScreenPicker.reticle` keeps clear of the pixels the loupe reads, at any GUI scale, because the next frame's read would include it.
- `PaletteBlocks.tint` uses the player's location for a range of biomes because the range has no single tint.

## Capture and items

- `Capture.render` draws a wide black outline under the white one, pushed off the block faces, so the box shows on pale blocks and beside the game's block highlight.
- `Capture` bounds the sides and their horizontal product before multiplying by height. Three command arguments can overflow a `long`. Iterate by offset so a box ending at `Integer.MAX_VALUE` terminates.
- `Give.commandItem` skips components without a codec because a command can't set network-only components.
- `Dye` lists vanilla dyeable items by name because Minecraft 26.3 has no `dyeable` tag and the server doesn't send its `crafting_dye` recipes. Keep reading the tag for mods and data packs.

## Hotbar fill

- `HotbarFill.fill` plans against a copy of the inventory because the server executes an operator's commands later. `roomFor` mirrors `/give` stack order so Undo can find the slot that changed. Creative drops obey the game's drop throttler.
- A fill with overflow must honour the confirmed `intoAxiom` choice. Axiom's hotbars can become unavailable while the dialog is open.
- Refuse new fills while a partial fill has slots left to undo. Another fill would replace that undo record.
- `CommandReplies` matches success lines by translation key. A command the player types during that window can lose a matching success reply too.
- Axiom swaps the active hotbar, so `HotbarFill` records the hotbar number and restores the slots there even after a swap. Undo through Axiom requires creative mode so writes can be read back immediately. Operators can undo ordinary fills through commands.

## Recipe viewers

- Let JEI load `JeiHook`. Keep `RecipeViewers.jeiRuntime` free of JEI types so the class can load without JEI.
- `RecipeViewers.Emi` uses reflection because EMI has no 26.3 release to compile against. This path has yet to run against EMI itself.
