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
    # The second batch (docs/sennheiser/PROTOCOL.md section 4.2).
    "SidetoneState": "sidetone",
    "BluetoothCompatibilityMode": "bt_compat",
    "ANC_Transparency": "anc_level",
    "TransparentHearing": "transparency_level",
    "VoicePrompt_Language": "prompt_language",
    "TapDetection": "tap_sensitivity",
    "Sound_OpenEartip": "open_eartip",
    "AptXLosslessSupport": "lossless",
    "PhysicalDevice_State": "physical_state",
    "Sound_Sample_Rate": "sample_rate",
    "Touch_Locked": "touch_area",
    # A write and a read that are two properties in the files (each an Invocation), kept as one id.
    "Config_GetTimer": "auto_off",
    "Config_SetTimer": "auto_off",
    "IsDefaultMMIConfig": "mmi_is_default",
    "SetDefaultMMIConfig": "mmi_default",
    "GetMMIConfig": "mmi",
    "SetMMIConfig": "mmi",
}
# Properties that need a payload to read (a timer number, a button and a pattern): the session reads them where it knows the payload.
NOT_ON_OPEN = {"auto_off", "mmi", "mmi_default", "mmi_is_default"}

# The Sennheiser vendor id's notification registration (RegisterNotification_* in the product files; `Additional_Values` is the feature id).
FEATURES = {"core": 0, "device": 2, "battery": 3, "generic_audio": 4, "user_eq": 8, "versions": 9, "device_management": 10, "mmi": 11,
            "transparent_hearing": 12, "anc": 13, "fit_test": 15, "personalized_sound": 16, "auracast": 21, "find_device": 22}

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

# The older GAIA v2 products Smart Control 4.9.4 defines (docs/sennheiser/PROTOCOL.md section 4.9): their product file, the app's name, the kind, the
# Bluetooth names the app maps to them, and whether Android opens them over the serial port (`DeviceConfig`) or only over Bluetooth LE. `three_band` is the gain
# limit of the app's three-band equalizer (`get3BandParametersForDevice`: 4 for all of these), `eq` whether the product's class writes the knobs.
GAIA2_MODELS = {
    "cx350.json": ("CX 350BT", "headband", ["CX 350BT"], "spp", True),
    "hd250.json": ("HD 250BT", "headband", ["HD 250BT"], "spp", True),
    "hd350.json": ("HD 350BT", "headband", ["HD 350BT"], "spp", True),
    "hd450.json": ("HD 450BT", "headband", ["HD 450BT"], "spp", True),
    "hd450se.json": ("HD 450SE", "headband", ["HD 450SE"], "spp", True),
    "hd458.json": ("HD 458BT", "headband", ["HD 458BT"], "spp", False),
    "ie80.json": ("IE 80S BT", "headband", ["IE 80S BT"], "spp", False),
    "cx400.json": ("CX 400BT True Wireless", "earbuds", ["CX TW", "CX 400BT TW"], "ble", True),
    "tw1.json": ("Momentum True Wireless", "earbuds", ["MOMENTUM TW"], "ble", True),
    "tw2.json": ("Momentum True Wireless 2", "earbuds", ["MOMENTUM TW 2"], "ble", True),
}
GAIA2_PROPS = {
    "Battery_Percent": "battery_percent",
    "Battery_ChargingStatus": "battery_charging",
    "Battery_ChargerEvent": "battery_charging",
    "AudioMode": "audio_mode",
    "Sound_CustomEQKnobX": "eq_knob",
    "Sound_SetCustomEQ": "eq_params",
    "SidetoneState": "sidetone",
    "Service_SystemReleaseVersion": "firmware_version",
    "Setting_AudioPromptMode": "prompt_mode",
    "Setting_AutoCall": "auto_call",
    "Setting_SmartPause": "smart_pause",
    "Sound_CodecUsed": "codec",
    "Config_GetTimer": "auto_off",
    "Config_SetTimer": "auto_off",
    "Setting_MultiFunctionButton": "multi_function",
    "VoicePrompt_Language": "prompt_language",
    "Setting_AutoLockMode": "touch_lock",
    "TransparentHearing": "transparency_status",
    "Sound_AdaptiveNoiseCancelationState": "anc_state",
}


def gaia2_side(cmd, rsp):
    """A GAIA v2 command (`gaia` object) and its response as PairFree keeps them."""
    if not isinstance(cmd, dict) or "gaia" not in cmd:
        return None
    g = cmd["gaia"]
    out = {"vendor": hexid(g["vendor_ID"]), "command": hexid(g["GAIA_command_ID"]), "params": [[p["type"], p["name"]] for p in cmd.get("parameter", [])]}
    if isinstance(rsp, dict) and "gaia" in rsp:
        out["reply"] = hexid(rsp["gaia"]["GAIA_command_ID"])
        out["reply_params"] = [[p["type"], p["name"]] for p in rsp.get("parameter", [])]
    return out


