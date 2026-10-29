"""Refresh data/soundcore/devices.json from a local checkout of OpenSCQ30.

usage: python tools/import_openscq30.py PATH_TO_OPENSCQ30

OpenSCQ30 (https://github.com/Oppzippy/OpenSCQ30, Kyle Scheuing, GPL-3.0-or-later) supports
about 40 Soundcore models. This reads its source and writes, for every model, the facts
PairFree uses: the model number, its English name, the OpenSCQ30 module that implements it,
and the features that module offers. Nothing of OpenSCQ30's code is copied into this repo.
What PairFree learned itself (reports, ldac_excludes, extra_features, notes, state, eq, buttons and
the rest of OURS) is kept.
"""
import copy
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEVICES = ROOT / "data" / "soundcore" / "devices.json"
OURS = ("reports", "ldac_excludes", "extra_features", "notes", "state", "checksum", "service_uuid", "battery", "commands",
        "min_firmware", "kind", "eq", "buttons", "fast_pair")


def names(scq: Path) -> dict:
    """Model number -> names, from OpenSCQ30's English strings ("Soundcore Q30 / Life Q30" -> two names)."""
    out = {}
    text = (scq / "lib/i18n/en/openscq30-lib.ftl").read_text(encoding="utf-8")
    for code, name in re.findall(r"^soundcore-([ad]\d{4}c?) = (.+)$", text, re.M):
        parts = [p.strip() for p in name.split(" / ")]
        out[code.upper()] = [p if p.lower().startswith("soundcore") else f"Soundcore {p}" for p in parts]
    return out


def modules(scq: Path) -> dict:
    """Model number -> OpenSCQ30 module, from DeviceModel::device_registry."""
    src = (scq / "lib/src/devices/device_model.rs").read_text(encoding="utf-8")
    registry = src[src.index("fn device_registry"):src.index("fn demo_device_registry")]
    out = {}
    pattern = r"((?:Self::Soundcore\w+\s*\|\s*)*Self::Soundcore\w+)\s*=>\s*\{?\s*new_soundcore_device!\(soundcore::(\w+)\)"
    for lhs, module in re.findall(pattern, registry):
        for model in re.findall(r"Self::Soundcore(\w+)", lhs):
            if model != "Development":
                out[model.upper()] = module
    return out


def features(scq: Path, module: str) -> list:
    """The modules OpenSCQ30's device builder adds for this model, in order."""
    text = (scq / f"lib/src/devices/soundcore/{module}.rs").read_text(encoding="utf-8")
    calls = re.findall(r"builder\s*\.\s*([a-z_0-9]+)\s*[(:<]", text)
    calls += re.findall(r"builder\.module_collection\(\)\.([a-z_0-9]+)\(", text)
    return [c for c in dict.fromkeys(calls) if c not in ("module_collection", "add_state_update")]


def dump(data):
    """devices.json as written: indented, with each [field, bytes] pair of a state layout on one line, and in
    touch controls each list of fields or buttons and each press on one line."""
    one_line = {}

    def mark(value):
        key = f"@@{len(one_line)}@@"
        one_line[key] = json.dumps(value, ensure_ascii=False)
        return key

    data = copy.deepcopy(data)
    for device in data.get("devices", []):
        buttons = device.get("buttons")
        if not buttons:
            continue
        for group in buttons["groups"]:
            group["fields"], group["order"] = mark(group["fields"]), mark(group["order"])
            group["presses"] = {press: mark(spec) for press, spec in group["presses"].items()}
        if "fixed" in buttons:
            buttons["fixed"] = mark(buttons["fixed"])
    text = json.dumps(data, indent=2, ensure_ascii=False)
    text = re.sub(r'"(@@\d+@@)"', lambda m: one_line[m.group(1)], text)
    return re.sub(r'\[\s+("(?:[^"\\]|\\.)*"),\s+(\d+)\s+\]', r"[\1, \2]", text) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    scq = Path(sys.argv[1])
    commit = subprocess.run(["git", "-C", str(scq), "log", "-1", "--format=%H %cs"], capture_output=True, text=True,
                            check=True).stdout.split()
    data = json.loads(DEVICES.read_text(encoding="utf-8"))
    old = {d["model"]: d for d in data["devices"]}
    all_names, all_modules = names(scq), modules(scq)
    devices = []
    for model in sorted(all_modules):
        module = all_modules[model]
        entry = {
            "model": model,
            "names": old.get(model, {}).get("names") or all_names.get(model, [f"Soundcore {model}"]),
            "openscq30": {"module": module, "features": features(scq, module)},
        }
        for key in OURS:
            if key in old.get(model, {}):
                entry[key] = old[model][key]
        devices.append(entry)
    # Models OpenSCQ30's library does not list, taken from elsewhere in its repository (module null and a source,
    # such as its device faker's captured states), stay as they are.
    devices += [d for m, d in old.items() if m not in all_modules and d.get("openscq30", {}).get("module") is None]
    devices.sort(key=lambda d: d["model"])
    data["source"] = {
        "project": "OpenSCQ30",
        "url": "https://github.com/Oppzippy/OpenSCQ30",
        "author": "Kyle Scheuing",
        "license": "GPL-3.0-or-later",
        "commit": commit[0],
        "date": commit[1],
        "note": "Model numbers, names and each model's features come from OpenSCQ30's source at this commit "
                "(tools/import_openscq30.py). Each model's state layout, battery scales, checksum, service and "
                "command overrides were read from OpenSCQ30's parsers at the same commit and checked against its "
                "tests. What PairFree found on hardware is in reports and notes, and in battery notes where it "
                "differs from OpenSCQ30.",
    }
    data["devices"] = devices
    DEVICES.write_text(dump(data), encoding="utf-8", newline="\n")
    print(f"{len(devices)} models from OpenSCQ30 {commit[0][:7]} ({commit[1]})")


if __name__ == "__main__":
    main()
