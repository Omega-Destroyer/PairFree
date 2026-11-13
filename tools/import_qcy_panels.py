"""Add QCY models that data/qcy/devices.json lacks, from the saved copy of QCY's own product list and control panels.

usage: python tools/import_qcy_panels.py [--check] [--write]

data/qcy/devices.json was built from Quicky's dump (hui1601/Quicky, no licence: facts only). QCY has added models since: the
owner's research fetch of 2026-11-04 (E:\\Builds\\research\\qcy-tools\\fetch_panels.py, saved under server_dump/qcy/, which git
ignores) lists 31 earphones and headsets the Quicky list lacks, among them the newer QCY T13 Pro (vendor id 32887, GitHub issue
#28). This script turns each panel into the same entry shape (the rules are those of the Quicky importer) and adds only the
models that are missing; existing entries are never touched.

--check  compare what this script makes of the models both lists hold with data/qcy/devices.json, and report the differences
--write  add the missing models

Derived facts only: no picture links, no ids of QCY's server records. Raw replies stay in server_dump/.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "server_dump" / "qcy"
DEVICES = ROOT / "data" / "qcy" / "devices.json"

FAMILIES = {0x10: "qualcomm", 0x40: "wuqi", 0x70: "jieli", 0x90: "zr", 0xA0: "rd", 0xC0: "realtek", 0xD0: "bes"}


def family(v):
    return FAMILIES.get(v & 0xF0, f"0x{v & 0xF0:02X}")


def protocol(v):
    if (v & 0xF0) == 0x70 or v == 512:
        # QCY's newer JieLi models take QCY's own items inside JieLi's custom command (Devicebind.isNewJLDevice).
        return "jieli_custom" if (v & 0x80F0) == 0x8070 else "jieli"
    return "qcy"


def kind(v):
    # Devicebind.isNotTWS: 0x100 not true wireless (headsets, neckbands), 0x300 speakers.
    return "headphones" if (v & 0xF00) == 0x100 else "earbuds"


def scene(name):
    n = name.lower().replace("\n", " ")
    for key, words in (("adaptive", "adaptive"), ("indoor", "indoor"), ("commuting", "commut"), ("noisy", "noisy"), ("anti_wind", "wind")):
        if words in n:
            return key
    raise ValueError(name)


def mode(name):
    n = name.lower()
    if "transparen" in n:
        return "transparency"
    if n == "normal":
        return "off"
    if "cancel" in n or "anc" in n:
        return "cancelling"
    raise ValueError(name)


def packed(v):
    return f"{v:06x}" if v > 0xFF else f"{v:02x}"


def anc(layout):
    modes = []
    for m in layout["modes"]:
        entry = {"mode": mode(m["name"]), "from": packed(m["startcmdid"]), "to": packed(m["endcmdid"]), "default": packed(m["defaultcmd"])}
        if m.get("items"):
            entry["scenes"] = [{"scene": scene(i["name"]), "from": packed(i["startcmdid"]), "to": packed(i["endcmdid"])} for i in m["items"]]
        modes.append(entry)
    return modes


ACTIONS = {
    "not work": "none", "play/pause": "play_pause", "playback/pause": "play_pause",
    "previous track": "previous", "skip track backward": "previous", "next track": "next", "skip track forward": "next",
    "voice assistant": "voice_assistant", "volume up": "volume_up", "volume down": "volume_down",
    "gaming mode": "game_mode", "low-latency": "game_mode", "low latency mode": "game_mode",
    "anc mode": "noise_control", "anc": "noise_control", "anc switching": "noise_control",
    "volume up+": "volume_up", "volume up-": "volume_down", "take pictures": "take_picture",
    "n/a": "none", "no function": "none", "nan": "none",
}
STANDARD = {"none": 0, "play_pause": 1, "previous": 2, "next": 3, "voice_assistant": 4, "volume_up": 5, "volume_down": 6, "game_mode": 7}  # data/qcy/commands.json keys.actions

GESTURES = {"touch": "single", "double touch": "double", "triple touch": "triple", "long press": "hold"}
CLICKS = (("single_click", "touch"), ("double_click", "double touch"), ("three_click", "triple touch"), ("long_press", "long press"))
NEVER = {"1", "3", "100", "type_4"}  # firmware update, resets, pairing records
SETTINGS = {6: "in_ear", 9: "game_mode", 16: "sleep_mode", 18: "led", 35: "ldac", 36: "multipoint", 39: "dynamic_eq", 42: "wind_detection", 45: "spatial_audio", 64: "adaptive_volume"}


def freq(text):
    t = text.strip().lower()
    return round(float(t[:-1]) * 1000) if t.endswith("k") else int(float(t))


def preset_gains(layout, eq):
    """The presets of a graphic EQ panel as curves: name and a gain in dB per band, for the presets that sit on the model's own bands (no frequency list of
    their own). The others are parametric (their own frequencies, Q and filter types, written as a parametric EQ) and are left out."""
    out = []
    for p in layout.get("sys_eq", []):
        gains = [x.strip() for x in str(p.get("eqs", "")).split(",") if x.strip() != ""]
        own = p.get("freq")
        if len(gains) != eq["bands"] or (own and [freq(x) for x in own.split(",")] != eq["freqs"]):
            continue
        if any(o["name"] == p["name"] for o in out):
            continue  # a second preset under the same name (two "Bass" on the H2 Pro): the first is kept
        try:
            out.append({"name": p["name"], "gains": [float(g) for g in gains]})
        except ValueError:
            continue
    return out


CHOICES = {46: "music_mode", 51: "touch_sensitivity"}  # panel rows of type 1000 or 111 that pick one of a few values (cmdid: PairFree's name)


def choices(panel):
    """The settings a panel row picks from a list: its item (the cmdid in hex), PairFree's name, and each option's value byte and the panel's name for it."""
    out = []
    for l in (panel.get("data") or {}).get("controlPanel", {}).get("layouts", []):
        cmd = l.get("cmdid")
        try:
            cmd = int(cmd)
        except (TypeError, ValueError):
            continue
        if cmd not in CHOICES or str(l.get("type")) not in ("1000", "111"):
            continue
        options = []
        for s_ in l.get("subItem", []):
            key = s_.get("key") if s_.get("key") is not None else ("%02X" % s_["index"] if "index" in s_ else None)
            if key is not None and s_.get("name"):
                options.append({"key": key, "name": s_["name"]})
        if not options:
            options = [{"key": k, "name": n} for k, n in (l.get("itemName") or {}).items()]
        if options and CHOICES[cmd] not in [x["setting"] for x in out]:
            out.append({"item": "%02X" % cmd, "setting": CHOICES[cmd], "options": options})
    return out


