"""The Soundcore page's table must match data/soundcore/devices.json."""
import unittest

import gen_soundcore_table as gen


class SoundcoreTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.devices = {m["model"]: m for m in cls.data["devices"]}

    def cells(self, model):
        row = next(r for r in gen.rows([self.devices[model]], gen.checked_models(self.data["devices"])))
        return [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_soundcore_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.devices), s["models"])
        self.assertEqual(["AeroClip", "Space A40"], s["tested"], "the models with a recording or a user's report")

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
        self.assertEqual("Checked", status("A3028"), "Life Q30: the Soundcore app's parser reads it the same way")
        self.assertEqual("Checked", status("A3927"), "Life A1: not in OpenSCQ30, read as the Soundcore app reads it")
        self.assertEqual("Not tested", status("A3031"), "no reply in OpenSCQ30's tests, not in the readable app")
        eq = lambda code: dict(zip(headers, self.cells(code)))["Equalizer"]
        self.assertEqual(f"{gen.YES} Checked", eq("A3947"), "Liberty 4 NC: OpenSCQ30 tests its EQ write")
        self.assertEqual(f"{gen.YES} Not tested", eq("A3028"), "Life Q30: OpenSCQ30's code alone")
        self.assertEqual(gen.NO, eq("D1301"), "Sleep A30: no EQ")
        touch = lambda code: dict(zip(headers, self.cells(code)))["Touch controls"]
        self.assertEqual(f"{gen.YES} Checked", touch("A3948"), "OpenSCQ30 tests its touch writes")
        self.assertEqual(f"{gen.YES} Not tested", touch("A3952"), "OpenSCQ30's code alone")
        self.assertEqual(gen.NO, touch("A3004"), "no touch controls in OpenSCQ30")
        self.assertTrue(gen.checked_models(self.data["devices"]) <= set(self.devices))

    def test_what_the_soundcore_app_shows_a_model_lacks(self):
        headers = ["Model", "Number", "Status", "Battery"] + [c for c, _ in gen.COLUMNS]
        a3926 = dict(zip(headers, self.cells("A3926")))
        self.assertEqual(gen.NO, a3926["Noise control"], "OpenSCQ30 lists sound modes; the app shows none")
        self.assertEqual(f"{gen.YES} Checked", a3926["Touch controls"])

    def test_models_read_with_another_models_code_have_its_layout(self):
        # A model the Soundcore app reads with the same code as another gets that model's layout and battery scale.
        copies = {c: m["soundcore_app"]["same_as"] for c, m in self.devices.items() if "same_as" in m.get("soundcore_app", {})}
        self.assertEqual({"A3927", "A3982", "A3983", "A3992", "A3993", "A3913", "A3025"}, set(copies))
        for code, source in copies.items():
            self.assertEqual(self.devices[source]["state"], self.devices[code]["state"], code)
            self.assertEqual(self.devices[source]["battery"], self.devices[code]["battery"], code)
            self.assertEqual(self.devices[source]["soundcore_app"]["parser"], self.devices[code]["soundcore_app"]["parser"], code)

    def test_every_model_has_a_name_and_features(self):
        self.assertGreaterEqual(len(self.devices), 39)
        for code, m in self.devices.items():
            self.assertTrue(m["names"], code)
            self.assertTrue(gen.features(m) or m.get("report_only"), code)
            self.assertRegex(code, r"^[AD]\d{4}C?$")


if __name__ == "__main__":
    unittest.main()
