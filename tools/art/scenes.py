"""One original scene per soundtrack card. Run: python3 scenes.py [ids...]"""
import math
import os
import sys

import numpy as np
from PIL import ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from kit import Scene  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "drawable-nodpi")


# ---------- extra shapes ----------
def palm(s, x, base, h, color, lean=0.15):
    m = s.mask()
    d = ImageDraw.Draw(m)
    X, B, Ht = s.px(x), s.py(base), s.py(h)
    top = (X + lean * Ht, B - Ht)
    pts = [(X + lean * Ht * (t ** 1.6), B - Ht * t) for t in np.linspace(0, 1, 30)]
    d.line(pts, fill=255, width=int(5 * s.rng.uniform(0.8, 1.2)) * 2)
    for k in range(7):
        a = -math.pi / 2 + (k - 3) * 0.5
        L = Ht * 0.42
        fr = [(top[0] + math.cos(a + 0.5 * t * (1 if k >= 3 else -1)) * L * t,
               top[1] + math.sin(a) * L * t + L * 0.5 * t * t) for t in np.linspace(0, 1, 16)]
        d.line(fr, fill=255, width=8)
    s.paint(s.arr(m), color)


def rain(s, n, color, alpha=0.25, angle=12):
    m = s.mask()
    d = ImageDraw.Draw(m)
    dx = math.tan(math.radians(angle))
    for _ in range(n):
        x, y = s.rng.uniform(-0.1, 1.1) * s.w, s.rng.uniform(0, 1) * s.h
        L = s.rng.uniform(20, 60) * 2
        d.line([(x, y), (x + dx * L, y + L)], fill=int(s.rng.uniform(80, 200)), width=2)
    s.add(s.arr(m), color, alpha)


def firework(s, cx, cy, r, color, n=48):
    m = s.mask()
    d = ImageDraw.Draw(m)
    X, Y, R = s.px(cx), s.py(cy), s.px(r)
    for k in range(n):
        a = 2 * math.pi * k / n
        for t in np.linspace(0.35, 1.0, 6):
            x, y = X + math.cos(a) * R * t, Y + math.sin(a) * R * t + R * 0.15 * t * t
            rr = 2.5 * (1.2 - t) * 2 + 1
            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=int(255 * (1.1 - t * 0.6)))
    s.glow(s.arr(m), color, 8, 1.1)


def road(s, vx, vy, color, line="#ffd28a"):
    m = s.mask()
    d = ImageDraw.Draw(m)
    X, Y = s.px(vx), s.py(vy)
    d.polygon([(X - 6, Y), (X + 6, Y), (s.px(1.05), s.h), (s.px(-0.05), s.h)], fill=255)
    s.paint(s.arr(m), color)
    dash = s.mask()
    dd = ImageDraw.Draw(dash)
    for i in range(14):
        t0, t1 = (i / 14) ** 2, ((i + 0.45) / 14) ** 2
        y0, y1 = Y + (s.h - Y) * t0, Y + (s.h - Y) * t1
        w0, w1 = 1 + 10 * t0, 1 + 10 * t1
        dd.polygon([(X - w0, y0), (X + w0, y0), (X + w1, y1), (X - w1, y1)], fill=255)
    s.glow(s.arr(dash), line, 4, 0.6)


def floating_island(s, cx, cy, w, h, top_color, rock_color, seed=1):
    pts = s.ridge_heights(0, 0.03, 0.6, seed, detail=6)
    m = s.mask()
    d = ImageDraw.Draw(m)
    X0, X1 = s.px(cx - w / 2), s.px(cx + w / 2)
    top = [(X0 + (X1 - X0) * x, s.py(cy) + s.py(y)) for x, y in pts]
    tip = (s.px(cx + 0.02), s.py(cy + h))
    d.polygon(top + [tip], fill=255)
    rock = s.arr(m)
    s.paint(rock, rock_color)
    g = s.mask()
    ImageDraw.Draw(g).polygon(top + [(X1, s.py(cy) + 18), (X0, s.py(cy) + 18)], fill=255)
    s.paint(s.arr(g) * rock, top_color)


