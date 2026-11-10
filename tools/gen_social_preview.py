"""Draws docs/assets/social-preview.png: the 1280x640 picture GitHub and other sites show when the repository or the web
site is shared (upload it under Settings, Social preview on GitHub; the web site uses it from docs/_config.yml).
Studio colours (docs/DESIGN.md). Needs Pillow and the Arial fonts of Windows.

usage: python tools/gen_social_preview.py
"""
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "assets" / "social-preview.png"
FONTS = Path("C:/Windows/Fonts")

BACKGROUND, PANEL, TEXT, SECONDARY, ACCENT = "#101214", "#181B1F", "#E8E6E1", "#A0A5AD", "#E6A23C"
BRANDS = ["Soundcore", "Bose", "JBL", "Sony", "Galaxy Buds", "AirPods", "Nothing", "Pixel Buds", "SOUNDPEATS", "QCY", "1MORE", "EarFun", "Cambridge"]


def font(name, size):
    from PIL import ImageFont

    return ImageFont.truetype(str(FONTS / name), size)


def main():
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (1280, 640), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 18, 640], fill=ACCENT)
    draw.text((84, 70), "PairFree", font=font("arialbd.ttf", 132), fill=TEXT)
    draw.text((88, 232), "One open-source app for your earbuds and headphones", font=font("arial.ttf", 44), fill=SECONDARY)
    # The brands as chips, wrapped to the width.
    chip_font = font("arialbd.ttf", 30)
    x, y, gap, pad = 88, 330, 14, 20
    for name in BRANDS:
        w = draw.textlength(name, font=chip_font) + 2 * pad
        if x + w > 1200:
            x, y = 88, y + 66
        draw.rounded_rectangle([x, y, x + w, y + 52], radius=8, fill=PANEL, outline="#2B3037", width=2)
        draw.text((x + pad, y + 7), name, font=chip_font, fill=TEXT)
        x += w + gap
    draw.text((88, 548), "Android  ·  Open source (GPL-3.0)  ·  No account  ·  No internet", font=font("arialbd.ttf", 34), fill=ACCENT)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT, optimize=True)
    print(OUT, OUT.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