def type1_touch(l):
    """The older button screen (type 1): left and right, each gesture with the actions and their bytes. The panel's own byte for an
    action that is not in QCY's standard table (ANC mode is 0A on some models and 0B on others) goes in extra_actions."""
    gestures, actions, extra = [], [], {}
    for key, gesture in CLICKS:
        g = (l.get("left") or {}).get(key)
        if not isinstance(g, dict) or not g.get("list"):
            continue
        gestures.append(GESTURES[gesture])
        for f in g["list"]:
            n = f["name"].strip().lower()
            cmd = int(f["cmd"], 16)
            name = {5: "volume_up", 6: "volume_down"}[cmd] if n == "volume" and cmd in (5, 6) else ACTIONS.get(n)
            if name is None:
                continue
            if name not in actions:
                actions.append(name)
            if STANDARD.get(name) != cmd:
                extra[str(cmd)] = name
    if not gestures:
        return None
    order = list(dict.fromkeys(ACTIONS.values()))
    out = {"gestures": gestures, "actions": sorted(actions, key=lambda a: order.index(a) if a in order else 99)}
    if extra:
        out["extra_actions"] = dict(sorted(extra.items(), key=lambda kv: int(kv[0])))
    return out


def jieli_noise(l):
    """The older noise screen (type 11): JieLi's own noise mode, one number per mode (ancindex 1 on, 2 off, 3 transparency), packed
    as mode << 16 so the shared noise control can use it."""
    names = {"anc on": "cancelling", "anc off": "off", "transparency": "transparency"}
    modes = []
    for m in l["modes"]:
        mode_ = names.get(m["name"].strip().lower())
        if mode_ is None:
            return None
        v = packed(m["ancindex"] << 16)
        modes.append({"mode": mode_, "from": v, "to": v, "default": v})
    return modes


