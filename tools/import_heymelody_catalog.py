"""Turn HeyMelody's bundled model catalog into data/oneplus/devices.json: the facts PairFree uses, not the vendor's file.

usage: python tools/import_heymelody_catalog.py CATALOG.json BASE.apk [--check]

CATALOG.json is HeyMelody 117.6.6's bundled whitelist (res/raw/heymelody_app_whitelist.json), authenticated and decoded locally
outside the repo (docs/oneplus/PROTOCOL.md section 6); BASE.apk is the app's base APK, whose default strings aapt2 reads for the
mode names. Neither is committed. What is kept per model: the product id, the names, the noise modes and sub-modes with their
protocol indices, the equalizer presets with their protocol indices and English names, and a few feature flags. The mode and preset
names come from the app's own tables (Ha.a.e for noise modes, p072eb.w.d for equalizer presets), transcribed below.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "oneplus" / "devices.json"

# Noise mode types (the app's Ha.a.e, Db.z for the strengths, Db.d for the adaptive range) -> a key PairFree's screens know.
NOISE_TYPES = {
    1: "off", 2: "transparency", 3: "light", 4: "strong", 5: "noise_cancelling", 6: "voice_focus", 7: "smart", 8: "moderate",
    10: "auto", 11: "less_noise", 12: "standard_noise", 13: "more_noise",
}

# Equalizer mode types (p072eb.w.d) -> resource name in the English split; types 1 to 4 have two names by model family.
EQ_RES = {
    5: "default", 35: "default", 6: "dynaudio_simple_and_leisurely", 36: "dynaudio_simple_and_leisurely",
    7: "dynaudio_warm_and_gentle", 37: "dynaudio_warm_and_gentle", 8: "dynaudio_surging_and_hearty", 38: "dynaudio_surging_and_hearty",
    9: "dynaudio_original", 39: "dynaudio_original", 10: "jiu_shi_rang_new", 11: "balanced", 12: "bass_plus", 13: "treble_plus",
    14: "pure_voice", 15: "soft_voice", 16: "enco_x_classic", 17: "balance", 18: "reno_dawn", 19: "hans",
    20: "natural_inspiration", 21: "reno_sun", 22: "original", 23: "surging_and_hearty", 24: "melodious_open", 25: "reno_galaxy",
    26: "ultimate_sound", 27: "hd_analyze", 28: "pure_vocals", 29: "powerful_bass", 30: "dynaudio_special", 31: "pulse_bass",
    32: "melodious_voice", 33: "galaxy_monologue", 34: "vitality", 40: "dynaudio_vocals", 41: "crisp_transparent",
    42: "dynaudio_original_sound", 43: "dynaudio_hd_analyze", 44: "dynaudio_bass", 45: "dynaudio_classic", 46: "clear_treble",
}
EQ_OLD = {1: "original", 2: "pulse_bass", 3: "melodious_voice"}
EQ_NEW = {1: "classic", 2: "sub_woofer", 3: "clear_voice", 4: "bright_transparent"}


def english(apk):
    """resource name -> English text, from aapt2's dump of the base APK."""
    import os
    sdk = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "build-tools"
    aapt = sorted(sdk.glob("*/aapt2.exe"))[-1]
    out = subprocess.run([str(aapt), "dump", "resources", str(apk)], capture_output=True, text=True, encoding="utf-8").stdout
    strings, name = {}, None
    for line in out.splitlines():
        m = re.search(r"string/(melody_ui_equalizer_\w+|melody_ui_enco_x_classic)$", line.strip())
        if m:
            name = m.group(1)
            continue
        m = re.match(r'\s+\(\) "(.*)"$', line)
        if m and name:
            strings[name] = m.group(1)
            name = None
    return strings


def eq_name(strings, mode_type, family, model_name):
    old = model_name in ("OPPO Enco R", "OPPO Enco Air2") or family == 2
    if mode_type in (1, 2, 3):
        key = (EQ_OLD if old else EQ_NEW)[mode_type]
    elif mode_type == 4:
        key = "soft_voice" if model_name == "OPPO Enco R" else "bright_transparent"
    else:
        key = EQ_RES[mode_type]
    res = "melody_ui_enco_x_classic" if key == "enco_x_classic" else "melody_ui_equalizer_" + key
    return strings.get(res, key)


def noise(modes):
    out = []
    for m in modes or []:
        out.append({"index": m["protocolIndex"], "type": NOISE_TYPES[m["modeType"]],
                    "children": [{"index": c["protocolIndex"], "type": NOISE_TYPES[c["modeType"]]} for c in m.get("childrenMode", [])]})
    return out


def entry(r, strings):
    f = r["function"]
    names = [r["name"]] + [c["name"] for c in r.get("children", [])]
    family = f.get("equalizer")
    e = {"id": r["id"], "name": r["name"], "names": names, "brand": r["brand"], "hidden": bool(f.get("hideFromUIList"))}
    if f.get("noiseReductionMode"):
        e["noise"] = noise(f["noiseReductionMode"])
    if f.get("equalizerMode"):
        e["eq"] = {"family": family, "custom": bool(f.get("customEqualizer")),
                   "presets": [{"index": m["protocolIndex"], "name": eq_name(strings, m["modeType"], family, r["name"])} for m in f["equalizerMode"]]}
        if f.get("customEqFrequency"):
            e["eq"]["frequencies"] = f["customEqFrequency"]
    flags = {k: f[k] for k in ("gameMode", "wearDetection", "multiDevicesConnect", "bassEngineSupport", "highToneQuality", "hearingEnhancement", "supportPinch", "zenMode", "headMotion", "spatialTypes") if k in f}
    if flags:
        e["features"] = flags
    return e


def main():
    catalog = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["compatWhiteList"]
    strings = english(sys.argv[2])
    devices = sorted((entry(r, strings) for r in catalog), key=lambda d: d["id"])
    data = {
        "_about": "Models of OnePlus and OPPO earbuds from HeyMelody 117.6.6's bundled catalog (docs/oneplus/PROTOCOL.md): per model the names, the noise modes "
                  "and sub-modes with their protocol indices, the equalizer presets with their protocol indices and English names, and feature flags. "
                  "Facts only, written by tools/import_heymelody_catalog.py; the catalog itself stays outside the repo. Source analysis and a virtual run "
                  "of the app (Buds Pro 2) only: no model here has been tried with PairFree.",
        "devices": devices,
    }
    text = json.dumps(data, indent=1, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        print("up to date" if OUT.read_text(encoding="utf-8") == text else "DIFFERENT")
        return
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(len(devices), "models;", sum(1 for d in devices if "noise" in d), "with noise control;", sum(1 for d in devices if "eq" in d), "with equalizer presets")


if __name__ == "__main__":
    main()
