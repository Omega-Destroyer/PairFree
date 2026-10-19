"""The 1MORE page's table must match data/1more/devices.json, and data/1more must hold together."""
import json
import unittest
from pathlib import Path

import gen_1more_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "1more"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class OneMoreTable(unittest.TestCase):
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
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_1more_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no 1MORE model tried with PairFree yet")

    def test_rows(self):
        aero = self.row("ES903")
        self.assertEqual("1MORE Aero", aero["Model"])
        self.assertEqual("ANC (adaptive, strong, mild, wind), transparency (pass-through)", aero["Noise control"])
        self.assertIn("touch controls (each earbud)", aero["Also"])
        self.assertEqual("Not tested", aero["Status"])
        self.assertEqual(("Headphones", "ANC, transparency"), (self.row("HQ51")["Type"], self.row("HQ51")["Noise control"]))
        self.assertEqual("Not supported (Tuya)", self.row("S52")["Status"])


class OneMoreData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        commands = load("commands.json")
        modes = set(commands["values"]["listen_mode"].values())
        names = [d["name"] for d in devices]
        self.assertEqual(len(names), len({n.lower() for n in names}), "one entry per Bluetooth name")
        for d in devices:
            self.assertTrue(d["name"].startswith("1MORE "), d["name"])
            self.assertIn(d["kind"], ("earbuds", "headphones"), d["name"])
            self.assertLessEqual(set(d["listen_modes"]), modes, d["name"])
            self.assertLessEqual(set(d["connection"]), {"game", "ldac"}, d["name"])
            self.assertIn(d["touch"], ("each", "both", None), d["name"])
            if d["listen_modes"]:
                self.assertIn("off", d["listen_modes"], d["name"])
            if d["kind"] == "headphones":
                self.assertFalse(d["case"], d["name"])
            for p in d["pictures"]:
                self.assertTrue(all(v.startswith("/profile/") for k, v in p.items() if k != "colour"), d["name"])
        self.assertEqual({"1MORE S52", "1MORE S22Ultra"}, {d["name"] for d in devices if d["tuya"]})

    def test_commands(self):
        commands = load("commands.json")["commands"]
        codes = [c["code"] for c in commands]
        self.assertEqual(len(codes), len(set(codes)))
        never = {c["name"] for c in commands if c.get("never_send")}
        self.assertLessEqual({"HANDSHAKE", "DELETE_MUSIC", "TRANSFER_START", "TRANSFER_DATA", "TRANSFER_END", "GET_AI_LICENSE"}, never)

    def test_vectors(self):
        from functools import reduce
        for v in load("test_vectors.json")["sent"]:
            b = bytes.fromhex(v["hex"])
            self.assertEqual(reduce(lambda a, c: a ^ c, b[:8]), b[8], v["command"])
            self.assertEqual(b[2:4].hex(), v["command"].lower())
            self.assertEqual(b[9:].hex(), v["payload"].lower())


if __name__ == "__main__":
    unittest.main()
