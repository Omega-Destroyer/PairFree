"""The looks in data/looks (docs/LOOKS.md): every look is complete, readable and named, and no screen or drawing escapes it."""
import json
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOOKS = ROOT / "data" / "looks"
APP = ROOT / "android" / "app" / "src" / "main"
KOTLIN = APP / "kotlin" / "io" / "github" / "omegadestroyer" / "pairfree"

ROLES = ["background", "panel", "raised", "hairline", "text", "secondary_text", "label", "accent", "on_accent", "destructive"]
HEX = re.compile(r"#[0-9A-Fa-f]{6}")
SYSTEM = re.compile(r"system_(accent[123]|neutral[12])_(0|10|50|100|200|300|400|500|600|700|800|900|1000)")
# The colours tools/gen_device_art.py writes; ui/DeviceDrawing.kt maps each to a role of the look.
ART_COLOURS = {"#101214", "#181B1F", "#1F2328", "#2B3037", "#E8E6E1", "#8A9099", "#E6A23C"}


def looks():
    return {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted(LOOKS.glob("*.json"))}


def luminance(colour):
    def channel(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (int(colour[i:i + 2], 16) for i in (1, 3, 5))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a, b):
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


class LooksTest(unittest.TestCase):
    def test_studio_is_there(self):
        self.assertIn("studio", looks(), "Studio is the default look")

    def test_ids_and_names(self):
        strings = (APP / "res" / "values" / "strings.xml").read_text(encoding="utf-8")
        for look_id, look in looks().items():
            self.assertRegex(look_id, r"^[a-z][a-z0-9-]*$")
            self.assertTrue(look.get("name"), look_id)
            self.assertIn(f'<string name="look_{look_id.replace("-", "_")}">', strings, f"{look_id}: its name goes in strings.xml as look_<id>")

    def test_palettes_are_complete(self):
        for look_id, look in looks().items():
            palettes = [k for k in ("dark", "light") if k in look]
            self.assertTrue(palettes, f"{look_id}: needs a dark or a light palette")
            for key in palettes:
                self.assertEqual(sorted(look[key]), sorted(ROLES), f"{look_id}.{key}")
                for role, value in look[key].items():
                    self.assertTrue(HEX.fullmatch(value) or SYSTEM.fullmatch(value), f"{look_id}.{key}.{role}: {value}")

    def test_text_can_be_read(self):
        """docs/DESIGN.md, Rules for every look: text contrast is at least 4.5:1. Wallpaper colours cannot be checked here."""
        pairs = [(ink, surface) for ink in ("text", "secondary_text", "label", "accent", "destructive") for surface in ("background", "panel", "raised")]
        pairs.append(("on_accent", "accent"))
        for look_id, look in looks().items():
            for key in ("dark", "light"):
                palette = look.get(key)
                if not palette:
                    continue
                for ink, surface in pairs:
                    a, b = palette[ink], palette[surface]
                    if HEX.fullmatch(a) and HEX.fullmatch(b):
                        self.assertGreaterEqual(contrast(a, b), 4.5, f"{look_id}.{key}: {ink} {a} on {surface} {b}")

    def test_fonts_are_known(self):
        known = set(re.findall(r'"(\w+)" ->', (KOTLIN / "ui" / "theme" / "Look.kt").read_text(encoding="utf-8"))) | {"system"}
        for look_id, look in looks().items():
            for key in ("text", "numbers"):
                self.assertIn(look["type"][key], known, f"{look_id}.type.{key}: add the font to fontFamily() in Look.kt")
            self.assertIsInstance(look["type"]["label_caps"], bool)

    def test_shapes_and_art(self):
        for look_id, look in looks().items():
            for key in ("hairline", "corner", "small_corner"):
                self.assertGreaterEqual(look["shape"][key], 0, f"{look_id}.shape.{key}")
            self.assertIsInstance(look["shape"]["panel_border"], bool)
            self.assertEqual(look["art"]["style"], "line", f"{look_id}: the only drawing style so far")
            self.assertGreater(look["art"]["stroke"], 0)

    def test_drawings_hold_only_role_colours(self):
        """A device drawing's colours stand for roles (ui/DeviceDrawing.kt); any other colour would stay Studio's in every look."""
        for path in (APP / "res" / "drawable").glob("device_*.xml"):
            found = {c.upper() for c in re.findall(r'Color="(#[0-9A-Fa-f]{6})"', path.read_text(encoding="utf-8"))}
            self.assertLessEqual(found, ART_COLOURS, path.name)

    def test_no_colour_outside_the_looks(self):
        """Screens, the widget and the notification take colours from the look: no colour literal in the app's Kotlin or layouts."""
        allowed = {"DeviceDrawing.kt"}  # the role keys of the drawings
        for path in KOTLIN.rglob("*.kt"):
            if path.name in allowed:
                continue
            self.assertNotRegex(path.read_text(encoding="utf-8"), r"\bColor\(0x", f"{path.name}: take the colour from Look.colors")
        for path in list((APP / "res" / "layout").glob("*.xml")) + list((APP / "res" / "drawable").glob("widget_*.xml")):
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("@color/", text, path.name)
            self.assertFalse(set(re.findall(r"#[0-9A-Fa-f]{6,8}", text)) - {"#FFFFFFFF"}, f"{path.name}: white shapes only, tinted by WidgetLook")


if __name__ == "__main__":
    unittest.main()
