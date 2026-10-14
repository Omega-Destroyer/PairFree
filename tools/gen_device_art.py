"""Generate the device drawings under assets/devices/soundpeats/ (Studio look).

Each drawing is also written as an Android vector drawable,
android/app/src/main/res/drawable/device_<model>_<part>.xml, so the app uses the
same shapes. Change the shapes here, never in the generated files.

H3: the earbud outlines are traced from a front-view product photo of the left
earbud: the shell, the chrome rim, the faceplate and the gold nozzle band, plus an
ellipse fitted to the ear tip. The case is drawn from the front: its outline was
measured from a photo of the back, and the lid, nameplate, seam band and base from
a photo of the front. The photos are not in the repo. Logos, text and the faceplate
pattern are left out.

Air5 Pro+: the earbud is traced from SoundPEATS' face-on product photo of the
pair (shell, gold inlay, nozzle band, an ellipse for the ear tip). The case is
traced from their photo of the closed Air5 Pro case, which the Air5 Pro+ shares,
taken from the front and a little above.

Other models: traced the same way from SoundPEATS' product photos and kept as data
in tools/device_art/soundpeats/<slug>.json, each with a note on the photo it came from. Where
no photo shows a part on a background it can be told apart from, the model uses the
generic earbud or case. Models with no file at all get both generic drawings.

The script also writes the app's lookup table, DeviceArtTable.kt: drawings by
Bluetooth name first, then by firmware code.

Output uses only paths, circles and rects with solid fills and plain presentation
attributes (no CSS, opacity, dash arrays or transforms), so the files convert
cleanly to Android vector drawables. Colours are the Studio palette in docs/DESIGN.md.

Usage:
    python tools/gen_device_art.py                    write every model into assets/
    python tools/gen_device_art.py --models h3        only some models (comma separated)
    python tools/gen_device_art.py --sheet sheet.html also write a preview page
"""
import argparse
import json
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
# Traced from SoundPEATS' face-on product photo of the pair, in photo pixels: the
# left-hand bud, whose ear tip points left, which is forward when worn, as for the H3.
#   body   the shell: head, the lobe that sits in the ear, and the stem
#   inlay  the gold faceplate, down the stem (its logo and dot pattern are left out)
#   ring   the gold nozzle band at the base of the ear tip
# The ear tip is an ellipse fitted to its visible edge; the body hides the rest of it.
A5_BUD = {
    "body": """
        698,641 653,646 610,656 567,670 526,688 487,711 450,736 415,765 383,796 353,829 326,865 316,908
        313,953 311,998 308,1042 305,1087 302,1132 300,1177 297,1222 294,1266 297,1310 325,1345 360,1374 401,1391
        408,1414 452,1417 497,1420 542,1423 587,1425 627,1435 627,1480 627,1524 628,1569 628,1614 628,1659 629,1704
        629,1749 629,1794 630,1839 630,1884 630,1929 630,1973 631,2018 631,2063 631,2108 632,2153 633,2198 639,2242
        651,2286 671,2326 701,2359 739,2382 782,2395 826,2400 871,2395 913,2380 950,2355 980,2321 1000,2281 1013,2238
        1018,2194 1020,2149 1020,2104 1019,2059 1019,2014 1019,1969 1019,1925 1018,1880 1018,1835 1018,1790 1017,1745 1017,1700
        1017,1655 1017,1610 1016,1565 1016,1520 1016,1476 1016,1431 1016,1386 1018,1341 1021,1296 1028,1252 1040,1209 1060,1169
        1075,1126 1085,1083 1090,1038 1090,993 1085,949 1075,905 1059,863 1038,823 1012,787 982,753 948,724 912,698
        872,676 831,660 787,648 743,642
    """,
    "inlay": """
        812,862 753,869 701,897 660,938 652,997 659,1056 677,1112 707,1163 727,1219 751,1274 770,1329 784,1387
        789,1446 794,1506 796,1565 797,1624 790,1683 785,1743 778,1802 769,1860 760,1919 751,1978 745,2037 745,2096
        751,2155 774,2210 814,2252 871,2255 921,2225 948,2172 954,2113 955,2054 952,1994 943,1935 937,1876 928,1818
        922,1759 917,1700 911,1641 910,1581 909,1522 914,1462 920,1403 931,1345 948,1288 967,1232 995,1179 1011,1122
        1030,1066 1034,1007 1020,950 981,905 929,878 871,864
    """,
    "ring": """
        237,1068 221,1076 205,1086 193,1099 182,1114 173,1129 175,1147 179,1164 184,1182 189,1199 196,1216 202,1233
        210,1250 217,1266 226,1282 234,1298 244,1313 253,1329 264,1344 275,1358 286,1372 298,1386 310,1399 326,1407
        344,1411 362,1411 380,1408 397,1402 401,1392 384,1387 367,1379 352,1370 338,1358 325,1346 313,1332 301,1318
        291,1303 281,1288 272,1273 263,1257 256,1240 249,1223 243,1206 239,1189 235,1171 237,1154 244,1139 241,1121
        239,1103 239,1085
    """,
}
A5_TIP = (419.7, 1180.7, 342.7, 361.5, 69.8)   # ear tip: centre, semi-axes, rotation (deg)
A5_AXIS = (848.4, 1180.7)    # inlay centroid x, ear tip centre y: where the guides cross


