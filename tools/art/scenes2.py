"""Second-generation card art: each scene built around that game's signature setting or moment.

Original, procedurally drawn scenery only — no characters, creatures, logos or traced artwork.
Run: python3 scenes2.py [ids...]
"""
import math
import os
import sys

import numpy as np
from PIL import ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from kit import Scene  # noqa: E402
from scenes import (OUT, banner, firework, floating_island, palm, pillars, rain,  # noqa: E402
                    road, shell_world, tiles)


# ---------------------------------------------------------------- shapes
def poly(s, pts, color, alpha=1.0):
    m = s.mask()
    ImageDraw.Draw(m).polygon([(s.px(x), s.py(y)) for x, y in pts], fill=255)
    a = s.arr(m)
    s.paint(a, color, alpha)
    return a


def rect(s, x0, y0, x1, y1, color, alpha=1.0):
    return poly(s, [(x0, y0), (x1, y0), (x1, y1), (x0, y1)], color, alpha)


def ellipse(s, cx, cy, rx, ry, color, alpha=1.0):
    m = s.mask()
    ImageDraw.Draw(m).ellipse([s.px(cx - rx), s.py(cy - ry), s.px(cx + rx), s.py(cy + ry)], fill=255)
    a = s.arr(m)
    s.paint(a, color, alpha)
    return a


def lights(s, pts, color, r=2.0, glow=6, strength=1.0):
    m = s.mask()
    d = ImageDraw.Draw(m)
    for x, y in pts:
        X, Y, R = s.px(x), s.py(y), r * 2
        d.ellipse([X - R, Y - R, X + R, Y + R], fill=255)
    s.glow(s.arr(m), color, glow, strength)


def plate_city(s, cx, cy, rx, ry, plate, glow, n_reactors=8):
    """A city built on a giant raised disc, ringed by glowing reactor stacks, tower at the centre."""
    # underside glow and the slums beneath
    s.radial(cx, cy + ry * 1.6, rx * 1.1, glow, 0.35)
    # central support column
    rect(s, cx - rx * 0.06, cy, cx + rx * 0.06, cy + ry * 3.2, plate)
    # the plate (thick disc)
    ellipse(s, cx, cy + ry * 0.35, rx, ry, plate)
    ellipse(s, cx, cy, rx, ry, plate)
    rim = s.mask()
    ImageDraw.Draw(rim).arc([s.px(cx - rx), s.py(cy - ry), s.px(cx + rx), s.py(cy + ry)], 0, 180,
                            fill=255, width=4)
    s.glow(s.arr(rim), glow, 6, 0.5)
    # buildings on top
    s.skyline(cy, plate, seed=77, x0=cx - rx * 0.8, x1=cx + rx * 0.8, hmin=0.01, hmax=0.05,
              wmin=0.012, wmax=0.03, windows=glow, win_density=0.25)
    # reactors around the rim
    tops = []
    for k in range(n_reactors):
        a = math.pi * 2 * k / n_reactors + 0.2
        x = cx + math.cos(a) * rx * 0.88
        y = cy + math.sin(a) * ry * 0.88
        h = 0.09 if math.sin(a) < 0 else 0.11
        rect(s, x - 0.012, y - h, x + 0.012, y + 0.01, plate)
        tops.append((x, y - h))
    lights(s, tops, glow, r=3.2, glow=14, strength=1.4)
    # headquarters tower
    rect(s, cx - 0.022, cy - 0.2, cx + 0.022, cy, plate)
    rect(s, cx - 0.03, cy - 0.08, cx + 0.03, cy, plate)
    lights(s, [(cx, cy - 0.2)], "#fff2c0", r=1.6, glow=8)


def cracked_moon(s, cx, cy, r, color="#c8281f", glow="#ff3b2b", seed=3):
    s.disc(cx, cy, r, color, glow_color=glow, glow_r=90, glow_s=0.9)
    rng = np.random.default_rng(seed)
    m = s.mask()
    d = ImageDraw.Draw(m)
    for _ in range(9):
        a = rng.uniform(0, 2 * math.pi)
        x, y = s.px(cx), s.py(cy)
        pts = [(x, y)]
        for _ in range(6):
            a += rng.uniform(-0.6, 0.6)
            step = s.px(r) * rng.uniform(0.12, 0.22)
            x, y = x + math.cos(a) * step, y + math.sin(a) * step
            pts.append((x, y))
        d.line(pts, fill=255, width=4)
    cracks = s.arr(m)
    s.paint(cracks, "#2a0606", 0.9)
    s.glow(cracks, "#ffb347", 5, 0.55, core=False)
    # debris drifting off
    s.particles(40, cx - r * 1.3, cx + r * 1.3, cy - r * 1.2, cy + r * 1.5, "#ff7a4a", 0.8, 3.2, glow_r=5, seed=seed)


def crystal_pillar(s, x, base, top, w, color="#9fe8ff"):
    """A colossal jagged crystal column rising from the ground."""
    pts = [(x - w, base)]
    steps = 14
    for i in range(1, steps):
        t = i / steps
        jag = (0.35 if i % 2 else -0.25) * w
        pts.append((x - w * (1 - 0.25 * t) + jag * 0.3, base - (base - top) * t))
    pts += [(x - w * 0.3, top), (x + w * 0.35, top - 0.01)]
    for i in range(steps - 1, 0, -1):
        t = i / steps
        jag = (0.3 if i % 2 else -0.3) * w
        pts.append((x + w * (1 - 0.2 * t) + jag * 0.3, base - (base - top) * t))
    pts.append((x + w, base))
    a = poly(s, pts, color, 0.92)
    s.add(s.blur(a, 30), color, 0.7)
    # facet highlights
    for k in range(5):
        fx = x - w * 0.5 + k * w * 0.25
        s.ribbon(lambda t, fx=fx: (fx + 0.01 * math.sin(t * 9), base - (base - top) * t),
                 "#ffffff", width=1.5, glow_r=4, strength=0.35, steps=30)


