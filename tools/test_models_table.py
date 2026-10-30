"""The SOUNDPEATS page's table of every model must match data/soundpeats/devices.json and the device reports."""
import unittest

import gen_models_table as gen


class ModelsTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.commands = {c["id"]: c for c in gen.load("commands.json")["commands"]}
        cls.devices = {m["firmware_code"]: m for m in gen.load("devices.json")["devices"]}

    def row(self, code):
        return next(r for r in gen.rows([self.devices[code]], self.commands))

    def cells(self, code):
        return [c.split(">", 1)[1] for c in self.row(code).removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_models_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(47, s["models"], "every model in the official app's table but the Capsule3 Pro, and two from reports")
        self.assertEqual(["Air5 Pro+", "C30", "H3", "TRUEFREE Clip C10"], s["tested"])

    def test_h3_report_reads(self):
        answered, silent, gaia = gen.read_reports(self.devices["S5"]["reports"], self.commands)
        self.assertIn("anc_mode", answered)
        self.assertIn("find_left", answered)
        self.assertIn("le_audio", answered)
        self.assertIn("water_drain", silent)
        self.assertIn(gen.GAIA_FIT_FEATURE, gaia)

    def test_columns(self):
        headers = [h for h, _ in gen.FEATURES] + ["Fit test", "Equalizer"]
        h3 = dict(zip(headers, self.cells("S5")[3:]))
        air5 = dict(zip(headers, self.cells("S2")[3:]))
        self.assertEqual(h3["LE Audio"], gen.NO, "answers the read, but PeatsAudio does not offer it")
        self.assertEqual(air5["LE Audio"], gen.YES)
        self.assertEqual(h3["Water drain"], gen.NO)
        self.assertEqual(air5["Water drain"], gen.YES)
        for model in (h3, air5):
            self.assertEqual(model["Fit test"], gen.YES)
            self.assertEqual(model["Equalizer"], gen.YES)
            self.assertEqual(model["In-ear detection"], gen.NO)

    def test_a_model_without_a_report_asks_for_one(self):
        row = self.row("QCC3091AIR5")
        self.assertIn("Needs a device report", row)
        self.assertIn("Needs a recording", row)

    def test_every_model_has_a_row(self):
        self.assertEqual(len(gen.rows(list(self.devices.values()), self.commands)), len(self.devices))
        self.assertIn("Not supported", self.row("QCYTS04"))

    def test_feature_columns_name_real_commands(self):
        for _, ids in gen.FEATURES:
            for i in ids:
                self.assertIn(i, self.commands)
                self.assertIn("get", self.commands[i])


if __name__ == "__main__":
    unittest.main()
