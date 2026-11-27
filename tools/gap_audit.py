"""Lists the open items of the vendor-app gap audit (owner, 2026-11-12): feature-level ids of each brand's table that no code uses and that
data/unused_ids.json has not explained yet (a reason that starts with "gap-audit"). GapAuditTest (Gradle) keeps the file true.

usage: python tools/gap_audit.py [brand]
"""
import json
import sys
from pathlib import Path

FILE = Path(__file__).resolve().parent.parent / "data" / "unused_ids.json"


def open_items(data):
    return {b: [i for i, why in ids.items() if why.startswith("gap-audit")] for b, ids in data.items() if not b.startswith("_")}


def main(argv):
    data = json.loads(FILE.read_text(encoding="utf-8"))
    items = open_items(data)
    for brand, ids in items.items():
        if argv and brand not in argv:
            continue
        explained = len(data[brand]) - len(ids)
        print(f"{brand}: {len(ids)} open, {explained} explained")
        for i in ids:
            print("   ", i)
    print("open in all:", sum(len(v) for v in items.values()))


if __name__ == "__main__":
    main(sys.argv[1:])
