"""Generate the device drawings under assets/devices/ (Studio look).

Each drawing is also written as an Android vector drawable,
android/app/src/main/res/drawable/device_<model>_<part>.xml, so the app uses the
same shapes. Change the shapes here, never in the generated files.

H3: the earbud outlines are traced from a front-view product photo of the left
earbud: the shell, the chrome rim, the faceplate and the gold nozzle band, plus an
ellipse fitted to the ear tip. The case is drawn from the front: its outline was
measured from a photo of the back, and the lid, nameplate, seam band and base from
a photo of the front. The photos are not in the repo. Logos, text and the faceplate
pattern are left out.

Air5 Pro+: proportions estimated by eye from product photos. Not yet traced.

Output uses only paths, circles and rects with solid fills and plain presentation
attributes (no CSS, opacity, dash arrays or transforms), so the files convert
cleanly to Android vector drawables. Colours are the Studio palette in docs/DESIGN.md.

Usage:
    python tools/gen_device_art.py                    write every model into assets/
    python tools/gen_device_art.py --models h3        only some models (comma separated)
    python tools/gen_device_art.py --sheet sheet.html also write a preview page
"""
import argparse
import math
import pathlib
import re

_ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
_ROOT = pathlib.Path(__file__).resolve().parent.parent
_ap.add_argument("--out", default=str(_ROOT / "assets"))
_ap.add_argument("--res", default=str(_ROOT / "android" / "app" / "src" / "main" / "res" / "drawable"),
                 help="where the Android vector drawables go")
_ap.add_argument("--models", default="")
_ap.add_argument("--sheet", default="")
ARGS = _ap.parse_args()
OUT = pathlib.Path(ARGS.out)
RES = pathlib.Path(ARGS.res)

# Studio palette (docs/DESIGN.md)
BACKGROUND = "#101214"
PANEL = "#181B1F"
RAISED = "#1F2328"
HAIRLINE = "#2B3037"
INK = "#E8E6E1"          # Text
LABEL = "#8A9099"        # Label text: guides and fine detail
ACCENT = "#E6A23C"
STROKE, DETAIL, FINE = 1.5, 1.1, 1.0


# ------------------------------------------------------------ geometry ------
def parse(d):
    """'M1 2C3 4 5 6 7 8Z' -> [('M', [(1,2)]), ('C', [(3,4),(5,6),(7,8)]), ('Z', [])]"""
    out = []
    for cmd, args in re.findall(r"([MCLZ])([^MCLZ]*)", d):
        nums = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", args)]
        out.append((cmd, list(zip(nums[0::2], nums[1::2]))))
    return out


def fmt(shape):
    return "".join(cmd + " ".join(f"{x:.1f} {y:.1f}" for x, y in pts) for cmd, pts in shape)


def tf(shape, fn):
    return [(cmd, [fn(x, y) for x, y in pts]) for cmd, pts in shape]


def rotate_about(cx, cy, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return lambda x, y: (cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca)


def ellipse_path(cx, cy, rx, ry):
    kx, ky = 0.5523 * rx, 0.5523 * ry
    return parse(f"M{cx - rx} {cy}C{cx - rx} {cy - ky} {cx - kx} {cy - ry} {cx} {cy - ry}"
                 f"C{cx + kx} {cy - ry} {cx + rx} {cy - ky} {cx + rx} {cy}"
                 f"C{cx + rx} {cy + ky} {cx + kx} {cy + ry} {cx} {cy + ry}"
                 f"C{cx - kx} {cy + ry} {cx - rx} {cy + ky} {cx - rx} {cy}Z")


def circle_path(cx, cy, r):
    return ellipse_path(cx, cy, r, r)


def points(text):
    """'x,y x,y ...' -> [(x, y), ...]"""
    return [tuple(float(v) for v in p.split(",")) for p in text.split()]


def curve_through(p, closed=True):
    """Smooth curve through the points (uniform Catmull-Rom), as cubic Beziers."""
    n = len(p)
    at = (lambda i: p[i % n]) if closed else (lambda i: p[min(max(i, 0), n - 1)])
    out = [("M", [p[0]])]
    for i in range(n if closed else n - 1):
        p0, p1, p2, p3 = at(i - 1), at(i), at(i + 1), at(i + 2)
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        out.append(("C", [c1, c2, p2]))
    return out + ([("Z", [])] if closed else [])


def fit(shapes, w, h, margin):
    """Uniformly scale and centre a dict of shapes into a w x h box."""
    pts = [p for s in shapes.values() for _, ps in s for p in ps]
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts)
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts)
    s = min((w - 2 * margin) / (x1 - x0), (h - 2 * margin) / (y1 - y0))
    ox = (w - (x1 - x0) * s) / 2 - x0 * s
    oy = (h - (y1 - y0) * s) / 2 - y0 * s
    return {k: tf(v, lambda x, y: (x * s + ox, y * s + oy)) for k, v in shapes.items()}, s


