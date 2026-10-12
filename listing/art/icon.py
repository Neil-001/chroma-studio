#!/usr/bin/env python3
"""Turns a picture into the mod's icon: 40 by 40 pixel art, shown at 320 by 320.

    python3 listing/art/icon.py picture.png

The picture can be any size. It is cut into a 40 by 40 grid and each cell becomes the colour most of its pixels
have, which undoes the blur an image generator or a resize leaves on pixel art. The result is cut down to at most
32 colours and written eight times larger, each pixel a block of 8 by 8, to the mod's resources and to
listing/media. Needs Pillow.
"""
import os
import sys
from collections import Counter

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUTPUTS = [os.path.join(ROOT, "src/main/resources/assets/chromastudio/icon.png"), os.path.join(ROOT, "listing/media/icon.png")]
GRID, SCALE, COLOURS = 40, 8, 32


def pixels(art):
    """An RGBA picture's pixels, row by row."""
    raw = art.tobytes()
    return [tuple(raw[i:i + 4]) for i in range(0, len(raw), 4)]


def snap(picture):
    """The picture as GRID by GRID pixels, each the most common colour of its cell. A see-through pixel counts as clear."""
    picture = picture.convert("RGBA")
    width, height = picture.size
    pixels = picture.load()
    out = Image.new("RGBA", (GRID, GRID))
    for gy in range(GRID):
        # A cell's edges by proportion, so a picture whose size isn't a multiple of the grid still divides evenly.
        top, bottom = gy * height // GRID, max((gy + 1) * height // GRID, gy * height // GRID + 1)
        for gx in range(GRID):
            left, right = gx * width // GRID, max((gx + 1) * width // GRID, gx * width // GRID + 1)
            counts = Counter()
            for y in range(top, min(bottom, height)):
                for x in range(left, min(right, width)):
                    r, g, b, a = pixels[x, y]
                    counts[(r, g, b, 255) if a >= 128 else (0, 0, 0, 0)] += 1
            out.putpixel((gx, gy), counts.most_common(1)[0][0] if counts else (0, 0, 0, 0))
    return out


def quantise(art):
    """The art in at most COLOURS colours, a clear one included if it has any. Art with few enough is left as it is."""
    colours = set(pixels(art))
    if len(colours) <= COLOURS:
        return art
    clear = any(c[3] == 0 for c in colours)
    reduced = art.convert("RGB").quantize(colors=COLOURS - (1 if clear else 0), method=Image.Quantize.MEDIANCUT,
                                          dither=Image.Dither.NONE).convert("RGBA")
    if clear:
        reduced.putalpha(art.getchannel("A"))
        # A clear pixel keeps no colour of its own, so it can't count as another.
        reduced.putdata([p if p[3] else (0, 0, 0, 0) for p in pixels(reduced)])
    return reduced


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    art = quantise(snap(Image.open(sys.argv[1])))
    icon = art.resize((GRID * SCALE, GRID * SCALE), Image.Resampling.NEAREST)
    for path in OUTPUTS:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        icon.save(path)
        print(f"{os.path.relpath(path, ROOT)}: {icon.width} by {icon.height}, {len(set(pixels(art)))} colours")


if __name__ == "__main__":
    main()
