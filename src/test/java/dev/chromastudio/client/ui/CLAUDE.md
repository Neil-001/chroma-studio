Tests of the window, its tabs and widgets.

## Theme

- The default theme's colours in `Theme` are written out by hand, not worked out. `ThemeTest.workingTheDefaultOutGivesNearlyWhatIsWritten` allows 0.02 in OKLab between each written colour and the one worked out from `Theme.DEFAULT_BASE`, which is a just noticeable step.
- In `ThemeTest.anyCustomColourReads`, a middling custom colour leaves the least room: black and white text both reach only about 4.6 contrast on the worst grey. The final `worst < 5` check proves the sweep reaches those colours.

## Text that fits

- `DialogTextTest` measures with the game's bitmap fonts, which the jar holds. The jar's unifont list is empty because the game downloads unifont as an asset, so a Chinese, Japanese or Korean character counts `UNIFONT_MOST`, 9: a unifont glyph advances half its width plus one, and the widest is 16 pixels.
- `DialogTextTest` measures each language with Axiom's windows named in English and again with the names Axiom shows in that language, which `AXIOM_NAMES` copies from Axiom's translations by hand. A name it lacks stays English. The Brazilian Portuguese names keep the typos Axiom's translation has, such as "Arquivc".
