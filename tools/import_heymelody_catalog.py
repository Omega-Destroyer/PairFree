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


# Touch control functions: the bit of a control's `support` mask (Ha.a.c names them) -> a key, and the number written in a key assignment
# (Ha.a.b, which the app derives from the same names). None where the app's own table gives no number.
FUNCTIONS = {
    1: ("voice_assistant", 3), 4: ("play_pause", 1), 8: ("volume_up", 11), 16: ("volume_down", 12), 32: ("previous", 5), 64: ("next", 6),
    128: ("noise_control", 8), 256: ("play_collection", 9), 512: ("none", 0), 1024: ("adjust_volume", 7), 2048: ("switch_song", 10),
    4096: ("switch_device", 13), 8192: ("game_mode", 17), 16384: ("enter_pairing", None), 32768: ("collect_music", 22), 65536: ("zen_mode", 18),
    131072: ("ai_summary", 25), 262144: ("reject_call", 28), 524288: ("answer_hang_up", 29), 1048576: ("ai_translation", 26),
    2097152: ("ai_translation", 26), 4194304: ("clear_pairing_history", None), 8388608: ("spy_tap", 32), 16777216: ("gmn_play_pause", 34),
    33554432: ("gmn_trigger", 35),
}
# A control's `action` -> the button action byte of a key assignment (the app's N.e, which passes other numbers through) and, for the stem
# actions 1 to 27, button byte 1; call actions 28 to 37 use button 6 and device type 4. Bytes 1 to 4 are single, double, triple and long press
# (the app's own UI order). Bytes 5 (slide up or down, with adjust volume) and 6 (a long hold that switches device, or a long press for volume)
# are named from the earbuds' manuals and reviews, not from the app (docs/oneplus/CATALOG_MAPPING.md); the others are kept as numbers.
ACTION_BYTES = {11: 4, 16: 1, 17: 2, 18: 3, 19: 4, 20: 4, 22: 6, 26: 2, 27: 4, 28: 1, 29: 2, 30: 3, 31: 6, 32: 1, 33: 2, 34: 3, 35: 6, 36: 2}
GESTURE_NAMES = {1: "single", 2: "double", 3: "triple", 4: "hold", 5: "slide", 6: "long_press"}


def gesture(action):
    byte = ACTION_BYTES.get(action, action)
    return GESTURE_NAMES.get(byte, f"gesture_{byte}"), byte


def functions(mask, model_name):
    old = model_name in ("OnePlus Buds", "OnePlus Buds Z")
    out = []
    for bit in sorted(FUNCTIONS):
        if mask & bit:
            key, wire = FUNCTIONS[bit]
            if old and key in ("previous", "next"):
                wire -= 1
            out.append({"key": key, "wire": wire})
    unknown = mask & ~sum(FUNCTIONS) & 0xFFFFFFFF
    if unknown:
        out.append({"key": f"mask_{unknown}", "wire": None})
    return out


def touch(f, model_name):
    out = []
    for c in f.get("control", []):
        key, byte = gesture(c["action"])
        e = {"gesture": key, "action": c["action"], "action_byte": byte, "button": c.get("button") or 1, "ear_choice": c.get("showEar", 0) == 1}
        if "support" in c:
            e["functions"] = functions(c["support"], model_name)
        out.append(e)
    for c in f.get("callControl", []):
        key, byte = gesture(c["action"])
        e = {"gesture": key, "action": c["action"], "action_byte": byte, "button": 6, "device_type": 4, "functions": functions(c.get("support", 0), model_name)}
        if "defaultFunction" in c:
            e["default"] = [x["key"] for x in functions(c["defaultFunction"], model_name)]
        out.append(e)
    return out


def noise(modes):
    out = []
    for m in modes or []:
        out.append({"index": m["protocolIndex"], "type": NOISE_TYPES[m["modeType"]],
                    "children": [{"index": c["protocolIndex"], "type": NOISE_TYPES[c["modeType"]]} for c in m.get("childrenMode", [])]})
    return out


# Catalog flag -> feature id of a plain switch, from HeyMelody's call sites of `setSwitchFeature` (`H0(address, id, on)`):
# smart play / wear detection 4, vocal enhance 9, hearing enhancement 11, multi-device connection 17, headset sound recording 19, high tone quality 24,
# automatic volume for the controls 28, bass engine 29, speech perception 50, long-press volume 53, swift pair 55, sleep detection 58, head motion 59.
SWITCH_FLAGS = {"wearDetection": 4, "vocalEnhance": 9, "hearingEnhancement": 11, "multiDevicesConnect": 17, "headSetSoundRecord": 19,
                "highToneQuality": 24, "controlAutoVolumeSupport": 28, "bassEngineSupport": 29, "speechPerception": 50, "longPressVolume": 53,
                "swiftPair": 55, "sleepDetection": 58, "headMotion": 59}


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
        if e["eq"]["custom"]:
            # The app's custom equalizer screen (p072eb.q): its own band list, 62 Hz to 16 kHz, unless the catalog lists others, and three slots.
            e["eq"]["frequencies"] = f.get("customEqFrequency") or [62, 250, 1000, 4000, 8000, 16000]
            e["eq"]["slots"] = f.get("customEqMax") or 3
    if f.get("control") or f.get("callControl"):
        e["touch"] = touch(f, r["name"])
    flags = {k: f[k] for k in ("gameMode", "wearDetection", "multiDevicesConnect", "bassEngineSupport", "highToneQuality", "hearingEnhancement", "supportPinch", "zenMode", "headMotion", "spatialTypes", "longPressType", "heyFindDevice", "findDevice") if k in f}
    if flags:
        e["features"] = flags
    # The plain on/off feature switches (`0403` with the feature id and 0 or 1, HeyMelody's `setSwitchFeature`): the ids whose catalog flag is set.
    switches = sorted(i for flag, i in SWITCH_FLAGS.items() if f.get(flag))
    if switches:
        e["switches"] = switches
    if f.get("promptVolume"):
        # The prompt volume bar (PromptVolumeItem): the catalog's range, else 1 to 10 as the bar's own defaults.
        rng = f.get("promptVolumeRange") or {}
        e["prompt_volume"] = {"min": rng.get("min") if rng.get("min", -1) != -1 else 1, "max": rng.get("max") if rng.get("max", -1) != -1 else 10}
    if f.get("tapLevelSetting"):
        e["tap_level"] = True
    if f.get("fitDetection"):
        e["fit_test"] = True
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