def gaia2_product(prop):
    entry = {}
    val = prop.get("Value", {})
    if val.get("map"):
        entry["values"] = val["map"]
    for key, where in (("Get", "get"), ("Set", "set")):
        blk = prop.get(key)
        if blk:
            side_ = gaia2_side(blk.get("Command"), blk.get("Response"))
            if side_:
                entry[where] = side_
    if "Invocation" in prop:
        inv = prop["Invocation"]
        side_ = gaia2_side(inv.get("Command"), prop.get("Result", {}).get("Response"))
        if side_:
            entry["set" if "Set" in inv["Command"]["PI_command_name"] else "get"] = side_
    return entry


# The Nautilus headphones (docs/sennheiser/PROTOCOL.md section 4.10): module, optional submodule and command ids instead of vendor words.
NAUTILUS_MODELS = {
    "m3.json": ("Momentum 3 Wireless", "headband", ["MOMENTUM 3"]),
    "pxc550.json": ("PXC 550", "headband", ["PXC 550"]),
    "pxc550II.json": ("PXC 550-II", "headband", ["PXC 550-II"]),
}
NAUTILUS_PROPS = {
    "Battery_Percent": "battery_percent",
    "Battery_VoltageLevel": "battery_level",
    "ANC_ActiveMode": "anc_mode",
    "ANC_AmbientAudio": "ambient_audio",
    "ANC_ControlType": "anc_control",
    "ANC_ToggleAmbientMode": "ambient_toggle",
    "Input_Proximity_Mode": "proximity",
    "Setting_AudioPromptMode": "prompt_mode",
    "VoicePrompt_Language": "prompt_language",
    "System_CurrentCodec": "codec",
    "Sound_AdaptiveNoiseCancelationState": "anc_state",
    "Sound_CallEnhancement": "call_enhancement",
    "ANC_SwitchMiddlePositionState": "anc_middle",
    "Service_SystemReleaseVersion": "firmware_version",
    "Service_SoftwareVersion": "software_version",
    "Sound_Preset": "sound_preset",
}


def nautilus_side(cmd, rsp):
    if not isinstance(cmd, dict) or "nautilus" not in cmd:
        return None
    g = cmd["nautilus"]
    out = {"module": hexid(g["Nautilus_module_ID"]), "command": hexid(g["Nautilus_command_ID"]), "params": [[p["type"], p["name"]] for p in cmd.get("parameter", [])]}
    if g.get("Nautilus_submodule_ID"):
        out["submodule"] = hexid(g["Nautilus_submodule_ID"])
    if isinstance(rsp, dict) and "nautilus" in rsp:
        r = rsp["nautilus"]
        out["reply"] = {"module": hexid(r["Nautilus_module_ID"]), "command": hexid(r["Nautilus_command_ID"]), "params": [[p["type"], p["name"]] for p in rsp.get("parameter", [])]}
        if r.get("Nautilus_submodule_ID"):
            out["reply"]["submodule"] = hexid(r["Nautilus_submodule_ID"])
    return out


# Models only Smart Control Plus 1.6.3 lists (its `cKd` enum), all on Qualcomm's GAIA 3 ("herakles"): no product file, so what they answer decides what shows.
SCP_MODELS = [
    ("Momentum 5 Wireless", "headband", ["M5AEBT", "MOMENTUM 5"], "800078"),
    ("Momentum True Wireless 5", "earbuds", ["MOMENTUM TW 5", "MTW5", "PHOBOS", "Phobos", "phobos"], "700350"),
    ("HDB 630", "headband", ["HDB 630", "HDBT"], "700445"),
    ("HDR 275 / 255", "headband", ["HDR 275", "HDR275", "HDR275 Black", "Venus TVL", "VENUS TVL"], "700438"),
]


