#!/usr/bin/env python3
"""Fetch per-model data from SoundPEATS' server, slowly, and save it unmodified.

This is a research tool for the project owner. It is the only code in this repo
that talks to SoundPEATS' server, and the app must never do so. It makes the same
read-only requests the official app makes when earbuds connect:

    ota/pairDataList                       every supported model: name, firmware code, chip family
    product/list                           product names per firmware code
    eq/preset/list?otaCode=..&type=0       EQ curves for one model
    ota/functionList?otaVersion=..&otaCode=..   features for one model

It sends no account, no device identifiers and no user data. It only sends GET
requests, waits at least 30 seconds between them, and stops at the first sign the
server is unhappy: any HTTP error other than "not found", a reply that says the
request was refused (the server sends "authentication failed" as HTTP 200 with
code 401 inside), five "not found" replies in a row, or a network error.

As of 2026-10-10 the server refuses every request without the official app's
authentication, so this tool gets nothing. It does not and will not authenticate.

The server's robots rules disallow automated fetching. Running this is the owner's
decision.

Responses are saved exactly as received under server_dump/, which git ignores.
Some may be encrypted text. This tool does not decode them and holds no key.

Usage:
    python tools/fetch_server_data.py --dry-run     show the plan, send nothing
    python tools/fetch_server_data.py               fetch everything, resumable
    python tools/fetch_server_data.py --only S27    just these firmware codes
    python tools/fetch_server_data.py --limit 6     stop after six requests
    python tools/fetch_server_data.py --version S27=0.1.9   firmware version to ask about
"""
import argparse
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

BASE_URL = "https://peats.xingkeqi.com/prod/"
MIN_DELAY = 30.0
# The official app sends these. os, appVersion and lang may select what comes back.
HEADERS = {
    "os": "Android",
    "appVersion": "v1.9.45",
    "lang": "en_US",
    "userId": "",
    "User-Agent": "SoundPeter-research (one request per 30 s)",
}
# Tried in order for the feature list when the firmware version is not known.
FALLBACK_VERSIONS = ["", "0.0.1", "9.9.9"]
REPO = pathlib.Path(__file__).resolve().parent.parent


class Stop(Exception):
    """Raised to end the run without sending anything more."""


def safe_name(text):
    return re.sub(r"[^A-Za-z0-9._+-]", "_", text) or "_"


def http_get(url, headers, timeout=30):
    """Returns (status, body bytes), also for HTTP error replies. Raises Stop if there is no reply."""
    request = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as reply:
            return reply.status, reply.read()
    except urllib.error.HTTPError as err:
        return err.code, err.read()
    except (urllib.error.URLError, OSError) as err:
        raise Stop(f"Network error for {url}: {err}. Stopping.") from err


def parse_json(body):
    try:
        return json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        return None


def refusal(body):
    """The server's reason if a 200 reply is really a refusal, for example
    {"code": 401, "success": false, "message": "...authentication failed..."}. None otherwise."""
    doc = parse_json(body)
    if not isinstance(doc, dict):
        return None
    code = doc.get("code")
    if doc.get("success") is False or (isinstance(code, int) and code != 200):
        return f"code {code}: {doc.get('message', '')}".strip()
    return None


def items_of(doc):
    """The list inside a {code, data, ...} envelope, or [] if it is not there."""
    data = doc.get("data") if isinstance(doc, dict) else None
    return data if isinstance(data, list) else []


def has_features(body):
    """True if this looks like a feature list with something in it, or is not plain JSON
    (then it may be encrypted and has to be decoded later)."""
    doc = parse_json(body)
    if doc is None:
        return len(body.strip()) > 0
    data = doc.get("data") if isinstance(doc, dict) else None
    return isinstance(data, dict) and bool(data.get("funcList"))