def giant_tree(s, cx, base, h, color, seed=5):
    rng = np.random.default_rng(seed)
    # trunk
    poly(s, [(cx - 0.07, base), (cx - 0.035, base - h), (cx + 0.035, base - h), (cx + 0.07, base)], color)
    # roots spreading
    m = s.mask()
    d = ImageDraw.Draw(m)
    for k in range(9):
        side = -1 if k % 2 else 1
        x0, y0 = s.px(cx + side * 0.03), s.py(base - rng.uniform(0.02, 0.15))
        pts = [(x0, y0)]
        x, y = x0, y0
        for _ in range(8):
            x += side * s.px(rng.uniform(0.03, 0.07))
            y += s.py(rng.uniform(-0.005, 0.03))
            pts.append((x, y))
        d.line(pts, fill=255, width=int(rng.uniform(8, 18)))
    # crown of branches
    for k in range(10):
        a = -math.pi / 2 + rng.uniform(-1.2, 1.2)
        x, y = s.px(cx), s.py(base - h)
        pts = [(x, y)]
        for _ in range(6):
            a += rng.uniform(-0.3, 0.3)
            x += math.cos(a) * s.px(0.045)
            y += math.sin(a) * s.px(0.045)
            pts.append((x, y))
        d.line(pts, fill=255, width=int(rng.uniform(6, 14)))
    s.paint(s.arr(m), color)


def water_sphere(s, cx, cy, r):
    s.disc(cx, cy, r, "#2f8fd8", glow_color="#7fd6ff", glow_r=50, glow_s=0.6, alpha=0.55)
    # inner bands & highlight
    for k in range(4):
        s.ribbon(lambda t, k=k: (cx - r + 2 * r * t, cy - r * 0.5 + k * r * 0.3 + 0.02 * math.sin(t * 8 + k)),
                 "#bff0ff", width=1.5, glow_r=4, strength=0.25, steps=60)
    ellipse(s, cx - r * 0.35, cy - r * 0.45, r * 0.25, r * 0.12, "#ffffff", 0.35)
    s.ring(cx, cy, r, 2, "#d8f6ff", glow_r=8, strength=0.7)


def ferris_wheel(s, cx, cy, r, frame, bulbs):
    m = s.mask()
    d = ImageDraw.Draw(m)
    X, Y, R = s.px(cx), s.py(cy), s.px(r)
    d.ellipse([X - R, Y - R, X + R, Y + R], outline=255, width=5)
    d.ellipse([X - R * 0.15, Y - R * 0.15, X + R * 0.15, Y + R * 0.15], fill=255)
    for k in range(16):
        a = 2 * math.pi * k / 16
        d.line([(X, Y), (X + math.cos(a) * R, Y + math.sin(a) * R)], fill=255, width=2)
    d.line([(X, Y), (X - R * 0.6, Y + R * 1.25)], fill=255, width=6)
    d.line([(X, Y), (X + R * 0.6, Y + R * 1.25)], fill=255, width=6)
    s.paint(s.arr(m), frame)
    pts = [(cx + math.cos(2 * math.pi * k / 32) * r, cy + math.sin(2 * math.pi * k / 32) * r * (s.w / s.h))
           for k in range(32)]
    lights(s, pts, bulbs, r=1.6, glow=6, strength=1.1)


def car(s, x, base, w, color, lamp="#fff0c0"):
    h = w * 0.28
    poly(s, [(x - w / 2, base), (x - w / 2, base - h * 0.55), (x - w * 0.25, base - h * 0.62),
             (x - w * 0.08, base - h), (x + w * 0.22, base - h), (x + w * 0.36, base - h * 0.62),
             (x + w / 2, base - h * 0.5), (x + w / 2, base)], color)
    ellipse(s, x - w * 0.3, base, w * 0.09, w * 0.09 * (s.w / s.h), color)
    ellipse(s, x + w * 0.3, base, w * 0.09, w * 0.09 * (s.w / s.h), color)
    lights(s, [(x - w / 2 + 0.005, base - h * 0.4)], "#ff4a3a", r=1.4, glow=6)


def red_airship(s, cx, cy, scale, hull="#1e0a0a", sail="#c0262a"):
    """A war airship: long hull and gondola, with swept crimson fins."""
    poly(s, [(cx - scale * 1.3, cy), (cx - scale, cy - scale * 0.22), (cx + scale, cy - scale * 0.22),
             (cx + scale * 1.4, cy), (cx + scale, cy + scale * 0.2), (cx - scale, cy + scale * 0.2)], hull)
    rect(s, cx - scale * 0.4, cy + scale * 0.2, cx + scale * 0.4, cy + scale * 0.36, hull)
    poly(s, [(cx - scale * 0.9, cy - scale * 0.1), (cx - scale * 1.35, cy - scale * 0.55),
             (cx - scale * 0.55, cy - scale * 0.18)], sail)
    poly(s, [(cx - scale * 0.2, cy - scale * 0.2), (cx - scale * 0.5, cy - scale * 0.6),
             (cx + scale * 0.25, cy - scale * 0.22)], sail, 0.9)
    lights(s, [(cx - scale * 0.3 + scale * 0.2 * k, cy + scale * 0.28) for k in range(4)], "#ffcf7a", r=1.0, glow=3)


def rose(s, cx, cy, r, color="#c81e3a"):
    for k, rr in enumerate([1.0, 0.75, 0.5, 0.3]):
        for j in range(5 if k < 3 else 1):
            a = 2 * math.pi * j / 5 + k * 0.6
            ox, oy = math.cos(a) * r * 0.35 * rr, math.sin(a) * r * 0.35 * rr
            ellipse(s, cx + ox, cy + oy * (s.w / s.h), r * 0.5 * rr, r * 0.5 * rr * (s.w / s.h),
                    color if (k + j) % 2 else "#8e0f26", 0.95)
    s.radial(cx, cy, r * 2.5, "#ff4a6a", 0.25)


def wing(s, cx, cy, scale, color="#f6f4ff", flip=False):
    """An outstretched feathered wing: long primaries fanning out from the shoulder."""
    from PIL import Image
    layer = Image.new("L", (s.w, s.h), 0)
    sgn = -1 if flip else 1
    X, Y, L = s.px(cx), s.py(cy), s.px(scale)
    rows = [(14, 1.0, 255), (11, 0.66, 235), (8, 0.4, 215)]
    for n, length, shade in rows:
        for k in range(n):
            t = k / (n - 1)
            ang = math.radians(200 + 115 * t) if flip else math.radians(-20 - 115 * t)
            flen = L * length * (0.55 + 0.45 * math.sin(math.pi * (0.25 + 0.6 * t)))
            fw = max(6, int(flen * 0.13))
            f = Image.new("L", (int(flen) + 8, fw * 2 + 8), 0)
            ImageDraw.Draw(f).ellipse([4, 4, int(flen) + 4, fw * 2 + 4], fill=shade)
            f = f.rotate(-math.degrees(ang), expand=True, resample=Image.BICUBIC)
            # anchor the feather's base at the wing's bone, which arcs out from the shoulder
            bx = X + sgn * L * 0.35 * t
            by = Y - L * 0.25 * math.sin(math.pi * t * 0.9)
            ox = bx + math.cos(ang) * flen / 2 - f.width / 2
            oy = by + math.sin(ang) * flen / 2 - f.height / 2
            layer.paste(f, (int(ox), int(oy)), f)
    a = s.arr(layer)
    s.glow(a, "#ffffff", 14, 0.25, core=False)
    s.paint(a, color, 0.95)


