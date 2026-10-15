"""LifeStream card art, third set: one original painted scene per title.

Each scene takes its palette, mood and setting from that title's official key art and logo
artwork (as a reference only). Nothing is copied: no characters, creatures, logos or traced
shapes, just original landscapes, skies, light and objects.

Run: python3 scenes3.py [ids...]   (no ids = all)
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from kit2 import Canvas, hexc, mix, smoothstep  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "drawable-nodpi")


def finish(c, shadows, highlights, amount=0.12, vignette=0.45, bloom=0.4, sat=1.05, contrast=1.04):
    c.bloom(0.72, 16, bloom)
    c.canvas_texture()
    c.grade(shadows, highlights, amount, contrast, sat)
    c.vignette(vignette)


# --------------------------------------------------------------------------------------------
# Final Fantasy: navy night and Amano gold. A ruined shrine on a hill, four elemental lights.
def ff1(c):
    c.vgrad([(0, "#0b1430"), (0.45, "#1d2f63"), (0.7, "#6f5a6e"), (0.82, "#d9a75e"), (1, "#2b2030")])
    c.stars(500, 0, 0.55, "#e9e4ff", fade=1 - smoothstep(0.2, 0.6, c.Y))
    c.radial(0.5, 0.86, 0.6, "#ffcf7a", 0.55, 2.0, squash=1.6)
    c.clouds(0.35, 0.7, "#2c3366", lit="#ffcc80", cover=0.42, scale=2.5, seed=3, alpha=0.75, light_dir=(0, 1))
    c.land(0.8, 0.05, "#3a3150", seed=1, haze="#d9a75e", haze_amt=0.35)
    # The shrine: broken colonnade on a hill.
    hill = c.below([(x, 0.74 + 0.1 * ((x - 0.5) / 0.5) ** 2) for x in np.linspace(0, 1, 60)])
    c.paint(hill, "#141226")
    shrine = np.zeros((c.h, c.w), np.float32)
    shrine = np.maximum(shrine, c.rect(0.34, 0.71, 0.66, 0.75))  # steps
    shrine = np.maximum(shrine, c.rect(0.36, 0.695, 0.64, 0.712))
    for i, x in enumerate(np.linspace(0.375, 0.625, 8)):
        if i == 5:
            shrine = np.maximum(shrine, c.rect(x - 0.008, 0.64, x + 0.008, 0.7))  # broken column
            continue
        shrine = np.maximum(shrine, c.rect(x - 0.008, 0.57, x + 0.008, 0.7))
    shrine = np.maximum(shrine, c.rect(0.36, 0.55, 0.56, 0.572))  # lintel, broken at the right
    shrine = np.maximum(shrine, c.poly([(0.355, 0.552), (0.47, 0.49), (0.56, 0.535), (0.56, 0.552)]))
    c.paint(shrine, "#120f20")
    c.radial(0.5, 0.64, 0.1, "#ffb35a", 0.5, 2)
    # Four lights rising: earth, fire, water, wind.
    for (x, y, col) in [(0.26, 0.3, "#f3c85a"), (0.4, 0.2, "#ff6a4a"), (0.6, 0.2, "#5aa8ff"), (0.74, 0.3, "#5fe08f")]:
        o = c.circle(x, y, 0.02)
        c.glow(o, col, 40, 1.3)
        c.add(c.circle(x, y, 0.009), "#ffffff", 0.9)
        c.ribbon(lambda t, x=x, y=y: (x + (0.5 - x) * t * 0.9, y + (0.68 - y) * t ** 1.4), col, 1.2, 10, 0.35, core=0.3)
    c.particles(120, 0.2, 0.8, 0.15, 0.75, "#ffe0a0", 0.4, 1.4, 4, 0.6, seed=2)
    finish(c, "#0a0f2a", "#ffe6b0")


# Final Fantasy II: rose and crimson. A rebel fortress at night under a burning sky, wild roses.
def ff2(c):
    c.vgrad([(0, "#14070d"), (0.4, "#3d0f1d"), (0.66, "#a3322b"), (0.78, "#e2793e"), (1, "#1a0a10")])
    c.clouds(0.15, 0.62, "#2a0b14", lit="#ff8a5a", cover=0.55, scale=2.2, seed=4, alpha=0.85, light_dir=(0, 1))
    c.particles(220, 0, 1, 0.25, 0.8, "#ffb070", 0.4, 1.5, 4, 0.8, seed=5)  # embers
    c.land(0.74, 0.06, "#3c1720", seed=2, haze="#e2793e", haze_amt=0.3)
    # Fortress on a crag, left of centre.
    crag = c.poly([(0.08, 1), (0.16, 0.7), (0.24, 0.64), (0.38, 0.62), (0.47, 0.7), (0.55, 1)])
    c.paint(crag, "#1b0a10")
    f = np.zeros((c.h, c.w), np.float32)
    for x0, x1, top in [(0.2, 0.25, 0.47), (0.25, 0.36, 0.55), (0.36, 0.4, 0.5), (0.28, 0.31, 0.43)]:
        f = np.maximum(f, c.rect(x0, top, x1, 0.65))
        f = np.maximum(f, c.poly([(x0 - 0.006, top), ((x0 + x1) / 2, top - 0.08), (x1 + 0.006, top)]))
    c.paint(f, "#14070c")
    win = np.zeros_like(f)
    for (x, y) in [(0.225, 0.52), (0.295, 0.5), (0.33, 0.58), (0.38, 0.56), (0.27, 0.6)]:
        win = np.maximum(win, c.rect(x, y, x + 0.005, y + 0.014))
    c.glow(win, "#ffb35a", 6, 1.0)
    # A distant warship burning on the horizon (generic airship hull).
    hull = c.ellipse(0.78, 0.42, 0.11, 0.025)
    hull = np.maximum(hull, c.poly([(0.68, 0.43), (0.88, 0.43), (0.85, 0.47), (0.71, 0.47)]))
    for mx, mh in [(0.74, 0.1), (0.79, 0.13), (0.84, 0.09)]:
        hull = np.maximum(hull, c.line([(mx, 0.42), (mx, 0.42 - mh)], 2))
        hull = np.maximum(hull, c.poly([(mx, 0.42 - mh), (mx + 0.03, 0.42 - mh * 0.6), (mx, 0.42 - mh * 0.3)]))
    c.paint(hull, "#26090f")
    fire = c.ellipse(0.8, 0.44, 0.05, 0.02, soft=6) * c.fbm(20, 3, 9)
    c.glow(fire, "#ff7a3a", 18, 1.2)
    c.ribbon(lambda t: (0.82 + t * 0.12, 0.43 - t * 0.25 + 0.03 * math.sin(t * 9)), "#4a1b20", 14, 20, 0.4, core=0)
    # Wild roses in the foreground.
    rng = c.rng
    for i in range(22):
        x, y = rng.uniform(0.0, 1.0), rng.uniform(0.88, 1.02)
        r = 0.01 + (y - 0.86) * 0.08
        stem = c.line([(x, y), (x + rng.uniform(-0.01, 0.01), 1.05)], 2)
        c.paint(stem, "#1a0a0c")
        base = mix("#8a0f22", "#d42a40", rng.random())
        for k in range(5):  # outer petals
            a = 2 * math.pi * k / 5 + rng.random()
            c.paint(c.circle(x + math.cos(a) * r * 0.55, y + math.sin(a) * r * 0.45, r * 0.55), base)
        c.paint(c.circle(x, y, r * 0.55), base * 0.75)
        for k in range(3):  # inner swirl
            a = 2 * math.pi * k / 3 + rng.random()
            c.paint(c.circle(x + math.cos(a) * r * 0.2, y + math.sin(a) * r * 0.18, r * 0.28), np.clip(base * 1.35, 0, 1))
        c.paint(c.circle(x - r * 0.2, y - r * 0.25, r * 0.15), "#ff9aa0", 0.6)
    c.particles(40, 0, 1, 0.5, 0.95, "#e0304a", 1.0, 2.2, 1, 0.8, seed=6)  # drifting petals
    c.haze_below(0.9, "#120509", 0.35)
    finish(c, "#1a0610", "#ffd2a8", vignette=0.5)


# Final Fantasy III: emerald and sky. The floating continent over a sea of clouds, a crystal spire.
def ff3(c):
    c.vgrad([(0, "#0e3a4f"), (0.45, "#3f8fa6"), (0.7, "#bfe3d3"), (1, "#e9f5ea")])
    c.radial(0.75, 0.25, 0.4, "#fff6d0", 0.6, 2.5)
    c.clouds(0.05, 0.4, "#ffffff", lit="#fffbe8", cover=0.35, scale=2.0, seed=2, alpha=0.5, light_dir=(1, -0.5))
    # Sea of clouds below.
    n = c.fbm(3, 6, 8, aspect=3)
    sea = smoothstep(0.68, 0.76, c.Y + (n - 0.5) * 0.12)
    c.paint(sea, "#f4fbf6")
    c.paint(sea * smoothstep(0.75, 1.0, c.Y), "#b7d9d0", 0.6)
    # The floating continent: grassy top, rocky underside tapering down.
    top = [(x, 0.48 - 0.02 * math.sin(x * 9) - 0.03 * math.exp(-((x - 0.45) / 0.08) ** 2)) for x in np.linspace(0.12, 0.88, 80)]
    under = [(0.88, 0.5), (0.74, 0.58), (0.62, 0.7), (0.52, 0.8), (0.46, 0.72), (0.34, 0.62), (0.2, 0.55), (0.12, 0.5)]
    rock = c.poly(top + under)
    shade = c.fbm(25, 4, 3)
    c.paint(rock, hexc("#3b5b4a")[None, None, :] * (0.6 + shade[..., None] * 0.6))
    grass = rock * (1 - smoothstep(0.47, 0.53, c.Y + (shade - 0.5) * 0.04))
    c.paint(grass, "#4fa86a")
    c.paint(grass * c.fbm(60, 3, 4), "#8fdc8a", 0.5)
    trees = c.particles  # little forest dots
    trees(260, 0.18, 0.8, 0.44, 0.49, "#1f5a3a", 1.5, 3.5, 0, 0.0, seed=6)
    for i in range(160):
        x, y = c.rng.uniform(0.18, 0.82), c.rng.uniform(0.445, 0.49)
        c.paint(c.ellipse(x, y, 0.006, 0.01), "#21603d")
    # Waterfalls off the rim.
    for x in (0.3, 0.68):
        fall = c.poly([(x - 0.006, 0.5), (x + 0.006, 0.5), (x + 0.014, 0.8), (x - 0.014, 0.8)], soft=1)
        c.paint(fall * (1 - smoothstep(0.55, 0.8, c.Y)), "#e8fbff", 0.85)
    # A crystal spire on the plateau.
    spire = c.poly([(0.55, 0.16), (0.565, 0.44), (0.535, 0.44)])
    c.paint(spire, "#cfeff3")
    c.paint(c.poly([(0.55, 0.16), (0.565, 0.44), (0.55, 0.44)]), "#7fb8c6")
    c.glow(c.circle(0.55, 0.16, 0.005), "#ffffff", 20, 1.0)
    c.particles(60, 0.45, 0.65, 0.1, 0.45, "#ffffff", 0.4, 1.2, 3, 0.6, seed=7)
    finish(c, "#0a2a36", "#fffbe6", amount=0.08, vignette=0.35, bloom=0.3)


# Final Fantasy IV: violet night, two moons, a fleet of red-sailed airships over a still sea.
def ff4(c):
    c.vgrad([(0, "#070718"), (0.35, "#1b1846"), (0.62, "#4b3a7c"), (0.68, "#8a6aa8"), (1, "#0a0920")])
    c.stars(700, 0, 0.62, "#eae6ff", fade=1 - smoothstep(0.3, 0.66, c.Y))
    big = c.circle(0.68, 0.28, 0.15)
    c.add(c.blur(big, 50), "#b9a8ff", 0.5)
    tex = c.fbm(8, 5, 11)
    c.paint(big, hexc("#d8d2f0")[None, None, :] * (0.75 + tex[..., None] * 0.4))
    c.paint(big * c.circle(0.62, 0.24, 0.15, soft=20), "#1b1846", 0.35)
    small = c.circle(0.3, 0.16, 0.04)
    c.glow(small, "#9bb7ff", 20, 0.4, core=0)
    c.paint(small, "#b8c6f2")
    c.clouds(0.4, 0.62, "#2a2456", lit="#c7b8ff", cover=0.4, scale=3, seed=4, alpha=0.7)
    c.land(0.66, 0.03, "#1f1a44", seed=4, haze="#8a6aa8", haze_amt=0.4)
    c.water(0.68, "#0d0b2a", dim=0.7, tint_amt=0.3, sparkle="#d8ccff")
    # Airships with red sails, nearer ones larger.
    for x, y, s in [(0.2, 0.44, 0.06), (0.36, 0.5, 0.045), (0.12, 0.56, 0.035), (0.48, 0.42, 0.03), (0.86, 0.5, 0.04)]:
        hull = c.ellipse(x, y, s, s * 0.22)
        hull = np.maximum(hull, c.poly([(x + s * 0.9, y), (x + s * 1.4, y - s * 0.12), (x + s * 1.2, y + s * 0.05)]))
        c.paint(hull, "#120e26")
        sail = c.poly([(x - s * 0.2, y - s * 0.15), (x + s * 0.1, y - s * 0.9), (x + s * 0.45, y - s * 0.15)])
        c.paint(sail, "#b3263a")
        c.paint(c.poly([(x - s * 0.2, y - s * 0.15), (x + s * 0.1, y - s * 0.9), (x + s * 0.05, y - s * 0.15)]), "#7a1426")
        c.glow(c.circle(x - s * 0.8, y + s * 0.05, 0.003), "#ffb070", 8, 0.8)
    finish(c, "#06061a", "#e6dcff", vignette=0.5)


# Final Fantasy V: teal wind over grassland; a crystal shrine breaking apart into shards.
def ff5(c):
    c.vgrad([(0, "#0f3c4a"), (0.4, "#2f7f86"), (0.62, "#a8d7c2"), (0.66, "#e7e2b8"), (1, "#244a36")])
    c.radial(0.3, 0.6, 0.45, "#fff4c8", 0.5, 2)
    c.clouds(0.05, 0.55, "#e9f7f2", lit="#ffffff", cover=0.38, scale=2.0, seed=5, alpha=0.55, stretch=5)
    c.streaks(220, "#ffffff", angle=-8, length=(0.05, 0.2), alpha=0.18, seed=1, width=0.8)  # wind
    c.land(0.64, 0.05, "#3e7a72", seed=1, haze="#a8d7c2", haze_amt=0.5)
    c.land(0.7, 0.04, "#2f6a4a", seed=2, haze="#a8d7c2", haze_amt=0.2)
    g = c.land(0.78, 0.03, "#2d5e33", seed=3)
    c.paint(g * c.blur(c.fbm(8, 4, 3), 4), "#5f9a4a", 0.5)
    c.streaks(500, "#bfe8a0", angle=-80, length=(0.008, 0.02), alpha=0.18, seed=2)
    # Shrine pillar with the crystal at its top, shards breaking off.
    tower = c.poly([(0.6, 0.72), (0.615, 0.33), (0.645, 0.33), (0.66, 0.72)])
    c.paint(tower, "#1d3c3a")
    c.paint(c.poly([(0.63, 0.72), (0.63, 0.33), (0.645, 0.33), (0.66, 0.72)]), "#2c5753")
    c.crystal(0.63, 0.22, 0.07, 0.2, "#7ff2e0", glow=50, glow_s=1.0)
    rng = c.rng
    for _ in range(14):
        x, y = 0.63 + rng.uniform(-0.18, 0.2), 0.24 + rng.uniform(-0.15, 0.12)
        c.crystal(x, y, 0.008 + rng.random() * 0.012, 0.02 + rng.random() * 0.025, "#9ff6ea",
                  glow=10, glow_s=0.5, tilt=rng.uniform(-1, 1))
    c.beams_from = None
    c.particles(90, 0.4, 0.9, 0.05, 0.5, "#e0fffa", 0.4, 1.4, 4, 0.7, seed=3)
    finish(c, "#08303a", "#fffbe0", amount=0.08, vignette=0.4)


# Final Fantasy VI: sepia and amber steampunk. A snowy mining town of chimneys at night.
def ff6(c):
    c.vgrad([(0, "#140d0a"), (0.4, "#3e2a1e"), (0.62, "#8a5a32"), (0.7, "#c98c4a"), (1, "#1a120e")])
    c.clouds(0.05, 0.6, "#2e1f17", lit="#e0a060", cover=0.55, scale=2.4, seed=6, alpha=0.8, light_dir=(0, 1))
    c.land(0.62, 0.12, "#3a2a22", seed=5, haze="#c98c4a", haze_amt=0.45)
    c.land(0.68, 0.1, "#2a1d17", seed=6, haze="#c98c4a", haze_amt=0.2)
    # Snow on the slopes.
    snow = c.below(c.ridge(0.66, 0.1, 6)) * smoothstep(0.55, 0.75, c.fbm(30, 3, 2))
    c.paint(snow * (1 - smoothstep(0.7, 0.85, c.Y)), "#e8d9c4", 0.35)
    # Town: stacked houses, chimneys, pipes, a pithead frame.
    town = c.skyline(0.86, "#120c09", seed=3, x0=0.0, x1=1.0, hmin=0.05, hmax=0.17, wmin=0.03, wmax=0.06,
                     lights="#ffb055", light_density=0.12, spires=0.0)
    roofs = np.zeros_like(town)
    for x in np.linspace(0.04, 0.96, 16):
        roofs = np.maximum(roofs, c.rect(x - 0.004, 0.62 + 0.08 * math.sin(x * 13) ** 2, x + 0.004, 0.86))
    c.paint(roofs, "#120c09")
    frame = c.line([(0.7, 0.86), (0.74, 0.55), (0.78, 0.86)], 3) + c.line([(0.715, 0.74), (0.765, 0.74)], 2)
    frame += c.line([(0.705, 0.8), (0.775, 0.8)], 2)
    c.paint(np.clip(frame, 0, 1), "#120c09")
    c.glow(c.circle(0.74, 0.55, 0.012), "#ffb055", 12, 0.8, core=0.4)
    # Steam plumes.
    for x in np.linspace(0.06, 0.94, 9):
        plume = c.ribbon(lambda t, x=x: (x + 0.05 * t + 0.01 * math.sin(t * 8), 0.66 - 0.35 * t), "#000000", 6, 0, 0, core=0)
        c.paint(c.blur(plume, 12), "#c9b49a", 0.25)
    gnd = c.below([(x, 0.86 + 0.01 * math.sin(x * 17)) for x in np.linspace(0, 1, 50)])
    c.paint(gnd, "#8a7660")
    c.paint(gnd * smoothstep(0.86, 1.0, c.Y), "#2a1d15", 0.7)
    c.paint(gnd * c.fbm(20, 4, 9), "#c8b498", 0.3)
    c.particles(900, 0, 1, 0, 1, "#f4e8d8", 0.4, 1.6, 2, 0.6, seed=8)  # snowfall
    finish(c, "#1a0f08", "#ffe0b0", amount=0.15, vignette=0.5, sat=0.85)


# Final Fantasy VII: black, green and a red-gold sky. The great city on the horizon, a falling star.
def ff7(c):
    c.vgrad([(0, "#0b0507"), (0.3, "#3a0f0c"), (0.55, "#a7341d"), (0.68, "#f09a45"), (0.72, "#3a2a1c"), (1, "#0c0b0a")])
    c.clouds(0.05, 0.6, "#2a0907", lit="#ffb05a", cover=0.5, scale=2.2, seed=7, alpha=0.75, light_dir=(-0.6, 1))
    # The falling star dominates the sky.
    m = c.circle(0.7, 0.24, 0.13)
    c.add(c.blur(m, 90), "#ff6a2a", 1.0)
    surf = c.fbm(10, 6, 12)
    c.paint(m, hexc("#7a1a0c")[None, None, :] * 0.5 + hexc("#ff7a30")[None, None, :] * surf[..., None] * 0.9)
    c.add(m * c.circle(0.65, 0.3, 0.12, soft=30), "#ffd27a", 0.6)
    c.paint(m * c.circle(0.78, 0.17, 0.13, soft=30), "#2a0806", 0.5)
    # Wasteland and the disc city.
    c.land(0.72, 0.03, "#3a2216", seed=4, haze="#f09a45", haze_amt=0.35)
    city = c.ellipse(0.32, 0.665, 0.25, 0.035)
    city = np.maximum(city, c.rect(0.07, 0.62, 0.57, 0.665))
    c.paint(city, "#120a08")
    tower = c.poly([(0.305, 0.665), (0.31, 0.5), (0.33, 0.5), (0.335, 0.665)])
    c.paint(tower, "#120a08")
    reactors = np.zeros_like(city)
    for x in np.linspace(0.1, 0.54, 8):
        reactors = np.maximum(reactors, c.rect(x - 0.006, 0.58, x + 0.006, 0.64))
        c.glow(c.circle(x, 0.58, 0.004), "#6dff9a", 10, 0.9)
    c.paint(reactors, "#120a08")
    c.particles(300, 0.08, 0.56, 0.6, 0.68, "#ffcf7a", 0.4, 1.0, 2, 0.6, seed=4)
    # Green glow rising from the land.
    c.ribbon(lambda t: (0.55 + 0.3 * t + 0.03 * math.sin(t * 7), 0.95 - 0.6 * t), "#5dff98", 2.5, 30, 0.8, core=0.4)
    c.ribbon(lambda t: (0.6 + 0.35 * t, 0.98 - 0.55 * t + 0.03 * math.sin(t * 9)), "#8affd0", 1.5, 18, 0.5, core=0.3)
    c.land(0.86, 0.04, "#0d0807", seed=8)
    finish(c, "#0a0303", "#ffd8a0", vignette=0.5)


# Final Fantasy VIII: pale blue and white. A ringed academy floats over the sea; feathers drift.
def ff8(c):
    c.vgrad([(0, "#1b3f6e"), (0.4, "#5e8fc4"), (0.62, "#cfe2f2"), (0.66, "#f4efe8"), (1, "#2a5578")])
    c.radial(0.72, 0.62, 0.35, "#fff5e6", 0.35, 2)
    c.clouds(0.05, 0.55, "#eef4fb", lit="#ffffff", cover=0.32, scale=2.0, seed=8, alpha=0.5, stretch=4)
    c.land(0.64, 0.025, "#6f8fae", seed=1, haze="#cfe2f2", haze_amt=0.5)
    c.water(0.65, "#244b72", dim=0.75, tint_amt=0.35, sparkle="#ffffff", sparkle_amt=0.45)
    # The floating ring-school: a hovering disc with a ring halo.
    x, y = 0.34, 0.47
    body = c.ellipse(x, y, 0.15, 0.045)
    body = np.maximum(body, c.poly([(x - 0.09, y), (x - 0.05, y - 0.13), (x + 0.05, y - 0.13), (x + 0.09, y)]))
    body = np.maximum(body, c.poly([(x - 0.06, y + 0.03), (x, y + 0.14), (x + 0.06, y + 0.03)]))
    c.paint(body, "#dfe7f1")
    c.paint(body * smoothstep(y - 0.13, y + 0.14, c.Y), "#4f6f94", 0.75)
    c.paint(body * smoothstep(x - 0.02, x + 0.15, c.X), "#ffffff", 0.25)
    for k in range(6):
        wy = y - 0.11 + k * 0.02
        c.glow(c.rect(x - 0.04, wy, x + 0.04, wy + 0.004) * body, "#ffe8b0", 2, 0.5)
    ring = c.ellipse(x, y - 0.12, 0.11, 0.022) - c.ellipse(x, y - 0.12, 0.095, 0.015)
    c.paint(np.clip(ring, 0, 1), "#aebfd4")
    c.glow(c.ellipse(x, y + 0.05, 0.1, 0.01), "#bfe8ff", 16, 0.9)
    # Its reflection is handled by painting a soft streak below.
    c.paint(c.ellipse(x, 0.74, 0.06, 0.05, soft=12), "#cfe0f0", 0.25)
    rng = c.rng
    for _ in range(14):
        c.feather(rng.uniform(0.45, 0.98), rng.uniform(0.08, 0.9), rng.uniform(0.02, 0.045), rng.uniform(0, 360),
                  "#ffffff", 0.85)
    finish(c, "#0f2a4a", "#fffaf0", amount=0.08, vignette=0.35, bloom=0.35)


# Final Fantasy IX: warm storybook dusk. A lakeside castle of blade-like towers, a sail airship.
def ff9(c):
    c.vgrad([(0, "#2b1d4a"), (0.35, "#7a4a7a"), (0.58, "#e69a6a"), (0.66, "#ffd59a"), (1, "#3a2340")])
    c.radial(0.5, 0.64, 0.4, "#fff0c0", 0.7, 2)
    c.clouds(0.1, 0.55, "#5a3a66", lit="#ffcf9a", cover=0.42, scale=2.4, seed=9, alpha=0.7, light_dir=(0, 1))
    c.land(0.64, 0.05, "#5b3e5e", seed=2, haze="#ffd59a", haze_amt=0.45)
    # Castle: central keep with tall blade-shaped towers.
    ca = np.zeros((c.h, c.w), np.float32)
    ca = np.maximum(ca, c.rect(0.36, 0.52, 0.64, 0.66))
    for x, h, w in [(0.5, 0.2, 0.018), (0.43, 0.32, 0.012), (0.57, 0.32, 0.012), (0.38, 0.4, 0.009), (0.62, 0.4, 0.009)]:
        ca = np.maximum(ca, c.poly([(x - w, 0.6), (x - w, h + 0.04), (x, h), (x + w, h + 0.04), (x + w, 0.6)]))
    for x in np.linspace(0.37, 0.63, 9):
        ca = np.maximum(ca, c.poly([(x - 0.012, 0.52), (x, 0.47), (x + 0.012, 0.52)]))
    c.paint(ca, "#3a2440")
    c.paint(ca * (1 - smoothstep(0.3, 0.66, c.Y)) * smoothstep(0.44, 0.5, c.X), "#ffcf9a", 0.25)
    win = np.zeros_like(ca)
    for _ in range(30):
        x, y = c.rng.uniform(0.37, 0.63), c.rng.uniform(0.53, 0.64)
        win = np.maximum(win, c.rect(x, y, x + 0.003, y + 0.008))
    c.glow(win, "#ffd27a", 5, 1.0)
    c.water(0.66, "#4a2c4a", dim=0.75, tint_amt=0.25, sparkle="#ffe0b0")
    # A sail airship drifting in from the right.
    x, y, s = 0.8, 0.3, 0.06
    hull = c.poly([(x - s, y), (x + s, y), (x + s * 0.7, y + s * 0.35), (x - s * 0.8, y + s * 0.3)])
    c.paint(hull, "#4a2a3a")
    for k, h in [(-0.4, 1.2), (0.2, 1.0)]:
        sail = c.poly([(x + k * s, y - s * 0.05), (x + k * s, y - s * h), (x + (k + 0.45) * s, y - s * 0.1)])
        c.paint(sail, "#f2e2c8")
    c.glow(c.ellipse(x + s * 1.1, y + s * 0.15, 0.012, 0.006), "#ffd27a", 6, 0.6)
    c.particles(80, 0, 1, 0.1, 0.6, "#fff0c8", 0.4, 1.2, 4, 0.5, seed=9)
    finish(c, "#1e1236", "#fff0d0", amount=0.1, vignette=0.42)


# Final Fantasy X: ocean blue and white. Ruins of a city at night; glowing motes rise to the stars.
def ff10(c):
    c.vgrad([(0, "#040a1c"), (0.4, "#0e2850"), (0.62, "#1d5e8c"), (0.68, "#5fb5d6"), (1, "#03101e")])
    c.stars(600, 0, 0.6, "#dff4ff", fade=1 - smoothstep(0.25, 0.62, c.Y))
    c.radial(0.5, 0.7, 0.5, "#7fd8ff", 0.4, 2)
    # Ruined towers: tall shafts with broken, slanted tops.
    ru = np.zeros((c.h, c.w), np.float32)
    rng = c.rng
    x = 0.03
    while x < 0.97:
        w = rng.uniform(0.015, 0.045)
        h = rng.uniform(0.06, 0.32) * (1 - abs(x - 0.5))
        tilt = rng.uniform(-0.04, 0.04)
        ru = np.maximum(ru, c.poly([(x, 0.68), (x, 0.67 - h + tilt), (x + w, 0.67 - h - tilt), (x + w, 0.68)]))
        x += w + rng.uniform(0.0, 0.03)
    c.paint(ru, "#0a1a30")
    c.paint(ru * c.fbm(40, 3, 6), "#14304e", 0.6)
    c.paint(ru * smoothstep(0.4, 0.68, c.Y), "#2a7aa0", 0.25)
    c.water(0.68, "#06223e", dim=0.7, tint_amt=0.3, sparkle="#bfefff")
    # Rising motes.
    c.particles(160, 0.05, 0.95, 0.15, 0.85, "#8ff6ff", 1.5, 4.5, 14, 1.2, seed=10)
    c.particles(50, 0.1, 0.9, 0.3, 0.8, "#fff4b0", 1.5, 3.5, 12, 1.0, seed=11)
    for k in range(5):
        x0 = 0.15 + k * 0.18
        c.ribbon(lambda t, x0=x0: (x0 + 0.04 * math.sin(t * 6 + k), 0.75 - 0.6 * t), "#6fe8ff", 0.8, 10, 0.25, core=0.2)
    finish(c, "#020814", "#d8f6ff", amount=0.1, vignette=0.45)


# Final Fantasy X-2: bright pop sunset over a tropical sea, a stage-light sky.
def ff10_2(c):
    c.vgrad([(0, "#1a0b3a"), (0.35, "#6a1e78"), (0.55, "#ff4f8a"), (0.66, "#ffb36a"), (0.68, "#ffe6a0"), (1, "#1d1240")])
    for a, col in [(-120, "#6fe8ff"), (-100, "#ff7ad8"), (-80, "#ffe36f"), (-60, "#6fe8ff")]:
        beam = c.poly([(0.5, 0.7), (0.5 + 1.2 * math.cos(math.radians(a - 3)), 0.7 + 1.2 * math.sin(math.radians(a - 3))),
                       (0.5 + 1.2 * math.cos(math.radians(a + 3)), 0.7 + 1.2 * math.sin(math.radians(a + 3)))], soft=10)
        c.add(beam, col, 0.25)
    c.clouds(0.2, 0.6, "#5a1866", lit="#ffd0a0", cover=0.38, scale=2.5, seed=12, alpha=0.6, light_dir=(0, 1))
    c.radial(0.5, 0.68, 0.35, "#fff0c0", 0.8, 2.5)
    c.land(0.67, 0.02, "#3a1a50", seed=3, haze="#ffb36a", haze_amt=0.4)
    c.water(0.68, "#2a1050", dim=0.8, tint_amt=0.25, sparkle="#ffe0f0", sparkle_amt=0.5)
    # Palm silhouettes left and right.
    for bx, lean in [(0.08, 0.12), (0.92, -0.1)]:
        trunk = c.line([(bx + lean * t ** 1.5 * 0.3, 1.0 - 0.55 * t) for t in np.linspace(0, 1, 30)], 7)
        c.paint(trunk, "#12081e")
        tx, ty = bx + lean * 0.3, 0.45
        for k in range(8):
            ang = math.radians(-160 + k * 40)
            fr = c.line([(tx + math.cos(ang) * 0.14 * t, ty + math.sin(ang) * 0.1 * t + 0.08 * t * t) for t in np.linspace(0, 1, 20)], 6)
            c.paint(fr, "#12081e")
    c.particles(160, 0, 1, 0.05, 0.6, "#fff0ff", 0.5, 1.8, 5, 0.7, seed=12)
    finish(c, "#14062a", "#fff0e0", amount=0.08, vignette=0.4, sat=1.1)


# Final Fantasy XI: gold and teal fantasy MMO dusk. A great crystal over three distant towns.
def ff11(c):
    c.vgrad([(0, "#0c1c2c"), (0.4, "#1f4a5c"), (0.62, "#c79a52"), (0.7, "#f2d38a"), (1, "#1a2422")])
    c.stars(250, 0, 0.4, "#ffffff", fade=1 - smoothstep(0.1, 0.4, c.Y))
    c.clouds(0.15, 0.62, "#2a4a56", lit="#ffe0a0", cover=0.4, scale=2.4, seed=13, alpha=0.7, light_dir=(0, 1))
    c.land(0.66, 0.08, "#33484a", seed=6, haze="#f2d38a", haze_amt=0.45)
    c.land(0.72, 0.06, "#243634", seed=7, haze="#f2d38a", haze_amt=0.2)
    for x, s in [(0.18, 0.7), (0.5, 1.0), (0.82, 0.75)]:
        mound = c.ellipse(x, 0.74, 0.1 * s, 0.035)
        c.paint(mound, "#1c2a28")
        c.skyline(0.725, "#151f1e", seed=int(x * 100), x0=x - 0.06 * s, x1=x + 0.06 * s, hmin=0.02, hmax=0.07 * s,
                  wmin=0.008, wmax=0.016, lights="#ffcf7a", light_density=0.2, spires=0.5)
    c.crystal(0.5, 0.3, 0.07, 0.22, "#8fe8ff", glow=60, glow_s=1.0)
    ring = c.ellipse(0.5, 0.33, 0.16, 0.035) - c.ellipse(0.5, 0.33, 0.155, 0.031)
    c.glow(np.clip(ring, 0, 1), "#ffe7a6", 8, 0.8)
    c.land(0.84, 0.04, "#0f1716", seed=9)
    c.particles(120, 0.3, 0.7, 0.1, 0.6, "#c8f6ff", 0.4, 1.5, 4, 0.7, seed=13)
    finish(c, "#081620", "#fff0c8", amount=0.1, vignette=0.45)


# Final Fantasy XII: Ivalice turquoise and sand. Floating isles with skystone glow and airships.
def ff12(c):
    c.vgrad([(0, "#0f4d6a"), (0.45, "#4fa2b8"), (0.66, "#e6d4a2"), (1, "#c09a62")])
    c.radial(0.25, 0.3, 0.4, "#fff6d8", 0.5, 2.5)
    c.clouds(0.3, 0.9, "#fff4e0", lit="#ffffff", cover=0.4, scale=2.5, seed=14, alpha=0.75, stretch=3)
    for (x, y, s, seed) in [(0.65, 0.42, 1.0, 1), (0.3, 0.58, 0.6, 2), (0.88, 0.25, 0.45, 3)]:
        top = [(x + (t - 0.5) * 0.36 * s, y + 0.01 * math.sin(t * 20)) for t in np.linspace(0, 1, 30)]
        under = [(x + 0.16 * s, y + 0.04 * s), (x + 0.06 * s, y + 0.16 * s), (x, y + 0.26 * s), (x - 0.07 * s, y + 0.14 * s),
                 (x - 0.18 * s, y + 0.03 * s)]
        isle = c.poly(top + under)
        sh = c.fbm(20, 4, seed)
        c.paint(isle, hexc("#8a6a48")[None, None, :] * (0.55 + sh[..., None] * 0.6))
        c.paint(isle * (1 - smoothstep(y, y + 0.03 * s, c.Y)), "#6fa86a")
        c.glow(c.circle(x, y + 0.2 * s, 0.01 * s), "#8ffff0", 20 * s, 1.0)
        # buildings
        for k in range(int(6 * s) + 2):
            bx = x + c.rng.uniform(-0.12, 0.12) * s
            c.paint(c.rect(bx, y - 0.04 * s * c.rng.uniform(0.5, 1.5), bx + 0.018 * s, y + 0.005), "#e8dcc0")
        c.paint(c.ellipse(x, y - 0.06 * s, 0.03 * s, 0.03 * s) * (c.Y < y - 0.055 * s), "#f4ecd6")
    for x, y, s in [(0.2, 0.25, 0.04), (0.45, 0.2, 0.025), (0.12, 0.42, 0.02)]:
        hull = c.ellipse(x, y, s, s * 0.3)
        hull = np.maximum(hull, c.poly([(x - s * 0.6, y), (x - s * 1.3, y - s * 0.5), (x - s * 1.0, y)]))
        hull = np.maximum(hull, c.poly([(x - s * 0.6, y), (x - s * 1.3, y + s * 0.5), (x - s * 1.0, y)]))
        c.paint(hull, "#4a4e5a")
        c.glow(c.circle(x - s * 1.0, y, 0.003), "#8ffff0", 6, 0.8)
    finish(c, "#0a3040", "#fff4d8", amount=0.08, vignette=0.35)


# Final Fantasy XIII: a shell world hanging in the sky over wild lands, held by a crystal pillar.
def ff13(c):
    c.vgrad([(0, "#0a1626"), (0.4, "#1f3f5c"), (0.62, "#6ea8a8"), (0.7, "#cfe6d0"), (1, "#14261e")])
    c.radial(0.5, 0.4, 0.6, "#c8f0ff", 0.3, 2)
    sphere = c.circle(0.5, 0.2, 0.3)
    c.add(c.blur(sphere, 40), "#9fe0ff", 0.3)
    shell = c.blur(c.fbm(10, 5, 15), 3)
    c.paint(sphere, hexc("#33506a")[None, None, :] * (0.65 + shell[..., None] * 0.6))
    c.paint(sphere * c.circle(0.42, 0.12, 0.3, soft=40), "#cfeaff", 0.35)
    c.paint(sphere * smoothstep(0.3, 0.42, c.dist(0.5, 0.2) * 1.3), "#0e1c2c", 0.5)  # darker limb
    rim = c.circle(0.5, 0.2, 0.3) - c.circle(0.5, 0.21, 0.295)
    c.glow(np.clip(rim, 0, 1), "#bfefff", 10, 0.6)
    # Crystal pillar from the ground to the sphere.
    pillar = c.poly([(0.47, 0.5), (0.53, 0.5), (0.58, 0.85), (0.42, 0.85)])
    c.add(c.blur(pillar, 30), "#aef4ff", 0.6)
    c.paint(pillar, "#bdf2ff", 0.9)
    c.paint(c.poly([(0.5, 0.5), (0.53, 0.5), (0.58, 0.85), (0.5, 0.85)]), "#e8fdff", 0.8)
    c.land(0.72, 0.08, "#2f5a4a", seed=3, haze="#cfe6d0", haze_amt=0.4)
    c.land(0.8, 0.06, "#1d3a2c", seed=4, haze="#cfe6d0", haze_amt=0.15)
    c.paint(c.poly([(0.42, 0.85), (0.58, 0.85), (0.6, 0.9), (0.4, 0.9)]), "#cdf6ff", 0.8)
    c.land(0.9, 0.03, "#0c1a14", seed=5)
    c.particles(160, 0.3, 0.7, 0.4, 0.9, "#e0ffff", 0.4, 1.5, 4, 0.8, seed=15)
    finish(c, "#081420", "#e8fff8", amount=0.1, vignette=0.45)


# Final Fantasy XIII-2: violet storm over a timeless sea; broken arches of time drift.
def ff13_2(c):
    c.vgrad([(0, "#0a0618"), (0.35, "#2a1650"), (0.6, "#6a4aa0"), (0.66, "#a890d8"), (1, "#0a0a1e")])
    c.clouds(0.0, 0.6, "#1a0f36", lit="#c8b0ff", cover=0.55, scale=2.0, seed=16, alpha=0.85, light_dir=(0, 1))
    bolt = [(0.78, 0.0)]
    for k in range(10):
        x, y = bolt[-1]
        bolt.append((x + c.rng.uniform(-0.03, 0.03), y + 0.06))
    c.glow(c.line(bolt, 2), "#e0d0ff", 14, 1.2)
    c.land(0.66, 0.02, "#2a2050", seed=2, haze="#a890d8", haze_amt=0.4)
    c.water(0.67, "#140c30", dim=0.7, tint_amt=0.3, sparkle="#e0d0ff")
    for (x, y, r, rot) in [(0.3, 0.42, 0.11, 0), (0.6, 0.3, 0.06, 0.4), (0.15, 0.2, 0.05, -0.3)]:
        arch = c.ellipse(x, y, r, r * 1.35) - c.ellipse(x, y, r * 0.82, r * 1.17)
        arch = np.clip(arch, 0, 1) * (c.Y < y + r * 0.6)
        c.paint(arch, "#d8c8f0")
        c.glow(arch * smoothstep(y - r, y + r, c.Y), "#b8a0ff", 10, 0.5, core=0)
        for k in range(12):
            a = 2 * math.pi * k / 12
            tick = c.line([(x + math.cos(a) * r * 0.6, y + math.sin(a) * r * 0.8), (x + math.cos(a) * r * 0.7, y + math.sin(a) * r * 0.95)], 1.5)
            c.add(tick, "#f0e8ff", 0.8)
    c.particles(220, 0, 1, 0.1, 0.65, "#e8dcff", 0.4, 1.4, 4, 0.8, seed=16)
    finish(c, "#08041a", "#f0e8ff", amount=0.1, vignette=0.5)


# Final Fantasy XIV: a red moon cracking open over the realm, fire seeping through.
def ff14(c):
    c.vgrad([(0, "#0c0610"), (0.35, "#2e0f20"), (0.6, "#7a2a2a"), (0.7, "#c8783e"), (1, "#120c10")])
    c.stars(300, 0, 0.5, "#ffe0d0", fade=1 - smoothstep(0.1, 0.5, c.Y))
    moon = c.circle(0.62, 0.24, 0.12)
    c.add(c.blur(moon, 70), "#ff5a2a", 0.7)
    c.paint(moon, hexc("#5a1a14")[None, None, :] * (0.7 + c.fbm(10, 5, 17)[..., None] * 0.6))
    cracks = np.zeros_like(moon)
    for k in range(7):
        a = c.rng.uniform(0, 2 * math.pi)
        pts = [(0.62, 0.24)]
        for _ in range(8):
            x, y = pts[-1]
            pts.append((x + math.cos(a) * 0.02 + c.rng.uniform(-0.01, 0.01), y + math.sin(a) * 0.03 + c.rng.uniform(-0.01, 0.01)))
        cracks = np.maximum(cracks, c.line(pts, 2))
    c.glow(cracks * moon, "#ffb050", 8, 1.4)
    c.particles(80, 0.5, 0.75, 0.1, 0.45, "#ffb070", 0.6, 2.0, 6, 0.8, seed=17)
    c.clouds(0.3, 0.7, "#3a1420", lit="#ff9a5a", cover=0.45, scale=2.5, seed=17, alpha=0.75, light_dir=(0.3, -1))
    c.land(0.7, 0.08, "#3a2028", seed=5, haze="#c8783e", haze_amt=0.4)
    # A walled city on the cliffs, left.
    cliff = c.poly([(0, 0.62), (0.16, 0.6), (0.3, 0.66), (0.38, 0.78), (0.4, 1), (0, 1)])
    c.paint(cliff, "#1a0d12")
    c.skyline(0.62, "#1a0d12", seed=17, x0=0.02, x1=0.28, hmin=0.03, hmax=0.12, wmin=0.012, wmax=0.03,
              lights="#ffb055", light_density=0.15, spires=0.5)
    c.land(0.86, 0.05, "#0e080a", seed=9)
    finish(c, "#0c0408", "#ffd8b0", vignette=0.5)


# Final Fantasy XV: black and silver night road. A great stone in a crater, a walled city far off.
def ff15(c):
    c.vgrad([(0, "#05070c"), (0.4, "#121a2a"), (0.6, "#2e3a50"), (0.66, "#8c8a8a"), (1, "#08080a")])
    c.stars(800, 0, 0.6, "#e8eeff", fade=1 - smoothstep(0.3, 0.62, c.Y))
    c.ribbon(lambda t: (t, 0.3 - 0.18 * math.sin(t * math.pi)), "#9fb0ff", 30, 60, 0.12, core=0)  # milky way
    c.particles(400, 0, 1, 0.1, 0.4, "#ffffff", 0.3, 0.9, 2, 0.5, seed=4)
    c.land(0.64, 0.05, "#2a2a32", seed=4, haze="#8c8a8a", haze_amt=0.4)
    # The meteor-stone in its crater.
    rock = c.poly([(0.66, 0.64), (0.69, 0.48), (0.74, 0.42), (0.8, 0.45), (0.84, 0.52), (0.86, 0.64)])
    c.paint(rock, hexc("#2a2620")[None, None, :] * (0.6 + c.fbm(30, 4, 2)[..., None] * 0.6))
    c.glow(c.ellipse(0.76, 0.64, 0.14, 0.02), "#ff9a4a", 30, 0.8)
    # The city's skyline under its dome, far left.
    c.skyline(0.64, "#151824", seed=15, x0=0.06, x1=0.3, hmin=0.02, hmax=0.11, wmin=0.006, wmax=0.016,
              lights="#cfe0ff", light_density=0.3, spires=0.4)
    dome = c.ellipse(0.18, 0.64, 0.16, 0.17) - c.ellipse(0.18, 0.64, 0.155, 0.165)
    c.glow(np.clip(dome, 0, 1) * (c.Y < 0.64), "#8fb8ff", 10, 0.5)
    # The road toward the horizon.
    road = c.poly([(0.47, 0.645), (0.49, 0.645), (0.75, 1.0), (0.15, 1.0)])
    c.paint(c.below([(0, 0.68), (1, 0.66)]), "#0c0c10")
    c.paint(road, "#1c1c22")
    for k in range(10):
        t0, t1 = (k / 10) ** 2, ((k + 0.45) / 10) ** 2
        y0, y1 = 0.645 + 0.355 * t0, 0.645 + 0.355 * t1
        x0, x1 = 0.48 - 0.03 * t0, 0.48 - 0.03 * t1
        c.paint(c.poly([(x0 - 0.001, y0), (x0 + 0.001, y0), (x1 + 0.004 * t1 + 0.001, y1), (x1 - 0.004 * t1 - 0.001, y1)]), "#e8e0c0", 0.8)
    c.glow(c.circle(0.478, 0.66, 0.003), "#ffdca0", 8, 0.8)
    finish(c, "#04060c", "#e8eeff", amount=0.1, vignette=0.5, sat=0.9)


# Final Fantasy XVI: fire against ice. A colossal crystal set in a mountain, the sky split in two.
def ff16(c):
    split = smoothstep(0.35, 0.65, c.X + (c.fbm(3, 4, 8) - 0.5) * 0.25)
    warm = c.gradient_field([(0, "#1a0604"), (0.5, "#8a2a10"), (0.75, "#f07a2a"), (1, "#2a0c06")], c.Y)
    cold = c.gradient_field([(0, "#04101e"), (0.5, "#1e4a7a"), (0.75, "#8fd0f0"), (1, "#081626")], c.Y)
    c.img = warm * (1 - split[..., None]) + cold * split[..., None]
    c.clouds(0.05, 0.6, "#1a0c0c", lit="#ffa060", cover=0.45, scale=2.2, seed=18, alpha=0.6 * 1.0, light_dir=(-1, 0))
    c.particles(220, 0, 0.45, 0.1, 0.9, "#ffb060", 0.5, 1.8, 5, 0.9, seed=18)  # embers
    c.particles(220, 0.55, 1, 0.1, 0.9, "#e8f8ff", 0.5, 1.8, 3, 0.7, seed=19)  # snow
    c.land(0.7, 0.14, "#2a1a1e", seed=11, haze="#6a4a5a", haze_amt=0.3)
    c.crystal(0.5, 0.25, 0.12, 0.42, "#bfe8ff", glow=80, glow_s=1.0)
    mtn = c.below(c.peak(0.5, 0.38, 0.8, 0.32, seed=4))
    c.paint(mtn, hexc("#1e1418")[None, None, :] * (0.7 + c.fbm(20, 4, 4)[..., None] * 0.6))
    c.paint(mtn * (1 - smoothstep(0.38, 0.5, c.Y)) * smoothstep(0.5, 0.56, c.X), "#8fc8e8", 0.35)
    c.paint(mtn * (1 - smoothstep(0.38, 0.5, c.Y)) * (1 - smoothstep(0.44, 0.5, c.X)), "#ff8a40", 0.35)
    c.land(0.86, 0.06, "#0e0a0c", seed=12)
    finish(c, "#0a0608", "#fff0e0", amount=0.08, vignette=0.5)


# Final Fantasy VII Remake: under the plate. Slum lights, a support pillar, green reactor glow.
def ff7r(c):
    c.vgrad([(0, "#05070a"), (0.3, "#0c1410"), (0.6, "#1f3a2a"), (1, "#0a0f0c")])
    # The plate overhead.
    plate = c.poly([(0, 0), (1, 0), (1, 0.22), (0.7, 0.26), (0.3, 0.25), (0, 0.2)])
    c.paint(plate, hexc("#141818")[None, None, :] * (0.7 + c.fbm(40, 3, 6)[..., None] * 0.5))
    under = c.line([(0, 0.2), (0.3, 0.25), (0.7, 0.26), (1, 0.22)], 2)
    c.glow(under, "#8affb0", 6, 0.4)
    for x in np.linspace(0.05, 0.95, 18):
        c.glow(c.circle(x, 0.235 + 0.02 * math.sin(x * 6), 0.003), "#fff0c0", 10, 0.8)
    # Reactor glow on the right.
    c.radial(0.85, 0.45, 0.5, "#4dff8a", 0.6, 2)
    c.radial(0.85, 0.45, 0.15, "#cfffe0", 0.5, 2)
    # Support pillar.
    pillar = c.poly([(0.36, 0.22), (0.46, 0.22), (0.5, 1), (0.32, 1)])
    c.paint(pillar, "#0e1210")
    for y in np.linspace(0.28, 0.9, 10):
        c.paint(c.line([(0.34, y), (0.48, y + 0.05)], 1.5), "#1e2a24")
    # Slums: layered shacks with warm windows.
    for base, col, dens, seed in [(0.76, "#1a2420", 0.12, 1), (0.86, "#111814", 0.18, 2), (0.98, "#0a0e0c", 0.22, 3)]:
        c.skyline(base, col, seed=seed, hmin=0.04, hmax=0.12, wmin=0.03, wmax=0.07, lights="#ffb055",
                  light_density=dens, spires=0.05)
    c.fog(0.6, 0.85, "#3a6a4a", 0.35, seed=4)
    c.particles(200, 0, 1, 0.25, 0.8, "#c8ffd8", 0.4, 1.3, 4, 0.6, seed=20)
    finish(c, "#040806", "#e0ffe8", amount=0.1, vignette=0.5)


# Final Fantasy VII Rebirth: open grassland at golden hour, white and yellow flowers, the green
# current of life ribboning across the sky.
def ff7rb(c):
    c.vgrad([(0, "#0e2a3a"), (0.4, "#3a7a8a"), (0.6, "#e8c88a"), (0.66, "#ffe8b0"), (1, "#2a3a1a")])
    c.radial(0.3, 0.62, 0.4, "#fff2c8", 0.8, 2)
    c.clouds(0.1, 0.5, "#f4e4c8", lit="#ffffff", cover=0.4, scale=2.2, seed=21, alpha=0.6, stretch=4)
    for k, col in enumerate(["#5dff9a", "#9affd8", "#3fe08a"]):
        c.ribbon(lambda t, k=k: (t, 0.3 + 0.06 * k - 0.15 * math.sin(t * math.pi * (1.2 + 0.2 * k)) + 0.03 * math.sin(t * 12 + k)),
                 col, 2.5 - k * 0.5, 30, 0.7, core=0.5)
    c.particles(120, 0, 1, 0.1, 0.5, "#c8ffe0", 0.4, 1.8, 5, 0.8, seed=21)
    c.land(0.64, 0.07, "#5a7a6a", seed=13, haze="#ffe8b0", haze_amt=0.55)  # distant canyon rim
    c.land(0.68, 0.03, "#5a8a4a", seed=14, haze="#ffe8b0", haze_amt=0.3)
    g = c.land(0.74, 0.03, "#4a7a32", seed=15, texture=0.5)
    c.streaks(900, "#d8f0a0", angle=-85, length=(0.01, 0.03), alpha=0.25, seed=21)
    rng = c.rng
    for i in range(240):
        y = rng.uniform(0.74, 1.02)
        depth = (y - 0.74) / 0.28
        r = 0.002 + depth * 0.009
        x = rng.uniform(0, 1)
        col = "#fff8e8" if rng.random() < 0.6 else "#ffd84a"
        c.paint(c.circle(x, y, r), col)
        c.paint(c.circle(x, y, r * 0.35), "#e8b830" if col == "#fff8e8" else "#ffffff", 0.9)
    finish(c, "#0a1e2a", "#fff4d8", amount=0.08, vignette=0.38)


# Crisis Core: Reunion: storm breaking at sunset over a cliff, white feathers on the wind.
def ccr(c):
    c.vgrad([(0, "#0c1018"), (0.35, "#2a3446"), (0.58, "#c9945a"), (0.64, "#ffe0a0"), (1, "#141618")])
    c.clouds(0.0, 0.5, "#1c2230", lit="#ffd9a0", cover=0.6, scale=2.0, seed=22, alpha=0.85, light_dir=(0, 1))
    c.radial(0.62, 0.6, 0.35, "#fff0c8", 0.9, 2.2)
    rays = c.circle(0.62, 0.6, 0.05) + (c.Y < 0.5) * smoothstep(0.55, 0.7, c.fbm(4, 4, 3)) * 0.0
    c.god_rays(0.62, 0.58, c.circle(0.62, 0.58, 0.06), "#ffe0a0", 0.6, reach=0.8)
    c.streaks(500, "#c8d0e0", angle=100, length=(0.03, 0.07), alpha=0.18, seed=22)  # rain
    c.land(0.66, 0.03, "#3a3a44", seed=16, haze="#ffe0a0", haze_amt=0.3)
    c.land(0.71, 0.02, "#26262c", seed=26, haze="#c9945a", haze_amt=0.15)
    # The city far off on the horizon.
    c.skyline(0.66, "#3a3a44", seed=22, x0=0.7, x1=0.95, hmin=0.01, hmax=0.04, wmin=0.006, wmax=0.014, spires=0.3)
    cliff = c.poly(c.ridge(0.7, 0.02, 27, x0=0.0, x1=0.48)[::4] + [(0.52, 0.74), (0.55, 0.85), (0.56, 1), (0, 1)])
    c.paint(cliff, hexc("#151618")[None, None, :] * (0.7 + c.fbm(30, 4, 8)[..., None] * 0.5))
    c.haze_below(0.8, "#0c0e12", 0.6)
    rng = c.rng
    for _ in range(18):
        c.feather(rng.uniform(0.05, 0.95), rng.uniform(0.05, 0.9), rng.uniform(0.02, 0.045), rng.uniform(0, 360),
                  "#ffffff", 0.9)
    finish(c, "#0a0e16", "#fff0d0", amount=0.1, vignette=0.45)


# Final Fantasy Tactics: parchment and sepia. A cathedral town under a wheel of twelve stars.
def fft(c):
    c.vgrad([(0, "#2a1e14"), (0.4, "#6a4a2e"), (0.64, "#d8b07a"), (0.7, "#efd8a8"), (1, "#3a2a1a")])
    paper = c.fbm(20, 5, 23)
    c.img = c.img * (0.85 + paper[..., None] * 0.3)
    cx, cy, r = 0.5, 0.26, 0.16
    for k in range(12):
        a = 2 * math.pi * k / 12 - math.pi / 2
        x, y = cx + math.cos(a) * r, cy + math.sin(a) * r * c.w / c.h
        c.glow(c.circle(x, y, 0.006), "#fff0c0", 12, 1.0)
    wheel = c.ellipse(cx, cy, r, r * c.w / c.h) - c.ellipse(cx, cy, r - 0.002, r * c.w / c.h - 0.003)
    c.add(np.clip(wheel, 0, 1), "#ffe8b0", 0.35)
    c.land(0.68, 0.07, "#6a5038", seed=17, haze="#efd8a8", haze_amt=0.45)
    # Cathedral town on a hill.
    hill = c.below([(x, 0.7 + 0.08 * ((x - 0.5) / 0.5) ** 2) for x in np.linspace(0, 1, 60)])
    c.paint(hill, "#3a2a1c")
    t = np.zeros((c.h, c.w), np.float32)
    t = np.maximum(t, c.rect(0.44, 0.56, 0.56, 0.7))
    t = np.maximum(t, c.poly([(0.43, 0.56), (0.5, 0.5), (0.57, 0.56)]))
    for x, h in [(0.46, 0.4), (0.54, 0.4), (0.5, 0.34)]:
        t = np.maximum(t, c.rect(x - 0.01, h + 0.04, x + 0.01, 0.6))
        t = np.maximum(t, c.poly([(x - 0.013, h + 0.045), (x, h), (x + 0.013, h + 0.045)]))
    c.paint(t, "#2a1c12")
    c.skyline(0.72, "#2a1c12", seed=23, x0=0.25, x1=0.75, hmin=0.02, hmax=0.06, wmin=0.012, wmax=0.03,
              lights="#ffcf7a", light_density=0.1, spires=0.3)
    c.glow(c.circle(0.5, 0.62, 0.006), "#ffcf7a", 8, 0.9)
    # Banners.
    for x, col in [(0.3, "#8a1e1e"), (0.7, "#1e3a8a")]:
        c.paint(c.line([(x, 0.86), (x, 0.7)], 2), "#1a120a")
        c.paint(c.poly([(x, 0.7), (x + 0.04, 0.71), (x + 0.03, 0.725), (x + 0.04, 0.74), (x, 0.74)]), col)
    finish(c, "#2a1a0a", "#fff0d0", amount=0.15, vignette=0.45, sat=0.85)


# Chrono Trigger: a lone lamp post on a platform in an endless twilight void, a spiral of time.
def ct(c):
    c.vgrad([(0, "#05081a"), (0.5, "#16204a"), (1, "#05081a")])
    d = c.dist(0.5, 0.4)
    ang = np.arctan2((c.Y - 0.4) * c.h / c.w, c.X - 0.5)
    spiral = (np.sin(ang * 3 + d * 40) * 0.5 + 0.5) * np.exp(-d * 3)
    c.add(spiral, "#3a6aff", 0.35)
    c.add(np.exp(-d * 9), "#8fd8ff", 0.5)
    c.stars(500, 0, 1, "#cfe0ff")
    # The platform.
    top = c.ellipse(0.5, 0.74, 0.2, 0.04)
    side = c.poly([(0.3, 0.74), (0.7, 0.74), (0.66, 0.8), (0.34, 0.8)])
    c.paint(np.maximum(top, side), "#1a1f3a")
    c.paint(top, "#2e3a66")
    c.paint(np.clip(top - c.ellipse(0.5, 0.74, 0.185, 0.034), 0, 1), "#5a6aa8", 0.8)
    pole = c.line([(0.5, 0.74), (0.5, 0.48)], 4)
    c.paint(pole, "#0e1226")
    arm = c.line([(0.5, 0.49), (0.53, 0.47), (0.55, 0.49)], 2)
    c.paint(arm, "#0e1226")
    lamp = c.ellipse(0.55, 0.505, 0.008, 0.014)
    c.glow(lamp, "#ffe8a0", 30, 1.4)
    c.radial(0.55, 0.52, 0.18, "#ffd88a", 0.5, 2)
    c.paint(c.ellipse(0.55, 0.745, 0.06, 0.012, soft=4), "#ffd88a", 0.35)
    # A swirling gate and clock rings.
    gate = c.ellipse(0.22, 0.35, 0.05, 0.08) - c.ellipse(0.22, 0.35, 0.03, 0.055)
    c.glow(np.clip(gate, 0, 1), "#6fb8ff", 18, 1.0)
    c.add(c.ellipse(0.22, 0.35, 0.03, 0.055, soft=6), "#1a3aaa", 0.6)
    for r, w in [(0.32, 1.5), (0.36, 1)]:
        ring = c.ellipse(0.5, 0.4, r, r * c.w / c.h) - c.ellipse(0.5, 0.4, r - 0.0015 * w, r * c.w / c.h - 0.002 * w)
        c.add(np.clip(ring, 0, 1), "#7fa8ff", 0.25)
    for k in range(12):
        a = 2 * math.pi * k / 12
        x, y = 0.5 + math.cos(a) * 0.34, 0.4 + math.sin(a) * 0.34 * c.w / c.h
        c.glow(c.circle(x, y, 0.003), "#bfd8ff", 6, 0.8)
    finish(c, "#040818", "#e8f0ff", amount=0.1, vignette=0.5)


# Chrono Cross: an island sea at sunset; a red flame glows inside a volcano's heart.
def cx(c):
    c.vgrad([(0, "#0c1a3a"), (0.35, "#2a4a8a"), (0.58, "#f08a6a"), (0.64, "#ffd8a0"), (1, "#0a1a2a")])
    c.radial(0.36, 0.64, 0.4, "#fff0c8", 0.7, 2.2)
    c.clouds(0.1, 0.56, "#3a3a7a", lit="#ffb08a", cover=0.38, scale=2.4, seed=24, alpha=0.6, stretch=4, light_dir=(-1, 1))
    c.disc_sun = c.circle(0.36, 0.63, 0.04)
    c.glow(c.disc_sun, "#ffe8c0", 30, 1.0)
    vol = c.below(c.peak(0.7, 0.41, 0.66, 0.17, seed=5, jag=0.05))
    vol = vol * (c.Y < 0.66)
    c.paint(vol, hexc("#1c1a30")[None, None, :] * (0.7 + c.fbm(20, 4, 2)[..., None] * 0.5))
    c.glow(c.circle(0.7, 0.4, 0.012), "#ff3a2a", 30, 1.2)
    c.add(c.poly([(0.69, 0.4), (0.71, 0.4), (0.715, 0.45), (0.685, 0.45)], soft=3), "#ff6a3a", 0.8)
    c.land(0.65, 0.015, "#1c2440", seed=3, haze="#ffd8a0", haze_amt=0.3)
    c.water(0.655, "#0e2a4a", dim=0.8, tint_amt=0.3, sparkle="#ffe0c0", sparkle_amt=0.5)
    # Islet with palms, left.
    islet = c.ellipse(0.12, 0.68, 0.12, 0.025)
    c.paint(islet, "#0c1020")
    for bx in (0.09, 0.14):
        trunk = c.line([(bx + 0.02 * t ** 1.5, 0.67 - 0.2 * t) for t in np.linspace(0, 1, 20)], 4)
        c.paint(trunk, "#0c1020")
        tx, ty = bx + 0.02, 0.47
        for k in range(7):
            ang = math.radians(-160 + k * 45)
            fr = c.line([(tx + math.cos(ang) * 0.07 * t, ty + math.sin(ang) * 0.05 * t + 0.04 * t * t) for t in np.linspace(0, 1, 16)], 4)
            c.paint(fr, "#0c1020")
    finish(c, "#08142a", "#fff0e0", amount=0.08, vignette=0.42)


# Lost Odyssey: gold and slate. A star falls on a vast battlefield plain at dusk.
def lo(c):
    c.vgrad([(0, "#141820"), (0.4, "#3a3e44"), (0.62, "#b08a4a"), (0.7, "#f0d08a"), (1, "#1a1814")])
    c.clouds(0.05, 0.6, "#2a2a30", lit="#ffd88a", cover=0.5, scale=2.2, seed=25, alpha=0.8, light_dir=(0.5, 1))
    c.ribbon(lambda t: (0.85 - 0.45 * t, 0.05 + 0.55 * t ** 1.2), "#ffe8b0", 8, 40, 0.9, core=0.6)
    c.glow(c.circle(0.4, 0.6, 0.02), "#fff4d0", 50, 1.4)
    c.radial(0.4, 0.62, 0.3, "#ffd88a", 0.6, 2)
    c.land(0.66, 0.05, "#4a4438", seed=18, haze="#f0d08a", haze_amt=0.5)
    c.land(0.72, 0.03, "#2e2a24", seed=19, haze="#f0d08a", haze_amt=0.2)
    # Distant mechanical spires and banners on the plain.
    for x in np.linspace(0.1, 0.9, 14):
        h = 0.03 + 0.03 * c.rng.random()
        c.paint(c.line([(x, 0.73), (x, 0.73 - h)], 1.5), "#1a1814")
        c.paint(c.poly([(x, 0.73 - h), (x + 0.015, 0.73 - h + 0.004), (x, 0.73 - h + 0.01)]), "#1a1814")
    tower = c.poly([(0.78, 0.72), (0.79, 0.4), (0.8, 0.36), (0.81, 0.4), (0.82, 0.72)])
    c.paint(tower, "#1e1c1a")
    for y in (0.45, 0.52, 0.6):
        c.paint(c.ellipse(0.8, y, 0.03, 0.006), "#1e1c1a")
    c.particles(200, 0.1, 0.8, 0.3, 0.75, "#ffe0a0", 0.4, 1.4, 4, 0.7, seed=25)
    c.land(0.86, 0.04, "#0e0d0c", seed=20)
    finish(c, "#0c0e12", "#fff0d0", amount=0.1, vignette=0.48)


# Lightning Returns: rose and plum twilight; a vast clock face over a gothic city, chaos rising.
def lr(c):
    c.vgrad([(0, "#12061a"), (0.38, "#3d1442"), (0.6, "#a8467a"), (0.68, "#f3a6c8"), (1, "#0e0814")])
    c.stars(300, 0, 0.45, "#ffe6f4", fade=1 - smoothstep(0.15, 0.45, c.Y))
    c.clouds(0.1, 0.62, "#2a0f33", lit="#ffb3d6", cover=0.45, scale=2.3, seed=31, alpha=0.7, light_dir=(0, 1))
    # The clock face: rings, hour ticks and two hands, faint and luminous.
    cx, cy, r = 0.5, 0.36, 0.2
    asp = c.w / c.h
    for rr, w, a_ in [(r, 2.5, 0.9), (r * 0.93, 1.2, 0.5), (r * 0.6, 1.0, 0.35)]:
        ring = c.ellipse(cx, cy, rr, rr * asp) - c.ellipse(cx, cy, rr - 0.002 * w, rr * asp - 0.003 * w)
        c.glow(np.clip(ring, 0, 1), "#ffd6ec", 8, a_)
    for k in range(12):
        ang = 2 * math.pi * k / 12
        x0, y0 = cx + math.cos(ang) * r * 0.82, cy + math.sin(ang) * r * 0.82 * asp
        x1, y1 = cx + math.cos(ang) * r * 0.9, cy + math.sin(ang) * r * 0.9 * asp
        c.glow(c.line([(x0, y0), (x1, y1)], 3 if k % 3 == 0 else 1.5), "#fff0f7", 5, 0.8)
    for ang, L, w in [(-math.pi / 2 + 0.35, 0.75, 3), (-math.pi / 2 - 1.3, 0.5, 4)]:
        c.glow(c.line([(cx, cy), (cx + math.cos(ang) * r * L, cy + math.sin(ang) * r * L * asp)], w), "#fff0f7", 6, 0.9)
    c.glow(c.circle(cx, cy, 0.006), "#ffffff", 10, 1.0)
    # Chaos: green-teal mist rising from below.
    for k in range(3):
        c.ribbon(lambda t, k=k: (0.1 + 0.8 * t, 0.72 - 0.12 * math.sin(t * math.pi * (1.3 + k * 0.3)) - 0.02 * k),
                 "#5ef2c8", 2.0 - k * 0.5, 28, 0.45, core=0.3)
    c.land(0.7, 0.05, "#3a1840", seed=31, haze="#f3a6c8", haze_amt=0.4)
    # Gothic city: spires and a cathedral at the centre.
    c.skyline(0.82, "#12081a", seed=32, hmin=0.04, hmax=0.14, wmin=0.012, wmax=0.03, lights="#ffcfe6",
              light_density=0.12, spires=0.7)
    cat = c.rect(0.44, 0.62, 0.56, 0.84)
    for x, h in [(0.46, 0.5), (0.54, 0.5), (0.5, 0.44)]:
        cat = np.maximum(cat, c.rect(x - 0.012, h + 0.05, x + 0.012, 0.84))
        cat = np.maximum(cat, c.poly([(x - 0.016, h + 0.055), (x, h), (x + 0.016, h + 0.055)]))
    c.paint(cat, "#12081a")
    c.glow(c.ellipse(0.5, 0.68, 0.012, 0.02), "#ffd6ec", 10, 0.9)
    c.fog(0.7, 0.92, "#3a7a6e", 0.3, seed=33)
    c.particles(160, 0.05, 0.95, 0.15, 0.85, "#d9fff3", 0.5, 1.8, 5, 0.8, seed=34)
    c.land(0.9, 0.03, "#0a0610", seed=35)
    finish(c, "#0c0412", "#ffe6f2", amount=0.1, vignette=0.48)


# Clair Obscur: Expedition 33 — gold dusk over the sea; a dark monolith on the horizon, a city
# with a bent iron tower, red petals on the wind. Painterly, Belle Époque palette.
def coe33(c):
    c.vgrad([(0, "#1a1410"), (0.35, "#5a3d26"), (0.58, "#d9a35a"), (0.66, "#f6dfa8"), (1, "#16120e")])
    c.radial(0.62, 0.62, 0.45, "#fff0c8", 0.6, 2.2)
    c.clouds(0.05, 0.58, "#3a2a1e", lit="#ffd79a", cover=0.5, scale=2.2, seed=41, alpha=0.75, light_dir=(0.2, 1))
    # The monolith: a tall, slightly tapering dark spire far across the water, faint glowing marks.
    mono = c.poly([(0.7, 0.66), (0.715, 0.12), (0.735, 0.1), (0.75, 0.13), (0.765, 0.66)])
    c.paint(mono, hexc("#17110d")[None, None, :] * (0.8 + c.fbm(30, 3, 4)[..., None] * 0.4))
    c.add(c.blur(mono, 30), "#ffcf8a", 0.15)
    marks = np.zeros_like(mono)
    for k in range(5):
        y = 0.22 + k * 0.07
        marks = np.maximum(marks, c.line([(0.722, y), (0.745, y + 0.012)], 1.2))
    c.glow(marks * mono, "#ffb36a", 6, 0.6)
    c.land(0.665, 0.012, "#2a2018", seed=42, haze="#f6dfa8", haze_amt=0.35)
    c.water(0.67, "#1e1712", dim=0.75, tint_amt=0.3, sparkle="#ffe2b0", sparkle_amt=0.45)
    # The city on the left shore, with its tall iron tower bent over at the top.
    shore = c.poly([(0, 0.7), (0.12, 0.68), (0.36, 0.69), (0.44, 0.73), (0.46, 1), (0, 1)])
    c.paint(shore, "#120d0a")
    c.skyline(0.7, "#120d0a", seed=43, x0=0.0, x1=0.4, hmin=0.02, hmax=0.08, wmin=0.01, wmax=0.026,
              lights="#ffd18a", light_density=0.14, spires=0.4)
    tower = c.poly([(0.17, 0.7), (0.2, 0.42), (0.21, 0.42), (0.24, 0.7)])
    tower = np.maximum(tower, c.line([(0.205, 0.42), (0.21, 0.32), (0.23, 0.26), (0.26, 0.24)], 3))
    tower = np.maximum(tower, c.rect(0.18, 0.6, 0.23, 0.61))
    tower = np.maximum(tower, c.rect(0.19, 0.5, 0.22, 0.508))
    c.paint(tower, "#120d0a")
    # Red petals drifting across.
    rng = c.rng
    for _ in range(70):
        x, y = rng.uniform(0, 1), rng.uniform(0.05, 0.95)
        rx = 0.003 + rng.random() * 0.006
        petal = c.ellipse(x, y, rx, rx * 0.55 * c.w / c.h)
        c.paint(petal, mix("#a5121e", "#e8463c", rng.random()), 0.9)
    c.particles(120, 0, 1, 0.1, 0.8, "#ffe6b8", 0.4, 1.4, 4, 0.6, seed=44)
    finish(c, "#120c08", "#fff0d0", amount=0.12, vignette=0.5, sat=0.95)


SCENES = {
    "ff1": ff1, "ff2": ff2, "ff3": ff3, "ff4": ff4, "ff5": ff5, "ff6": ff6, "ff7": ff7, "ff8": ff8,
    "ff9": ff9, "ff10": ff10, "ff10_2": ff10_2, "ff11": ff11, "ff12": ff12, "ff13": ff13,
    "ff13_2": ff13_2, "ff14": ff14, "ff15": ff15, "ff16": ff16, "ff7r": ff7r, "ff7rb": ff7rb,
    "ccr": ccr, "fft": fft, "ct": ct, "cx": cx, "lo": lo, "lr": lr, "coe33": coe33,
}

if __name__ == "__main__":
    out = os.environ.get("ART_OUT", OUT)
    ids = sys.argv[1:] or list(SCENES)
    for i, key in enumerate(ids):
        c = Canvas(seed=1000 + list(SCENES).index(key))
        SCENES[key](c)
        c.save(os.path.join(out, f"art_{key}.webp"))
        print("painted", key, flush=True)
