"""Write each SOUNDPEATS model's EQ (and the models PairFree lacked) into data/soundpeats/devices.json from saved copies
of the SOUNDPEATS app's per-model replies.

The owner fetched these with their own account (2026-11-04) into server_dump/soundpeats/v2/, which git ignores:
devices_pairs.json (every model: firmware code, Bluetooth name, chip family) and per code <code>_presets.json (the
classic curve and the presets, each with its master gain, frequencies, Qs and gains, and paramAppend, the app's
EqDataStyle). Kept per model: the curves' names and band values, the chip family and the Bluetooth name. Left out:
ids, codes, images, trace ids and everything else.

How a curve goes to the earbuds follows the app (docs/soundpeats/PROTOCOL.md section 4): EqDataStyle ORIGIN ("2",
onto_classic_bands) sends a preset as it is and the user's gains on the classic curve's bands; ADD ("1",
append_after_classic) sends the classic bands first, then the preset's or the user's own bands; OPERA ("3") sends a
preset as it is, its band count its own, and the user's bands after the classic ones. The Clip C10 (T2), which the
server has no curves for, keeps its recording.

This tool only reads the saved replies; it never fetches anything.

usage: python tools/import_soundpeats_server.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEVICES = ROOT / "data" / "soundpeats" / "devices.json"
COMMANDS = ROOT / "data" / "soundpeats" / "commands.json"
DUMP = ROOT / "server_dump" / "soundpeats" / "v2"
STYLE = {"1": "append_after_classic", "2": "onto_classic_bands", "3": "opera"}
RANGE_DB = 6


def num(v):
    v = float(v)
    return int(v) if v.is_integer() else v


def parse(text):
    # The Wings2's (TS03WINGS2) classic frequencies read "[]50, 120, ...": the stray "]" is dropped.
    text = text.strip()
    return json.loads("[" + text[2:] if text.startswith("[]") and len(text) > 2 else text)


def curve(p):
    hz, q, gain = (parse(p[k]) for k in ("fjson", "qjson", "gainJson"))
    return [{"hz": int(h), "q": num(qq), "gain_db": num(g)} for h, qq, g in zip(hz, q, gain)]


def sendable(bands):
    """Whether every band fits the write as the app encodes it (frequency x 3 and Q x 4096 in 16 bits, Q above 0).
    A few server curves do not: negative Qs (ECHO7018) and bands at 22 or 24 kHz, which the app's encoding wraps."""
    return all(0 < b["q"] * 4096 <= 0xFFFF and 0 < b["hz"] * 3 <= 0xFFFF for b in bands)


def eq_entry(model, presets, custom):
    style = STYLE.get(str(presets[0].get("paramAppend")), "append_after_classic")
    classic = next((p for p in presets if p["name"].lower() == "classic"), presets[0])
    others = [p for p in presets if p is not classic]
    base = curve(classic)
    if not sendable(base):
        return style, None
    others = [p for p in others if sendable(curve(p))]
    # The QCY-built models take the whole curve on QCY's EQ characteristic, 000B (docs/soundpeats/PROTOCOL.md section 10.5).
    eq = {"command": model["eq_command"] or "000B", "master_gain_db": num(classic["masterGain"]), "user_range_db": RANGE_DB}
    if style == "onto_classic_bands":
        eq["bands"] = [{"hz": b["hz"], "q": b["q"], "base_gain_db": b["gain_db"]} for b in base]
        eq["presets"] = [{"name": p["name"], "bands": curve(p)} for p in others]
    else:
        eq["fixed_bands"] = base
        eq["bands"] = [{"hz": h, "q": custom["q"], "base_gain_db": 0} for h in custom["hz"][model.get("custom_eq_bands", 10)]]
        lead = [] if style == "opera" else base
        eq["presets"] = [{"name": p["name"], "bands": lead + curve(p)} for p in others]
    return style, eq


def main():
    data = json.loads(DEVICES.read_text(encoding="utf-8"))
    commands = json.loads(COMMANDS.read_text(encoding="utf-8"))["eq"]
    custom = {"q": commands["custom_band_q"], "hz": {10: commands["custom_bands_hz"], 8: commands["custom_bands_hz_bluetrum"]}}
    by_code = {m["firmware_code"]: m for m in data["devices"] if m.get("firmware_code")}
    pairs = json.loads((DUMP / "devices_pairs.json").read_text(encoding="utf-8"))["data"]
    added = updated = 0
    for p in pairs:
        code, family = p["otaCode"], p["schemeCode"]
        model = by_code.get(code)
        if model is None:
            ble = family == "QCY-WUQI"
            model = {"firmware_code": code, "names": [p["bluetoothName"].strip()], "family": family, "family_sources": {},
                     "old_eq_protocol": None, "family_conflict": False, "transport": "qcy_ble" if ble else "spp",
                     "eq_command": None if ble else "0E01" if family == "QUALCOMM" else "0E02", "notes": "Untested."}
            data["devices"].append(model)
            by_code[code] = model
            added += 1
        elif model.get("family") == "UNKNOWN":
            model["family"] = family
        # The app's rules (docs/soundpeats/PROTOCOL.md section 4) where the server gives no curves.
        onto = model["family"] == "QUALCOMM" or code in commands["merge_onto_classic_codes"]
        model.setdefault("eq_merge", "onto_classic_bands" if onto else "append_after_classic")
        model["custom_eq_bands"] = 8 if model["family"] == "FOTA" else 10
        if p["bluetoothName"].strip() not in model["names"]:
            model["names"].append(p["bluetoothName"].strip())
        f = DUMP / f"{code}_presets.json"
        presets = json.loads(f.read_text(encoding="utf-8")).get("data") if f.exists() else None
        if presets:
            style, eq = eq_entry(model, presets, custom)
            if eq is None:
                print(f"{code}: its classic curve cannot be sent as the app encodes it; no EQ")
                continue
            model["eq"] = eq
            if style != "opera":
                model["eq_merge"] = style
            updated += 1
    DEVICES.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"{added} models added, {updated} EQs written")


if __name__ == "__main__":
    main()