def clock_face(s, cx, cy, r, color="#ffd166"):
    s.ring(cx, cy, r, 3, color, glow_r=10, strength=1.0)
    s.ring(cx, cy, r * 0.88, 1.5, color, glow_r=6, strength=0.6)
    m = s.mask()
    d = ImageDraw.Draw(m)
    X, Y, R = s.px(cx), s.py(cy), s.px(r)
    for k in range(12):
        a = 2 * math.pi * k / 12
        d.line([(X + math.cos(a) * R * 0.78, Y + math.sin(a) * R * 0.78),
                (X + math.cos(a) * R * 0.88, Y + math.sin(a) * R * 0.88)], fill=255, width=5)
    d.line([(X, Y), (X + R * 0.0, Y - R * 0.6)], fill=255, width=6)
    d.line([(X, Y), (X + R * 0.45, Y + R * 0.15)], fill=255, width=4)
    s.glow(s.arr(m), color, 6, 0.9)


def pagoda(s, cx, base, w, color, lamps="#ff9a5a"):
    y = base
    tier_w = w
    for _ in range(4):
        rect(s, cx - tier_w * 0.32, y - 0.045, cx + tier_w * 0.32, y, color)
        poly(s, [(cx - tier_w * 0.6, y - 0.045), (cx + tier_w * 0.6, y - 0.045),
                 (cx + tier_w * 0.35, y - 0.075), (cx - tier_w * 0.35, y - 0.075)], color)
        lights(s, [(cx - tier_w * 0.55, y - 0.04), (cx + tier_w * 0.55, y - 0.04)], lamps, r=1.4, glow=6)
        y -= 0.075
        tier_w *= 0.78
    rect(s, cx - 0.003, y - 0.05, cx + 0.003, y, color)


def tower(s, cx, base, top, w, color, tip=None):
    poly(s, [(cx - w, base), (cx - w * 0.35, top + 0.04), (cx, top), (cx + w * 0.35, top + 0.04), (cx + w, base)], color)
    if tip:
        lights(s, [(cx, top)], tip, r=2.5, glow=14, strength=1.3)


def mist(s, y0, y1, color, layers=5, alpha=0.35, seed=9):
    for k in range(layers):
        s.clouds(8, y0 + (y1 - y0) * k / layers, y0 + (y1 - y0) * (k + 1) / layers, color,
                 alpha, size=(0.3, 0.6), blur=20, seed=seed + k)


def tents(s, base, xs, color, fire="#ffa64a"):
    for x in xs:
        poly(s, [(x - 0.06, base), (x, base - 0.07), (x + 0.06, base)], color)
    s.particles(1, 0.5, 0.5, base - 0.015, base - 0.015, fire, 7, 7, glow_r=36, strength=1.6, seed=11)
    s.particles(18, 0.47, 0.53, base - 0.1, base - 0.02, fire, 0.6, 1.8, glow_r=4, seed=12)


def stage(s, base, color, edge):
    poly(s, [(0.08, base), (0.92, base), (1.0, base + 0.06), (0.0, base + 0.06)], color)
    s.ribbon(lambda t: (0.08 + 0.84 * t, base), edge, width=2, glow_r=8, strength=0.9, steps=20)
    for x in (0.04, 0.96):
        rect(s, x - 0.035, base - 0.2, x + 0.035, base + 0.06, color)
        lights(s, [(x, base - 0.15), (x, base - 0.09)], edge, r=2.5, glow=6, strength=0.6)


def gem_ring(s, cx, cy, r, colors):
    for k, c in enumerate(colors):
        a = 2 * math.pi * k / len(colors) - math.pi / 2
        x, y = cx + math.cos(a) * r, cy + math.sin(a) * r * (s.w / s.h)
        s.crystal(x, y, 0.022, 0.05, c, glow_r=14, glow_s=1.0)


def arch_ruin(s, cx, base, w, h, color):
    """A broken temple arch: two columns, a round arch, the right column snapped off."""
    col = w * 0.16
    rect(s, cx - w / 2, base - h, cx - w / 2 + col, base, color)
    rect(s, cx + w / 2 - col, base - h * 0.55, cx + w / 2, base, color)
    m = s.mask()
    d = ImageDraw.Draw(m)
    box = [s.px(cx - w / 2), s.py(base - h) - s.px(w / 2), s.px(cx + w / 2), s.py(base - h) + s.px(w / 2)]
    d.arc(box, 180, 300, fill=255, width=int(s.px(col)))
    s.paint(s.arr(m), color)
    rect(s, cx - w * 0.7, base - 0.01, cx + w * 0.7, base + 0.01, color)


def steam(s, x, y, color="#e9e2f2"):
    s.clouds(6, y - 0.12, y, color, 0.45, size=(0.03, 0.07), blur=10, seed=int(x * 100))


# ---------------------------------------------------------------- scenes
def ff1(s):
    """Four elemental crystals above a shrine ruin; the kingdom's castle and its bridge below."""
    s.vgrad([(0, "#040920"), (0.5, "#132a66"), (0.78, "#2c4590"), (1, "#060a1c")])
    s.stars(260, 0, 0.6)
    gem = [("#ffb347", 0.2), ("#ff5a4e", 0.4), ("#4fb8ff", 0.6), ("#6ef2b5", 0.8)]
    for c, x in gem:
        y = 0.2 + 0.05 * math.sin(x * 7)
        s.crystal(x, y, 0.07, 0.17, c, glow_r=34, glow_s=1.1)
        s.ribbon(lambda t, x=x, y=y: (x, y + 0.09 + 0.25 * t), c, width=1.5, glow_r=10, strength=0.35, steps=20)
    s.mountains(0.72, 0.07, "#0d1638", seed=11)
    pillars(s, 0.1, 0.34, 0.77, 4, 0.12, "#070c1e")
    rect(s, 0.08, 0.765, 0.36, 0.78, "#070c1e")
    s.castle(0.68, 0.74, 0.12, "#060a1a", windows="#ffd27a")
    poly(s, [(0.3, 0.8), (0.95, 0.76), (0.95, 0.78), (0.3, 0.82)], "#060a1a")
    s.water(0.8, tint="#0a1236", dim=0.6)


