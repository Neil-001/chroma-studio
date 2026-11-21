Everything in Chroma Studio that refers to Axiom: its five tools in Axiom's editor, Axiom's active block and extra hotbars, scripts for Axiom's Script Brush, and `/chroma axiom`.

## Entry and failure

- `AxiomBridge` and `AxiomCommand` name no Axiom or ImGui class, in signatures or bodies, because they load on NeoForge and on clients without Axiom. The same holds for every class a script maker reaches (`AxiomScripts`, `RecolourPlan`, `ScatterBlocks`, `GradientShapes`, `FoundBuild`), which `WithoutAxiomTest` checks with Axiom's classes hidden. The bridge's paths that need Axiom enter the other classes only once `AxiomBridge.isPresent` is true.
- The UI asks `AxiomBridge.whyNot` and greys a control out with its answer, rather than checking the mod id. The bridge's script makers call `AxiomScripts` without that guard, as scripts are only text, and the controls that offer them check `whyNot` instead.
- Brigadier merges literals of the same name, so `AxiomCommand` builds the whole `/chroma axiom` tree, and a new subcommand is one more child of `axiom`.
- `AxiomBridge.register` runs after Axiom's own entrypoint, so Axiom's tool list is there to add to. `AxiomTools.register` makes every tool before it adds any, so a tool this Axiom can't make leaves none behind.
- Axiom registers its service implementations in its own jar, so `AxiomTools.service` loads each with the API class's own class loader.
- A throw that reaches `AxiomBridge.fail` turns every Axiom feature off for the session, with a reason the controls show, because the same call would fail again. A `LinkageError`, or any throw while the tools are registered, counts as an Axiom version this Chroma Studio doesn't support; any other throw says the tools stopped and a restart brings them back, as it isn't the version's fault. In the editor the screen is only the 3D view, so `Hud` wraps the notice to the screen's width. `GuardedTool` catches every call Axiom makes into a tool, and `AxiomBridge.tick` catches the tick, so nothing reaches the game loop.
- `GuardedTool` catches the throws that escape a tool, so tool code lets Axiom's throws reach it unless it has a fallback of its own. `AxiomBlock.live` logs a failed read and gives no block, and `BuildPreview.show` shows nothing when it can't take the chunk render override. `AxiomEditor.remember` logs the first of a run of failed reads and forgets the remembered block, so `AxiomBlock.take` says the read failed rather than offering an older block.
- Once the features are off, a throw while putting the tools away is dropped (`GuardedTool.failed`, `AxiomTools.shutDown`, `AxiomBridge.fail`): the first failure is already logged, and Axiom's classes may be what's missing before any tool exists.
- Once the features are off, a tool Axiom still holds does nothing, but its panel is still drawn and says why (`GuardedTool.displayImguiOptions`, `AxiomPanel.Layout.draw`).

## Axiom internals

- `AxiomInternals` holds the handles into one Axiom feature that isn't in its API. Axiom's internals change between releases without notice, so nothing is compiled against them.
- `AxiomInternals` finds each method by its exact signature with `MethodHandles.publicLookup()` the first time the feature is asked about. A missing handle, or a call through one that throws, turns that feature off for the session, and the log says why once.
- New work waits for `AxiomInternals.reason` to be empty. Handles that were found stay usable after a failed call, so an Undo can still put back what a hotbar fill wrote (`AxiomHotbars.unreachable`), which is how the player recovers.
- `AxiomMask.SCRIPT` and `AxiomMask.WINDOW` are optional, and `SCRIPT` reads `lastLuaCompile`, which is private to Axiom's `ToolMaskWindow`. When they're missing, the masks are still followed, and only the check for a script that doesn't compile and the panels' masks line are lost.

## Axiom's editor