def pillars(s, x0, x1, base, n, h, color, broken=True):
    m = s.mask()
    d = ImageDraw.Draw(m)
    for i in range(n):
        x = s.px(x0 + (x1 - x0) * i / max(1, n - 1))
        ph = s.py(h * (s.rng.uniform(0.45, 1.0) if broken else 1))
        w = s.px(0.018)
        d.rectangle([x - w, s.py(base) - ph, x + w, s.py(base)], fill=255)
        d.rectangle([x - w * 1.5, s.py(base) - ph - 6, x + w * 1.5, s.py(base) - ph + 4], fill=255)
    s.paint(s.arr(m), color)


def banner(s, x, base, h, color, cloth):
    m = s.mask()
    d = ImageDraw.Draw(m)
    X, B, Ht = s.px(x), s.py(base), s.py(h)
    d.line([(X, B), (X, B - Ht)], fill=255, width=5)
    s.paint(s.arr(m), color)
    c = s.mask()
    dc = ImageDraw.Draw(c)
    w = Ht * 0.32
    dc.polygon([(X, B - Ht), (X + w, B - Ht + w * 0.15), (X + w * 0.85, B - Ht + w * 0.75), (X, B - Ht + w * 0.65)], fill=255)
    s.paint(s.arr(c), cloth)


def tiles(s, y0, rows, color_a, color_b, line):
    m_a, m_b, m_l = s.mask(), s.mask(), s.mask()
    da, db, dl = ImageDraw.Draw(m_a), ImageDraw.Draw(m_b), ImageDraw.Draw(m_l)
    tw = s.px(0.16)
    th = tw * 0.5
    for r in range(rows):
        for c in range(-2, 9):
            cx = c * tw + (tw / 2 if r % 2 else 0)
            cy = s.py(y0) + r * th / 2
            poly = [(cx, cy - th / 2), (cx + tw / 2, cy), (cx, cy + th / 2), (cx - tw / 2, cy)]
            (da if (r + c) % 2 else db).polygon(poly, fill=255)
            dl.polygon(poly, outline=255)
    s.paint(s.arr(m_a), color_a)
    s.paint(s.arr(m_b), color_b)
    s.add(s.arr(m_l), line, 0.35)


def shell_world(s, cx, cy, r, color, lights):
    a = s.disc(cx, cy, r, color, glow_color=lights, glow_r=40, glow_s=0.35)
    m = s.mask()
    d = ImageDraw.Draw(m)
    for _ in range(260):
        ang = s.rng.uniform(0, 2 * math.pi)
        rr = s.px(r) * math.sqrt(s.rng.uniform(0, 1))
        x, y = s.px(cx) + math.cos(ang) * rr, s.py(cy) + math.sin(ang) * rr * 0.98
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=int(s.rng.uniform(80, 255)))
    s.glow(s.arr(m) * a, lights, 3, 0.8)


# ---------- scenes ----------
def ff1(s):
    s.vgrad([(0, "#050a24"), (0.55, "#13245e"), (0.75, "#2c3f86"), (1, "#070b1c")])
    s.stars(260, 0, 0.6)
    s.radial(0.5, 0.42, 0.55, "#3a63d8", 0.35)
    cols = ["#ffb347", "#ff5a4e", "#4fb8ff", "#6ef2b5"]
    for i, c in enumerate(cols):
        a = math.pi * (0.15 + 0.7 * i / 3)
        s.crystal(0.5 - math.cos(a) * 0.3, 0.36 - math.sin(a) * 0.12, 0.075, 0.17, c, glow_r=30, glow_s=1.0)
    s.mountains(0.78, 0.07, "#0d1638", seed=11)
    s.castle(0.5, 0.8, 0.13, "#060a1a", windows="#ffd27a")
    s.particles(50, 0.1, 0.9, 0.2, 0.7, "#cfe3ff", 0.6, 1.6, seed=2)


def ff2(s):
    s.vgrad([(0, "#1a0508"), (0.45, "#5c0f12"), (0.7, "#d1431f"), (0.78, "#ff8d3a"), (1, "#14060a")])
    s.clouds(14, 0.1, 0.5, "#2a0a0e", 0.55, seed=4)
    s.radial(0.5, 0.75, 0.6, "#ff6a1f", 0.5)
    s.mountains(0.74, 0.08, "#3a0d10", seed=21)
    s.skyline(0.82, "#12050a", seed=5, hmin=0.04, hmax=0.16, windows="#ff9a3c", win_density=0.15, spires=0.4)
    s.particles(160, 0, 1, 0.25, 0.85, "#ffb050", 0.6, 2.2, glow_r=5, seed=6)


