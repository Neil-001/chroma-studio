## Game behaviour

- The game doesn't count key-bind presses while a screen is open, so `ChromaScreen` and `MatchTab` check mouse bindings against click events.
- `CycleButton` rebuilds its tooltip on value changes. Create it through `Widgets.cycler` and use `Widgets.disable` for disabled tooltips.
- In `Widgets` and `AxiomPanel` callers, use tooltips to name icon buttons, reveal clipped text, or show a shortcut, consequence or prerequisite the label doesn't convey.
- `ColourDialog` and `MatchTab` share colour field names and format instructions under `colour.chromastudio`.
- The game's fields copy selections without a size check. `Widgets.field` routes copy and cut through `Clipboard`, and `Widgets.disable` clears `active` so cut respects the disabled state.
- `ChromaScreen.renderPinned` supplies screen size because the game updates it only for open screens.

## Window and widgets

- `ChromaScreen.open` leaves Axiom's editor because it constrains the window to its 3D view. Skip background blur while `ScreenPicker` is armed because it reads the frame.
- `Window.gripAt` leaves the grip dots to tab content and resizes from the corner band. The dots overlap editable tab content.
- `Window.within` handles crossed limits when a screen shrinks mid-drag, where `Math.clamp` would throw.
- Clear `Handoff` fields after consuming them. Explore keeps its highlight until the player clears it or another list arrives.
- The game rebuilds item tooltips every frame, so `Tooltips.add` logs failures once per block.
- A `Widgets` click or wheel callback may rebuild its group. Return after it runs. `SettingsScreen` defers rebuilding rows to keep the widget alive through the click.
- `SettingsScreen` hides `hotbarAxiom` without Axiom but retains its saved value. Keep a saved `startTab` from a removed mod in the cycler too.
- `ColourDialog` uses `Theme.FIXED_*` so extreme custom colours don't make the editor hard to read. Only Done keeps changes.
- `ColourDialog` and `MatchTab.typed` ignore their own field writes because rounding on readback would shift the colour.
- `Hud` uses dark tooltip colours on its dark backing. Its panels avoid the crosshair and Axiom's menu bar, and don't count down while a screen covers them.
- Read `Theme` colours while drawing and call `Theme.sync` at the start of a frame. `Theme.LIGHT_FROM` is the OKLab lightness where black and white have equal contrast.
- `SettingsScreen` lights group Reset only for changed visible rows. Hidden window and brush memory and unavailable `hotbarAxiom` still reset, but don't light the button or appear in Reset all.