def features(panel):
    out = {}
    layouts = (panel.get("data") or {}).get("controlPanel", {}).get("layouts", [])
    for l in layouts:
        t = l.get("type")
        if t == 9 and l.get("modes"):
            out["anc"] = anc(l)
        elif t == 2 and l.get("freq"):
            out["eq"] = {"bands": l["count"], "max_db": l["maxdb"], "freqs": [freq(x) for x in l["freq"].split(",")],
                         "presets": [p["name"] for p in l.get("sys_eq", [])]}
            gains = preset_gains(l, out["eq"])
            if gains:
                out["eq"]["preset_gains"] = gains
        elif t in (10, 14) and (l.get("music") or {}).get("event"):
            # The button screen of the newer panels (the older type 1 screen is not read, as in the Quicky list).
            events = [(e["name"], e["left"]["list"]) for e in l["music"]["event"]]
            gestures = [GESTURES[n.lower()] for n, _ in events if n.lower() in GESTURES]
            actions = {ACTIONS[f["name"].lower()] for _, lst in events for f in lst if f["name"].lower() in ACTIONS}
            out["touch"] = {"gestures": gestures, "actions": sorted(actions, key=list(dict.fromkeys(ACTIONS.values())).index)}
        elif t == 8:
            out["balance"] = True
        elif t == 102:
            out["sleep_timer"] = True
        elif t == 4:
            seen = []
            for s_ in l.get("items", []):
                cmd = s_.get("cmdid")
                typ = str(s_.get("type"))
                if typ in NEVER:
                    continue
                setting = SETTINGS.get(cmd)
                item = f"{cmd:02X}" if setting else None
                if setting is None and (typ.startswith("201") or (cmd is None and "in-ear" in str(s_.get("title", "")).lower())):
                    # QCY's in-ear row without an item: wearing detection (2C) on QCY's protocol, JieLi's own on JieLi models.
                    setting, item = "in_ear", "2C"
                if setting and setting not in [x["setting"] for x in seen]:
                    seen.append({"item": item, "setting": setting})
            if seen:
                out["settings"] = seen
    if "touch" not in out:
        for l in layouts:
            if l.get("type") == 1:
                t1 = type1_touch(l)
                if t1:
                    out["touch"] = t1
    if "anc" not in out:
        for l in layouts:
            if l.get("type") == 11 and l.get("modes"):
                modes = jieli_noise(l)
                if modes:
                    out["anc"] = modes
                    out["jieli_noise"] = True
    picks = choices(panel)
    if picks:
        out["settings"] = out.get("settings", []) + picks
    return out


def name(title):
    t = re.sub(r"[^\x20-\x7e]", " ", title).strip()
    t = re.sub(r"\s+", " ", t)
    return t if t.upper().startswith("QCY") else "QCY " + t


