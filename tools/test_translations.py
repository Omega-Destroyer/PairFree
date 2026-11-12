"""The language files: the English one is sound, every translation matches it, and the generated names are in step."""
import re
import unittest
from pathlib import Path

import translations
from translations import Entry

ROOT = Path(__file__).resolve().parent.parent
KOTLIN = ROOT / "android/app/src/main/kotlin/io/github/omegadestroyer/pairfree/ui/VendorNames.kt"


def entry(text, kind="string", translatable=True):
    return Entry("x", kind, text if isinstance(text, dict) else {None: text}, translatable)


class Checker(unittest.TestCase):
    """The checker itself, on made-up entries."""

    def test_a_sound_translation_has_no_problems(self):
        english = {"a": entry("Delete %1$s?"), "b": entry({"one": "%1$d bar", "other": "%1$d bars"}, "plurals")}
        translation = {"a": entry("%1$s löschen?"), "b": entry({"one": "ein Balken", "other": "%1$d Balken"}, "plurals")}
        self.assertEqual([], translations.problems(english, translation))

    def test_a_missing_entry_is_not_a_problem(self):
        self.assertEqual([], translations.problems({"a": entry("A"), "b": entry("B")}, {"a": entry("Ah")}))

    def test_a_lost_or_changed_placeholder_is_found(self):
        english = {"a": entry("%1$s of %2$d")}
        self.assertTrue(translations.problems(english, {"a": entry("%1$s von")}))
        self.assertTrue(translations.problems(english, {"a": entry("%1$d von %2$d")}))
        self.assertEqual([], translations.problems(english, {"a": entry("%2$d: %1$s")}))

    def test_a_percent_sign_is_not_a_placeholder(self):
        self.assertEqual([], translations.problems({"a": entry("%1$d%%")}, {"a": entry("%1$d %%")}))

    def test_unknown_and_untranslatable_entries_are_found(self):
        english = {"name": entry("PairFree", translatable=False)}
        self.assertTrue(translations.problems(english, {"gone": entry("x")}))
        self.assertTrue(translations.problems(english, {"name": entry("페어프리")}))

    def test_a_plural_needs_other(self):
        english = {"b": entry({"one": "%1$d bar", "other": "%1$d bars"}, "plurals")}
        self.assertTrue(translations.problems(english, {"b": entry({"one": "%1$d"}, "plurals")}))


class Files(unittest.TestCase):
    def setUp(self):
        self.english = translations.read(translations.ENGLISH)

    def test_the_english_file_is_the_only_one_in_values(self):
        extra = [p.name for p in (translations.RES / "values").glob("*.xml") if p.name not in ("strings.xml", "colors.xml")]
        self.assertEqual([], extra, "text goes in strings.xml, one file per language (docs/TRANSLATING.md)")

    def test_every_translation_matches_the_english_file(self):
        for code, path in translations.languages().items():
            with self.subTest(language=code):
                self.assertEqual([], translations.problems(self.english, translations.read(path)))

    def test_the_generated_names_and_their_table_are_in_step(self):
        text = translations.ENGLISH.read_text(encoding="utf-8")
        begin, end = text.index("<!-- BEGIN EQ PRESET NAMES"), text.index("<!-- END EQ PRESET NAMES -->")
        in_file = set(re.findall(r'<string name="([^"]+)"', text[begin:end]))
        in_table = set(re.findall(r"R\.string\.(\w+)", KOTLIN.read_text(encoding="utf-8")))
        self.assertEqual(in_file, in_table, "run python tools/gen_vendor_strings.py")
        self.assertTrue(all(n.startswith(("preset_name_", "vendor_")) for n in in_file))
        outside = set(re.findall(r'<string name="((?:preset_name_|vendor_)[^"]+)"', text[:begin] + text[end:]))
        self.assertEqual(set(), outside, "preset names belong in the generated section")


if __name__ == "__main__":
    unittest.main()
