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

# The presets each model lists (TestKt.custSoundData), by data/1more/commands.json's preset names; 1MORE's app sends
# each as its byte (DefaultSoundActivity.getStyleCmd). The HQ60 lists SOUND BY LUCA (luca), sent as acoustic's byte; its
# THX preset depends on its firmware and is left out.
SIX = ["studio", "bass_reducer", "bass_booster", "loud", "classical", "pop"]
TWELVE = ["studio", "bass_reducer", "bass_booster", "acoustic", "classical", "podcast", "deep", "electronic", "hiphop", "lounge", "pop", "loud"]
PRESETS_SIX = {"1MORE S51", "1MORE S70", "1MORE HQ20", "1MORE S20", "1MORE S52", "1MORE S22", "1MORE S22Ultra", "1MORE S20NOVA",
               "1MORE S25", "1MORE S20PRO", "1MORE S20Ultra", "1MORE Q12", "1MORE S23", "1MORE AERO Q32", "1MORE S21", "1MORE G50",
               "1MORE Q50", "1MORE S11", "1MORE S12", "1MORE S10AI", "1MORE S10S", "1MORE Q21", "1MORE NANO", "1MORE HQ36"}
PRESETS = {"1MORE S-31": ["studio", "bass_reducer", "bass_booster", "loud"],
           "1MORE HQ60": ["studio", "luca", "bass_reducer", "bass_booster", "classical", "podcast", "deep", "electronic", "hiphop", "lounge", "pop", "loud"]}

# The custom EQ's bands (CustomSoundActivity.getEqBandConfig and its per-band Qs): frequencies (Hz), Qs (hundredths),
# the raw gain that is 0 dB (gainBValue plus 300), per-band offsets (the HQ36's), and whole_db for the models whose
# gains go in whole dB steps (useProtocolGain). Gains are from -3 to 3 dB.
DEFAULT_Q = [60, 70, 80, 90, 100, 140, 150, 200, 220, 200]
DEFAULT_BANDS = {"hz": [69, 282, 682, 1234, 2013, 3107, 4641, 6794, 9815, 14053], "q": DEFAULT_Q, "zero": 1200}
HQ31_BANDS = {"hz": [68, 288, 682, 1600, 2000, 3560, 4800, 7192, 9300, 13236], "q": DEFAULT_Q, "zero": 1500}
BANDS = {
    "1MORE Q12": {"hz": [95, 200, 300, 650, 1000, 2000, 3200, 4500, 5500, 7500, 9800, 12000],
                  "q": [80, 90, 80, 100, 100, 100, 160, 160, 160, 160, 160, 160], "zero": 1500, "whole_db": True},
    "1MORE SonoFlow QC30": {"hz": [68, 288, 682, 1600, 2000, 3560, 4800, 7400, 9308, 13236], "q": DEFAULT_Q, "zero": 1500},
    "1MORE HQ36": {"hz": [74, 200, 800, 1200, 2000, 3200, 4800, 6700, 11000, 13500], "q": [80, 200, 110, 70, 90, 80, 150, 100, 200, 200],
                   "zero": 1500, "offsets": [450, -800, 250, 0, 0, 300, 0, 0, 150, 0]},
    "1MORE HQ51": {"hz": [68, 282, 682, 1234, 2013, 3080, 4800, 6794, 9815, 13236], "q": DEFAULT_Q, "zero": 1500},
    "1MORE HQ60": {"hz": [76, 260, 680, 1200, 2200, 3200, 4800, 7300, 10000, 13000],
                   "q": [130, 80, 90, 100, 120, 220, 120, 160, 200, 200], "zero": 1500, "whole_db": True},
    "1MORE NANO": {"hz": [85, 105, 225, 315, 1680, 2500, 3900, 6800, 10000, 14000], "q": DEFAULT_Q, "zero": 1500},
    "1MORE HQ31": HQ31_BANDS,
    "1MORE SonoFlow S": HQ31_BANDS,
}


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
        "eq_presets": (SIX if name in PRESETS_SIX else PRESETS.get(name, TWELVE)) if on(r, "presetsoundEffects") else [],
        "eq_bands": BANDS.get(name, DEFAULT_BANDS) if on(r, "customizesoundEffects") else None,
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
                  "data/1more/commands.json's names; tuya, kind, case, touch_assistant, ear_return, volume_limit, eq_presets and eq_bands come from 1MORE's app code; tuya marks "
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
