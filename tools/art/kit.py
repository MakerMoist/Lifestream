"""A tiny painting kit for Lifestream's card art.

Everything is drawn procedurally (gradients, silhouettes, glows, particles) so the art is
original. Scenes render at 2x and are downsampled for smooth edges.
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 540, 600          # final card size (aspect 0.9, matches the card)
S = 2                    # supersampling factor


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], dtype=np.float32)


class Scene:
    def __init__(self, seed=1):
        self.w, self.h = W * S, H * S
        self.img = np.zeros((self.h, self.w, 3), dtype=np.float32)
        self.rng = random.Random(seed)
        self.np_rng = np.random.default_rng(seed)

    # ---------- helpers ----------
    def px(self, x):
        """Fraction of width -> pixels."""
        return x * self.w

    def py(self, y):
        return y * self.h

    def mask(self):
        return Image.new("L", (self.w, self.h), 0)

    @staticmethod
    def arr(m):
        return np.asarray(m, dtype=np.float32) / 255.0

    def blur(self, m, r):
        if isinstance(m, np.ndarray):
            m = Image.fromarray(np.clip(m * 255, 0, 255).astype(np.uint8))
        return self.arr(m.filter(ImageFilter.GaussianBlur(r * S)))

    def paint(self, m, color, alpha=1.0):
        c = hexc(color) if isinstance(color, str) else color
        a = (m * alpha)[..., None]
        self.img = self.img * (1 - a) + c * a

    def add(self, m, color, strength=1.0):
        c = hexc(color) if isinstance(color, str) else color
        self.img = self.img + (m * strength)[..., None] * c

    def glow(self, m, color, radius, strength=1.0, core=True):
        """Additive bloom of a mask, optionally with the crisp shape painted too."""
        self.add(self.blur(m, radius), color, strength)
        if core:
            self.add(m, color, strength * 0.6)

    # ---------- sky ----------
    def vgrad(self, stops):
        """stops: [(y_fraction, '#hex'), ...] top to bottom."""
        ys = np.linspace(0, 1, self.h)
        pos = [p for p, _ in stops]
        cols = np.stack([hexc(c) for _, c in stops])
        out = np.stack([np.interp(ys, pos, cols[:, i]) for i in range(3)], axis=1)
        self.img[:] = out[:, None, :]

    def radial(self, cx, cy, r, color, strength=1.0, power=2.0):
        yy, xx = np.mgrid[0:self.h, 0:self.w].astype(np.float32)
        d = np.sqrt((xx - self.px(cx)) ** 2 + (yy - self.py(cy)) ** 2) / self.px(r)
        m = np.clip(1 - d, 0, 1) ** power
        self.add(m, color, strength)

    def stars(self, n, y0=0.0, y1=0.6, color="#ffffff", big=0.04):
        m = self.mask()
        d = ImageDraw.Draw(m)
        bigm = self.mask()
        db = ImageDraw.Draw(bigm)
        for _ in range(n):
            x = self.rng.uniform(0, self.w)
            y = self.rng.uniform(self.py(y0), self.py(y1))
            r = self.rng.uniform(0.6, 1.8) * S
            b = int(self.rng.uniform(90, 255))
            d.ellipse([x - r, y - r, x + r, y + r], fill=b)
            if self.rng.random() < big:
                db.ellipse([x - r * 2, y - r * 2, x + r * 2, y + r * 2], fill=255)
        self.add(self.arr(m), color, 1.0)
        self.glow(self.arr(bigm), color, 6, 0.9)

    def disc(self, cx, cy, r, color, glow_color=None, glow_r=60, glow_s=0.8, alpha=1.0):
        m = self.mask()
        R = self.px(r)
        ImageDraw.Draw(m).ellipse([self.px(cx) - R, self.py(cy) - R, self.px(cx) + R, self.py(cy) + R], fill=255)
        a = self.arr(m)
        if glow_color:
            self.add(self.blur(a, glow_r), glow_color, glow_s)
        self.paint(a, color, alpha)
        return a

    def clouds(self, n, y0, y1, color, alpha=0.35, size=(0.15, 0.4), blur=18, seed=None):
        rng = random.Random(seed) if seed is not None else self.rng
        m = self.mask()
        d = ImageDraw.Draw(m)
        for _ in range(n):
            cx = rng.uniform(-0.1, 1.1) * self.w
            cy = rng.uniform(y0, y1) * self.h
            rw = rng.uniform(*size) * self.w
            rh = rw * rng.uniform(0.18, 0.35)
            d.ellipse([cx - rw, cy - rh, cx + rw, cy + rh], fill=int(rng.uniform(120, 255)))
        self.paint(self.blur(m, blur), color, alpha)

    # ---------- land ----------
    def ridge_heights(self, base, amp, rough=0.55, seed=0, detail=9):
        rng = random.Random(seed)
        n = 2 ** detail + 1
        hts = [0.0] * n
        hts[0], hts[-1] = rng.uniform(-1, 1), rng.uniform(-1, 1)
        step, scale = n - 1, 1.0
        while step > 1:
            half = step // 2
            for i in range(half, n - 1, step):
                hts[i] = (hts[i - half] + hts[i + half]) / 2 + rng.uniform(-1, 1) * scale
            step, scale = half, scale * rough
        xs = np.linspace(0, 1, n)
        return [(x, base - h * amp) for x, h in zip(xs, hts)]

    def fill_below(self, pts, color, alpha=1.0, shade=None):
        m = self.mask()
        poly = [(self.px(x), self.py(y)) for x, y in pts] + [(self.w, self.h), (0, self.h)]
        ImageDraw.Draw(m).polygon(poly, fill=255)
        a = self.arr(m)
        self.paint(a, color, alpha)
        return a

    def mountains(self, base, amp, color, rough=0.55, seed=0, alpha=1.0, detail=9):
        return self.fill_below(self.ridge_heights(base, amp, rough, seed, detail), color, alpha)

    def water(self, horizon, tint="#0b1530", dim=0.55, ripple=0.004, tint_amt=0.35):
        hy = int(self.py(horizon))
        rows = self.h - hy
        src = self.img[max(0, hy - rows):hy][::-1].copy()
        if src.shape[0] < rows:
            pad = np.repeat(src[-1:], rows - src.shape[0], axis=0)
            src = np.concatenate([src, pad])
        out = np.empty_like(src)
        for i in range(rows):
            off = int(math.sin(i * 0.35) * ripple * self.w * (0.3 + i / rows))
            out[i] = np.roll(src[i], off, axis=0)
        fade = np.linspace(1.0, 0.55, rows)[:, None, None]
        refl = out * dim * fade
        refl = refl * (1 - tint_amt) + hexc(tint) * tint_amt
        self.img[hy:] = refl
        # bright shimmer lines
        m = self.mask()
        d = ImageDraw.Draw(m)
        for _ in range(140):
            y = self.rng.uniform(hy, self.h)
            x = self.rng.uniform(0, self.w)
            L = self.rng.uniform(10, 60) * S
            d.line([x, y, x + L, y], fill=int(self.rng.uniform(30, 90)), width=S)
        self.add(self.arr(m), "#ffffff", 0.35)

    def skyline(self, base, color, seed=0, x0=0.0, x1=1.0, hmin=0.05, hmax=0.22, wmin=0.025, wmax=0.07,
                windows=None, win_density=0.25, spires=0.2):
        rng = random.Random(seed)
        m = self.mask()
        d = ImageDraw.Draw(m)
        win = self.mask()
        dw = ImageDraw.Draw(win)
        x = x0
        while x < x1:
            bw = rng.uniform(wmin, wmax)
            bh = rng.uniform(hmin, hmax)
            X0, X1 = self.px(x), self.px(x + bw)
            Y0, Y1 = self.py(base - bh), self.py(base) + 4
            d.rectangle([X0, Y0, X1, Y1], fill=255)
            if rng.random() < spires:
                cx = (X0 + X1) / 2
                d.polygon([(cx - self.px(bw) * 0.15, Y0), (cx, Y0 - self.py(rng.uniform(0.03, 0.08))),
                           (cx + self.px(bw) * 0.15, Y0)], fill=255)
            if windows:
                yy = Y0 + 6 * S
                while yy < Y1 - 6 * S:
                    xx = X0 + 4 * S
                    while xx < X1 - 4 * S:
                        if rng.random() < win_density:
                            dw.rectangle([xx, yy, xx + 2 * S, yy + 3 * S], fill=255)
                        xx += 6 * S
                    yy += 8 * S
            x += bw * rng.uniform(0.85, 1.05)
        a = self.arr(m)
        self.paint(a, color)
        if windows:
            wa = self.arr(win) * a
            self.glow(wa, windows, 3, 0.9)
        return a

    def castle(self, cx, base, scale, color, windows=None):
        m = self.mask()
        d = ImageDraw.Draw(m)
        s = self.px(scale)
        X, B = self.px(cx), self.py(base)
        # keep
        d.rectangle([X - s * 0.35, B - s * 0.7, X + s * 0.35, B], fill=255)
        towers = [(-0.55, 0.95, 0.12), (0.55, 0.95, 0.12), (-0.18, 1.25, 0.1), (0.2, 1.1, 0.09), (0.0, 1.5, 0.08)]
        for ox, th, tw in towers:
            tx = X + ox * s
            d.rectangle([tx - tw * s, B - th * s, tx + tw * s, B], fill=255)
            d.polygon([(tx - tw * s * 1.3, B - th * s), (tx, B - th * s - s * 0.35), (tx + tw * s * 1.3, B - th * s)], fill=255)
        # walls
        d.rectangle([X - s * 0.9, B - s * 0.35, X + s * 0.9, B], fill=255)
        for i in range(-9, 10, 2):
            d.rectangle([X + i * s * 0.1 - s * 0.03, B - s * 0.42, X + i * s * 0.1 + s * 0.03, B - s * 0.35], fill=255)
        a = self.arr(m)
        self.paint(a, color)
        if windows:
            w = self.mask()
            dw = ImageDraw.Draw(w)
            for _ in range(18):
                wx = X + self.rng.uniform(-0.6, 0.6) * s
                wy = B - self.rng.uniform(0.15, 1.1) * s
                dw.rectangle([wx, wy, wx + 2 * S, wy + 4 * S], fill=255)
            self.glow(self.arr(w) * a, windows, 4, 1.0)
        return a

    def trees(self, base, color, seed=0, n=60, hmin=0.04, hmax=0.12, x0=0.0, x1=1.0, ground=True):
        rng = random.Random(seed)
        m = self.mask()
        d = ImageDraw.Draw(m)
        for _ in range(n):
            x = self.px(rng.uniform(x0, x1))
            h = self.py(rng.uniform(hmin, hmax))
            w = h * rng.uniform(0.25, 0.4)
            B = self.py(base) + rng.uniform(0, 8) * S
            d.polygon([(x - w, B), (x, B - h), (x + w, B)], fill=255)
        if ground:
            d.rectangle([0, self.py(base) + 4 * S, self.w, self.h], fill=255)
        self.paint(self.arr(m), color)

    # ---------- magic & light ----------
    def crystal(self, cx, cy, w, h, color, edge="#ffffff", glow_r=40, glow_s=0.9, tilt=0.0):
        X, Y, Wd, Ht = self.px(cx), self.py(cy), self.px(w), self.py(h)
        t = tilt * Wd
        top, bot = (X + t, Y - Ht / 2), (X - t, Y + Ht / 2)
        left, right = (X - Wd / 2, Y - Ht * 0.08), (X + Wd / 2, Y - Ht * 0.08)
        L = self.mask()
        ImageDraw.Draw(L).polygon([top, left, bot], fill=255)
        R = self.mask()
        ImageDraw.Draw(R).polygon([top, right, bot], fill=255)
        whole = np.clip(self.arr(L) + self.arr(R), 0, 1)
        c = hexc(color)
        self.add(self.blur(whole, glow_r), c, glow_s)
        self.paint(self.arr(L), c * 0.75)
        self.paint(self.arr(R), np.clip(c * 1.15 + 0.1, 0, 1))
        hl = self.mask()
        ImageDraw.Draw(hl).polygon([top, (X + Wd * 0.12, Y - Ht * 0.12), (X, Y - Ht * 0.05)], fill=255)
        self.paint(self.arr(hl), hexc(edge), 0.6)
        return whole

    def particles(self, n, x0, x1, y0, y1, color, rmin=1.0, rmax=3.5, glow_r=6, strength=1.0, seed=None):
        rng = random.Random(seed) if seed is not None else self.rng
        m = self.mask()
        d = ImageDraw.Draw(m)
        for _ in range(n):
            x, y = self.px(rng.uniform(x0, x1)), self.py(rng.uniform(y0, y1))
            r = rng.uniform(rmin, rmax) * S
            d.ellipse([x - r, y - r, x + r, y + r], fill=int(rng.uniform(140, 255)))
        self.glow(self.arr(m), color, glow_r, strength)

    def ribbon(self, fn, color, width=6, glow_r=20, strength=1.0, steps=200):
        m = self.mask()
        d = ImageDraw.Draw(m)
        pts = [fn(t / steps) for t in range(steps + 1)]
        pts = [(self.px(x), self.py(y)) for x, y in pts]
        d.line(pts, fill=255, width=int(width * S), joint="curve")
        self.glow(self.arr(m), color, glow_r, strength)

    def meteor(self, cx, cy, r, angle_deg, trail, color, core="#fff4d6"):
        ang = math.radians(angle_deg)
        X, Y, R = self.px(cx), self.py(cy), self.px(r)
        tail = self.mask()
        d = ImageDraw.Draw(tail)
        tx, ty = X - math.cos(ang) * self.px(trail), Y - math.sin(ang) * self.px(trail)
        nx, ny = -math.sin(ang), math.cos(ang)
        d.polygon([(X + nx * R, Y + ny * R), (tx, ty), (X - nx * R, Y - ny * R)], fill=200)
        self.glow(self.blur(tail, 6), color, 30, 1.0)
        self.disc(cx, cy, r, color, glow_color=color, glow_r=50, glow_s=1.2)
        self.disc(cx, cy, r * 0.6, core, glow_color=core, glow_r=12, glow_s=0.8)

    def beams(self, ox, oy, angles, length, spread, color, strength=0.35, blur=10):
        m = self.mask()
        d = ImageDraw.Draw(m)
        X, Y = self.px(ox), self.py(oy)
        for a in angles:
            r = math.radians(a)
            L = self.px(length)
            p1 = (X + math.cos(r - spread) * L, Y + math.sin(r - spread) * L)
            p2 = (X + math.cos(r + spread) * L, Y + math.sin(r + spread) * L)
            d.polygon([(X, Y), p1, p2], fill=255)
        self.add(self.blur(m, blur), color, strength)

    def snow(self, n, color="#ffffff", seed=None):
        self.particles(n, 0, 1, 0, 1, color, 0.6, 2.2, glow_r=2, strength=0.7, seed=seed)

    def airship(self, cx, cy, scale, color):
        m = self.mask()
        d = ImageDraw.Draw(m)
        X, Y, s = self.px(cx), self.py(cy), self.px(scale)
        d.ellipse([X - s, Y - s * 0.28, X + s, Y + s * 0.28], fill=255)
        d.polygon([(X - s * 0.9, Y), (X - s * 1.35, Y - s * 0.35), (X - s * 1.25, Y), (X - s * 1.35, Y + s * 0.3)], fill=255)
        d.rectangle([X - s * 0.35, Y + s * 0.3, X + s * 0.35, Y + s * 0.45], fill=255)
        d.line([(X - s * 0.3, Y + s * 0.2), (X - s * 0.3, Y + s * 0.32)], fill=255, width=S * 2)
        d.line([(X + s * 0.3, Y + s * 0.2), (X + s * 0.3, Y + s * 0.32)], fill=255, width=S * 2)
        self.paint(self.arr(m), color)

    def feathers(self, n, color, seed=None):
        rng = random.Random(seed) if seed is not None else self.rng
        layer = Image.new("L", (self.w, self.h), 0)
        for _ in range(n):
            fw, fh = int(rng.uniform(7, 14) * S), int(rng.uniform(30, 56) * S)
            f = Image.new("L", (fw * 2, fh * 2), 0)
            fd = ImageDraw.Draw(f)
            fd.ellipse([fw * 0.5, fh * 0.2, fw * 1.5, fh * 1.8], fill=int(rng.uniform(170, 255)))
            fd.line([(fw, fh * 0.1), (fw, fh * 1.95)], fill=90, width=S)
            f = f.rotate(rng.uniform(0, 360), expand=True, resample=Image.BICUBIC)
            layer.paste(f, (int(rng.uniform(0, self.w)), int(rng.uniform(0, self.h))), f)
        a = self.arr(layer)
        self.glow(a, color, 8, 0.35, core=False)
        self.paint(a, color, 0.9)

    def ring(self, cx, cy, r, width, color, glow_r=10, strength=1.0):
        m = self.mask()
        R = self.px(r)
        ImageDraw.Draw(m).ellipse([self.px(cx) - R, self.py(cy) - R, self.px(cx) + R, self.py(cy) + R],
                                  outline=255, width=int(width * S))
        a = self.arr(m)
        self.glow(a, color, glow_r, strength)
        return a

    def gear(self, cx, cy, r, teeth, color, alpha=1.0, hole=0.35):
        m = self.mask()
        d = ImageDraw.Draw(m)
        X, Y, R = self.px(cx), self.py(cy), self.px(r)
        pts = []
        for i in range(teeth * 2):
            a = math.pi * i / teeth
            rr = R if i % 2 == 0 else R * 0.84
            for k in (-0.35, 0.35):
                aa = a + k * math.pi / teeth
                pts.append((X + math.cos(aa) * rr, Y + math.sin(aa) * rr))
        d.polygon(pts, fill=255)
        d.ellipse([X - R * hole, Y - R * hole, X + R * hole, Y + R * hole], fill=0)
        a = self.arr(m)
        self.paint(a, color, alpha)
        return a

    def flowers(self, y0, y1, n, colors, seed=None):
        rng = random.Random(seed) if seed is not None else self.rng
        for col in colors:
            m = self.mask()
            d = ImageDraw.Draw(m)
            for _ in range(n // len(colors)):
                y = rng.uniform(y0, y1)
                depth = (y - y0) / (y1 - y0)
                r = (1.0 + depth * 4.0) * S
                x = self.px(rng.uniform(0, 1))
                yy = self.py(y)
                d.ellipse([x - r, yy - r * 0.7, x + r, yy + r * 0.7], fill=int(140 + depth * 115))
            a = self.arr(m)
            self.glow(a, col, 3, 0.5, core=False)
            self.paint(a, col, 0.95)

    # ---------- finish ----------
    def vignette(self, strength=0.55):
        yy, xx = np.mgrid[0:self.h, 0:self.w].astype(np.float32)
        d = np.sqrt(((xx / self.w) - 0.5) ** 2 + ((yy / self.h) - 0.45) ** 2) / 0.75
        self.img *= (1 - strength * np.clip(d, 0, 1) ** 2)[..., None]

    def bottom_shade(self, start=0.62, strength=0.75, color="#0A0E1C"):
        ys = np.linspace(0, 1, self.h)
        a = np.clip((ys - start) / (1 - start), 0, 1) ** 1.3 * strength
        self.paint(np.repeat(a[:, None], self.w, axis=1), color)

    def save(self, path, grain=0.018):
        img = self.img
        img = img / (1 + np.maximum(img - 1, 0) * 0.5)  # soft-clip highlights
        img = img + self.np_rng.normal(0, grain, img.shape).astype(np.float32)
        out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
        out = out.resize((W, H), Image.LANCZOS)
        out.save(path, "WEBP", quality=86, method=6)
        return out