def ff3(s):
    s.vgrad([(0, "#0b2a3a"), (0.5, "#2f7f7a"), (0.72, "#b8e6c4"), (1, "#0f2b2a")])
    s.radial(0.75, 0.2, 0.4, "#fff6c8", 0.5)
    s.clouds(18, 0.62, 0.95, "#e8fff4", 0.55, size=(0.2, 0.45), seed=7)
    floating_island(s, 0.52, 0.36, 0.58, 0.32, "#3fa86a", "#1d3b3a", seed=3)
    s.trees(0.355, "#1f5a3c", seed=9, n=30, hmin=0.02, hmax=0.05, x0=0.3, x1=0.75, ground=False)
    s.ribbon(lambda t: (0.66 + 0.004 * math.sin(t * 20), 0.37 + 0.3 * t), "#bff8ff", width=3, glow_r=10, strength=0.8)
    s.crystal(0.45, 0.3, 0.03, 0.07, "#a8fff0", glow_r=25, glow_s=1.2)
    s.clouds(10, 0.75, 1.0, "#d6f5ea", 0.6, size=(0.3, 0.6), seed=8)


def ff4(s):
    s.vgrad([(0, "#05051a"), (0.5, "#1c1a5a"), (0.75, "#3a2f7a"), (1, "#07061a")])
    s.stars(240, 0, 0.65)
    s.disc(0.66, 0.22, 0.13, "#e9e4ff", glow_color="#9f95ff", glow_r=70, glow_s=0.7)
    s.disc(0.3, 0.14, 0.045, "#cfc6ff", glow_color="#8d82ff", glow_r=40, glow_s=0.6)
    s.airship(0.35, 0.42, 0.07, "#0b0a24")
    s.airship(0.18, 0.5, 0.04, "#13113a")
    s.mountains(0.7, 0.12, "#120f38", seed=31)
    s.water(0.72, tint="#0c0b2a")


def ff5(s):
    s.vgrad([(0, "#06222b"), (0.5, "#156b74"), (0.7, "#7fd8d2"), (1, "#062026")])
    s.meteor(0.8, 0.14, 0.022, 145, 0.35, "#ffb168")
    s.clouds(10, 0.3, 0.6, "#bff3ee", 0.3, seed=12)
    m = s.mask()
    d = ImageDraw.Draw(m)
    d.polygon([(s.px(0.32), s.py(0.72)), (s.px(0.42), s.py(0.6)), (s.px(0.58), s.py(0.62)), (s.px(0.7), s.py(0.72))], fill=255)
    s.paint(s.arr(m), "#0b2e33")
    t = s.mask()
    dt = ImageDraw.Draw(t)
    dt.rectangle([s.px(0.47), s.py(0.38), s.px(0.53), s.py(0.64)], fill=255)
    dt.polygon([(s.px(0.45), s.py(0.38)), (s.px(0.5), s.py(0.3)), (s.px(0.55), s.py(0.38))], fill=255)
    s.paint(s.arr(t), "#0a262b")
    s.crystal(0.5, 0.25, 0.05, 0.1, "#7ff5ff", glow_r=40, glow_s=1.3)
    s.water(0.72, tint="#06323a")


def ff6(s):
    s.vgrad([(0, "#14051f"), (0.5, "#4a1460"), (0.72, "#a24a9a"), (1, "#12051c")])
    s.stars(120, 0, 0.4)
    s.mountains(0.6, 0.2, "#d9c6ec", seed=41, rough=0.6)
    s.mountains(0.68, 0.12, "#8f6aa6", seed=42)
    m = s.mask()
    d = ImageDraw.Draw(m)
    for x in (0.28, 0.34, 0.66):
        d.rectangle([s.px(x), s.py(0.6), s.px(x + 0.02), s.py(0.8)], fill=255)
    s.paint(s.arr(m), "#1a0b24")
    s.clouds(6, 0.5, 0.6, "#cdb7dc", 0.35, size=(0.05, 0.12), seed=43)
    s.skyline(0.82, "#1a0b24", seed=44, hmin=0.03, hmax=0.1, windows="#ffc56a", win_density=0.3)
    s.fill_below([(0, 0.84), (1, 0.82)], "#e9def5")
    s.snow(260, seed=45)


