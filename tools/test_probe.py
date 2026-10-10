#!/usr/bin/env python3
"""Tests for tools/probe.py. No hardware needed.

Run from the repo root:  python -m unittest discover -s tools -v
"""
import json
import pathlib
import socket
import sys
import threading
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import probe  # noqa: E402

DATA = HERE.parent / "data"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class Vectors(unittest.TestCase):
    """The probe's codec must reproduce data/test_vectors.json byte for byte."""

    def setUp(self):
        self.v = load("test_vectors.json")

    def test_build(self):
        for case in self.v["build"]:
            frame = probe.build(int(case["command"], 16), bytes.fromhex(case["payload"]),
                                int(case["vendor"], 16))
            self.assertEqual(frame.hex(), case["frame"], case["name"])

    def test_parse(self):
        for case in self.v["parse"]:
            frames, rest = probe.parse_stream(bytes.fromhex(case["stream"]))
            got = [{"vendor": f"{v:04X}", "command": f"{c:04X}", "payload": p.hex()} for v, c, p in frames]
            self.assertEqual(got, case["frames"], case["name"])
            self.assertEqual(rest.hex(), case["leftover"], case["name"])

    def test_eq_band(self):
        for case in self.v["eq_band_0E01"]:
            got = probe.eq_band(case["count"], case["index"], case["freq_hz"], case["gain_db"],
                                case["q"], case["master_gain_db"])
            self.assertEqual(got.hex(), case["payload"])

    def test_eq_layout_matches_the_official_app(self):
        models = {d["firmware_code"]: d for d in load("devices.json")["devices"]}
        for case in self.v["eq_layout"]:
            eq = models[case["model"]]["eq"]
            bands = eq["bands"]
            got = []
            for i, (band, user) in enumerate(zip(bands, case["user_gains_db"])):
                # added in tenths of a dB, as the Kotlin EqLayout does, so the sums stay exact
                gain = (round(band["base_gain_db"] * 10) + user * 10) / 10
                got.append(probe.eq_band(len(bands), i + 1, band["hz"], gain, band["q"], eq["master_gain_db"]).hex())
            self.assertEqual(got, case["payloads"], case["name"])

    def test_eq_presets_match_the_official_app(self):
        models = {d["firmware_code"]: d for d in load("devices.json")["devices"]}
        for case in self.v["eq_preset"]:
            eq = models[case["model"]]["eq"]
            preset = next(p for p in eq["presets"] if p["name"] == case["preset"])
            bands = preset["bands"]
            got = [probe.eq_band(len(bands), i + 1, b["hz"], b["gain_db"], b["q"], eq["master_gain_db"]).hex()
                   for i, b in enumerate(bands)]
            self.assertEqual(got, case["payloads"], case["preset"])

    def test_ble_packet_is_frame_without_header(self):
        for case in self.v["ble_qualcomm"]:
            self.assertEqual(case["frame"][8:], case["ble_packet"], case["name"])


class CommandTable(unittest.TestCase):
    """The probe keeps its own copy of the table so it stays a single file.
    It must not drift from data/commands.json."""

    def test_probe_matches_commands_json(self):
        table = {c["id"]: c for c in load("commands.json")["commands"]}
        for name, (get, st, _note) in probe.COMMANDS.items():
            self.assertIn(name, table)
            self.assertEqual(f"{get:04X}", table[name]["get"], name)
            if st is not None:
                self.assertEqual(f"{st:04X}", table[name]["set"], name)

    def test_probe_only_reads(self):
        sets = {c["set"] for c in load("commands.json")["commands"] if "set" in c}
        for name, (get, _st, _note) in probe.COMMANDS.items():
            self.assertNotIn(f"{get:04X}", sets, f"{name}: GET number is a SET in commands.json")

    def test_ids_and_numbers_unique(self):
        cmds = load("commands.json")["commands"]
        self.assertEqual(len({c["id"] for c in cmds}), len(cmds))
        gets = [c["get"] for c in cmds if "get" in c]
        self.assertEqual(len(set(gets)), len(gets))

    def test_device_rules_name_known_switches(self):
        table = {c["id"]: c for c in load("commands.json")["commands"]}
        for device in load("devices.json")["devices"]:
            for name in device.get("ldac_excludes", []):
                self.assertIn(name, table, device["firmware_code"])
                self.assertIn("set", table[name], device["firmware_code"])


