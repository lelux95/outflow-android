#!/usr/bin/env python3
"""Generatore e risolutore dei livelli di Outflow (v3: frecce lunghe, spirali, sagome).

Regole: ogni freccia e' una catena di celle adiacenti (coda -> punta). La punta guarda nella
direzione dell'ultimo tratto. Una freccia esce se tutte le celle davanti alla punta, fino al
bordo, sono libere (anche dal proprio corpo); poi scivola via seguendo il proprio percorso.

Metodo "a buccia di cipolla": le frecce vengono create NELL'ORDINE IN CUI SI TOLGONO.
La freccia numero k deve avere davanti alla punta solo celle gia' assegnate alle frecce 1..k-1
(che escono prima di lei) o celle vuote. Le celle ancora libere che soddisfano questa regola
sono esattamente le "estreme" di ogni riga e colonna (la piu' in alto della colonna verso l'alto,
la piu' a sinistra della riga verso sinistra, ecc.): ce n'e' sempre almeno una, quindi la griglia
si riempie al 100%. Il corpo invece puo' serpeggiare liberamente dentro la parte non ancora
assegnata: frecce lunghe, spirali e incastri senza doverle spezzare.
Il risolutore ricontrolla comunque ogni livello.

Uso:  python tools/generate_levels.py  ->  app/src/main/assets/levels.json
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from silhouettes import SIL, BOSS_ORDER, silhouette_mask  # noqa: E402

DIRS = [(0, -1), (1, 0), (0, 1), (-1, 0)]  # 0 su, 1 destra, 2 giu', 3 sinistra
LEVEL_COUNT = 250
SEED = 20261113
FORMAT_VERSION = 5


# ---------------------------------------------------------------- forme geometriche (livelli normali)
def inside(shape, u, v):
    if shape == "square":
        return True
    if shape == "diamond":
        return abs(u) + abs(v) <= 1.08
    if shape == "circle":
        return u * u + v * v <= 1.08
    if shape == "plus":
        return abs(u) <= 0.45 or abs(v) <= 0.45
    if shape == "triangle":
        return abs(u) <= (1 - v) / 2 + 0.08
    if shape == "heart":
        x, y = u * 1.22, v * 1.22 + 0.22
        return (x * x + y * y - 1) ** 3 - x * x * y ** 3 <= 0
    if shape == "star":
        r = math.hypot(u, v)
        a = math.atan2(v, u) - math.pi / 2
        return r <= 0.62 + 0.45 * max(0.0, math.cos(5 * a)) ** 1.1
    if shape == "hourglass":
        return abs(u) <= abs(v) + 0.25
    if shape == "ring":
        r = u * u + v * v
        return 0.16 <= r <= 1.08
    if shape == "arrow":
        return (v >= 0 and abs(u) <= (1 - v) * 0.95 + 0.05) or (v < 0 and abs(u) <= 0.38)
    if shape == "cross":
        return abs(abs(u) - abs(v)) <= 0.42
    if shape == "house":
        return (v <= 0.15 and abs(u) <= 0.82) or (v > 0.15 and abs(u) <= (1 - v) * 1.0 + 0.05)
    raise ValueError(shape)


def shape_mask(shape, w, h):
    if shape.startswith("sil:"):
        return silhouette_mask(shape[4:], w, h)
    cells = set()
    for y in range(h):
        for x in range(w):
            u = (x + 0.5) / w * 2 - 1
            v = 1 - (y + 0.5) / h * 2
            if inside(shape, u, v):
                cells.add((x, y))
    return cells


# ---------------------------------------------------------------- logica
def ray(head, d, w, h):
    dx, dy = DIRS[d]
    x, y = head[0] + dx, head[1] + dy
    while 0 <= x < w and 0 <= y < h:
        yield (x, y)
        x, y = x + dx, y + dy


def head_dir(cells, d):
    if len(cells) >= 2:
        (ax, ay), (bx, by) = cells[-2], cells[-1]
        return DIRS.index((bx - ax, by - ay))
    return d


def solve(arrows, w, h):
    """Toglie a ondate le frecce libere. Restituisce (risolto, ondate, libere_iniziali)."""
    alive = set(range(len(arrows)))
    occ = {}
    for i, (cells, _) in enumerate(arrows):
        for c in cells:
            occ[c] = i
    waves, first_free = 0, None
    while alive:
        free = [i for i in alive
                if all(c not in occ for c in ray(arrows[i][0][-1], head_dir(*arrows[i]), w, h))]
        if first_free is None:
            first_free = len(free)
        if not free:
            return False, waves, first_free
        for i in free:
            alive.discard(i)
            for c in arrows[i][0]:
                occ.pop(c, None)
        waves += 1
    return True, waves, first_free or 0


# ---------------------------------------------------------------- generatore
class Params:
    def __init__(self, max_len, p_short, turn_p, spiral_p, straight_p, hard):
        self.max_len = max_len        # lunghezza massima delle frecce
        self.p_short = p_short        # quota di frecce corte (2-4)
        self.turn_p = turn_p          # probabilita' di curvare a ogni passo (cammino libero)
        self.spiral_p = spiral_p      # probabilita' che una freccia si avvolga a spirale
        self.straight_p = straight_p  # probabilita' di una freccia lunga e dritta
        self.hard = hard              # quanto preferire punte bloccate da molte frecce


def pick_length(p, rng):
    r = rng.random()
    if r < p.p_short:
        return rng.randint(2, 4)
    if r < p.p_short + 0.45:
        return rng.randint(4, max(5, p.max_len // 2))
    return rng.randint(max(5, p.max_len // 2), max(6, p.max_len))


def generate(mask, w, h, p, rng):
    owner = {}       # cella -> indice freccia (ordine di uscita)
    arrows = []      # [celle coda->punta, direzione]
    depth = []      # ondata minima in cui la freccia puo' uscire
    free_cells = set(mask)

    def is_free(c):
        return c in free_cells

    def frontier():
        """Celle libere con la strada gia' sgombra in una direzione: (cella, direzione)."""
        out = []
        cols, rows = {}, {}
        for (x, y) in free_cells:
            cols.setdefault(x, []).append(y)
            rows.setdefault(y, []).append(x)
        for x, ys in cols.items():
            out.append(((x, min(ys)), 0))
            out.append(((x, max(ys)), 2))
        for y, xs in rows.items():
            out.append(((max(xs), y), 1))
            out.append(((min(xs), y), 3))
        return out

    def walk(head, d, target):
        """Costruisce il corpo partendo dalla punta e andando all'indietro."""
        prev = (head[0] - DIRS[d][0], head[1] - DIRS[d][1])
        if not is_free(prev):
            return [head]
        path = [head, prev]
        used = {head, prev}
        cur = (d + 2) % 4           # direzione in cui stiamo camminando (all'indietro)
        mode = rng.random()
        spiral = mode < p.spiral_p
        straight = (not spiral) and mode < p.spiral_p + p.straight_p
        side = rng.choice((1, 3))   # verso di avvolgimento della spirale
        run = 1
        run_max = rng.randint(3, 7)
        while len(path) < target:
            x, y = path[-1]
            turn_a, turn_b = (cur + side) % 4, (cur + 4 - side) % 4
            if spiral:
                # dritto fino a un ostacolo (o a fine tratto), poi sempre dallo stesso lato
                cand = [cur, turn_a, turn_b] if run < run_max else [turn_a, cur, turn_b]
            elif straight:
                cand = [cur, turn_a, turn_b] if rng.random() > 0.06 else [turn_a, turn_b, cur]
            else:
                # cammino libero: curva a caso, e comunque dopo un tratto dritto troppo lungo
                if rng.random() < p.turn_p or run >= run_max + 2:
                    t = [turn_a, turn_b]
                    rng.shuffle(t)
                    cand = t + [cur]
                else:
                    cand = [cur, turn_a, turn_b] if rng.random() < 0.5 else [cur, turn_b, turn_a]
            moved = False
            for nd in cand:
                n = (x + DIRS[nd][0], y + DIRS[nd][1])
                if is_free(n) and n not in used:
                    if nd != cur:
                        run = 1
                        if spiral:
                            run_max = max(2, run_max - rng.randint(0, 1))
                    else:
                        run += 1
                    cur = nd
                    path.append(n)
                    used.add(n)
                    moved = True
                    break
            if not moved:
                break
        return list(reversed(path))  # coda -> punta

    while free_cells:
        cands = []
        for (c, d) in frontier():
            prev = (c[0] - DIRS[d][0], c[1] - DIRS[d][1])
            body = is_free(prev)
            hit = {owner[r] for r in ray(c, d, w, h) if r in owner}
            dep = 1 + max((depth[i] for i in hit), default=0)
            cands.append((c, d, body, dep, len(hit)))
        with_body = [t for t in cands if t[2]]
        pool = with_body or cands

        def score(t):
            _, _, _, dep, nhit = t
            s = rng.random() * 1.5
            if dep == 1:
                s -= p.hard * 1.6
            return s + p.hard * (min(dep, 10) * 0.35 + min(nhit, 6) * 0.25)
        c, d, body, dep, _ = max(pool, key=score)
        cells = walk(c, d, pick_length(p, rng)) if body else [c]
        idx = len(arrows)
        for cell in cells:
            owner[cell] = idx
            free_cells.discard(cell)
        arrows.append([cells, head_dir(cells, d)])
        depth.append(dep)

    merge_singles(arrows, w, h, rng)
    return [(cells, d) for cells, d in arrows if cells]