def ff7(s):
    s.vgrad([(0, "#020a07"), (0.45, "#0b2a1c"), (0.72, "#1f5a3a"), (1, "#020806")])
    s.stars(80, 0, 0.35)
    s.meteor(0.7, 0.2, 0.09, 150, 0.45, "#ff4b2b", core="#ffd2a1")
    s.radial(0.5, 0.82, 0.6, "#3cff9a", 0.55)
    m = s.mask()
    d = ImageDraw.Draw(m)
    d.ellipse([s.px(0.05), s.py(0.66), s.px(0.95), s.py(0.86)], fill=255)
    s.paint(s.arr(m), "#06140e")
    s.skyline(0.76, "#081a12", seed=51, x0=0.12, x1=0.88, hmin=0.02, hmax=0.08, windows="#9effc8", win_density=0.2)
    t = s.mask()
    ImageDraw.Draw(t).rectangle([s.px(0.48), s.py(0.52), s.px(0.53), s.py(0.78)], fill=255)
    s.paint(s.arr(t), "#06140e")
    s.ribbon(lambda t: (t, 0.9 - 0.06 * math.sin(t * 7)), "#6ef2b5", width=4, glow_r=24, strength=1.0)
    s.ribbon(lambda t: (t, 0.95 - 0.04 * math.sin(t * 9 + 1)), "#3cd68f", width=3, glow_r=18, strength=0.8)


def ff8(s):
    s.vgrad([(0, "#0a1030"), (0.5, "#3a3f8a"), (0.72, "#b8a6dc"), (1, "#1a1530")])
    s.stars(180, 0, 0.55)
    s.disc(0.62, 0.26, 0.15, "#f4f0ff", glow_color="#c2b8ff", glow_r=80, glow_s=0.7)
    s.ribbon(lambda t: (0.15 + 0.3 * t, 0.12 + 0.18 * t), "#ffffff", width=2, glow_r=8, strength=0.9)
    s.mountains(0.66, 0.06, "#2a2a5a", seed=61)
    s.fill_below([(0, 0.7), (1, 0.68)], "#1d3a3a")
    s.flowers(0.68, 1.0, 1400, ["#f4f1ff", "#f9c6e0", "#ffe9a8"], seed=62)


def ff9(s):
    s.vgrad([(0, "#2a1530"), (0.4, "#b2566a"), (0.65, "#ffbf6b"), (1, "#2a1520")])
    s.disc(0.25, 0.5, 0.07, "#fff0c2", glow_color="#ffcf7a", glow_r=90, glow_s=0.9)
    s.clouds(12, 0.2, 0.5, "#ffd8b0", 0.3, seed=71)
    s.airship(0.72, 0.22, 0.06, "#3a1a2a")
    s.mountains(0.66, 0.08, "#6a3248", seed=72)
    m = s.mask()
    ImageDraw.Draw(m).polygon([(s.px(0.45), s.py(1)), (s.px(0.5), s.py(0.6)), (s.px(0.95), s.py(0.58)), (s.px(1), s.py(1))], fill=255)
    s.paint(s.arr(m), "#2a1220")
    s.castle(0.7, 0.6, 0.12, "#2a1220", windows="#ffd38a")
    s.ribbon(lambda t: (0.5 + 0.004 * math.sin(t * 30), 0.6 + 0.35 * t), "#ffe8c8", width=8, glow_r=14, strength=0.7)
    s.clouds(8, 0.85, 1.0, "#ffd8c0", 0.4, size=(0.3, 0.5), seed=73)


def ff10(s):
    s.vgrad([(0, "#0b1438"), (0.4, "#4a3a8a"), (0.62, "#ff9a6a"), (0.68, "#ffd9a0"), (1, "#0b1030")])
    s.disc(0.5, 0.64, 0.06, "#fff3d6", glow_color="#ffb070", glow_r=80, glow_s=1.0)
    s.clouds(10, 0.3, 0.55, "#ffb8a0", 0.3, seed=81)
    s.skyline(0.68, "#141a3a", seed=82, x0=0.05, x1=0.95, hmin=0.03, hmax=0.2, wmin=0.02, wmax=0.05,
              windows="#ffe7b0", win_density=0.35, spires=0.5)
    s.water(0.68, tint="#14183a", dim=0.7)
    s.particles(90, 0.05, 0.95, 0.3, 0.95, "#b6f0ff", 1.2, 3.5, glow_r=9, seed=83)