# The equalizer (docs/sennheiser/PROTOCOL.md section 6). Smart Control 4.9.4's JavaScript holds the numbers (`41710` get5BandParametersForDevice,
# get3BandParametersForDevice and getMultiBandGen2ParametersForDevice, and `default3BandEqPresets`, `momentum4_5BandEqPresets` and the other preset functions
# of `EqPresetsService`); they are transcribed here with the product each belongs to.
ROOT2 = 1 / 2 ** 0.5
EQ_GEN1 = {
    # key: (bassBoost, loudnessWeights, maxHeadroom, qFactor, bandFrequencies, labels)
    "momentum4": ([5, 5, 0, 0, 0], [-26.683, -10.813, -4.773, -4.813, -5.993], [5, 5, 4, 3, 2], [ROOT2] * 4, [0, 120, 400, 3000, 6000], [63, 250, 1000, 4000, 8000]),
    "accentum": ([5, -2, 0, 0, 0], [-26.683, -10.813, -4.773, -4.813, -5.993], [0, 0, 0, 0, 0], [ROOT2] * 4, [0, 125, 400, 1600, 5000], [50, 250, 800, 3000, 8000]),
    "mtw3": ([4, 1.5, 0, 0, 0], [-28.4, -10.6, -3.7, -3.6, -13.9], [3, 6, 3, 3, 3], [0.5, ROOT2, ROOT2, ROOT2], [0, 90, 325, 1500, 6500], [63, 250, 1000, 4000, 8000]),
    "momentum_sport": ([3, 3, 0, 0, 0], [-28.4, -10.6, -3.7, -3.6, -13.9], [2, 2, 2, 2, 2], [0.5, ROOT2, ROOT2, ROOT2], [0, 90, 325, 1500, 6500], [63, 250, 1000, 4000, 8000]),
    "accentum_tw": ([6, 3, 0, 1.5, 0], [-26.683, -10.813, -4.773, -4.813, -5.993], [2.5] * 5, [0.5, ROOT2, ROOT2, ROOT2], [0, 90, 325, 1500, 6500], [63, 250, 1000, 4000, 8000]),
    "mtw4": ([4, 1.5, 0, 0, 0], [-28.4, -10.6, -3.7, -3.6, -13.9], [3] * 5, [0.5, ROOT2, ROOT2, ROOT2], [0, 90, 325, 1500, 6500], [63, 250, 1000, 4000, 8000]),
}
EQ_GEN2_HZ = [63, 250, 1000, 4000, 8000]
EQ_3BAND = {"hz": [50, 1000, 8000], "bass_boost": 5, "bass_boost_frequency": 150, "lower_mid_frequency": 280, "upper_mid_frequency": 1600, "range_db": 6}
EQ_PRESETS = {
    "default3": [("Rock", [0, 3, 0]), ("Pop", [0, 2, 3]), ("Dance", [2, -2, 2]), ("Hip hop", [3, 2, 0]), ("Classical", [-2, 0, 1]), ("Movie", [0.7, 1.2, -2.2])],
    "momentum4": [("Rock", [0, 2, 2.5, 1.5, -2]), ("Pop", [0, -2.5, 0, 2.5, 0]), ("Dance", [3.5, 2, -1.5, 1.5, 3]), ("Hip hop", [3, 1.5, -1.5, 0, -1.5]),
                  ("Classical", [-2, -1.5, 0, 3.5, 4]), ("Movie", [0, 0, 2, 2, -2]), ("Jazz", [-3.2, 0, 2.2, 2.2, 0])],
    "accentum": [("Rock", [0, 0, 3, 3, -1]), ("Pop", [0, -5, 0, 5, 0]), ("Dance", [2, -3, -3, 1, 1]), ("Hip hop", [3, -1, -4, 2, 0]),
                 ("Classical", [0, 0, 0, 1, 2]), ("Movie", [0, 0, 2, 2, -1]), ("Jazz", [-3, 0, 2, 2, 0])],
    "mtw3": [("Rock", [-2.5, 0.5, 2.5, -0.5, -3.5]), ("Pop", [0, -2.5, 0, 2.5, -1]), ("Dance", [3, -1, -2, 0, 1]), ("Hip hop", [2.5, 1.5, -1.5, -1.5, -1.5]),
             ("Classical", [-3.5, -1.5, 0, 1.5, 0.5]), ("Movie", [-3, -1, 0, 1, -4]), ("Jazz", [-4, 0, 1, 3, -1])],
}
EQ_PRESETS["momentum_sport"] = [("Natural", [-2, 1, 0, 0.7, 0])] + EQ_PRESETS["mtw3"]
# Per model (the product file's name): which equalizer generations it has and with what. gen2: user-eq-2 (the earbuds answer UserEQ_GetConfiguration);
# gen1: the 5-band shelf EQ of user-eq-1 (its parameters, and the firmware from which the app uses it: `Eq5Band_Gen1_MinFwVersion`); three_band: the older bass,
# mid and treble knobs (gain limit of `get3BandParametersForDevice`); presets: the set `EqPresetsService.getOriginalPresets` gives the product.
EQ_MODELS = {
    "ac1.json": {"gen2": True, "gen1": ["accentum", "2.12.0"], "three_band": 4, "presets": "accentum"},
    "acp1.json": {"gen2": True, "gen1": ["accentum", "2.12.0"], "three_band": 4, "presets": "accentum"},
    "atw1.json": {"gen2": True, "gen1": ["accentum_tw", "2.10.5"], "three_band": 4, "presets": "mtw3"},
    "cx200.json": {"gen2": False, "gen1": None, "three_band": 4, "presets": "default3"},
    "cx200sports.json": {"gen2": False, "gen1": None, "three_band": 0, "presets": "default3"},
    "cx400II.json": {"gen2": False, "gen1": None, "three_band": 4, "presets": "default3"},
    "cx500.json": {"gen2": False, "gen1": None, "three_band": 4, "presets": "default3"},
    "m4.json": {"gen2": True, "gen1": ["momentum4", "2.12.0"], "three_band": 2, "presets": "momentum4"},
    "msport1.json": {"gen2": True, "gen1": ["momentum_sport", "0.0.0"], "three_band": 4, "presets": "momentum_sport"},
    "tw3.json": {"gen2": False, "gen1": ["mtw3", "2.10.5"], "three_band": 4, "presets": "mtw3"},
    "tw4.json": {"gen2": True, "gen1": ["mtw4", "0.0.0"], "three_band": 4, "presets": "mtw3"},
}