def ff2(s):
    """A rebel's wild rose before a burning imperial city and a looming dreadnought."""
    s.vgrad([(0, "#14040a"), (0.45, "#5a0d16"), (0.7, "#d1431f"), (0.8, "#ff8d3a"), (1, "#14060a")])
    s.clouds(14, 0.05, 0.45, "#260a0e", 0.6, seed=4)
    # dreadnought, side-on: ram bow on the left, bridge tower, finned stern on the right
    poly(s, [(0.04, 0.27), (0.2, 0.2), (0.86, 0.19), (0.86, 0.33), (0.22, 0.33)], "#1a0608")
    poly(s, [(0.52, 0.19), (0.56, 0.1), (0.68, 0.1), (0.7, 0.19)], "#1a0608")
    rect(s, 0.6, 0.05, 0.615, 0.1, "#1a0608")
    poly(s, [(0.84, 0.19), (0.98, 0.1), (0.95, 0.21)], "#1a0608")
    poly(s, [(0.84, 0.33), (0.98, 0.42), (0.95, 0.31)], "#1a0608")
    rect(s, 0.86, 0.22, 0.9, 0.3, "#1a0608")
    for x in (0.3, 0.45, 0.6, 0.75):
        rect(s, x - 0.012, 0.33, x + 0.012, 0.36, "#140406")
        lights(s, [(x, 0.365)], "#ff7a3a", r=2.6, glow=16, strength=1.3)
    lights(s, [(0.9, 0.26)], "#ff9a3a", r=3.5, glow=20, strength=1.4)
    lights(s, [(0.22 + 0.04 * k, 0.25) for k in range(16)], "#ffb06a", r=1.0, glow=3)
    lights(s, [(0.56 + 0.025 * k, 0.14) for k in range(5)], "#ffb06a", r=1.0, glow=3)
    s.radial(0.5, 0.8, 0.6, "#ff6a1f", 0.55)
    s.skyline(0.82, "#12050a", seed=5, hmin=0.04, hmax=0.15, windows="#ff9a3c", win_density=0.15, spires=0.4)
    s.particles(160, 0, 1, 0.3, 0.9, "#ffb050", 0.6, 2.2, glow_r=5, seed=6)
    poly(s, [(0.235, 0.98), (0.245, 0.98), (0.25, 0.68), (0.235, 0.68)], "#1f3a14")
    rose(s, 0.245, 0.64, 0.045)


def ff3(s):
    """The floating continent, its crystal tower piercing the clouds; an airship below."""
    s.vgrad([(0, "#0b2a3a"), (0.5, "#2f7f7a"), (0.72, "#b8e6c4"), (1, "#0f2b2a")])
    s.radial(0.8, 0.15, 0.4, "#fff6c8", 0.5)
    s.clouds(18, 0.65, 0.95, "#e8fff4", 0.55, size=(0.2, 0.45), seed=7)
    floating_island(s, 0.5, 0.42, 0.62, 0.3, "#3fa86a", "#1d3b3a", seed=3)
    s.trees(0.41, "#1f5a3c", seed=9, n=24, hmin=0.02, hmax=0.04, x0=0.24, x1=0.44, ground=False)
    tower(s, 0.6, 0.42, 0.07, 0.035, "#cdeff0", tip="#ffffff")
    s.add(s.blur(poly(s, [(0.58, 0.42), (0.6, 0.08), (0.62, 0.42)], "#e8ffff", 0.6), 20), "#bff8ff", 0.6)
    s.ribbon(lambda t: (0.7 + 0.004 * math.sin(t * 20), 0.43 + 0.28 * t), "#bff8ff", width=3, glow_r=10, strength=0.8)
    s.airship(0.22, 0.7, 0.05, "#1d3b3a")
    s.clouds(10, 0.8, 1.0, "#d6f5ea", 0.65, size=(0.3, 0.6), seed=8)


def ff4(s):
    """Two moons over a kingdom of airships: a red-sailed fleet leaving the castle at night."""
    s.vgrad([(0, "#05051a"), (0.5, "#1c1a5a"), (0.75, "#3a2f7a"), (1, "#07061a")])
    s.stars(240, 0, 0.6)
    s.disc(0.7, 0.2, 0.13, "#e9e4ff", glow_color="#9f95ff", glow_r=70, glow_s=0.7)
    s.disc(0.33, 0.12, 0.05, "#bfb3ff", glow_color="#8d82ff", glow_r=40, glow_s=0.6)
    red_airship(s, 0.32, 0.4, 0.08)
    red_airship(s, 0.58, 0.47, 0.055)
    red_airship(s, 0.16, 0.53, 0.04)
    s.mountains(0.72, 0.1, "#120f38", seed=31)
    s.castle(0.75, 0.76, 0.1, "#0a0920", windows="#ffd27a")
    s.water(0.78, tint="#0c0b2a")


def ff5(s):
    """A meteor crashes beside the wind shrine; a pirate ship's sails on a teal sea."""
    s.vgrad([(0, "#06222b"), (0.5, "#156b74"), (0.7, "#7fd8d2"), (1, "#062026")])
    s.meteor(0.72, 0.18, 0.04, 140, 0.45, "#ffb168")
    s.clouds(10, 0.3, 0.55, "#bff3ee", 0.3, seed=12)
    poly(s, [(0.08, 0.72), (0.2, 0.55), (0.4, 0.6), (0.48, 0.72)], "#0b2e33")
    tower(s, 0.27, 0.6, 0.3, 0.04, "#0a262b")
    s.crystal(0.27, 0.25, 0.05, 0.1, "#7ff5ff", glow_r=40, glow_s=1.3)
    # pirate ship
    poly(s, [(0.6, 0.7), (0.88, 0.7), (0.84, 0.74), (0.64, 0.74)], "#0a2226")
    rect(s, 0.735, 0.48, 0.742, 0.7, "#0a2226")
    poly(s, [(0.745, 0.5), (0.84, 0.56), (0.745, 0.66)], "#e8f4ef", 0.9)
    poly(s, [(0.73, 0.52), (0.65, 0.6), (0.73, 0.66)], "#e8f4ef", 0.85)
    s.water(0.72, tint="#06323a")