def ff10_2(s):
    s.vgrad([(0, "#14051e"), (0.45, "#5a1450"), (0.7, "#ff5a8a"), (1, "#14051e")])
    s.beams(0.5, 0.95, [-120, -100, -80, -60], 1.2, 0.05, "#ff8fd0", 0.45)
    s.beams(0.2, 0.95, [-95, -70], 1.1, 0.04, "#7fe8ff", 0.35)
    s.beams(0.8, 0.95, [-110, -85], 1.1, 0.04, "#7fe8ff", 0.35)
    s.disc(0.5, 0.36, 0.09, "#ffe0f0", glow_color="#ff7ac0", glow_r=70, glow_s=1.0)
    s.water(0.72, tint="#2a0a30")
    s.particles(120, 0, 1, 0.1, 0.9, "#ffc0e8", 1, 3, glow_r=8, seed=91)


def ff11(s):
    s.vgrad([(0, "#06101e"), (0.5, "#16304a"), (0.75, "#d08a5a"), (1, "#0a0f18")])
    s.stars(150, 0, 0.5)
    s.ribbon(lambda t: (t, 0.2 + 0.08 * math.sin(t * 5)), "#6ef2b5", width=20, glow_r=40, strength=0.5)
    s.ribbon(lambda t: (t, 0.28 + 0.06 * math.sin(t * 4 + 1)), "#b48cff", width=14, glow_r=36, strength=0.45)
    s.mountains(0.7, 0.12, "#1a2230", seed=101)
    t = s.mask()
    d = ImageDraw.Draw(t)
    d.polygon([(s.px(0.66), s.py(0.72)), (s.px(0.69), s.py(0.38)), (s.px(0.72), s.py(0.72))], fill=255)
    s.paint(s.arr(t), "#0c121c")
    s.crystal(0.69, 0.34, 0.03, 0.06, "#cfe6ff", glow_r=25)
    s.fill_below(s.ridge_heights(0.8, 0.03, seed=102), "#0b1018")
    s.particles(1, 0.3, 0.3, 0.82, 0.82, "#ffa64a", 6, 6, glow_r=30, strength=1.5, seed=103)


def ff12(s):
    s.vgrad([(0, "#2a160a"), (0.45, "#b8642a"), (0.68, "#ffd08a"), (1, "#2a1608")])
    s.disc(0.75, 0.3, 0.07, "#fff3d6", glow_color="#ffc070", glow_r=90, glow_s=0.9)
    s.airship(0.3, 0.25, 0.08, "#3a1e10")
    s.airship(0.55, 0.16, 0.035, "#5a3018")
    s.mountains(0.7, 0.05, "#a85a2a", seed=111, rough=0.4)
    m = s.mask()
    d = ImageDraw.Draw(m)
    B = s.py(0.72)
    for x, w, h in [(0.35, 0.1, 0.12), (0.5, 0.14, 0.2), (0.65, 0.09, 0.1), (0.25, 0.06, 0.07)]:
        d.rectangle([s.px(x - w / 2), B - s.py(h), s.px(x + w / 2), B], fill=255)
        d.ellipse([s.px(x - w / 2.4), B - s.py(h) - s.px(w / 2.4), s.px(x + w / 2.4), B - s.py(h) + s.px(w / 2.4)], fill=255)
    d.rectangle([s.px(0.495), B - s.py(0.32), s.px(0.505), B], fill=255)
    s.paint(s.arr(m), "#4a2412")
    s.fill_below(s.ridge_heights(0.8, 0.04, 0.45, seed=112), "#7a3e1a")
    s.fill_below(s.ridge_heights(0.9, 0.04, 0.45, seed=113), "#3a1c0c")