- Axiom's API can't say whether its editor is open, so `AxiomEditor` asks Axiom's `EditorUI`. When that fails, the editor counts as closed and the tools send nothing (`AxiomChanges.send` refuses while `AxiomEditor.known` is false).
- `EditorUI.isActive` answers a frame late. `EditorUI.isEnabled` changes as soon as the editor opens or closes, so code that must notice the player's own toggle reads it (`AxiomTools.editorOn`, `AxiomEditor.leaveForWindow`, `AxiomEditor.tick`).
- While its editor is open, Axiom tells the game that the window is only the editor's 3D view, and moves the mouse and takes the first click to match. No screen can be bigger than that view, so `AxiomEditor.leaveForWindow` leaves the editor before Chroma Studio's window opens, as Axiom's own key would.
- `AxiomEditor.tick` takes the player back into the editor once they're in the world again, or once a pinned window is put away. Axiom keeps its selection, tool, views and camera through the trip, as it does for its key.
- When this Axiom can't say whether its editor is open, or a call to leave it throws, the window may open squeezed into the 3D view, so `AxiomEditor.leaveForWindow` warns the player: once a session when the editor can't be told, at once when the call threw.
- `AxiomEditor.tick` goes back into the editor only when the player hasn't left the world, opened the editor themselves, gone to another mod's screen or changed game mode, so it never opens the editor for a player who wasn't in it.
- Outside the editor, and until the editor counts as active, Axiom's API answers stone for the active block. So `AxiomEditor` remembers the block while the editor is active, and `AxiomBlock` takes the remembered one, as Chroma Studio's window is never open inside the editor.
- When this Axiom can't say whether its editor is open, `AxiomBlock` takes the API's answer and adds a note when it's stone.
- Outside Axiom's own drawing, the current ImGui context may be none, and asking it then crashes the game in native code, past any catch. So `AxiomEditor.frame` makes the editor's context current around the one question, as Axiom does, and asks only once `isImGuiContextActive` says there is one.
- Axiom's `pushImGuiContext` returns the context it replaced, for `popImGuiContext`.
- `AxiomTools.visit` counts the times the player has left the editor, so changes queued on one visit are never sent on the next. It goes up whenever the editor is seen switched off after being seen on, and it's read on every tick, every frame a tool is drawn and every time changes are queued or sent.

## How Axiom treats a tool

- Axiom draws only the tool in use and never tells a tool that its editor closed. With "Clear tool when swapping" off, it tells a tool that another was picked only by calling `toolDeselected` on its own entry for the tool.
- `AxiomIcons` passes `toolDeselected` to `GuardedTool.deselected`, and `Drawn` drops a stroke or preview once a whole frame has gone by without the tool drawn.
- `Drawn` counts frames, not time, so a game that stalls never counts as having put the tool away. A frame that can't be told counts as one without the tool.
- `Drawn.missed` is asked between frames, so it needs a gap of two, as a frame drawn since may still be the one the tool is drawn in next.
- Axiom resets a tool before it says another was picked, so a stroke dropped in `reset` is the one left partway. Axiom also resets a tool when the player only switches to another for a moment, so `SamplerTool.reset` keeps the sample.
- Axiom draws a tool every frame, but its panel only while Tool Options is open, so state the panel shows is cleared in `render`.
- Within one frame, Axiom draws Tool Options before it calls the tool's `callConfirm`, so a click in the panel can change the choices between the preview and Apply.
- Axiom gives the chunk render override (`acquireChunkRenderOverrider`) to one tool at a time, and the next tool may take it at once. Its Text tool keeps it while put away with "Clear tool when swapping" off.
- `acquireChunkRenderOverrider` throws when another tool holds the override, so `BuildPreview.show` shows nothing then. It rethrows a `LinkageError` or `VirtualMachineError`, which mean this Axiom or the game is failing.
- `BuildPreview.clear` drops the preview before it releases the chunk render override, so nothing is left to send whatever Axiom does. It counts the override as released only once Axiom has taken it back, so `GuardedTool`'s reset tries again when Axiom throws.
- `GuardedTool.PutAway.putAway` releases the override but keeps what the tool was used on, so the preview comes back with the tool.
- Axiom maps button 1 of `ToolService.isMouseDown` to its own "use tool" key, so `BrushStroke.USE_TOOL` follows a player who rebinds that key.
- Axiom keeps a brush ball's radius for the whole stroke, so a stroke whose Radius was changed after it started has nothing to paint with at the check (`painterNow`).
- Axiom's `BooleanRegion` can't drop a block, so `SamplerTool` rebuilds its outline from the sample when a block leaves it.
- `BrushStroke` shows a stroke not yet sent with a slow pulse, as Axiom's own brushes show theirs.

## Sending changes

