"""The EarFun page's table must match data/earfun/devices.json, and data/earfun must hold together."""
import json
import unittest
from pathlib import Path

import gen_earfun_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "earfun"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class EarfunTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "EarFun's code", "Type", "Noise control", "Also", "Status"]

    def row(self, code):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<code>{code}</code>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_earfun_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no EarFun model tried with PairFree yet")

    def test_rows(self):
        pro4 = self.row("TW600")
        self.assertEqual("EarFun Air Pro 4", pro4["Model"])
        self.assertEqual("ANC (standard, balanced, adaptive, adapts to your ears, wind), ambient (full)", pro4["Noise control"])
        self.assertIn("10-band EQ with presets", pro4["Also"])
        self.assertEqual("Not tested", pro4["Status"])
        self.assertEqual("ANC (standard, adaptive, wind), ambient", self.row("TW220")["Noise control"])
        self.assertEqual(("Headphones", "Not yet (BES (or BT8931H))"), (self.row("HP200")["Type"], self.row("HP200")["Status"]))


class EarfunData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        values = load("commands.json")["values"]
        presets = load("eq_presets.json")
        names = [n.lower() for d in devices for n in [d["name"], *d["names"]]]
        self.assertEqual(len(names), len(set(names)), "one entry per Bluetooth name")
        codes = [c for d in devices for c in d["codes"]]
        self.assertEqual(len(codes), len(set(codes)), "each of EarFun's codes once")
        for d in devices:
            self.assertIn(d["kind"], ("earbuds", "headphones"), d["name"])
            if not d["covered"]:
                self.assertNotIn(d["protocol"], ("qualcomm", "earfun"), d["name"])
                continue
            self.assertIn(d["protocol"], ("qualcomm", "earfun"), d["name"])
            if d["auto_off"]:
                self.assertLessEqual(set(d["auto_off"]), set(values["auto_off"][d["auto_off_table"]]), d["name"])
            if d["touch"]:
                actions = values["key_actions"][d["touch"]["map"]]
                for gesture, options in d["touch"]["gestures"].items():
                    self.assertLessEqual(set(options), set(actions), (d["name"], gesture))
                    self.assertNotIn("power_off", options, "power off is never offered")
            if d["eq"]:
                self.assertEqual(len(d["eq"]["hz"]), len(d["eq"]["q"]), d["name"])
                for p in presets[d["eq"]["presets"]]:
                    self.assertEqual(len(d["eq"]["hz"]), len(p["positions"]), (d["name"], p["name"]))
                    self.assertTrue(all(-10 <= x <= 10 for x in p["positions"]), p["name"])
        self.assertEqual(21, len(presets["gain_db"]))


if __name__ == "__main__":
    unittest.main()
