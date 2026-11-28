# 1.3.0

Chroma Studio adds five tools to Axiom's editor on Fabric. Recolour, shade or lay a gradient across a build, sample a build into a palette, or pick a block's colour. Axiom handles undo, permissions and tool masks for changes to the world. Chroma Studio also adds previews for generating palettes, exporting schematics and filling the hotbar. This release is for Minecraft 1.21.11 on Fabric and NeoForge. The editor tools were tested with Axiom 6.1.5 for Minecraft 1.21.11.

## Features

- Recolour a build into a palette with the Chroma palette brush, keeping its shading, its stairs and slabs, and its windows (#56)
- Preview a whole build's recolour and press Apply to change it in one go (#57)
- Scatter a palette's blocks at random by weight with the palette brush's Scatter mode (#52)
- Pick a block's colour with the Chroma eyedropper in Axiom's editor (#52)
- Match Axiom's active block from the Match tab or with `/chroma axiom block` (#52)
- Copy a palette or gradient as a Script Brush script from the Palettes and Gradient tabs or with `/chroma axiom` (#52)
- Lay a gradient across a build with the Chroma gradient tool. Right-click where it starts and ends, preview the result, and press Apply (#54)
- Choose how far the gradient steps blend. Stairs, slabs and walls take their step's block in the same shape (#58)
- Shade a build darker or lighter with the Chroma shade brush, which finds blocks close in hue and texture (#59)
- Shade with a palette's blocks by stepping to its next darker or lighter block (#70)
- Sample a build into a weighted palette by brushing over it or taking the whole build (#60)
- Put the blocks of a hotbar fill past the ninth into Axiom's hotbars, and undo it (#53)
- Open Chroma Studio from Axiom's editor and return to the editor when you close the window. X picks the block under the mouse in the editor, and the export dialog shows how to import a `.litematic` into Axiom (#55)
- See a whole build's outline before previewing its recolour with the palette brush (#103)
- See the pointed block and its next shade side by side in the shade brush panel (#104)
- Preview and copy colours in the Axiom eyedropper panel, compare the three closest blocks, and revisit recent picks (#105)
- Review sampled blocks and exclusions alongside the build sampler's actions (#106)
- Follow the gradient tool's prompts for setting ends, previewing and applying (#107)
- See which blocks each Analyse finding refers to and what its suggested replacement would change (#94)
- Inspect gradient steps and alternatives beside the strip, with stop controls next to the stops (#95)
- Enter a custom colour with format hints, validation and keyboard controls (#96)
- Name or rename a palette with a Cancel button and checks for empty or duplicate names (#97)
- Preview a gradient before saving and see which saved gradient it would replace (#98)
- Navigate settings by keyboard and see what a reset affects (#99)
- Paste a palette into the import dialog and check it before importing (#100)
- See what a delete or replace affects, with Cancel focused by default (#101)
- See each block's share in the Diorama legend, highlight its cells by pointing at it, and double-click the 3D view to reset its angle (#108)
- Move the settings button to the title bar and use the window's visible move and resize handles (#109)
- Preview the blocks in a generated palette. Generate uses the Match tab's filters and saves the blocks shown in the preview (#110)
- Follow hue around the Explore plot, read its labelled axes and use a minimap while zoomed in (#111)
- Preview a palette from an image and choose its blocks through the Match tab's filters (#112)
- Preview a schematic before exporting it and read the full save and placement results (#113)
- See each block's share on the Palettes tab, with labelled tools and read-only presets marked (#114)
- See clearer in-world readouts, capture dimensions and key hints. The screen picker magnifies the pixels under the pointer (#115)
- Choose a WorldEdit command from labelled buttons and read its preview with each part coloured (#116)
- Search biomes by name or ID, preview grass and leaves colours, and see which palette the Match tab adds to. Match panels leave room for results where space allows (#117)
- Preview which blocks a hotbar fill will put in each destination (#118)

## Fixes

- Copying very long text, such as a long share string or palette notes, reports that the text is too long before writing to the clipboard (#55)
- Comparing blocks whose colours are almost tied always finishes (#76)
- A saved gradient loaded while Chroma Studio reads the blocks again, as after a resource pack change, takes its blocks from the new read (#77)
- The import dialog and `/chroma export` say in your language why a palette can't be read (#82)
- After Export and place, the export dialog shows where the file was saved as well as where it was placed. The hotbar fill dialog counts blocks by destination and warns before any could drop at your feet (#81)
- Tooltips are shorter, the Diorama toolbar wraps when its labels need more room, and texture filter chips stay inside the filters panel (#83)
- The nine translations use the game's own words for the hotbar, shulker boxes and bedrock, and name a block's face once (#86)
- Filter rows scroll beneath the panel title without running into it (#87)
- Scatter and Script Brush scripts leave blocks that hold items or text alone. Tools and scripts preserve water in waterlogged blocks (#71)
- A glass gradient step leaves iron bars and other non-glass panes alone (#91)
- Axiom controls appear only when Axiom is installed on Fabric (#93)
- The Axiom script dialog explains the server's length limit and shows script details in the dialog (#102)

# 1.2.1

The Gradient tab's buttons along the bottom no longer run past the window's edge at its smallest size in German, Spanish, French, Japanese, Polish, Portuguese and Russian. When they don't fit on one line they now take a second. This release is for Minecraft 1.21.11 on Fabric and NeoForge.

## Fixes

- The Gradient tab's buttons along the bottom wrap onto a second line when the window is too narrow for them (#48)

# 1.2.0

Save a gradient and load it back later with the very blocks it had, your swaps included. Other mods can now read your saved gradients and palettes and match colours to blocks: Curve Generator 3.0.0 uses this to paint its shapes with your gradients and palettes. This release is for Minecraft 1.21.11 on Fabric and NeoForge.

## Features

- Save gradients in the Gradient tab and load them back with their settings and every step's block, and let other mods, such as Curve Generator, read them and match colours to blocks (#42)

# 1.1.0

Chroma Studio 1.1.0 is for Minecraft 1.21.11 on Fabric and NeoForge. It is the first release for this version, so it also brings everything in 1.0.0 below. Choose OKLCH, RGB or HSL for the colour controls, change the recipe key in Minecraft's key binds, and choose the order of the window's title buttons.

## Features

- Choose OKLCH, RGB or HSL for the Match tab's colour sliders through settings (#28)
- Change the recipe key in Minecraft's key binds, with a shortcut from Chroma Studio's settings (#27)
- The fullscreen button now sits beside Close by default, with a setting to restore the previous order (#26)

## Fixes

- Update the mod icon (#24)

# 1.0.0

Chroma Studio 1.0.0 is the first release. It finds the blocks closest to a colour, builds palettes and gradients from them, and exports them as schematics. It runs on the client only and never places a block. This version is for Minecraft 26.3 on Fabric and NeoForge.

## Features

- A window on C with four tabs. Drag it, resize it, make it fullscreen, or pin a tab so it stays on screen while you build
- Match: pick a colour, a block or any pixel of the screen and get the closest blocks, compared by whole texture in OKLab, CIEDE2000, CIE76, sRGB or map colour
- A viewing distance slider, since a texture's detail blends into one colour from far away, and an option to match blocks as the game shades them
- Grass, leaves and water matched with the biome's tint, where you stand or in a biome you choose, and a search for the biome that brings a block closest to a colour
- Filters for the kind of block, full cubes, solid and opaque blocks, tinted blocks, light sources, mods, tone, lightness, chroma and how busy the texture is
- Material sets: open a row to see its slab, stairs, wall and the rest, and make a palette of them in one click
- An eyedropper on X that reads the block you look at and copies its hex code
- Gradient: 2 to 32 steps between two or more colours or blocks, with a different block for every step, chosen from the blocks that pass the Match tab's filters. Drag a stop to move it
- Swap a gradient's step: click it for a short list of the next-best blocks, and pick one to keep there while the other steps are chosen around it
- Palettes with weights, tags and notes, a search by name or tag, ten presets, and share strings such as `chroma1:stone,andesite*3/Castle`
- Make a palette from a colour harmony, from a box captured in the world or from a picture
- Analyse a palette for what it lacks, and see it as a wall of blocks, flat or in 3D, orthographic or with perspective
- Export to `.litematic` or vanilla `.nbt` in six layouts, and place the result with Litematica
- WorldEdit patterns and commands from a palette
- Fill the hotbar from a palette or a gradient, and undo it. The blocks past the ninth go into free inventory slots, or into shulker boxes: never, when the inventory is full, or always
- Explore: every block as a dot by hue and lightness
- Item tooltips with a block's colour, kind and distance from your current colour
- Dye leather, leather horse and wolf armour in an exact colour
- Recipe lookup with JEI, REI or EMI
- `/chroma` commands to match, capture, export and place
- A settings screen with a search, and an undo and a reset for each group or for everything, also in Mod Menu on Fabric and the mod list on NeoForge
- Four window colours, Default, Warm, Vanilla and Dark, or a colour of your own, with text that stays readable on any of them
- Translations into Chinese, Russian, Portuguese, German, Spanish, French, Japanese, Korean and Polish, written by a program
- An API for other mods to read block colours, closest blocks and palettes