def dashes(x1, y1, x2, y2, on=2.0, off=3.0):
    length = math.hypot(x2 - x1, y2 - y1)
    ux, uy = (x2 - x1) / length, (y2 - y1) / length
    d, pos = [], 0.0
    while pos < length:
        end = min(pos + on, length)
        d.append(f"M{x1 + ux * pos:.1f} {y1 + uy * pos:.1f}L{x1 + ux * end:.1f} {y1 + uy * end:.1f}")
        pos += on + off
    return "".join(d)


def rounded_rect(x, y, w, h, r):
    k = 0.5523 * r
    pts = (f"M{x + r} {y}L{x + w - r} {y}C{x + w - r + k} {y} {x + w} {y + r - k} {x + w} {y + r}"
           f"L{x + w} {y + h - r}C{x + w} {y + h - r + k} {x + w - r + k} {y + h} {x + w - r} {y + h}"
           f"L{x + r} {y + h}C{x + r - k} {y + h} {x} {y + h - r + k} {x} {y + h - r}"
           f"L{x} {y + r}C{x} {y + r - k} {x + r - k} {y} {x + r} {y}Z")
    return fmt(parse(pts))


def path(d, stroke=None, sw=None, fill=None):
    """One drawing element. Every drawing is a list of these, written out as SVG and as an Android vector."""
    return dict(d=d, stroke=stroke, sw=sw, fill=fill)


def drawing(w, h, elements, label):
    return dict(w=w, h=h, elements=elements, label=label)


def svg(dr):
    out = []
    for e in dr["elements"]:
        s = f'<path d="{e["d"]}" fill="{e["fill"] or "none"}"'
        if e["stroke"]:
            s += f' stroke="{e["stroke"]}" stroke-width="{e["sw"]}" stroke-linecap="round" stroke-linejoin="round"'
        out.append(s + "/>")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {dr["w"]} {dr["h"]}" width="{dr["w"]}" '
            f'height="{dr["h"]}" role="img" aria-label="{dr["label"]}">\n' + "\n".join(out) + "\n</svg>\n")


def vector(dr):
    """The same drawing as an Android vector drawable, sized in dp like the SVG in px."""
    out = []
    for e in dr["elements"]:
        s = f'    <path\n        android:pathData="{e["d"]}"'
        if e["fill"]:
            s += f'\n        android:fillColor="{e["fill"]}"'
        if e["stroke"]:
            s += (f'\n        android:strokeColor="{e["stroke"]}"\n        android:strokeWidth="{e["sw"]}"'
                  '\n        android:strokeLineCap="round"\n        android:strokeLineJoin="round"')
        out.append(s + " />")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {dr["label"]}. Generated by tools/gen_device_art.py; change the shapes there. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{dr["w"]}dp"\n    android:height="{dr["h"]}dp"\n'
            f'    android:viewportWidth="{dr["w"]}"\n    android:viewportHeight="{dr["h"]}">\n'
            + "\n".join(out) + "\n</vector>\n")


