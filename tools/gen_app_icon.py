"""Draws PairFree's program icon (desktop/app/PairFree.ico) from the same shapes as the Android launcher icon (res/drawable/ic_launcher_foreground.xml):
a dot with two sound arcs in Studio amber on the dark Studio background. Needs Pillow.

    python tools/gen_app_icon.py
"""
import math
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
TARGET = ROOT / "desktop" / "app" / "PairFree.ico"
BACK = (0x10, 0x12, 0x14, 255)
AMBER = (0xE6, 0xA2, 0x3C, 255)
SIZES = [16, 24, 32, 48, 64, 128, 256]
SCALE = 8  # supersampling, for edges that are smooth at every size


def draw(size: int) -> Image.Image:
    n = size * SCALE
    img = Image.new("RGBA", (n, n), BACK)
    px = img.load()
    # The launcher icon's canvas is 108 units and its middle 66 show: x and y from 21 to 87.
    unit = n / 66.0
    cx, cy = 46.0, 54.0

    def inside(x: float, y: float) -> bool:
        if (x - cx) ** 2 + (y - cy) ** 2 <= 6 ** 2:
            return True
        for radius in (13.0, 23.0):
            d = math.hypot(x - cx, y - cy)
            angle = math.degrees(math.atan2(y - cy, x - cx))
            if abs(d - radius) <= 1.75 and abs(angle) <= 60:
                return True
            # round caps at the ends of the arc
            for sign in (-1, 1):
                ex = cx + radius * math.cos(math.radians(60 * sign))
                ey = cy + radius * math.sin(math.radians(60 * sign))
                if (x - ex) ** 2 + (y - ey) ** 2 <= 1.75 ** 2:
                    return True
        return False

    for j in range(n):
        for i in range(n):
            if inside(21 + i / unit, 21 + j / unit):
                px[i, j] = AMBER
    return img.resize((size, size), Image.LANCZOS)


def main():
    frames = [draw(s) for s in SIZES]
    frames[-1].save(TARGET, format="ICO", sizes=[(s, s) for s in SIZES], append_images=frames[:-1])
    print("wrote", TARGET.relative_to(ROOT), TARGET.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
