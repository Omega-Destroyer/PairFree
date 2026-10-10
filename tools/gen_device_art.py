"""Generate the device drawings under assets/devices/.

One geometry per device part, styled once per look. Output uses only paths,
circles and rects with plain presentation attributes (no CSS, no dash arrays,
no transforms), so the files convert cleanly to Android vector drawables.

Shapes are original simplified outlines. Proportions were estimated by eye from
product photos used as reference only. No photo, logo or wordmark is reproduced.

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
_ap.add_argument("--out", default=str(pathlib.Path(__file__).resolve().parent.parent / "assets"))
_ap.add_argument("--models", default="")
_ap.add_argument("--sheet", default="")
ARGS = _ap.parse_args()
OUT = pathlib.Path(ARGS.out)

LOOKS = {
    "studio": dict(ink="#E8E6E1", sw=1.5, detail_sw=1.1, dim="#5A616B", dim_sw=1.0,
                   accent="#E6A23C", accent_sw=1.6, panel="#181B1F",
                   face_fill="#E6A23C", face_alpha=0.16, face_stroke="#E6A23C",
                   shadow=None, lid="#1F2328", tip_fill="#181B1F", guides=True),
    "sketchbook": dict(ink="#33423F", sw=2.6, detail_sw=1.8, dim="#7F908B", dim_sw=1.6,
                       accent="#2B7670", accent_sw=2.4, panel="#FBFCFA",
                       face_fill="#F2B8A0", face_alpha=1, face_stroke="#33423F",
                       shadow="#C5D5CF", lid="#D6E9E5", tip_fill="#FBFCFA", guides=False),
}


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


def scale_about(cx, cy, s):
    return lambda x, y: (cx + (x - cx) * s, cy + (y - cy) * s)


def rotate_about(cx, cy, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return lambda x, y: (cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca)


def circle_path(cx, cy, r):
    k = 0.5523 * r
    return parse(f"M{cx - r} {cy}C{cx - r} {cy - k} {cx - k} {cy - r} {cx} {cy - r}"
                 f"C{cx + k} {cy - r} {cx + r} {cy - k} {cx + r} {cy}"
                 f"C{cx + r} {cy + k} {cx + k} {cy + r} {cx} {cy + r}"
                 f"C{cx - k} {cy + r} {cx - r} {cy + k} {cx - r} {cy}Z")


def offset(shape, d):
    """Offset a closed, tangent-continuous M/C/Z path outward by d (inward if d < 0).

    Tiller-Hanson: move each leg of every control polygon along its normal and
    intersect neighbouring legs. The path must run clockwise on screen."""
    def unit(a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        n = math.hypot(dx, dy) or 1.0
        return dx / n, dy / n

    def meet(p, u, q, v):
        den = u[0] * v[1] - u[1] * v[0]
        if abs(den) < 1e-6:
            return None
        t = ((q[0] - p[0]) * v[1] - (q[1] - p[1]) * v[0]) / den
        return p[0] + u[0] * t, p[1] + u[1] * t

    out, cur = [], None
    for cmd, pts in shape:
        if cmd == "M":
            cur = pts[0]
            out.append(None)                      # filled in from the first curve
        elif cmd == "C":
            P = [cur] + list(pts)
            legs = [unit(P[i], P[i + 1]) for i in range(3)]
            nrm = [(u[1] * d, -u[0] * d) for u in legs]
            q0 = (P[0][0] + nrm[0][0], P[0][1] + nrm[0][1])
            q3 = (P[3][0] + nrm[2][0], P[3][1] + nrm[2][1])
            a = [(P[i][0] + nrm[i][0], P[i][1] + nrm[i][1]) for i in range(3)]
            q1 = meet(a[0], legs[0], a[1], legs[1]) or (P[1][0] + nrm[0][0], P[1][1] + nrm[0][1])
            q2 = meet(a[1], legs[1], a[2], legs[2]) or (P[2][0] + nrm[2][0], P[2][1] + nrm[2][1])
            if out[0] is None:
                out[0] = ("M", [q0])
            out.append(("C", [q1, q2, q3]))
            cur = P[3]
        else:
            out.append((cmd, []))
    return out


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


def path(d, stroke=None, sw=None, fill="none", alpha=None):
    s = f'<path d="{d}" fill="{fill}"'
    if alpha is not None and alpha != 1:
        s += f' fill-opacity="{alpha}"'
    if stroke:
        s += f' stroke="{stroke}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"'
    return s + "/>"


def svg(w, h, body, label):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" '
            f'role="img" aria-label="{label}">\n' + "\n".join(body) + "\n</svg>\n")


# ---------------------------------------------------------------- H3 --------
# Faceplate seen face-on: a round lobe on a short neck, symmetric about its long
# axis (y = 52). Proportions estimated from a reference photo: length to lobe height
# about 1.37, neck about 0.37 of the lobe height, widest point about three quarters
# of the way along. Drawn as the LEFT earbud:
# neck on the left, ear tip behind the neck and above the axis.
_H3_UPPER = [((14, 46.5), (18, 42), (24, 42)), ((30, 42), (34, 42.8), (39, 42.5)),
             ((45, 42.14), (52, 25), (66, 25)), ((80, 25), (88, 36), (88, 52))]
_m = lambda p: (p[0], 104 - p[1])
_H3_LOWER = [(_m(c2), _m(c1), _m(prev_end)) for (c1, c2, _e), prev_end in
             reversed(list(zip(_H3_UPPER, [(14, 52)] + [seg[2] for seg in _H3_UPPER[:-1]])))]
H3_FACE = [("M", [(14, 52)])] + [("C", list(seg)) for seg in _H3_UPPER + _H3_LOWER] + [("Z", [])]
H3_C = (52, 52)


def h3_shapes(tilt=0):
    shapes = {
        "face": H3_FACE,
        "shell": offset(H3_FACE, 4.2),                       # clear body: an even rim all round
        "rim": offset(H3_FACE, -3.4),                        # bevel line inside the faceplate
        "tip": circle_path(22, 29, 19),                      # silicone tip, behind the neck
        "bore": circle_path(15.5, 24.5, 5),                  # nozzle ring seen through the tip
        "shine1": parse("M70 34.5C75.5 35.8 80 39.6 82 44.5"),
        "port": circle_path(22, 52, 1.3),                    # small opening at the neck end
    }
    rot = rotate_about(*H3_C, tilt)
    return {k: tf(v, rot) for k, v in shapes.items()}


def h3_bud(k):
    g, s = fit(h3_shapes(), 96, 96, 4.5)
    b = []
    if k["guides"]:
        fp = [p for _, ps in g["face"] for p in ps]
        ax = (min(p[1] for p in fp) + max(p[1] for p in fp)) / 2      # the symmetry axis
        mx = (min(p[0] for p in fp) + max(p[0] for p in fp)) / 2
        b.append(path(dashes(mx, 2, mx, 94) + dashes(2, ax, 94, ax), k["dim"], k["dim_sw"]))
    if k["shadow"]:
        sh = tf(g["shell"], lambda x, y: (x + 3, y + 3))
        b.append(path(fmt(sh), fill=k["shadow"]))
    b.append(path(fmt(g["tip"]), k["ink"], k["sw"], fill=k["tip_fill"]))
    b.append(path(fmt(g["bore"]), k["accent"], k["accent_sw"]))
    b.append(path(fmt(g["shell"]), k["ink"], k["sw"], fill=k["panel"]))
    b.append(path(fmt(g["face"]), k["face_stroke"], k["sw"], fill=k["face_fill"], alpha=k["face_alpha"]))
    b.append(path(fmt(g["rim"]), k["dim"], k["dim_sw"]))
    b.append(path(fmt(g["shine1"]), k["ink"], k["detail_sw"]))
    b.append(path(fmt(g["port"]), fill=k["ink"]))
    return svg(96, 96, b, "SoundPEATS H3 left earbud")


def h3_case(k):
    # Front view, lid closed: a capsule. Tinted lid on top, a thin band at the seam,
    # a framed plate just under the seam, the status light under the plate.
    X, Y, W, H, R = 10, 13, 100, 70, 31
    seam = Y + R                      # 44: sides are vertical from here down to the lower corners
    b = []
    if k["guides"]:
        b.append(path(dashes(60, 4, 60, 92), k["dim"], k["dim_sw"]))
    if k["shadow"]:
        b.append(f'<rect x="{X + 3}" y="{Y + 3}" width="{W}" height="{H}" rx="{R}" fill="{k["shadow"]}"/>')
    b.append(f'<rect x="{X}" y="{Y}" width="{W}" height="{H}" rx="{R}" fill="{k["panel"]}"/>')
    lid = (f"M{X} {seam}C{X} {Y + R * 0.45} {X + R * 0.45} {Y} {X + R} {Y}H{X + W - R}"
           f"C{X + W - R * 0.45} {Y} {X + W} {Y + R * 0.45} {X + W} {seam}Z")
    b.append(path(lid, fill=k["lid"]))
    # the earbuds, seen through the lid
    mini, _ = fit({"a": tf(H3_FACE, rotate_about(*H3_C, -14))}, 25, 18, 0)
    left = tf(mini["a"], lambda x, y: (x + 31, y + 20))
    right = tf(mini["a"], lambda x, y: (120 - (x + 31), y + 20))
    b.append(path(fmt(left) + fmt(right), k["dim"], k["dim_sw"]))
    # lid highlight
    b.append(path(f"M{X + 9} {seam - 7}C{X + 10} {Y + 13} {X + 19} {Y + 7} {X + 30} {Y + 5.5}", k["dim"], k["dim_sw"]))
    # band at the seam
    band = k["accent"] if k["guides"] else "#F2B8A0"
    b.append(f'<rect x="{X}" y="{seam}" width="{W}" height="3.4" fill="{band}"/>')
    if not k["guides"]:
        b.append(path(f"M{X} {seam}H{X + W}M{X} {seam + 3.4}H{X + W}", k["ink"], k["detail_sw"]))
    b.append(f'<rect x="{X}" y="{Y}" width="{W}" height="{H}" rx="{R}" fill="none" stroke="{k["ink"]}" stroke-width="{k["sw"]}"/>')
    # framed plate, left blank on purpose
    plate_fill = "none" if k["guides"] else "#FBE6DA"
    plate_stroke = k["accent"] if k["guides"] else k["ink"]
    b.append(f'<rect x="43" y="{seam + 6.5}" width="34" height="11" rx="1.2" fill="{plate_fill}" '
             f'stroke="{plate_stroke}" stroke-width="{k["detail_sw"] + 0.3}"/>')
    b.append(f'<circle cx="60" cy="{seam + 26}" r="1.8" fill="{k["accent"]}"/>')
    return svg(120, 96, b, "SoundPEATS H3 charging case")


# -------------------------------------------------------- Air5 Pro+ ---------
# Proportions estimated by eye from reference photos. The coordinates below are in an
# arbitrary working space about 400 wide and 680 tall; fit() scales them to the icon.
# Drawn as the LEFT earbud seen from outside: head on top, ear tip pointing left,
# stem straight down, inlay running from the head down the stem. Which side this
# is has not been checked against the L/R marking on a real pair.
def ellipse_path(cx, cy, rx, ry):
    kx, ky = 0.5523 * rx, 0.5523 * ry
    return parse(f"M{cx - rx} {cy}C{cx - rx} {cy - ky} {cx - kx} {cy - ry} {cx} {cy - ry}"
                 f"C{cx + kx} {cy - ry} {cx + rx} {cy - ky} {cx + rx} {cy}"
                 f"C{cx + rx} {cy + ky} {cx + kx} {cy + ry} {cx} {cy + ry}"
                 f"C{cx - kx} {cy + ry} {cx - rx} {cy + ky} {cx - rx} {cy}Z")


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


def a5_bud(k):
    g, s = fit(a5_shapes(), 96, 96, 4.5)
    b = []
    if k["guides"]:
        mx, ay = g["axis"][0][1][0]
        b.append(path(dashes(mx, 2, mx, 94) + dashes(2, ay, 94, ay), k["dim"], k["dim_sw"]))
    if k["shadow"]:
        sh = tf(g["body"], lambda x, y: (x + 3, y + 3))
        b.append(path(fmt(sh), fill=k["shadow"]))
    b.append(path(fmt(g["tip"]), k["ink"], k["sw"], fill=k["tip_fill"]))
    b.append(path(fmt(g["collar"]), k["accent"], k["accent_sw"]))
    b.append(path(fmt(g["body"]), k["ink"], k["sw"], fill=k["panel"]))
    b.append(path(fmt(g["inlay"]), k["face_stroke"], k["sw"], fill=k["face_fill"], alpha=k["face_alpha"]))
    b.append(path("".join(fmt(g[f"dot{i}"]) for i in range(4)), fill=k["accent"]))
    b.append(path(fmt(g["shine"]), k["ink"], k["detail_sw"]))
    return svg(96, 96, b, "SoundPEATS Air5 Pro+ left earbud")


def a5_case(k):
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
    b = []
    if k["guides"]:
        b.append(path(dashes(60, 3, 60, 93), k["dim"], k["dim_sw"]))
    if k["shadow"]:
        b.append(f'<path d="{outline}" fill="{k["shadow"]}" transform="translate(3 3)"/>')
    b.append(path(outline, k["ink"], k["sw"], fill=k["panel"]))
    b.append(path(f"M{120 - xr:.1f} {seam_y}L{xr:.1f} {seam_y}", k["ink"], k["detail_sw"]))
    b.append(path("M19 40C21 27 31 19 46 16.5", k["dim"], k["dim_sw"]))       # lid highlight
    b.append(f'<circle cx="60" cy="70" r="1.8" fill="{k["accent"]}"/>')
    return svg(120, 96, b, "SoundPEATS Air5 Pro+ charging case")


DEVICES = {
    "h3": ("SoundPEATS H3", h3_bud, h3_case),
    "air5-pro-plus": ("SoundPEATS Air5 Pro+", a5_bud, a5_case),
}
only = ARGS.models.split(",") if ARGS.models else list(DEVICES)
ACTIVE_LOOKS = ["studio"]   # Sketchbook is parked for now

cells = []
for model in only:
    name, bud, case = DEVICES[model]
    for look, k in ((l, LOOKS[l]) for l in ACTIVE_LOOKS):
        d = OUT / "devices" / model / look
        d.mkdir(parents=True, exist_ok=True)
        (d / "bud.svg").write_text(bud(k))
        (d / "case.svg").write_text(case(k))
        mirrored = bud(k).replace('viewBox="0 0 96 96"', 'viewBox="0 0 96 96" style="transform: scaleX(-1)"')
        cells.append((name, look, k["panel"], k["ink"], bud(k), case(k), mirrored))

if ARGS.sheet:
    rows = []
    for name, look, panel, ink, bud_svg, case_svg, right_svg in cells:
        page = "#101214" if look == "studio" else "#EEF3EF"
        border = "#2B3037" if look == "studio" else "#4F625E"
        big = lambda s, w: s.replace('width="96" height="96"', f'width="{w}" height="{w}"').replace(
            'width="120" height="96"', f'width="{int(w * 1.25)}" height="{w}"')
        rows.append(f'''
    <section style="background:{page};color:{ink};padding:20px 24px;display:flex;align-items:center;gap:28px">
      <div style="width:150px;font:600 15px/1.3 sans-serif">{name}<br><span style="font-weight:400;opacity:.75">{look}</span></div>
      <div style="background:{panel};border:1px solid {border};border-radius:8px;padding:14px;display:flex;gap:18px;align-items:center">
        {big(bud_svg, 240)}{big(case_svg, 240)}
      </div>
      <div style="background:{panel};border:1px solid {border};border-radius:8px;padding:12px 16px;display:flex;gap:22px;align-items:center">
        {big(bud_svg, 72)}{big(case_svg, 72)}{big(right_svg, 72)}
      </div>
    </section>''')
    pathlib.Path(ARGS.sheet).write_text(
        '<!doctype html><meta charset="utf-8"><body style="margin:0;width:1080px">' + "".join(rows) + "</body>")
print("wrote", sum(1 for _ in (OUT / "devices").rglob("*.svg")), "svg files")
