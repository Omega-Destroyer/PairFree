#!/usr/bin/env python3
"""
Read-only probe for SoundPEATS earbuds (written for the H3).

It opens the earbuds' Bluetooth serial (SPP / RFCOMM) channel, sends every
known GET command from the PeatsAudio app, and prints what comes back.
Nothing here changes a setting. Use --set only when you mean to.

Pair the earbuds with the PC first, and close PeatsAudio on your phone so it
is not holding the serial channel.

Usage
  Windows, via the COM port Windows creates for the earbuds (needs pyserial):
      python probe.py --port COM5
  Windows or Linux, straight to the Bluetooth address (no extra packages):
      python probe.py --mac AA:BB:CC:DD:EE:FF
      python probe.py --mac AA:BB:CC:DD:EE:FF --channel 7
  Send one SET command (hex command, hex payload):
      python probe.py --mac ... --set 0311 02
"""
import argparse
import socket
import sys
import time

VENDOR_MAIN = 0x000A   # everything except EQ
VENDOR_EQ = 0x001D     # EQ band writes

# name: (get command, set command or None, note on the reply/payload)
COMMANDS = {
    "firmware_version":   (0x0309, None,   "ASCII, CODE_x_VERSION"),
    "battery_left":       (0x0306, None,   "percent"),
    "battery_right":      (0x0307, None,   "percent"),
    "battery_case":       (0x0323, None,   "percent"),
    "anc_mode":           (0x0310, 0x0311, "0 off, 1 ANC, 2 transparency, 3 adaptive, 4 weak"),
    "anc_level":          (0x0324, 0x0325, "pairs of (level, active); 0x12 indoor, 0x13 outdoor, 0x14 traffic, 0x15 adaptive, 0x16 wind"),
    "transparency_level": (0x034C, 0x034D, "pairs of (level, active); 1 voice, 2 standard"),
    "anc_env_adaptive":   (0x0364, 0x0365, "0 off, 1 on"),
    "game_mode":          (0x030E, 0x030F, "0 off, 1 on"),
    "multipoint":         (0x0314, 0x0315, "0 off, 1 on"),
    "ldac":               (0x0338, 0x0339, "0 off, 1 on"),
    "le_audio":           (0x0335, 0x0334, "0 off, 1 on"),
    "in_ear_detection":   (0x030C, 0x030D, "0 off, 1 on"),
    "touch_disabled":     (0x0312, 0x0313, "0 off, 1 on"),
    "custom_keys":        (0x03AA, 0x03AB, "pairs of (gesture, action)"),
    "prompt_volume":      (0x0319, 0x0320, "0-100"),
    "auto_shutdown":      (0x036E, 0x036F, "minutes; 0 = never"),
    "voice_language":     (0x0321, 0x0322, "0 en, 1 zh, 2 de, 3 ja, 4 fr, 5 es, 6 it"),
    "dynamic_eq":         (0x0331, 0x0332, "0 off, 1 on"),
    "dynamic_bass":       (0x033C, 0x033D, "0 off, 1 on"),
    "movie_mode":         (0x0327, 0x0328, "0 off, 1 on"),
    "spatial_audio":      (0x0357, 0x0358, "0 off, 1 on"),
    "panoramic_sound":    (0x0329, 0x0330, "0 normal, 1 music, 2 movie"),
    "dolby":              (0x0348, 0x0349, "0 off, 1 on"),
    "adaptive_volume":    (0x0346, 0x0347, "0 off, 1 on"),
    "personal_volume":    (0x0340, 0x0341, "0 off, 1 on"),
    "light_effect":       (0x030A, 0x030B, "0 off, 1 on"),
    "privacy_mode":       (0x036B, 0x036C, "0 off, 1 on"),
    "reset_state":        (0x0367, None,   "1 = was just factory reset"),
}

GESTURES = {1: "tap L", 2: "tap R", 3: "double L", 4: "double R",
            5: "triple L", 6: "triple R", 7: "hold L", 8: "hold R"}
ACTIONS = {0: "none", 1: "vol+", 2: "vol-", 3: "play/pause", 4: "game mode",
           5: "prev", 6: "next", 7: "ANC", 8: "assistant"}


def build(command, payload=b"", vendor=VENDOR_MAIN):
    """FF 04 00 LEN VENDOR(2) COMMAND(2) PAYLOAD. No checksum."""
    if len(payload) > 255:
        raise ValueError("payload too long")
    return (bytes([0xFF, 0x04, 0x00, len(payload)])
            + vendor.to_bytes(2, "big") + command.to_bytes(2, "big") + payload)


def eq_band(total, index, freq_hz, gain_db, q, master_gain_db=0.0):
    """One EQ band write (send one per band, index starting at 1).
    Send with command 0x0E01 and vendor VENDOR_EQ."""
    def s16(v):
        return (int(v) & 0xFFFF).to_bytes(2, "big")
    head = bytes([((total & 0xF) << 4) | (index & 0xF)])
    return head + s16(master_gain_db * 60) + s16(freq_hz * 3) + s16(gain_db * 60) + s16(q * 4096)


