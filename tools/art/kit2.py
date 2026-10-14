"""Painting kit for LifeStream's card art (second generation).

Everything is procedural (noise, gradients, silhouettes, light), so the art is original: each
title's official key art is only a reference for palette, mood and setting. No characters,
creatures, logos or traced artwork.

Cards are wide (1.35:1). Scenes are painted at 2x and downsampled for clean edges.
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1080, 800  # final size
S = 2  # supersampling


def hexc(h):
    if not isinstance(h, str):
        return np.asarray(h, dtype=np.float32)
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], dtype=np.float32)


def mix(a, b, t):
    return hexc(a) * (1 - t) + hexc(b) * t


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


class Canvas:
    def __init__(self, seed=1):
        self.w, self.h = W * S, H * S
        self.img = np.zeros((self.h, self.w, 3), dtype=np.float32)
        self.seed = seed
        self.rng = random.Random(seed)
        self.nrng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:self.h, 0:self.w].astype(np.float32)
        self.X = xs / self.w  # 0..1 across
        self.Y = ys / self.h  # 0..1 down

    # ---------------- coordinates ----------------
    def px(self, x):
        return x * self.w

    def py(self, y):
        return y * self.h

    def pts(self, ps):
        return [(self.px(x), self.py(y)) for x, y in ps]

    # ---------------- masks ----------------
    def new_mask(self):
        return Image.new("L", (self.w, self.h), 0)

    @staticmethod
    def arr(m):
        return np.asarray(m, dtype=np.float32) / 255.0

    def to_img(self, a):
        return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))

    def blur(self, a, r):
        if r <= 0:
            return a
        return self.arr(self.to_img(a).filter(ImageFilter.GaussianBlur(r * S)))

    def poly(self, ps, soft=0.0):
        m = self.new_mask()
        ImageDraw.Draw(m).polygon(self.pts(ps), fill=255)
        a = self.arr(m)
        return self.blur(a, soft) if soft else a

    def ellipse(self, cx, cy, rx, ry, soft=0.0):
        m = self.new_mask()
        ImageDraw.Draw(m).ellipse([self.px(cx - rx), self.py(cy - ry), self.px(cx + rx), self.py(cy + ry)], fill=255)
        a = self.arr(m)
        return self.blur(a, soft) if soft else a

    def circle(self, cx, cy, r, soft=0.0):
        """r is a fraction of the width, so circles stay round."""
        R = self.px(r)
        m = self.new_mask()
        ImageDraw.Draw(m).ellipse([self.px(cx) - R, self.py(cy) - R, self.px(cx) + R, self.py(cy) + R], fill=255)
        a = self.arr(m)
        return self.blur(a, soft) if soft else a

    def line(self, ps, width, soft=0.0):
        m = self.new_mask()
        ImageDraw.Draw(m).line(self.pts(ps), fill=255, width=max(1, int(width * S)), joint="curve")
        a = self.arr(m)
        return self.blur(a, soft) if soft else a

    def rect(self, x0, y0, x1, y1):
        m = self.new_mask()
        ImageDraw.Draw(m).rectangle([self.px(x0), self.py(y0), self.px(x1), self.py(y1)], fill=255)
        return self.arr(m)

    # ---------------- noise ----------------
    def noise(self, scale, seed=0, aspect=1.0):
        """Smooth value noise in 0..1; scale = features across the width."""
        rng = np.random.default_rng(self.seed * 1000 + seed)
        gw = max(2, int(scale) + 2)
        gh = max(2, int(scale * self.h / self.w * aspect) + 2)
        g = rng.random((gh, gw)).astype(np.float32)
        im = Image.fromarray(g, mode="F").resize((self.w, self.h), Image.BICUBIC)
        return np.clip(np.asarray(im), 0, 1)

    def fbm(self, scale, octaves=5, seed=0, gain=0.5, aspect=1.0):
        total, amp, norm = 0.0, 1.0, 0.0
        for o in range(octaves):
            total = total + self.noise(scale * 2 ** o, seed + o * 17, aspect) * amp
            norm += amp
            amp *= gain
        return total / norm

    # ---------------- painting ----------------
    def paint(self, a, color, alpha=1.0):
        c = hexc(color) if isinstance(color, str) else color
        a = np.clip(a * alpha, 0, 1)
        if np.ndim(c) == 3:
            self.img = self.img * (1 - a[..., None]) + c * a[..., None]
        else:
            self.img = self.img * (1 - a[..., None]) + c * a[..., None]

    def add(self, a, color, strength=1.0):
        c = hexc(color) if isinstance(color, str) else color
        self.img = self.img + (a * strength)[..., None] * c

    def glow(self, a, color, radius, strength=1.0, core=0.7):
        self.add(self.blur(a, radius), color, strength)
        if core:
            self.add(a, color, core * strength)

    def screen(self, a, color, strength=1.0):
        c = hexc(color)
        t = (a * strength)[..., None]
        self.img = 1 - (1 - self.img) * (1 - c * t)

    # ---------------- sky ----------------
    def vgrad(self, stops):
        pos = [p for p, _ in stops]
        cols = np.stack([hexc(c) for _, c in stops])
        ys = np.linspace(0, 1, self.h)
        out = np.stack([np.interp(ys, pos, cols[:, i]) for i in range(3)], axis=1).astype(np.float32)
        self.img[:] = out[:, None, :]

    def gradient_field(self, stops, field):
        """Colour by any 0..1 field (e.g. distance from the sun)."""
        pos = [p for p, _ in stops]
        cols = np.stack([hexc(c) for _, c in stops])
        out = np.stack([np.interp(field, pos, cols[:, i]) for i in range(3)], axis=-1).astype(np.float32)
        return out

    def radial(self, cx, cy, r, color, strength=1.0, power=2.0, squash=1.0):
        d = np.sqrt(((self.X - cx)) ** 2 + ((self.Y - cy) * self.h / self.w * squash) ** 2) / r
        self.add(np.clip(1 - d, 0, 1) ** power, color, strength)

    def dist(self, cx, cy):
        """Distance from a point, in widths."""
        return np.sqrt((self.X - cx) ** 2 + ((self.Y - cy) * self.h / self.w) ** 2)

    def clouds(self, y0, y1, color, lit=None, cover=0.5, scale=3.0, alpha=0.9, seed=0, stretch=3.0,
               light_dir=(0.0, -1.0), softness=0.12):
        """Banks of noise clouds between y0..y1, lit from light_dir."""
        n = self.fbm(scale, 6, seed, aspect=stretch)
        band = smoothstep(y0 - 0.05, y0 + 0.08, self.Y) * (1 - smoothstep(y1 - 0.08, y1 + 0.05, self.Y))
        dens = smoothstep(1 - cover, 1 - cover + softness, n) * band
        self.paint(dens, color, alpha)
        if lit:
            # Rim light: where density falls off toward the light.
            dx, dy = light_dir
            shift = np.roll(np.roll(dens, int(dy * 6 * S), axis=0), int(dx * 6 * S), axis=1)
            rim = np.clip(dens - shift, 0, 1)
            self.add(self.blur(rim, 1.5), lit, 1.6 * alpha)
        return dens

    def stars(self, n, y0=0.0, y1=0.7, color="#ffffff", seed=0, big=0.03, fade=None):
        rng = random.Random(self.seed * 77 + seed)
        m = self.new_mask()
        d = ImageDraw.Draw(m)
        bm = self.new_mask()
        db = ImageDraw.Draw(bm)
        for _ in range(n):
            x, y = rng.uniform(0, self.w), rng.uniform(self.py(y0), self.py(y1))
            r = rng.uniform(0.5, 1.6) * S
            d.ellipse([x - r, y - r, x + r, y + r], fill=int(rng.uniform(70, 255)))
            if rng.random() < big:
                db.ellipse([x - r * 1.8, y - r * 1.8, x + r * 1.8, y + r * 1.8], fill=255)
        a = self.arr(m)
        if fade is not None:
            a = a * fade
        self.add(a, color, 1.0)
        self.glow(self.arr(bm), color, 5, 0.8)

    # ---------------- land ----------------
    def ridge(self, base, amp, seed=0, rough=0.55, detail=10, x0=0.0, x1=1.0):
        rng = random.Random(self.seed * 31 + seed)
        n = 2 ** detail + 1
        hts = [0.0] * n
        hts[0], hts[-1] = rng.uniform(-1, 1), rng.uniform(-1, 1)
        step, sc = n - 1, 1.0
        while step > 1:
            half = step // 2
            for i in range(half, n - 1, step):
                hts[i] = (hts[i - half] + hts[i + half]) / 2 + rng.uniform(-1, 1) * sc
            step, sc = half, sc * rough
        xs = np.linspace(x0, x1, n)
        return [(x, base - h * amp) for x, h in zip(xs, hts)]

    def peak(self, cx, top, base, half, seed=0, rough=0.6, jag=0.08):
        """A mountain outline across the whole width: a rounded peak roughened with detail."""
        xs = np.linspace(0, 1, 513)
        det = self.ridge(0, 1, seed, rough, detail=9)
        out = []
        k = 3.0
        for i, x in enumerate(xs):
            u = abs(x - cx) / half
            env = top + (base - top) * (1 - math.exp(-k * u ** 1.4)) / (1 - math.exp(-k)) if u < 1 else base
            fall = max(0.0, 1 - u * 0.6)
            out.append((x, min(base, env + det[i][1] * jag * fall)))
        return out

    def below(self, pts, soft=0.0):
        ps = list(pts) + [(pts[-1][0], 1.2), (pts[0][0], 1.2)]
        return self.poly(ps, soft)

    def land(self, base, amp, color, seed=0, rough=0.55, haze=None, haze_amt=0.0, texture=0.0, alpha=1.0):
        """A ridge filled to the bottom; haze mixes it toward the sky colour for depth."""
        a = self.below(self.ridge(base, amp, seed, rough))
        c = hexc(color)
        if haze is not None:
            c = c * (1 - haze_amt) + hexc(haze) * haze_amt
        if texture:
            t = self.fbm(40, 3, seed + 5)
            col = c[None, None, :] * (1 - texture * 0.5 + t[..., None] * texture)
            self.paint(a, col, alpha)
        else:
            self.paint(a, c, alpha)
        return a

    def fog(self, y0, y1, color, strength=0.6, seed=0, patchy=0.5):
        band = smoothstep(y0, (y0 + y1) / 2, self.Y) * (1 - smoothstep((y0 + y1) / 2, y1, self.Y))
        n = self.fbm(3, 4, seed + 900, aspect=4)
        a = band * (1 - patchy + patchy * n) * strength
        self.paint(a, color, 1.0)

    def haze_below(self, y, color, strength=0.5, power=1.5):
        a = np.clip((self.Y - y) / (1 - y), 0, 1) ** power * strength
        self.paint(a, color)

    def water(self, horizon, tint, dim=0.6, tint_amt=0.35, ripple=0.006, sparkle="#ffffff", sparkle_amt=0.3):
        hy = int(self.py(horizon))
        rows = self.h - hy
        src = self.img[max(0, hy - rows):hy][::-1].copy()
        if src.shape[0] < rows:
            src = np.concatenate([src, np.repeat(src[-1:], rows - src.shape[0], axis=0)])
        wob = self.fbm(30, 3, 4321, aspect=0.15)[hy:]
        out = np.empty_like(src)
        for i in range(rows):
            k = (i / rows)
            off = int((wob[i, 0] - 0.5) * ripple * self.w * (0.4 + 3 * k))
            out[i] = np.roll(src[i], off, axis=0)
        fade = np.linspace(1.0, 0.5, rows)[:, None, None]
        refl = out * dim * fade
        refl = refl * (1 - tint_amt) + hexc(tint) * tint_amt
        self.img[hy:] = refl
        rng = random.Random(self.seed + 99)
        m = self.new_mask()
        d = ImageDraw.Draw(m)
        for _ in range(260):
            y = rng.uniform(hy, self.h)
            k = (y - hy) / rows
            x = rng.uniform(0, self.w)
            L = rng.uniform(8, 50) * S * (0.3 + k)
            d.line([x, y, x + L, y], fill=int(rng.uniform(30, 110)), width=max(1, int(S * (0.5 + k))))
        lum = self.img.mean(axis=2)
        self.add(self.arr(m) * np.clip(lum * 1.5, 0.15, 1), sparkle, sparkle_amt)

    # ---------------- structures ----------------
    def skyline(self, base, color, seed=0, x0=0.0, x1=1.0, hmin=0.04, hmax=0.2, wmin=0.012, wmax=0.04,
                lights=None, light_density=0.18, spires=0.25, haze=None, haze_amt=0.0):
        rng = random.Random(self.seed * 13 + seed)
        m = self.new_mask()
        d = ImageDraw.Draw(m)
        win = self.new_mask()
        dw = ImageDraw.Draw(win)
        x = x0
        while x < x1:
            bw = rng.uniform(wmin, wmax)
            mid = 1 - abs(((x - x0) / max(1e-6, x1 - x0)) - 0.5) * 1.2
            bh = rng.uniform(hmin, hmax) * (0.55 + 0.45 * mid)
            X0, X1 = self.px(x), self.px(x + bw)
            Y0, Y1 = self.py(base - bh), self.py(base) + 6 * S
            d.rectangle([X0, Y0, X1, Y1], fill=255)
            if rng.random() < spires:
                cx = (X0 + X1) / 2
                d.polygon([(cx - (X1 - X0) * 0.2, Y0), (cx, Y0 - self.py(rng.uniform(0.02, 0.07))),
                           (cx + (X1 - X0) * 0.2, Y0)], fill=255)
            if lights:
                yy = Y0 + 4 * S
                while yy < Y1 - 4 * S:
                    xx = X0 + 3 * S
                    while xx < X1 - 3 * S:
                        if rng.random() < light_density:
                            dw.rectangle([xx, yy, xx + 1.5 * S, yy + 2.5 * S], fill=255)
                        xx += 5 * S
                    yy += 6 * S
            x += bw * rng.uniform(0.7, 1.0)
        a = self.arr(m)
        c = hexc(color)
        if haze is not None:
            c = c * (1 - haze_amt) + hexc(haze) * haze_amt
        self.paint(a, c)
        if lights:
            self.glow(self.arr(win) * a, lights, 2.5, 0.9)
        return a

    def crystal(self, cx, cy, w, h, color, glow=40, glow_s=0.9, tilt=0.0, alpha=1.0, facets=True):
        X, Y = cx, cy
        t = tilt * w
        top, bot = (X + t, Y - h / 2), (X - t, Y + h / 2)
        left, right = (X - w / 2, Y - h * 0.1), (X + w / 2, Y - h * 0.1)
        L = self.poly([top, left, bot])
        R = self.poly([top, right, bot])
        whole = np.clip(L + R, 0, 1)
        c = hexc(color)
        self.add(self.blur(whole, glow), c, glow_s)
        self.paint(L, c * 0.7, alpha)
        self.paint(R, np.clip(c * 1.1 + 0.12, 0, 1), alpha)
        if facets:
            hl = self.poly([top, (X + w * 0.14, Y - h * 0.14), (X + w * 0.02, Y - h * 0.02)])
            self.paint(hl, "#ffffff", 0.55 * alpha)
        return whole

    # ---------------- light ----------------
    def god_rays(self, cx, cy, source, color, strength=0.6, steps=28, decay=0.965, reach=0.35):
        """Radial blur of a bright mask toward (cx, cy): light shafts past silhouettes."""
        src = self.to_img(source)
        acc = np.zeros((self.h, self.w), dtype=np.float32)
        w = 1.0
        tot = 0.0
        X, Y = self.px(cx), self.py(cy)
        for k in range(steps):
            s = 1 + reach * k / steps
            # Scale about (X, Y).
            a, e = 1 / s, 1 / s
            c, f = X - X / s, Y - Y / s
            im = src.transform((self.w, self.h), Image.AFFINE, (a, 0, c, 0, e, f), resample=Image.BILINEAR)
            acc += self.arr(im) * w
            tot += w
            w *= decay
        self.add(acc / tot, color, strength)

    def bloom(self, threshold=0.7, radius=18, strength=0.45):
        lum = self.img.max(axis=2)
        bright = np.clip((lum - threshold) / (1 - threshold + 1e-6), 0, 1)
        b = self.blur(bright, radius)
        self.img = self.img + b[..., None] * np.clip(self.img, 0, 1.5) * strength

    def particles(self, n, x0, x1, y0, y1, color, rmin=0.6, rmax=2.4, glow=5, strength=1.0, seed=0):
        rng = random.Random(self.seed * 101 + seed)
        m = self.new_mask()
        d = ImageDraw.Draw(m)
        for _ in range(n):
            x, y = self.px(rng.uniform(x0, x1)), self.py(rng.uniform(y0, y1))
            r = rng.uniform(rmin, rmax) * S
            d.ellipse([x - r, y - r, x + r, y + r], fill=int(rng.uniform(120, 255)))
        self.glow(self.arr(m), color, glow, strength)

    def streaks(self, n, color, angle=100, length=(0.02, 0.06), alpha=0.25, seed=0, width=1.0):
        rng = random.Random(self.seed * 7 + seed)
        m = self.new_mask()
        d = ImageDraw.Draw(m)
        ang = math.radians(angle)
        for _ in range(n):
            x, y = rng.uniform(-0.1, 1.1), rng.uniform(-0.1, 1.0)
            L = rng.uniform(*length)
            d.line([(self.px(x), self.py(y)),
                    (self.px(x + math.cos(ang) * L), self.py(y + math.sin(ang) * L * self.w / self.h))],
                   fill=int(rng.uniform(80, 220)), width=max(1, int(width * S)))
        self.add(self.arr(m), color, alpha)

    def ribbon(self, fn, color, width=4.0, glow=18, strength=1.0, steps=300, core=0.7):
        ps = [fn(t / steps) for t in range(steps + 1)]
        a = self.line(ps, width)
        self.glow(a, color, glow, strength, core)
        return a

    def feather(self, cx, cy, length, angle, color, alpha=0.9):
        """A simple stylised feather (vane + shaft)."""
        L = self.px(length)
        im = Image.new("L", (int(L * 0.5) * 2 + 4, int(L * 1.2)), 0)
        d = ImageDraw.Draw(im)
        wv, hv = im.size
        d.ellipse([wv * 0.28, hv * 0.05, wv * 0.72, hv * 0.92], fill=230)
        d.line([(wv / 2, hv * 0.02), (wv / 2, hv * 0.99)], fill=120, width=max(1, S))
        im = im.rotate(angle, expand=True, resample=Image.BICUBIC)
        m = self.new_mask()
        m.paste(im, (int(self.px(cx) - im.size[0] / 2), int(self.py(cy) - im.size[1] / 2)), im)
        a = self.arr(m)
        self.glow(a, color, 4, 0.25, core=0)
        self.paint(a, color, alpha)

    # ---------------- finish ----------------
    def grade(self, shadows="#000000", highlights="#ffffff", amount=0.15, contrast=1.05, saturation=1.0):
        img = np.clip(self.img, 0, None)
        lum = (img * np.array([0.299, 0.587, 0.114], dtype=np.float32)).sum(axis=2, keepdims=True)
        img = lum + (img - lum) * saturation
        sh = hexc(shadows)
        hi = hexc(highlights)
        t = np.clip(lum, 0, 1)
        img = img + ((1 - t) * (sh - 0.0) + t * (hi - 1.0)) * amount
        img = (img - 0.5) * contrast + 0.5
        self.img = img

    def vignette(self, strength=0.45, cy=0.48):
        d = np.sqrt((self.X - 0.5) ** 2 + ((self.Y - cy) * 0.9) ** 2) / 0.72
        self.img *= (1 - strength * np.clip(d, 0, 1) ** 2.2)[..., None]

    def canvas_texture(self, amount=0.035):
        """Fine painterly grain: a little brushed noise so flat areas don't look digital."""
        n = self.fbm(180, 2, 4242, aspect=0.35) - 0.5
        self.img = self.img * (1 + n[..., None] * amount * 2)

    def save(self, path, grain=0.012, quality=86):
        img = self.img
        img = img / (1 + np.maximum(img - 1, 0) * 0.6)  # soft highlight roll-off
        img = img + self.nrng.normal(0, grain, img.shape).astype(np.float32)
        out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
        out = out.resize((W, H), Image.LANCZOS)
        out.save(path, "WEBP", quality=quality, method=6)
        return out
