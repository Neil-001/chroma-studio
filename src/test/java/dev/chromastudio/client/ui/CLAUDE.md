Tests of the window, its tabs and widgets.

## Theme

- The default theme's colours in `Theme` are written out by hand, not worked out. `ThemeTest.workingTheDefaultOutGivesNearlyWhatIsWritten` allows 0.02 in OKLab between each written colour and the one worked out from `Theme.DEFAULT_BASE`, which is a just noticeable step.
- In `ThemeTest.anyCustomColourReads`, a middling custom colour leaves the least room: black and white text both reach only about 4.6 contrast on the worst grey. The final `worst < 5` check proves the sweep reaches those colours.