# What each product's class in Smart Control's JavaScript sets up beyond its properties (docs/sennheiser/PROTOCOL.md section 4.2).
# sidetone_max: the highest level of `SidetoneState` the product's screen offers (`phoneCallsMaxValue`, or the CX models' OFF, MIN and MAX);
# transparency: "slider" (`ANC_Transparency`, 0 to 100, on the Accentum Plus and the Momentum 4) or "levels" (`TransparentHearing`: the app writes 1, 50 or 100).
# The Bluetooth LE characteristics Smart Control's `DeviceConfig` names for the products Android opens over LE (service, write, notify, the encrypted data one):
# `GAIA_BLE_*` (63331338, 39, 3A) or `GAIA3_BLE_*` (6333133B, 3C, 3D) on the service FDCE, or for the Momentum True Wireless 1 the GAIA service 63331337.
_BASE = "-23C1-11E5-B696-FEFF819CDC9F"
_FDCE = "0000FDCE-0000-1000-8000-00805F9B34FB"
BLE_GAIA = {"service": _FDCE, "write": "63331338" + _BASE, "notify": "63331339" + _BASE, "data": "6333133A" + _BASE, "pairing": "63331379" + _BASE}
BLE_GAIA3 = {"service": _FDCE, "write": "6333133B" + _BASE, "notify": "6333133C" + _BASE, "data": "6333133D" + _BASE, "pairing": "63331379" + _BASE}
BLE_MTW = {**BLE_GAIA, "service": "63331337" + _BASE}
BLE_V3 = {"cx200.json": BLE_GAIA, "cx200sports.json": BLE_GAIA, "cx400II.json": BLE_GAIA3, "cx500.json": BLE_GAIA3}
BLE_V2 = {"cx400.json": BLE_GAIA, "tw1.json": BLE_MTW, "tw2.json": BLE_GAIA}

EXTRAS = {
    "ac1.json": {"sidetone_max": 2, "transparency": None},
    "acp1.json": {"sidetone_max": 4, "transparency": "slider"},
    "atw1.json": {"sidetone_max": 2, "transparency": "levels"},
    "cx200.json": {"ble_only": True, "sidetone_max": 2, "transparency": None},
    "cx200sports.json": {"ble_only": True, "sidetone_max": 2, "transparency": None},
    "cx400II.json": {"ble_only": True, "sidetone_max": 2, "transparency": None},
    "cx500.json": {"ble_only": True, "sidetone_max": 2, "transparency": None},
    "m4.json": {"sidetone_max": 4, "transparency": "slider"},
    "msport1.json": {"sidetone_max": 2, "transparency": "levels"},
    "tw3.json": {"sidetone_max": None, "transparency": "levels"},
    "tw4.json": {"sidetone_max": 2, "transparency": "levels"},
}
AUTO_OFF_SECONDS = [0, 900, 1800, 3600]
TRANSPARENCY_LEVELS = [1, 50, 100]

