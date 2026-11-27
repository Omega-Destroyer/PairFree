"""The desktop apps' own wording (desktop/strings/<language>.xml, docs/DESKTOP.md) overrides entries of the one strings file."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
DESKTOP = ROOT / "desktop" / "strings"


def names(path):
    root = ET.parse(path).getroot()
    return {e.get("name"): e for e in root if e.tag in ("string", "plurals")}


def places(text):
    """The format places of a text, in the form a translator must keep: ['%s', '%d', '%%']."""
    return sorted(re.findall(r"%(?:\d+\$)?[sd%]", text or ""))


class DesktopStringsTest(unittest.TestCase):
    def test_every_override_names_an_entry_of_the_main_file(self):
        main = names(RES / "values" / "strings.xml")
        for f in sorted(DESKTOP.glob("*.xml")):
            for name in names(f):
                self.assertIn(name, main, f"{f.name}: {name} is not in values/strings.xml")

    def test_an_override_keeps_the_places_of_the_text_it_replaces(self):
        main = names(RES / "values" / "strings.xml")
        for f in sorted(DESKTOP.glob("*.xml")):
            for name, e in names(f).items():
                if name in main and e.tag == "string" and main[name].tag == "string":
                    self.assertEqual(places(main[name].text), places(e.text), f"{f.name}: {name}")

    def test_a_language_file_belongs_to_a_built_in_language(self):
        built = {"en"} | {d.name.removeprefix("values-").replace("-r", "-") for d in RES.glob("values-*")}
        for f in DESKTOP.glob("*.xml"):
            self.assertIn(f.stem, built, f"{f.name} is for a language the app does not have")


if __name__ == "__main__":
    unittest.main()
