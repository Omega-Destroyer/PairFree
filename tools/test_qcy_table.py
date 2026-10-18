"""The QCY page's table must match data/qcy/devices.json, and data/qcy must hold together."""
import json
import unittest
from pathlib import Path

import gen_qcy_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "qcy"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class QcyTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "Vendor id", "Chip", "Type", "Noise control", "Also", "Status"]

    def row(self, code):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<code>{code}</code>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_qcy_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no QCY model tried with PairFree yet")

    def test_rows(self):
        n70 = self.row("5D45")
        self.assertEqual("QCY MeloBuds N70", n70["Model"])
        self.assertEqual("WUQI", n70["Chip"])
        self.assertEqual("ANC (5 scenes), transparency (7 levels)", n70["Noise control"])
        self.assertIn("LDAC", n70["Also"])
        self.assertEqual("Not tested", n70["Status"])
        h3 = self.row("4177")
        self.assertEqual(("Headphones", "JieLi", "Not yet (JieLi)"), (h3["Type"], h3["Chip"], h3["Status"]))


class QcyData(unittest.TestCase):
    def test_devices(self):
        data = load("devices.json")
        devices = data["devices"]
        ids = [d["vendor_id"] for d in devices]
        self.assertEqual(len(ids), len(set(ids)), "one row per vendor id")
        self.assertEqual(171, len(devices), "Quicky's earphones, and nothing else")
        self.assertEqual(40, len(data["quicky_commit"]))
        for d in devices:
            self.assertTrue(d["name"].startswith("QCY"), d["name"])
            self.assertIn(d["protocol"], ("qcy", "jieli", "jieli_custom"), d["name"])
            self.assertIn(d["kind"], gen.KINDS, d["name"])
            for m in d.get("anc", []):
                self.assertIn(m["mode"], ("cancelling", "transparency", "off"), d["name"])
                self.assertLessEqual(int(m["from"], 16), int(m["to"], 16), d["name"])
            for s in d.get("settings", []):
                self.assertIn(s["setting"], gen.NAMES, d["name"])
            # The chip decides the protocol, as QCY's app does (Devicebind.isJLDevice).
            self.assertEqual(d["family"] == "jieli", d["protocol"] != "qcy", d["name"])

    def test_commands(self):
        data = load("commands.json")
        ids = [i["id"] for i in data["items"]]
        self.assertEqual(len(ids), len(set(ids)))
        never = {i["name"] for i in data["items"] if i.get("never_send")}
        for name in ("RESET_DEFAULT", "CLEAR_PAIR", "FACTORY_RESET", "TEST_MODE", "PAIR_NAME", "RESTART"):
            self.assertIn(name, never)
        self.assertEqual("521C", data["company_id"])

    def test_vectors(self):
        vectors = load("test_vectors.json")
        for group in ("requests", "replies", "writes"):
            for r in vectors["n70"][group]:
                packet = bytes.fromhex(r["bytes"])
                self.assertEqual(0xFF, packet[0], r["what"])
                self.assertEqual(len(packet), 2 + packet[1], r["what"])
        self.assertEqual(24, len(bytes.fromhex(vectors["advert"]["bytes"])))


if __name__ == "__main__":
    unittest.main()
