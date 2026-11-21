"""The release gate (owner, 2026-11-21): a release is refused while a brand it touches has an open item.

Why it exists. "Release complete builds" was an instruction in CLAUDE.md and in memory for months, and releases still shipped with settings missing
(the Momentum 5's equalizer, the Q30's two-device switch). The audits that existed (`gap_audit.py`, `doc_gaps.py`) only checked that a gap had a
"blocked: ...; checked: ..." sentence, so a shallow check passed as well as a deep one. This gate checks more, and it runs from a hook
(`.claude/settings.json`) before any `git tag`, `git push` of a tag or `gh release`, so it does not depend on anyone remembering.

What it checks (`python tools/release_gate.py`, exit 1 when anything fails):

1. `data/completeness.json`: the registry of known open and settled items per brand and model. An item is `open` (known, not built), `built`, `blocked`
   (a hard rule or a real impossibility) or `not-applicable`. A blocked or not-applicable item carries `evidence`: searches over the vendor app's files that
   must find nothing (`expect: "none"`) or something (`expect: "some"`); the gate runs them again, so a blocker that the app's own files refute fails here.
   An `open` item in a brand that this release touches fails the gate, unless the owner waived it.
2. The older audits as ratchets: the number of open `gap-audit` entries and of baseline sentences in `data/doc_gaps_baseline.json` may not grow past the
   ceilings the owner keeps in `data/release_waivers.json`.
3. The tree: clean, on `main`, level with `origin/main`, and the tag's version matches `versionName`.

Waivers (`data/release_waivers.json`) are the owner's: `{"ceilings": {...}, "waived": [{"id": ..., "why": ..., "date": ...}]}`. The hook refuses edits to that file, to this
gate and to `.claude/settings.json` from the agent, so a waiver is a decision somebody takes, not a line an agent adds.

`--hook` reads a Claude Code hook's JSON from stdin and blocks (exit 2) the commands and edits above; every other call passes.
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REGISTRY = os.path.join(ROOT, "data", "completeness.json")
WAIVERS = os.path.join(ROOT, "data", "release_waivers.json")

# The Kotlin package of a brand where it differs from its folder name.
PACKAGE = {"1more": "onemore"}
BRANDS = ["soundpeats", "soundcore", "samsung", "sony", "bose", "jbl", "apple", "nothing", "pixel", "qcy", "1more", "earfun",
          "cambridge", "oneplus", "sennheiser", "jabra", "skullcandy"]

PROTECTED = ("data/release_waivers.json", "tools/release_gate.py", ".claude/settings.json", "data/completeness_rules.json")
RELEASE_COMMAND = re.compile(r"(git\s+(-c\s+\S+\s+)?tag\s+(-[asfm]\b|v\d)|git\s+(-c\s+\S+\s+)?push\b[^|;&]*\s(v\d+\.\d+\.\d+|--tags|--follow-tags)|gh\s+release\s+(create|upload|edit)|gh_release\w*\.py|gh\s+api\b[^|;&]*releases)")


def run(*args, cwd=ROOT):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=300).stdout.strip()


def load(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def brands_touched(changed):
    """The brands whose data, docs, tools or protocol code appear in the changed paths."""
    out = set()
    for path in changed:
        p = path.replace("\\", "/")
        for b in BRANDS:
            pkg = PACKAGE.get(b, b)
            if (p.startswith(f"data/{b}/") or p.startswith(f"docs/{b}/") or p.startswith(f"assets/devices/{b}/")
                    or f"/protocol/{pkg}/" in p or f"/brands/{b.capitalize()}" in p
                    or re.search(rf"(^|/)(gen_|import_|test_){re.escape(b)}", p)):
                out.add(b)
    # Skullcandy's files carry names like SkullcandyRaceTalk.kt outside a package folder of their own name.
    for path in changed:
        low = path.lower().replace("\\", "/")
        for b in BRANDS:
            if b.replace("1more", "onemore") in low.rsplit("/", 1)[-1].lower() and low.endswith(".kt"):
                out.add(b)
    return out


def search(evidence):
    """Runs one evidence search: files under `root` (matching `glob`) whose text matches `pattern`. Returns (hits, missing_root)."""
    root = evidence["root"]
    if not os.path.isdir(root):
        return [], True
    pat = re.compile(evidence["pattern"], re.I)
    ext = evidence.get("glob", "")
    hits = []
    for dp, _, files in os.walk(root):
        for name in files:
            if ext and not re.search(ext, name):
                continue
            path = os.path.join(dp, name)
            try:
                if os.path.getsize(path) > 40_000_000:
                    continue
                with open(path, encoding="utf-8", errors="ignore") as f:
                    if pat.search(f.read()):
                        hits.append(path)
            except OSError:
                pass
            if len(hits) >= 5:
                return hits, False
    return hits, False


def check_registry(registry, waived_ids, touched, allow_missing_research=False):
    """Returns (failures, notes) from the registry: open items in touched brands, and blocked items whose evidence disagrees."""
    failures, notes = [], []
    for item in registry.get("items", []):
        iid, brand, status = item["id"], item["brand"], item["status"]
        waived = iid in waived_ids
        if status == "open":
            line = f"[{brand}] {iid}: {item['what']} (reported: {item.get('reported', 'audit')})"
            if brand in touched and not waived:
                failures.append("OPEN in a brand this release touches: " + line)
            else:
                notes.append(("waived " if waived else "open, brand not touched: ") + line)
        elif status in ("blocked", "not-applicable"):
            if not item.get("evidence"):
                failures.append(f"[{brand}] {iid}: a {status} item with no evidence searches")
            for ev in item.get("evidence", []):
                hits, missing = search(ev)
                if missing:
                    msg = f"[{brand}] {iid}: evidence root not found: {ev['root']}"
                    (notes if allow_missing_research else failures).append(msg)
                    continue
                if ev.get("expect", "none") == "none" and hits:
                    failures.append(f"[{brand}] {iid}: {status} claim refuted: /{ev['pattern']}/ is in {hits[0]}")
                elif ev.get("expect") == "some" and not hits:
                    failures.append(f"[{brand}] {iid}: evidence gone: /{ev['pattern']}/ not found under {ev['root']}")
        elif status != "built":
            failures.append(f"[{brand}] {iid}: unknown status {status!r}")
    return failures, notes


def check_ceilings(ceilings):
    failures = []
    gap = run(sys.executable, "tools/gap_audit.py")
    m = re.search(r"open in all: (\d+)", gap)
    n_gap = int(m.group(1)) if m else 10**6
    if n_gap > ceilings.get("gap_audit_open", 0):
        failures.append(f"gap_audit: {n_gap} open entries, the owner's ceiling is {ceilings.get('gap_audit_open', 0)} (python tools/gap_audit.py lists them)")
    base = load(os.path.join(ROOT, "data", "doc_gaps_baseline.json"), [])
    n_doc = len(base) if isinstance(base, list) else len(base.get("statements", base))
    if n_doc > ceilings.get("doc_gaps_baseline", 0):
        failures.append(f"doc_gaps_baseline: {n_doc} statements, the owner's ceiling is {ceilings.get('doc_gaps_baseline', 0)}")
    return failures


def check_tree(tag):
    failures = []
    if run("git", "status", "--porcelain", "--untracked-files=no"):
        failures.append("the working tree has uncommitted changes")
    branch = run("git", "rev-parse", "--abbrev-ref", "HEAD")
    if branch != "main":
        failures.append(f"on branch {branch}, not main")
    run("git", "fetch", "origin")
    ahead = run("git", "rev-list", "--count", "origin/main..HEAD")
    behind = run("git", "rev-list", "--count", "HEAD..origin/main")
    if behind not in ("", "0"):
        failures.append(f"main is {behind} commits behind origin/main: fetch and merge first (the 0.17.0 lesson)")
    if tag:
        gradle = open(os.path.join(ROOT, "android", "app", "build.gradle.kts"), encoding="utf-8").read()
        m = re.search(r'versionName = "([\d.]+)"', gradle)
        if not m or "v" + m.group(1) != tag:
            failures.append(f"tag {tag} does not match versionName {m.group(1) if m else '?'}")
    return failures


def last_release_tag(before):
    tags = [t for t in run("git", "tag", "--sort=-v:refname").splitlines() if re.fullmatch(r"v\d+\.\d+\.\d+", t)]
    for t in tags:
        if t != before:
            return t
    return None


def gate(tag=None, allow_missing_research=False, quiet=False):
    registry = load(REGISTRY, {"items": []})
    waivers = load(WAIVERS, {"ceilings": {}, "waived": []})
    waived_ids = {w["id"] for w in waivers.get("waived", [])}
    prev = last_release_tag(tag)
    changed = run("git", "diff", "--name-only", f"{prev}..HEAD").splitlines() if prev else []
    touched = brands_touched(changed)
    failures, notes = check_registry(registry, waived_ids, touched, allow_missing_research)
    failures += check_ceilings(waivers.get("ceilings", {}))
    failures += check_tree(tag)
    if not quiet:
        print(f"release gate: since {prev}, brands touched: {', '.join(sorted(touched)) or 'none'}")
        for n in notes:
            print("  note:", n)
        for f in failures:
            print("  FAIL:", f)
        print("release gate:", "REFUSED" if failures else "passed")
    return failures


def hook():
    """Claude Code PreToolUse hook: exit 2 blocks, with the reason on stderr."""
    try:
        event = json.load(sys.stdin)
    except Exception:
        return 0
    tool, inp = event.get("tool_name", ""), event.get("tool_input", {}) or {}
    if tool in ("Edit", "Write", "MultiEdit", "NotebookEdit"):
        path = str(inp.get("file_path", "")).replace("\\", "/")
        if any(path.endswith(p) for p in PROTECTED):
            print(f"Blocked: {os.path.basename(path)} belongs to the owner (the release gate, its waivers and its hook). Ask the owner to change it.", file=sys.stderr)
            return 2
        return 0
    if tool in ("Bash", "PowerShell"):
        cmd = str(inp.get("command", ""))
        norm = cmd.replace("\\", "/")
        if any(p in norm for p in PROTECTED) and re.search(r"(>|>>|\btee\b|\bsed\b\s+-i|\bmv\b|\bcp\b|\brm\b|Set-Content|Out-File|open\(|write|git\s+(checkout|restore|reset))", norm):
            print("Blocked: a command that writes the release gate, its waivers or its hook. Those belong to the owner.", file=sys.stderr)
            return 2
        if RELEASE_COMMAND.search(cmd):
            tag = None
            m = re.search(r"\bv\d+\.\d+\.\d+\b", cmd)
            if m:
                tag = m.group(0)
            failures = gate(tag=tag, quiet=True)
            if failures:
                print("Blocked by the release gate (tools/release_gate.py): this release has open items.\n" + "\n".join("  - " + f for f in failures)
                      + "\nBuild what is missing, or have the owner waive an item in data/release_waivers.json. Do not release around this.", file=sys.stderr)
                return 2
    return 0


def main():
    args = sys.argv[1:]
    if "--hook" in args:
        sys.exit(hook())
    tag = next((a for a in args if re.fullmatch(r"v\d+\.\d+\.\d+", a)), None)
    failures = gate(tag=tag, allow_missing_research="--allow-missing-research" in args)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
