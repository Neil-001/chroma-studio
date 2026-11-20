# 1.3.0

Chroma Studio now works with Axiom, the building editor, on Fabric. In Axiom's editor, its tools can recolour, shade or lay a gradient across a build, and sample one into a palette. They change blocks only when you use them, and only through Axiom, so Axiom's undo and permissions apply. The rest of Chroma Studio still places nothing, and without Axiom the Axiom controls are greyed out with the reason. This release is for Minecraft 26.3 on Fabric and NeoForge. The Axiom features need Fabric, and were made with Axiom 6.1.5.

## Features

- Copy a palette or gradient as a script for Axiom's Script Brush, from the Palettes and Gradient tabs or with `/chroma axiom`. Match Axiom's active block from the Match tab, and pick colours with the Chroma eyedropper in Axiom's editor (#52)
- A hotbar fill can put the blocks past the ninth into Axiom's hotbars. The dialog says which hotbars and how many of their slots it replaces, and Undo puts back what it replaced, even after you switch hotbars (#53)
- Lay a gradient across a build with the Chroma gradient tool in Axiom's editor: right-click where it starts and where it ends, see the result in the world, then Apply (#54)
- Opened in Axiom's editor, Chroma Studio's window steps out of the editor and goes back when it closes. The eyedropper reads the block under the mouse there, and the export dialog says how to import a `.litematic` into Axiom (#55)
- Recolour a build into a palette with the Chroma palette brush or a script, keeping its shading. Stairs, slabs and walls keep their shape, windows stay windows, and grass, dirt, sand and gravel are left alone unless you recolour from a palette that has them (#56)
- Recolour a whole build at once, shaded by its own blocks, and limit it with Axiom's tool masks. Scatter places only whole blocks that stay put, and the brush remembers its choices (#57)
- Set how far the gradient tool blends its steps into each other, from hard bands to a fade across each step. Stairs, slabs, walls and fences take their step's block in the same shape (#58)
- Shade a build lighter or darker with the Chroma shade brush, which turns each block into a near shade, close in hue and texture (#59)
- Sample a build into a weighted palette with the Chroma build sampler, by brushing over it or taking the whole build (#60)
- A recolour changes a door as a whole, so a door never ends up in two materials. A fill into Axiom's hotbars that fails partway names the slots it left changed, and Undo puts them back (#62)
- The Chroma tools in Axiom's editor each have their own icon, and the palette brush, gradient tool and shade brush all follow Axiom's tool masks. A stroke or Apply changes everything you saw, or nothing if something changed meanwhile. The gradient tool straightens ends that are nearly in line, and a `%` in a palette or block name shows as written (#63)
- Axiom's tool masks always fail safe: when the tools can't read them, or a mask fails during a stroke, nothing changes and the panel says why (#64)
- A stroke or Apply is worked out again from the world and the tool masks when you let go or press Apply, and changes nothing unless it comes out as you saw it. The panel says when no tool mask applies because Axiom's Tool Masks window is closed (#65)
- A tool's change is also refused when the brush's radius, or an option that changes the result, changed as you confirmed it. A tool mask whose script doesn't compile stops the tools, and a refusal caused by a mask whose noise has no seed says how to fix it (#66)
- Among Chroma's tools in Axiom's grid, the ones that change blocks come first, the shade brush has a clearer icon, and the panels keep their main buttons at the bottom (#67)
- Recolour keeps the blocks the palette already has and puts the build's main material on one of the palette's main blocks. A From palette also changes the stairs and slabs of its blocks. The shade brush never turns a plain block mossy, cracked, chiseled or infested, the scatter script places the same blocks as the brush's Scatter, and the gradient script leaves out steps that aren't full blocks (#68)
- A recolour says how many blocks stay as they are because the palette already has them, in the palette brush and in the script's summary. With Shades set to a palette, the shade brush steps to that palette's next block darker or lighter, so a wall recoloured to Desert can be darkened from sandstone to yellow terracotta (#70)
- Scatter, in the palette brush and as a script, leaves chests, signs, brewing stands, flower pots and every other block that holds items or text as they are, and with Paint air off the scatter script leaves cave air too, and the gradient script leaves alone every block the gradient tool does, such as a brewing stand, a head or a flower pot (#71)

## Fixes

- Copying very long text, such as a long share string or palette notes, no longer crashes the game. It says the text is too long to copy instead (#55)

# 1.2.1

The Gradient tab's buttons along the bottom no longer run past the window's edge at its smallest size in German, Spanish, French, Japanese, Polish, Portuguese and Russian. When they don't fit on one line they now take a second. This release is for Minecraft 26.3 on Fabric and NeoForge.

## Fixes

- The Gradient tab's buttons along the bottom wrap onto a second line when the window is too narrow for them (#48)

# 1.2.0

Save a gradient and load it back later with the very blocks it had, your swaps included. Other mods can now read your saved gradients and palettes and match colours to blocks: Curve Generator 3.0.0 uses this to paint its shapes with your gradients and palettes. This release is for Minecraft 26.3 on Fabric and NeoForge.

## Features

- Save gradients in the Gradient tab and load them back with their settings and every step's block, and let other mods, such as Curve Generator, read them and match colours to blocks (#42)

# 1.1.0

Choose OKLCH, RGB or HSL for the colour controls, change the recipe key in Minecraft's key binds, and choose the order of the window's title buttons. This release is for Minecraft 26.3 on Fabric and NeoForge.

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