def a5_bud():
    shapes = {k: curve_through(points(v)) for k, v in A5_BUD.items()}
    cx, cy, rx, ry, ang = A5_TIP
    shapes["tip"] = tf(ellipse_path(cx, cy, rx, ry), rotate_about(cx, cy, ang))
    shapes["centre"] = [("M", [A5_AXIS])]
    g, _ = fit(shapes, 96, 96, 4.5)
    mx, my = g["centre"][0][1][0]
    return drawing(96, 96, [
        path(dashes(mx, 2, mx, 94) + dashes(2, my, 94, my), LABEL, FINE),
        path(fmt(g["tip"]), INK, STROKE, fill=PANEL),
        path(fmt(g["ring"]), fill=ACCENT),
        path(fmt(g["body"]), INK, STROKE, fill=PANEL),
        path(fmt(g["inlay"]), ACCENT, STROKE, fill=RAISED),
    ], "SoundPEATS Air5 Pro+ left earbud")


# The case, closed, from the front and a little above, traced from SoundPEATS' photo
# of the Air5 Pro, which uses the same case (same shape, plate and status light). From
# this height the seam dips in the middle, where the recessed plate sits under it; the
# plate's text is left out.
A5_CASE = {
    "outline": """
        660,764 620,766 580,770 541,778 503,790 467,807 433,829 402,854 374,882 349,913 328,947 312,984
        300,1022 293,1061 289,1101 289,1141 294,1181 303,1220 316,1258 334,1293 356,1327 381,1357 410,1385 442,1409
        477,1429 513,1445 552,1456 591,1462 631,1466 671,1467 711,1467 751,1467 791,1467 831,1467 871,1467 911,1466
        951,1464 990,1458 1029,1448 1066,1433 1101,1414 1134,1390 1163,1364 1190,1334 1213,1301 1232,1266 1246,1228 1255,1190
        1261,1150 1262,1110 1259,1070 1253,1031 1242,992 1227,955 1207,921 1183,889 1156,860 1125,834 1092,812 1056,794
        1019,780 979,772 940,767 900,765 860,764 820,764 780,764 740,764 700,764
    """,
    "seam_left": "325,1206 345,1219 365,1232 385,1243 405,1251 425,1257 445,1261 465,1264 485,1267 505,1268 525,1269",
    "seam_right": "1010,1271 1030,1269 1050,1268 1070,1266 1090,1264 1110,1260 1130,1255 1150,1249 1170,1240 1190,1230 1210,1216 1230,1200",
}
A5_PLATE = (574, 977, 1283)   # the plate's bright lower lip: left end, right end, height
A5_LED = (775.2, 1344.3, 8.5)     # status light: centre, radius


def join(*curves):
    """One path from open curves, each continuing where the last ended."""
    out = []
    for i, c in enumerate(curves):
        out += [("L" if i and cmd == "M" else cmd, pts) for cmd, pts in c]
    return out + [("Z", [])]


