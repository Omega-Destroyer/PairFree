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

    def test_every_brand_in_the_readme_has_a_page(self):
        pages = {b["page"] for b in gen_site.BRANDS}
        self.assertEqual(pages, set(gen_site.summary()))


if __name__ == "__main__":
    unittest.main()
