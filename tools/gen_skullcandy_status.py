"""Writes docs/skullcandy/STATUS.md: what PairFree does for each of the 56 Skullcandy products, from data/skullcandy/devices.json and the rules the Kotlin code applies
(`SkullcandyNames.kt`, `SkullcandyAtSession`, `SkullcandyRaceTalk`). Run after a change to either.

    python tools/gen_skullcandy_status.py [--check]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data" / "skullcandy"
OUT = ROOT / "docs" / "skullcandy" / "STATUS.md"

AT_SDK = {"GRIND", "GRIND_FUEL", "PUSH_ACTIVE", "CHP200"}
AT_LEVEL = {"GRIND", "GRIND_FUEL", "PUSH_ACTIVE"}


RPC_SDK = {"T99_PLUS", "T120", "CHP300"}
RAIL_SDK = {"T70", "T80_PLUS"}


def rail_rows(p, modes):
    f = set(p["features"])
    out = ["battery (both earbuds and the case)"]
    if "adjustableNoiseCancellation" in f:
        out.append("noise mode + strength")
    elif "adjustableStayAwareMode" in f:
        out.append("transparency + strength")
    if f & {"multipointPairingToggle", "multipointPairing"}:
        out.append("multipoint")
    if "customEqualizer" in f:
        presets = [m for m, n in modes.get(p["enum"], {}).items() if 1 <= n <= 99]
        out.append("equalizer (custom" + (f" + {len(presets)} presets" if presets else "") + ")")
    return out


def rpc_rows(p, modes):
    f = set(p["features"])
    out = ["battery"]
    if f & {"adjustableNoiseCancellation", "boseQuietControlANC", "adjustableStayAwareMode"}:
        out.append("noise mode + strength + profile")
    if "adjustableSidetoneMode" in f:
        out.append("sidetone")
    if "lowLatencyMode" in f:
        out.append("gaming mode")
    if "wearDetect" in f:
        out.append("wear detection (RPC)")
    if f & {"crusherTechnologyDial", "crusherTechnologyProfiles"}:
        out.append("Crusher intensity and mode")
    if f & {"boseTrueSpatialMotion", "boseTrueSpatialStill"}:
        out.append("spatial audio")
    out += ["voice prompts", "prompt language"]
    if f & {"multipointPairingToggle", "multipointPairing"}:
        out.append("multipoint (RACE)")
    if "customButtonActions" in f:
        out.append("button actions")
    if "boseEqualizer" in f:
        out.append("equalizer (the earbuds' own: presets and custom)")
    elif "customEqualizer" in f:
        presets = [m for m, n in modes.get(p["enum"], {}).items() if 1 <= n <= 99]
        out.append("equalizer (RACE: custom" + (f" + {len(presets)} presets" if presets else "") + ")")
    return out


def airoha_sdk_rows(p, modes):
    """The Airoha models without an SDK configuration: the Rail's RACE conversation without the Rail's own commands (SkullcandyRaceTalk, primary; issue #66)."""
    f = set(p["features"])
    out = ["battery (both earbuds and the case)"]
    if f & {"adjustableNoiseCancellation", "activeNoiseCancellation", "adjustableStayAwareMode", "stayAwareMode"}:
        out.append("noise mode")
    if f & {"adjustableNoiseCancellation", "adjustableStayAwareMode"}:
        out.append("noise cancelling and transparency strength")
    if f & {"lowLatencyMode", "gamingMode"}:
        out.append("gaming mode")
    if f & {"multipointPairingToggle", "multipointPairing"}:
        out.append("multipoint")
    if "wearDetect" in f:
        out.append("wear detection")
    if "crusherBassBoost" in f:
        out.append("bass boost")
    if "customEqualizer" in f:
        presets = [m for m, n in modes.get(p["enum"], {}).items() if 1 <= n <= 99]
        out.append("equalizer (custom" + (f" + {len(presets)} presets" if presets else "") + ")")
    if "customButtonActions" in f and p.get("default_buttons"):
        vendor = p["enum"] in {"iconANC", "aviatorANC", "hesh540ANC", "crusher720"}
        out.append("button actions (" + ("the app's own key map" if vendor else "Airoha's gesture API") + ")")
    if "voicePrompts" in f:
        out.append("voice prompts")
    if "adjustableSidetoneMode" in f:
        out.append("sidetone (state + level)")
    if "gameChatMix" in f:
        out.append("game and chat mix")
    if "adjustableMicGain" in f:
        out.append("microphone volume")
    if "capTouchControl" in f:
        out.append("touch controls")
    if "adaptiveANCMode" in f:
        out.append("adaptive noise cancelling")
    if "crusherTechnology" in f:
        out.append("Crusher intensity")
    return out


def qualcomm_rows(p, modes):
    f = set(p["features"])
    out = ["battery (both earbuds)"]
    if "ambientMode" in f:
        out.append("ambient mode")
    if "definedEqualizer" in f:
        out.append("equalizer (3 presets)")
    return out


def session(p):
    if p["chip"] == "qualcomm":
        return "C-Chip over Bluetooth LE"
    if p["sdk"] in RAIL_SDK:
        return "Airoha RACE on Airoha's service"
    if p["sdk"] in RPC_SDK:
        return "Bragi RPC (unsecured)"
    if p["chip"] == "bes":
        return "BES TOTA (AES-128 handshake)"
    if p["chip"] == "airoha" and p["sdk"] in AT_SDK:
        return "AT on Skullcandy's serial service"
    if p["chip"] == "airoha" and p["sdk"] is None and not p["name"].startswith("Session "):
        return "Airoha RACE on Airoha's service (the SDK's own commands)"
    return None


def bes_rows(p, modes):
    f = set(p["features"])
    out = ["battery"]
    if f & {"adjustableNoiseCancellation", "activeNoiseCancellation", "stayAwareMode", "adjustableStayAwareMode"}:
        out.append("noise mode" + (" + level" if "advancedHearingModes" in f else ""))
    if f & {"definedEqualizer", "definedEqualizerToggle", "customEqualizer"}:
        presets = [m for m, n in modes.get(p["enum"], {}).items() if 1 <= n <= 99] if f & {"definedEqualizer", "definedEqualizerToggle"} else []
        out.append("equalizer (" + ("custom" if "customEqualizer" in f else "") + (" + " if presets and "customEqualizer" in f else "") + (f"{len(presets)} presets" if presets else "") + ")")
    if f & {"multipointPairingToggle", "multipointPairing"}:
        out.append("multipoint")
    if f & {"lowLatencyMode", "gamingMode"}:
        out.append("gaming mode")
    if "adjustableSidetoneMode" in f:
        out.append("sidetone")
    if "capTouchControl" in f:
        out.append("touch controls")
    if "customButtonActions" in f:
        out.append("button actions")
    return out


def rows(p, modes):
    out = base_rows(p, modes)
    if "findWithTile" in p["features"] and out:
        out.append("Find with Tile (link)")
    if p["sdk"] == "CHP300":
        out.append("Auracast receiver (RACE)")
    return out


def base_rows(p, modes):
    if p["chip"] == "qualcomm":
        return qualcomm_rows(p, modes)
    if p["sdk"] in RAIL_SDK:
        return rail_rows(p, modes)
    if p["sdk"] in RPC_SDK:
        return rpc_rows(p, modes)
    if p["chip"] == "bes":
        return bes_rows(p, modes)
    if p["chip"] == "airoha" and p["sdk"] is None and session(p):
        return airoha_sdk_rows(p, modes)
    f = set(p["features"])
    out = []
    if session(p):
        out.append("battery")
        if f & {"adjustableNoiseCancellation", "activeNoiseCancellation", "adjustableStayAwareMode", "stayAwareMode", "ambientMode"}:
            out.append("noise mode")
        if p["sdk"] in AT_LEVEL and f & {"adjustableStayAwareMode", "stayAwareMode", "ambientMode"}:
            out.append("transparency level")
        if p["sdk"] == "CHP200":
            out.append("strength (RACE)")
        if f & {"lowLatencyMode", "gamingMode"}:
            out.append("gaming mode")
        if f & {"multipointPairingToggle", "multipointPairing"}:
            out.append("multipoint (RACE)")
        if "wearDetect" in f:
            out.append("wear detection (RACE)")
        if "crusherBassBoost" in f:
            out.append("bass boost (RACE)")
        if "customEqualizer" in f:
            presets = [m for m, n in modes.get(p["enum"], {}).items() if 1 <= n <= 99]
            out.append("equalizer (custom" + (f" + {len(presets)} presets" if presets else "") + ")")
    return out


def main():
    d = json.loads((DATA / "devices.json").read_text(encoding="utf-8"))
    modes = json.loads((DATA / "eq_modes.json").read_text(encoding="utf-8"))["modes"]
    lines = ["# Skullcandy: what PairFree does for each product", "",
             "Generated by `tools/gen_skullcandy_status.py` from `data/skullcandy/devices.json` and the rules in the Kotlin code; refresh it after a change. The brand is not released: "
             "the first release waits until every product and protocol is mapped (owner, 2026-11-18).", "",
             "| Product | Id | Chip | Session today | Rows |", "|---|---|---|---|---|"]
    counts = {}
    for p in d["products"]:
        s = session(p)
        r = rows(p, modes)
        if s:
            counts["AT"] = counts.get("AT", 0) + 1
        else:
            counts[p["chip"] if p["sdk"] is None or p["chip"] != "airoha" else "airoha (SDK, other protocol)"] = counts.get(p["chip"] if p["sdk"] is None or p["chip"] != "airoha" else "airoha (SDK, other protocol)", 0) + 1
        lines.append(f"| {p['name']} | {p['id']} | {p['chip']}{' (SDK ' + p['sdk'] + ')' if p['sdk'] else ''} | {s or 'found only (no session yet)'} | {', '.join(r) or ''} |")
    lines += ["", "Not covered yet, by group:", "",
              "- **Airoha with an SDK configuration on another protocol**: the Rail and Rail ANC are built over Airoha RACE on Airoha's own service, the Method 360 and 540 ANC and the Crusher 1080 ANC over Bragi's protobuf RPC, unsecured. Their button actions are built too.",
              "- **BES** (Icon 180, Dime 540 ANC, Smokin' Buds 360 H and S, Push 180 Open): BES's TOTA protocol, built (`SkullcandyBesTalk`): every setting the app offers the five models is built, the buttons included.",
              "- **Qualcomm Bluetooth LE** (Crusher ANC, Crusher Evo, Indy Fuel, Indy Evo, Push Ultra, Crusher 540 Active): built over the app's own C-Chip frames (battery, the Indy models' ambient mode, three equaliser presets). Four of them share a name with an Airoha twin, so the classic channel is tried first and the LE one when it fails.",
              "- **Speakers**: the four Session speakers are found and held back (speakers wait, owner 2026-10-30).",
              "- **JieLi, Actions**: the app shows no setting for these (Smokin' Buds 3, the three Terrain speakers).", ""]
    text = "\n".join(lines) + "\n"
    if "--check" in sys.argv:
        sys.exit(0 if OUT.exists() and OUT.read_text(encoding="utf-8") == text else "docs/skullcandy/STATUS.md is out of date")
    OUT.write_text(text, encoding="utf-8")
    print(OUT, len(d["products"]), "products;", counts)


if __name__ == "__main__":
    main()
