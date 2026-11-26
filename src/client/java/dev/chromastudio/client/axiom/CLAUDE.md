## Loading and failure

- `AxiomBridge`, `AxiomCommand` and every class reached by the script makers must load without Axiom or ImGui. Scripts are text and can be made without Axiom, but their controls and `/chroma axiom` appear only when `AxiomBridge.isPresent`, as Axiom has no NeoForge release. An installed Axiom that fails keeps them, greyed with `AxiomBridge.whyNot`, so the player sees why.
- Register through `AxiomBridge` after Axiom's entrypoint. Construct every tool before adding any, so construction failure leaves no partial set. `AxiomTools.service` uses the API class's loader because Axiom's jar supplies the service.
- Let tool failures reach `GuardedTool` unless the tool can recover. `AxiomBridge.fail` disables the integration for the session and the panels still display the reason. A registration failure or `LinkageError` reports an unsupported Axiom version.
- `AxiomInternals` resolves exact signatures lazily and disables the affected feature on a failed lookup or call. Keep resolved handles usable for recovery, as when Undo needs to restore a hotbar after a failed fill.
- `AxiomMask.SCRIPT` and `WINDOW` are optional. Without `SCRIPT`, compile failures are undetectable and hover treats the mask as scripted. Without `WINDOW`, the panel can't tell whether Tool Masks is open. Destination mask checks still apply.

## Editor and tool lifecycle

- Use `AxiomEditor.enabled` for the player's toggle. Axiom's `EditorUI.isActive` can lag a frame. Refuse changes when the editor's state can't be read.
- Axiom shrinks the game's window and routes clicks to its 3D view. `AxiomEditor.leaveForWindow` uses the editor's own toggle so Chroma Studio can open at full size. Return once the player is back in the world with no pinned window. `AxiomEditor.tick` abandons the return if the player disconnects, leaves creative mode, opens another screen or turns the editor on. Axiom preserves the selection, tool and camera through the toggle.
- Axiom's active-block API returns stone outside the active editor. `AxiomEditor` remembers the block before leaving. A failed read clears the remembered block so the window can't offer a stale one.
- `AxiomEditor.frame` needs Axiom's ImGui context current and must first check `isImGuiContextActive`. Asking ImGui without a context can crash in native code.
- Axiom draws only the selected tool and has no editor-close callback. With "Clear tool when swapping" off, the tool entry's `toolDeselected` is the only swap notification. `Drawn` detects missed draws in frames, so a stall doesn't count as putting the tool away. A gap of one between frames can still be the frame the tool will draw in.
- Axiom can reset a tool on a temporary swap, so `SamplerTool.reset` preserves its sample. A tool renders without its panel open, so clear transient state in `render`. Axiom draws Tool Options before `callConfirm`, so the same frame's click can change a preview's options before Apply.
- Axiom grants one chunk render override at a time. Its Text tool can keep the override across swaps, and trying to acquire it then throws. `BuildPreview` skips the preview if the override is busy and lets linkage and virtual machine errors propagate. Drop the preview before releasing it so a failed release leaves nothing to send. Keep the override handle until release succeeds so reset can retry.
- `ToolService.isMouseDown` button 1 follows Axiom's "Use Tool" binding. Axiom fixes its brush ball's radius when the stroke starts, so refuse a stroke whose radius changed.
- Axiom's `BooleanRegion` can't remove a position. `SamplerTool` rebuilds the outline when removing blocks.

## Sending changes