def ff13(s):
    s.vgrad([(0, "#06121e"), (0.5, "#2d6a8a"), (0.75, "#bfe8f2"), (1, "#0a1a24")])
    shell_world(s, 0.5, 0.14, 0.32, "#1a2a3a", "#ffe6a0")
    s.clouds(10, 0.45, 0.65, "#e8fbff", 0.35, seed=121)
    for x, w, h, tl in [(0.5, 0.12, 0.52, 0.0), (0.38, 0.08, 0.36, -0.15), (0.63, 0.09, 0.4, 0.12),
                        (0.28, 0.05, 0.22, -0.2), (0.72, 0.05, 0.24, 0.2)]:
        s.crystal(x, 0.86 - h / 2, w, h, "#8fe4ff", glow_r=30, glow_s=0.6, tilt=tl)
    s.fill_below(s.ridge_heights(0.86, 0.02, seed=122), "#0d2230")


def ff14(s):
    s.vgrad([(0, "#0a0614"), (0.5, "#2a1650"), (0.75, "#5a3a8a"), (1, "#0a0614")])
    s.stars(200, 0, 0.6)
    s.disc(0.66, 0.24, 0.14, "#d8342a", glow_color="#ff3b2b", glow_r=90, glow_s=0.9)
    s.disc(0.62, 0.21, 0.05, "#ff7a5a", alpha=0.35)
    s.mountains(0.72, 0.1, "#1c1236", seed=141)
    t = s.mask()
    d = ImageDraw.Draw(t)
    d.polygon([(s.px(0.24), s.py(0.75)), (s.px(0.3), s.py(0.18)), (s.px(0.36), s.py(0.75))], fill=255)
    s.paint(s.arr(t), "#120a24")
    s.ribbon(lambda t: (0.3 + 0.002 * math.sin(t * 40), 0.2 + 0.55 * t), "#b48cff", width=2, glow_r=14, strength=0.8)
    s.crystal(0.3, 0.16, 0.035, 0.07, "#c8b0ff", glow_r=30, glow_s=1.2)
    s.particles(80, 0, 1, 0.3, 0.95, "#c8b0ff", 0.8, 2.4, glow_r=7, seed=142)


def ff15(s):
    s.vgrad([(0, "#05070c"), (0.5, "#1a2230"), (0.68, "#4a5a6a"), (1, "#05070c")])
    s.stars(320, 0, 0.6)
    s.radial(0.5, 0.62, 0.55, "#ffcf7a", 0.45)
    s.skyline(0.62, "#0d1118", seed=151, x0=0.32, x1=0.68, hmin=0.02, hmax=0.12, wmin=0.012, wmax=0.03,
              windows="#ffd98a", win_density=0.45, spires=0.6)
    s.crystal(0.5, 0.44, 0.025, 0.06, "#9fe0ff", glow_r=40, glow_s=1.4)
    s.mountains(0.66, 0.05, "#0c1016", seed=152)
    road(s, 0.5, 0.64, "#151a22")


def ff16(s):
    s.vgrad([(0, "#0c0606"), (0.5, "#2a1410"), (0.75, "#4a2a26"), (1, "#0a0505")])
    s.radial(0.15, 0.7, 0.6, "#ff6a1f", 0.65)
    s.radial(0.85, 0.7, 0.6, "#4fb8ff", 0.55)
    s.mountains(0.62, 0.32, "#140b0b", seed=161, rough=0.5)
    s.crystal(0.5, 0.24, 0.12, 0.3, "#ffe7a8", glow_r=60, glow_s=1.0)
    s.particles(140, 0, 0.5, 0.2, 0.95, "#ff9a4a", 0.8, 2.6, glow_r=6, seed=162)
    s.particles(140, 0.5, 1, 0.2, 0.95, "#bfe8ff", 0.8, 2.6, glow_r=6, seed=163)


def ff7r(s):
    s.vgrad([(0, "#02090c"), (0.5, "#0a2a2a"), (0.8, "#145a4a"), (1, "#02080a")])
    s.radial(0.5, 0.9, 0.7, "#2cffb0", 0.45)
    s.skyline(0.7, "#0c2424", seed=171, hmin=0.08, hmax=0.3, wmin=0.04, wmax=0.09, windows="#7affd0", win_density=0.12)
    s.skyline(0.85, "#061414", seed=172, hmin=0.1, hmax=0.38, wmin=0.05, wmax=0.12, windows="#c8fff0", win_density=0.18, spires=0.0)
    m = s.mask()
    d = ImageDraw.Draw(m)
    for x in (0.22, 0.78):
        d.rectangle([s.px(x - 0.02), s.py(0.18), s.px(x + 0.02), s.py(0.7)], fill=255)
    s.paint(s.arr(m), "#04100e")
    s.clouds(6, 0.1, 0.2, "#1e5a4a", 0.4, size=(0.05, 0.12), seed=173)
    s.particles(2, 0.21, 0.79, 0.18, 0.18, "#5affc0", 3, 3, glow_r=20, strength=1.5, seed=174)
    rain(s, 600, "#b8fff0", 0.22)


