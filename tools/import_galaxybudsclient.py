"""Refresh data/samsung/devices.json from a local checkout of GalaxyBudsClient.

usage: python tools/import_galaxybudsclient.py PATH_TO_GALAXYBUDSCLIENT

GalaxyBudsClient (https://github.com/timschneeb/GalaxyBudsClient, Tim Schneeberger, GPL-3.0)
supports every Samsung Galaxy Buds model. This reads its device specifications and writes, for
every model, the facts PairFree uses: the model number, its name, the Bluetooth name part
GalaxyBudsClient matches, the RFCOMM service, the framing, the ambient sound volume range, the
touch-and-hold options and the features the model has, each with the lowest state revision it
needs where GalaxyBudsClient gives one. Nothing of GalaxyBudsClient's code is copied into this
repo. What PairFree added itself (OURS) is kept.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEVICES = ROOT / "data" / "samsung" / "devices.json"
OURS = ("names", "reports", "notes", "kind")
SERVICES = {"SppLegacy": "00001102-0000-1000-8000-00805f9b34fd", "SppStandard": "00001101-0000-1000-8000-00805f9b34fb",
            "SppNew": "2e73a4ad-332d-41fc-90e2-16bef06523f2"}
TOUCH_OPTIONS = {"VoiceAssistant": "voice_assistant", "QuickAmbientSound": "quick_ambient_sound", "Volume": "volume",
                 "AmbientSound": "ambient_sound", "Anc": "anc", "NoiseControl": "noise_control",
                 "SpotifySpotOn": "spotify", "OtherL": "custom", "OtherR": "custom"}


def snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def models(src: Path) -> dict:
    """Model enum name -> (number, name, generation), from Models in Constants.cs. The generation is the enum's
    value, which GalaxyBudsClient compares to tell newer models from older ones."""
    text = (src / "Model/Constants.cs").read_text(encoding="utf-8")
    out = {}
    pattern = r'ModelMetadata\(Name = "([^"]+)", FwPattern = "[^"]*", BuildPrefix = "([^"]*)"\)\]\s*(\w+) = (\d+)'
    for name, fw, enum, value in re.findall(pattern, text):
        if enum != "NULL":
            out[enum] = ("SM-" + fw, name, int(value))
    return out


def colors(src: Path) -> dict:
    """Model enum name -> {device id: colour}, from DeviceIds in Constants.cs."""
    text = (src / "Model/Constants.cs").read_text(encoding="utf-8")
    out = {}
    for enum, colour, number in re.findall(r"\[AssociatedModel\(Models\.(\w+)\)\]\s*(\w+) = (\d+)", text):
        out.setdefault(enum, {})[number] = snake(colour.removeprefix(enum)) or "default"
    return out


def touch_maps(src: Path) -> dict:
    out = {}
    for f in (src / "Model/Specifications/Touch").glob("*TouchMap.cs"):
        pairs = re.findall(r"\{TouchOptions\.(\w+), (\d+)\}", f.read_text(encoding="utf-8"))
        if pairs:
            out[f.stem] = [[int(b), TOUCH_OPTIONS[o]] for o, b in pairs]
    return out


def spec(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")

    def prop(name):
        return re.search(rf"{name} => ([^;]+);", text).group(1).strip()

    features = {}
    for feature, rule in re.findall(r"\{ *Features\.(\w+), *(null|new FeatureRule\([^)]*\)) *\}", text):
        m = re.match(r"new FeatureRule\((\d+)", rule)
        features[snake(feature)] = int(m.group(1)) if m else None
    return {
        "device": prop("Device").removeprefix("Models."),
        "base_name": prop("DeviceBaseName").strip('"'),
        "service": SERVICES[prop("ServiceUuid").removeprefix("Uuids.")],
        "framing": "legacy" if "Legacy" in prop("StartOfMessage") else "standard",
        "max_ambient_volume": int(re.match(r"\d+", prop("MaximumAmbientVolume")).group(0)),
        "touch_map": prop("TouchMap").removeprefix("new ").removesuffix("()"),
        "features": features,
    }


def dump(data):
    """devices.json as written: indented, with each touch option pair and colour map on one line."""
    text = json.dumps(data, indent=2, ensure_ascii=False)
    text = re.sub(r'\[\s+(\d+),\s+("[^"]*")\s+\]', r"[\1, \2]", text)
    return text + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    checkout = Path(sys.argv[1])
    src = checkout / "GalaxyBudsClient"
    commit = subprocess.run(["git", "-C", str(checkout), "log", "-1", "--format=%H %cs"], capture_output=True, text=True,
                            check=True).stdout.split()
    old = {}
    if DEVICES.exists():
        old = {d["model"]: d for d in json.loads(DEVICES.read_text(encoding="utf-8"))["devices"]}
    all_models, all_colors, maps = models(src), colors(src), touch_maps(src)
    devices = []
    for path in sorted((src / "Model/Specifications").glob("*DeviceSpec.cs")):
        if path.stem in ("IDeviceSpec", "StubDeviceSpec"):
            continue
        s = spec(path)
        number, name, generation = all_models[s["device"]]
        entry = {
            "model": number,
            "names": old.get(number, {}).get("names") or [name],
            "galaxybudsclient": {
                "device": s["device"],
                "generation": generation,
                "base_name": s["base_name"],
                "service_uuid": s["service"],
                "framing": s["framing"],
                "max_ambient_volume": s["max_ambient_volume"],
                "touch_hold": maps[s["touch_map"]],
                "colors": all_colors.get(s["device"], {}),
                "features": s["features"],
            },
        }
        for key in OURS:
            if key in old.get(number, {}) and key != "names":
                entry[key] = old[number][key]
        devices.append(entry)
    devices.sort(key=lambda d: d["galaxybudsclient"]["generation"])
    data = {
        "_about": ("Samsung Galaxy Buds models PairFree knows. model is Samsung's model number (SM-R...), names the "
                   "names the earbuds use. galaxybudsclient holds what GalaxyBudsClient's device specification gives "
                   "(tools/import_galaxybudsclient.py): its name for the model and the generation it compares (newer "
                   "models have higher numbers), the part of the Bluetooth name it matches (base_name), the RFCOMM "
                   "service, the framing (legacy: start FE, end EE, a type and a one-byte length; standard: start FD, end "
                   "DD, a two-byte length), the highest ambient sound volume step, the touch-and-hold options as [byte, "
                   "option] (custom runs a phone-side action, set in Samsung's app), the colours by device id, and the "
                   "features, each with the lowest state revision that has it (null: every revision). The rest is "
                   "PairFree's: reports lists recordings in data/samsung/reports, notes what was found on the earbuds."),
        "source": {
            "project": "GalaxyBudsClient",
            "url": "https://github.com/timschneeb/GalaxyBudsClient",
            "author": "Tim Schneeberger",
            "license": "GPL-3.0",
            "commit": commit[0],
            "date": commit[1],
            "note": "Model numbers, names and specifications come from GalaxyBudsClient's source at this commit "
                    "(tools/import_galaxybudsclient.py). Its author worked them out from Samsung's own apps and the "
                    "earbuds' replies.",
        },
        "devices": devices,
    }
    DEVICES.parent.mkdir(parents=True, exist_ok=True)
    DEVICES.write_text(dump(data), encoding="utf-8", newline="\n")
    print(f"{len(devices)} models from GalaxyBudsClient {commit[0][:7]} ({commit[1]})")


if __name__ == "__main__":
    main()