def ff6(s):
    """A snowbound mining town at night: steam, lanterns, and the world split by a ruined sky."""
    s.vgrad([(0, "#1a0610"), (0.3, "#5a1430"), (0.55, "#3a1460"), (1, "#12051c")])
    s.radial(0.75, 0.12, 0.45, "#ff4a3a", 0.35)
    s.stars(80, 0.25, 0.5)
    s.mountains(0.58, 0.22, "#d9c6ec", seed=41, rough=0.6)
    s.mountains(0.66, 0.14, "#8f6aa6", seed=42)
    for x in (0.18, 0.3, 0.62, 0.8):
        rect(s, x, 0.55, x + 0.025, 0.82, "#1a0b24")
        steam(s, x + 0.012, 0.55)
    s.skyline(0.82, "#1a0b24", seed=44, hmin=0.03, hmax=0.12, windows="#ffc56a", win_density=0.35)
    # rail bridge / mine trestle
    for k in range(8):
        x = 0.05 + k * 0.12
        poly(s, [(x, 0.84), (x + 0.06, 0.74), (x + 0.12, 0.84)], "#1a0b24")
    rect(s, 0.0, 0.735, 1.0, 0.75, "#1a0b24")
    s.fill_below([(0, 0.86), (1, 0.84)], "#e9def5")
    s.snow(300, seed=45)


def ff7(s):
    """The city on the plate: eight glowing reactors around a central tower, Meteor overhead."""
    s.vgrad([(0, "#020a07"), (0.45, "#0b2a1c"), (0.72, "#1a4a32"), (1, "#020806")])
    s.stars(70, 0, 0.3)
    s.meteor(0.74, 0.16, 0.09, 150, 0.45, "#ff4b2b", core="#ffd2a1")
    s.radial(0.74, 0.16, 0.5, "#ff3a1f", 0.2)
    plate_city(s, 0.5, 0.6, 0.42, 0.075, "#06140e", "#4cff9e")
    s.fill_below(s.ridge_heights(0.92, 0.02, seed=52), "#020806")
    s.ribbon(lambda t: (t, 0.95 - 0.03 * math.sin(t * 7)), "#6ef2b5", width=3, glow_r=20, strength=0.8)


def ff8(s):
    """A field of white flowers under a vast moon, a dark stream falling from it like rain."""
    s.vgrad([(0, "#0a1030"), (0.5, "#3a3f8a"), (0.72, "#b8a6dc"), (1, "#1a1530")])
    s.stars(180, 0, 0.55)
    s.disc(0.6, 0.22, 0.16, "#f4f0ff", glow_color="#c2b8ff", glow_r=80, glow_s=0.7)
    s.particles(160, 0.53, 0.67, 0.36, 0.64, "#3a1a4a", 0.8, 2.2, glow_r=2, strength=0.6, seed=63)
    s.ribbon(lambda t: (0.6 + 0.02 * math.sin(t * 9), 0.36 + 0.3 * t), "#6a4a9a", width=10, glow_r=24, strength=0.25)
    s.ribbon(lambda t: (0.12 + 0.3 * t, 0.1 + 0.16 * t), "#ffffff", width=2, glow_r=8, strength=0.9)
    s.mountains(0.66, 0.05, "#2a2a5a", seed=61)
    s.fill_below([(0, 0.7), (1, 0.68)], "#1d3a3a")
    s.flowers(0.68, 1.0, 1600, ["#f8f6ff", "#f4f1ff", "#ffe9f4"], seed=62)
    s.feathers(5, "#ffffff", seed=64)


def ff9(s):
    """A theatre airship over a sea of mist, a colossal tree on the horizon, a castle on the cliff."""
    s.vgrad([(0, "#2a1530"), (0.4, "#b2566a"), (0.62, "#ffbf6b"), (1, "#2a1520")])
    s.disc(0.24, 0.46, 0.06, "#fff0c2", glow_color="#ffcf7a", glow_r=90, glow_s=0.9)
    giant_tree(s, 0.8, 0.6, 0.32, "#4a2238")
    s.castle(0.24, 0.62, 0.09, "#3a1828", windows="#ffd38a")
    poly(s, [(0.0, 1.0), (0.0, 0.62), (0.36, 0.6), (0.42, 1.0)], "#2a1220")
    s.airship(0.52, 0.3, 0.075, "#3a1a2a")
    poly(s, [(0.47, 0.27), (0.5, 0.2), (0.53, 0.27)], "#3a1a2a")
    lights(s, [(0.46 + 0.02 * k, 0.31) for k in range(6)], "#ffd38a", r=1.2, glow=4)
    mist(s, 0.6, 1.0, "#f2d0e0", alpha=0.4)


def ff10(s):
    """A sleepless city of light across the water at sunset, a great water-sphere arena at its heart."""
    s.vgrad([(0, "#0b1438"), (0.4, "#4a3a8a"), (0.62, "#ff9a6a"), (0.68, "#ffd9a0"), (1, "#0b1030")])
    s.disc(0.18, 0.62, 0.05, "#fff3d6", glow_color="#ffb070", glow_r=80, glow_s=1.0)
    s.skyline(0.68, "#141a3a", seed=82, x0=0.05, x1=0.95, hmin=0.04, hmax=0.24, wmin=0.02, wmax=0.05,
              windows="#ffe7b0", win_density=0.4, spires=0.5)
    water_sphere(s, 0.58, 0.42, 0.13)
    s.beams(0.58, 0.68, [-100, -80], 0.4, 0.05, "#bfe8ff", 0.25)
    s.water(0.68, tint="#14183a", dim=0.7)
    s.particles(110, 0.02, 0.98, 0.25, 0.98, "#b6f0ff", 1.2, 3.5, glow_r=9, seed=83)


def ff10_2(s):
    """A concert stage at night: spotlights sweeping up, a giant sphere screen, the sea beyond."""
    s.vgrad([(0, "#14051e"), (0.45, "#5a1450"), (0.7, "#ff5a8a"), (1, "#14051e")])
    s.water(0.6, tint="#2a0a30")
    s.beams(0.2, 0.8, [-110, -80, -60], 1.1, 0.04, "#7fe8ff", 0.35)
    s.beams(0.8, 0.8, [-120, -100, -70], 1.1, 0.04, "#7fe8ff", 0.35)
    s.beams(0.5, 0.82, [-115, -90, -65], 1.2, 0.05, "#ff8fd0", 0.45)
    water_sphere(s, 0.5, 0.36, 0.12)
    stage(s, 0.8, "#1a0620", "#ff8fd0")
    s.particles(140, 0, 1, 0.05, 0.85, "#ffc0e8", 1, 3, glow_r=8, seed=91)