def a5_case():
    outline = points(A5_CASE["outline"])
    seam = points(A5_CASE["seam_left"]) + points(A5_CASE["seam_right"])
    lx, rx, ly = A5_PLATE
    sl, sr = points(A5_CASE["seam_left"])[-1], points(A5_CASE["seam_right"])[0]
    # the lid: over the top of the outline, from the seam's right end back to its left end
    i_l = min(range(len(outline)), key=lambda i: math.dist(outline[i], seam[0]))
    i_r = min(range(len(outline)), key=lambda i: math.dist(outline[i], seam[-1]))
    over = [outline[i % len(outline)] for i in range(i_r, i_l + len(outline) + 1)] if i_r > i_l else outline[i_r:i_l + 1]
    cx = sum(p[0] for p in outline) / len(outline)
    cy = sum(p[1] for p in outline) / len(outline)
    upper_left = [p for p in outline if p[0] < cx and p[1] < cy - 100]
    shapes = {
        "outline": curve_through(outline),
        "lid": join(curve_through(over, closed=False), curve_through(seam, closed=False)),
        "seam": curve_through(seam, closed=False),
        "lip": parse(f"M{sl[0] + 15} {sl[1]}L{lx} {ly}L{rx} {ly}L{sr[0] - 10} {sr[1]}"),
        "shine": curve_through([(cx + (x - cx) * 0.86, cy + (y - cy) * 0.86) for x, y in upper_left[2:-2]], closed=False),
        "led": circle_path(A5_LED[0], A5_LED[1], A5_LED[2] * 1.4),
        "axis": [("M", [(cx, cy)])],
    }
    g, _ = fit(shapes, 120, 96, 4)
    mx = g["axis"][0][1][0][0]
    return drawing(120, 96, [
        path(dashes(mx, 3, mx, 93), LABEL, FINE),
        path(fmt(g["outline"]), fill=PANEL),
        path(fmt(g["lid"]), fill=RAISED),
        path(fmt(g["shine"]), LABEL, FINE),
        path(fmt(g["seam"]), INK, DETAIL),
        path(fmt(g["lip"]), ACCENT, 1.4),
        path(fmt(g["led"]), fill=ACCENT),
        path(fmt(g["outline"]), INK, STROKE),
    ], "SoundPEATS Air5 Pro+ charging case")


# ------------------------------------------------------- traced models -----
# Every other model is traced the same way and kept as data in tools/device_art/soundpeats/<slug>.json:
#   name, names       the product name, and the Bluetooth names that use these drawings
#   source            where the outlines were traced from
#   bud               one earbud, or the whole product for headphones and neckbands, or
#                     "generic" when no photo shows it well enough:
#     kind            "bud" (the default), "headphones" or "neckband"
#     body            outline, "x,y x,y ..." in photo pixels
#     tip             ear tip ellipse: centre, semi-axes, rotation (deg), drawn under the body
#     accent          gold or coloured parts, filled amber
#     panel           a faceplate or touch panel, outlined amber
#     holes           openings through the part (a clip's ring), outlined
#     lines           open lines for edges inside the outline
#     axis            where the guides cross
#   case              the charging case from the front, lid closed; "generic" when no photo
#                     shows it well enough, or null when there is none:
#     outline, seam (open line where the lid meets the base), led (centre, radius), lines,
#     accent (coloured bands, filled amber)
ART_DATA = _ROOT / "tools" / "device_art" / "soundpeats"


