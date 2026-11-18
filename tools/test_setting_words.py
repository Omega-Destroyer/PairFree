"""desktop/shell's SettingWords.kt is made from the Android app's SettingRows.kt (tools/gen_setting_words.py) and must be current."""
import unittest
import importlib.util
from pathlib import Path

TOOL = Path(__file__).resolve().parent / "gen_setting_words.py"


class SettingWords(unittest.TestCase):
    def test_generated_file_is_current(self):
        spec = importlib.util.spec_from_file_location("gen_setting_words", TOOL)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        self.assertTrue(mod.TARGET.exists(), "run python tools/gen_setting_words.py")
        self.assertEqual(mod.TARGET.read_text(encoding="utf-8").replace("\r\n", "\n"), mod.generate(),
                         "SettingRows.kt changed: run python tools/gen_setting_words.py")

    def test_label_words_are_current(self):
        spec = importlib.util.spec_from_file_location("gen_label_words", TOOL.parent / "gen_label_words.py")
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        self.assertEqual(mod.TARGET.read_text(encoding="utf-8").replace("\r\n", "\n"), mod.generate(),
                         "Labels.kt changed: run python tools/gen_label_words.py")

    def test_every_key_it_names_is_in_the_strings_file(self):
        import re
        mod_text = (Path(__file__).resolve().parent.parent / "desktop/shell/src/commonMain/kotlin/SettingWords.kt").read_text(encoding="utf-8")
        strings = (Path(__file__).resolve().parent.parent / "android/app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
        names = set(re.findall(r'name="(\w+)"', strings))
        # A name the Android file uses through R.string always exists (it would not compile), so this only guards the transform.
        used = set(re.findall(r'"([a-z][a-z0-9_]*)"', mod_text)) & {n for n in names}
        self.assertGreater(len(used), 300)


if __name__ == "__main__":
    unittest.main()