def ff11(s):
    """Adventurers' camp at dusk: tents and a campfire, a distant tower and a crystal in the aurora."""
    s.vgrad([(0, "#06101e"), (0.5, "#16304a"), (0.75, "#d08a5a"), (1, "#0a0f18")])
    s.stars(150, 0, 0.5)
    s.ribbon(lambda t: (t, 0.18 + 0.08 * math.sin(t * 5)), "#6ef2b5", width=20, glow_r=40, strength=0.5)
    s.ribbon(lambda t: (t, 0.26 + 0.06 * math.sin(t * 4 + 1)), "#b48cff", width=14, glow_r=36, strength=0.45)
    s.crystal(0.72, 0.14, 0.045, 0.1, "#cfe6ff", glow_r=40, glow_s=1.2)
    s.mountains(0.7, 0.12, "#1a2230", seed=101)
    tower(s, 0.72, 0.72, 0.36, 0.03, "#0c121c", tip="#cfe6ff")
    s.airship(0.28, 0.4, 0.04, "#1a2230")
    s.fill_below(s.ridge_heights(0.82, 0.02, seed=102), "#0b1018")
    tents(s, 0.84, [0.3, 0.68], "#141a24")


def ff12(s):
    """A desert royal city of domes under a sky crowded with airships."""
    s.vgrad([(0, "#2a160a"), (0.45, "#b8642a"), (0.68, "#ffd08a"), (1, "#2a1608")])
    s.disc(0.78, 0.36, 0.07, "#fff3d6", glow_color="#ffc070", glow_r=90, glow_s=0.9)
    for x, y, sc in [(0.25, 0.18, 0.09), (0.52, 0.11, 0.04), (0.66, 0.22, 0.05), (0.12, 0.32, 0.03), (0.4, 0.3, 0.03)]:
        s.airship(x, y, sc, "#3a1e10")
    s.mountains(0.7, 0.05, "#a85a2a", seed=111, rough=0.4)
    m = s.mask()
    d = ImageDraw.Draw(m)
    B = s.py(0.73)
    for x, w, h in [(0.3, 0.1, 0.12), (0.5, 0.16, 0.22), (0.68, 0.1, 0.12), (0.18, 0.07, 0.07), (0.82, 0.07, 0.08)]:
        d.rectangle([s.px(x - w / 2), B - s.py(h), s.px(x + w / 2), B], fill=255)
        d.ellipse([s.px(x - w / 2.4), B - s.py(h) - s.px(w / 2.4), s.px(x + w / 2.4), B - s.py(h) + s.px(w / 2.4)], fill=255)
    for x in (0.44, 0.56):
        d.rectangle([s.px(x - 0.006), B - s.py(0.34), s.px(x + 0.006), B], fill=255)
    s.paint(s.arr(m), "#4a2412")
    s.fill_below(s.ridge_heights(0.8, 0.04, 0.45, seed=112), "#7a3e1a")
    s.fill_below(s.ridge_heights(0.9, 0.04, 0.45, seed=113), "#3a1c0c")


def ff13(s):
    """The shell-world hanging in the sky, held up by a colossal crystal pillar."""
    s.vgrad([(0, "#06121e"), (0.5, "#2d6a8a"), (0.78, "#bfe8f2"), (1, "#0a1a24")])
    crystal_pillar(s, 0.42, 0.9, 0.22, 0.09)
    shell_world(s, 0.5, 0.12, 0.3, "#1a2a3a", "#ffe6a0")
    s.clouds(10, 0.5, 0.7, "#e8fbff", 0.35, seed=121)
    s.fill_below(s.ridge_heights(0.88, 0.03, seed=122), "#0d2230")
    for x in (0.7, 0.8, 0.2):
        s.crystal(x, 0.84, 0.03, 0.09, "#8fe4ff", glow_r=20, glow_s=0.5)


def ff13_2(s):
    """A city of crystal spires adrift in a violet storm of chaos, a time gate open above, the shell-world falling."""
    s.vgrad([(0, "#0a0418"), (0.45, "#2a1250"), (0.72, "#5a3a9a"), (1, "#08041a")])
    # the chaos vortex
    for k in range(5):
        r = 0.42 - k * 0.07
        s.ribbon(lambda t, r=r, k=k: (0.5 + r * math.cos(t * 2 * math.pi + k), 0.26 + r * 0.55 * math.sin(t * 2 * math.pi + k)),
                 "#7a3cff" if k % 2 else "#2fd6c8", width=3, glow_r=18, strength=0.35, steps=120)
    s.radial(0.5, 0.26, 0.3, "#b48cff", 0.35)
    # the shell-world falling, trailing fire
    s.meteor(0.2, 0.13, 0.03, 35, 0.22, "#ff8a4a", core="#ffb07a")
    shell_world(s, 0.2, 0.13, 0.065, "#1a1430", "#ffd27a")
    # time gate: concentric rings with a bright core
    for r, w, c in [(0.12, 3, "#2fd6c8"), (0.09, 2, "#e8d8ff"), (0.05, 3, "#ffffff")]:
        s.ring(0.5, 0.26, r, w, c, glow_r=12)
    s.particles(1, 0.5, 0.5, 0.26, 0.26, "#ffffff", 6, 6, glow_r=30, strength=1.5, seed=131)
    # crystal city: tall shards of different heights
    for x, w, h, tl in [(0.14, 0.05, 0.3, -0.1), (0.26, 0.06, 0.42, 0.05), (0.38, 0.05, 0.28, -0.05),
                        (0.62, 0.06, 0.36, 0.08), (0.74, 0.05, 0.46, -0.06), (0.87, 0.05, 0.3, 0.1)]:
        s.crystal(x, 0.86 - h / 2, w, h, "#9fd8ff", glow_r=24, glow_s=0.5, tilt=tl)
    s.fill_below(s.ridge_heights(0.86, 0.02, seed=132), "#0c0820")
    s.particles(120, 0, 1, 0.05, 0.95, "#cdb8ff", 0.6, 2.2, glow_r=5, seed=133)


def ff14(s):
    """A cracked red moon breaking apart over the realm, a crystal tower standing below."""
    s.vgrad([(0, "#0a0614"), (0.5, "#2a1650"), (0.75, "#5a3a8a"), (1, "#0a0614")])
    s.stars(200, 0, 0.6)
    cracked_moon(s, 0.62, 0.24, 0.16)
    s.mountains(0.72, 0.1, "#1c1236", seed=141)
    tower(s, 0.25, 0.75, 0.2, 0.05, "#120a24")
    s.ribbon(lambda t: (0.25 + 0.002 * math.sin(t * 40), 0.22 + 0.5 * t), "#b48cff", width=2, glow_r=14, strength=0.8)
    s.crystal(0.25, 0.17, 0.035, 0.07, "#c8b0ff", glow_r=30, glow_s=1.2)
    s.particles(80, 0, 1, 0.3, 0.95, "#c8b0ff", 0.8, 2.4, glow_r=7, seed=142)


