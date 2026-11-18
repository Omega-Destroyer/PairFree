"""The release key of the desktop apps' updates (docs/DESKTOP.md, section 6): the program checks a downloaded update against it before it
replaces itself, so a release file changed on GitHub is not installed.

ECDSA with P-256 and SHA-256, through the `openssl` command line. The private key lives outside the repo, next to the other release secrets
(never commit it); the public half goes into desktop/shell/.../UpdateKeyData.kt, which the program carries.

    python tools/release_signing.py keygen --private E:\\Builds\\releases\\pairfree-update.pem
    python tools/release_signing.py sign PairFree-0.15.0.exe --private E:\\Builds\\releases\\pairfree-update.pem
        writes PairFree-0.15.0.exe.sig (64 bytes, r then s); upload both to the release.
"""
import argparse
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
KEY_FILE = ROOT / "desktop" / "shell" / "src" / "commonMain" / "kotlin" / "UpdateKeyData.kt"


def openssl(*args, **kw):
    return subprocess.run(["openssl", *args], check=True, capture_output=True, **kw)


def public_coordinates(private: pathlib.Path):
    """The public key's X and Y as 64 hex digits each."""
    der = openssl("ec", "-in", str(private), "-pubout", "-outform", "DER").stdout
    point = der[-65:]
    if point[0] != 4:
        raise SystemExit("not an uncompressed P-256 public key")
    return point[1:33].hex(), point[33:].hex()


def der_to_raw(sig: bytes) -> bytes:
    """An ECDSA signature in DER (what openssl writes) as 64 bytes, r then s, each padded to 32."""
    if sig[0] != 0x30:
        raise ValueError("not a DER signature")
    i = 2 + (sig[1] & 0x7F) if sig[1] & 0x80 else 2
    parts = []
    for _ in range(2):
        if sig[i] != 2:
            raise ValueError("not a DER integer")
        n = sig[i + 1]
        parts.append(sig[i + 2:i + 2 + n].lstrip(b"\0").rjust(32, b"\0"))
        i += 2 + n
    return parts[0] + parts[1]


def sign_bytes(private: pathlib.Path, data: bytes) -> bytes:
    with tempfile.TemporaryDirectory() as d:
        f = pathlib.Path(d) / "data"
        f.write_bytes(data)
        return der_to_raw(openssl("dgst", "-sha256", "-sign", str(private), str(f)).stdout)


def key_source(x: str, y: str) -> str:
    return (
        "// Written by tools/release_signing.py keygen. The release key's public half, as two 32-byte coordinates in hex; empty until a key was made.\n"
        "package io.github.omegadestroyer.pairfree.shell\n\n"
        f'internal const val UPDATE_KEY_X = "{x}"\n'
        f'internal const val UPDATE_KEY_Y = "{y}"\n'
    )


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    k = sub.add_parser("keygen", help="make the release key and write its public half into the program's source")
    k.add_argument("--private", required=True, type=pathlib.Path)
    s = sub.add_parser("sign", help="write FILE.sig")
    s.add_argument("file", type=pathlib.Path)
    s.add_argument("--private", required=True, type=pathlib.Path)
    a = ap.parse_args()
    if a.cmd == "keygen":
        if a.private.exists():
            sys.exit(f"{a.private} exists: not overwritten")
        a.private.parent.mkdir(parents=True, exist_ok=True)
        openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(a.private))
        x, y = public_coordinates(a.private)
        KEY_FILE.write_text(key_source(x, y), encoding="utf-8", newline="\n")
        print("wrote", a.private, "(keep it outside the repo, and keep a copy)")
        print("public half written to", KEY_FILE.relative_to(ROOT), ": every release from now on needs a signature made with it")
    else:
        sig = sign_bytes(a.private, a.file.read_bytes())
        out = a.file.with_name(a.file.name + ".sig")
        out.write_bytes(sig)
        print("wrote", out)


if __name__ == "__main__":
    main()
