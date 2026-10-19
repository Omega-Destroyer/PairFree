"""Write data/1more/devices.json from a saved copy of 1MORE's product list.

The owner approved (2026-10-18) one fetch of https://newapp.1more.com/prod-api/system/headphone/list and keeping
these per-model facts: the Bluetooth name 1MORE's app matches on (headphoneName), the product model, the control
flags it reads (HeadsetConfigListApiData.HeadsetConfig.is*Able; a flag is on when it is 1) and the pictures' paths.
Left out: colours' ids, firmware and OTA data, server ids, the burn-in code, Sound ID, smart hearing, soothing
music, the pairing window and the test entry. Facts from 1MORE's app code, by name, are added (the sets below).

This tool only reads the saved reply; it never fetches anything. Fetch the list again only with the owner's OK, by
hand, into server_dump/ (which git ignores). Each model's reports are kept from the current devices.json.

usage: python tools/import_1more_list.py [server_dump/1more/headphone_list.json]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "1more" / "devices.json"
DUMP = ROOT / "server_dump" / "1more" / "headphone_list.json"

# The listen modes by 1MORE's value names (Const.kt), in the order its app lists them, and the flag for each.
CANCELLING = [("adaptive", "dynamicNoise"), ("strong", "deepnoiseReduction"), ("mild", "comfortableNoise"), ("wind", "antiwindNoise")]
TRANSPARENCY = [("transparent", "penetrability"), ("pass_through", "transparentEnvironment"), ("voice_enhancement", "vocalEnhancement")]
EQ = [("preset", "presetsoundEffects"), ("style", "stylesoundEffect"), ("custom", "customizesoundEffects")]
PICTURE_KEYS = [("home", "homeImg"), ("ear", "earImg"), ("list", "listImg"), ("left", "leftImg"), ("right", "rightImg"),
                ("dialog", "dialogImg"), ("dialog_gif", "dialogGif"), ("swatch", "earColorImg")]

# From 1MORE's app code (HeadsetActivity, ConstKt, CustomSettingsMonauralActivity), by Bluetooth name.
ONE_BATTERY = {"1MORE HQ20", "1MORE HQ31", "1MORE HQ36", "1MORE HQ51", "1MORE HQ60", "1MORE SonoFlow S",
               "1MORE SonoFlow QC30", "1MORE NANO", "1MORE G50"}
NO_CASE = {"1MORE S11", "1MORE S12"}
NO_ASSISTANT_TOUCH = {"1MORE S-31", "1MORE S51", "1MORE S70", "1MORE HQ20", "1MORE S25"}
EAR_RETURN = {"1MORE HQ20"}
# Tuya's AI earbuds (DataKt.aIDeviceList): 1MORE's app reaches them through Tuya's platform, not its serial protocol.
TUYA = {"1MORE S52", "1MORE S22Ultra"}


def on(row, field):
    return row.get(field) == 1


def listen_modes(r):
    if not on(r, "listeningMode"):
        return []
    modes = ["off"] if on(r, "close") else []
    if on(r, "noiseReduction"):
        children = [m for m, f in CANCELLING if on(r, f)]
        # Noise cancelling without a choice is mode 1, which 1MORE's app sends first (prevNoiseReduction).
        modes += children or ["strong"]
    modes += [m for m, f in TRANSPARENCY if on(r, f)]
    return modes


def pictures(r):
    try:
        items = json.loads(r.get("pictureList") or "[]")
    except ValueError:
        return []
    out = []
    for p in items:
        entry = {"colour": p.get("earColorName")}
        entry.update({k: p[f] for k, f in PICTURE_KEYS if p.get(f)})
        out.append(entry)
    return out


def device(r):
    name = r["headphoneName"].strip()
    connection = [m for m, f in (("game", "entertainmentMode"), ("ldac", "ldma")) if on(r, f)]
    touch = "each" if on(r, "monauralOperation") else "both" if on(r, "binauralOperation") else None
    return {
        "name": name,
        "model": r["headphoneModel"].strip(),
        "tuya": name in TUYA,
        "kind": "headphones" if name in ONE_BATTERY else "earbuds",
        "case": name not in ONE_BATTERY and name not in NO_CASE,
        "listen_modes": listen_modes(r),
        "connection": connection,
        "autoplay": on(r, "autoplay"),
        "spatial_audio": on(r, "spatialAudio"),
        "sleep_detection": on(r, "sleepDetection"),
        "dual_device": on(r, "dobleConnection"),
        "hot_word": on(r, "hotwordMonitoring"),
        "touch": touch,
        "touch_assistant": touch is not None and name not in NO_ASSISTANT_TOUCH,
        "eq": [k for k, f in EQ if on(r, f)],
        "ear_return": name in EAR_RETURN,
        "volume_limit": name in EAR_RETURN,
        "pictures": pictures(r),
        "reports": [],
    }


def main(dump_path, out_path=OUT):
    rows = json.load(open(dump_path, encoding="utf-8"))["rows"]
    devices = sorted((device(r) for r in rows if (r.get("headphoneName") or "").startswith("1MORE")), key=lambda d: d["name"].lower())
    if Path(out_path).exists():
        old = {d["name"]: d for d in json.loads(Path(out_path).read_text(encoding="utf-8"))["devices"]}
        for d in devices:
            d["reports"] = old.get(d["name"], {}).get("reports", [])
    data = {
        "_about": "1MORE's models (docs/1more/PROTOCOL.md section 5): one entry per Bluetooth name, as 1MORE's app matches them, with the "
                  "controls its product list gives each model. The list is 1MORE's own (system/headphone/list, which 1MORE's app 2.0.0 "
                  "downloads), fetched once by hand on 2026-10-18 with the owner's OK; PairFree never contacts 1MORE. listen_modes uses "
                  "data/1more/commands.json's names; tuya, kind, case, touch_assistant, ear_return and volume_limit come from 1MORE's app code; tuya marks "
                  "the two models 1MORE's app reaches through Tuya's platform instead, which PairFree does not control. "
                  "pictures are paths on https://newapp.1more.com/prod-api, per colour, kept for tracing device art.",
        "fetched": "2026-10-18",
        "devices": devices,
    }
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(dump(data))
    print(len(devices), "models")


def dump(data):
    """One line per field of each model, pictures one line each."""
    lines = ["{", f' "_about": {json.dumps(data["_about"], ensure_ascii=False)},', f' "fetched": {json.dumps(data["fetched"])},', ' "devices": [']
    for i, d in enumerate(data["devices"]):
        lines.append("  {")
        keys = list(d)
        for j, k in enumerate(keys):
            end = "," if j < len(keys) - 1 else ""
            if k == "pictures" and d[k]:
                lines.append('   "pictures": [')
                lines += [f"    {json.dumps(p, ensure_ascii=False)}" + ("," if n < len(d[k]) - 1 else "") for n, p in enumerate(d[k])]
                lines.append("   ]" + end)
            else:
                lines.append(f'   "{k}": {json.dumps(d[k], ensure_ascii=False)}{end}')
        lines.append("  }" + ("," if i < len(data["devices"]) - 1 else ""))
    lines += [" ]", "}"]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else DUMP)