def ff15(s):
    """A convertible parked on an empty highway at night, the walled crown city glowing ahead."""
    s.vgrad([(0, "#05070c"), (0.5, "#1a2230"), (0.66, "#4a5a6a"), (1, "#05070c")])
    s.stars(320, 0, 0.55)
    s.radial(0.5, 0.6, 0.5, "#ffcf7a", 0.45)
    s.ring(0.5, 0.6, 0.22, 2, "#9fe0ff", glow_r=14, strength=0.35)
    s.skyline(0.6, "#0d1118", seed=151, x0=0.32, x1=0.68, hmin=0.02, hmax=0.13, wmin=0.012, wmax=0.03,
              windows="#ffd98a", win_density=0.45, spires=0.6)
    s.crystal(0.5, 0.42, 0.025, 0.06, "#9fe0ff", glow_r=40, glow_s=1.4)
    s.mountains(0.64, 0.05, "#0c1016", seed=152)
    road(s, 0.5, 0.62, "#151a22")
    s.beams(0.5, 0.71, [-90], 0.08, 0.4, "#ffe0a0", 0.25, blur=8)
    car(s, 0.5, 0.75, 0.2, "#2a3446")
    lights(s, [(0.405, 0.725), (0.595, 0.725)], "#ff4a3a", r=1.6, glow=8, strength=1.2)


def ff16(s):
    """The mothercrystal on its mountain, fire on one side and ice on the other, ash in the air."""
    s.vgrad([(0, "#0c0606"), (0.5, "#2a1410"), (0.75, "#4a2a26"), (1, "#0a0505")])
    s.radial(0.12, 0.66, 0.6, "#ff6a1f", 0.7)
    s.radial(0.88, 0.66, 0.6, "#4fb8ff", 0.6)
    s.mountains(0.6, 0.34, "#140b0b", seed=161, rough=0.5)
    s.crystal(0.5, 0.2, 0.13, 0.32, "#ffe7a8", glow_r=60, glow_s=1.0)
    s.ribbon(lambda t: (0.5 - 0.42 * t, 0.36 + 0.4 * t * t), "#ff8a3a", width=3, glow_r=14, strength=0.6)
    s.ribbon(lambda t: (0.5 + 0.42 * t, 0.36 + 0.4 * t * t), "#9fdcff", width=3, glow_r=14, strength=0.6)
    s.particles(160, 0, 0.5, 0.15, 0.95, "#ff9a4a", 0.8, 2.6, glow_r=6, seed=162)
    s.particles(160, 0.5, 1, 0.15, 0.95, "#bfe8ff", 0.8, 2.6, glow_r=6, seed=163)


def ff7r(s):
    """Under the plate: rain on the slums, the plate overhead and its reactor glow."""
    s.vgrad([(0, "#02090c"), (0.5, "#0a2a2a"), (0.85, "#145a4a"), (1, "#02080a")])
    poly(s, [(0, 0), (1, 0), (1, 0.2), (0, 0.26)], "#030c0c")
    lights(s, [(0.08 + 0.07 * k, 0.24 - 0.004 * k) for k in range(14)], "#7affd0", r=1.4, glow=5)
    rect(s, 0.45, 0.2, 0.55, 0.75, "#051414")
    s.radial(0.78, 0.35, 0.3, "#2cffb0", 0.6)
    rect(s, 0.74, 0.22, 0.82, 0.6, "#041010")
    lights(s, [(0.78, 0.3)], "#5affc0", r=3, glow=20, strength=1.6)
    s.skyline(0.82, "#0a1e1e", seed=172, hmin=0.05, hmax=0.2, wmin=0.04, wmax=0.1, windows="#ffcf8a", win_density=0.2, spires=0.0)
    s.fill_below([(0, 0.86), (1, 0.84)], "#030a0a")
    rain(s, 700, "#b8fff0", 0.22)


def ff7int(s):
    """Fireworks bursting over the plate city at night, festival lanterns along the streets."""
    s.vgrad([(0, "#0c0418"), (0.5, "#2a0f4a"), (0.8, "#6a2a8a"), (1, "#0c0418")])
    firework(s, 0.3, 0.2, 0.15, "#ffd36a")
    firework(s, 0.72, 0.15, 0.11, "#ff6ad5")
    firework(s, 0.58, 0.33, 0.07, "#7ff5ff", n=36)
    plate_city(s, 0.5, 0.66, 0.46, 0.07, "#12061e", "#ff9ae8", n_reactors=8)
    lights(s, [(0.05 + 0.06 * k, 0.86 + 0.01 * math.sin(k)) for k in range(16)], "#ffb36a", r=1.6, glow=6)


def ff7rb(s):
    """Beyond the city at last: an amusement park's lights and Ferris wheel at dusk, open plains, a red star."""
    s.vgrad([(0, "#1a2a4a"), (0.45, "#6a7ab8"), (0.66, "#ffc9a0"), (1, "#1a2a1a")])
    s.meteor(0.8, 0.13, 0.025, 150, 0.1, "#ff7a4a")
    s.clouds(12, 0.15, 0.45, "#ffffff", 0.3, seed=191)
    s.mountains(0.62, 0.1, "#4a5a7a", seed=192)
    ferris_wheel(s, 0.68, 0.48, 0.12, "#2a2440", "#ffd36a")
    s.skyline(0.66, "#2a2440", seed=195, x0=0.5, x1=0.92, hmin=0.02, hmax=0.08, windows="#ffd36a", win_density=0.4, spires=0.6)
    s.fill_below(s.ridge_heights(0.7, 0.03, 0.45, seed=194), "#4a7a34")
    s.fill_below(s.ridge_heights(0.82, 0.03, 0.45, seed=193), "#2e5a22")
    s.ribbon(lambda t: (t * 0.6, 0.86 - 0.06 * math.sin(t * 6)), "#6ef2b5", width=3, glow_r=18, strength=0.8)


