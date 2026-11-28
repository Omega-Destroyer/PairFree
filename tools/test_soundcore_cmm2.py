"""The Soundcore app's whole command table against PairFree's (issue #79: Find Earbuds was in the app's table and in no audit).

GapAuditTest checks the ids PairFree's own table holds. docs/soundcore/CMM2_COMMANDS.md lists the app's table, and a command listed there but never taken into
data/soundcore/commands.json was invisible to every check. Each row must now be in commands.json, or in data/soundcore/cmm2_unused.json with a reason; a reason that
starts with "gap-audit" is an open item, not an explanation. A listed command that has since been taken into the table must be removed from the list.
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def app_commands():
    text = (ROOT / "docs" / "soundcore" / "CMM2_COMMANDS.md").read_text(encoding="utf-8")
    return {a + b: row.strip() for a, b, row in re.findall(r"^\| ([0-9a-fA-F]{2}) ([0-9a-fA-F]{2}) \|[^|]*\|([^|]*)\|", text, re.M)}


class SoundcoreAppTable(unittest.TestCase):
    def test_every_command_of_the_app_is_taken_up_or_explained(self):
        table = {c["command"].lower() for c in json.loads((ROOT / "data" / "soundcore" / "commands.json").read_text(encoding="utf-8"))["commands"]}
        listed = json.loads((ROOT / "data" / "soundcore" / "cmm2_unused.json").read_text(encoding="utf-8"))["commands"]
        app = {k.lower(): v for k, v in app_commands().items()}
        self.assertGreater(len(app), 50)
        missing = sorted(f"{k} {app[k]}" for k in app if k not in table and k not in listed)
        self.assertEqual([], missing, "in the app's table, neither in commands.json nor in cmm2_unused.json")
        stale = sorted(k for k in listed if k in table or k not in app)
        self.assertEqual([], stale, "listed as unused although taken up, or not in the app's table")
        self.assertTrue(all(isinstance(v, str) and v.strip() for v in listed.values()))


if __name__ == "__main__":
    unittest.main()
