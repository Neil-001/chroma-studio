## Export

- `ClipboardSize.MAX_BYTES` leaves room for other data on LWJGL's 64 KiB stack. An oversized UTF-8 clipboard write crashes the game.
- `LitematicBits` follows `LitematicaBitArray`, whose indices may straddle longs. Vanilla chunk sections pad each long.

## Hotbar

- Axiom writes may throw after taking effect or return without changing anything. `HotbarWrites` snapshots every slot first, verifies writes by reading back and includes the failed slot when rolling back. Report any slot whose restoration can't be confirmed.
- Avoid probe writes in `HotbarWrites`. They can send needless packets and save hotbars without detecting ignored writes. A mod can throw the same exception instance twice, which must not be suppressed onto itself.

## Text

- `LineBreaks` keeps spaces next to Chinese or Japanese characters inside a line, so a number stays with its counter in text such as "2 個のブロック". Korean spaces its words and uses the caller's wrapping.
- `Plural.key` uses `.one` for one. Each translation's base key needs wording that fits every other number.