def merge_singles(arrows, w, h, rng):
    """Le frecce da 1 casella si attaccano alla coda di una freccia vicina, se l'ordine di uscita
    resta valido: chi ha quella cella sulla propria strada deve uscire dopo la freccia che la prende."""
    owner = {}
    for i, (cells, _) in enumerate(arrows):
        for c in cells:
            owner[c] = i
    ray_of = {}  # cella -> frecce che la hanno sulla strada
    for i, (cells, d) in enumerate(arrows):
        for r in ray(cells[-1], d, w, h):
            ray_of.setdefault(r, []).append(i)
    for i, (cells, d) in enumerate(arrows):
        if len(cells) != 1:
            continue
        s = cells[0]
        opts = []
        for dx, dy in DIRS:
            n = (s[0] + dx, s[1] + dy)
            j = owner.get(n)
            if j is None or j == i or not arrows[j][0] or arrows[j][0][0] != n:
                continue
            if len(arrows[j][0]) == 1 and (dx, dy) != DIRS[arrows[j][1]]:
                continue  # unendo due frecce da 1 la punta cambierebbe verso
            # (se s sta sulla strada di j stessa, j compare in ray_of[s] e viene esclusa: si bloccherebbe da sola)
            if all(k > j for k in ray_of.get(s, ()) if k != i):
                opts.append(j)
        if opts:
            j = rng.choice(opts)
            arrows[j][0].insert(0, s)
            owner[s] = j
            arrows[i][0] = []
            # la strada della freccia tolta non conta piu'
            for r in ray(s, d, w, h):
                lst = ray_of.get(r)
                if lst and i in lst:
                    lst.remove(i)


