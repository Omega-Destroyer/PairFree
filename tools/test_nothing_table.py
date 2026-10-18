"""The Nothing page's table must match data/nothing/devices.json, and data/nothing must hold together."""
import json
import unittest
from pathlib import Path

import gen_nothing_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "nothing"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class NothingTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "Product", "Type", "Noise control", "Also", "Status"]

    def row(self, product):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<code>{product}</code>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_nothing_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no Nothing model tried with PairFree yet")

    def test_rows(self):
        b184 = self.row("B184")
        self.assertEqual("CMF Buds 2 Plus", b184["Model"])
        self.assertEqual("ANC (adaptive, high, mid, low), transparency", b184["Noise control"])
        self.assertEqual("nada", b184["Status"])
        self.assertEqual("ANC (high, low), transparency", self.row("B181")["Noise control"], "the Ear (1)'s two strengths")
        self.assertEqual("—", self.row("B174")["Noise control"], "the Ear (open) has none")
        self.assertIn("LHDC", self.row("B155")["Also"])
        self.assertEqual("Headphones", self.row("B170")["Type"])
        self.assertEqual("Not tested", self.row("B163")["Status"])


class NothingData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        ids = [d["product_id"] for d in devices]
        self.assertEqual(len(ids), len(set(ids)), "one row per product id")
        names = [n.lower() for d in devices for n in d["names"]]
        for d in devices:
            self.assertIn(d["kind"], gen.KINDS, d["name"])
            self.assertIn(d["brand"], ("Nothing", "CMF"), d["name"])
            self.assertRegex(d["product_id"], r"^B\d{3}$")
            self.assertTrue(d["configs"], d["name"])
            for c in d["configs"]:
                self.assertTrue(set(c["anc"]) <= {"high", "mid", "low", "adaptive", "transparency", "off"}, d["name"])
                self.assertTrue(set(c["codecs"]) <= {"lhdc", "ldac"}, d["name"])
                for g in c["gestures"]:
                    self.assertIn(g["device"], (2, 4, 6), d["name"])
        # The Ear (1) alone sends no CRC.
        self.assertEqual(["B181"], [d["product_id"] for d in devices if not d["crc"]])
        repeated = {n for n in names if names.count(n) > 1}
        self.assertEqual({"cmf buds pro 2"}, repeated, "the B172 and B187 share a name; the product id tells them apart")

    def test_commands(self):
        data = load("commands.json")
        codes = [c["code"] for c in data["commands"]]
        self.assertEqual(len(codes), len(set(codes)))
        by_name = {c["name"]: c for c in data["commands"]}
        for c in data["commands"]:
            self.assertRegex(c["code"], r"^[0-9A-F]{4}$")
            prefix = {"get": "C0", "set": "F0", "event": "E0", "debug": "FC"}[c["kind"]]
            self.assertTrue(c["code"].startswith(prefix), c["name"])
            if c["kind"] == "debug" or "OTA" in c["name"]:
                self.assertTrue(c.get("never_send"), c["name"])
        for name in ("RESTORE_FACTORY_SETTING", "SET_CALIBRATION"):
            self.assertTrue(by_name[name].get("never_send"), name)
        self.assertTrue(by_name["GET_REMOTE_CONFIGURATION"].get("private"), "it holds the serial number")
        self.assertEqual("AEAC4A03-DFF5-498F-843A-34487CF133EB", data["frame"]["service_uuid"])

    def test_vectors(self):
        vectors = load("test_vectors.json")
        for f in vectors["frames"]:
            frame = bytes.fromhex(f["bytes"])
            self.assertEqual(0x55, frame[0], f["what"])
            length = frame[5] | frame[6] << 8
            self.assertEqual(len(frame), 8 + length + (2 if f["crc"] else 0), f["what"])


if __name__ == "__main__":
    unittest.main()
