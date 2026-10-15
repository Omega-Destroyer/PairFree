"""The AirPods page's table must match data/apple/devices.json, and data/apple must hold together."""
import json
import unittest
from pathlib import Path

import gen_apple_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "apple"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class AppleTable(unittest.TestCase):
    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_apple_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(gen.load()["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no AirPods tried with PairFree yet")

    def test_rows(self):
        rows = gen.rows(gen.load()["devices"])
        self.assertTrue(rows[0].startswith("<tr><td>AirPods"), "AirPods first")
        pro2 = next(r for r in rows if "<code>A3048</code>" in r)
        self.assertIn("<td>Yes</td>", pro2)
        studio = next(r for r in rows if "Beats Studio Buds +" in r)
        self.assertIn("In doubt", studio)


class AppleData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        numbers = [n for d in devices for n in d["model_numbers"]]
        self.assertEqual(len(numbers), len(set(numbers)), "each model number names one model")
        ids = [d["product_id"] for d in devices]
        self.assertEqual(len(ids), len(set(ids)))
        for d in devices:
            self.assertIn(d["kind"], gen.KINDS, d["name"])
            self.assertEqual(4, len(d["product_id"]))
            int(d["product_id"], 16)
            for n in d["model_numbers"]:
                self.assertRegex(n, r"^A\d{4}$")

    def test_commands(self):
        data = load("commands.json")
        self.assertEqual(4097, data["psm"])
        ids = [c["id"] for c in data["controls"]]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertFalse(set(ids) & set(data["never_send"]["controls"]), "nothing offered is never sent")
        for opcode in ("30", "1a", "10"):
            self.assertIn(opcode, data["never_send"]["opcodes"])

    def test_vectors(self):
        vectors = load("test_vectors.json")
        for r in vectors["requests"] + vectors["replies"]:
            packet = bytes.fromhex(r["hex"])
            if r["name"] != "handshake":
                self.assertEqual(bytes.fromhex("04000400"), packet[:4], r["name"])
        info = next(r for r in vectors["replies"] if r["name"] == "information")
        text = bytes.fromhex(info["hex"]).decode("latin-1")
        self.assertIn("0000000000", text, "serial numbers zeroed")


if __name__ == "__main__":
    unittest.main()
