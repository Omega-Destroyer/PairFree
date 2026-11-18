"""Writes data/skullcandy/chips.json from Skullcandy's own app (5.0.6): which chip family each of the app's 56 product models is on.

The app's Flutter code holds this as `SKDYProductModelInfo.chip()` (skdyproductmodel.dart): a jump table indexed by the model's enum value, whose
targets return one of five `SKDYProductChip` values (qualcomm, airoha, bes, actions, jieli). The Dart snapshot (libapp.so) was run through Blutter
(outside the repo); this reads its output.

    python tools/import_skullcandy_dart.py PATH_TO_blutter_out [--check]

where the folder holds `pp.txt`, `objs.txt` and `asm/`.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "skullcandy" / "chips.json"
MODEL_FILE = "asm/skullcandy_app/product/skdyproductmodel.dart"


def enum_values(objs, cls):
    pat = re.compile(r"Obj!%s@\w+ : \{\s*Super!_Enum : \{\s*off_8: int\((0x[0-9a-f]+)\),\s*off_10: \"([^\"]*)\"" % cls)
    return {int(a, 16): b for a, b in pat.findall(objs)}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    root = Path(args[0])
    pp = (root / "pp.txt").read_text(encoding="utf-8")
    objs = (root / "objs.txt").read_text(encoding="utf-8")
    asm = (root / MODEL_FILE).read_text(encoding="utf-8")
    models = enum_values(objs, "SKDYProductModel")
    chips_by_pp = {}
    for m in re.finditer(r"\[pp\+(0x[0-9a-f]+)\] Obj!SKDYProductChip@\w+ : \{\s*Super!_Enum : \{\s*off_8: int\((0x[0-9a-f]+)\),\s*off_10: \"([^\"]*)\"", pp):
        chips_by_pp[m.group(1)] = m.group(3)
    # the chip() function
    start = asm.index("static _ SKDYProductModelInfo.chip(")
    end = asm.index("\n  }\n", start)
    body = asm[start:end]
    base = int(re.search(r"addr: (0x[0-9a-f]+)", body).group(1), 16)
    table_slot = re.search(r"; \[pp\+(0x[0-9a-f]+)\] _Int32List", body).group(1)
    table_line = re.search(r"\[pp\+%s\] _Int32List\((\d+)\) \[([^\]]*)\]" % table_slot, pp)
    count = int(table_line.group(1))
    table = [int(v, 16) for v in table_line.group(2).split(",")]
    assert len(table) == count == len(models), (len(table), count, len(models))
    # each case: an address and the chip returned there; `adr x2, #base` then `add x2, x2, x1` means target = base + table[i]
    cases = {}
    for m in re.finditer(r"// (0x[0-9a-f]+): r0 = Instance_SKDYProductChip\s*\n\s*//\s+0x[0-9a-f]+: add\s+x0, PP, #0x[0-9a-f]+, lsl #12\s+; \[pp\+(0x[0-9a-f]+)\]", body):
        cases[int(m.group(1), 16)] = chips_by_pp[m.group(2)]
    default_chip = cases[max(cases)]
    result = []
    for i in range(count):
        target = base + table[i]
        result.append({"index": i, "model": models[i], "chip": cases[target]})
    data = {"_about": "Skullcandy 5.0.6: the chip family of each of the app's 56 product models, read from SKDYProductModelInfo.chip() in the Flutter code (Blutter output), by tools/import_skullcandy_dart.py. "
                      "`model` is the app's own enum name; the manifest's product ids are joined by name in data/skullcandy/devices.json.",
            "chips": ["qualcomm", "airoha", "bes", "actions", "jieli"], "models": result}
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        sys.exit(0 if OUT.exists() and OUT.read_text(encoding="utf-8") == text else "data/skullcandy/chips.json is out of date")
    OUT.write_text(text, encoding="utf-8")
    print(OUT, count, "models; default case", default_chip)


if __name__ == "__main__":
    main()
