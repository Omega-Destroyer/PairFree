"""The Samsung page's table must match data/samsung/devices.json."""
import unittest

import gen_samsung_table as gen


class SamsungTable(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.load()
        cls.devices = {m["model"]: m for m in cls.data["devices"]}
        cls.headers = ["Model", "Number", "Status", "Battery"] + [c for c, _ in gen.COLUMNS]

    def row(self, model):
        rows = gen.rows(self.data["devices"], gen.checked_models())
        row = next(r for r in rows if f"<td>{model}</td>" in r)
        cells = [c.split(">", 1)[1] for c in row.removeprefix("<tr>").removesuffix("</tr>").split("</td>")[:-1]]
        return dict(zip(self.headers, cells))

    def test_page_is_up_to_date(self):
        text = gen.PAGE.read_text(encoding="utf-8")
        start, end = text.index(gen.START), text.index(gen.END) + len(gen.END)
        self.assertEqual(text[start:end], gen.table(), "run python tools/gen_samsung_table.py")

    def test_summary(self):
        s = gen.summary()
        self.assertEqual(len(self.devices), s["models"])
        self.assertEqual([], s["tested"], "no Galaxy Buds tried yet")

    def test_source_is_credited(self):
        src = self.data["source"]
        self.assertEqual("https://github.com/timschneeb/GalaxyBudsClient", src["url"])
        self.assertEqual(40, len(src["commit"]))
        self.assertIn("GalaxyBudsClient", gen.table())

    def test_checked_models_are_the_ones_with_test_replies(self):
        self.assertEqual({"SM-R170", "SM-R175", "SM-R180", "SM-R190", "SM-R177", "SM-R510", "SM-R400N"}, gen.checked_models())
        self.assertEqual("Checked", self.row("SM-R510")["Status"])
        self.assertEqual("Not tested", self.row("SM-R630")["Status"])

    def test_rows_follow_the_models(self):
        buds = self.row("SM-R170")
        self.assertEqual("Each earbud", buds["Battery"], "the 2019 Buds report no case battery")
        self.assertEqual("Ambient", buds["Noise control"])
        self.assertEqual("Presets", buds["Equalizer"])
        self.assertEqual(gen.NO, buds["Game mode"])
        self.assertEqual("ANC", self.row("SM-R180")["Noise control"], "the Buds Live: noise cancelling on or off")
        pro2 = self.row("SM-R510")
        self.assertEqual("ANC, ambient", pro2["Noise control"])
        self.assertEqual(gen.YES, pro2["Hold switches between"])
        self.assertEqual(gen.YES, pro2["Fit test"])
        pro3 = self.row("SM-R630")
        self.assertEqual("Presets, custom", pro3["Equalizer"])
        self.assertEqual("Not yet", pro3["Hold switches between"], "the Buds3 cycle write is unconfirmed")
        self.assertEqual(f"{gen.YES} rev. 5+", self.row("SM-R177")["Ambient per earbud"], "the Buds2 from state revision 5")

    def test_models_in_samsungs_order(self):
        order = [r.split("</td><td>")[1] for r in gen.rows(self.data["devices"])]
        self.assertEqual("SM-R170", order[0])
        self.assertEqual(sorted(order, key=lambda m: gen.spec(self.devices[m])["generation"]), order)

    def test_samsungs_plugin_wins_for_the_buds4_family(self):
        buds4, pro = self.row("SM-R540"), self.row("SM-R640")
        self.assertEqual("Samsung's app", buds4["Status"])
        self.assertEqual(gen.NO, buds4["Fit test"], "Samsung's plugin lists no fit test for the Buds4")
        self.assertEqual(gen.YES, pro["Fit test"])
        self.assertEqual(gen.YES, pro["Hold switches between"], "Samsung's plugin shows the write")
        self.assertEqual("One", self.row("SM-U700")["Battery"], "the H1 is a headset")
        self.assertEqual("Samsung's app", self.row("SM-R545")["Status"])


if __name__ == "__main__":
    unittest.main()
