"""The Soundcore page's table must match data/soundcore/devices.json."""
import unittest

import gen_soundcore_table as gen


class SoundcoreTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.devices = {m["model"]: m for m in cls.data["devices"]}

    def cells(self, model):
        row = next(r for r in gen.rows([self.devices[model]], gen.checked_models()))
        return [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_soundcore_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.devices), s["models"])
        self.assertEqual(["Space A40"], s["tested"], "the models with a recording")

    def test_source_is_credited(self):
        src = self.data["source"]
        self.assertEqual("https://github.com/Oppzippy/OpenSCQ30", src["url"])
        self.assertEqual("GPL-3.0-or-later", src["license"])
        self.assertEqual(40, len(src["commit"]))
        self.assertIn("OpenSCQ30", gen.table())

    def test_space_a40(self):
        headers = ["Model", "Number", "Status", "Battery"] + [c for c, _ in gen.COLUMNS]
        a40 = dict(zip(headers, self.cells("A3936")))
        self.assertEqual("Tested", a40["Status"])
        self.assertEqual("Each earbud, case", a40["Battery"])
        for column in ("Noise control", "HearID", "Two devices", "LDAC", "Game mode", "Touch tone", "Auto power-off"):
            self.assertEqual(gen.YES, a40[column], column)
        self.assertEqual(f"{gen.YES} Tested", a40["Equalizer"], "the A40's EQ writes are recorded")
        self.assertEqual(f"{gen.YES} Tested", a40["Touch controls"], "the A40's touch writes are recorded")
        self.assertEqual(gen.YES, a40["3D / spatial"], "3D sound, confirmed on the A40 beyond OpenSCQ30")
        self.assertEqual(gen.NO, a40["Wear detection"])

    def test_status(self):
        headers = ["Model", "Number", "Status", "Battery"] + [c for c, _ in gen.COLUMNS]
        status = lambda code: dict(zip(headers, self.cells(code)))["Status"]
        self.assertEqual("Checked", status("A3947"), "Liberty 4 NC: replies in OpenSCQ30's tests")
        self.assertEqual("Not tested", status("A3028"), "Life Q30: no reply in OpenSCQ30's tests")
        eq = lambda code: dict(zip(headers, self.cells(code)))["Equalizer"]
        self.assertEqual(f"{gen.YES} Checked", eq("A3947"), "Liberty 4 NC: OpenSCQ30 tests its EQ write")
        self.assertEqual(f"{gen.YES} Not tested", eq("A3028"), "Life Q30: OpenSCQ30's code alone")
        self.assertEqual(gen.NO, eq("D1301"), "Sleep A30: no EQ")
        touch = lambda code: dict(zip(headers, self.cells(code)))["Touch controls"]
        self.assertEqual(f"{gen.YES} Checked", touch("A3948"), "OpenSCQ30 tests its touch writes")
        self.assertEqual(f"{gen.YES} Not tested", touch("A3952"), "OpenSCQ30's code alone")
        self.assertEqual(gen.NO, touch("A3004"), "no touch controls in OpenSCQ30")
        self.assertTrue(gen.checked_models() <= set(self.devices))

    def test_every_model_has_a_name_and_features(self):
        self.assertGreaterEqual(len(self.devices), 39)
        for code, m in self.devices.items():
            self.assertTrue(m["names"], code)
            self.assertTrue(m["openscq30"]["features"], code)
            self.assertRegex(code, r"^[AD]\d{4}C?$")


if __name__ == "__main__":
    unittest.main()