# ---------------------------------------------------------------- piano dei livelli
def lerp(a, b, t):
    return a + (b - a) * max(0.0, min(1.0, t))


def grid_for(lid):
    if lid <= 2:
        return 6, 8
    if lid <= 10:
        t = (lid - 3) / 7
        return round(lerp(8, 12, t)), round(lerp(11, 16, t))
    if lid <= 40:
        t = (lid - 11) / 29
        return round(lerp(13, 26, t)), round(lerp(18, 36, t))
    t = (lid - 41) / 209
    return round(lerp(26, 28, t)), round(lerp(36, 40, t))


def params_for(lid, boss):
    t40 = (lid - 1) / 39
    tall = (lid - 41) / 209
    if lid <= 40:
        max_len = round(lerp(4, 16, t40))
    else:
        max_len = round(lerp(16, 30, tall))
    p_short = lerp(0.55, 0.3, t40) if lid <= 40 else lerp(0.3, 0.22, tall)
    turn_p = 0.0 if lid <= 2 else (lerp(0.15, 0.45, t40) if lid <= 40 else 0.45)
    spiral_p = 0.0 if lid < 8 else (lerp(0.08, 0.25, t40) if lid <= 40 else lerp(0.25, 0.35, tall))
    straight_p = 0.08
    hard = 0.2 if lid <= 2 else (lerp(0.4, 1.1, t40) if lid <= 40 else lerp(1.1, 1.8, tall))
    # quota di frecce libere all'inizio a cui puntare
    target = lerp(0.45, 0.16, t40) if lid <= 40 else lerp(0.16, 0.07, tall)
    if boss:
        max_len += 3
        spiral_p += 0.05
        hard += 0.3
        target *= 0.7
    return Params(max_len, p_short, turn_p, spiral_p, straight_p, hard), target


SHAPES = {
    "early": ["square", "diamond", "circle", "plus"],
    "mid": ["square", "circle", "heart", "hourglass", "diamond", "triangle", "plus", "arrow", "house"],
    "late": ["square", "circle", "heart", "star", "ring", "hourglass", "triangle", "house", "cross", "arrow", "diamond", "plus"],
}


