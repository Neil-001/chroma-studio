# Listing

The Modrinth and CurseForge project pages, and the images they share.

- `description.md` is the description, which Modrinth takes as it is. `curseforge.html` is the same text as CurseForge's HTML, written by `art/curseforge.py`, because CurseForge's markdown drops `<center>` while its HTML keeps a centred paragraph. Run `python3 listing/art/curseforge.py` after editing the description; the build checks that the two match. It needs the `markdown` package.
- `media/` holds the images that aren't screenshots, which both pages load from `main`. A new or changed image shows on the pages once it's merged there.
- `gallery/` holds the screenshots for the two sites' galleries. Its `README.md` has each one's title and description, which the `Listing` workflow (`.github/workflows/listing.yml`) uploads to Modrinth along with the description and icon; CurseForge's gallery is uploaded by hand. The description loads some of them from `main` too.
- `art/icon.py` makes the mod's icon from a picture of any size: `python3 listing/art/icon.py picture.png`. It snaps the picture to 40 by 40 pixels, keeps at most 32 colours, and writes it at 320 by 320 to `src/main/resources/assets/chromastudio/icon.png` and `media/icon.png`. It needs Pillow.

After changing the icon, run `gh workflow run listing.yml --ref main` once the change has merged to update Modrinth. Upload `media/icon.png` as the CurseForge project's icon by hand. An icon-only change needs no description or gallery uploads. The mod's bundled icon updates with the next release.
