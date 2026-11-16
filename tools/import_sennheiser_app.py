"""Builds data/sennheiser/commands.json and devices.json from Sennheiser Smart Control 4.9.4's own product files.

    python tools/import_sennheiser_app.py PATH_TO_assets/app

PATH holds the app's product files (`m4.json`, `ac1.json`, ... and `features/`), taken from the APK outside the repo (docs/sennheiser/PROTOCOL.md section 1).
Only facts go into the repo's data: command words, parameter types, value maps, and which properties each product's file lists. The app's code and
the files themselves are not copied. Smart Control Plus 1.6.3 (its Dart snapshot) gave the newer models' names and platforms (`SCP_MODELS`).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "sennheiser"

# The properties PairFree builds (docs/sennheiser/PROTOCOL.md section 5): app property name -> PairFree's id.
PROPS = {
    "Battery_Percent": "battery_percent",
    "Battery_ChargingStatus": "battery_charging",
    "Sound_CodecUsed": "codec",
    "Service_SystemReleaseVersion": "firmware_version",
    "Versions_ModelId": "model_id",
    "ANC_Status": "anc_status",
    "ANC": "anc_mode",
    "TransparentHearing_Status": "transparency_status",
    "TransparentHearingMode": "transparency_pause",
    "Setting_OnHeadDetection": "on_head",
    "Setting_SmartPause": "smart_pause",
    "Setting_AutoCall": "auto_call",
    "Setting_ComfortCall": "comfort_call",
    "LowLatencyMode": "low_latency",
    "Setting_AutoLockMode": "touch_lock",
    "Power_EcoMode": "eco_mode",
    "Power_BatteryProtectionMode": "battery_protection",
    "Setting_AudioPromptMode": "prompt_mode",
}

# The Sennheiser vendor id's notification registration (RegisterNotification_* in the product files; `Additional_Values` is the feature id).
FEATURES = {"core": 0, "device": 2, "battery": 3, "generic_audio": 4, "user_eq": 8, "versions": 9, "device_management": 10, "mmi": 11,
            "transparent_hearing": 12, "anc": 13}

# The products Smart Control 4.9.4 defines as GAIAV3 (its product file, the app's name), with the Bluetooth names the app maps to them
# (DeviceType.getDeviceTypeByManufacturerName and deviceTypeByBluetoothNameMap) and what kind of product it is.
SC_MODELS = {
    "ac1.json": ("Accentum", "headband", ["AC1", "AC1 BLACK", "AC1 WHITE", "ACAEBT", "ACAEBT Black", "ACAEBT R", "ACAEBT SE", "ACAEBT White", "ACCENTUM", "VENUS", "VENUS Black", "VENUS White"]),
    "acp1.json": ("Accentum Plus", "headband", ["ACCENTUM Plus", "ACP1", "ACP1 BLACK", "ACP1 WHITE", "ACPAEBT Black", "ACPAEBT White", "APHRODITE", "APHRODITE Black", "APHRODITE White"]),
    "atw1.json": ("Accentum True Wireless", "earbuds", ["ACCENTUM TW", "ATW1", "ATW1 Black", "ATW1 Blue", "ATW1 SE", "ATW1 White", "Accentum TW"]),
    "cx200.json": ("CX 200 True Wireless", "earbuds", ["CX 200BT TW", "CX True Wireless", "CX200TW1", "CX200TW1 Black", "CX200TW1 White"]),
    "cx200sports.json": ("CX Sport True Wireless", "earbuds", ["CX200TW1 S", "CX200TW1 S Black", "Sport True Wireless"]),
    "cx400II.json": ("CX Plus True Wireless", "earbuds", ["CX 400BT TW2", "CX 400iiBT TW", "CX Plus SE True Wireless", "CX Plus True Wireless", "CX400TW2", "CXPLUSTW1 Black", "CXPLUSTW1 SE", "CXPLUSTW1 White"]),
    "cx500.json": ("CX 500BT True Wireless", "earbuds", ["CX 500BT", "CX500BT1", "CX500BT1 Grey"]),
    "m4.json": ("Momentum 4 Wireless", "headband", ["M4AEBT", "M4AEBT Black", "M4AEBT Denim", "M4AEBT Graphite", "M4AEBT JD", "M4AEBT Olive", "M4AEBT PRIDE", "M4AEBT SE", "M4AEBT White", "MARS", "MARS Black", "MARS White", "MOMENTUM 4", "Mars"]),
    "msport1.json": ("Momentum Sport", "earbuds", ["MOMENTUM SPORT", "MOMENTUM SPORT1", "MOMENTUM Sport", "MSPORT1", "MSPORT1 Black", "MSPORT1 Graphite", "MSPORT1 Olive", "Magnum", "Magnum Black", "Magnum Graphite", "Magnum Olive"]),
    "tw3.json": ("Momentum True Wireless 3", "earbuds", ["MOMENTUM TW 3", "MTW3", "MTW3 Black", "MTW3 Graphite", "MTW3 White"]),
    "tw4.json": ("Momentum True Wireless 4", "earbuds", ["MOMENTUM TW 4", "MTW4", "MTW4 Black", "MTW4 Black Copper", "MTW4 Black Graphite", "MTW4 Copper", "MTW4 Denim", "MTW4 Graphite", "MTW4 JD", "MTW4 Silver", "MTW4 White", "MTW4 White Silver"]),
}

# Models only Smart Control Plus 1.6.3 lists (its `cKd` enum), all on Qualcomm's GAIA 3 ("herakles"): no product file, so what they answer decides what shows.
SCP_MODELS = [
    ("Momentum 5 Wireless", "headband", ["M5AEBT", "MOMENTUM 5"], "800078"),
    ("Momentum True Wireless 5", "earbuds", ["MOMENTUM TW 5", "MTW5", "PHOBOS", "Phobos", "phobos"], "700350"),
    ("HDB 630", "headband", ["HDB 630", "HDBT"], "700445"),
    ("HDR 275 / 255", "headband", ["HDR 275", "HDR275", "HDR275 Black", "Venus TVL", "VENUS TVL"], "700438"),
]


def hexid(v):
    return format(int(v, 16), "04X")


def command(c):
    """One command or response object of a product file as PairFree keeps it."""
    g = c["gaiaV3"]
    out = {"vendor": hexid(g["vendor_ID"]), "command": hexid(g["GAIA_command_ID"])}
    if "GAIA_notification_ID" in g and int(g["GAIA_notification_ID"], 16):
        out["notification"] = hexid(g["GAIA_notification_ID"])
    if "GAIA_error_ID" in g:
        out["error"] = hexid(g["GAIA_error_ID"])
    out["params"] = [[p["type"], p["name"]] for p in c.get("parameter", [])]
    return out


def side(prop, key):
    """The read ("Get") or the write ("Set") of a property: its command and its response."""
    block = prop.get(key)
    if not block:
        return None
    cmd = block["Command"] if isinstance(block.get("Command"), dict) else None
    rsp = block.get("Response") if isinstance(block.get("Response"), dict) else None
    if not cmd or "gaiaV3" not in cmd:
        return None
    out = {"send": command(cmd)}
    if rsp and "gaiaV3" in rsp:
        out["reply"] = command(rsp)
    ev = block.get("Event")
    if isinstance(ev, dict) and "gaiaV3" in ev:
        out["event"] = command(ev)
    return out


def main(assets):
    assets = Path(assets)
    products = {}
    commands = {}
    for fname in SC_MODELS:
        d = json.loads((assets / fname).read_text(encoding="utf-8"))
        assert d["protocol"] == "GAIAV3", fname
        products[fname] = {p["name"]: p for p in d["properties"]}
        for app_name, pid in PROPS.items():
            p = products[fname].get(app_name)
            if not p:
                continue
            entry = {"app_property": app_name}
            val = p.get("Value", {})
            if val.get("map"):
                entry["values"] = val["map"]
            get, set_ = side(p, "Get"), side(p, "Set")
            if get:
                entry["get"] = get
            if set_:
                entry["set"] = set_
            if pid in commands and commands[pid] != entry:
                # The same property on another product: the ids must agree.
                assert commands[pid]["get"]["send"]["command"] == entry.get("get", {}).get("send", {}).get("command"), (pid, fname)
            commands.setdefault(pid, entry)
    reg = products["m4.json"]["RegisterNotification_ANC"]["Invocation"]["Command"]["gaiaV3"]
    data = {
        "_about": "Sennheiser's GAIA v3 commands, read from Smart Control 4.9.4's product files (docs/sennheiser/PROTOCOL.md). A command is a vendor id (0495 Sennheiser's own, 001D Qualcomm's) and a command word; its reply is the word + 0100 and its error + 0180. send/reply list each parameter's type and the app's name for it; values is the app's map of names to numbers. Written by tools/import_sennheiser_app.py; PairFree builds only what is listed here (the app's other properties are in the research notes).",
        "transport": {
            "classic_uuid": "a2129ff3-081b-4c45-8afe-469d9c4842ec",
            "ble_services": ["FCFE", "FDCE"],
            "ble_write": "6333133B-23C1-11E5-B696-FEFF819CDC9F",
            "ble_read": "6333133C-23C1-11E5-B696-FEFF819CDC9F",
            "company_id": "0BA3",
        },
        "register_notification": {"vendor": hexid(reg["vendor_ID"]), "command": hexid(reg["GAIA_command_ID"]), "features": FEATURES},
        "commands": [{"id": pid, **commands[pid]} for pid in PROPS.values() if pid in commands],
        "never_send": [
            {"app_property": "Service_FactoryReset", "vendor": "0495", "command": "0040", "why": "factory reset"},
            {"app_property": "Debug_ErasePanicLog", "why": "erases the earbuds' panic log"},
            {"app_property": "Upgrade_*", "why": "firmware update (the VMUP commands)"},
            {"app_property": "SetMMIConfig, SetDefaultMMIConfig", "why": "not built yet"},
        ],
    }
    models = []
    for fname, (name, klass, names) in SC_MODELS.items():
        have = [pid for app_name, pid in PROPS.items() if app_name in products[fname]]
        models.append({"name": name, "kind": klass, "names": names, "source": "Smart Control 4.9.4 " + fname, "properties": have})
    for name, klass, names, article in SCP_MODELS:
        models.append({"name": name, "kind": klass, "names": names, "article": article, "source": "Smart Control Plus 1.6.3 (cKd), no product file", "properties": None})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "commands.json").write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    (OUT / "devices.json").write_text(json.dumps({
        "_about": "Sennheiser models PairFree controls, all on Qualcomm's GAIA v3 (docs/sennheiser/PROTOCOL.md). names are the Bluetooth names Smart Control maps to the model; properties lists the PairFree properties (commands.json ids) the app's own product file has, or null for a model with no product file in the apps: PairFree reads each property and shows what answers. reports lists device reports.",
        "models": models,
    }, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(data["commands"]), "commands,", len(models), "models")


if __name__ == "__main__":
    main(sys.argv[1])