def shape_for(lid, boss):
    if boss:
        return "sil:" + BOSS_ORDER[lid // 10 - 1]
    if lid % 3 != 0:
        return "square"
    group = "early" if lid <= 10 else ("mid" if lid <= 60 else "late")
    lst = SHAPES[group]
    return lst[(lid * 7) % len(lst)]


def build_level(lid, rng):
    boss = lid % 10 == 0
    w, h = grid_for(lid)
    shape = shape_for(lid, boss)
    if shape != "square":
        w, h = w + 2, h + 3  # le forme hanno meno caselle: griglia un po' piu' grande
    mask = shape_mask(shape, w, h)
    prm, target = params_for(lid, boss)
    best = None
    tries = 10 if boss else 6
    for k in range(tries):
        # ogni tentativo varia un po' la "cattiveria" per avvicinarsi alla quota di libere
        p = Params(prm.max_len, prm.p_short, prm.turn_p, prm.spiral_p, prm.straight_p,
                   prm.hard * lerp(0.0 if lid <= 20 else 0.5, 1.3, k / max(1, tries - 1)))
        arrows = generate(mask, w, h, p, rng)
        ok, waves, free = solve(arrows, w, h)
        if not ok or sum(len(c) for c, _ in arrows) != len(mask):
            continue
        n = len(arrows)
        share = free / n
        singles = sum(1 for c, _ in arrows if len(c) == 1) / n
        key = abs(share - target) * 10 + singles * 6 - min(waves, 40) * 0.03
        if best is None or key < best[0]:
            best = (key, arrows, waves, share)
    if best is None:
        raise SystemExit(f"nessun candidato valido per il livello {lid}")
    _, arrows, waves, share = best
    arrows = list(arrows)
    rng.shuffle(arrows)
    n = len(arrows)
    avg = sum(len(c) for c, _ in arrows) / n
    diff = waves * 0.6 + (1 - share) * 6 + avg * 0.8 + n * 0.02
    lv = {
        "id": lid, "shape": shape, "w": w, "h": h, "boss": boss,
        "arrows": [{"c": [v for cell in cells for v in cell], "d": head_dir(cells, d)} for cells, d in arrows],
        "difficulty": round(diff, 2),
    }
    if boss:
        lv["name"] = SIL[shape[4:]][0]
        lv["name_en"] = SIL[shape[4:]][1]
        # tempo proporzionato alle frecce: circa 1 s l'una, minimo 45 s, a passi di 5
        lv["time"] = int(max(45, round(n * 1.0 / 5) * 5))
    return lv


def build_levels():
    rng = random.Random(SEED)
    raw = []
    for lid in range(1, LEVEL_COUNT + 1):
        raw.append(build_level(lid, rng))
        print(f"  generato {lid}/{LEVEL_COUNT}", file=sys.stderr, end="\r", flush=True)
    print(file=sys.stderr)
    # ogni blocco di 10 (escluso il boss) in ordine di difficolta' crescente
    out = []
    for b in range(0, LEVEL_COUNT, 10):
        block = raw[b:b + 10]
        normal = sorted([l for l in block if not l["boss"]], key=lambda l: (l["w"] * l["h"], l["difficulty"]))
        bosses = [l for l in block if l["boss"]]
        for lid, lv in zip(range(b + 1, b + 11), normal + bosses):
            lv["id"] = lid
            out.append(lv)
    return out


def main():
    levels = build_levels()
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "app", "src", "main", "assets", "levels.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        json.dump({"version": FORMAT_VERSION, "levels": levels}, f, separators=(",", ":"))
    for lv in levels:
        cells = [([(a["c"][k], a["c"][k + 1]) for k in range(0, len(a["c"]), 2)], a["d"]) for a in lv["arrows"]]
        ok, waves, free = solve(cells, lv["w"], lv["h"])
        assert ok, f"livello {lv['id']} non risolvibile"
        lens = [len(c) for c, _ in cells]
        tag = f" BOSS {lv.get('name', '')} {lv['time']}s" if lv["boss"] else ""
        print(f"{lv['id']:>3} {lv['shape']:<14} {lv['w']}x{lv['h']:<3} frecce={len(lens):>3} "
              f"lung.max={max(lens):>2} media={sum(lens)/len(lens):.1f} singole={lens.count(1):>2} "
              f"libere={free/len(lens):.0%} ondate={waves:>2} diff={lv['difficulty']}{tag}")
    print("scritto", out, os.path.getsize(out), "byte")


if __name__ == "__main__":
    main()
