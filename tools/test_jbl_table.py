"""The JBL page's table must match data/jbl/devices.json, and data/jbl must hold together."""
import json
import unittest
from pathlib import Path

import gen_jbl_table as gen

DATA = Path(__file__).resolve().parent.parent / "data" / "jbl"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class JblTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.headers = ["Model", "Product", "Type", "Connection", "Settings", "Status"]

    def row(self, pid):
        row = next(r for r in gen.rows(self.data["devices"]) if f"<code>{pid}</code>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_jbl_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.data["devices"]), s["models"])
        self.assertEqual([], s["tested"], "no JBL model tried with PairFree yet")

    def test_rows(self):
        flex2 = self.row("210E")
        self.assertEqual("Tune Flex 2", flex2["Model"])
        self.assertEqual("Earbuds", flex2["Type"])
        self.assertEqual("Bluetooth LE", flex2["Connection"])
        self.assertEqual(
            "noise cancelling, Ambient Aware, TalkThru, auto power-off, Smart Audio &amp; Video, voice prompts, balance, VoiceAware, SafeSound, "
            "3D spatial sound, sound effect, audio limiter, Find My Buds, touch controls, adaptive noise cancelling, auto compensation",
            flex2["Settings"],
        )
        self.assertIn("JblTray", flex2["Status"])
        self.assertEqual("Serial", self.row("2113")["Connection"], "the Quantum 360")
        tune720 = self.row("20B4")["Settings"]
        self.assertTrue(tune720.startswith("auto power-off, "), tune720)
        self.assertNotIn("noise cancelling", tune720, "the Tune 720BT has no noise control")
        self.assertTrue(self.row("21B6")["Settings"].startswith("noise cancelling, Ambient Aware, TalkThru, "), "the Tune Flex 3, on protocol 4")
        self.assertTrue(self.row("2133")["Settings"].startswith("auto power-off, "), "the Sense Lite")
        self.assertEqual("auto power-off, Smart Audio &amp; Video, voice prompts, balance, VoiceAware, SafeSound", self.row("2135")["Settings"], "the Tune 135BT")
        self.assertTrue(self.row("2160")["Settings"].startswith("noise cancelling, Ambient Aware, TalkThru, auto power-off, pause when taken off, "), "the Live 680NC, security mode 1")


class JblData(unittest.TestCase):
    def test_not_covered(self):
        # GitHub issues #1 and #4: owners of these could not tell why their model was missing.
        data = load("devices.json")
        covered = {p for d in data["devices"] for p in d["pids"]}
        missing = {m["name"]: m["why"] for m in data["not_covered"]}
        self.assertNotIn("JBL Live Buds 3", missing, "covered over Airoha's RACE commands since issue #4")
        self.assertNotIn("JBL Tour Pro 3", missing, "LE Audio, on the same Airoha layer (issue #1)")
        tour = next(d for d in data["devices"] if d["name"] == "JBL Tour Pro 3")
        self.assertEqual(("airoha", "TourPro3LeControl"), (tour["family"], tour["control"]))
        for m in data["not_covered"]:
            self.assertIn(m["why"], gen.WHY, m["name"])
            self.assertFalse(set(m["pids"]) & covered, f"{m['name']} is covered")

    def test_devices(self):
        devices = load("devices.json")["devices"]
        pids = [p for d in devices for p in d["pids"]]
        self.assertEqual(len(pids), len(set(pids)), "each product id names one model")
        for d in devices:
            self.assertIn(d["kind"], gen.KINDS, d["name"])
            self.assertIn(d["connect"], gen.CONNECT, d["name"])
            self.assertIn(d["family"], ("direct", "feature", "p4", "airoha"), d["name"])
            # The Airoha models' noise control and auto power-off go over RACE, and only theirs.
            if d.get("modes"):
                self.assertEqual(d["family"] == "airoha", d["noise"] == "race", d["name"])
            if d.get("auto_off"):
                self.assertEqual(d["family"] == "airoha", d["auto_off"] == "race", d["name"])
            self.assertTrue(all(isinstance(n, str) and n.startswith("JBL ") for n in d.get("names", [])), d["name"])
            self.assertTrue(d["name"].startswith("JBL "), d["name"])
            for p in d["pids"]:
                self.assertEqual(4, len(p))
                self.assertEqual(p.upper(), p)
                int(p, 16)
            self.assertEqual(bool(d.get("modes")), "noise" in d, d["name"])
            self.assertTrue(set(d.get("modes", [])) <= set(gen.MODES), d["name"])
            self.assertIn(d.get("auto_off"), (None, "v1", "v2", "feature", "p4", "race"), d["name"])
            self.assertIn(d.get("security", 0), (0, 1, 2, 3), d["name"])
            if d.get("tried"):
                self.assertIn(d["tried"], gen.TRIED)

    def test_commands(self):
        data = load("commands.json")
        names = [c["name"] for c in data["commands"]]
        self.assertEqual(len(names), len(set(names)))
        self.assertEqual(170, data["start"])
        for number in ("95", "97", "f0"):
            self.assertIn(number, data["never_send"], "factory reset, power off and firmware update are never sent")
        for number in ("00ff", "0101", "0102"):
            self.assertIn(number, data["protocol4"]["never_send"], "protocol 4's factory reset and firmware update")
        used = {c["command"] for c in data["commands"]}
        self.assertFalse(used & set(data["never_send"]))
        race = data["airoha"]
        for number in ("0A01", "0D01", "0010"):
            self.assertIn(number, race["never_send"], "NV key writes, relays to the other earbud and analytics are never sent")
        self.assertFalse(set(race["commands"].values()) & set(race["never_send"]))
        self.assertEqual({"00A8", "00A9"}, set(race["key_events"].values()), "the only key events: Smart Audio & Video's")

    def test_vectors(self):
        vectors = load("test_vectors.json")
        for r in vectors["requests"] + vectors["replies"]:
            frame = bytes.fromhex(r["hex"])
            if frame[1] == 0xDD:
                self.assertEqual(len(frame), 8 + (frame[6] | (frame[7] << 8)), r["name"])
                continue
            self.assertEqual(0xAA, frame[0], r["name"])
            wide = frame[1] == 0x06
            length = frame[2] | (frame[3] << 8) if wide else frame[2]
            self.assertEqual(len(frame), (4 if wide else 3) + length, r["name"])
            self.assertIn(r["source"], ("jbltray", "fajkpes", "app"), r["name"])
        # RACE: 05, the type (5A a command, 5B a response, 5D an indication), the length of the id and payload, the id.
        race = vectors["race"]
        for r in race["requests"] + race["replies"]:
            frame = bytes.fromhex(r["hex"])
            self.assertEqual(0x05, frame[0], r["name"])
            self.assertIn(frame[1], (0x5A, 0x5B, 0x5D), r["name"])
            self.assertEqual(len(frame), 4 + (frame[2] | (frame[3] << 8)), r["name"])
        self.assertTrue(all(bytes.fromhex(r["hex"])[1] == 0x5A for r in race["requests"]))


if __name__ == "__main__":
    unittest.main()