def fft(s):
    """A battlefield of chequered ground under a sepia sky; banners and twelve zodiac stones."""
    s.vgrad([(0, "#3a2a18"), (0.5, "#a8865a"), (0.7, "#e8d2a0"), (1, "#2a1e10")])
    gem_ring(s, 0.5, 0.27, 0.17, ["#ff6a5a", "#ffb347", "#ffe066", "#8fe36a", "#5ad1ff", "#7a8cff",
                                   "#c48cff", "#ff8ad8", "#ffffff", "#6ef2b5", "#ffd1a1", "#a0f0ff"])
    s.mountains(0.56, 0.1, "#7a5a36", seed=202)
    s.castle(0.2, 0.6, 0.08, "#4a3420")
    tiles(s, 0.64, 16, "#6a5232", "#5a4428", "#e8d2a0")
    banner(s, 0.62, 0.72, 0.2, "#2a1a0e", "#8a1e1e")
    banner(s, 0.8, 0.76, 0.18, "#2a1a0e", "#1e3a8a")


def ccr(s):
    """Light breaking through storm clouds over the cliffs, white feathers drifting down."""
    s.vgrad([(0, "#0a1228"), (0.4, "#2a3a6a"), (0.68, "#f2b6a8"), (1, "#0a1020")])
    s.clouds(16, 0.02, 0.32, "#1a2242", 0.7, size=(0.2, 0.45), seed=214)
    s.beams(0.55, 0.16, [70, 85, 100, 115], 1.0, 0.035, "#fff2d8", 0.4, blur=14)
    s.radial(0.55, 0.18, 0.25, "#ffe8c8", 0.6)
    s.disc(0.7, 0.6, 0.07, "#fff2e6", glow_color="#ffc2a8", glow_r=90, glow_s=0.8)
    s.fill_below([(0, 0.72), (0.38, 0.7), (0.46, 0.76), (1, 0.82)], "#0c1428")
    rain(s, 300, "#cfd8ff", 0.14, angle=8)
    s.feathers(22, "#f6f4ff", seed=212)


def ct(s):
    """A blue time gate opening before a great clock, gears turning in the dark."""
    s.vgrad([(0, "#040a1e"), (0.5, "#0f2a5a"), (1, "#050a1a")])
    s.stars(150, 0, 1)
    s.gear(0.15, 0.82, 0.2, 14, "#0d1d3a")
    s.gear(0.88, 0.2, 0.16, 12, "#0d1d3a")
    s.gear(0.93, 0.85, 0.12, 10, "#13254a")
    clock_face(s, 0.5, 0.3, 0.2)
    s.radial(0.5, 0.62, 0.3, "#4fa8ff", 0.8)
    for r, w, c in [(0.14, 3, "#7fd0ff"), (0.1, 2, "#b8e8ff"), (0.06, 3, "#ffffff")]:
        s.ring(0.5, 0.62, r, w, c, glow_r=12)
    s.ribbon(lambda t: (0.5 + 0.06 * math.sin(t * math.pi * 2), 0.5 + 0.02 * t), "#ffd166", width=2, glow_r=6, strength=0.5)


def cx(s):
    """Two worlds mirrored: a tropical island and its reflection under twin skies."""
    s.vgrad([(0, "#06223a"), (0.45, "#1a6a8a"), (0.62, "#ffcf9a"), (1, "#062030")])
    s.disc(0.7, 0.22, 0.07, "#f2fbff", glow_color="#bfefff", glow_r=60, glow_s=0.7)
    s.disc(0.22, 0.16, 0.035, "#ffd0c0", glow_color="#ff9a8a", glow_r=40, glow_s=0.5)
    s.clouds(8, 0.3, 0.55, "#ffe6d0", 0.3, seed=231)
    ellipse(s, 0.32, 0.64, 0.3, 0.04, "#0a2a2a")
    palm(s, 0.2, 0.63, 0.3, "#08201f", lean=0.18)
    palm(s, 0.36, 0.635, 0.22, "#08201f", lean=-0.1)
    s.water(0.64, tint="#0a5a6a", dim=0.75, tint_amt=0.4)
    s.ring(0.7, 0.86, 0.06, 2, "#bffcff", glow_r=10, strength=0.6)
    s.particles(40, 0.4, 0.95, 0.66, 0.95, "#bffcff", 1, 2.5, glow_r=6, seed=232)


def lo(s):
    """A thousand years of memory: an ancient tower on the sea cliffs, memories rising as light."""
    s.vgrad([(0, "#140c08"), (0.45, "#5a3a22"), (0.68, "#e7b76b"), (1, "#120a06")])
    s.disc(0.32, 0.6, 0.06, "#fff0cc", glow_color="#ffcf80", glow_r=90, glow_s=0.8)
    s.clouds(10, 0.3, 0.55, "#ffd8a0", 0.3, seed=241)
    s.fill_below([(0.45, 1), (0.55, 0.56), (1, 0.52)], "#1e130c")
    poly(s, [(0.7, 0.54), (0.72, 0.18), (0.76, 0.22), (0.78, 0.53)], "#1e130c")
    pillars(s, 0.84, 0.96, 0.53, 3, 0.12, "#1e130c")
    s.water(0.64, tint="#2a1a10", dim=0.7)
    s.ribbon(lambda t: (0.74 + 0.15 * math.cos(t * 14) * (1 - t), 0.55 - 0.45 * t + 0.02 * math.sin(t * 14)),
             "#ffe2a0", width=2, glow_r=12, strength=0.9)
    s.particles(90, 0.5, 0.98, 0.05, 0.55, "#ffe2a0", 0.8, 2.4, glow_r=6, seed=242)


SCENES = {
    "ff1": ff1, "ff2": ff2, "ff3": ff3, "ff4": ff4, "ff5": ff5, "ff6": ff6, "ff7": ff7, "ff8": ff8,
    "ff9": ff9, "ff10": ff10, "ff10_2": ff10_2, "ff11": ff11, "ff12": ff12, "ff13": ff13, "ff13_2": ff13_2, "ff14": ff14,
    "ff15": ff15, "ff16": ff16, "ff7r": ff7r, "ff7rb": ff7rb, "fft": fft, "ccr": ccr,
    "ct": ct, "cx": cx, "lo": lo,
}


def render(name, out_dir=OUT):
    os.makedirs(out_dir, exist_ok=True)
    s = Scene(seed=sum(map(ord, name)))
    SCENES[name](s)
    s.bottom_shade()
    s.vignette(0.45)
    return s.save(os.path.join(out_dir, f"art_{name}.webp"))


if __name__ == "__main__":
    for n in sys.argv[1:] or list(SCENES):
        render(n)
        print("rendered", n)
