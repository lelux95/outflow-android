#!/usr/bin/env python3
"""Generatore e risolutore dei livelli di Outflow.

Ogni livello e' una griglia W x H con una maschera (la forma) e frecce di una cella.
Una freccia esce se tutte le celle nella sua direzione, fino al bordo, sono libere.

Garanzia di risolvibilita': le frecce vengono piazzate una alla volta, ciascuna
con la strada libera nel momento in cui viene piazzata. Togliendole in ordine
inverso il livello si risolve sempre. Il risolutore lo ricontrolla comunque.

Uso:  python3 generate_levels.py  ->  scrive app/src/main/assets/levels.json
"""
import json
import math
import os
import random

DIRS = [(0, -1), (1, 0), (0, 1), (-1, 0)]  # 0 su, 1 destra, 2 giu', 3 sinistra


# ---------------------------------------------------------------- forme
def shape_mask(shape, w, h):
    cells = set()
    for y in range(h):
        for x in range(w):
            # coordinate normalizzate in [-1, 1], y verso l'alto
            u = (x + 0.5) / w * 2 - 1
            v = 1 - (y + 0.5) / h * 2
            if inside(shape, u, v):
                cells.add((x, y))
    return cells


def inside(shape, u, v):
    if shape == "square":
        return True
    if shape == "diamond":
        return abs(u) + abs(v) <= 1.05
    if shape == "circle":
        return u * u + v * v <= 1.05
    if shape == "plus":
        return abs(u) <= 0.42 or abs(v) <= 0.42
    if shape == "triangle":
        return v <= 1.0 and abs(u) <= (1 - v) / 2 + 0.06
    if shape == "heart":
        x, y = u * 1.25, v * 1.25 + 0.25
        return (x * x + y * y - 1) ** 3 - x * x * y ** 3 <= 0
    if shape == "star":
        r = math.hypot(u, v)
        a = math.atan2(v, u) - math.pi / 2
        k = math.cos(5 * a)  # 5 punte
        return r <= 0.62 + 0.45 * max(0.0, k) ** 1.1
    if shape == "hourglass":
        return abs(u) <= abs(v) + 0.2
    if shape == "ring":
        r = u * u + v * v
        return 0.18 <= r <= 1.05
    raise ValueError(shape)


# ---------------------------------------------------------------- logica
def path_clear(x, y, d, w, h, occupied):
    dx, dy = DIRS[d]
    x, y = x + dx, y + dy
    while 0 <= x < w and 0 <= y < h:
        if (x, y) in occupied:
            return False
        x, y = x + dx, y + dy
    return True


def generate(mask, w, h, rng):
    """Piazza le frecce dal centro verso l'esterno: ogni freccia ha la strada libera
    quando viene piazzata, quindi il livello e' risolvibile."""
    cx, cy = (w - 1) / 2, (h - 1) / 2
    order = sorted(mask, key=lambda c: (c[0] - cx) ** 2 + (c[1] - cy) ** 2 + rng.random() * 0.8)
    occupied = {}
    for (x, y) in order:
        options = [d for d in range(4) if path_clear(x, y, d, w, h, occupied)]
        if options:
            occupied[(x, y)] = rng.choice(options)
    return occupied


def solve(arrows, w, h):
    """Toglie a ondate tutte le frecce libere. Restituisce (risolto, ondate, libere_iniziali)."""
    left = dict(arrows)
    waves, first_free = 0, None
    while left:
        free = [c for c, d in left.items() if path_clear(c[0], c[1], d, w, h, left)]
        if first_free is None:
            first_free = len(free)
        if not free:
            return False, waves, first_free
        for c in free:
            del left[c]
        waves += 1
    return True, waves, first_free or 0


def difficulty(arrows, w, h):
    ok, waves, free = solve(arrows, w, h)
    if not ok:
        return None
    blocked_share = 1 - free / len(arrows)
    return waves * 1.0 + blocked_share * 4


# ---------------------------------------------------------------- piano dei livelli
# (id dal, id al, dimensione, forme)
PLAN = [
    (1, 1, 3, ["square"]),
    (2, 3, 4, ["square"]),
    (4, 5, 4, ["square", "diamond"]),
    (6, 10, 5, ["square", "diamond", "plus", "circle"]),
    (11, 15, 6, ["square", "circle", "plus", "diamond", "hourglass"]),
    (16, 22, 7, ["heart", "triangle", "circle", "plus", "square", "diamond", "hourglass"]),
    (23, 30, 8, ["star", "heart", "ring", "triangle", "circle", "square", "hourglass", "plus"]),
]


def build_levels(seed=20261110):
    rng = random.Random(seed)
    levels = []
    for start, end, size, shapes in PLAN:
        group = []
        for i, lid in enumerate(range(start, end + 1)):
            shape = shapes[i % len(shapes)]
            mask = shape_mask(shape, size, size)
            # difficolta' cercata cresce dentro il gruppo
            span = max(1, end - start)
            want_high = (lid - start) / span
            cands = []
            for _ in range(60):
                arrows = generate(mask, size, size, rng)
                if len(arrows) < max(3, int(len(mask) * 0.85)):
                    continue
                diff = difficulty(arrows, size, size)
                if diff is None:
                    continue
                cands.append((diff, arrows))
            cands.sort(key=lambda t: t[0])
            # sceglie nella fascia della difficolta' cercata (evita i casi estremi)
            idx = int(round(want_high * (len(cands) - 1) * 0.8 + (len(cands) - 1) * 0.1))
            diff, arrows = cands[idx]
            ok, waves, free = solve(arrows, size, size)
            assert ok
            group.append({
                "id": lid,
                "shape": shape,
                "w": size,
                "h": size,
                "arrows": [[x, y, d] for (x, y), d in sorted(arrows.items(), key=lambda t: (t[0][1], t[0][0]))],
                "difficulty": round(diff, 2),
            })
        # dentro il gruppo: difficolta' crescente, poi rinumera
        group.sort(key=lambda lv: lv["difficulty"])
        for lid, lv in zip(range(start, end + 1), group):
            lv["id"] = lid
        levels.extend(group)
    return levels


def main():
    levels = build_levels()
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "app", "src", "main", "assets", "levels.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        json.dump({"version": 1, "levels": levels}, f, separators=(",", ":"))
    for lv in levels:
        print(f"{lv['id']:>3} {lv['shape']:<10} {lv['w']}x{lv['h']} frecce={len(lv['arrows']):>2} diff={lv['difficulty']}")
    print("scritto", out)


if __name__ == "__main__":
    main()
