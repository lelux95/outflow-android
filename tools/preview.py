#!/usr/bin/env python3
"""Anteprime PNG dei livelli di Outflow (solo per controllo, non finiscono nell'app).

Uso:
  python tools/preview.py sagome  OUT.png            -> tutte le sagome dei boss
  python tools/preview.py livelli OUT.png 1 10 40    -> i livelli indicati, disegnati come nel gioco
"""
import json
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from silhouettes import SIL, BOSS_ORDER, silhouette_mask  # noqa: E402

DIRS = [(0, -1), (1, 0), (0, 1), (-1, 0)]


class Canvas:
    def __init__(self, w, h, bg=(35, 43, 51)):
        self.w, self.h = w, h
        self.px = bytearray(bytes(bg) * (w * h))

    def rect(self, x0, y0, x1, y1, col):
        x0, x1 = max(0, int(x0)), min(self.w, int(x1))
        y0, y1 = max(0, int(y0)), min(self.h, int(y1))
        row = bytes(col) * (x1 - x0)
        for y in range(y0, y1):
            i = (y * self.w + x0) * 3
            self.px[i:i + len(row)] = row

    def tri(self, pts, col):
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        (ax, ay), (bx, by), (cx, cy) = pts
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if den == 0:
            return
        for y in range(max(0, int(min(ys))), min(self.h, int(max(ys)) + 1)):
            for x in range(max(0, int(min(xs))), min(self.w, int(max(xs)) + 1)):
                l1 = ((by - cy) * (x - cx) + (cx - bx) * (y - cy)) / den
                l2 = ((cy - ay) * (x - cx) + (ax - cx) * (y - cy)) / den
                if l1 >= -0.01 and l2 >= -0.01 and 1 - l1 - l2 >= -0.01:
                    i = (y * self.w + x) * 3
                    self.px[i:i + 3] = bytes(col)

    def save(self, path):
        raw = b"".join(b"\x00" + bytes(self.px[y * self.w * 3:(y + 1) * self.w * 3]) for y in range(self.h))

        def chunk(t, d):
            c = struct.pack(">I", len(d)) + t + d
            return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 2, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)


def draw_level(cv, ox, oy, lv, cell, col=(170, 182, 196)):
    t = max(1, round(cell * 0.14))
    for a in lv["arrows"]:
        c = a["c"]
        cells = [(c[k], c[k + 1]) for k in range(0, len(c), 2)]
        pts = [(ox + (x + 0.5) * cell, oy + (y + 0.5) * cell) for x, y in cells]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            cv.rect(min(x0, x1) - t / 2, min(y0, y1) - t / 2, max(x0, x1) + t / 2 + 1, max(y0, y1) + t / 2 + 1, col)
        if len(pts) == 1:
            cv.rect(pts[0][0] - t / 2, pts[0][1] - t / 2, pts[0][0] + t / 2 + 1, pts[0][1] + t / 2 + 1, col)
        dx, dy = DIRS[a["d"]]
        hx, hy = pts[-1]
        tipx, tipy = hx + dx * cell * 0.42, hy + dy * cell * 0.42
        # coda della punta: un tratto fino alla punta
        cv.rect(min(hx, tipx) - t / 2, min(hy, tipy) - t / 2, max(hx, tipx) + t / 2 + 1, max(hy, tipy) + t / 2 + 1, col)
        s = cell * 0.26
        px, py = -dy, dx
        cv.tri([(tipx + dx * s * 0.6, tipy + dy * s * 0.6),
                (tipx - dx * s * 0.7 + px * s, tipy - dy * s * 0.7 + py * s),
                (tipx - dx * s * 0.7 - px * s, tipy - dy * s * 0.7 - py * s)], col)


def sheet_silhouettes(out, w=28, h=40, cell=7):
    cols = 7
    rows = (len(BOSS_ORDER) + cols - 1) // cols
    pad = 12
    cv = Canvas(cols * (w * cell + pad) + pad, rows * (h * cell + pad) + pad)
    for i, name in enumerate(BOSS_ORDER):
        ox = pad + (i % cols) * (w * cell + pad)
        oy = pad + (i // cols) * (h * cell + pad)
        cv.rect(ox, oy, ox + w * cell, oy + h * cell, (28, 34, 40))
        for (x, y) in silhouette_mask(name, w, h):
            cv.rect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell - 1, oy + (y + 1) * cell - 1, (200, 170, 90))
    cv.save(out)


def sheet_levels(out, ids, cell=14):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, "app", "src", "main", "assets", "levels.json")) as f:
        levels = {lv["id"]: lv for lv in json.load(f)["levels"]}
    sel = [levels[i] for i in ids]
    pad = 20
    W = sum(lv["w"] * cell + pad for lv in sel) + pad
    H = max(lv["h"] * cell for lv in sel) + 2 * pad
    cv = Canvas(W, H)
    ox = pad
    for lv in sel:
        draw_level(cv, ox, pad, lv, cell)
        ox += lv["w"] * cell + pad
    cv.save(out)


if __name__ == "__main__":
    if sys.argv[1] == "sagome":
        sheet_silhouettes(sys.argv[2])
    else:
        sheet_levels(sys.argv[2], [int(a) for a in sys.argv[3:]])
