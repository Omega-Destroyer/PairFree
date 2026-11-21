"""Tests of the release gate's logic on small made-up registries (the real registry is checked by running the gate itself)."""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import release_gate as g


class ReleaseGate(unittest.TestCase):
    def test_brands_touched_by_paths(self):
        changed = [
            "data/qcy/devices.json",
            "android/protocol/src/main/kotlin/io/github/omegadestroyer/pairfree/protocol/onemore/OneMoreSession.kt",
            "docs/skullcandy/PROTOCOL.md",
            "tools/gen_sennheiser_table.py",
            "README.md",
        ]
        self.assertEqual({"qcy", "1more", "skullcandy", "sennheiser"}, g.brands_touched(changed))
        self.assertEqual(set(), g.brands_touched(["README.md", "docs/DESIGN.md"]))

    def test_open_item_in_a_touched_brand_refuses(self):
        reg = {"items": [{"id": "x", "brand": "qcy", "status": "open", "what": "a thing"}]}
        failures, _ = g.check_registry(reg, set(), {"qcy"})
        self.assertEqual(1, len(failures))

    def test_open_item_in_another_brand_is_a_note(self):
        reg = {"items": [{"id": "x", "brand": "qcy", "status": "open", "what": "a thing"}]}
        failures, notes = g.check_registry(reg, set(), {"jbl"})
        self.assertEqual([], failures)
        self.assertEqual(1, len(notes))

    def test_a_waiver_lets_an_open_item_through(self):
        reg = {"items": [{"id": "x", "brand": "qcy", "status": "open", "what": "a thing"}]}
        failures, notes = g.check_registry(reg, {"x"}, {"qcy"})
        self.assertEqual([], failures)
        self.assertTrue(notes[0].startswith("waived"))

    def test_a_blocker_that_the_apps_files_refute_fails(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "app.txt"), "w", encoding="utf-8") as f:
                f.write("the app has Equalizer2 here")
            refuted = {"items": [{"id": "b", "brand": "x", "status": "blocked", "what": "w",
                                  "evidence": [{"root": d, "pattern": "Equalizer", "expect": "none"}]}]}
            self.assertEqual(1, len(g.check_registry(refuted, set(), set())[0]))
            fine = {"items": [{"id": "b", "brand": "x", "status": "blocked", "what": "w",
                               "evidence": [{"root": d, "pattern": "Nothing like this", "expect": "none"}]}]}
            self.assertEqual([], g.check_registry(fine, set(), set())[0])

    def test_a_blocker_without_evidence_fails(self):
        reg = {"items": [{"id": "b", "brand": "x", "status": "blocked", "what": "w"}]}
        self.assertEqual(1, len(g.check_registry(reg, set(), set())[0]))

    def test_the_hook_pattern_catches_releases_only(self):
        rc = g.RELEASE_COMMAND
        for cmd in ["git tag -a v0.18.0 -m x", "git -c credential.username=a push origin v0.18.0", "gh release create v1.0.0",
                    "python E:/Builds/releases/tools/gh_release_0180.py n.md a.apk", "git push origin --tags"]:
            self.assertTrue(rc.search(cmd), cmd)
        for cmd in ["git tag --list", "git tag", "git push origin main", "git log --oneline", "gh issue view 5", "gh release list"]:
            self.assertFalse(rc.search(cmd), cmd)


if __name__ == "__main__":
    unittest.main()
