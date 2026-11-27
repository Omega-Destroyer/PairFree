"""Builds data/jabra/commands.json, settings.json and devices.json from the research files in data/jabra/research/.

    python tools/import_jabra_app.py

The research files (docs/jabra/PROTOCOL.md, written 2026-11-09 from Jabra Sound+ 5.22.0) hold the whole schema catalog the app bundles: 1,755 command
definitions, 315 settings variants and 46 firmware snapshots. PairFree needs a small part of that, in the shape its session reads:

- `commands.json`: the commands the session sends by name (battery, noise control, equalizer, ...), the addresses and the never-sent list.
- `settings.json`: every setting a production profile lists, with the command, the selector bytes of its read, the value's position and width,
  and the option codes. A shared payload (sidetone: one command, two values) is marked so the session reads the whole payload before it changes one byte.
- `devices.json`: the production models by display name, their variants (product id), each variant's firmware snapshots with the settings they list,
  and what the app's definition files say about the model.

Settings that touch things PairFree does not do are left out and listed under `left_out` with the reason (renaming, the pairing list, firmware lock,
speed dial). Nothing here is copied from the app's code; the facts come from the research files only.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "data" / "jabra" / "research"
OUT = ROOT / "data" / "jabra"

# Settings PairFree does not build, by the vendor's command name. blocked: <reason>; checked: <what was read>.
LEFT_OUT = {
    "BLUETOOTH_NAME": "renaming the earbuds (owner, 2026-11-04: no renaming); the name read is private",
    "AUDIO_NAME": "the USB audio name of a headset (a rename)",
    "GET_DB_RECORD": "the pairing list holds other devices' addresses and names; reading it is private and deleting from it is out of scope",
    "SPEED_NUMBER": "a phone number the user would type (contact data); PairFree keeps none",
    "FIRMWARE_UPGRADE_LOCK": "firmware update traffic (hard rule)",
}

# Settings the session builds itself from the native handlers (the ambience and loop commands are virtual in the SDK).
VIRTUAL = ("ANC_HT_SOUND_SETTINGS_SPECIAL_CMD", "SOUND_MODE_LOOP_SPECIAL_CMD")

# Commands the session sends by name: constants for the hand-written parts (docs/jabra/PROTOCOL.md).
NAMED = {
    "pid": ("IDENT", 2, 17),
    "type": ("IDENT", 2, 2),
    "version": ("IDENT", 2, 3),
    "available_languages": ("IDENT", 2, 8),
    "battery": ("STATUS", 18, 2),
    "ambience_noise_level": ("STATUS", 18, 13),
    "config_mode_enter": ("DEVICE", 13, 17),
    "config_mode_end": ("DEVICE", 13, 18),
    "ambience": ("CONFIG", 19, 0xBE),
    "sound_mode_loop": ("CONFIG", 19, 0xBF),
    "equalizer": ("CONFIG", 19, 0x7D),
    "equalizer_enable": ("CONFIG", 19, 0x7E),
    "buttons": ("CONFIG", 19, 0x9A),
    "buttons_default": ("CONFIG", 19, 0xAD),
    "spatial": ("CONFIG", 19, 0xE4),
    "spotify": ("CONFIG", 19, 0xE5),
    "mysound": ("CONFIG", 19, 0x9C),
}

# Never sent without the owner's confirmation in that moment (hard rules): factory default, restart, DFU, firmware update, debug, production tests.
NEVER_SEND_FAMILIES = {"DFU": 7, "FWU": 15, "HWTEST": 4, "HW_TEST": 4, "BT_PROD_TEST": 12, "DEBUG": 6, "FLASH": 25, "AUTOMATED_TEST": 29, "LOG": 35, "SYSMON": 34}
NEVER_SEND_COMMANDS = {
    "factory_default": (13, 19),
    "factory_default_no_boot": (13, 23),
    "prepare_restart": (13, 22),
    "enter_mode": (13, 21),
    "modify_db_record": (13, 51),
    "delete_db_record": (13, 42),
    "get_bt_addr_and_linkkey": (13, 56),
}


def hexbyte(v):
    s = str(v).upper()
    return s.zfill(2 + len(s) % 2) if len(s) % 2 else s.zfill(2)


def width_of(options, field_shape):
    if field_shape == "integer":
        return 2
    sizes = [len(o["hex"]) for o in options] or [2]
    return max(1, (max(sizes) + 1) // 2)


POSITIVE = ("enabled", "enable", "on", "unmuted")
NEGATIVE = ("disabled", "disable", "off", "muted")

# Options of the older noise form (`13/87`), by the layout's own code names.
ANC_CODES = {"Off": "GNP_CONFIG_ANC_OFF", "NoiseCancelling": "GNP_CONFIG_ANC_ON", "Surroundings_only": "GNP_CONFIG_ANC_HT_NOTMIXED", "Music_surroundings": "GNP_CONFIG_ANC_HT_MIXED"}
# The programmable button's choices by the layout's names (the form's "Start_voice_instruction" has no code there).
BUTTON_CODES = {"PlayPause": "Play/pause", "Speed_dial": "Speed dial", "No_function": "No Function"}


def tail(name):
    n = name.lower().replace("-", "_").replace("/", "_")
    for word in POSITIVE:
        if n == word or n.endswith("_" + word):
            return "on"
    for word in NEGATIVE:
        if n == word or n.endswith("_" + word):
            return "off"
    return None


def resolve_codes(setting, layout_codes):
    """The form leaves some options with no code, or one shared by both; the command's layout names the real codes. Returns the options, or None when they cannot be resolved."""
    opts = setting["options"]
    codes = [o[1] for o in opts]
    if len(set(codes)) == len(codes) and all(codes):
        return opts
    by_name = {n: h for n, h in layout_codes}
    resolved = []
    for name, _ in opts:
        code = None
        if setting["command"] == "ANC":
            code = by_name.get(ANC_CODES.get(name, ""))
        elif setting["command"] == "BUTTON_FUNCTION":
            code = by_name.get(BUTTON_CODES.get(name, ""))
        else:
            want = tail(name)
            matches = [h for n, h in layout_codes if want and tail(n) == want]
            code = matches[0] if len(matches) == 1 else None
        if code is not None:
            resolved.append([name, hexbyte(code)])
    if len(resolved) < 2 or len({c for _, c in resolved}) != len(resolved):
        return None
    return resolved


