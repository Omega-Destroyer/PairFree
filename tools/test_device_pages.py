"""The README's summary of the device pages, and the links between the repo's Markdown files."""
import re
import unittest
from pathlib import Path

import device_pages

ROOT = device_pages.ROOT
SKIP_DIRS = {".git", ".gradle", ".idea", "build", "node_modules"}
LINK = re.compile(r"\]\(([^)\s]+)\)|(?:href|src)=\"([^\"]+)\"")


def markdown_files():
    return [p for p in ROOT.rglob("*.md") if not SKIP_DIRS & set(p.relative_to(ROOT).parts)]


def anchors(path):
    """The anchors GitHub gives the file's headings."""
    text = re.sub(r"(?ms)^```.*?^```", "", path.read_text(encoding="utf-8"))
    return {re.sub(r"[^\w\- ]", "", h.strip().lower()).replace(" ", "-") for h in re.findall(r"(?m)^#+ (.+)$", text)}


class DevicePages(unittest.TestCase):
    def test_up_to_date(self):
        for path, start, end, content in device_pages.outputs():
            old, new = device_pages.splice(path, start, end, content)
            self.assertEqual(old, new, f"{path.relative_to(ROOT).as_posix()}: run python tools/device_pages.py")

    def test_summary_links_every_brand_page(self):
        summary = device_pages.summary()
        for brand in device_pages.brands():
            self.assertTrue(brand.PAGE.exists(), brand.PAGE)
            self.assertIn(f"]({brand.PAGE.relative_to(ROOT).as_posix()})", summary)
        self.assertEqual(sorted(p.name for p in device_pages.PAGES.glob("*.md")),
                         sorted(b.PAGE.name for b in device_pages.brands()), "a page for every brand, and no other")

    def test_relative_links_resolve(self):
        for md in markdown_files():
            text = md.read_text(encoding="utf-8")
            text = re.sub(r"(?ms)^```.*?^```", "", text)
            text = re.sub(r"`[^`\n]*`", "", text)
            for m in LINK.finditer(text):
                target = m.group(1) or m.group(2)
                if re.match(r"^[a-z]+:", target):
                    continue
                file, _, anchor = target.partition("#")
                path = (md.parent / file).resolve() if file else md
                where = f"{md.relative_to(ROOT).as_posix()}: {target}"
                self.assertTrue(path.exists(), where)
                if anchor and path.suffix == ".md":
                    self.assertIn(anchor, anchors(path), where)


if __name__ == "__main__":
    unittest.main()