# The touch and button functions (`O.S2`), the gestures (`O.m3`) and which buttons: the app's own numbers. A product's `touch` lists the functions its class
# offers (`availableActions`) with the gestures each is allowed on (`constrainedTo`; empty: any), and the firmware a function needs where the class says so.
# The Accentum, Accentum Plus and Momentum 4 offer none (their `availableActions` is empty: the app only shows their fixed gestures).
TOUCH_ACTIONS = {
    "none": 0, "play_pause": 1, "next_song": 2, "previous_song": 3, "volume_up": 4, "volume_down": 5, "transparent_hearing": 6, "voice_assistant": 7,
    "anc": 9, "toggle_lock": 13, "toggle_open_ear_tip": 16, "anc_th_toggle": 18, "toggle_bass_boost": 21, "sound_mode_toggle": 15,
}
TOUCH_PATTERNS = {"single": 1, "double": 2, "triple": 3, "long_press": 4}
TOUCH_SIDES = {"right": 0, "left": 1}
_RW = ["single", "double", "triple"]
_FE = ["double", "triple"]
_CX = [("none", []), ("play_pause", _RW), ("next_song", _RW), ("previous_song", _RW), ("voice_assistant", []), ("volume_up", ["long_press"]), ("volume_down", ["long_press"])]
TOUCH = {
    "atw1.json": [*_CX, ("anc", []), ("transparent_hearing", []), ("toggle_lock", ["triple", "long_press"])],
    "cx200.json": list(_CX),
    "cx200sports.json": [*_CX, ("toggle_open_ear_tip", [], "2.9.5"), ("toggle_lock", ["triple"], "2.9.5")],
    "cx400II.json": [*_CX, ("anc", []), ("transparent_hearing", [])],
    "cx500.json": [*_CX, ("anc", []), ("transparent_hearing", [])],
    "msport1.json": [("none", _FE), ("play_pause", _FE), ("previous_song", _FE), ("next_song", _FE), ("anc_th_toggle", _FE), ("toggle_lock", _FE),
                     ("toggle_bass_boost", _FE, "3.17.3"), ("sound_mode_toggle", _FE, "3.17.3"), ("voice_assistant", _FE), ("volume_up", ["long_press"]), ("volume_down", ["long_press"])],
    "tw3.json": [*_CX, ("anc", []), ("transparent_hearing", [])],
    "tw4.json": [*_CX, ("anc", []), ("transparent_hearing", []), ("toggle_lock", ["triple", "long_press"])],
}

# The commands of the shared feature files (assets/app/features): (file, app property) -> (PairFree id, read on opening). The ones that take a payload (a band's
# index, the parameters to query) are read when the equalizer asks, not when the channel opens.
FEATURE_PROPS = {
    ("audio-mode-2", "AudioMode"): ("audio_mode", True),
    ("user-eq-2", "UserEQ_GetConfiguration"): ("eq2_config", True),
    ("user-eq-2", "UserEQ_SetSliderGain"): ("eq2_slider", False),
    ("user-eq-2", "UserEQ_GetSliderGain"): ("eq2_slider", False),
    ("user-eq-2", "User_EQ_SubMode"): ("eq_submode", True),
    ("user-eq-1", "Sound_CustomEQKnobY"): ("eq1_knob", False),
    ("user-eq-1", "Sound_SetCustomEQ"): ("eq1_params", False),
    ("user-eq-1", "Sound_GetCustomEQ"): ("eq1_params", False),
    ("user-eq-1", "Sound_LevelShift"): ("eq1_level_shift", True),
}