# ---------------------------------------------------------------- H3 --------
# Traced from a front-view product photo of the LEFT earbud, in photo pixels. Each
# outline is the traced edge, smoothed and reduced to points that a Catmull-Rom curve
# passes through; the curve stays within about 1 px of the traced edge on average.
#   body   the clear shell, and the housing that runs up to the nozzle
#   rim    outer edge of the chrome rim around the faceplate
#   bevel  a line halfway across the chrome rim
#   panel  the faceplate inside the rim
#   ring   the gold band at the base of the ear tip
H3_BUD = {
    "body": """
        125,59 103,67 85,83 73,103 71,127 70,151 69,175 69,199 68,222 59,242 46,256 52,279
        63,300 76,320 92,338 109,354 128,369 148,382 170,393 191,402 214,410 238,413 261,414
        285,416 306,425 330,425 354,425 378,425 398,413 420,403 439,389 455,372 470,352
        481,331 489,309 499,289 499,265 499,241 499,217 498,193 486,173 476,152 465,130
        451,111 432,96 411,87 387,83 363,81 340,83 316,88 293,95 275,107 255,113 234,101
        213,90 192,79 171,68 149,59
    """,
    "rim": """
        362,82 338,87 316,95 294,107 274,119 253,132 232,144 210,154 187,160 163,163 139,164
        116,169 95,181 80,200 70,222 68,246 71,270 81,292 97,310 118,322 141,326 165,328
        189,332 212,340 233,351 255,362 274,376 294,389 315,401 338,408 362,413 386,412
        409,406 430,394 449,379 464,360 477,340 487,318 493,294 498,271 499,247 498,223
        494,199 487,176 477,154 465,133 450,114 431,99 409,89 386,83
    """,
    "bevel": """
        362,98 338,103 316,112 295,123 274,136 254,149 233,160 210,169 187,175 163,178
        139,179 116,186 97,201 86,222 83,246 87,270 98,291 118,305 141,311 165,313 189,317
        212,324 234,334 255,346 276,358 295,373 316,384 339,393 362,398 386,397 409,389
        429,376 446,359 460,339 471,317 478,294 482,271 484,246 483,222 478,199 471,176
        460,154 447,134 430,117 409,105 386,98
    """,
    "panel": """
        362,110 339,115 317,125 296,137 275,150 255,162 234,173 211,182 188,187 164,190
        140,191 117,199 102,217 95,240 98,264 109,284 130,297 153,300 177,302 201,308
        224,316 245,326 266,338 286,351 306,365 327,376 350,384 374,386 397,381 418,369
        435,353 450,333 460,311 467,288 471,265 471,241 470,217 463,194 455,171 443,150
        428,131 409,118 386,110
    """,
    "ring": """
        127,58 118,61 110,64 102,69 94,74 88,80 82,87 77,94 73,102 72,111 74,117 77,108
        81,100 85,92 92,85 98,79 106,74 114,70 123,68 132,65 141,66 150,67 158,69 167,73
        175,77 177,74 170,69 162,64 154,61 145,59 136,59
    """,
}
H3_TIP = (138.5, 134.4, 129.9, 133.4, 103.1)   # ear tip: centre, semi-axes, rotation (deg)
H3_FACE_CENTRE = (312.7, 246.8)               # faceplate centroid; its long axis is level


def h3_bud():
    shapes = {k: curve_through(points(v)) for k, v in H3_BUD.items()}
    cx, cy, rx, ry, ang = H3_TIP
    shapes["tip"] = tf(ellipse_path(cx, cy, rx, ry), rotate_about(cx, cy, ang))
    shapes["centre"] = [("M", [H3_FACE_CENTRE])]
    g, _ = fit(shapes, 96, 96, 4.5)
    mx, my = g["centre"][0][1][0]
    return drawing(96, 96, [
        path(dashes(mx, 2, mx, 94) + dashes(2, my, 94, my), LABEL, FINE),
        path(fmt(g["tip"]), INK, STROKE, fill=PANEL),
        path(fmt(g["body"]), INK, STROKE, fill=PANEL),
        path(fmt(g["ring"]), fill=ACCENT),
        path(fmt(g["rim"]), INK, DETAIL, fill=RAISED),
        path(fmt(g["bevel"]), LABEL, FINE),
        path(fmt(g["panel"]), ACCENT, STROKE, fill=PANEL),
    ], "SoundPEATS H3 left earbud")


# Front view, lid closed. Fractions of the case width (x) and height (y).
# Outline: |x/a|^P + |y/b|^Q = 1, measured from a photo of the back of the case
# (levelled and mirrored; within 2% of the photo's width at every height). Its
# width-to-height ratio matches the published 70 x 48 mm.
# Layout: measured down the middle of a front photo taken from about 18 degrees above,
# corrected for that angle with the case's 31 mm depth. The lid is smoke-tinted and
# see-through; the nameplate sits behind it, just above the seam band. The rear plate
# is part of the hinge and does not show from the front.
H3_CASE = dict(aspect=1.463, P=4.05, Q=1.85,
               band=(0.64, 0.71),                 # seam band: top, bottom
               plate=(0.30, 0.41, 0.40, 0.21))    # nameplate: x, y, w, h


