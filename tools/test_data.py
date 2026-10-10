#!/usr/bin/env python3
"""Checks on the data files that do not involve the probe. No hardware needed.

Run from the repo root:  python -m unittest discover -s tools -v
"""
import json
import pathlib
import struct
import unittest

DATA = pathlib.Path(__file__).resolve().parent.parent / "data"


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class ModelTable(unittest.TestCase):
    """data/devices.json must agree with the rules written down in data/commands.json."""

    def setUp(self):
        self.devices = load("devices.json")["devices"]
        self.eq = load("commands.json")["eq"]

    def test_codes_unique(self):
        codes = [d["firmware_code"] for d in self.devices]
        self.assertEqual(len(set(codes)), len(codes))

    def test_transport_follows_family(self):
        for d in self.devices:
            want = "qcy_ble" if d["family"] == "QCY-WUQI" else "spp"
            self.assertEqual(d["transport"], want, d["firmware_code"])

    def test_eq_command_follows_rule(self):
        for d in self.devices:
            if d["transport"] != "spp":
                self.assertIsNone(d["eq_command"], d["firmware_code"])
                continue
            old = d["family"] == "QUALCOMM" or d["old_eq_protocol"] is True
            want = self.eq["command_qualcomm"] if old else self.eq["command_other"]
            self.assertEqual(d["eq_command"], want, d["firmware_code"])

    def test_eq_merge_follows_rule(self):
        listed = set(self.eq["merge_onto_classic_codes"])
        for d in self.devices:
            onto = d["family"] == "QUALCOMM" or d["firmware_code"] in listed
            want = "onto_classic_bands" if onto else "append_after_classic"
            self.assertEqual(d["eq_merge"], want, d["firmware_code"])

    def test_custom_eq_band_count(self):
        for d in self.devices:
            want = len(self.eq["custom_bands_hz_bluetrum"] if d["family"] == "FOTA" else self.eq["custom_bands_hz"])
            self.assertEqual(d["custom_eq_bands"], want, d["firmware_code"])


class CommandTable(unittest.TestCase):
    def test_set_numbers_unique(self):
        sets = [c["set"] for c in load("commands.json")["commands"] if "set" in c]
        self.assertEqual(len(set(sets)), len(sets))

    def test_no_number_is_both_get_and_set(self):
        cmds = load("commands.json")["commands"]
        gets = {c["get"] for c in cmds if "get" in c}
        sets = {c["set"] for c in cmds if "set" in c}
        self.assertEqual(gets & sets, set())


def qcy_packet(items):
    """FF, length of the rest, then id, value length, value for each item."""
    body = b""
    for item in items:
        value = bytes.fromhex(item["value"])
        body += bytes.fromhex(item["id"]) + bytes([len(value)]) + value
    return b"\xff" + bytes([len(body)]) + body


def qcy_parse(packet):
    assert len(packet) == packet[1] + 2
    items, i = [], 2
    while i < len(packet):
        n = packet[i + 1]
        items.append({"id": f"{packet[i]:02X}", "value": packet[i + 2:i + 2 + n].hex()})
        i += 2 + n
    return items


def qcy_eq(master_gain_db, bands):
    out = b"\x00" + struct.pack("<h", int(master_gain_db) * 100)
    for b in bands:
        out += struct.pack("<HhH", b["freq_hz"], round(b["gain_db"] * 100), round(b["q"] * 100))
    return out


class QcyVectors(unittest.TestCase):
    """The Capsule3 Pro vectors must follow the packet rules in data/qcy_commands.json."""

    def setUp(self):
        self.v = load("test_vectors.json")
        self.q = load("qcy_commands.json")

    def test_build_and_parse(self):
        for case in self.v["qcy_packets"]:
            self.assertEqual(qcy_packet(case["items"]).hex(), case["packet"], case["name"])
            parsed = qcy_parse(bytes.fromhex(case["packet"]))
            want = [{"id": i["id"].upper(), "value": i["value"].lower()} for i in case["items"]]
            self.assertEqual(parsed, want, case["name"])

    def test_vector_items_are_known(self):
        known = {i["id"] for i in self.q["items"]}
        for case in self.v["qcy_packets"]:
            for item in case["items"]:
                self.assertIn(item["id"], known, case["name"])

    def test_eq(self):
        for case in self.v["qcy_eq"]:
            self.assertEqual(qcy_eq(case["master_gain_db"], case["bands"]).hex(), case["payload"])

    def test_buttons(self):
        gestures, functions = self.q["buttons"]["gestures"], self.q["buttons"]["functions"]
        for case in self.v["qcy_buttons"]:
            self.assertEqual(bytes(b for pair in case["pairs"] for b in pair).hex(), case["payload"], case["name"])
            for gesture, function in case["pairs"]:
                self.assertIn(str(gesture), gestures)
                self.assertIn(str(function), functions)

    def test_item_ids_unique(self):
        ids = [i["id"] for i in self.q["items"]]
        self.assertEqual(len(set(ids)), len(ids))

    def test_missing_features_are_real_command_ids(self):
        ids = {c["id"] for c in load("commands.json")["commands"]}
        for name in self.q["not_available"]:
            self.assertIn(name, ids)


if __name__ == "__main__":
    unittest.main()
