"""Turn an EQ capture report from the app into a model's EQ data.

usage: python tools/import_eq_capture.py report.txt > eq.json

The report is the text the app's "Record from PeatsAudio" screen produces (paste it from the
GitHub issue into a file). Its settings sections are for tools/gen_models_table.py; this reads
only the equalizer section, where each named curve lists the exact payload of every band write. This decodes them, checks every value re-encodes to the same bytes, and prints:

  - "firmware": the firmware version in the report, if any
  - "eq": the model's eq entry for data/devices.json. The base curve is the write named
    SOUNDPEATS Classic, which on every model checked so far is also what Reset sends.
  - "eq_layout" and "eq_preset": cases for data/test_vectors.json

It writes nothing. Review the output, then add it to the model's entry by hand. The user
slider range is not in a capture; it is set to 6 dB, as on every model checked so far.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from probe import eq_band  # noqa: E402

CLASSIC = "soundpeats classic"
USER_RANGE_DB = 6

SECTION = re.compile(r"^\s*(\d+)\.\s+(.+?)\s*$")
# One band's payload per line, or in a compact report all of a curve's on one line.
_HEX = r"(?:[0-9a-fA-F]{18}|[0-9a-fA-F]{20})"
PAYLOAD = re.compile(rf"^\s*({_HEX}(?:\s+{_HEX})*)\s*$")
FIRMWARE = re.compile(r"^\s*Firmware:\s+(\S.*?)\s*$")


def parse(text):
    """Returns (firmware or None, [(name, [payload bytes])]) in report order."""
    firmware, writes = None, []
    for line in text.splitlines():
        if (m := FIRMWARE.match(line)) and firmware is None:
            value = m.group(1)
            firmware = None if value.startswith("not in") else value.split(",")[0].strip()
        elif m := SECTION.match(line):
            writes.append((m.group(2), []))
        elif (m := PAYLOAD.match(line)) and writes:
            writes[-1][1].extend(bytes.fromhex(token) for token in m.group(1).split())
    return firmware, [(name, payloads) for name, payloads in writes if payloads]


def s16(b):
    return int.from_bytes(b, "big", signed=True)


def u16(b):
    return int.from_bytes(b, "big")


def command_of(payloads):
    sizes = {len(p) for p in payloads}
    if sizes == {9}:
        return "0E01"
    if sizes == {10}:
        return "0E02"
    raise ValueError(f"band payloads of mixed or unknown size: {sorted(sizes)}")


def position(command, p):
    return (p[0] >> 4, p[0] & 0x0F) if command == "0E01" else (p[0], p[1])


def values(command, p):
    at = 1 if command == "0E01" else 2
    return s16(p[at:at + 2]), u16(p[at + 2:at + 4]), s16(p[at + 4:at + 6]), u16(p[at + 6:at + 8])


def nicest(raw, scale, digits=4):
    """The shortest decimal that encodes back to raw (scaled and truncated, as the app does)."""
    for d in range(0, digits + 1):
        v = round(raw / scale, d)
        if int(v * scale) == raw:
            return float(v)
    raise ValueError(f"no decimal up to {digits} digits encodes to {raw} at scale {scale}")


def decode(name, command, payloads):
    count = position(command, payloads[0])[0]
    if [position(command, p) for p in payloads] != [(count, i) for i in range(1, count + 1)]:
        raise ValueError(f"{name}: bands are not 1..{count} in order")
    masters = {values(command, p)[0] for p in payloads}
    if len(masters) != 1:
        raise ValueError(f"{name}: master gain differs between bands")
    master = nicest(masters.pop(), 60, 2)
    bands = []
    for p in payloads:
        _, hz3, gain, q = values(command, p)
        hz = hz3 / 3
        bands.append({"hz": int(hz) if hz.is_integer() else round(hz, 2),
                      "q": nicest(q, 4096), "gain_db": nicest(gain, 60, 2)})
    return master, bands


def encode(command, bands, master):
    """The payloads the app's EqLayout would send for these bands."""
    out = []
    for i, b in enumerate(bands, start=1):
        p = eq_band(len(bands), i, b["hz"], b["gain_db"], b["q"], master)
        out.append(p if command == "0E01" else bytes([len(bands), i]) + p[1:])
    return out


def build(text):
    firmware, writes = parse(text)
    if not writes:
        raise ValueError("no curves in the report")
    command = command_of([p for _, ps in writes for p in ps])
    decoded = []
    for name, payloads in writes:
        master, bands = decode(name, command, payloads)
        if [p.hex() for p in encode(command, bands, master)] != [p.hex() for p in payloads]:
            raise ValueError(f"{name}: decoded values do not encode back to the same bytes")
        decoded.append((name, master, bands, payloads))
    masters = {m for _, m, _, _ in decoded}
    if len(masters) != 1:
        raise ValueError(f"master gain differs between curves: {sorted(masters)}")
    master = masters.pop()
    code = firmware.split("_")[0] if firmware else None

    base = next((d for d in decoded if d[0].strip().lower() == CLASSIC), None)
    eq = {"command": command, "master_gain_db": master, "user_range_db": USER_RANGE_DB}
    eq_layout = []
    if base is not None:
        eq["bands"] = [{"hz": b["hz"], "q": b["q"], "base_gain_db": b["gain_db"]} for b in base[2]]
        eq_layout.append({"name": "Reset: the base curve alone", "model": code,
                          "user_gains_db": [0] * len(base[2]), "payloads": [p.hex() for p in base[3]]})
    presets, eq_preset, seen = [], [], set()
    for name, _, bands, payloads in decoded:
        if name.strip().lower() == CLASSIC or name in seen:
            continue
        seen.add(name)
        presets.append({"name": name, "bands": bands})
        eq_preset.append({"model": code, "preset": name, "payloads": [p.hex() for p in payloads]})
    eq["presets"] = presets
    return {"firmware": firmware, "eq": eq, "eq_layout": eq_layout, "eq_preset": eq_preset,
            "missing": [] if base is not None else ["SOUNDPEATS Classic (the base curve)"]}


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    result = build(Path(sys.argv[1]).read_text(encoding="utf-8"))
    print(json.dumps(result, indent=1))
    if result["missing"]:
        print("missing: " + ", ".join(result["missing"]), file=sys.stderr)


if __name__ == "__main__":
    main()
