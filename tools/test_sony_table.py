"""The Sony page's table must match data/sony/devices.json, and data/sony must hold together."""
import json
import unittest
from pathlib import Path

import gen_sony_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "sony"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class SonyTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "Type", "Table set", "Status", "Name from"]

    def row(self, model):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<td>{model}</td>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_sony_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no Sony model tried yet")

    def test_rows(self):
        self.assertEqual({"Model": "WH-1000XM3", "Type": "Headphones", "Table set": "1", "Status": "Not tested",
                          "Name from": "Gadgetbridge"}, self.row("WH-1000XM3"))
        self.assertEqual("2", self.row("WF-1000XM5")["Table set"])
        self.assertEqual(gen.NO, self.row("WH-1000XM4")["Table set"], "nobody wrote it down")
        self.assertEqual("Sony's app", self.row("LinkBuds Fit")["Name from"])
        order = [r.split("</td><td>")[1] for r in gen.rows(self.data["devices"])]
        kinds = list(gen.KINDS.values())
        self.assertEqual(sorted(order, key=kinds.index), order, "headphones, then earbuds, then neckbands")

    def test_gadgetbridge_is_credited(self):
        self.assertEqual(40, len(self.data["gadgetbridge_commit"]))
        self.assertIn("Gadgetbridge", gen.table())


class SonyData(unittest.TestCase):
    def test_devices(self):
        devices = load("devices.json")["devices"]
        names = [n for d in devices for n in d["names"]]
        self.assertEqual(len(names), len(set(names)), "each name belongs to one model")
        for d in devices:
            self.assertIn(d["kind"], gen.KINDS, d["name"])
            self.assertIn(d["source"], gen.SOURCES, d["name"])
            self.assertIn(d["name"], d["names"], d["name"])
            self.assertFalse(any(n.startswith("LE_") for n in d["names"]), "the LE Audio side is not a model")
            if d.get("first_reply"):
                # Gadgetbridge's rule: four bytes from set 1, eight from set 2.
                size = len(bytes.fromhex(d["first_reply"]))
                self.assertEqual({4: 1, 8: 2}[size], d["table_set"], d["name"])

    def test_commands(self):
        data = load("commands.json")
        self.assertEqual({"set1_table1", "set1_table2", "set2_table1", "set2_table2"}, set(data["tables"]))
        for key, table in data["tables"].items():
            numbers = [c["number"] for c in table["commands"]]
            self.assertEqual(sorted(numbers), numbers, key)
            self.assertEqual(len(numbers), len(set(numbers)), key)
            self.assertEqual(data["data_types"]["table2" if key.endswith("table2") else "table1"], table["data_type"])
            for c in table["commands"]:
                if c["name"].startswith("UPDT_"):
                    self.assertTrue(c.get("never_send"), f"{key} {c['name']}: firmware update is never sent")
        # PairFree reads the commands both sets number alike through set 1's constants.
        one = {c["name"]: c["number"] for c in data["tables"]["set1_table1"]["commands"]}
        two = {c["name"]: c["number"] for c in data["tables"]["set2_table1"]["commands"]}
        for name in ("CONNECT_GET_PROTOCOL_INFO", "NCASM_SET_PARAM", "EQEBB_SET_PARAM", "AUDIO_SET_PARAM", "SYSTEM_SET_PARAM"):
            self.assertEqual(one[name], two[name], name)

    def test_test_vectors(self):
        vectors = load("test_vectors.json")
        for case in vectors["acks"]:
            frame = bytes.fromhex(case["frame"])
            self.assertEqual((0x3E, 0x01, 0x3C), (frame[0], frame[1], frame[-1]))
            self.assertEqual(1 - case["received_seq"], frame[2], "an ACK carries the other number")
            self.assertEqual(sum(frame[1:-2]) & 0xFF, frame[-2], "the checksum is the sum")


if __name__ == "__main__":
    unittest.main()