def main():
    settings_in = json.loads((RES / "gnp_settings.json").read_text(encoding="utf-8"))["settings"]
    defs = {d["definition_id"]: d for d in json.loads((RES / "gnp_commands.json").read_text(encoding="utf-8"))["definitions"]}
    profiles_in = json.loads((RES / "firmware_profiles.json").read_text(encoding="utf-8"))["profiles"]
    devices_in = json.loads((RES / "devices.json").read_text(encoding="utf-8"))["models"]
    gaia_in = json.loads((RES / "gaia_commands.json").read_text(encoding="utf-8"))
    presets_in = json.loads((RES / "eq_presets.json").read_text(encoding="utf-8"))

    settings = {}
    left_out = {}
    for s in settings_in:
        cmd = s["command_name"]
        if cmd in LEFT_OUT:
            left_out[s["setting_id"]] = cmd
            continue
        d = defs[s["command_definitions"][0]]
        virtual = cmd.startswith(VIRTUAL)
        if virtual:
            pass
        elif s["widget"] in ("listsettingvalue", "boolsettingvalue") and s.get("options"):
            fields = d["layouts"].get("write") or d["layouts"].get("reply") or []
            value_pos = int(s["value_position"]) if s.get("shared_payload") else None
            if value_pos is None:
                selp = {int(x["position"]) for x in s.get("selectors", [])}
                free = [f for f in d["layouts"].get("reply", fields) if int(f["position"]) not in selp]
                value_pos = int(free[0]["position"]) if free else 1
            field = [f for f in fields if int(f["position"]) == value_pos]
            layout_codes = [(c["name"], c["hex"]) for r in (field[0].get("ranges", []) if field else []) for c in r.get("codes", [])]
            fixed = resolve_codes({"command": cmd, "options": [[o["name"], hexbyte(o["hex"]) if o["hex"] else ""] for o in s["options"]]}, layout_codes)
            if fixed is None:
                left_out[s["setting_id"]] = cmd + " (option codes not resolvable)"
                continue
            s = dict(s, options=[{"name": n, "hex": h} for n, h in fixed])
        reply = d["layouts"].get("reply", [])
        sel = [[int(x["position"]), x["hex"].upper().zfill(2)] for x in s.get("selectors", [])]
        selpos = {p for p, _ in sel}
        free = [f for f in reply if int(f["position"]) not in selpos]
        entry = {
            "name": s["name"],
            "group": s["group"].lower(),
            "dest": s["destination"],
            "command": cmd,
            "widget": {"listsettingvalue": "list", "boolsettingvalue": "bool", "statictextsettingvalue": "static", "textsettingvalue": "text"}[s["widget"]],
            "options": [[o["name"], hexbyte(o["hex"])] for o in s.get("options", [])],
        }
        if not virtual:
            entry["main"] = d["main"]
            entry["sub"] = d["subcommand"]
            if sel:
                entry["selectors"] = sel
            cfg = {k: v for k, v in d.get("configuration_mode_required", {}).items() if str(v).lower() == "true"}
            if cfg:
                entry["config_mode"] = sorted(cfg)
        if s.get("shared_payload"):
            entry["shared"] = True
            entry["pos"] = int(s["value_position"])
        elif free:
            entry["pos"] = int(free[0]["position"])
        entry["width"] = width_of(s.get("options", []), free[0]["shape"] if free else "byte")
        if s.get("range_value"):
            entry["range_value"] = True
        if s.get("special_handler"):
            entry["special"] = True
        if s.get("command_dependencies"):
            entry["depends"] = s["command_dependencies"]
        if virtual:
            entry["virtual"] = cmd.split("SPECIAL_CMD_")[-1] if "SPECIAL_CMD" in cmd else cmd
        settings[s["setting_id"]] = entry

    # Profiles: one per bundled firmware snapshot.
    profiles = {}
    for p in profiles_in:
        profiles[p["snapshot"]] = {
            "pid": p["product_id"],
            "variant": p["variant"],
            "firmware": p["firmware"],
            "caps": sorted(k for k, v in p["device_capabilities"].items() if str(v).lower() == "true"),
            "settings": [i for i in p["settings"] if i in settings],
        }

    # Models, by display name; a name with several product ids (Evolve2 65: UC, MS, mono) keeps each as a variant.
    models = {}
    for m in devices_in:
        if not m["production"]:
            continue
        model = models.setdefault(m["name"], {"name": m["name"], "kind": m["kind"], "variants": []})
        flags = sorted(k for k, v in m["capability_flags"].items() if v)
        v = {
            "pid": m["product_id"],
            "definition": m["definition"],
            "protocol": m["protocol"],
            "flags": flags,
            "battery": m["battery_layout"],
            "wear": m["wear_detection"],
            "snapshots": m["firmware_profiles"],
        }
        if m.get("default_noise_loop"):
            v["noise_loop"] = m["default_noise_loop"]
        if m.get("forced_noise_modes"):
            v["forced_noise"] = {k: x for k, x in m["forced_noise_modes"].items() if x}
        if m.get("my_controls_onboarding"):
            v["my_controls"] = True
        if m.get("hearing_personalization"):
            v["mysound"] = True
        if m.get("spatial") and any(m["spatial"].values()):
            v["spatial"] = {k: x for k, x in m["spatial"].items() if x}
        if m.get("assistants"):
            v["assistants"] = m["assistants"].get("supportedVoiceAssistants", [])
        if m.get("spotify_platforms"):
            v["spotify"] = "android" in m["spotify_platforms"]
        if m.get("le_audio"):
            v["le_audio_firmware"] = m["le_audio"].get("firstle_audio_firmware")
        model["variants"].append(v)
        if model["kind"] != m["kind"]:
            model["kind"] = m["kind"]
    for model in models.values():
        model["variants"].sort(key=lambda v: v["pid"])

    commands = {
        "_about": "Commands PairFree's Jabra session sends by name, from docs/jabra/PROTOCOL.md (research 2026-11-09, Jabra Sound+ 5.22.0). Written by tools/import_jabra_app.py.",
        "wire": {
            "spp_uuid": "00001101-0000-1000-8000-00805F9B34FB",
            "destination": 4,
            "source": 9,
            "max_frame": 63,
            "attributes": {"event": 0, "read": 1, "write": 2, "reply": 3},
            "addresses": {"headset": 4, "dsp": 8, "mobile": 9},
        },
        "named": {k: {"family": f, "main": m, "sub": s} for k, (f, m, s) in NAMED.items()},
        "never_send": {
            "families": NEVER_SEND_FAMILIES,
            "commands": {k: {"main": m, "sub": s} for k, (m, s) in NEVER_SEND_COMMANDS.items()},
            "note": "Production tests, firmware and debug families, factory default, restart preparation, pairing database writes and link keys. Never sent; restart goes only from a confirmed action.",
        },
        "eq_presets": presets_in["presets"],
        "gaia": gaia_in,
    }

    out_settings = {
        "_about": "Jabra's settings as the Sound+ forms list them, one entry per variant (research/gnp_settings.json). `pos` is the value's position in the command's payload after the subcommand byte, counting from 1; `selectors` are the bytes of its read; a `shared` payload holds several values and is read before one is changed. Written by tools/import_jabra_app.py.",
        "left_out": {"_about": "settings the research lists that PairFree does not build, by id, with the command", **left_out},
        "settings": settings,
    }
    out_devices = {
        "_about": "Jabra's production models (research/devices.json) with the firmware snapshots the app bundles. Pick the variant by the product id the earbuds report, the snapshot by firmware (the nearest earlier one when none matches). Written by tools/import_jabra_app.py.",
        "profiles": profiles,
        "models": sorted(models.values(), key=lambda m: m["name"]),
    }
    (OUT / "commands.json").write_text(json.dumps(commands, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (OUT / "settings.json").write_text(json.dumps(out_settings, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (OUT / "devices.json").write_text(json.dumps(out_devices, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(settings)} settings ({len(left_out)} left out), {len(profiles)} profiles, {len(models)} models")


if __name__ == "__main__":
    main()