class DeviceArt(unittest.TestCase):
    """Every traced drawing belongs to a model in data/devices.json, and says where it came from."""

    def test_art_files(self):
        names = {n.lower() for d in load("devices.json")["devices"] for n in d["names"]}
        files = sorted((HERE / "device_art").glob("*.json"))
        self.assertTrue(files)
        for f in files:
            art = json.loads(f.read_text(encoding="utf-8"))
            self.assertTrue(art["source"], f.name)
            for n in art["names"]:
                self.assertIn(n.lower(), names, f.name)
            for part in ("bud", "case"):
                value = art[part]
                self.assertTrue(value is None or value == "generic" or isinstance(value, dict), f.name)
            if isinstance(art["bud"], dict):
                self.assertIn(art["bud"].get("kind", "bud"), ("bud", "hook", "clip", "headphones"), f.name)


class Describe(unittest.TestCase):
    def test_version(self):
        self.assertEqual(probe.describe("firmware_version", b"\x00S99_xx_1.2.3"), "'S99_xx_1.2.3'")

    def test_switch_and_error(self):
        self.assertEqual(probe.describe("game_mode", b"\x00\x01"), "1")
        self.assertIn("status 1", probe.describe("game_mode", b"\x01\x00"))

    def test_keys(self):
        out = probe.describe("custom_keys", bytes([0, 3, 6, 7, 7]))
        self.assertEqual(out, "double L -> next, hold L -> ANC")

    def test_levels_with_and_without_status_byte(self):
        with_status = probe.describe("anc_level", bytes([0, 0x12, 0, 0x13, 1]))
        without = probe.describe("anc_level", bytes([0x12, 0, 0x13, 1]))
        self.assertTrue(with_status.startswith("0x12, 0x13*"))
        self.assertTrue(without.startswith("0x12, 0x13*"))


class Loopback(unittest.TestCase):
    """Drive exchange() against a fake earbud on a socket pair."""

    def setUp(self):
        self.a, self.b = socket.socketpair()
        threading.Thread(target=self._fake, daemon=True).start()

    def tearDown(self):
        self.a.close()
        self.b.close()

    def _fake(self):
        buf = b""
        while True:
            try:
                data = self.b.recv(256)
            except OSError:
                return
            if not data:
                return
            buf += data
            frames, buf = probe.parse_stream(buf)
            for _vendor, cmd, _payload in frames:
                if cmd == 0x0309:  # reply split across two writes
                    reply = probe.build(0x8309, b"\x00S99_xx_1.2.3")
                    self.b.sendall(reply[:5])
                    self.b.sendall(reply[5:])
                elif cmd == 0x0306:  # unsolicited frame ahead of the reply
                    self.b.sendall(probe.build(0x8367, b"\x00\x00") + probe.build(0x8306, b"\x00\x55"))
                # anything else: stay silent, like an unsupported command

    def link(self):
        a = self.a

        class Link:
            def write(self, data):
                a.sendall(data)

            def read(self):
                a.settimeout(0.1)
                try:
                    return a.recv(256)
                except (socket.timeout, TimeoutError):
                    return b""
        return Link()

    def test_split_reply_is_reassembled(self):
        self.assertEqual(probe.exchange(self.link(), 0x0309), [(0x000A, 0x8309, b"\x00S99_xx_1.2.3")])

    def test_unsolicited_frame_is_kept_separate(self):
        frames = probe.exchange(self.link(), 0x0306)
        self.assertIn((0x000A, 0x8306, b"\x00\x55"), frames)
        self.assertEqual(len(frames), 2)

    def test_no_reply_times_out_empty(self):
        self.assertEqual(probe.exchange(self.link(), 0x0310, wait=0.3), [])


if __name__ == "__main__":
    unittest.main()
