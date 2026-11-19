"""Writes data/skullcandy/race_commands.json from Skullcandy's own app (5.0.6): the RACE command classes of its Flutter code (58 files, 46 with literal byte strings)
(`utils/race_commands/**`, Blutter output outside the repo). For each class, the byte strings its code writes (the request, with the
RACE `05 5A` header the app prepends: `05 5A <length u16 LE> <command id u16 LE> <payload>`) and the reply prefixes it checks (`05 5B`
answers, `05 5D` notifications). Only the literal strings are taken; values the code builds at run time (the setting being written) are
not in the file and are to be read from the class itself.

    python tools/import_skullcandy_race.py PATH_TO_blutter_out [--tota] [--check]

With --tota it reads the app's BES TOTA command classes (`utils/tota_commands`) into data/skullcandy/tota_commands.json instead.
"""
import collections
import glob
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "skullcandy" / "race_commands.json"
SUB = "asm/skullcandy_app/utils/race_commands"
if "--tota" in sys.argv:
    # BES's TOTA commands (the app's utils/tota_commands), same method, own file
    OUT = ROOT / "data" / "skullcandy" / "tota_commands.json"
    SUB = "asm/skullcandy_app/utils/tota_commands"


def load_build(path):
    t = open(path, encoding="utf-8").read()
    m = re.search(r"\n  _ buildCommand\(.*?\n  \}\n", t, re.S)
    body = m.group(0)
    ins = []
    for line in body.split("\n"):
        mm = re.match(r"\s*// (0x[0-9a-f]+): (.*)$", line)
        if mm:
            ins.append((int(mm.group(1), 16), mm.group(2).strip()))
    return ins


def run(ins, idx, flag):
    addr_i = {a: i for i, (a, _) in enumerate(ins)}
    regs = {"w0": None}
    fl = None
    i = 0
    steps = 0
    result = None
    while i < len(ins) and steps < 500:
        steps += 1
        a, s = ins[i]
        mm = re.match(r"cmp\s+(\w+), #(0x[0-9a-f]+|\d+)", s)
        if mm:
            reg = mm.group(1)
            val = int(mm.group(2), 0)
            v = regs.get(reg)
            fl = (v, val)
            i += 1
            continue
        mm = re.match(r"b\.(gt|lt|le|ge|eq|ne)\s+#(0x[0-9a-f]+)", s)
        if mm:
            op, tgt = mm.group(1), int(mm.group(2), 16)
            v, c = fl if fl else (None, None)
            if v is None:
                i += 1
                continue
            take = {"gt": v > c, "lt": v < c, "le": v <= c, "ge": v >= c, "eq": v == c, "ne": v != c}[op]
            i = addr_i[tgt] if take else i + 1
            continue
        mm = re.match(r"b\s+#(0x[0-9a-f]+)", s)
        if mm:
            tgt = int(mm.group(1), 16)
            if tgt in addr_i:
                i = addr_i[tgt]
            else:
                return result
            continue
        mm = re.match(r"tbnz\s+w0, #4, #(0x[0-9a-f]+)", s)
        if mm and regs.get("bool") is not None:
            tgt = int(mm.group(1), 16)
            i = addr_i[tgt] if not regs["bool"] else i + 1  # bit 4 set means false
            continue
        mm = re.match(r"LoadField: r(\d+) = r0->field_7$", s)
        if mm:
            regs["x" + mm.group(1)] = idx
            i += 1
            continue
        if re.match(r"LoadField: r0 = r2->field_b", s):
            regs["bool"] = flag
            i += 1
            continue
        if s.startswith("r0 = BoxInt64Instr"):
            regs["w0"] = idx * 2
            i += 1
            continue
        mm = re.match(r'r0 = "([^"]*)"', s)
        if mm:
            result = mm.group(1)
            i += 1
            continue
        mm = re.match(r"r0 = (-?\d+)$", s)
        i += 1
    return result


def by_model(path, names):
    """What `buildCommand` returns for each model (by the app's enum index) and for the on and off value, grouped."""
    try:
        ins = load_build(path)
    except AttributeError:
        return None
    out = {}
    for flag in (True, False):
        groups = collections.defaultdict(list)
        for m in names:
            r = run(ins, m["index"], flag)
            if r:
                groups[r].append(m["model"])
        out["on" if flag else "off"] = [{"bytes": r, "models": ms} for r, ms in groups.items()]
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    base = Path(args[0]) / SUB
    names = json.loads((ROOT / "data" / "skullcandy" / "chips.json").read_text(encoding="utf-8"))["models"]
    commands = []
    for path in sorted(glob.glob(str(base / "**" / "*.dart"), recursive=True)):
        text = Path(path).read_text(encoding="utf-8")
        cls = re.search(r"^class (\w+)", text, re.M)
        if not cls:
            continue
        requests, replies = [], []
        for m in re.finditer(r'\br\d+ = "([^"]*)"', text):
            s = m.group(1)
            if re.fullmatch(r"([0-9A-Fa-f]{2} ?)+", s) and len(s.replace(" ", "")) >= 4:
                target = requests if " " in s else replies
                if s not in target:
                    target.append(s)
        if not requests and not replies:
            continue
        rel = Path(path).relative_to(base).as_posix().replace(".dart", "")
        entry = {"file": rel, "class": cls.group(1), "request_literals": requests, "reply_prefixes": replies}
        built = by_model(path, names)
        if built and (built["on"] or built["off"]):
            entry["built_by_model"] = built
        commands.append(entry)
    data = {"_about": "Skullcandy 5.0.6: the RACE command classes of the app's Flutter code (utils/race_commands), by tools/import_skullcandy_race.py. Only the byte strings the code "
                      "writes literally are here; request values built at run time are to be read from each class (docs/skullcandy/PROTOCOL.md section 11).",
            "commands": commands}
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        sys.exit(0 if OUT.exists() and OUT.read_text(encoding="utf-8") == text else "data/skullcandy/race_commands.json is out of date")
    OUT.write_text(text, encoding="utf-8")
    print(OUT, len(commands), "commands")


if __name__ == "__main__":
    main()