def ff7int(s):
    s.vgrad([(0, "#0c0418"), (0.5, "#2a0f4a"), (0.8, "#6a2a8a"), (1, "#0c0418")])
    firework(s, 0.32, 0.24, 0.16, "#ffd36a")
    firework(s, 0.7, 0.18, 0.12, "#ff6ad5")
    firework(s, 0.6, 0.38, 0.08, "#7ff5ff", n=36)
    s.skyline(0.82, "#12061e", seed=181, hmin=0.06, hmax=0.25, wmin=0.03, wmax=0.08,
              windows="#ff9ae8", win_density=0.25, spires=0.3)
    s.radial(0.5, 0.9, 0.6, "#b44cff", 0.35)


def ff7rb(s):
    s.vgrad([(0, "#1a2a4a"), (0.45, "#6a8ab8"), (0.66, "#ffd9a0"), (1, "#1a2a1a")])
    s.disc(0.25, 0.56, 0.05, "#fff3d6", glow_color="#ffcf8a", glow_r=90, glow_s=0.9)
    s.meteor(0.78, 0.16, 0.03, 150, 0.12, "#ff7a4a")
    s.clouds(12, 0.2, 0.5, "#ffffff", 0.35, seed=191)
    s.mountains(0.62, 0.08, "#4a6a7a", seed=192)
    s.water(0.64, tint="#203a5a", dim=0.8)
    s.fill_below(s.ridge_heights(0.74, 0.04, 0.45, seed=194), "#4a7a34")
    s.fill_below(s.ridge_heights(0.84, 0.03, 0.45, seed=193), "#2e5a22")
    s.trees(0.75, "#1e3a16", seed=195, n=18, hmin=0.02, hmax=0.05, x0=0.05, x1=0.4, ground=False)
    s.ribbon(lambda t: (t * 0.65, 0.84 - 0.08 * math.sin(t * 6)), "#6ef2b5", width=4, glow_r=20, strength=0.9)


def fft(s):
    s.vgrad([(0, "#3a2a18"), (0.5, "#a8865a"), (0.7, "#e8d2a0"), (1, "#2a1e10")])
    s.disc(0.7, 0.24, 0.07, "#fff0c8", glow_color="#ffd890", glow_r=60, glow_s=0.6)
    s.clouds(10, 0.15, 0.45, "#f2e2bc", 0.35, seed=201)
    s.mountains(0.56, 0.12, "#7a5a36", seed=202)
    s.castle(0.3, 0.58, 0.1, "#4a3420")
    tiles(s, 0.66, 14, "#6a5232", "#5a4428", "#e8d2a0")
    banner(s, 0.62, 0.7, 0.2, "#2a1a0e", "#8a1e1e")
    banner(s, 0.78, 0.74, 0.18, "#2a1a0e", "#1e3a8a")


def ccr(s):
    s.vgrad([(0, "#081430"), (0.45, "#3a4a8a"), (0.7, "#f2a6a0"), (1, "#0a1020")])
    s.disc(0.62, 0.62, 0.08, "#fff2e6", glow_color="#ffc2a8", glow_r=90, glow_s=0.9)
    s.clouds(12, 0.4, 0.66, "#ffd6d0", 0.35, seed=211)
    s.fill_below([(0, 0.72), (0.35, 0.7), (0.5, 0.76), (1, 0.8)], "#0c1428")
    s.feathers(16, "#f6f4ff", seed=212)
    s.particles(60, 0, 1, 0.1, 0.9, "#ffe8f0", 0.6, 1.8, glow_r=5, seed=213)


