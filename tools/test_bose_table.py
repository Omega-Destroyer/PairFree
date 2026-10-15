"""The Bose page's table must match data/bose/devices.json, and data/bose must hold together."""
import json
import unittest
from pathlib import Path

import gen_bose_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "bose"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class BoseTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "Product", "Type", "Status"]

    def row(self, product):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<code>{product}</code>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_bose_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no Bose model tried with PairFree yet")

    def test_rows(self):
        self.assertEqual({"Model": "QC Ultra 2 HP", "Product": "<code>4082</code>", "Type": "Headphones", "Status": "bosectl"},
                         self.row("4082"))
        self.assertEqual("Earbuds", self.row("4062")["Type"])
        self.assertEqual("Not tested", self.row("4024")["Status"], "the NC 700")
        self.assertIn("bosectl", gen.table())


class BoseData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        ids = [d["product_id"] for d in devices]
        self.assertEqual(len(ids), len(set(ids)), "one row per product id")
        for d in devices:
            self.assertIn(d["kind"], gen.KINDS, d["name"])
            self.assertTrue(d["name"].startswith("Bose "), d["name"])
            self.assertEqual(4, len(d["product_id"]))
            int(d["product_id"], 16)
        self.assertEqual(40, len(load("devices.json")["bosectl_commit"]))

    def test_commands(self):
        data = load("commands.json")
        self.assertEqual({"set": 0, "get": 1, "setget": 2, "status": 3, "error": 4, "start": 5, "result": 6, "processing": 7},
                         data["operators"])
        names = set()
        for block in data["blocks"]:
            numbers = [f["function"] for f in block["functions"]]
            self.assertEqual(len(numbers), len(set(numbers)), block["name"])
            for f in block["functions"]:
                self.assertTrue(f["name"].startswith(block["name"]), f["name"])
                names.add(f["name"])
                if block["name"] in ("FirmwareUpdate", "Authentication"):
                    self.assertTrue(f.get("never_send"), f["name"])
        for name in ("ControlFactoryDefault", "ControlPower"):
            self.assertTrue(next(f for b in data["blocks"] for f in b["functions"] if f["name"] == name).get("never_send"), name)

    def test_vectors(self):
        vectors = load("test_vectors.json")
        for r in vectors["qc_ultra2_headphones"]:
            self.assertNotIn(r["function"], ("0.6", "0.7", "0.12", "0.17", "1.2"), "no address, serial, GUID or name")
            bytes.fromhex(r["payload"])
        for r in vectors["requests"]:
            packet = bytes.fromhex(r["bytes"])
            self.assertEqual(len(packet), 4 + packet[3], r["what"])


if __name__ == "__main__":
    unittest.main()