def traced_bud(data, label):
    b = data["bud"]
    shapes = {"body": curve_through(points(b["body"]))}
    if "tip" in b:
        cx, cy, rx, ry, ang = b["tip"]
        shapes["tip"] = tf(ellipse_path(cx, cy, rx, ry), rotate_about(cx, cy, ang))
    for i, a in enumerate(b.get("accent", [])):
        shapes[f"accent{i}"] = curve_through(points(a))
    for i, a in enumerate(b.get("panel", [])):
        shapes[f"panel{i}"] = curve_through(points(a))
    for i, a in enumerate(b.get("lines", [])):
        shapes[f"line{i}"] = curve_through(points(a), closed=False)
    for i, a in enumerate(b.get("holes", [])):
        shapes[f"hole{i}"] = curve_through(points(a))
    shapes["centre"] = [("M", [tuple(b["axis"])])]
    g, _ = fit(shapes, 96, 96, 4.5)
    mx, my = g["centre"][0][1][0]
    els = [path(dashes(mx, 2, mx, 94) + dashes(2, my, 94, my), LABEL, FINE)]
    if "tip" in g:
        els.append(path(fmt(g["tip"]), INK, STROKE, fill=PANEL))
    els.append(path(fmt(g["body"]), INK, STROKE, fill=PANEL))
    els += [path(fmt(g[k]), fill=ACCENT) for k in g if k.startswith("accent")]
    els += [path(fmt(g[k]), ACCENT, STROKE, fill=RAISED) for k in g if k.startswith("panel")]
    els += [path(fmt(g[k]), INK, DETAIL) for k in g if k.startswith("line")]
    els += [path(fmt(g[k]), INK, STROKE) for k in g if k.startswith("hole")]
    return drawing(96, 96, els, label)


def traced_case(data, label):
    c = data["case"]
    outline = points(c["outline"])
    cx = sum(p[0] for p in outline) / len(outline)
    cy = sum(p[1] for p in outline) / len(outline)
    top = min(p[1] for p in outline)
    shapes = {"outline": curve_through(outline), "axis": [("M", [(cx, cy)])]}
    if "seam" in c:
        seam = points(c["seam"])
        i_l = min(range(len(outline)), key=lambda i: math.dist(outline[i], seam[0]))
        i_r = min(range(len(outline)), key=lambda i: math.dist(outline[i], seam[-1]))
        over = [outline[i % len(outline)] for i in range(i_r, i_l + len(outline) + 1)] if i_r > i_l else outline[i_r:i_l + 1]
        shapes["lid"] = join(curve_through(over, closed=False), curve_through(seam, closed=False))
        shapes["seam"] = curve_through(seam, closed=False)
    # a highlight along the upper left, inside the outline
    arc = sorted((math.atan2(y - cy, x - cx), x, y) for x, y in outline)
    arc = [(x, y) for t, x, y in arc if math.radians(-160) < t < math.radians(-115)]
    if len(arc) > 3:
        shapes["shine"] = curve_through([(cx + (x - cx) * 0.86, cy + (y - cy) * 0.86) for x, y in arc], closed=False)
    for i, a in enumerate(c.get("lines", [])):
        shapes[f"line{i}"] = curve_through(points(a), closed=False)
    for i, a in enumerate(c.get("accent", [])):
        shapes[f"accent{i}"] = curve_through(points(a))
    if "led" in c:
        shapes["led"] = circle_path(c["led"][0], c["led"][1], c["led"][2])
    g, _ = fit(shapes, 120, 96, 4)
    mx = g["axis"][0][1][0][0]
    els = [path(dashes(mx, 3, mx, 93), LABEL, FINE), path(fmt(g["outline"]), fill=PANEL)]
    if "lid" in g:
        els.append(path(fmt(g["lid"]), fill=RAISED))
    if "shine" in g:
        els.append(path(fmt(g["shine"]), LABEL, FINE))
    if "seam" in g:
        els.append(path(fmt(g["seam"]), INK, DETAIL))
    els += [path(fmt(g[k]), INK, DETAIL) for k in g if k.startswith("line")]
    els += [path(fmt(g[k]), fill=ACCENT) for k in g if k.startswith("accent")]
    if "led" in g:
        els.append(path(fmt(g["led"]), fill=ACCENT))
    els.append(path(fmt(g["outline"]), INK, STROKE))
    return drawing(120, 96, els, label)


# ------------------------------------------------------------- generic -------
# For models with no drawings of their own: a plain earbud and case, not any one model.
def generic_bud():
    shapes = {
        "tip": tf(ellipse_path(30, 40, 16, 19), rotate_about(30, 40, -20)),
        "body": ellipse_path(55, 48, 30, 28),
        "panel": ellipse_path(58, 49, 19, 17),
        "centre": [("M", [(58, 49)])],
    }
    g, _ = fit(shapes, 96, 96, 8)
    mx, my = g["centre"][0][1][0]
    return drawing(96, 96, [
        path(dashes(mx, 2, mx, 94) + dashes(2, my, 94, my), LABEL, FINE),
        path(fmt(g["tip"]), INK, STROKE, fill=PANEL),
        path(fmt(g["body"]), INK, STROKE, fill=PANEL),
        path(fmt(g["panel"]), ACCENT, STROKE, fill=RAISED),
    ], "Earbud")


