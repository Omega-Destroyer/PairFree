"""The app's versionCode must follow from its versionName (CLAUDE.md, Versions)."""
import re
import unittest
from pathlib import Path

GRADLE = Path(__file__).resolve().parent.parent / "android" / "app" / "build.gradle.kts"


class Version(unittest.TestCase):
    def test_code_follows_name(self):
        text = GRADLE.read_text(encoding="utf-8")
        code = int(re.search(r"versionCode = (\d+)", text).group(1))
        x, y, z = map(int, re.search(r'versionName = "(\d+)\.(\d+)\.(\d+)"', text).groups())
        self.assertEqual(x * 1_000_000 + y * 1_000 + z, code, "versionCode is x * 1,000,000 + y * 1,000 + z")


    def test_desktop_apps_carry_the_same_version(self):
        text = GRADLE.read_text(encoding="utf-8")
        name = re.search(r'versionName = "([\d.]+)"', text).group(1)
        shell = (GRADLE.parent.parent.parent / "desktop" / "shell" / "src" / "commonMain" / "kotlin" / "AppVersion.kt").read_text(encoding="utf-8")
        self.assertIn(f'APP_VERSION = "{name}"', shell, "one version for every platform (CLAUDE.md, Versions)")

    def test_readme_names_the_released_version(self):
        text = GRADLE.read_text(encoding="utf-8")
        name = re.search(r'versionName = "([\d.]+)"', text).group(1)
        readme = (GRADLE.parent.parent.parent / "README.md").read_text(encoding="utf-8")
        self.assertIn(f"Latest release: v{name}", readme, "bump the README's Latest release: vX.Y.Z with versionName")


if __name__ == "__main__":
    unittest.main()