# Commands that are not a plain read or write of a value (docs/sennheiser/PROTOCOL.md sections 4.4 to 4.8): (where it lives, the app's property, PairFree's id, how it is used).
# "get": a command whose answer (a reply, or an event with the same command number) is read; "set": a command that does something; "sides": a property with a read and a write.
# `where` is a product file (the same property on every product that has it) or a shared feature file of `assets/app/features`.
ACTIONS = [
    ("find-device-1", "FindDeviceRingSeparateDevice", "find_ring", "set"),
    ("find-device-1", "FindDeviceStopRinging", "find_stop", "set"),
    ("find-device-1", "FindDeviceIsDeviceRinging", "find_query", "get"),
    ("sound-personalization-1", "PersonalizedSound_Mode", "personalized_mode", "sides"),
    ("sound-personalization-1", "PersonalizedSound_Reset", "personalized_reset", "set"),
    ("product", "FitTestCheckPrecondition", "fit_precondition", "get"),
    ("product", "FitTestCheck", "fit_check", "get"),
    ("product", "PairedDevicesListSize", "peer_count", "get"),
    ("product", "PairedDevicesGetDeviceInfo", "peer_info", "get"),
    ("product", "PairedDevicesOwnDeviceIndex", "peer_own", "get"),
    ("product", "PairedDevicesConnectDevice", "peer_connect", "set"),
    ("product", "PairedDevicesDisconnectDevice", "peer_disconnect", "set"),
    ("product", "PairedDevicesDeleteEntry", "peer_delete", "set"),
    ("product", "PairedDevicesGetConnectionStatus", "peer_status", "get"),
    ("product", "Sensor_SensorData", "sensor_data", "get"),
    ("product", "Sensor_SetSensorEnable", "sensor_enable", "set"),
    ("product", "Sensor_GetSensorEnable", "sensor_state", "get"),
    ("product", "Reboot", "reboot", "set"),
    ("auracast-1", "Auracast_AddSource", "auracast_add", "set"),
    ("auracast-1", "Auracast_RemoveSource", "auracast_remove", "set"),
    ("auracast-1", "Auracast_SetBroadcastCode", "auracast_code", "set"),
    ("auracast-1", "Auracast_GetSourceIds", "auracast_ids", "sides"),
    ("auracast-1", "Auracast_GetReceiveState", "auracast_state", "get"),
    ("auracast-1", "Auracast_StartSourceScan", "auracast_scan", "set"),
    ("auracast-1", "Auracast_StopSourceScan", "auracast_scan_stop", "set"),
    ("auracast-1", "Auracast_ScanResultEvent", "auracast_result", "event"),
    ("auracast-1", "Auracast_StatusSourceScan", "auracast_scan_status", "sides"),
]
# Which product files carry a shared feature file (with the firmware its entry names), as the products' `features` say.
FEATURE_FILES = ("find-device-1", "sound-personalization-1", "sound-personalization-2", "audio-mode-1", "audio-mode-2", "auracast-1")


def invoke_block(prop):
    """The command and reply of a property written as an Invocation, whatever its name says."""
    inv = prop.get("Invocation", {})
    cmd = inv.get("Command")
    rsp = prop.get("Result", {}).get("Response")
    block = {"send": command(cmd)}
    if isinstance(rsp, dict) and "gaiaV3" in rsp:
        block["reply"] = command(rsp)
    return block


