<center>

<a href="https://fabricmc.net/"><img alt="Supported on Fabric" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg"></a>
<a href="https://neoforged.net/"><img alt="Supported on NeoForge" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg"></a>
<a href="https://modrinth.com/mod/fabric-api"><img alt="Requires Fabric API" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg"></a>
<a href="https://github.com/Neil-001/chroma-studio"><img alt="Source on GitHub" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>

</center>

Chroma Studio finds the blocks closest to a colour. Pick a colour, or point at a block, and it lists every block in the game by how close its texture comes, modded blocks and resource packs included. From there it builds palettes and gradients, and exports them as schematics.

<center>

<img alt="The Chroma Studio window over a village. On the left a colour picker set to teal. On the right a list of the twelve closest blocks, from warped shelf to cyan stained glass, each with its distance, kind and texture." width="100%" src="https://raw.githubusercontent.com/Neil-001/chroma-studio/main/listing/gallery/01-match.jpg">

*The Match tab. Each row has the block's distance from the colour, a badge for how busy its texture is, and the texture as it looks from the viewing distance you set.*

</center>

It compares whole textures, not only average colours, so a speckled block and a flat block of the same average don't count as the same. It matches in OKLab by default, which follows what the eye sees more closely than RGB does. Grass, leaves and water are matched with the tint of the biome you stand in, or of any biome you choose.

The mod runs on your client only. It never places or changes a block. A server doesn't need it.

## How to use it

Press **C** for the window. It has four tabs. You can drag it, resize it, make it fullscreen, or pin a tab so it stays on screen while you build.

### Match

1. Pick a colour with the square, the sliders or a hex code. Or press **Block** to pick a block in the world, or **Screen** to click any pixel of the game. The slot at the top left takes the block in your hand.
2. Read the list. Click a row to get the block, in creative mode or as an operator. Shift-click copies its id. The **+** button adds it to your palette.
3. Narrow the list with the row of filters: one kind of block, full cubes only, one mod, a tone, a lightness range and more.
4. Click the arrow beside a row to see the other blocks of its material, such as the slab, stairs and wall. One more click makes a palette of them.

Out in the world, press **X** on a block to read its colour. The hex code goes to your clipboard. Shift and **X** opens the window on that block.

<center>

<img alt="The Match tab on cherry planks, with its row opened to show cherry log, wood, slab, stairs, signs, fence, gate and trapdoor beneath." width="100%" src="https://raw.githubusercontent.com/Neil-001/chroma-studio/main/listing/gallery/02-material-set.jpg">

*Matching a block instead of a colour, with its material set open.*

</center>

### Gradient

Give it two or more colours or blocks and a number of steps, from 2 to 32. It finds a different block for every step so that the whole run is as close as it can be. Then fill your hotbar with it, copy the list, save it as a palette or export it.

<center>

<img alt="The Gradient tab with a sixteen-step run from dark blue through red and orange to pale sandstone. A strip shows the colour wanted at each step, with the chosen block below it." width="100%" src="https://raw.githubusercontent.com/Neil-001/chroma-studio/main/listing/gallery/03-gradient.jpg">

*Sixteen steps between three colours. The strip on top is the colour each step wants, and the one below is what its block gives.*

</center>

### Palettes

A palette is a list of blocks, each with a weight. Ten come with the mod, and you can make your own in several ways.

- **Generate** makes one from a colour and a harmony, such as analogous or triadic.
- **Capture** counts the blocks in a box you mark in the world with two presses of **X**.
- **Image** takes the main colours of a picture.
- **Analyse** says what a palette lacks, such as a dark shade or an accent, and **Fix** opens Match on a colour that would fill the gap.
- **Diorama** shows the palette as a wall of blocks, flat or in 3D, mixed by its weights.
- **Export** writes a `.litematic` or vanilla `.nbt` file, as a strip, a wall, a grid of patches or one of the diorama's walls.
- **WorldEdit** writes a pattern such as `70%stone,30%andesite` and the `//set` or `//replace` command to go with it.
- **Hotbar** puts the palette in your hotbar, with the rest in a shulker box. Undo puts back what was there.

**Share** copies a palette as one line of text, like `chroma1:stone,andesite*3,oak_log[axis=y]/Castle`, and **Import** reads one.

<center>

<img alt="The Palettes tab showing the Medieval preset as a wall of stone bricks, cobblestone, spruce planks, dark oak logs and terracotta, drawn in 3D." width="100%" src="https://raw.githubusercontent.com/Neil-001/chroma-studio/main/listing/gallery/05-diorama.jpg">

*The Medieval preset as a 3D diorama. Drag to turn it.*

</center>

### Explore

Every block is a dot, with hue across and lightness up. Scroll to zoom, drag to move, and click a dot to match it. The filters you set in Match apply here too.

<center>

<img alt="The Explore tab: about a thousand coloured dots spread by hue from red on the left to pink on the right, with ten dots outlined." width="100%" src="https://raw.githubusercontent.com/Neil-001/chroma-studio/main/listing/gallery/07-explore.jpg">

*Every block by hue and lightness. The outlined dots are the blocks of one palette.*

</center>

## Commands

- `/chroma #ff8800` and `/chroma oak_planks` open Match on a colour or a block.
- `/chroma capture x1 y1 z1 x2 y2 z2` makes a palette from a box.
- `/chroma export <share string> [layout] [format] [place]` writes a schematic, and `/chroma place <file name>` places one with Litematica.

## Good to know

- Getting blocks, filling the hotbar and dyeing need creative mode or operator rights. Everything else works anywhere, including on servers without the mod.
- Schematics go to Litematica's schematics folder when you have it, and to `schematics` in the game folder otherwise. The mod never overwrites a file.
- With Litematica installed, **Export and place** loads the schematic as a placement where you look, ready to move.
- Item tooltips show a block's colour, kind and distance from your current colour while you hold Shift.
- Chests, banners, shulker boxes and heads have no block model to read, so the list leaves them out unless you turn them on under **More…**
- **Dye…** gives leather armour, leather horse armour or wolf armour in exactly your colour.
- The cog at the top right of the screen opens the settings. On Fabric they are also in Mod Menu, and on NeoForge behind the Config button in the mod list.
- The mod is translated into Chinese, Russian, Portuguese, German, Spanish, French, Japanese, Korean and Polish. A program wrote those translations, so corrections are welcome.

## Works with

| Mod | |
| --- | --- |
| [Fabric API](https://modrinth.com/mod/fabric-api) | Required on Fabric |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Optional. Opens the settings from the mod list |
| [Litematica](https://modrinth.com/mod/litematica), or Forgematica on NeoForge | Optional. Places exported schematics |
| JEI, REI or EMI | Optional. **R** over a block looks up its recipe |

Chroma Studio 1.0 is for Minecraft 26.3.

The [wiki](https://github.com/Neil-001/chroma-studio/wiki) covers matching, palettes, gradients, exports, settings, and how resource packs and modded blocks work.
