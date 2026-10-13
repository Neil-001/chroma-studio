"""Checks the gallery README parser against the real README and a few shapes it must reject."""
import importlib.util
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("listing_modrinth", HERE / "listing-modrinth.py")
listing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(listing)


class GalleryReadme(unittest.TestCase):
    def test_the_real_readme_lists_every_picture_once(self):
        readme = (HERE.parents[1] / "listing" / "gallery" / "README.md").read_text(encoding="utf-8")
        entries = listing.gallery_entries(readme)
        files = [file for file, _, _ in entries]
        self.assertEqual(len(files), len(set(files)))
        for file, title, description in entries:
            self.assertTrue((HERE.parents[1] / "listing" / "gallery" / file).is_file(), file)
            self.assertTrue(title and description, file)

    def test_a_section_parses_into_file_title_and_description(self):
        readme = "# Gallery\n\nIntro.\n\n## 1. Match a colour\n\n`01-match.jpg`\n\nFirst line.\n\nSecond paragraph.\n"
        self.assertEqual(listing.gallery_entries(readme), [("01-match.jpg", "Match a colour", "First line. Second paragraph.")])

    def test_a_section_without_a_file_is_refused(self):
        with self.assertRaises(ValueError):
            listing.gallery_entries("## 1. Lost\n\nNo file here.\n")

    def test_a_long_description_is_refused(self):
        readme = "## 1. Long\n\n`a.jpg`\n\n" + "x" * 256 + "\n"
        with self.assertRaises(ValueError):
            listing.gallery_entries(readme)


if __name__ == "__main__":
    unittest.main()