def entry(product, panel):
    v = product["vendorID"]
    names = []
    for n in (product.get("subTitle") or "", product["title"], name(product["title"])):
        n = re.sub(r"[^\x20-\x7e]", " ", n).strip()
        if n and n.lower() not in [x.lower() for x in names]:
            names.append(n)
    d = {"vendor_id": v, "name": name(product["title"]), "names": names, "family": family(v), "protocol": protocol(v), "kind": kind(v)}
    f = features(panel)
    for key in ("anc", "jieli_noise", "eq", "touch", "balance", "sleep_timer", "settings"):
        if key in f:
            d[key] = f[key]
    return d


def products():
    listing = json.loads((DUMP / "product_list.json").read_text(encoding="utf-8"))
    out = {}
    for p in listing["earphones"] if isinstance(listing["earphones"], list) else listing["earphones"].values():
        out[p["vendorID"]] = p
    return out


def panel(v):
    path = DUMP / "panels" / f"{v}.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def main():
    data = json.loads(DEVICES.read_text(encoding="utf-8"))
    have = {d["vendor_id"]: d for d in data["devices"]}
    prods = products()
    if "--check" in sys.argv:
        same = diff = 0
        for v, d in have.items():
            if v in prods and panel(v):
                made = entry(prods[v], panel(v))
                if made == d:
                    same += 1
                else:
                    diff += 1
                    keys = [k for k in set(made) | set(d) if made.get(k) != d.get(k)]
                    print(v, d["name"], "differs in", sorted(keys))
        print(f"{same} equal, {diff} different")
    missing = [prods[v] for v in sorted(prods) if v not in have and panel(v)]
    print(len(missing), "models missing")
    new = [entry(p, panel(p["vendorID"])) for p in missing]
    for d in new:
        print("  ", d["vendor_id"], d["name"], d["protocol"], sorted(k for k in d if k not in ("vendor_id", "name", "names", "family", "protocol", "kind")))
    if "--choices" in sys.argv:
        # Adds the list-picking settings (music mode, touch sensitivity) to the models whose panel has them, leaving every other field as it is.
        added = 0
        for v, d in have.items():
            if v in prods and panel(v):
                have_names = [x["setting"] for x in d.get("settings", [])]
                for pick in choices(panel(v)):
                    if pick["setting"] not in have_names:
                        d.setdefault("settings", []).append(pick)
                        added += 1
        DEVICES.write_text(json.dumps(data, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8", newline=chr(10))
        print(added, "settings added")
    if "--keys" in sys.argv:
        # Adds the older panels' buttons (type 1) and noise screen (type 11) to the models that lack them, leaving every other field as it is.
        touched = noised = 0
        for v, d in have.items():
            if v in prods and panel(v):
                f = features(panel(v))
                if "touch" not in d and f.get("touch"):
                    d["touch"] = f["touch"]
                    touched += 1
                if "anc" not in d and f.get("jieli_noise"):
                    d["anc"] = f["anc"]
                    d["jieli_noise"] = True
                    noised += 1
        DEVICES.write_text(json.dumps(data, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8", newline=chr(10))
        print("buttons added to", touched, "models, noise control to", noised)
    if "--presets" in sys.argv:
        # Adds the preset curves to the models that have them, leaving every other field as it is.
        added = 0
        for v, d in have.items():
            if d.get("eq") and v in prods and panel(v):
                gains = (entry(prods[v], panel(v)).get("eq") or {}).get("preset_gains")
                if gains and d["eq"].get("preset_gains") != gains:
                    d["eq"]["preset_gains"] = gains
                    added += 1
        DEVICES.write_text(json.dumps(data, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8", newline=chr(10))
        print("preset curves added to", added, "models")
    if "--write" in sys.argv and new:
        data["devices"] = sorted(data["devices"] + new, key=lambda d: d["vendor_id"])
        about = data["_about"]
        data["_about"] = about + (" Models the Quicky dump lacks were added from QCY's own product list and control panels as fetched on 2026-11-04 "
                                  "(tools/import_qcy_panels.py).") if "import_qcy_panels" not in about else about
        DEVICES.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        print("added", len(new))


if __name__ == "__main__":
    main()
