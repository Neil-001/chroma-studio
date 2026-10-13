# 1.0.0

Chroma Studio 1.0.0 is the first release. It finds the blocks closest to a colour, builds palettes and gradients from them, and exports them as schematics. It runs on the client only and never places a block. This version is for Minecraft 26.3 on Fabric and NeoForge.

## Features

- A window on C with four tabs. Drag it, resize it, make it fullscreen, or pin a tab so it stays on screen while you build
- Match: pick a colour, a block or any pixel of the screen and get the closest blocks, compared by whole texture in OKLab, CIEDE2000, CIE76, sRGB or map colour
- A viewing distance slider, since a texture's detail blends into one colour from far away, and an option to match blocks as the game shades them
- Grass, leaves and water matched with the biome's tint, where you stand or in a biome you choose, and a search for the biome that brings a block closest to a colour
- Filters for the kind of block, full cubes, solid and opaque blocks, tinted blocks, light sources, mods, tone, lightness, chroma and how busy the texture is
- Material sets: open a row to see its slab, stairs, wall and the rest, and make a palette of them in one click
- Swap a result: click a row for a short list of the next-best blocks of its kind, and pick one to take its place
- An eyedropper on X that reads the block you look at and copies its hex code
- Gradient: 2 to 32 steps between two or more colours or blocks, with a different block for every step, chosen from the blocks that pass the Match tab's filters. Drag a stop to move it
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
