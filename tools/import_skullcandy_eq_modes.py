"""Writes data/skullcandy/eq_modes.json from Skullcandy's own app (5.0.6): for each product, the equalizer modes the app offers and the number each is sent as
(the Airoha PEQ category: 1 to 7 for the earbuds' own presets, 101 and up for the custom curves).

The app's Flutter code holds this as `SKDYEqualizerModeUtils.valueForFeature(feature, product)` (features/skdyequalizermode.dart), a jump table over the product
and comparisons on the feature; the Dart snapshot was run through Blutter (outside the repo) and this runs the function's instructions for every pair.

    python tools/import_skullcandy_eq_modes.py PATH_TO_blutter_out [--check]
"""
import json
import re
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "data" / "skullcandy" / "eq_modes.json"
if len(sys.argv) < 2:
    sys.exit(__doc__)
ROOT_OUT = Path(sys.argv[1])
import json
asm = (ROOT_OUT / "asm/skullcandy_app/features/skdyequalizermode.dart").read_text(encoding="utf-8")
m = re.search(r"static _ valueForFeature\(.*?\n  \}\n", asm, re.S)
body = m.group(0)
base = int(re.search(r"addr: (0x[0-9a-f]+)", body).group(1), 16)
ins = []
for line in body.split("\n"):
    mm = re.match(r"\s*// (0x[0-9a-f]+): (.*)$", line)
    if mm:
        ins.append((int(mm.group(1), 16), mm.group(2).strip()))
idx = {a: i for i, (a, _) in enumerate(ins)}
pp = (ROOT_OUT / "pp.txt").read_text(encoding="utf-8")
tm = re.search(r"\[pp\+0x1a638\] _Int32List\((\d+)\) \[([^\]]*)\]", pp)
table = [int(x, 16) for x in tm.group(2).split(",")]
objs = (ROOT_OUT / "objs.txt").read_text(encoding="utf-8")
def enum(cls):
    pat = re.compile(r"Obj!%s@\w+ : \{\s*Super!_Enum : \{\s*off_8: int\((0x[0-9a-f]+)\),\s*off_10: \"([^\"]*)\"" % cls)
    return {int(a, 16): b for a, b in pat.findall(objs)}
feats = enum("SKDYFeature"); models = enum("SKDYProductModel")

def run(feature, product):
    objs_ = {"fp-8": feature, "fp-0x10": product}
    regs = {}
    fl = None
    i = 0; steps = 0
    while i < len(ins) and steps < 2000:
        steps += 1
        a, s = ins[i]
        mm = re.match(r"ldur\s+(x\d+), \[fp, #(-0x[0-9a-f]+|-\d+)\]", s)
        if mm:
            off = mm.group(2)
            key = {"-8": "fp-8", "-0x8": "fp-8", "-0x10": "fp-0x10"}.get(off)
            if key: regs[mm.group(1)] = ("obj", objs_[key])
            i += 1; continue
        mm = re.match(r"LoadField: r(\d+) = r(\d+)->field_7", s)
        if mm:
            src = regs.get("x" + mm.group(2))
            if src and src[0] == "obj": regs["x" + mm.group(1)] = ("int", src[1])
            i += 1; continue
        mm = re.match(r"r0 = BoxInt64Instr\(r(\d+)\)", s)
        if mm:
            v = regs.get("x" + mm.group(1))
            regs["w0"] = ("int", v[1] * 2) if v else None
            i += 1; continue
        mm = re.match(r"cmp\s+(\w+), #(0x[0-9a-f]+|-?\d+)", s)
        if mm:
            r = regs.get(mm.group(1)); fl = (r[1] if r else None, int(mm.group(2), 0)); i += 1; continue
        mm = re.match(r"b\.(gt|lt|le|ge|eq|ne)\s+#(0x[0-9a-f]+)", s)
        if mm:
            op, tgt = mm.group(1), int(mm.group(2), 16)
            v, c = fl
            if v is None: i += 1; continue
            take = {"gt": v > c, "lt": v < c, "le": v <= c, "ge": v >= c, "eq": v == c, "ne": v != c}[op]
            i = idx[tgt] if take else i + 1; continue
        mm = re.match(r"b\s+#(0x[0-9a-f]+)", s)
        if mm:
            i = idx[int(mm.group(1), 16)]; continue
        mm = re.match(r"tbz\s+w0, #4, #(0x[0-9a-f]+)", s)
        if mm:
            i = idx[int(mm.group(1), 16)]; continue
        if s.startswith("r2 = _Int32List"):
            i += 1; continue
        if s.startswith("ArrayLoad: r2 = r2[r0]"):
            w = regs.get("w0"); regs["x2"] = ("int", table[w[1] // 2]) if w else None; i += 1; continue
        if s.startswith("adr  ") or s.startswith("adr\t"):
            i += 1; continue
        if re.match(r"add\s+x3, x3, x2", s):
            regs["x3"] = ("int", base + regs["x2"][1]); i += 1; continue
        if re.match(r"br\s+x3", s):
            i = idx[regs["x3"][1]]; continue
        mm = re.match(r"r0 = (-?\d+)$", s)
        if mm:
            regs["ret"] = int(mm.group(1)); i += 1; continue
        if s == "ret":
            return regs.get("ret")
        i += 1
    return None


eq_features = [i for i in sorted(feats) if feats[i].endswith("Mode") or feats[i].endswith("Mode2") or feats[i].endswith("Mode3") or feats[i].endswith("Mode4") or feats[i] == "thxGamingEqualizer"]
modes = {}
for p in sorted(models):
    row = {}
    for f in eq_features:
        v = run(f, p)
        if v is not None and v != -1:
            row[feats[f]] = v
    if row:
        modes[models[p]] = row
data = {"_about": "Skullcandy 5.0.6: the equalizer modes each product offers and the number each is sent as, by tools/import_skullcandy_eq_modes.py from SKDYEqualizerModeUtils.valueForFeature "
                  "(a number 1 to 7 picks a preset the earbuds hold; 101 and up the custom curves; the Crusher 1080 ANC's 16 to 18 are its Bose modes). `modes` is keyed by the app's enum name.",
        "modes": modes}
text = json.dumps(data, indent=2, ensure_ascii=False) + chr(10)
if "--check" in sys.argv:
    sys.exit(0 if OUT.exists() and OUT.read_text(encoding="utf-8") == text else "data/skullcandy/eq_modes.json is out of date")
OUT.write_text(text, encoding="utf-8")
print(OUT, len(modes), "products with modes")