def generic_case():
    outline = "M14 50C14 26 32 14 60 14C88 14 106 26 106 50C106 72 88 84 60 84C32 84 14 72 14 50Z"
    return drawing(120, 96, [
        path(dashes(60, 3, 60, 93), LABEL, FINE),
        path(outline, INK, STROKE, fill=PANEL),
        path("M15 56L105 56", INK, DETAIL),
        path(fmt(circle_path(60, 68, 1.8)), fill=ACCENT),
    ], "Charging case")


# For headphones and speakers, which report one battery: the whole product, not any one model.
def generic_headphones():
    return drawing(96, 96, [
        path(dashes(48, 2, 48, 94), LABEL, FINE),
        path("M20 58C20 30 32 12 48 12C64 12 76 30 76 58", INK, STROKE),
        path(rounded_rect(10, 50, 20, 34, 9), INK, STROKE, fill=PANEL),
        path(rounded_rect(66, 50, 20, 34, 9), INK, STROKE, fill=PANEL),
        path(rounded_rect(16, 57, 8, 20, 4), ACCENT, STROKE, fill=RAISED),
        path(rounded_rect(72, 57, 8, 20, 4), ACCENT, STROKE, fill=RAISED),
    ], "Headphones")


def generic_speaker():
    return drawing(96, 96, [
        path(dashes(48, 2, 48, 94), LABEL, FINE),
        path(rounded_rect(8, 26, 80, 46, 12), INK, STROKE, fill=PANEL),
        path(rounded_rect(16, 36, 64, 28, 7), ACCENT, STROKE, fill=RAISED),
        path(fmt(circle_path(48, 31, 1.6)), fill=ACCENT),
    ], "Speaker")


# ------------------------------------------------------------- output --------
# slug: (name, bud, case or None, Bluetooth names that use these drawings)
DEVICES = {
    "h3": ("SoundPEATS H3", h3_bud, h3_case, ["SOUNDPEATS H3"]),
    "air5-pro-plus": ("SoundPEATS Air5 Pro+", a5_bud, a5_case, ["SOUNDPEATS Air5 Pro+"]),
}


def _traced(d):
    generic = d["bud"] == "generic"
    kind = "bud" if generic else d["bud"].get("kind", "bud")
    bud_label = f"{d['name']} left earbud" if kind == "bud" else d["name"]
    return (
        d["name"],
        generic_bud if generic else (lambda: traced_bud(d, bud_label)),
        (lambda: traced_case(d, f"{d['name']} charging case")) if isinstance(d.get("case"), dict) else d.get("case"),
        d["names"],
    )


for f in sorted(ART_DATA.glob("*.json")) if ART_DATA.exists() else []:
    DEVICES[f.stem] = _traced(json.loads(f.read_text(encoding="utf-8")))
DEVICES["generic"] = ("Any other model", generic_bud, generic_case, [])
DEVICES["generic-headphones"] = ("Any headphones", generic_headphones, None, [])
DEVICES["generic-speaker"] = ("Any speaker", generic_speaker, None, [])
only = ARGS.models.split(",") if ARGS.models else list(DEVICES)

cells = []
for model in only:
    name, bud, case, _names = DEVICES[model]
    d = OUT / "devices" / "soundpeats" / model / "studio"
    d.mkdir(parents=True, exist_ok=True)
    RES.mkdir(parents=True, exist_ok=True)
    for part, fn in (("bud", bud), ("case", case)):
        if not callable(fn) or fn in (generic_bud, generic_case) and model != "generic":
            continue
        dr = fn()
        (d / f"{part}.svg").write_text(svg(dr), newline="\n")
        (RES / f"device_{model.replace('-', '_')}_{part}.xml").write_text(vector(dr), newline="\n")
    cells.append((name, svg(bud()), svg(case()) if callable(case) else svg(generic_case()) if case == "generic" else ""))


