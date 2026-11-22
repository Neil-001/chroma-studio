What the mod does for the player in the game: reading colours off blocks and the screen, giving and dyeing items, filling the hotbar and looking up recipes.

## Clipboard and file dialogs

- `Clipboard.copy` is the only way the mod puts text on the clipboard. Text past `ClipboardSize.MAX_BYTES` would crash the game, so it is refused and the caller shows the player why.
- `FileDialog.choose` hands `extensions` to SDL as they are, so they are separated by semicolons with no leading dot, such as `png;jpg`.
- Every caller of `FileDialog.choose` also offers a field to type a path into, because some systems bring up no dialog.
- SDL hands `FileDialog`'s callback a null list on an error, such as a system with no dialog to show, and an empty list when the player cancels or picks nothing. Only the null list calls `failed`.
- `FileDialog` frees a dialog's callback when the next dialog opens. A callback can't be freed from inside its own call, nor before its dialog answers, however long the player leaves it open.

## Reading colours

- `Eyedropper.press` reads the block under the mouse in Axiom's editor, because the editor hides the crosshair and points with the mouse.
- `Eyedropper.read` turns a Minecraft `Direction` into a core `Face` by ordinal, so `Face` must keep the order of `Direction`.
- `ScreenPicker` can read only the last frame shown, GUI and all, because the game lays out a frame's GUI before it draws the world. So the window isn't drawn while the picker is armed, and a click counts only after `SETTLE` frames, once the window is out of the frame that is read.
- The game's frame runs from the bottom up, so `ScreenPicker.read` flips its rows. `ScreenPicker.read` reads nothing from a frame whose pixels aren't four bytes, and it decodes the four bytes as the `RGBA8_UNORM` channels of the game's main frame.
- `PaletteBlocks.queryFace` takes the north face for the Side setting, because a block's four sides nearly always look alike.
- `PaletteBlocks.tint` reads where the player stands unless the biome setting names one biome, because a range over all biomes has no one tint.

## Capture

- `Capture` clamps a box's height to the world, checks each horizontal side against `MAX_VOLUME`, and checks their product before it multiplies by the height, because three sides typed into the command can multiply past what a `long` holds.
- `Capture` steps through a box by offset from its corner, because a loop up to a coordinate at the very end of the `int` range would never end.

## Giving and dyeing items

- `Give.commandItem` skips a component whose type has no codec. Such a component is only ever sent over the network, so no command can set it.
- `Dye` lists the vanilla dyeable items by name because 26.3 has no `dyeable` item tag. The `crafting_dye` recipes decide what can be dyed, and a client isn't sent them. `Dye` still reads the tag, so a mod or data pack that fills it is heard.

## Hotbar fill

- `HotbarFill.fill` plans against its own copy of the inventory, updated as each command is sent. An operator's commands run on the server later, and `/give` goes by the inventory as it will be once the earlier commands have run.
- `/give` first tops up a stack of the same block and drops only what the inventory can't hold. So `HotbarFill.roomFor` looks for that stack in the game's own order, the held slot, then the off hand, then slots 0 to 35, and that slot is remembered for Undo.
- The server ignores a creative player's drops that come too fast, and the client keeps the same count, so `HotbarFill.drop` stops once the player's drop throttler is over its threshold.
- When a fill has more than nine blocks, `HotbarFill.fill` goes by `intoAxiom` as the player was told it and fills nothing if that is no longer so, because Axiom's hotbars can come or go between the dialog and the fill.
- `HotbarFill.whyNoFill` refuses a fill while a failed one waits for Undo, because a new fill would take the place of what Undo has to put back. Undo keeps only the slots it couldn't put back, so a slot the player has used since isn't written again.
- `CommandReplies` hides one of the game's success lines for each command a fill sent, matched by translation key, and gives up on the count after `TIMEOUT` ticks of silence. It never hides an error line, so a command the player types in that time can lose its reply.
- `CommandReplies.SUCCESS` also lists `commands.item.entity.set.success.single`, which 26.3's own server never sends, in case another server still does.

## Axiom hotbars

- Axiom swaps the player's hotbar for one of its own, so the hotbar a fill wrote may not be the one in hand at Undo. `HotbarFill` keeps what a fill replaced by Axiom's number for that hotbar, and Undo writes it back there, through Axiom when that hotbar is no longer held.
- `HotbarFill` names Axiom's hotbars to the player from 1, as Axiom's menu shows them.
- `HotbarFill.fillAxiom` treats a write that quietly didn't take as a sign of a changed Axiom, the same as one that threw, and reports it to `AxiomBridge.hotbarsFailed`.
- `HotbarFill.undo` reads back each slot it writes through Axiom's hotbars. That works because `whyNoUndo` allows an Undo through Axiom in creative mode alone, where every write takes at once. An operator in survival undoes an ordinary fill through commands.

## Recipe viewers

- Only JEI loads `JeiHook`: on Fabric through the `jei_mod_plugin` entrypoint in `fabric.mod.json`, on NeoForge by its annotation. Nothing in the mod refers to it.
- `RecipeViewers.jeiRuntime` is an `Object` so `RecipeViewers` loads without JEI. Only the inner `Jei` class casts it.
- `RecipeViewers.Emi` calls `EmiApi.displayRecipes(EmiStack.of(stack))` by reflection, because EMI has no release for this Minecraft to compile against. It has not been run against a real EMI.