- Send world changes only through `AxiomChanges.send`. Take `queue` before reading the index for the plan so a rebuild during planning invalidates it. Compare the index version again right before sending because a build can land on another thread. Record the editor visit so a plan can't cross two visits.
- Recompute with the same stroke positions, seed, starting block and radius, using the current world, palette and tool options. Treat the shade and gradient preview caches as display data. Keep every reached position, including positions the masks or painter left alone. See [`QueuedChanges`](../../../../../../main/java/dev/chromastudio/core/palette/CLAUDE.md#plans).
- Drop a refused preview until the player uses the tool again. Start the new search from the current world and invalidate the old preview before doing it, so Apply can't send the old one.
- `AxiomMask.aloneChanged` attributes a refusal to the masks only when their earlier answers would reproduce the plan. It uses recorded answers so explaining the refusal can't run Lua again.

## Tool masks

- Axiom's API does not apply masks to submitted changes. On the client thread, use the destination mask from `MaskManager.getDestMask`, which includes the selection. Axiom applies tool masks while Tool Masks is open, docked or minimised. With the window closed, only the selection still applies.
- Fail closed when reading or calling the mask fails. Axiom reports Lua runtime errors only through `ToolMaskWindow.luaExecutionError`, which a fresh context compiling the script can clear. Keep a stroke failed once its context raised an error. `AxiomMask.now` compares message identity because Axiom creates a new object for each failure. Ignore an old Lua error only when Tool Masks is known closed.
- A compile error on the Scripting tab silently leaves no script and applies no mask. `AxiomMask.scriptInvalid` uses the missing script and nonzero `lastLuaCompile` to distinguish it from a tab that has never compiled. Refuse while the mask applies or the window state is unknown.
- Each `AxiomMask.Test` uses a fresh context so edited Lua recompiles. Axiom seeds `getSimplexNoise` and `getVoronoiEdgeNoise` at random per context unless the script supplies a seed. Keep the first answer per position to give one answer throughout the plan.
- `AxiomMask.peek` is for hover. A failure there only withholds the answer. Skip Lua masks on hover because running one can leave an error and its answer needn't match the stroke's.

## Blocks and scripts

- `RecolourPlan.change` changes doors in pairs, even if the second half is outside the brush ball. Apply the mask and build boundary to both halves. Read the second half before writing the first in case the writer changes the same level. Shade and gradient leave doors alone because the halves could take different steps.
- Every tool and script preserves water in the blocks it changes and avoids placing falling blocks. In `ScatterBlocks.paintsOver`, also protect blocks that hold player data and both halves of doors and tall plants.
- `RecolourPlan` chooses by block for both brush and script. Use the first state in the From palette, or the default state if there is no From palette. Shading and `SampleKind` both count shapes with their material's full block so a sampled palette and its build agree on the main material.
- With Whole build on, `PaletteBrushTool` outlines the build under the pointer before the right-click. It searches positions only, and again only when the pointer moves to a block outside the outline. A search always includes the pointed block, so a capped build can't search every frame. Pointing off the build, a new editor visit, `reset` and `putAway` drop the outline, so changes made meanwhile can't leave it stale.
- `FoundBuild` gives the palette brush a local lightness range. Using every block would confine a stone build to a few middle shades. Keep the ground intact when there's no From palette so brushing past a wall leaves the lawn alone.
- `PaletteBrushTool.Bound` binds the object before it can be renamed. The key can change on rename or be reused after deletion. Keep the binding through rename and refuse a missing From palette so deletion can't broaden the recolour.
- `GradientShapes.glassPair` pairs glass and panes by id because the index puts them in separate materials. Its fallback must also be glass. The index puts iron bars and glass panes in the same kind, and mods can name a `StainedGlassPaneBlock` without the `glass_pane` suffix.
- `GradientShapes` avoids unwaxed copper as a substitute because weathering changes its colour. Cache by state because lit and unlit blocks can take different substitutes.
- Swap ends reverses the steps. Keep `GradientTool`'s endpoints and seed because straightening anchors to the first end and "Like the first block" uses the seed's material.
- `GradientTool.search` seeds "Only blocks like the first" along the gradient line up to the first gap, liquid or excluded ground. Starting only at the first block lets trim cut off the rest. Traversing other materials can reach stone under a footing and spend the search cap on terrain.
- `AxiomScripts` shares block eligibility with the tools. A gradient script covers the positions the Script Brush reaches, can paint air on request and leaves shapes alone because Lua can't resolve their counterparts. Require gradient steps to be full cubes without block entities. The script approximates full-cube eligibility with exclusions and `isSolid(getBlock(...))`, which checks for any collision on the default state.
- The recolour script can't read the tool masks. It changes both door halves with `setBlock` even if the mask admitted only one. Keep Script Brush rules that depend on Axiom's Lua semantics in [the core palette record](../../../../../../main/java/dev/chromastudio/core/palette/CLAUDE.md#axioms-script-brush).

## Hotbars and icons

- Axiom's `HotbarManager` numbers hotbars from 0. Its menu uses the game's `axiom.keybind.context` mapping, so reading the binding needs no Axiom class.
- Write the active hotbar through the player's inventory and only extra hotbars through `AxiomHotbars.set`. Axiom silently ignores active-hotbar writes outside creative. It saves extra hotbars per world or server unless the player chose global hotbars. It saves only while active, about ten seconds after a change.
- Keep `AxiomHotbars.INDEX` independent of the other handles so Undo can still detect a swap after an integration failure.
- Axiom's API gives every custom tool a pencil. `AxiomIcons.give` replaces its entry with an icon proxy and forwards deselection. In 6.1.5, Axiom keys bindings by entry identity and doesn't add API tools to that map. Skip keyed entries and roll back an incomplete swap so an icon failure can't lose the tool.

## Panels

- ImGui treats `text`, `textWrapped` and tooltip strings as printf formats. Draw through `AxiomPanel` so a name containing `%` is safe. Its wrapping supplies Chinese and Japanese breaks and groups the lines for hover. Give duplicate labels separate ImGui ids, as a preset and a player's palette can share a name.
- Defer panel actions through `AxiomPanel.Layout.later` so later controls in the frame don't read state the click cleared. Apply acts immediately because its tool resets immediately after. Release panel focus on Apply so Axiom gets the undo key. Read slider values through `held` or `radius` even when the panel hasn't drawn yet.
- `AxiomPanel.Layout` gives the mask, status and action rows space first. A packed layout, which `PaletteBrushTool` uses, puts those rows straight under the options while everything fits, so its Apply stays beside the choices it confirms. It falls back to pinning them to the bottom when space runs out. A narrow panel can wrap the packed rows under the scrollbar, so it keeps the packed and pinned heights apart and packs only when the packed height fits. A change in the pinned height or the panel's size lets it try packing again. `Layout.flow` instead always draws the actions right after a growing list, which still shrinks to keep them visible. `SamplerTool` uses it because it has no mask row and its actions act on the list. Keep variable text below controls or after a growing list so the list can absorb the change. ImGui lays out top to bottom, so reserve the previous frame's bottom height and let both parts scroll together when space runs out. Close ImGui children in `finally` because an unclosed child breaks Axiom's next draw.
- Put mask and editor failures first in tool status. Keep preview details in the tooltip and mark receipts as receipts so a disabled Apply can explain the next step. Axiom stores no compile error text, so direct the player to Scripting.
- Name Axiom UI through `AxiomNames`, using its keys with English fallback. Axiom 6.1.5 loads translations from `config/axiom/translations.zip` into the game's language. Its Mask and Scripting tabs are English literals. Quote names in translations so they need no inflection.

## Shade and sampling

- `ShadeBrushTool` shades shapes by their full block. It avoids trapdoors because their textures are see-through and excludes rotatable full blocks because a colour from one face can't predict the lightness of another orientation.
- Axiom's raycast loses the block when the pointer enters the panel. `ShadeBrushTool` keeps the last pointed block there and through a stroke, so its preview stays readable. The preview sits under the controls in a slot at least two rows tall so it doesn't jump. The stroke's receipt follows it under its own heading until the next stroke. Its swatches show the pointed face, as the matching face averages grass with its dirt sides.
- Take unrestricted shades from the Building Blocks and Colored Blocks tabs, which are empty before joining a world. Skip coal, lapis and raw ore blocks and carved accents so they don't become shades of plain stone.
- Scatter uses full blocks because a ball of randomly placed stairs or slabs would face one way, and non-full blocks such as flowers can break in midair. Use persistent leaves so they don't decay.
- `SampleKind` checks plants and ground from the block itself so blocks the index omits can't join separate builds. `SamplerTool` starts over on the first valid sample in a new world or dimension so a missed click loses nothing.
- `SamplerTool` keeps its row order during a stroke so rows don't move under the pointer, and re-sorts when the stroke ends. It tints left-out kinds red, and a hovered row's blocks blue, so the view matches the list. The brush cursor has no outline so it can't merge with the sampled outline.
- `SamplerTool` uses `PaletteStore.createSaved` so a failed save adds nothing that could be saved twice. The Palettes tab keeps unsaved edits for retry.