- `AxiomChanges.send` is the only caller of `pushBlockRegionChange`. Before it sends, it works the tool's plan out again from the world, the index, the editor and the tool masks as they are then (`QueuedChanges.check`), and sends nothing unless that equals what the player saw.
- Take `AxiomChanges.queue` before reading the index to work out a plan, so an index rebuilt meanwhile refuses the changes rather than vouching for them. The index is rebuilt on its own thread, so `send` compares its version again just before `pushBlockRegionChange`.
- The steps that make a plan run again at send and read everything afresh: the panel's choices, the palette's blocks and the build found in the world (`BrushStroke.Setup`, `GradientTool.layNow`, `PaletteBrushTool.wholeBuildNow`). Only what the stroke is stays the same: the positions its ball reached, the block it started on, the random seed for its choices and its radius.
- `ShadeBrushTool.plan` and `GradientTool.searched` are caches for the panel and the preview, and the steps run at send never use them.
- A brush records every position its ball reached, the ones the masks kept out included, and a whole-build tool asks about every block of the build. Each position is asked of the masks whether or not anything would change, and recorded with what it holds (`QueuedChanges.leave`), so a block that changes under it refuses the stroke.
- A plan that can't be made again at send, as when the world now gives the brush nothing to go on, comes out empty, which never equals a plan that changed something (`BrushStroke.again`, `PaletteBrushTool.makePreview`).
- A refused Apply drops the preview, and no new preview is made until the tool is used again, so a second Apply has nothing to send. Using the tool again drops the old preview at once, so Apply can't send it before the new one is made, and searches for the build afresh, so a retry sees what was built meanwhile.
- `AxiomChanges.send` names the masks in a refusal only when the masks' earlier answers would have made the same plan (`AxiomMask.aloneChanged`), as with a Lua mask's unseeded noise. `aloneChanged` never asks the masks anything new, as that could run a script again and leave an error behind, and it only chooses what the player is told.

## Tool masks

- Axiom's API doesn't apply the tool masks to a tool's changes, so the tools ask Axiom's `MaskManager.getDestMask` for the destination mask, as Axiom's Script Brush does: the mask set under Tool Masks, kept within the selection when there is one, or every block when neither is set.
- Axiom applies the masks only while its Tool Masks window is open, docked or minimised; closed, every block passes, or the selection's when there is one. The tools follow Axiom in that, as its own brushes do, and the panels say which it is (`AxiomMask.windowClosed`).
- When opening or closing the Tool Masks window between a stroke and its sending changes the plan, nothing is sent.
- Ask the masks on the client thread, as Axiom does.
- Masks that can't be read, or a call into them that fails, let no block through, and the tools change nothing until the game restarts. No version of Axiom counts as having no masks.
- A mask whose Lua script fails answers no from then on. Axiom says so only in `ToolMaskWindow.luaExecutionError`, which stays until a fresh context compiles the script or the player closes the message.
- Axiom makes a new message object for each Lua failure, so `AxiomMask.now` compares the error by identity to tell an error its own answer raised from one left earlier.
- A test that saw its own Lua error stays failed for good, because the check's fresh context compiles the script and so clears Axiom's error. A stroke or preview whose masks failed at any point is never sent or shown (`BrushStroke.render`, `GradientTool.preview`, `PaletteBrushTool.makePreview`).
- With the Scripting tab in use and a script that doesn't compile, Axiom keeps no script and applies no mask at all, quietly. So while the masks apply, or the window's state can't be told, `AxiomMask.scriptInvalid` stops the tools until the script is fixed.
- Before the Scripting tab has compiled anything, `lastLuaCompile` is 0 and there's no script either, which isn't an error.
- Each `AxiomMask.Test` has a context of its own, as each of Axiom's brushes does, and a context compiles the script on first use. Axiom gives each context a random seed for a script's `getSimplexNoise` and `getVoronoiEdgeNoise`, so a script that gives its noise no seed answers differently each time.
- Reusing one mask context would run a script edited since as it was, so the tools tell the player to seed the noise instead.
- A test keeps each position's first answer, because a script may answer differently each time it's asked, and one stroke or preview must give a position one answer, or a door could be let through by halves.

## Doors, water and falling blocks

- A door changes as a whole: where the brush reaches one half, the other half changes too, past the edge of the stroke, where the painter's `admit` lets it through the same masks. Where a mask or the build keeps the other half out, neither changes.
- A capped build search can hold one half of a door without the other, so a whole-build recolour admits only positions in the build. `RecolourPlan.change` finds the other half before its first put, in case that put reaches the level it reads.
- The shade brush never changes doors, as a ball's edge could part their halves, and `GradientShapes` leaves them alone, as their halves could fall in different steps.
- Every tool and script changes a block with water in it only into one that keeps the water (`RecolourPlan.keepsWater`), as a player could take the water out with a bucket.
- `RecolourPlan`, `ScatterBlocks`, `GradientShapes` and `ShadeBrushTool` never place a block that falls, such as sand or concrete powder, which would fall out of a wall the first time a block next to it changed.

