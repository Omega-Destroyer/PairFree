#!/usr/bin/env python3
"""Reads the app's language files and checks a translation against the English one.

The English file is `android/app/src/main/res/values/strings.xml`; a translation is the same file under
`values-<language code>/` (docs/TRANSLATING.md). `problems()` lists what would break or mislead at run time; the tests in
`test_translations.py` fail on any, and `translation_status.py` reports how much of each language is there.
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android/app/src/main/res"
ENGLISH = RES / "values/strings.xml"

# %s, %1$s, %2$d, %.1f ...; %% is a percent sign, not a placeholder.
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")
VENDOR = "vendor_"


class Entry:
    def __init__(self, name, kind, texts, translatable):
        self.name, self.kind, self.texts, self.translatable = name, kind, texts, translatable


def read(path):
    """The entries of a language file by name. `texts` is {None: text} for a string, {quantity: text} for a plural."""
    out = {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if el.tag == "string":
            texts = {None: "".join(el.itertext())}
        elif el.tag == "plurals":
            texts = {item.get("quantity"): "".join(item.itertext()) for item in el.findall("item")}
        else:
            continue
        out[name] = Entry(name, el.tag, texts, el.get("translatable") != "false")
    return out


def placeholders(text):
    """The placeholders of [text], as a sorted list of (position or order, type)."""
    found = PLACEHOLDER.findall(text.replace("%%", ""))
    return sorted((int(pos) if pos else i + 1, kind) for i, (pos, kind) in enumerate(found))


def problems(english, translation):
    """What is wrong with [translation] (entries by name), in words; empty when it is sound."""
    out = []
    for name, entry in translation.items():
        base = english.get(name)
        if base is None:
            out.append(f"{name}: not in the English file (renamed or removed there?)")
            continue
        if not base.translatable:
            out.append(f"{name}: marked translatable=\"false\" in the English file, leave it out")
            continue
        if entry.kind != base.kind:
            out.append(f"{name}: is a {entry.kind} here and a {base.kind} in the English file")
            continue
        if entry.kind == "plurals" and "other" not in entry.texts:
            out.append(f"{name}: a plural needs its \"other\" form")
        wanted = {tuple(placeholders(t)) for t in base.texts.values()}
        for quantity, text in entry.texts.items():
            got = tuple(placeholders(text))
            # A plural's form for one may drop the number ("one bar"); otherwise the placeholders are the English ones.
            if got not in wanted and not (entry.kind == "plurals" and not got):
                where = f" ({quantity})" if quantity else ""
                out.append(f"{name}{where}: placeholders {list(got)} differ from the English {sorted(wanted)}")
    return out


def languages():
    """The translations in the app: language code (as in the folder's name) to its file."""
    return {p.parent.name[len("values-"):]: p for p in sorted(RES.glob("values-*/strings.xml"))}


def coverage(english, translation):
    """How many translatable entries [translation] has, of how many: (own, own total, vendor wording, vendor total)."""
    own = [n for n, e in english.items() if e.translatable and not n.startswith(VENDOR)]
    vendor = [n for n, e in english.items() if e.translatable and n.startswith(VENDOR)]
    return sum(n in translation for n in own), len(own), sum(n in translation for n in vendor), len(vendor)