class Fetcher:
    MAX_MISSES = 5

    def __init__(self, out, delay=MIN_DELAY, limit=None, get=http_get, sleep=time.sleep, clock=time.time, log=print):
        self.out = pathlib.Path(out)
        self.delay = max(float(delay), MIN_DELAY)        # never below the floor
        self.limit = limit
        self.get, self.sleep, self.clock, self.log = get, sleep, clock, log
        self.sent = 0
        self.misses = 0
        self.index_path = self.out / "index.json"
        self.index = json.loads(self.index_path.read_text()) if self.index_path.exists() else []
        # A new run still honours the gap since the previous run's last request.
        self.last = max((e.get("fetched_epoch", 0) for e in self.index), default=None)

    def fetch(self, path, params, rel):
        """Returns the body, from disk if already saved. Saves new bodies unmodified."""
        target = self.out / rel
        if target.exists():
            return target.read_bytes()
        if self.limit is not None and self.sent >= self.limit:
            raise Stop(f"Reached --limit {self.limit}.")
        if self.last is not None:
            wait = self.delay - (self.clock() - self.last)
            if wait > 0:
                self.sleep(wait)
        query = "?" + urllib.parse.urlencode(params) if params else ""
        url = BASE_URL + path + query
        self.log(f"GET {path}{query}")
        try:
            status, body = self.get(url, HEADERS)
        finally:
            self.last = self.clock()
            self.sent += 1
        self.out.mkdir(parents=True, exist_ok=True)
        refused = refusal(body) if status == 200 else None
        entry = {"file": rel, "url": url, "status": status, "bytes": len(body), "fetched_epoch": self.last,
                 "fetched_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(self.last))}
        if refused:
            entry["refused"] = refused
        self.index.append(entry)
        self.index_path.write_text(json.dumps(self.index, indent=2))
        if refused:
            # Not saved as an answer, so nothing later reads it as data.
            raise Stop(f"The server refused {path}: {refused}. Stopping; it will refuse the rest too.")
        if status == 404:
            self.misses += 1
            if self.misses >= self.MAX_MISSES:
                raise Stop(f"{self.misses} 'not found' replies in a row. Stopping.")
            body = b""                                    # saved empty so a later run does not ask again
        elif status != 200:
            raise Stop(f"HTTP {status} from {url}. Stopping so the server is not pushed.")
        else:
            self.misses = 0
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        return body


def model_codes(pair_body, product_body, local_codes, first):
    """Firmware codes to ask about, the owner's models first. Returns (codes, names by code)."""
    names = {}
    for item in items_of(parse_json(pair_body) or {}):
        if isinstance(item, dict) and item.get("otaCode"):
            names.setdefault(item["otaCode"], []).append(str(item.get("bluetoothName", "")))
    for item in items_of(parse_json(product_body) or {}):
        if isinstance(item, dict) and item.get("otaCode"):
            names.setdefault(item["otaCode"], []).append(str(item.get("productName", "")))
    for code in local_codes:
        names.setdefault(code, [])

    def rank(code):
        text = " ".join(names[code]).lower()
        hits = [i for i, want in enumerate(first) if want.lower() in text]
        return (min(hits) if hits else len(first), code)

    return sorted(names, key=rank), names


def run(fetcher, only=None, first=("H3", "Air5 Pro+"), versions=None, local_codes=(), dry_run=False):
    versions = versions or {}
    if dry_run:
        fetcher.log("Dry run. Nothing is sent.")
        fetcher.log("1. ota/pairDataList and product/list (2 requests)")
        fetcher.log("2. per firmware code: eq/preset/list, then ota/functionList (2 to 4 requests)")
        codes = sorted(only or local_codes)
        fetcher.log(f"Known codes before asking the server: {len(codes)}")
        low, high = 2 + 2 * len(codes), 2 + 4 * len(codes)
        fetcher.log(f"Between {low} and {high} requests, at least {low * fetcher.delay / 60:.0f} to "
                    f"{high * fetcher.delay / 60:.0f} minutes. The server may list more models.")
        return []
    pair = fetcher.fetch("ota/pairDataList", None, "pairDataList.json")
    products = fetcher.fetch("product/list", None, "product_list.json")
    codes, names = model_codes(pair, products, local_codes, first)
    if only:
        codes = [c for c in codes if c in set(only)] + [c for c in only if c not in codes]
    fetcher.log(f"{len(codes)} firmware codes to ask about.")
    for code in codes:
        label = f"{code} ({', '.join(n for n in names.get(code, []) if n) or 'name unknown'})"
        fetcher.log(label)
        name = safe_name(code)
        fetcher.fetch("eq/preset/list", {"otaCode": code, "type": 0}, f"eq/{name}.json")
        tries = [versions[code]] if code in versions else FALLBACK_VERSIONS
        for version in tries:
            body = fetcher.fetch("ota/functionList", {"otaVersion": version, "otaCode": code},
                                 f"functions/{name}__{safe_name(version) if version else 'noversion'}.json")
            if has_features(body):
                break
    return codes


def pack(out):
    archive = out.with_suffix(".zip")
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(out.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(out.parent))
    return archive


def main(argv=None):
    ap = argparse.ArgumentParser(description="Fetch per-model data from SoundPEATS' server, slowly.")
    ap.add_argument("--out", default=str(REPO / "server_dump"))
    ap.add_argument("--delay", type=float, default=MIN_DELAY, help="seconds between requests, never below 30")
    ap.add_argument("--only", default="", help="comma-separated firmware codes")
    ap.add_argument("--first", default="H3,Air5 Pro+", help="models whose names contain these go first")
    ap.add_argument("--limit", type=int, default=None, help="stop after this many requests")
    ap.add_argument("--version", action="append", default=[], metavar="CODE=VERSION",
                    help="firmware version to ask the feature list for; repeatable")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    try:
        local = [d["firmware_code"] for d in json.loads((REPO / "data" / "devices.json").read_text())["devices"]]
    except (OSError, ValueError, KeyError):
        local = []
    versions = dict(v.split("=", 1) for v in args.version)
    only = [c for c in args.only.split(",") if c]
    out = pathlib.Path(args.out)
    fetcher = Fetcher(out, delay=args.delay, limit=args.limit)
    try:
        run(fetcher, only=only, first=[f for f in args.first.split(",") if f], versions=versions,
            local_codes=local, dry_run=args.dry_run)
    except Stop as stop:
        print(stop)
    except KeyboardInterrupt:
        print("Interrupted. Run again to continue where it stopped.")
    if not args.dry_run and out.exists():
        print(f"{fetcher.sent} requests sent this run. Saved under {out}")
        print(f"Packed into {pack(out)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