## Recolour

- `RecolourPlan` works a recolour out once for the script and the palette brush alike, so the two change a build the same way. Each block is decided whole, by the look of its first state in the index, as a script can only ask which block stands somewhere.
- The block a recolour gives keeps the properties both blocks share, such as a stair's facing.
- Without a From palette, the ground (grass, dirt, sand and gravel) is left alone, so a brush that reaches past a build doesn't repaint the lawn.
- The script can't see Axiom's tool masks, so with a mask that cuts a door in two, the script changes both halves where the brush changes neither.
- A palette that lists full blocks recolours full blocks only into those, and a block it lists only a shape of, such as oak stairs beside stone bricks, lends just that shape.
- A solid block turns see-through only when the palette has nothing solid at all, so a palette of glass and sea lanterns leaves walls alone rather than glazing them.
- The same block in another state, such as a log the palette lists on its side, stays as it is and counts as kept. The kept count only chooses what the player is told, because a build mostly of the palette's own blocks hardly changes, which would look like the tool failing.
- In a script's summary the kept count goes last, after the note on servers' limit, which the dialog is sized to show. The palette brush gives it only in Shading, as Closest colour may still swap a block for one of the same colour listed first.
- In Shading, a shape such as a stair or a door counts as its material's full block, as `SampleKind` counts it for a sampled palette, so a build and a palette sampled from it as From find the same main material. Each block knows its family, so variants share a shade and different materials don't.
- The palette brush's default From is the build found from the block it's used on (`FoundBuild`), whose darkest and lightest blocks set the range the palette is spread over. Against every block in the game, a grey stone house would use only the palette's few middle shades.

## Gradient

- `GradientShapes` gives a stair, slab, wall or other shaped block its step's block in the same shape, facing and half, through `RecolourPlan`, so the gradient and the recolour shape blocks alike. When the step's material has no block of that shape, as concrete has no stairs, the shape comes from the block of that shape closest in colour to the step.
- A glass pane follows only a gradient of glass, taking the pane closest in colour to its step's glass, so a window in a stone gradient keeps its glass. A pane is its own kind to a recolour, so the step's pane itself is the recolour's palette, where a stair comes from the full block.
- The index keeps a glass and its pane in separate materials, as their ids differ by more than a shape's suffix, so `GradientShapes.glassPair` pairs them by id. The index counts iron bars as panes, and they never take part.
- `GradientShapes` never borrows a shape from copper that isn't waxed, which would weather to another colour after it was placed. It caches what a step does by the step's state, not its block, as two states of one block, such as a lit and an unlit lamp, differ in colour.
- `GradientTool.ON_TAB` is the empty string, which no saved gradient's name can be, as names are never blank.
- Swap ends sets `GradientTool.reversed` and leaves the ends and `seed` where they are. `GradientLine.between` anchors a straightened line on the first end, and Like the first block keeps to the seed's material, so swapping the ends could move the line or change the material, where Swap ends should only turn the steps round.
- Blend starts at 75, which fades steps into each other on a tower a few blocks a step as on a long wall, while each step still shows pure along its middle.
- The Gradient tab works its gradient out only once it has been shown, so until then the gradient tool's panel offers to open it, but only when no saved gradient it shows is usable.

## Scripts

- Each script reuses its tool's rules for which blocks may change (`RecolourPlan`, `ScatterBlocks`, `GradientShapes.paintable`), so it never replaces a block its tool would leave alone for what the block is. A script can't see Axiom's tool masks, which apply to it as to any Script Brush, and the gradient script fills air with its Paint air box ticked, which the gradient tool never does.
- A script names blocks as the game writes them, and copies properties by value name, as Axiom's `getBlockProperty` gives them and `withBlockProperty` sets them.
- The gradient script paints only full cubes with nothing inside, as a chain or a slab would leave holes in a wall and a chest would come empty. Where the gradient tool gives a stair or slab its step's shape, the script can't reshape a block and leaves it alone.
- What a script leaves alone rests only on block names and properties. A tag is a world's data, which its data packs may change, and a script may run in another world than the one it was made in.
- Servers limit how long a script a player without import rights may run. So a script keeps whole blocks, and where a property belongs only to blocks the tool leaves in every state, such as a turtle egg's `hatch`, it asks for that property in place of the blocks' names when that's shorter.
- Axiom's `isSolid`, given the block id `getBlock` returns, asks about that block's default state, whatever state stands there.

