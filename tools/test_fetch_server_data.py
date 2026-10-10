#!/usr/bin/env python3
"""Tests for tools/fetch_server_data.py. Nothing here touches the network.

Run from the repo root:  python -m unittest discover -s tools -v
"""
import json
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import fetch_server_data as fsd  # noqa: E402

PAIRS = json.dumps({"code": 200, "data": [
    {"bluetoothName": "SOUNDPEATS Air4", "otaCode": "AIR4", "schemeCode": "QUALCOMM"},
    {"bluetoothName": "SOUNDPEATS H3", "otaCode": "S99", "schemeCode": "QUALCOMM"},
]}).encode()
PRODUCTS = json.dumps({"code": 200, "data": [{"productName": "Air5 Pro+", "otaCode": "S98"}]}).encode()
EQ = json.dumps({"code": 200, "data": []}).encode()
NO_FEATURES = json.dumps({"code": 200, "data": {"funcList": []}}).encode()
FEATURES = json.dumps({"code": 200, "data": {"funcList": [{"code": "FSN08"}]}}).encode()


class FakeServer:
    """Answers like the server would and records every call with the fake time it arrived."""

    def __init__(self, clock, feature_version="0.0.1", status=None):
        self.clock, self.feature_version, self.status = clock, feature_version, status or {}
        self.calls = []

    def __call__(self, url, headers):
        self.calls.append((self.clock.now, url, dict(headers)))
        path = url[len(fsd.BASE_URL):]
        for part, code in self.status.items():
            if part in path:
                return code, b"nope"
        if path == "ota/pairDataList":
            return 200, PAIRS
        if path == "product/list":
            return 200, PRODUCTS
        if path.startswith("eq/preset/list"):
            return 200, EQ
        return 200, FEATURES if f"otaVersion={self.feature_version}&" in path else NO_FEATURES


class Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class FetchPlan(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.out = pathlib.Path(self.tmp.name) / "dump"
        self.clock = Clock()
        self.server = FakeServer(self.clock)

    def tearDown(self):
        self.tmp.cleanup()

    def fetcher(self, **kw):
        return fsd.Fetcher(self.out, get=self.server, sleep=self.clock.sleep, clock=self.clock, log=lambda *_: None, **kw)

    def test_owner_models_go_first_and_versions_fall_back(self):
        codes = fsd.run(self.fetcher(), local_codes=["OLD1"])
        self.assertEqual(codes, ["S99", "S98", "AIR4", "OLD1"])
        paths = [url[len(fsd.BASE_URL):] for _, url, _ in self.server.calls]
        self.assertEqual(paths[:5], ["ota/pairDataList", "product/list", "eq/preset/list?otaCode=S99&type=0",
                                     "ota/functionList?otaVersion=&otaCode=S99",
                                     "ota/functionList?otaVersion=0.0.1&otaCode=S99"])
        self.assertNotIn("ota/functionList?otaVersion=9.9.9&otaCode=S99", paths)   # stopped once features came back

    def test_gap_is_at_least_thirty_seconds_whatever_is_asked(self):
        fsd.run(self.fetcher(delay=1))
        times = [t for t, _, _ in self.server.calls]
        self.assertTrue(all(b - a >= 30 for a, b in zip(times, times[1:])))

    def test_only_get_requests_to_known_paths_with_no_identifiers(self):
        fsd.run(self.fetcher())
        allowed = ("ota/pairDataList", "product/list", "eq/preset/list?", "ota/functionList?")
        for _, url, headers in self.server.calls:
            self.assertTrue(url.startswith(fsd.BASE_URL))
            self.assertTrue(url[len(fsd.BASE_URL):].startswith(allowed), url)
            self.assertEqual(headers.get("userId"), "")
            self.assertFalse({"model", "manufacturer", "sdkVersion"} & set(headers))

    def test_second_run_sends_nothing_new_and_keeps_the_gap(self):
        fsd.run(self.fetcher())
        sent = len(self.server.calls)
        fsd.run(self.fetcher())
        self.assertEqual(len(self.server.calls), sent)
        # a resumed run that does need a request still waits out the gap from the last run
        (self.out / "eq" / "AIR4.json").unlink()
        self.clock.now += 5
        last = self.server.calls[-1][0]
        fsd.run(self.fetcher())
        self.assertGreaterEqual(self.server.calls[-1][0] - last, 30)

    def test_limit_stops_before_sending(self):
        with self.assertRaises(fsd.Stop):
            fsd.run(self.fetcher(limit=3))
        self.assertEqual(len(self.server.calls), 3)

    def test_server_error_stops_at_once(self):
        self.server.status = {"eq/preset/list": 429}
        with self.assertRaises(fsd.Stop):
            fsd.run(self.fetcher())
        self.assertEqual(len(self.server.calls), 3)

    def test_not_found_is_skipped_but_a_run_of_them_stops(self):
        self.server.status = {"otaCode=S99": 404}
        fsd.run(self.fetcher())                         # S99 misses 4 times, then others answer
        self.assertEqual((self.out / "eq" / "S99.json").read_bytes(), b"")
        self.assertTrue((self.out / "eq" / "AIR4.json").exists())
        self.server.status = {"eq/preset/list": 404, "ota/functionList": 404}
        other = pathlib.Path(self.tmp.name) / "dump2"
        f = fsd.Fetcher(other, get=self.server, sleep=self.clock.sleep, clock=self.clock, log=lambda *_: None)
        with self.assertRaises(fsd.Stop):
            fsd.run(f)

    def test_explicit_version_is_the_only_one_tried(self):
        fsd.run(self.fetcher(), only=["S99"], versions={"S99": "0.4.6"})
        paths = [url[len(fsd.BASE_URL):] for _, url, _ in self.server.calls]
        self.assertEqual([p for p in paths if "functionList" in p], ["ota/functionList?otaVersion=0.4.6&otaCode=S99"])

    def test_dry_run_sends_nothing(self):
        fsd.run(self.fetcher(), local_codes=["A", "B"], dry_run=True)
        self.assertEqual(self.server.calls, [])
        self.assertFalse(self.out.exists())

    def test_unreadable_reply_counts_as_an_answer(self):
        self.assertTrue(fsd.has_features(b"Zm9vYmFy"))   # may be encrypted; decoded later
        self.assertFalse(fsd.has_features(b""))
        self.assertFalse(fsd.has_features(NO_FEATURES))


if __name__ == "__main__":
    unittest.main()