def ct(s):
    s.vgrad([(0, "#040a1e"), (0.5, "#0f2a5a"), (1, "#050a1a")])
    s.stars(150, 0, 1)
    s.gear(0.18, 0.78, 0.2, 14, "#0d1d3a")
    s.gear(0.86, 0.22, 0.16, 12, "#0d1d3a")
    s.gear(0.92, 0.82, 0.12, 10, "#13254a")
    s.radial(0.5, 0.42, 0.35, "#4fa8ff", 0.7)
    for r, w, c in [(0.26, 3, "#7fd0ff"), (0.2, 2, "#ffd166"), (0.14, 3, "#b8e8ff"), (0.08, 2, "#ffffff")]:
        s.ring(0.5, 0.42, r, w, c, glow_r=12)
    for k in range(12):
        a = 2 * math.pi * k / 12
        x, y = 0.5 + math.cos(a) * 0.29, 0.42 + math.sin(a) * 0.29 * 0.9
        s.particles(1, x, x, y, y, "#ffd166", 3, 3, glow_r=8, seed=220 + k)
    s.ribbon(lambda t: (0.5, 0.42 - 0.2 * t), "#ffd166", width=3, glow_r=8, strength=0.9, steps=10)
    s.ribbon(lambda t: (0.5 + 0.12 * t, 0.42 + 0.05 * t), "#ffd166", width=3, glow_r=8, strength=0.9, steps=10)


def cx(s):
    s.vgrad([(0, "#06223a"), (0.45, "#1a6a8a"), (0.62, "#ffcf9a"), (1, "#062030")])
    s.disc(0.66, 0.24, 0.07, "#f2fbff", glow_color="#bfefff", glow_r=60, glow_s=0.7)
    s.clouds(8, 0.3, 0.55, "#ffe6d0", 0.3, seed=231)
    m = s.mask()
    ImageDraw.Draw(m).ellipse([s.px(0.0), s.py(0.6), s.px(0.55), s.py(0.68)], fill=255)
    s.paint(s.arr(m), "#0a2a2a")
    palm(s, 0.18, 0.64, 0.3, "#08201f", lean=0.18)
    palm(s, 0.33, 0.645, 0.22, "#08201f", lean=-0.1)
    s.water(0.64, tint="#0a5a6a", dim=0.75, tint_amt=0.4)
    s.particles(40, 0.4, 0.95, 0.66, 0.95, "#bffcff", 1, 2.5, glow_r=6, seed=232)


def lo(s):
    s.vgrad([(0, "#140c08"), (0.45, "#5a3a22"), (0.68, "#e7b76b"), (1, "#120a06")])
    s.disc(0.35, 0.6, 0.06, "#fff0cc", glow_color="#ffcf80", glow_r=90, glow_s=0.8)
    s.clouds(10, 0.3, 0.55, "#ffd8a0", 0.3, seed=241)
    s.fill_below([(0.45, 1), (0.55, 0.56), (1, 0.52)], "#1e130c")
    t = s.mask()
    d = ImageDraw.Draw(t)
    d.polygon([(s.px(0.7), s.py(0.54)), (s.px(0.72), s.py(0.2)), (s.px(0.76), s.py(0.24)), (s.px(0.78), s.py(0.53))], fill=255)
    s.paint(s.arr(t), "#1e130c")
    pillars(s, 0.84, 0.96, 0.53, 3, 0.12, "#1e130c")
    s.water(0.64, tint="#2a1a10", dim=0.7)
    s.ribbon(lambda t: (0.74 + 0.15 * math.cos(t * 14) * (1 - t), 0.55 - 0.45 * t + 0.02 * math.sin(t * 14)),
             "#ffe2a0", width=2, glow_r=12, strength=0.9)
    s.particles(70, 0.55, 0.95, 0.1, 0.55, "#ffe2a0", 0.8, 2.2, glow_r=6, seed=242)


SCENES = {
    "ff1": ff1, "ff2": ff2, "ff3": ff3, "ff4": ff4, "ff5": ff5, "ff6": ff6, "ff7": ff7, "ff8": ff8,
    "ff9": ff9, "ff10": ff10, "ff10_2": ff10_2, "ff11": ff11, "ff12": ff12, "ff13": ff13, "ff14": ff14,
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
    names = sys.argv[1:] or list(SCENES)
    for n in names:
        render(n)
        print("rendered", n)
