The window, its tabs and widgets, the HUD and the settings screen.

## The game's behaviour

- The game doesn't count presses for key binds while a screen is open. `ChromaScreen.mouseClicked` and `MatchTab.mouseClicked` test a key bound to a mouse button against the mouse event themselves.
- A `CycleButton` takes its tooltip afresh from its builder every time its value changes, so a tooltip set afterwards is lost on the first click. `Widgets.cycler` takes the tooltip when it builds the button, and `SettingsScreen.control` leaves an option button's tooltip to it. `SettingsScreen.control` also leaves a greyed control's tooltip alone, so it keeps the reason `Widgets.disable` gave it.
- The game copies a field's selection itself, and some fields, such as a palette's notes, hold more than the clipboard takes without crashing. `Widgets.field` sends copies and cuts through `Clipboard`, which refuses one that large. Only `Widgets.disable` makes a field uneditable, and it clears `active` too, so `active` says whether the field can cut.
- The game draws a tooltip a little above the cursor and doesn't keep it on screen. `ChromaScreen.extractRenderState` asks for a title bar button's tooltip lower down, where all its lines fit.
- The game updates a screen's size only while the screen is open, so `ChromaScreen.renderPinned` sets `width` and `height` itself.
- `KeyBindsScreen` can't show one category, so `SettingsScreen.openKeys` scrolls its list to the mod's heading.
- The game's own button height of 20 is too tall for the window at its smallest, so `Widgets.H` is 16.
- `Hud.ABOVE_HOTBAR` puts the mod's readout above the hotbar, the health and food rows and the held item's name.

## Window and screen

- `ChromaScreen.open` makes Axiom's editor step aside through `AxiomBridge.leaveEditorForWindow`, because the editor would squeeze the window into its 3D view.
- `ScreenPicker` reads the frame, so `ChromaScreen.extractBackground` draws no blur while it is armed.
- The default theme's light text reads on the game's blurred backdrop. `ChromaScreen.chrome` fills the fullscreen window's background only for another theme.
- `Window.within` replaces `Math.clamp`, which throws when the limits cross. A screen that shrinks mid-drag can cross them, and then the lower limit wins.
- `ChromaScreen` sends a tab the release and the drags of a click the tab took, even outside the tab's area.
- `Tab.closed` also runs when the window only makes way for the settings screen, and `Tab.init` follows when the settings screen hands back.
- A `Handoff` field's consumer sets it back to null after acting on it, so a handoff is applied once. `Handoff.exploreHighlight` is the exception: the Explore tab keeps it until a new list arrives or the player clears it.
- The game builds an item's tooltip every frame it is shown. `Tooltips.add` keeps one block's failure out of the tooltip so it can't break every frame, and warns once per block so the failure is still seen.

## Widgets and settings

- A right-click or wheel action may rebuild its `Widgets` group, so `Widgets.mouseClicked` and `Widgets.mouseScrolled` return as soon as one has run.
- `SettingsScreen` sets `stale` and lays its rows out again before the next frame, never inside a widget's callback, which would replace the widget under the click.
- The settings' Refresh calls `BlockIndex.rebuild`, which skips the texture cache. The cache is keyed by the packs' names, so it can't tell that a pack's files changed in place.
- On the settings screen, the `hotbarAxiom` toggle is greyed and reads OFF only when Axiom is missing or its hotbars can't be used by this version. The saved choice is kept. Only the fill dialog greys the toggle for passing reasons, such as survival mode.
- When the saved `startTab` names a tab that no longer exists, for example from a removed mod, `SettingsScreen` keeps it listed in the cycler instead of changing it silently.
- `ColourDialog` and `MatchTab.typed` ignore text they wrote into a field themselves. A colour rounded to whole numbers and read back would drift.

## Theme

- `Theme`'s colour fields are statics that change with the theme setting, so code reads them while drawing and never keeps one. `Theme.sync` brings them up to date, and whatever starts a frame of the mod's drawing calls it.
- `Theme.LIGHT_FROM` is the OKLab lightness at which black and white text read equally well.