def h3_case():
    c = H3_CASE
    W = 100
    H = W / c["aspect"]
    X, Y = 10, (96 - H) / 2
    cx, cy, a, b = X + W / 2, Y + H / 2, W / 2, H / 2

    def at(t, s=1.0):
        ct, st = math.cos(t), math.sin(t)
        return (cx + s * a * math.copysign(abs(ct) ** (2 / c["P"]), ct),
                cy - s * b * math.copysign(abs(st) ** (2 / c["Q"]), st))

    def t_below(y):
        """Parameter on the right-hand side of the outline where it crosses y (below the middle)."""
        return -math.asin(((y - cy) / b) ** (c["Q"] / 2))

    outline = curve_through([at(2 * math.pi * i / 72) for i in range(72)])
    band_top, band_bottom = (Y + f * H for f in c["band"])
    t0, t1 = t_below(band_top), t_below(band_bottom)
    # lid: over the top from the left end of the band to the right end
    lid_pts = [at(math.pi - t0 - (math.pi - 2 * t0) * i / 40) for i in range(41)]
    lid = curve_through(lid_pts, closed=False) + [("Z", [])]
    # band: follows the outline at both ends
    right = [at(t0 + (t1 - t0) * i / 4) for i in range(5)]
    left = [(2 * cx - x, y) for x, y in reversed(right)]
    band = "M" + "L".join(f"{x:.1f} {y:.1f}" for x, y in right + left) + "Z"
    shine = curve_through([at(math.pi * (0.62 + 0.2 * i / 8), 0.86) for i in range(9)], closed=False)
    px, py, pw, ph = c["plate"]
    return drawing(120, 96, [
        path(dashes(cx, 3, cx, 93), LABEL, FINE),
        path(fmt(outline), fill=PANEL),
        path(fmt(lid), fill=RAISED),
        path(fmt(shine), LABEL, FINE),
        path(rounded_rect(X + px * W, Y + py * H, pw * W, ph * H, 1.2), ACCENT, 1.4),
        path(band, fill=ACCENT),
        path(fmt(outline), INK, STROKE),
    ], "SoundPEATS H3 charging case")


# -------------------------------------------------------- Air5 Pro+ ---------
# Proportions estimated by eye from reference photos. The coordinates below are in an
# arbitrary working space about 400 wide and 680 tall; fit() scales them to the icon.
# Drawn as the LEFT earbud seen from outside: head on top, ear tip pointing left,
# stem straight down, inlay running from the head down the stem. Which side this
# is has not been checked against the L/R marking on a real pair.
A5_BODY = ("M265 5C345 5 405 60 405 135C405 215 378 255 378 340L378 615"
           "C378 650 345 675 300 675C255 675 222 650 222 615L222 400"
           "C222 350 215 318 190 308C150 296 105 290 85 235C60 150 150 5 265 5Z")
A5_INLAY = ("M308 88C352 88 385 122 385 165C385 225 345 270 343 345C342 420 352 500 352 570"
            "C352 605 335 628 306 628C277 628 260 605 260 570C260 500 284 420 283 345"
            "C282 270 232 225 232 165C232 122 264 88 308 88Z")
A5_AXIS_X, A5_HEAD_Y = 302, 165


def a5_shapes():
    shapes = {
        "body": parse(A5_BODY),
        "inlay": parse(A5_INLAY),
        "tip": ellipse_path(108, 220, 108, 125),              # silicone tip, side-on
        "collar": parse("M78 168C50 195 48 262 96 300"),      # nozzle band between tip and head
        "shine": parse("M150 128C166 88 196 62 236 52"),
        "axis": parse(f"M{A5_AXIS_X} {A5_HEAD_Y}"),           # carried through fit() for the guides
    }
    for i, y in enumerate((440, 482, 524, 566)):              # nod to the perforated lower inlay
        shapes[f"dot{i}"] = circle_path(306, y, 7)
    return shapes


