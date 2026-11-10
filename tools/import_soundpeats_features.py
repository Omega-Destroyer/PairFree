"""Write each SOUNDPEATS model's feature list into data/soundpeats/devices.json (`features`), from saved copies of the
SOUNDPEATS app's per-model replies.

The owner fetched these with their own account (2026-11-04 and 2026-11-07) into server_dump/soundpeats/v2/, which git
ignores: <code>_<version>_features.json, the reply to GET /devices/firmware/{otaCode}/versions/{otaVersion}/features
that SOUNDPEATS 2.5.3 reads to decide which settings a model shows (E:\\Builds\\research\\soundpeats-tools\\
fetch_features.py). Kept per model: the firmware version asked for, the feature codes (FSN08 and so on), and for noise
control (FSN08) the modes the app offers (0 off, 1 cancelling, 2 transparency: the `own` value of each child) with each
mode's scene codes (its children's `own`: ANC_LEVEL codes under cancelling, TRANSPARENCY_LEVEL codes under
transparency). A model that lists LE-Audio (FSN10) gets `le_audio` among its `confirmed` commands (the app offers it
only there). Left out: ids, names, icons, routes, firmware download details and everything else.

This tool only reads the saved replies; it never fetches anything.

usage: python tools/import_soundpeats_features.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEVICES = ROOT / "data" / "soundpeats" / "devices.json"
DUMP = ROOT / "server_dump" / "soundpeats" / "v2"
NOISE = "FSN08"


def own(fn):
    try:
        return json.loads(fn.get("command") or "{}").get("own")
    except ValueError:
        return None


def version_key(v):
    return [int(x) for x in re.findall(r"\d+", v)]


def features(data):
    functions = [f for g in data.get("functionGroups") or [] for f in g.get("functions") or []]
    out = {"features": sorted({f["code"] for f in functions})}
    noise = next((f for f in functions if f["code"] == NOISE), None)
    if noise:
        modes = []
        for child in sorted(noise.get("children") or [], key=lambda c: own(c) if own(c) is not None else 99):
            mode = own(child)
            if mode is None:
                continue
            scenes = [own(s) for s in child.get("children") or [] if own(s) is not None]
            modes.append({"mode": mode, "scenes": sorted(scenes)})
        out["noise"] = modes
    return out


def main():
    table = json.loads(DEVICES.read_text(encoding="utf-8"))
    by_code = {d["firmware_code"]: d for d in table["devices"]}
    latest = {}
    for f in DUMP.glob("*_features.json"):
        code, version = f.name[: -len("_features.json")].rsplit("_", 1)
        data = (json.loads(f.read_text(encoding="utf-8")).get("data")) or {}
        if not data.get("functionGroups"):
            continue
        if code not in latest or version_key(version) > version_key(latest[code][0]):
            latest[code] = (version, data)
    written, unknown = 0, []
    for code, (version, data) in sorted(latest.items()):
        device = by_code.get(code)
        if device is None:
            unknown.append(code)
            continue
        device["features"] = {"firmware": version, **features(data)}
        # The app offers LE Audio only where the model lists it (a command marked per_model in commands.json).
        if "FSN10" in device["features"]["features"] and "le_audio" not in device.get("confirmed", []):
            device["confirmed"] = [*device.get("confirmed", []), "le_audio"]
        written += 1
    table["_fields"]["features"] = (
        "From SOUNDPEATS' server, fetched with the owner's account (tools/import_soundpeats_features.py): the feature "
        "codes the SOUNDPEATS app shows for this model at that firmware, and noise control's modes (0 off, "
        "1 cancelling, 2 transparency) with each mode's scene codes (ANC_LEVEL under 1, TRANSPARENCY_LEVEL under 2). "
        "Untested unless the model has reports."
    )
    DEVICES.write_text(json.dumps(table, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"{written} models' features written")
    if unknown:
        print("Not in devices.json: " + ", ".join(unknown))


if __name__ == "__main__":
    main()
