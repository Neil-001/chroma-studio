## Game behaviour

- The game doesn't count key-bind presses while a screen is open. `ChromaScreen.mouseClicked` and `MatchTab.mouseClicked` check mouse bindings against the event.
- A `CycleButton` rebuilds its tooltip from the builder on each value change. Set it through `Widgets.cycler` and leave disabled tooltips to `Widgets.disable`.
- The game's text fields copy their selection without checking clipboard size. `Widgets.field` routes copy and cut through `Clipboard`. `Widgets.disable` clears `active` when it makes a field uneditable so cut can use that flag.
- `ChromaScreen.extractRenderState` puts title bar tooltips lower because the game can draw them past the top edge. `renderPinned` supplies the size because the game updates it only for open screens.
- The game's button height of 20 is too tall at the minimum window size. `Widgets.H` is 16.

## Window and widgets

- `ChromaScreen.open` leaves Axiom's editor first because Axiom constrains the window to its 3D view. Skip the background blur while `ScreenPicker` is armed because it reads the frame.
- `Window.within` handles a screen shrinking mid-drag. Crossed limits are possible then and `Math.clamp` would throw.
- `ChromaScreen` routes drags and releases for a click the tab took even outside the tab. `Tab.closed` also runs on the way to settings.
- Clear `Handoff` fields after consuming them. Explore keeps its highlight until the player clears it or another list arrives.
- The game rebuilds item tooltips every frame. `Tooltips.add` isolates failures and logs once per block.
- A `Widgets` click or wheel callback may rebuild its group. Return once the callback runs. `SettingsScreen` defers rebuilding rows to keep the widget alive through the click.
- `SettingsScreen` hides `hotbarAxiom` without Axiom and retains the saved choice while the integration is unavailable. Only the fill dialog disables it for passing reasons such as survival mode. Also keep a saved `startTab` from a removed mod in the cycler.
- `ColourDialog` draws its frame in `Theme.FIXED_*` because it previews the custom colour live, and a very light or dark pick would otherwise make the editor itself hard to read. Escape and Cancel restore the starting colour, and a click outside does nothing, so only Done keeps a change. Its third field is HSV so the numbers match the square.
- `ColourDialog` and `MatchTab.typed` ignore text they wrote themselves. Rounding then reading it back would shift the colour.
- Read `Theme` colours while drawing and call `Theme.sync` at the start of a frame. `Theme.LIGHT_FROM` is the OKLab lightness where black and white have equal contrast.
- `SettingsScreen` lights a group's Reset only when a shown row differs from its default. Window and brush memory, and `hotbarAxiom` without Axiom, still reset with their group but don't light the button or appear in the Reset all list, because the player can't see them.