## Hotbars

- Axiom 6.1 keeps its extra hotbars in its internal `HotbarManager`, numbered from 0 with no upper limit, nine slots each, shown nine to a page in the menu its context key opens. That key is the game key mapping `axiom.keybind.context`, Left Alt by default, so no Axiom class is needed to read it.
- The active hotbar is the player's own: writing it sends the creative inventory packet, but only in creative mode, and otherwise does nothing without a word. So callers write the active hotbar themselves, and only the others through `AxiomHotbars.set`.
- The other hotbars live on the client, kept per world or server unless the player chose global hotbars, and Axiom saves them about ten seconds after a change, but only while Axiom is active. Axiom is inactive on a server that doesn't support it, and then saves no hotbar and opens no menu to reach one.
- `AxiomHotbars.INDEX` is looked up apart from the other handles, so Undo can still tell that the player switched hotbars when the rest has changed, and refuse rather than write into the wrong one. `AxiomBridge.activeHotbar` reads it even with the tools off.
- Undo goes ahead after a failed call, as putting back is how the player recovers, but needs creative mode and Axiom active, as a fill does.

## Tool icons and order

- Axiom's API draws every tool added through it with the same pencil. So `AxiomIcons.give` swaps Axiom's entry for each tool in `ToolManager.getTools` for a proxy that answers its own `iconChar` and passes every other call to Axiom's entry.
- Axiom's entry for a tool is the last one in the list with its name, as Axiom's own tools never share Chroma Studio's names.
- Axiom 6.1.5 finds its tools by their place in that list, or by their class for its own tools, and keys only their key bindings by the entry (`keybindMap`), which holds none of the tools added through its API. So the swap loses nothing.
- `AxiomIcons.give` leaves the swap undone when the entry is keyed, and undoes it when anything goes wrong after it: a pencil is better than a tool Axiom can't find.
- The proxy is equal only to itself, as Axiom keys maps by its entries, and rethrows what Axiom's entry throws unchanged, such as the API's refusal to write settings.
- The icon characters are ones Axiom 6.1.5's own tools don't use: a scribble and pencil for the palette brush, two strands twisting from one end to the other for the gradient, a picture for the shade brush, a hand that takes blocks for the sampler, and a marker for the eyedropper. Axiom's font has no pipette, and its arrows over a slope look too like its Elevation tool.
- Another Axiom version could draw something else for the icon characters, which would show a wrong icon but never break a tool.
- `AxiomTools.register` adds the tools that change blocks first, then those that only read them, each group in the order a build goes: paint, gradient, shade; then sampler, eyedropper.

## Panels

- ImGui reads the text of its tooltips and of `text` and `textWrapped` as a printf format, so a `%` in a palette's or block's name would show as garbage. `AxiomPanel` sends all text through `textUnformatted`, and draws a control's own label as it is.
- ImGui wraps text only at spaces, which Chinese and Japanese don't have between words, so `AxiomPanel` breaks their text into lines with `LineBreaks`.
- `AxiomPanel.wrapped` draws broken lines as one ImGui group, so a tooltip set after it belongs to all of them.
- An ImGui label is also its id, and palettes may share a name, as a player's palette may share a preset's, so `AxiomPanel.list` adds each row's place to its id. Two long names often differ only at the end, so `AxiomPanel.fit` cuts a name in the middle and keeps more of its end, and the whole name shows on hover.
- A list that can grow takes the height the options part has left, less what was drawn below it in the last frame (`AxiomPanel.Layout.grow`), so it never pushes the controls under it out of view, and shows at least three rows.
- A click in a panel acts through `AxiomPanel.Layout.later`, after the whole panel is drawn, so nothing drawn later in the same frame reads state the click cleared. Apply still acts at once, as its tool resets itself right after.
- Axiom's panel shows no scrollbar, so `AxiomPanel.Layout` scrolls a tool's options in their own area and holds a bottom part to the panel's bottom edge. The bottom part has the first claim on the space, and holds what the player must read before acting: the masks line in the tools that change blocks, the one status row, then the buttons.
- The status row says one thing, chosen in the tool's `status` method, with a failure of the masks or the editor always first. Details of a preview go in its tooltip, and a receipt stays until the next action, so a greyed Apply gives the next step, not the receipt. A refusal from `AxiomChanges.send` says only what happened, and `AxiomPanel.refusal` adds the tool's next step, and for `refused.masks_differ` the advice on seeding a Lua mask's noise, but only when the script is the same as when the preview or stroke began (`AxiomMask.script`), as an edited script explains itself. A status that is a receipt says so (`AxiomPanel.Status.receipt`), so a greyed Apply never repeats it.
- Axiom keeps no text for a Lua script that doesn't compile, only marks in its editor, so the status's tooltip sends the player to the Scripting tab.
- In the options part, a line that comes and goes with a mode or a result sits below every control, so no control moves. In the bottom part, the lines sit above the buttons, so a button's tooltip never covers them.
- ImGui lays a window out top to bottom, so `AxiomPanel.Layout` keeps the bottom part's height from the last frame. In a panel too short for both parts, the two scroll together.
- `AxiomPanel` closes an ImGui child in `finally`, as ImGui left with an open child fails on Axiom's next call into it.
- A tool reads a slider's value through `AxiomPanel.held` or `AxiomPanel.radius`, as the panel may not have been drawn since the value was set, and a value typed in or remembered could be anything.
- `AxiomPanel.applyCancel` drops the panel's focus after Apply. Otherwise the panel keeps the keyboard, and Axiom's undo key goes to it.
- A panel opens Chroma Studio's window with `ChromaStudioClient.openWindowSoon`, so it opens at the next tick, after Axiom has finished drawing its panels.
- Panels translate their text each frame through `AxiomTools.tr`, and tools keep what they say as a `Component`, so a change of language shows at once. `AxiomTools.count` uses `key.one` for one, and languages with more plural forms word `key` so that it fits any other number.