def a5_bud():
    g, s = fit(a5_shapes(), 96, 96, 4.5)
    mx, ay = g["axis"][0][1][0]
    return drawing(96, 96, [
        path(dashes(mx, 2, mx, 94) + dashes(2, ay, 94, ay), LABEL, FINE),
        path(fmt(g["tip"]), INK, STROKE, fill=PANEL),
        path(fmt(g["collar"]), ACCENT, 1.6),
        path(fmt(g["body"]), INK, STROKE, fill=PANEL),
        path(fmt(g["inlay"]), ACCENT, STROKE, fill=RAISED),
        path("".join(fmt(g[f"dot{i}"]) for i in range(4)), fill=ACCENT),
        path(fmt(g["shine"]), INK, DETAIL),
    ], "SoundPEATS Air5 Pro+ left earbud")


def a5_case():
    # Front view, lid closed: an opaque pebble, widest just below the middle. The seam
    # is a straight horizontal line. (In photos taken from above it looks curved, and
    # the dip in the rim only shows with the lid open.) Status light under the seam.
    outline = "M10 49C10 24 28 11 60 11C92 11 110 24 110 49C110 70 90 84 60 84C30 84 10 70 10 49Z"
    seam_y = 58.0
    # where the seam meets the outline: solve the lower-right curve for y = seam_y
    P = [(110, 49), (110, 70), (90, 84), (60, 84)]
    bez = lambda t, i: sum(c * P[j][i] * (1 - t) ** (3 - j) * t ** j for j, c in enumerate((1, 3, 3, 1)))
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if bez(mid, 1) < seam_y else (lo, mid)
    xr = bez(lo, 0)
    return drawing(120, 96, [
        path(dashes(60, 3, 60, 93), LABEL, FINE),
        path(outline, INK, STROKE, fill=PANEL),
        path(f"M{120 - xr:.1f} {seam_y}L{xr:.1f} {seam_y}", INK, DETAIL),
        path("M19 40C21 27 31 19 46 16.5", LABEL, FINE),       # lid highlight
        path(fmt(circle_path(60, 70, 1.8)), fill=ACCENT),                # status light
    ], "SoundPEATS Air5 Pro+ charging case")


DEVICES = {
    "h3": ("SoundPEATS H3", h3_bud, h3_case),
    "air5-pro-plus": ("SoundPEATS Air5 Pro+", a5_bud, a5_case),
}
only = ARGS.models.split(",") if ARGS.models else list(DEVICES)

cells = []
for model in only:
    name, bud, case = DEVICES[model]
    d = OUT / "devices" / model / "studio"
    d.mkdir(parents=True, exist_ok=True)
    RES.mkdir(parents=True, exist_ok=True)
    for part, dr in (("bud", bud()), ("case", case())):
        (d / f"{part}.svg").write_text(svg(dr), newline="\n")
        (RES / f"device_{model.replace('-', '_')}_{part}.xml").write_text(vector(dr), newline="\n")
    cells.append((name, svg(bud()), svg(case())))

if ARGS.sheet:
    def big(s, w):
        return s.replace('width="96" height="96"', f'width="{w}" height="{w}"').replace(
            'width="120" height="96"', f'width="{int(w * 1.25)}" height="{w}"')

    rows = []
    for name, bud_svg, case_svg in cells:
        right = bud_svg.replace('viewBox="0 0 96 96"', 'viewBox="0 0 96 96" style="transform: scaleX(-1)"')
        rows.append(f'''
    <section style="background:{BACKGROUND};color:{INK};padding:20px 24px;display:flex;align-items:center;gap:28px">
      <div style="width:150px;font:600 15px/1.3 sans-serif">{name}</div>
      <div style="background:{PANEL};border:1px solid {HAIRLINE};border-radius:6px;padding:14px;display:flex;gap:18px;align-items:center">
        {big(bud_svg, 240)}{big(case_svg, 240)}
      </div>
      <div style="background:{PANEL};border:1px solid {HAIRLINE};border-radius:6px;padding:12px 16px;display:flex;gap:22px;align-items:center">
        {big(bud_svg, 72)}{big(case_svg, 72)}{big(right, 72)}
      </div>
    </section>''')
    pathlib.Path(ARGS.sheet).write_text(
        '<!doctype html><meta charset="utf-8"><body style="margin:0;width:1080px">' + "".join(rows) + "</body>")
print("wrote", sum(1 for _ in (OUT / "devices").rglob("*.svg")), "svg files and",
      sum(1 for _ in RES.glob("device_*.xml")), "vector drawables")
