"""Writes data/skullcandy/race_commands.json from Skullcandy's own app (5.0.6): the 58 RACE command classes of its Flutter code
(`utils/race_commands/**`, Blutter output outside the repo). For each class, the byte strings its code writes (the request, with the
RACE `05 5A` header the app prepends: `05 5A <length u16 LE> <command id u16 LE> <payload>`) and the reply prefixes it checks (`05 5B`
answers, `05 5D` notifications). Only the literal strings are taken; values the code builds at run time (the setting being written) are
not in the file and are to be read from the class itself.

    python tools/import_skullcandy_race.py PATH_TO_blutter_out [--check]
"""
import glob
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "skullcandy" / "race_commands.json"
SUB = "asm/skullcandy_app/utils/race_commands"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    base = Path(args[0]) / SUB
    commands = []
    for path in sorted(glob.glob(str(base / "**" / "*.dart"), recursive=True)):
        text = Path(path).read_text(encoding="utf-8")
        cls = re.search(r"^class (\w+)", text, re.M)
        if not cls:
            continue
        requests, replies = [], []
        for m in re.finditer(r'r16 = "([^"]*)"', text):
            s = m.group(1)
            if re.fullmatch(r"([0-9A-Fa-f]{2} ?)+", s) and len(s.replace(" ", "")) >= 4:
                target = requests if " " in s else replies
                if s not in target:
                    target.append(s)
        if not requests and not replies:
            continue
        rel = Path(path).relative_to(base).as_posix().replace(".dart", "")
        commands.append({"file": rel, "class": cls.group(1), "request_literals": requests, "reply_prefixes": replies})
    data = {"_about": "Skullcandy 5.0.6: the RACE command classes of the app's Flutter code (utils/race_commands), by tools/import_skullcandy_race.py. Only the byte strings the code "
                      "writes literally are here; request values built at run time are to be read from each class (docs/skullcandy/PROTOCOL.md section 11).",
            "commands": commands}
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        sys.exit(0 if OUT.exists() and OUT.read_text(encoding="utf-8") == text else "data/skullcandy/race_commands.json is out of date")
    OUT.write_text(text, encoding="utf-8")
    print(OUT, len(commands), "commands")


if __name__ == "__main__":
    main()