def parse_stream(buf):
    """Split a byte buffer into (vendor, command, payload) frames.
    Returns (frames, leftover)."""
    frames = []
    while True:
        start = buf.find(b"\xff")
        if start < 0:
            return frames, b""
        buf = buf[start:]
        if len(buf) < 8:
            return frames, buf
        n = buf[3]
        if len(buf) < 8 + n:
            return frames, buf
        frames.append((int.from_bytes(buf[4:6], "big"),
                       int.from_bytes(buf[6:8], "big"), buf[8:8 + n]))
        buf = buf[8 + n:]


class SerialLink:
    def __init__(self, port):
        import serial  # pip install pyserial
        self.s = serial.Serial(port, 115200, timeout=0.2)

    def write(self, data):
        self.s.write(data)

    def read(self):
        return self.s.read(256)

    def close(self):
        self.s.close()


class RfcommLink:
    def __init__(self, mac, channel):
        self.s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        self.s.settimeout(6)
        self.s.connect((mac, channel))
        self.s.settimeout(0.2)

    def write(self, data):
        self.s.sendall(data)

    def read(self):
        try:
            return self.s.recv(256)
        except (socket.timeout, TimeoutError):
            return b""

    def close(self):
        self.s.close()


def exchange(link, command, payload=b"", vendor=VENDOR_MAIN, wait=0.8):
    """Send one frame, collect every frame that arrives within `wait` seconds."""
    link.write(build(command, payload, vendor))
    buf, frames, end = b"", [], time.time() + wait
    while time.time() < end:
        buf += link.read()
        got, buf = parse_stream(buf)
        frames += got
        if any(c == (command | 0x8000) for _, c, _ in frames):
            break
    return frames


def find_channel(mac):
    """Try RFCOMM channels until one answers the firmware-version query."""
    for ch in range(1, 31):
        try:
            link = RfcommLink(mac, ch)
        except OSError:
            continue
        try:
            if any(c == 0x8309 for _, c, _ in exchange(link, 0x0309, wait=1.5)):
                print(f"channel {ch} answers")
                return link
        except OSError:
            pass
        link.close()
    return None


def describe(name, payload):
    if not payload:
        return "(empty)"
    status, body = payload[0], payload[1:]
    if name == "firmware_version":
        text = (body if status == 0 else payload).decode("utf-8", "replace")
        return repr(text)
    if status != 0 and len(payload) <= 2:
        return f"status {status} (not supported or error)"
    if name == "custom_keys":
        pairs = [(body[i], body[i + 1]) for i in range(0, len(body) - 1, 2)]
        return ", ".join(f"{GESTURES.get(g, g)} -> {ACTIONS.get(a, a)}" for g, a in pairs)
    if name in ("anc_level", "transparency_level"):
        # the app drops a leading status byte only when the length is odd
        body = body if (len(payload) % 2 == 1 and status == 0) else payload
        pairs = [(body[i], body[i + 1]) for i in range(0, len(body) - 1, 2)]
        return ", ".join(f"0x{lv:02X}{'*' if on == 1 else ''}" for lv, on in pairs) + "   (* = active)"
    if len(body) == 1:
        return str(body[0])
    return body.hex(" ")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", help="serial port, e.g. COM5 or /dev/rfcomm0")
    ap.add_argument("--mac", help="earbud Bluetooth address")
    ap.add_argument("--channel", type=int, help="RFCOMM channel (found automatically if omitted)")
    ap.add_argument("--set", nargs=2, metavar=("CMD", "PAYLOAD"), help="send one SET, both in hex")
    a = ap.parse_args()

    if a.port:
        link = SerialLink(a.port)
    elif a.mac and a.channel:
        link = RfcommLink(a.mac, a.channel)
    elif a.mac:
        link = find_channel(a.mac)
        if link is None:
            sys.exit("No RFCOMM channel answered. Is PeatsAudio still connected on the phone?")
    else:
        ap.error("give --port or --mac")

    try:
        if a.set:
            cmd, payload = int(a.set[0], 16), bytes.fromhex(a.set[1])
            print("sent ", build(cmd, payload).hex(" "))
            for v, c, p in exchange(link, cmd, payload, wait=1.5):
                print(f"reply vendor={v:04X} cmd={c:04X} payload={p.hex(' ')}")
            return
        for name, (get, _set, note) in COMMANDS.items():
            frames = exchange(link, get)
            reply = next((p for _, c, p in frames if c == (get | 0x8000)), None)
            extra = [f"{c:04X}:{p.hex()}" for _, c, p in frames if c != (get | 0x8000)]
            if reply is None:
                line = "no reply"
            else:
                line = f"{describe(name, reply):<40} raw={reply.hex(' ')}"
            print(f"{name:<20} {get:04X}  {line}" + (f"   other={extra}" if extra else ""))
            time.sleep(0.05)
    finally:
        link.close()


if __name__ == "__main__":
    main()
