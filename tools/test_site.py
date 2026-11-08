"""The web site's pages (tools/gen_site.py) are up to date, and each has a title and a description for search results."""
import re
import unittest
from pathlib import Path

import gen_site

ROOT = Path(__file__).resolve().parent.parent


class Site(unittest.TestCase):
    def test_pages_are_up_to_date(self):
        for rel, text in gen_site.build().items():
            self.assertEqual((ROOT / rel).read_text(encoding="utf-8").replace("\r\n", "\n"), text, f"{rel} is out of date: run tools/gen_site.py")

    def test_each_page_has_a_title_a_description_and_its_own_address(self):
        seen = set()
        for rel, text in gen_site.build().items():
            if rel == "README.md":
                continue
            front = text.split("---")[1]
            title = re.search(r'^title: "(.+)"$', front, re.M).group(1)
            description = re.search(r'^description: "(.+)"$', front, re.M).group(1)
            permalink = re.search(r"^permalink: (\S+)$", front, re.M).group(1)
            self.assertLessEqual(len(title), 70, rel)
            self.assertTrue(70 <= len(description) <= 320, f"{rel}: {len(description)} characters")
            self.assertNotIn(permalink, seen, rel)
            seen.add(permalink)

    def test_the_social_preview_picture_exists_and_names_every_brand(self):
        # GitHub's API cannot upload it (Settings, Social preview), so only the file can be checked; a brand added to the
        # site needs the picture drawn again (tools/gen_social_preview.py) and uploaded again by hand.
        import gen_social_preview

        png = (ROOT / "docs" / "assets" / "social-preview.png").read_bytes()
        self.assertEqual(png[:8], b"\x89PNG\r\n\x1a\n")
        self.assertEqual((int.from_bytes(png[16:20], "big"), int.from_bytes(png[20:24], "big")), (1280, 640))
        self.assertLess(len(png), 1_000_000, "GitHub takes social preview pictures under 1 MB")
        self.assertEqual(len(gen_social_preview.BRANDS), len(gen_site.BRANDS), "a brand was added or removed: redraw the picture")

    def test_the_search_console_verification_file_stays(self):
        # Google Search Console proved the owner holds the site with this file (2026-11-08); removing it drops the verification.
        self.assertEqual((ROOT / "docs" / "googlefe35c1928cb1cdd6.html").read_text(encoding="utf-8").strip(), "google-site-verification: googlefe35c1928cb1cdd6.html")

    def test_the_site_has_its_config_and_robots_file(self):
        config = (ROOT / "docs" / "_config.yml").read_text(encoding="utf-8")
        self.assertIn('baseurl: "/PairFree"', config)
        self.assertIn("jekyll-sitemap", config)
        robots = (ROOT / "docs" / "robots.txt").read_text(encoding="utf-8")
        self.assertIn(f"Sitemap: {gen_site.SITE}/sitemap.xml", robots)

    def test_every_brand_in_the_readme_has_a_page(self):
        pages = {b["page"] for b in gen_site.BRANDS}
        self.assertEqual(pages, set(gen_site.summary()))


if __name__ == "__main__":
    unittest.main()