# The app's lookup. The Bluetooth name decides first, because some firmware codes are
# shared by models that look different (Air3 and Air3 Deluxe); codes only fill in.
def kotlin_art(slug):
    r = slug.replace("-", "_")
    bud = "R.drawable.device_generic_bud" if DEVICES[slug][1] is generic_bud else f"R.drawable.device_{r}_bud"
    c = DEVICES[slug][2]
    case = f"R.drawable.device_{r}_case" if callable(c) else "R.drawable.device_generic_case" if c == "generic" else "null"
    return f"DeviceArt({bud}, {case})"


by_name = {n.lower(): slug for slug, (_, _, _, names) in DEVICES.items() for n in names}
by_code = {}
for dev in json.loads((_ROOT / "data" / "soundpeats" / "devices.json").read_text(encoding="utf-8"))["devices"]:
    slugs = {by_name[n.lower()] for n in dev["names"] if n.lower() in by_name}
    by_code.setdefault(dev["firmware_code"], set()).update(slugs)
by_code = {code: s.pop() for code, s in by_code.items() if len(s) == 1}
KT = _ROOT / "android" / "app" / "src" / "main" / "kotlin" / "io" / "github" / "omegadestroyer" / "soundpeter" / "ui" / "DeviceArtTable.kt"
KT.write_text(
    "// Generated by tools/gen_device_art.py from its drawings and data/soundpeats/devices.json. Do not edit.\n"
    "package io.github.omegadestroyer.soundpeter.ui\n\n"
    "import io.github.omegadestroyer.soundpeter.R\n\n"
    "/** Drawings by Bluetooth name, lower case. */\n"
    "internal val ART_BY_NAME: Map<String, DeviceArt> = mapOf(\n"
    + "".join(f'    "{n}" to {kotlin_art(s)},\n' for n, s in sorted(by_name.items()))
    + ")\n\n"
    "/** Drawings by firmware code, for codes only one look uses. */\n"
    "internal val ART_BY_CODE: Map<String, DeviceArt> = mapOf(\n"
    + "".join(f'    "{c}" to {kotlin_art(s)},\n' for c, s in sorted(by_code.items()))
    + ")\n\n"
    "/** For models without drawings of their own. */\n"
    f"internal val GENERIC_ART: DeviceArt = {kotlin_art('generic')}\n"
    f"internal val GENERIC_HEADPHONES_ART: DeviceArt = {kotlin_art('generic-headphones')}\n"
    f"internal val GENERIC_SPEAKER_ART: DeviceArt = {kotlin_art('generic-speaker')}\n",
    newline="\n",
)

if ARGS.sheet:
    def big(s, w):
        return s.replace('width="96" height="96"', f'width="{w}" height="{w}"').replace(
            'width="120" height="96"', f'width="{int(w * 1.25)}" height="{w}"')

    rows = []
    for name, bud_svg, case_svg in cells:
        right = bud_svg.replace('viewBox="0 0 96 96"', 'viewBox="0 0 96 96" style="transform: scaleX(-1)"')
        rows.append(f"""
    <section style="background:{BACKGROUND};color:{INK};padding:12px 24px;display:flex;align-items:center;gap:28px">
      <div style="width:150px;font:600 15px/1.3 sans-serif">{name}</div>
      <div style="background:{PANEL};border:1px solid {HAIRLINE};border-radius:6px;padding:10px;display:flex;gap:18px;align-items:center">
        {big(bud_svg, 160)}{big(case_svg, 160)}
      </div>
      <div style="background:{PANEL};border:1px solid {HAIRLINE};border-radius:6px;padding:12px 16px;display:flex;gap:22px;align-items:center">
        {big(bud_svg, 64)}{big(case_svg, 64)}{big(right, 64)}
      </div>
    </section>""")
    pathlib.Path(ARGS.sheet).write_text(
        '<!doctype html><meta charset="utf-8"><body style="margin:0;width:1080px">' + "".join(rows) + "</body>")
print("wrote", sum(1 for _ in (OUT / "devices").rglob("*.svg")), "svg files,",
      sum(1 for _ in RES.glob("device_*.xml")), "vector drawables and", KT.name)
