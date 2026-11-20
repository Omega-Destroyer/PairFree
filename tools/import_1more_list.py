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
# The light's mode and colour: HeadsetActivity shows them for this name alone.
LIGHTS = {"1MORE NANO"}
# The switch that turns the touch controls off (0074): 1MORE Life 5.0.3 shows it on the ComfoBuds Mini above firmware 1048.
TOUCH_LOCK = {"1MORE ComfoBuds Mini"}
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

# Models that 1MORE Life 5.0.3 (com.onemore.life, read 2026-11-19) names and the product list above does not hold. The
# list comes from the app's DeviceFactoryImpl (Bluetooth name to model), its per-model classes (com.bitaxon.n2.d.a: which
# of the first requests it sends: autoplay 0063, game mode 006C, spatial audio 0083) and its home screen (fragment d: which
# listen mode buttons each model shows; the values are 0 off, 1 strong, 4 wind, 3 transparency, 2 mild) and its equalizer
# screens (preset_eq_content, the twelve presets, for ES303, EC302, S31, EF906 and the SonoFlows; eq_content, the 22 older ones sent as their index, for ES901, ES602 and EF606). A setting is offered
# only once its read answers, so a request the model ignores shows nothing. Not read from the app yet: touch controls, the
# equalizer of the models that use its older list (ES901, ES602, EF606), pictures.
CLASSIC = ["default", "bass_reducer_1", "bass_reducer_2", "bass_booster_1", "bass_booster_2", "acoustic_1", "acoustic_2", "classical_1", "classical_2",
           "podcast", "vocal_booster", "deep_1", "deep_2", "electronic_1", "electronic_2", "hiphop_1", "hiphop_2", "lounge_1", "lounge_2", "pop_1", "pop_2", "loud"]
LIFE = [
    # name, model, kind, listen modes, game mode, autoplay, spatial audio, equalizer (presets, custom)
    ("1MORE ComfoBuds 2", "ES303", "earbuds", [], True, True, True, (TWELVE, True)),
    ("1MORE ColorBuds 2", "ES602", "earbuds", ["off", "strong", "transparent"], False, True, True, (CLASSIC, False)),
    ("1MORE PistonBuds Pro", "EC302", "earbuds", ["off", "strong", "wind", "transparent"], True, True, True, (TWELVE, True)),
    ("1MORE ComfoBuds Pro", "ES901", "earbuds", ["off", "strong", "mild", "wind", "transparent"], False, True, True, (CLASSIC, False)),
    ("1MORE SonoFlow", "HC905", "headphones", ["off", "strong", "transparent"], False, False, False, (TWELVE, False)),
    ("1MORE SonoFlow SE", "HC306", "headphones", ["off", "strong", "transparent"], False, False, False, (TWELVE, False)),
    ("1MORE Fit", "EF906", "earbuds", [], True, True, True, (TWELVE, False)),
    ("1MORE Fit SE", "EF606", "earbuds", [], False, False, True, (CLASSIC, False)),
    ("1MORE S31", "S31", "earbuds", [], False, False, True, (TWELVE, False)),
]


# Touch: Life opens OperationSettingActivity (a tap for each earbud: the four gestures of command 0084, the actions 0 none,
# 1 next, 2 previous, 3 volume down, 4 volume up, 5 play/pause, 6 voice control) for the models of its list K (ES903, EF606, EF906,
# EC305, S31, S51, S70), which hides the last action on S31, S51 and S70; ES901OperationCustomActivity and OperationCustomActivity
# (a tap for both earbuds: commands 0064 and 0066) for the others.
LIFE_TOUCH = {"ES303": ("both", True), "ES602": ("both", True), "EC302": ("both", True), "ES901": ("both", True),
              "EF606": ("each", True), "EF906": ("each", True), "S31": ("each", False)}


def life_device(name, model, kind, modes, game, autoplay, spatial, eq):
    touch, assistant = LIFE_TOUCH.get(model, (None, False))
    return {
        "name": name, "model": model, "tuya": False, "kind": kind, "case": kind == "earbuds", "listen_modes": modes,
        "connection": ["game"] if game else [], "autoplay": autoplay, "spatial_audio": spatial, "sleep_detection": False,
        "dual_device": False, "hot_word": False, "touch": touch, "touch_assistant": assistant,
        "eq": (["preset"] + (["custom"] if eq[1] else [])) if eq else [],
        "eq_presets": eq[0] if eq else [], "eq_classic": bool(eq) and eq[0] is CLASSIC, "eq_bands": DEFAULT_BANDS if eq and eq[1] else None,
        "ear_return": False, "volume_limit": False, "sleep_mode": False, "voice_enhancement": False, "lights": False,
        "touch_lock": False, "long_touch": ["strong", "mild", "wind"] if model == "ES901" else [],
        "pictures": [], "reports": [],
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
        # 1MORE's app 2.1.3: the music and sleep modes (HeadsetActivity: comfortableMelody), and the Q50's voice enhancement switch (ShortcutActionsActivity hides it on any other name).
        "sleep_mode": on(r, "comfortableMelody"),
        "voice_enhancement": name == "1MORE Q50",
        "lights": name in LIGHTS,
        "eq_classic": False,
        "touch_lock": name in TOUCH_LOCK,
        "long_touch": [],
        "pictures": pictures(r),
        "reports": [],
    }


def main(dump_path, out_path=OUT):
    rows = json.load(open(dump_path, encoding="utf-8"))["rows"]
    devices = sorted((device(r) for r in rows if (r.get("headphoneName") or "").startswith("1MORE")), key=lambda d: d["name"].lower())
    have = {d["name"].lower() for d in devices}
    devices += [life_device(*m) for m in LIFE if m[0].lower() not in have]
    devices.sort(key=lambda d: d["name"].lower())
    if Path(out_path).exists():
        old = {d["name"]: d for d in json.loads(Path(out_path).read_text(encoding="utf-8"))["devices"]}
        for d in devices:
            d["reports"] = old.get(d["name"], {}).get("reports", [])
    data = {
        "_about": "1MORE's models (docs/1more/PROTOCOL.md section 5): one entry per Bluetooth name, as 1MORE's app matches them, with the "
                  "controls its product list gives each model. The list is 1MORE's own (system/headphone/list, which 1MORE's app 2.0.0 "
                  "downloads), fetched once by hand on 2026-10-18 with the owner's OK; PairFree never contacts 1MORE. listen_modes uses "
                  "data/1more/commands.json's names; tuya, kind, case, touch_assistant, ear_return, volume_limit, lights, eq_presets and eq_bands come from 1MORE's app code; tuya marks "
                  "the two models 1MORE's app reaches through Tuya's platform instead, which PairFree does not control. "
                  "The models of the LIFE list in tools/import_1more_list.py come from 1MORE Life 5.0.3's code instead (no product list entry, no pictures). "
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