def invocation(prop):
    """A property written as an Invocation (a command with its result): its command and reply, as read or write by its name."""
    inv = prop.get("Invocation", {})
    cmd = inv.get("Command")
    rsp = prop.get("Result", {}).get("Response")
    if not isinstance(cmd, dict) or "gaiaV3" not in cmd:
        return None, None
    block = {"send": command(cmd)}
    if isinstance(rsp, dict) and "gaiaV3" in rsp:
        block["reply"] = command(rsp)
    return ("set" if "Set" in cmd["PI_command_name"] else "get"), block


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
            if "Invocation" in p:
                where, blk = invocation(p)
                if where:
                    entry[where] = blk
            if pid in commands:
                # The same property on another product (or the other half of a read and a write): the ids must agree, and a half adds to the entry.
                old = commands[pid]
                for key in ("get", "set"):
                    if key in entry and key in old:
                        assert old[key]["send"]["command"] == entry[key]["send"]["command"], (pid, fname, key)
                    elif key in entry:
                        old[key] = entry[key]
                if "values" in entry and "values" not in old:
                    old["values"] = entry["values"]
                continue
            commands[pid] = entry
    features = {name: {p["name"]: p for p in json.loads((assets / "features" / (name + ".json")).read_text(encoding="utf-8"))["properties"]} for name in ("audio-mode-2", "user-eq-1", "user-eq-2")}
    for (fname, app_name), (pid, on_open) in FEATURE_PROPS.items():
        p = features[fname][app_name]
        entry = commands.setdefault(pid, {"app_property": app_name, "open": on_open})
        for key in ("Get", "Set"):
            blk = side(p, key)
            if blk:
                entry["get" if key == "Get" else "set"] = blk
        if "Invocation" in p:
            where, blk = invocation(p)
            if where:
                entry[where] = blk
        if p.get("Value", {}).get("map"):
            entry["values"] = p["Value"]["map"]
    shared = {name: {p["name"]: p for p in json.loads((assets / "features" / (name + ".json")).read_text(encoding="utf-8"))["properties"]} for name in FEATURE_FILES}
    for where, app_name, pid, how in ACTIONS:
        sources = [shared[where]] if where != "product" else [products[f] for f in SC_MODELS]
        for src in sources:
            p = src.get(app_name)
            if not p:
                continue
            entry = {"app_property": app_name}
            if how == "sides":
                if p.get("Value", {}).get("map"):
                    entry["values"] = p["Value"]["map"]
                for key in ("Get", "Set"):
                    blk = side(p, key)
                    if blk:
                        entry["get" if key == "Get" else "set"] = blk
            elif "Invocation" in p:
                entry[how] = invoke_block(p)
            elif "Get" in p and "Event" in p["Get"]:
                # An event only: the command word is the notification's without its type bit.
                ev = p["Get"]["Event"]
                g = ev["gaiaV3"]
                word = format(int(g["GAIA_notification_ID"], 16) & ~0x80, "04X")
                entry["get"] = {"send": {"vendor": hexid(g["vendor_ID"]), "command": word, "params": []},
                                "reply": {"vendor": hexid(g["vendor_ID"]), "command": word, "notification": hexid(g["GAIA_notification_ID"]), "params": [[x["type"], x["name"]] for x in ev.get("parameter", [])]}}
            else:
                blk = side(p, "Get")
                if blk:
                    entry[how] = blk
            if pid in commands:
                old = commands[pid]
                for key in ("get", "set"):
                    if key in entry and key in old:
                        assert old[key]["send"]["command"] == entry[key]["send"]["command"], (pid, key)
                continue
            if how == "get" or "get" in entry or "set" in entry:
                commands[pid] = {**entry, "open": False}
    legacy = []
    for fname, (name, klass, names, transport, eq) in GAIA2_MODELS.items():
        d = json.loads((assets / fname).read_text(encoding="utf-8"))
        assert d["protocol"] == "GAIA", fname
        P = {p["name"]: p for p in d["properties"]}
        props = {}
        for app_name, pid in GAIA2_PROPS.items():
            if app_name not in P:
                continue
            entry = gaia2_product(P[app_name])
            if not entry.get("get") and not entry.get("set"):
                continue
            if pid in props:
                for key in ("get", "set"):
                    if key in entry and key not in props[pid]:
                        props[pid][key] = entry[key]
                continue
            props[pid] = entry
        legacy.append({"name": name, "kind": klass, "names": names, "transport": transport, "ble": BLE_V2.get(fname), "source": "Smart Control 4.9.4 " + fname, "eq": eq and "eq_knob" in props, "properties": props})
    (OUT / "gaia2.json").write_text(json.dumps({
        "_about": "Sennheiser's GAIA v2 products as Smart Control 4.9.4's product files define them (docs/sennheiser/PROTOCOL.md section 4.9): a frame is FF, 01, flags, the payload's length, a vendor (0494 Sennheiser's, 000A Qualcomm's), a command and the payload; a response is the command + 8000 and starts with a status byte (0 is success). transport says how Android opens the product (spp: the serial port, ble: Bluetooth LE only). Written by tools/import_sennheiser_app.py.",
        "transport": {"spp_uuid": "a2129ff3-081b-4c45-8afe-469d9c4842eb", "vendor": "0494", "qualcomm_vendor": "000A"},
        "products": legacy,
    }, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    naut = []
    for fname, (name, klass, names) in NAUTILUS_MODELS.items():
        d = json.loads((assets / fname).read_text(encoding="utf-8"))
        assert d["protocol"] == "Nautilus", fname
        P = {p["name"]: p for p in d["properties"]}
        props = {}
        for app_name, pid in NAUTILUS_PROPS.items():
            if app_name not in P:
                continue
            prop = P[app_name]
            entry = {}
            if prop.get("Value", {}).get("map"):
                entry["values"] = prop["Value"]["map"]
            for key, where in (("Get", "get"), ("Set", "set")):
                blk = prop.get(key)
                if blk:
                    got = nautilus_side(blk.get("Command"), blk.get("Response"))
                    if got:
                        entry[where] = got
            if "Invocation" in prop:
                got = nautilus_side(prop["Invocation"].get("Command"), prop.get("Result", {}).get("Response"))
                if got:
                    entry["set"] = got
            if entry.get("get") or entry.get("set"):
                props[pid] = entry
        naut.append({"name": name, "kind": klass, "names": names, "source": "Smart Control 4.9.4 " + fname, "properties": props})
    (OUT / "nautilus.json").write_text(json.dumps({
        "_about": "Sennheiser's Nautilus headphones (Momentum 3, PXC 550, PXC 550-II) as Smart Control 4.9.4's product files define them (docs/sennheiser/PROTOCOL.md section 4.10): a packet is EB FE 01 00 01 00 00 00, the length of what follows the checksum (two bytes, little-endian), a CRC-8 (polynomial 07, initial FF; two bytes, little-endian) and the header (module, optional submodule, command; each two bytes little-endian) with the parameters; on the serial port each packet is wrapped in 2A and 2A and the bytes 2A and 3F are escaped. Written by tools/import_sennheiser_app.py.",
        "transport": {"spp_uuid": "1ddce62a-ecb1-4455-8153-0743c87aec9f"},
        "products": naut,
    }, indent=1, ensure_ascii=False) + chr(10), encoding="utf-8")
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
        "commands": [{"id": pid, "open": commands[pid].get("open", pid not in NOT_ON_OPEN), **{k: v for k, v in commands[pid].items() if k != "open"}} for pid in dict.fromkeys(list(PROPS.values()) + [v[0] for v in FEATURE_PROPS.values()] + [a[2] for a in ACTIONS]) if pid in commands],
        "never_send": [
            {"app_property": "Service_FactoryReset", "vendor": "0495", "command": "0040", "why": "factory reset"},
            {"app_property": "Debug_ErasePanicLog", "why": "erases the earbuds' panic log"},
            {"app_property": "Upgrade_*", "why": "firmware update (the VMUP commands)"},
        ],
    }
    models = []
    for fname, (name, klass, names) in SC_MODELS.items():
        have = [pid for app_name, pid in PROPS.items() if app_name in products[fname]]
        touch = TOUCH.get(fname)
        ble = BLE_V3.get(fname)
        feats = {f["name"]: f.get("minVersion", "0.0.0") for f in json.loads((assets / fname).read_text(encoding="utf-8"))["features"]}
        models.append({"name": name, "kind": klass, "names": names, "source": "Smart Control 4.9.4 " + fname, "properties": have, "eq": EQ_MODELS[fname], **EXTRAS[fname],
                       "features": {**{k: v for k, v in feats.items() if k in FEATURE_FILES}, **({"reboot": "0.0.0"} if "Reboot" in products[fname] else {})}, "peers": "PairedDevicesListSize" in products[fname], "fit_test": "FitTestCheck" in products[fname], "sensors": "Sensor_SensorData" in products[fname],
                       "ble": ble,
                       "touch": None if touch is None else [{"action": a[0], "gestures": a[1], **({"min_firmware": a[2]} if len(a) > 2 else {})} for a in touch]})
    for name, klass, names, article in SCP_MODELS:
        models.append({"name": name, "kind": klass, "names": names, "article": article, "source": "Smart Control Plus 1.6.3 (cKd), no product file", "properties": None, "eq": None, "sidetone_max": None, "transparency": None, "touch": None, "ble": None, "features": {}, "peers": False, "fit_test": False, "sensors": False})
    OUT.mkdir(parents=True, exist_ok=True)
    eq = {
        "_about": "Sennheiser's equalizers as Smart Control 4.9.4 computes them (docs/sennheiser/PROTOCOL.md section 6): gen2 (the earbuds hold five band gains in tenths of a dB), gen1 (a five band shelf filter set the app calculates: bass boost, loudness weights, headroom, Q, band frequencies) and the older three-band bass, mid and treble; presets in dB per band. Written by tools/import_sennheiser_app.py.",
        "gen2": {"hz": EQ_GEN2_HZ, "range_db": 6},
        "gen1": {k: {"bass_boost": v[0], "loudness_weights": v[1], "max_headroom": v[2], "q": [round(q, 6) for q in v[3]], "band_frequencies": v[4], "hz": v[5], "range_db": 6} for k, v in EQ_GEN1.items()},
        "three_band": EQ_3BAND,
        "presets": {k: [{"name": n, "gains": g} for n, g in v] for k, v in EQ_PRESETS.items()},
    }
    (OUT / "touch.json").write_text(json.dumps({
        "_about": "Sennheiser's touch and button configuration as Smart Control 4.9.4 uses it (docs/sennheiser/PROTOCOL.md section 4.3): the numbers of the functions, gestures and buttons, the gestures it reads from each earbud, and the auto power-off and transparency choices. Each model's functions are in devices.json. Written by tools/import_sennheiser_app.py.",
        "actions": TOUCH_ACTIONS,
        "gestures": TOUCH_PATTERNS,
        "buttons": TOUCH_SIDES,
        "auto_off_seconds": AUTO_OFF_SECONDS,
        "transparency_levels": TRANSPARENCY_LEVELS,
    }, indent=1) + "\n", encoding="utf-8")
    (OUT / "eq.json").write_text(json.dumps(eq, indent=1) + "\n", encoding="utf-8")
    (OUT / "commands.json").write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    (OUT / "devices.json").write_text(json.dumps({
        "_about": "Sennheiser models PairFree controls, all on Qualcomm's GAIA v3 (docs/sennheiser/PROTOCOL.md). names are the Bluetooth names Smart Control maps to the model; properties lists the PairFree properties (commands.json ids) the app's own product file has, or null for a model with no product file in the apps: PairFree reads each property and shows what answers. reports lists device reports.",
        "models": models,
    }, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(data["commands"]), "commands,", len(models), "models")


if __name__ == "__main__":
    main(sys.argv[1])
