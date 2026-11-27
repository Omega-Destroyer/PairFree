#!/usr/bin/env python3
"""How much of each language the app has: PairFree's own text and the vendors' wording, counted apart.

    python tools/translation_status.py            prints the table and any problems
    python tools/translation_status.py --write    also puts the table into docs/TRANSLATING.md
"""
import sys
from pathlib import Path

import translations

ROOT = Path(__file__).resolve().parent.parent
PAGE = ROOT / "docs/TRANSLATING.md"
BEGIN, END = "<!-- BEGIN LANGUAGES -->", "<!-- END LANGUAGES -->"


def table():
    english = translations.read(translations.ENGLISH)
    own_total = sum(1 for n, e in english.items() if e.translatable and not n.startswith(translations.VENDOR))
    vendor_total = sum(1 for n, e in english.items() if e.translatable and n.startswith(translations.VENDOR))
    rows = ["| Language | File | PairFree's text | Vendor wording |", "|---|---|---|---|",
            f"| English | `values/strings.xml` | {own_total} entries | {vendor_total} entries |"]
    notes = []
    for code, path in translations.languages().items():
        entries = translations.read(path)
        own, _, vendor, _ = translations.coverage(english, entries)
        rows.append(f"| `{code}` | `values-{code}/strings.xml` | {own * 100 // own_total}% ({own}) | {vendor * 100 // max(vendor_total, 1)}% ({vendor}) |")
        notes += [f"{code}: {p}" for p in translations.problems(english, entries)]
    return "\n".join(rows), notes


def main():
    text, notes = table()
    print(text)
    for note in notes:
        print("problem:", note)
    if "--write" in sys.argv:
        page = PAGE.read_text(encoding="utf-8")
        page = page[: page.index(BEGIN) + len(BEGIN)] + "\n" + text + "\n" + page[page.index(END):]
        PAGE.write_text(page, encoding="utf-8", newline="\n")
    sys.exit(1 if notes else 0)


if __name__ == "__main__":
    main()
