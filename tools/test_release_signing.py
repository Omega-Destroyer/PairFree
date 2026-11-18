"""tools/release_signing.py: the signature it writes is 64 bytes in the form the desktop programs check (needs the openssl command)."""
import importlib.util
import pathlib
import shutil
import subprocess
import tempfile
import unittest

TOOL = pathlib.Path(__file__).resolve().parent / "release_signing.py"
spec = importlib.util.spec_from_file_location("release_signing", TOOL)
rs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rs)


class ReleaseSigning(unittest.TestCase):
    def test_der_signatures_become_64_bytes(self):
        # r and s as DER writes them: a leading zero when the high bit is set, and short ones without padding.
        r = bytes([0x80]) + bytes(31)
        s = bytes([0x01, 0x02])
        der = bytes([0x30, 2 + 1 + 32 + 2 + 2, 0x02, 33, 0]) + r + bytes([0x02, 2]) + s
        raw = rs.der_to_raw(der)
        self.assertEqual(64, len(raw))
        self.assertEqual(r, raw[:32])
        self.assertEqual(bytes(30) + s, raw[32:])

    def test_the_key_file_is_what_the_program_reads(self):
        text = rs.key_source("a" * 64, "b" * 64)
        self.assertIn('UPDATE_KEY_X = "' + "a" * 64 + '"', text)
        self.assertIn("package io.github.omegadestroyer.pairfree.shell", text)

    @unittest.skipUnless(shutil.which("openssl"), "needs the openssl command")
    def test_a_real_key_signs_to_64_bytes_and_its_public_half_is_32_and_32(self):
        with tempfile.TemporaryDirectory() as d:
            key = pathlib.Path(d) / "k.pem"
            subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key)], check=True, capture_output=True)
            x, y = rs.public_coordinates(key)
            self.assertEqual((64, 64), (len(x), len(y)))
            self.assertEqual(64, len(rs.sign_bytes(key, b"data")))

    def test_the_committed_key_is_empty_or_complete(self):
        text = rs.KEY_FILE.read_text(encoding="utf-8")
        import re
        x = re.search(r'UPDATE_KEY_X = "([0-9a-f]*)"', text).group(1)
        y = re.search(r'UPDATE_KEY_Y = "([0-9a-f]*)"', text).group(1)
        self.assertIn((len(x), len(y)), [(0, 0), (64, 64)])


if __name__ == "__main__":
    unittest.main()