## Shade brush

- `ShadeBrushTool.STEPS` are OKLab lightness steps. The smallest is about one shade a builder can see.
- The shade brush doesn't change trapdoors, whose textures are see-through, unlike their planks'.
- A full block that turns, such as a log or glazed terracotta, is neither shaded nor a shade. Its colour is read from one face, one way round, so a step judged by it could make a turned one darker when asked for lighter.
- Stairs and other shapes turn too, but they take their full block's shade.
- The shade brush asks the masks about the block under the pointer, at most once a block or every 20 frames, to say when they keep it out. It asks through `AxiomMask.peek`, whose failure only leaves the panel without an answer, as a hover must never stop the tools. It never asks a Lua mask, which could raise an error that stops every tool, or answer differently from the stroke.
- With any block as a shade, the brush takes only plain building blocks from the Building blocks and Coloured blocks tabs, which are empty until a world is joined. It leaves out blocks of coal, lapis and raw ore, which the Building blocks tab lists but no builder takes for a shade of stone, and chiseled blocks, whose carved motif is an accent rather than a shade.

## Scatter and sampler

- Scatter fills a ball, so it places whole blocks: a stair or slab placed at random, always facing the same way, reads as noise. A palette's shapes scatter as their material's full block, and a block with none, such as a lantern, a carpet or a flower, is left out, as a flower in the air breaks and drops.
- Scatter leaves out blocks that hold items, and places leaves persistent, so they don't decay.
- Scatter doesn't paint over a block that holds items, text or what a player put in it, a block with water in it, or one half of a door or a tall plant (`ScatterBlocks.paintsOver`), as what it holds would be lost or the other half left on its own.
- `SampleKind.of` counts a shape as its material's first full block, as a recolour in Shading counts it. `SampleKind` tells the ground and plants from the block itself, so a plant the index doesn't list still can't join a build to its neighbour.
- `SamplerTool` starts the sample over in a new world or dimension only at the first block there that counts, so a missed click there loses nothing.
- `SamplerTool` saves through `PaletteStore.createSaved`, which adds a palette only once its file is written, so a failed save leaves nothing to save twice. The Palettes tab keeps a palette it couldn't write, so the player can retry there.

## Palette brush

- `PaletteBrushTool` refuses a recolour while `fromGone` is true, that is while the From palette is gone, rather than recolour every block but the ground.
- A palette's key in `PaletteBrushTool.keys` comes from its name or file, which a rename changes and a new palette can take. So `PaletteBrushTool.Bound` holds the chosen palette itself, binds it on the first tick, before the player can rename anything, follows it through a rename and saves its new key at the next tick, and once it is deleted, no palette made later under its key takes its place. A click in the panel binds the palette clicked, and setting `paletteKey` or `fromKey` to another key chooses again.
