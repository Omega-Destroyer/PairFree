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

    def _settled(self, status, d, **extra):
        read = [{"path": d, "what": "the folder the claim is about, read for the thing in question"}]
        return {"items": [{"id": "b", "brand": "x", "status": status, "what": "w", "read": read, **extra}]}

    def test_a_blocker_that_the_apps_files_refute_fails(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "app.txt"), "w", encoding="utf-8") as f:
                f.write("the app has Equalizer2 here")
            refuted = self._settled("not-applicable", d, evidence=[{"root": d, "pattern": "Equalizer", "expect": "none"}])
            self.assertEqual(1, len(g.check_registry(refuted, set(), set())[0]))
            fine = self._settled("not-applicable", d, evidence=[{"root": d, "pattern": "Nothing like this", "expect": "none"}])
            self.assertEqual([], g.check_registry(fine, set(), set())[0])

    def test_a_settled_item_without_proof_fails(self):
        with tempfile.TemporaryDirectory() as d:
            no_evidence = self._settled("not-applicable", d)
            self.assertEqual(1, len(g.check_registry(no_evidence, set(), set())[0]))
            no_read = {"items": [{"id": "b", "brand": "x", "status": "not-applicable", "what": "w", "evidence": [{"root": d, "pattern": "q", "expect": "none"}]}]}
            self.assertEqual(1, len(g.check_registry(no_read, set(), set())[0]))
            gone = self._settled("not-applicable", d, evidence=[{"root": d, "pattern": "q", "expect": "none"}])
            gone["items"][0]["read"][0]["path"] = os.path.join(d, "missing.txt")
            self.assertEqual(1, len(g.check_registry(gone, set(), set())[0]))

    def test_blocked_needs_one_of_the_hard_rules(self):
        with tempfile.TemporaryDirectory() as d:
            ev = [{"root": d, "pattern": "q", "expect": "none"}]
            self.assertEqual(1, len(g.check_registry(self._settled("blocked", d, evidence=ev, rule="too complicated"), set(), set())[0]))
            self.assertEqual([], g.check_registry(self._settled("blocked", d, evidence=ev, rule="server authentication"), set(), set())[0])

    def test_needs_report_notes_but_never_gates_and_needs_its_proof(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "app.txt"), "w", encoding="utf-8") as f:
                f.write("the function that builds it at run time")
            good = self._settled("needs-report", d, needs="a device report taken after a reconnect, with the last answers",
                                 evidence=[{"root": d, "pattern": "at run time", "expect": "some"}])
            failures, notes = g.check_registry(good, set(), {"x"})
            self.assertEqual([], failures)
            self.assertTrue(notes[0].startswith("AWAITING A REPORT"))
            # no search that finds the code: the claim is not tied to anything
            vague = self._settled("needs-report", d, needs="a device report taken after a reconnect, with the last answers",
                                  evidence=[{"root": d, "pattern": "absent", "expect": "none"}])
            self.assertEqual(1, len(g.check_registry(vague, set(), {"x"})[0]))
            # no statement of what to ask
            mute = self._settled("needs-report", d, evidence=[{"root": d, "pattern": "at run time", "expect": "some"}])
            self.assertEqual(1, len(g.check_registry(mute, set(), {"x"})[0]))

    def test_the_hook_pattern_catches_releases_only(self):
        rc = g.RELEASE_COMMAND
        for cmd in ["git tag -a v0.18.0 -m x", "git -c credential.username=a push origin v0.18.0", "gh release create v1.0.0",
                    "python E:/Builds/releases/tools/gh_release_0180.py n.md a.apk", "git push origin --tags"]:
            self.assertTrue(rc.search(cmd), cmd)
        for cmd in ["git tag --list", "git tag", "git push origin main", "git log --oneline", "gh issue view 5", "gh release list"]:
            self.assertFalse(rc.search(cmd), cmd)


if __name__ == "__main__":
    unittest.main()
