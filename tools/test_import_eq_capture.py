"""import_eq_capture.py must rebuild the H3's and Air5 Pro+'s EQ exactly from their captured bytes."""
import json
import unittest
from pathlib import Path

import import_eq_capture as imp

DATA = Path(__file__).resolve().parent.parent / "data"


def report(code, vectors, firmware):
    """A report in the app's format (EqCapture.reportLines), from the test vectors."""
    reset = next(c for c in vectors["eq_layout"] if c["model"] == code and c["name"].startswith("Reset"))
    presets = [c for c in vectors["eq_preset"] if c["model"] == code]
    lines = ["SoundPeter EQ capture", "", "Model:     whatever", f"Firmware:  {firmware}",
             "EQ:        command 0E01, 10 bands, master gain -2 dB"]
    for i, (name, payloads) in enumerate([("SOUNDPEATS Classic", reset["payloads"])] +
                                         [(c["preset"], c["payloads"]) for c in presets], start=1):
        lines += ["", f"{i}. {name}", "   Hz   1 2 3", "   dB   +1 +2", "   Q    0.7"]
        lines += [f"   {p}" for p in payloads]
    return "\n".join(lines) + "\n"


class ImportEqCaptureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vectors = json.loads((DATA / "test_vectors.json").read_text(encoding="utf-8"))
        cls.devices = json.loads((DATA / "devices.json").read_text(encoding="utf-8"))["devices"]

    def check(self, code, firmware):
        model = next(m for m in self.devices if m.get("firmware_code") == code)
        out = imp.build(report(code, self.vectors, firmware))
        self.assertEqual(out["firmware"], firmware)
        self.assertEqual(out["missing"], [])
        self.assertEqual(out["eq"], model["eq"])
        self.assertEqual([c["payloads"] for c in out["eq_preset"]],
                         [c["payloads"] for c in self.vectors["eq_preset"] if c["model"] == code])
        self.assertEqual(out["eq_layout"][0]["user_gains_db"], [0] * 10)

    def test_h3(self):
        self.check("S5", "S5_20250605_V0.4.9")

    def test_air5_pro_plus(self):
        self.check("S2", "S2_20250816_V1.2")

    def test_report_pasted_without_indents_in_a_code_block(self):
        text = report("S5", self.vectors, "S5_20250605_V0.4.9")
        flat = "```\n" + "\n".join(line.strip() for line in text.splitlines()) + "\n```\n"
        self.assertEqual(imp.build(flat)["eq"], imp.build(text)["eq"])

    def test_report_with_the_settings_sections(self):
        # A recording also lists the settings PeatsAudio and SoundPeter read, before the curves.
        settings = "\n".join([
            "---- What PeatsAudio read from SOUNDPEATS H3, firmware S5_20250605_V0.4.9 (from the Bluetooth log) ----",
            "firmware_version     0309  'S5_20250605_V0.4.9'                     raw=00 53 35 5f 32 30 32 35 30 36 30 35 5f 56 30 2e 34 2e 39",
            "anc_mode             0310  1                                        raw=00 01",
            "water_drain          0350  no reply",
            "gaia_features        0001  00 01 05 06 07 08 09 0c 0d",
            "",
            "---- Equalizer: the curves PeatsAudio sent ----",
            "",
        ])
        text = report("S5", self.vectors, "S5_20250605_V0.4.9")
        head, body = text.split("Firmware:", 1)
        mixed = head + settings + "Firmware:" + body
        self.assertEqual(imp.build(mixed)["eq"], imp.build(text)["eq"])
        self.assertEqual(imp.build(mixed)["firmware"], "S5_20250605_V0.4.9")

    def test_without_classic_says_the_base_is_missing(self):
        text = report("S5", self.vectors, "S5_20250605_V0.4.9").replace("SOUNDPEATS Classic", "Mystery")
        out = imp.build(text)
        self.assertNotIn("bands", out["eq"])
        self.assertEqual(out["missing"], ["SOUNDPEATS Classic (the base curve)"])

    def test_refuses_a_damaged_payload(self):
        text = report("S5", self.vectors, "S5_20250605_V0.4.9")
        first = self.vectors["eq_preset"][0]["payloads"][3]
        damaged = text.replace(first, first[:2] + "00" + first[4:], 1)
        with self.assertRaises(ValueError):
            imp.build(damaged)


if __name__ == "__main__":
    unittest.main()
