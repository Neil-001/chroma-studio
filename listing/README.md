# Listing

The Modrinth and CurseForge project pages, and the images they share.

- `description.md` is the description. Both sites use this one file as it is. It uses no HTML beyond links and images, because CurseForge's markdown drops tags such as `<center>`; the pictures are full width, so nothing needs centring.
- `media/` holds the images that aren't screenshots, which both pages load from `main`. A new or changed image shows on the pages once it's merged there.
- `gallery/` holds the screenshots for the two sites' galleries, which are uploaded by hand. Its `README.md` has each one's title and description. The description loads some of them from `main` too.
- `art/icon.py` makes the mod's icon from a picture of any size: `python3 listing/art/icon.py picture.png`. It snaps the picture to 40 by 40 pixels, keeps at most 32 colours, and writes it at 320 by 320 to `src/main/resources/assets/chromastudio/icon.png` and `media/icon.png`. It needs Pillow.
