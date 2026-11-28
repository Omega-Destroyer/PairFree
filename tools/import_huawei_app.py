"""Bring HUAWEI Audio Connect's own model data into data/huawei/devices.json.

    python tools/import_huawei_app.py PATH_TO_HUAWEI_Audio_Connect.apk

The app keeps, per product id, which cards its screens show (assets/config), the values each touch gesture takes (assets/touchsetting) and its
equalizer sounds (assets/config, assets/eqextend). Where it describes a model, it replaces what PairFree took from public projects; what it says
nothing about (the prompt language, which models keep curves of the user's own, ...) stays as it is. The APK is not committed.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEVICES = ROOT / "data/huawei/devices.json"

# The app's product ids by PairFree's model names. Several ids are one model on sale (two firmware or colour lines).
MODELS = {
    "FreeBuds 3": ["004O"], "FreeBuds 3i": ["ZA02"], "FreeLace Pro": ["ZA04"], "FreeBuds Studio": ["ZA06"], "FreeBuds Pro": ["ZA07"],
    "FreeBuds 4i": ["ZA08"], "HONOR Earbuds 2 Lite": ["ZB05"], "FreeBuds 4": ["ZA09"], "FreeBuds Lipstick": ["ZAA0"], "FreeBuds SE": ["ZAA1"],
    "FreeLace Lite": ["ZAA2"], "FreeBuds 4E": ["ZAA4"], "FreeBuds Pro 2+": ["ZAA6"], "FreeBuds Pro 2": ["ZAA7"], "FreeBuds 5i": ["ZAA9", "ZAAD"],
    "W Buds": ["ZAAA"], "FreeBuds 5": ["ZAAB"], "FreeBuds SE 2": ["ZAAF"], "FreeBuds Pro 3": ["ZAAG"], "FreeLace Pro 2": ["ZAAH"],
    "FreeClip": ["ZAAI"], "FreeBuds Lipstick 2": ["ZAAJ"], "FreeBuds 6i": ["ZAAK"], "FreeBuds 6": ["ZAAL", "ZAAQ"], "FreeBuds Pro 4": ["ZAAM"],
    "FreeBuds 4E (open fit)": ["ZAAN"], "FreeBuds SE 3": ["ZAAO"], "FreeArc": ["ZAAP"], "FreeClip 2": ["ZAAS"], "FreeBuds SE 4 ANC": ["ZAAT"],
    "FreeBuds 7i": ["ZAAU"], "FreeBuds Pro 5": ["ZAAV", "ZAAW"], "W Buds 2": ["ZAAX"], "FreeBuds 7": ["ZAB1"],
    "FreeClip 2 (Collector's Edition)": ["ZAB2"], "FreeBuds Neo": ["ZAB4"], "HUAWEI Eyewear (2022)": ["ZCA0"], "HUAWEI Eyewear 2": ["ZCA1"],
    "FreeBuds SE 5 MAX": ["2SFW"], "FreeBuds A1": ["2SO9"],
}

# The eight products AI Life's plugin gives: their layouts differ (docs/huawei/PROTOCOL.md section 3), so their entries are left alone.
PLUGIN = {"004O", "ZA02", "ZA04", "ZA06", "ZA07", "ZB02", "ZB04", "ZC01", "ZC02"}

NEW = {
    "HONOR Earbuds 2 Lite": dict(id="ZB05", form="EARBUDS", aliases=["HONOR Earbuds 2 Lite"]),
    "HUAWEI Eyewear (2022)": dict(id="ZCA0", form="EARBUDS", aliases=[]),
    "FreeBuds A1": dict(id="2SO9", form="EARBUDS", aliases=[]),
}

# The features the app's files speak for. A model with a config file has exactly the ones its file gives.
SPOKEN = {
    "anc", "two_modes", "anc_levels", "dynamic", "voice", "adaptive_awareness", "wear", "latency", "quality", "hd_call", "pickup", "spatial",
    "head", "voice_control", "voice_wake", "drop", "single_anc", "case_sound", "smart_charge", "noisy_volume", "find", "rest", "ear_tip", "fit",
    "wind_noise", "wind", "multipoint", "auto_lr", "double", "double_call", "triple", "hold", "hold_right", "hold_call", "hold_reject",
    "hold_extra", "hold_assistant", "hold_toggle", "hold_cycle", "swipe", "swipe_sides", "pinch", "pinch_hold", "conversation", "adaptive_volume",
    "limited_levels", "report_only", "soh", "light", "pair_new",
}

GESTURES = {"double": ("011F", 1), "double_call": ("011F", 4), "triple": ("0125", 1), "hold": ("2B16", 1), "hold_call": ("2B16", 4),
            "swipe": ("2B1E", 1)}

PRESETS = {
    0: "Default", 1: "Default", 2: "Bass boost", 3: "Treble boost", 5: "Default", 9: "Voices", 10: "Lively", 11: "Balanced", 12: "Classical",
    13: "Movie", 14: "Gaming", 15: "Podcast", 16: "Punchy", 17: "Adaptive", 18: "Natural", 19: "Concert hall", 20: "HUAWEI SOUND Balanced",
    21: "HUAWEI SOUND Voice", 22: "HUAWEI SOUND Bass", 23: "HUAWEI SOUND Classical", 25: "Shooter games",
}
CURVES = {200: "Symphony", 201: "Hi-Fi Live", 202: "Sport"}


def load(archive, name):
    if name not in archive.namelist():
        return None
    text = archive.read(name).decode("utf-8-sig")
    try:
        return json.loads(text)
    except ValueError:
        return json.loads(re.sub(r",(\s*[}\]])", r"\1", re.sub(r"(?m)^\s*//.*$", "", text)))


def find(node, key):
    """Every value stored under [key] anywhere in [node]."""
    if isinstance(node, dict):
        for name, value in node.items():
            if name == key:
                yield value
            yield from find(value, key)
    elif isinstance(node, list):
        for value in node:
            yield from find(value, key)


def touches(node):
    """(command, tag) -> the values the app can write there, and the commands it writes with two or more tags together."""
    values, together = {}, set()
    for data in find(node, "sendData"):
        if not (isinstance(data, list) and len(data) > 4 and all(isinstance(x, int) for x in data)):
            continue
        raw = [x & 255 for x in data]
        command = "%02X%02X" % (raw[0], raw[1])
        fields, index = [], 2
        while index + 1 < len(raw):
            tag, size = raw[index], raw[index + 1]
            fields.append((tag, raw[index + 2:index + 2 + size]))
            index += 2 + size
        if command == "2B1E" and [t for t, _ in fields] == [1, 2]:
            together.add(command)
        if command == "2B92":
            if fields and fields[0][1] == [3]:
                together.add("2B92:hold")
            together.add(command)
            continue
        for tag, value in fields:
            if len(value) == 1:
                values.setdefault((command, tag), [])
                if value[0] not in values[(command, tag)]:
                    values[(command, tag)].append(value[0])
        if command == "2B18":
            together.add(command)
    return values, together


def describe(archive, pid):
    """The features, gestures and sounds of one product id, or None when the app has neither a screen nor a touch file for it."""
    config = load(archive, f"assets/config/config_{pid}.json")
    touch = load(archive, f"assets/touchsetting/config_{pid.lower()}.json") or load(archive, f"assets/touchsetting/config_{pid}.json")
    if touch is not None and not touches(touch)[0]:
        touch = None
    if config is None and touch is None:
        return None
    if config is None:
        # No screen file: the product list still says how many noise modes the model has.
        listed = next((d for d in load(archive, "assets/device_information.json") if d.get("productId") == pid), {})
        modes = listed.get("noiseCtrlModesNum", -1)
        config = {"noiseControl": {"isSupportNoiseControl": modes >= 2, "isSupportSimpleNoiseControl": modes == 2}}
    tags = {t for t in find(config, "tag") if isinstance(t, str)}
    noise = config.get("noiseControl", {})
    quality = config.get("soundQuality", {})
    more = config.get("moreSetting", {})
    features = set()

    def when(condition, *names):
        if condition:
            features.update(names)

    anc = "noisecontrol" in tags or noise.get("isSupportNoiseControl") or noise.get("isSupportSimpleNoiseControl")
    when(anc, "anc")
    when(anc and noise.get("isSupportSimpleNoiseControl"), "two_modes")
    when(noise.get("isSupportAncLevel"), "anc_levels")
    when(noise.get("isSupportAncLevel") and noise.get("isSupportSimpleNoiseControl"), "limited_levels")
    when(noise.get("isSupportAiLevel") or noise.get("isSupportDynamicAiLevel"), "dynamic")
    when(noise.get("isSupportSubHumanVoice"), "voice")
    when(noise.get("isSupportSmartAwareness"), "adaptive_awareness")
    when(noise.get("isSupportWindNoise"), "wind_noise")
    when("wear_detection" in tags or "wearDetection" in more, "wear")
    when(quality.get("isSupportGameLow"), "latency")
    when(quality.get("isSupportCode") or "qualityMode" in more, "quality")
    when(quality.get("isSupportHdCall"), "hd_call")
    when(quality.get("isSupportPickup"), "pickup")
    when("spatialAudio" in tags or config.get("spatialAudio", {}).get("isSupportSpatialAudio"), "spatial")
    when("headcontrol" in tags, "head")
    when("voice_control" in tags, "voice_control")
    when("voice_wakeup" in tags, "voice_wake")
    when("drop_detection" in tags, "drop")
    when("alone_noise" in tags, "single_anc")
    when("charging_tip" in tags, "case_sound")
    when("smart_charge" in tags, "smart_charge")
    when("big_volume_new" in tags, "noisy_volume")
    when("earpuds_search" in tags or config.get("findHeadset"), "find")
    when(config.get("isSupportHeadsetHealthAlert"), "rest")
    when("earbudhearSwitch" in tags, "ear_tip")
    when("earplugdetection" in tags or "tipFitTest" in more and config.get("version", 1) >= 2 or config.get("isSupportEarplugLeakDetection"), "fit")
    when(config.get("menu", {}).get("dualConnect"), "multipoint")
    when(config.get("isLeftRightEarSupport"), "auto_lr")
    when("adaptiveaudio" in tags, "adaptive_volume", "conversation")
    when(config.get("isSupportSoh"), "soh")
    when("light_setting" in tags, "light")
    when("pare_new_device" in tags, "pair_new")

    gestures = {}
    if touch is not None:
        values, together = touches(touch)
        for name, key in GESTURES.items():
            offered = values.get(key, [])
            if any(v != 255 for v in offered):
                gestures[name] = offered
        if "double" in gestures:
            features.add("double")
        if 0 in gestures.get("double_call", []):
            features.add("double_call")
        gestures.pop("double_call", None)
        if "triple" in gestures:
            features.add("triple")
        if "hold" in gestures:
            features.add("hold")
            if any(v != 255 for v in values.get(("2B16", 2), [])):
                features.add("hold_right")
        if 0 in gestures.get("hold_call", []):
            features.add("hold_reject")
        gestures.pop("hold_call", None)
        if "2B18" in together:
            features.add("hold_cycle")
        if "swipe" in gestures:
            features.add("swipe" if "2B1E" in together else "swipe_sides")
        if "2B92" in together:
            features.add("pinch")
        if "2B92:hold" in together:
            features.add("pinch_hold")

    sounds = []
    for group in config.get("eqModes", {}).get("eqGroupList", []):
        sounds += [s for s in group.get("supportEq", []) if s not in sounds]
    extended = load(archive, f"assets/eqextend/config_{pid.lower()}.json")
    curves = {e["eqType"]: [int(v) for v in e["eqNum"].split(",")] for e in extended["extended"]} if extended else {}
    return features, gestures, sounds, curves


def merge(product, found):
    features = set()
    gestures, sounds, curves = {}, [], {}
    for one in found:
        features |= one[0]
        for name, values in one[1].items():
            gestures.setdefault(name, [])
            gestures[name] += [v for v in values if v not in gestures[name]]
        sounds += [s for s in one[2] if s not in sounds]
        curves.update(one[3])
    kept = [f for f in product.get("features", []) if f not in SPOKEN]
    order = [f for f in product.get("features", []) if f in features]
    product["features"] = kept + order + sorted(features - set(order))
    if gestures:
        product["gestures"] = gestures
    presets = {str(s): PRESETS[s] for s in sounds if s in PRESETS}
    if presets:
        product["presets"] = presets
    if curves:
        product["curves"] = {CURVES[i]: gains for i, gains in sorted(curves.items())}
        product["curve_ids"] = {CURVES[i]: i for i in sorted(curves)}
    elif sounds:
        product.pop("curves", None)
        product.pop("curve_ids", None)


def main(path):
    data = json.loads(DEVICES.read_text(encoding="utf-8"))
    products = {p["name"]: p for p in data["products"]}
    with zipfile.ZipFile(path) as archive:
        for name, ids in MODELS.items():
            if ids[0] in PLUGIN:
                continue
            product = products.get(name)
            if product is None:
                product = dict(name=name, source="audioconnect", features=["modern_battery"], **NEW[name])
                data["products"].append(product)
                products[name] = product
            product["app_ids"] = ids
            found = [d for d in (describe(archive, pid) for pid in ids) if d is not None]
            if not found:
                continue
            before = set(product["features"])
            merge(product, found)
            if "audioconnect" not in product["source"]:
                product["source"] += "+audioconnect"
            after = set(product["features"])
            if before != after:
                print(f"{name}: +{sorted(after - before)} -{sorted(before - after)}")
    DEVICES.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(data["products"]), "products")


if __name__ == "__main__":
    main(sys.argv[1])
