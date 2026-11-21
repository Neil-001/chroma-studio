The mod's logic that needs no game: colour maths, palettes and gradients, schematic layouts, hotbar writes and line breaking.

`color` and `palette` have their own `CLAUDE.md`. The records of the three small packages are here.

## export

- `ClipboardSize.MAX_BYTES` is 40,000 because the game hands the clipboard its text as UTF-8 through LWJGL's stack of 64 KiB, and text longer than what is left of that stack crashes the game. The margin is for whatever else is on the stack at the time.
- `LitematicBits` packs palette indices the way Litematica's `LitematicaBitArray` does: values are laid end to end and may straddle two longs. Vanilla chunk sections pad each long instead, so their packing can't be reused.

## hotbar

- The hotbars `HotbarWrites` writes are Axiom's, reached through calls that may throw or return without taking the item. So every slot is read before anything changes and read back after the writes, and on any failure what was written is put back before the caller hears of it.
- A set that threw may still have taken, so `HotbarWrites.write` puts that slot back too.
- `HotbarWrites` never writes a slot's own item back to it first to test that it can be set. That would send the server a change for every slot and save Axiom's hotbars for nothing, and it still couldn't catch a slot that ignores writes. Reading back after the writes catches that.
- `HotbarWrites.together` checks that two failures are different objects before `addSuppressed`, because a mod may throw the same exception object again and `Throwable` refuses to suppress itself.

## text

- `LineBreaks` never breaks at a space next to a Chinese or Japanese character. The translations put spaces around numbers and Latin words, as in "2 個のブロック", only for looks, and a break there would leave a number at the end of one line and its counter at the start of the next. A space between two Latin words still breaks.
- `LineBreaks.needed` is false for Korean, which spaces its words, so Korean is left to the caller's own wrapping.
