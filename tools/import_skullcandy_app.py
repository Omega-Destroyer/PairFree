"""Writes data/skullcandy/sdk_devices.json from Skullcandy's own app (5.0.6): the ten device configurations of the Bragi SDK
(`com/bragi/sdk/android/device/config/c.java` in a jadx decompile kept outside the repo). Each holds a model, its chipset, its
serial services and the features the app offers it, each feature with the transport that carries it (BOS_AT, BOS_RPC, AIROHA_RACE,
AIROHA_RACE_CUSTOM, PLUGIN) and its attributes.

    python tools/import_skullcandy_app.py PATH_TO_jadx/sources [--check]

PairFree reads the decompiled code; nothing of it is copied. The configuration is parsed as the Java the decompiler wrote
(`new Feature(type, service, null, null, attributes, 28, null)`), not retyped.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "skullcandy" / "sdk_devices.json"
CONFIG = "com/bragi/sdk/android/device/config/c.java"


class P:
    """A small parser for the decompiled Java expressions: calls, `new`, arrays, strings, numbers and names."""

    def __init__(self, text):
        self.t = text
        self.i = 0

    def ws(self):
        while self.i < len(self.t) and self.t[self.i] in " \n\r\t":
            self.i += 1

    def expr(self):
        self.ws()
        t = self.t
        if t.startswith("(Object[])", self.i) or t.startswith("(byte[])", self.i):
            self.i = t.index(")", self.i) + 1
            return self.expr()
        if t[self.i] == '"':
            j = self.i + 1
            while t[j] != '"':
                j += 2 if t[j] == "\\" else 1
            s = t[self.i + 1:j]
            self.i = j + 1
            return ("str", s)
        if t[self.i] == "{":
            self.i += 1
            items = self.items("}")
            return ("arr", items)
        m = re.compile(r"-?\d+(\.\d+)?f?|[A-Za-z_$][\w$.]*").match(t, self.i)
        assert m, t[self.i:self.i + 40]
        word = m.group(0)
        self.i = m.end()
        self.ws()
        if word == "new":
            cls = re.compile(r"[\w$.]+(\[\])*").match(t, self.i)
            self.i = cls.end()
            self.ws()
            if t[self.i] == "(":
                self.i += 1
                return ("new", cls.group(0), self.items(")"))
            if t[self.i] == "{":
                self.i += 1
                return ("arr", self.items("}"))
            return ("new", cls.group(0), [])
        if self.i < len(t) and t[self.i] == "(":
            self.i += 1
            return ("call", word, self.items(")"))
        if re.fullmatch(r"-?\d+(\.\d+)?f?", word):
            return ("num", float(word.rstrip("f")) if "." in word else int(word))
        return ("name", word)

    def items(self, close):
        out = []
        while True:
            self.ws()
            if self.t[self.i] == close:
                self.i += 1
                return out
            out.append(self.expr())
            self.ws()
            if self.t[self.i] == ",":
                self.i += 1


def simple(x):
    """A value as plain JSON where it is a literal."""
    k = x[0]
    if k in ("str", "num"):
        return x[1]
    if k == "arr" or (k == "call" and x[1].endswith("listOf")):
        items = x[1] if k == "arr" else x[2]
        if len(items) == 1 and items[0][0] == "arr":
            items = items[0][1]
        return [simple(v) for v in items]
    if k == "call" and x[1].endswith("mapOf"):
        return {str(simple(a[2][0])): simple(a[2][1]) for a in x[2] if a[0] == "call"}
    if k == "name":
        return x[1].split(".")[-1]
    return None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    root = Path(args[0])
    src = root / CONFIG
    consts = {m.group(1): int(m.group(2)) for m in re.finditer(r"byte (\w+) = (-?\d+);", (root / "com/jieli/bluetooth/constant/AttrAndFunCode.java").read_text(encoding="utf-8"))}

    def with_consts(s):
        return re.sub(r"AttrAndFunCode\.(\w+)", lambda m: str(consts[m.group(1)]), s)

    text = with_consts(src.read_text(encoding="utf-8"))
    cfg_b = with_consts((root / "com/bragi/sdk/android/device/config/b.java").read_text(encoding="utf-8"))
    sdk_uuid = {}
    wraps = re.findall(r"wrap\(new byte\[\]\{([^}]*)\}\)", cfg_b)
    for letter, arr in zip("ab", wraps):
        raw = bytes(int(v) & 0xFF for v in arr.split(","))
        h = raw.hex()
        sdk_uuid[letter] = f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"
    body = text[text.index("static {"):]
    # statements: `Type name = expr;` and `name = expr;` and `return expr;`
    env = {}
    statements = []
    for m in re.finditer(r"\n\s+(?:(?:public static final |static |final )?[\w.<>\[\]]+ )?([\w]+) = (.*?);\n", body, re.S):
        statements.append((m.group(1), m.group(2)))
    methods = {}
    for m in re.finditer(r"public static (?:DeviceConfig|BragiBleConfig) (\w)\(.*?\) \{(.*?)\n    \}", text, re.S):
        methods[m.group(1)] = m.group(2)

    def parse(s):
        return P(s).expr()

    def resolve(x, env):
        if x[0] == "name" and x[1] in env:
            return env[x[1]]
        return x

    def build_scope(chunk):
        scope = dict(env)
        for name, expr in re.findall(r"\n\s+(?:[\w.<>\[\]]+ )?(\w+) = (.*?);(?=\n)", chunk, re.S):
            try:
                scope[name] = parse(expr)
            except Exception:
                pass
        return scope

    def feature_of(x, scope):
        x = resolve(x, scope)
        assert x[0] == "new" and x[1] == "Feature", x
        a = x[2]
        typ = simple(resolve(a[0], scope))
        svc = simple(resolve(a[1], scope))
        attrs = []
        if len(a) > 4 and a[4][0] != "name":
            for at in (resolve(a[4], scope)[2] if resolve(a[4], scope)[0] == "call" else []):
                at = resolve(at, scope)
                if at[0] == "arr":
                    items = at[1]
                else:
                    items = [at]
                for it in items:
                    it = resolve(it, scope)
                    if it[0] == "new":
                        attrs.append({"attribute": it[1].split(".")[-1], "args": [simple(resolve(v, scope)) for v in it[2]]})
                    elif it[0] == "name":
                        attrs.append({"attribute": it[1].split(".")[0]})
                    elif it[0] == "call" and it[1].endswith("listOf"):
                        for sub in it[2]:
                            sub = resolve(sub, scope)
                            if sub[0] == "new":
                                attrs.append({"attribute": sub[1].split(".")[-1], "args": [simple(resolve(v, scope)) for v in sub[2]]})
                            elif sub[0] == "name":
                                attrs.append({"attribute": sub[1].split(".")[0]})
        return {"type": typ, "service": svc, **({"attributes": attrs} if attrs else {})}

    def uuid_of(x, scope):
        x = resolve(x, scope)
        return x

    # the static scope: UUIDs, maps and models
    static = build_scope(body[:body.index("\n    }\n\n    public static")])
    uuids = {}
    for name, x in static.items():
        if x[0] == "call" and x[1] == "UUID.fromString":
            uuids[name] = x[2][0][1]
    # `d`, `e`, `c`, `b` of the class are the static UUIDs: uuidFromStringN names map by order
    named = {"b": uuids.get("uuidFromString"), "c": uuids.get("uuidFromString2"), "d": uuids.get("uuidFromString3"), "e": uuids.get("uuidFromString5")}
    models = {}
    for name, x in static.items():
        if x[0] == "new" and x[1] == "DeviceModel":
            a = x[2]
            models[name] = {"names": simple(resolve(a[0], static)), "type": simple(a[2]), "chipset": simple(a[3][2][0]) if a[3][0] == "call" else None, "key": a[4][1]}
    # statics i..l hold the models that the method configs use
    static_model = {}
    for m in re.finditer(r"\n\s+([ijkl]) = (deviceModel\d+);", body):
        static_model[m.group(1)] = m.group(2)

    def services(x, scope):
        x = resolve(x, scope)
        out = {}
        labels = ["control", "secondary", "unused2", "race", "unused4", "unused5", "unused6"]
        for label, a in zip(labels, x[2]):
            a = resolve(a, scope)
            if a[0] == "new" and a[1].endswith("Spp"):
                u = resolve(a[2][0], scope)
                if u[0] == "name":
                    key = u[1].split(".")[-1]
                    out[label] = {"c": named["c"], "d": named["d"], "e": named["e"], "b": named["b"]}.get(key, u[1])
                    if u[1].startswith("b."):
                        out[label] = sdk_uuid[u[1][2:]]
                    elif out[label] in uuids:
                        out[label] = uuids[out[label]]
                else:
                    out[label] = simple(u)
        return out

    def ble(x, scope):
        x = resolve(x, scope)
        out = {}
        if x[0] == "call" and x[1] == "a":
            arr = resolve(x[2][0], scope)
            out["modelId"] = simple(arr)
        return out

    configs = []

    def config_of(model_name, x, scope):
        assert x[0] == "new" and x[1] == "DeviceConfig", x
        a = x[2]
        mname = resolve(a[0], scope)
        feats = [feature_of(f, scope) for f in resolve(a[2], scope)[2][0][1]] if resolve(a[2], scope)[0] == "call" else []
        return {"service_type": simple(resolve(a[1], scope)), "features": feats, "services": services(a[3], scope), "ble_model_id": ble(a[4], scope)}

    # the first six configs and the speaker are assigned in the static block; the four others by methods
    assign = {}
    for name, expr in re.findall(r"\n\s+([m-v]) = (.*?);(?=\n)", body[:body.index("\n    public static BragiBleConfig a(")], re.S):
        assign[name] = expr
    scope = static
    result = []
    order = {"m": "deviceModel", "n": "deviceModel2", "o": "deviceModel3", "p": "deviceModel4", "q": "deviceModel5", "v": "deviceModel10"}
    for letter, mname in order.items():
        x = parse(assign[letter])
        cfg = config_of(mname, x, scope)
        cfg["model"] = models[mname]
        result.append(cfg)
    for letter, meth in (("r", "d"), ("s", "e"), ("t", "c"), ("u", "b")):
        mb = methods[meth]
        sc = dict(static)
        sc.update(build_scope(mb))
        # the returned expression
        ret = re.search(r"return (new DeviceConfig\(.*?\));\s*$", mb, re.S).group(1)
        x = parse(ret)
        mod = resolve(x[2][0], sc)
        model = models[mod[1]] if mod[0] == "name" and mod[1] in models else (models[static_model[mod[1]]] if mod[0] == "name" else None)
        cfg = config_of("", x, sc)
        cfg["model"] = model
        # the BLE id comes from a(byte[]) in the method
        bm = re.search(r"bragiBleConfigA = a\(new byte\[\]\{(\d+)\}\)", mb)
        cfg["ble_model_id"] = {"modelId": [int(bm.group(1))]} if bm else {}
        result.append(cfg)
    data = {"_about": "Skullcandy 5.0.6's Bragi SDK device configurations (com.bragi.sdk.android.device.config.c), imported by tools/import_skullcandy_app.py. "
                      "Each feature names its transport (BOS_AT, BOS_RPC, AIROHA_RACE, AIROHA_RACE_CUSTOM, PLUGIN). Only these ten products have a config in the SDK; "
                      "the app's other models go through other routes (docs/skullcandy/PROTOCOL.md).",
            "devices": result}
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        sys.exit(0 if OUT.exists() and OUT.read_text(encoding="utf-8") == text else "data/skullcandy/sdk_devices.json is out of date")
    OUT.write_text(text, encoding="utf-8")
    print(OUT, len(result), "devices")


if __name__ == "__main__":
    main()
