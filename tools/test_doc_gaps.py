import unittest

import doc_gaps


class DocGaps(unittest.TestCase):
    def test_no_new_unbuilt_statement_without_a_blocker(self):
        old = doc_gaps.baseline()
        new = [f"{f}: {l[:140]}" for f, l in doc_gaps.statements() if (f, l) not in old]
        self.assertEqual([], new, "a protocol doc says a feature is not built, not sent or not read: build it, or write 'blocked: <reason>; checked: <what was "
                                  "opened and what it showed>' in the same paragraph (CLAUDE.md, Before saying something cannot be done)")

    def test_the_baseline_only_holds_statements_that_are_still_there(self):
        now = set(doc_gaps.statements())
        gone = [f"{f}: {l[:120]}" for f, l in doc_gaps.baseline() if (f, l) not in now]
        self.assertEqual([], gone, "built or rewritten: delete these from data/doc_gaps_baseline.json (python tools/doc_gaps.py --write)")


if __name__ == "__main__":
    unittest.main()
