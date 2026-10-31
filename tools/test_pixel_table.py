"""The Pixel Buds page's table must match data/pixel/devices.json, and data/pixel must hold together."""
import json
import unittest
from pathlib import Path

import gen_pixel_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "pixel"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class PixelTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "Device type", "Connection", "Noise control", "Also", "Status"]

    def row(self, name):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<td>{name}</td>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_pixel_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(5, s["models"], "the Pro's two device types are one model")
        self.assertEqual([], s["tested"], "no Pixel Buds tried with PairFree yet")

    def test_rows(self):
        pro = self.row("Pixel Buds Pro")
        self.assertEqual("<code>4, 5</code>", pro["Device type"])
        self.assertEqual("Maestro", pro["Connection"])
        self.assertEqual("ANC, adaptive, transparency", self.row("Pixel Buds Pro 2")["Noise control"])
        self.assertEqual("plain frames", self.row("Pixel Buds A-Series")["Connection"])
        self.assertEqual("—", self.row("Pixel Buds")["Noise control"])


class PixelData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        types = [d["device_type"] for d in devices]
        self.assertEqual(len(types), len(set(types)), "one row per device type")
        settings = load("commands.json")["settings"]
        for d in devices:
            self.assertIn(d["family"], settings, d["name"])
            self.assertIn(d["transport"], gen.TRANSPORTS, d["name"])
            # The Maestro models take the Allegro message; the plain frames' their own.
            self.assertEqual(d["transport"] == "maestro", d["family"] == "allegro", d["name"])

    def test_settings(self):
        c = load("commands.json")
        for family, fields in c["settings"].items():
            numbers = [f["field"] for f in fields]
            self.assertEqual(len(numbers), len(set(numbers)), family)
            for f in fields:
                self.assertIn(f["type"], ("bool", "enum", "int", "sint32", "message"), f["name"])
                if f.get("never_send"):
                    self.assertNotIn("key", f, f"{family} {f['name']}: never sent, so no row")
                    self.assertNotIn("read", f, f"{family} {f['name']}: never sent, never read")
        for p in c["eq"]["presets"]:
            self.assertEqual(len(c["eq"]["bands"]), len(p["gains"]), p["name"])
            self.assertTrue(all(abs(g) <= c["eq"]["range_db"] for g in p["gains"]), p["name"])
        self.assertTrue(set(c["hold_options"]) <= set(c["touch_actions"]))


if __name__ == "__main__":
    unittest.main()
