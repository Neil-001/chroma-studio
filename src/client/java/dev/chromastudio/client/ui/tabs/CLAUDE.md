The window's tabs and the state they share.

## Threads

- `MatchState` and `GradientTab.stepsOnShow` are for the client thread only. `stepsOnShow` is a plain static list that outlives the window, because Axiom's gradient tool reads it.
- Colours and candidate blocks are read on the client thread, because a block's tint may need the world. `GradientTab.solve` and `GradientTab.pool` read them there and run only the assignment on the worker thread.
- `MatchState.tint` fixes "Where you stand" at the player's position when it is called, so a search on another thread sees one place.

## Following the settings and the index

- The settings screen changes and resets settings without telling the tabs. `MatchState.follow` and `MatchFilters.followSettings` compare them on each call. A filter left at the old value would go on showing, for example, technical blocks after its setting was reset.
- `MatchTab.init` and `GradientTab.layout` call `MatchState.follow` before they build any control, because the controls are built from the filters.
- Each `BlockIndex` build makes new entries, and an entry kept from an earlier build shows the textures of a pack that has since changed. `MatchState.follow` moves a block target to the current build. `GradientTab.build` tells builds apart by the identity of stone's first entry.

## Keeping what the player types

- Laying controls out again makes new widgets, which drops a number being typed and moves a field's cursor. `GradientTab` lays its filter controls out again only when their area, the `MatchFilters` or the filter state changes. `GradientTab.tick` marks a filter change made while the tab is on show as seen, because it came from the tab's own controls. `PalettesTab` filters its list as each letter is typed without laying the tab out again.
- `PalettesTab.layoutEntries` gives the tags and notes fields no length limit. A palette's tags and notes may be any length, and a limit would cut an imported one on its first edit.
- `MatchState.remember` is called when the player settles on a colour, never while a slider moves, so a drag doesn't fill the recent colours.

## Gradient

- `GradientTab.solve` adds each pinned block to the candidates even when the filters now leave it out or offer another state of it, because the player chose that exact state.
- The quality badge's ΔE is measured in OKLab whatever metric chose the blocks, because `GOOD_UP_TO` and `FAIR_UP_TO` are OKLab bands.
- `GradientTab.clicked` clears the stop press and drag first. A press whose release was lost when the window closed would otherwise turn the next drag into a stop drag.

## Match

- `MatchFilters.candidates` drops a block whose model draws nothing, such as a barrier, because its colour would be the sign on its item. A chest has such a model too, but the "Chests and banners" filter decides on it.
- `MatchFilters.apply` treats a chroma maximum at the slider's top as no limit, because a few textures are more vivid than the slider reaches.
- The viewing distance slider is logarithmic, because each halving of a texture's detail takes twice the distance.
- In `MatchTab.build`, OKLCH's figures are a few pixels wider than half the narrowest column, and RGB's and HSL's a few narrower, so the OKLCH field takes what it needs from the other.
- `TargetModel` keeps the figures of the picker the player moved instead of working them out from the colour, so that picker's handle stays where it was put. A grey has no hue of its own, and an OKLCH colour outside sRGB is shown clipped.
- `TargetModel.CHROMA_MAX` is 0.4, above the 0.32 or so that sRGB itself reaches.
- `TargetModel.HUE_MAX` is 359.99 and `setLch` clamps to it, because `Oklch` turns a hue of 360 into 0 and the handle would jump from one end of the slider to the other.

## Palettes and dialogs

- `PaletteDialogs.Export` shows the Axiom hint only for `.litematic` exports. Axiom's File > Import Schematic reads `.litematic` files but not `.nbt` ones.
- `PaletteDialogs.WorldEdit` strips one slash before `sendChatCommand`. Chat commands go without their first slash, and a WorldEdit command starts with two.
- `PaletteDialogs.WorldEdit.build` creates the "from" field last. Giving it its text calls its responder, which greys the buttons, so the buttons must exist first.
- `PaletteDialogs.Hotbar` ignores a Fill click until the dialog has been drawn since it was last laid out. A click can arrive before the tick that lays the dialog out again, and a second click queued behind one that changed the dialog must wait until the player has seen the change.
- `PanelTab.mouseClicked` gives every click to both the tab's own handler and its widgets, so a click in one group ends typing in the other.

## Drawing

- `DioramaPanel` arranges the wall with `Arrangement`, the export's sizes and the entries this game has blocks for, as `SchematicExporter` does, and hands its layout and seed to the export dialog. Exporting from the panel then writes the wall that shows.
- `DioramaPip.State.yaw` turns the wall's front to the viewer's right and `pitch` tips its top towards the viewer, in degrees, so `DioramaPanel` adds a drag's movement to them and the wall follows the cursor.
- `DioramaPip.renderToTexture` makes its own `BlockModelResolver`, because the game keeps its own private on Fabric. A resolver is only a handle on the model manager.
- The GUI's y runs down the screen, so `DioramaPip.renderToTexture` turns the wall 180 degrees about z before tilting it.
- `DioramaPip.lookThroughALens` replaces the flat projection the game has just set with a perspective one. When it runs, the pose stack holds only the game's translation to the picture's centre. Near and far are swapped because the game's depth runs from far to near.
- `DioramaPip.FIT` is 0.85 because perspective draws a turned wall's near end bigger than its middle, so at full size the wall would run out of the picture.
- `ExploreTab.TIP_OFFSET` and `TIP_MARGIN` copy the game's tooltip placement: 12 pixels right of the cursor, moving to the cursor's left when it would pass the screen's right edge. `ExploreTab.render` puts the block icon on the side the tooltip leaves free.
- `Thumbnails.draw` paints every pixel with any alpha as solid. Glass drawn at its own alpha over the dark list looks far darker than the colour it is matched by.
- The game has no greyed-out item drawing, so `ToolButton` lays a veil over an inactive button's icon.
