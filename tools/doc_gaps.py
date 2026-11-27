"""List the protocol docs' unbuilt-feature statements that carry no "blocked ... checked:" line.

CLAUDE.md (2026-11-15): a feature a protocol doc says PairFree does not send, does not build, or has not read is either built or written as
"blocked: <reason>; checked: <what was opened and what it showed>". The gap audit of 2026-11-12 only watched ids in the generated tables, so a gap
written as a sentence (the older JieLi models' equalizer, issue #35) went unseen for days. `test_doc_gaps.py` fails on a new such statement;
the statements that were already there are in data/doc_gaps_baseline.json as a worklist: build one and delete its line, or rewrite it with
its "blocked: ...; checked: ..." and the line leaves the list.

    python tools/doc_gaps.py            lists the statements not in the baseline
    python tools/doc_gaps.py --write    writes the baseline from what is there now (only to shrink it: it cannot be used to hide a new one)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "data" / "doc_gaps_baseline.json"
PHRASES = re.compile(
    r"does not send|do not send|not (?:yet )?built|not yet|not covered|not offered|not read\b|not implemented|not imported|to do:|"
    r"PairFree does not|PairFree builds none|is not built|are not built",
    re.IGNORECASE,
)
BLOCKED = re.compile(r"checked:", re.IGNORECASE)


def normalise(line):
    return re.sub(r"\s+", " ", line).strip()


def statements():
    """(file, normalised line) for each statement without a checked: line within its paragraph."""
    out = []
    for doc in sorted((ROOT / "docs").glob("*/PROTOCOL.md")):
        lines = doc.read_text(encoding="utf-8").split("\n")
        for i, line in enumerate(lines):
            if not PHRASES.search(line):
                continue
            # The paragraph: this line and the following ones up to a blank line or the next list item.
            para = [line]
            for nxt in lines[i + 1:i + 8]:
                if not nxt.strip() or re.match(r"\s*([-*]|\d+\.)\s", nxt) or nxt.startswith("#"):
                    break
                para.append(nxt)
            if BLOCKED.search(" ".join(para)):
                continue
            out.append((doc.relative_to(ROOT).as_posix(), normalise(line)))
    return out


def baseline():
    if not BASELINE.exists():
        return {}
    return {(e["file"], e["line"]) for e in json.loads(BASELINE.read_text(encoding="utf-8"))["statements"]}


if __name__ == "__main__":
    found = statements()
    if "--write" in sys.argv:
        old = baseline()
        keep = [s for s in found if s in old] if old else found
        BASELINE.write_text(json.dumps({
            "_about": "Statements in docs/*/PROTOCOL.md that say a feature is not built, not sent or not read, with no 'checked:' line (tools/doc_gaps.py). "
                      "A worklist: build the feature, or rewrite the statement as 'blocked: <reason>; checked: <what was opened and what it showed>', "
                      "and delete its entry. New statements are not added here; test_doc_gaps.py fails on them.",
            "statements": [{"file": f, "line": l} for f, l in keep],
        }, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{len(keep)} statements in the baseline")
    else:
        old = baseline()
        new = [s for s in found if s not in old]
        for f, l in new:
            print(f"{f}: {l[:160]}")
        print(f"{len(new)} not in the baseline, {len(found) - len(new)} in it")
