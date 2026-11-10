#!/usr/bin/env python3
"""Generatore e risolutore dei livelli di Outflow (v2: frecce a serpente).

Ogni freccia e' una catena di celle adiacenti, dalla coda alla punta, lunga 1-7.
La punta guarda nella direzione dell'ultimo tratto (per le frecce da 1 cella: "d").
Una freccia esce se tutte le celle davanti alla punta, fino al bordo, sono libere;
poi scivola via seguendo il proprio percorso.

Garanzia di risolvibilita': le frecce vengono piazzate una alla volta e ciascuna,
quando viene piazzata, ha la strada davanti alla punta libera da tutte le frecce
gia' presenti (e dal proprio corpo). Togliendole in ordine inverso il livello si
risolve sempre. Il risolutore lo ricontrolla per ogni livello.

Uso:  python3 tools/generate_levels.py  ->  app/src/main/assets/levels.json
"""
import json
import math
import os
import random
import sys

DIRS = [(0, -1), (1, 0), (0, 1), (-1, 0)]  # 0 su, 1 destra, 2 giu', 3 sinistra
LEVEL_COUNT = 100


# ---------------------------------------------------------------- forme
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
    if shape == "arrow":  # una freccia verso l'alto
        return (v >= 0 and abs(u) <= (1 - v) * 0.95 + 0.05) or (v < 0 and abs(u) <= 0.38)
    if shape == "cross":  # una X
        return abs(abs(u) - abs(v)) <= 0.42
    if shape == "house":
        return (v <= 0.15 and abs(u) <= 0.82) or (v > 0.15 and abs(u) <= (1 - v) * 1.0 + 0.05)
    raise ValueError(shape)


def shape_mask(shape, w, h):
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


class Params:
    def __init__(self, max_len, turn_p, zigzag, min_len=1, greed=1, hot=0.7, edge_short=2, fill_min=1):
        self.max_len = max_len      # lunghezza massima delle frecce
        self.turn_p = turn_p        # probabilita' di curvare a ogni passo
        self.zigzag = zigzag        # ammesse piu' curve (zig-zag, S)
        self.min_len = min_len
        self.greed = greed          # quante proposte confrontare a ogni passo (piu' alto = piu' incastri)
        self.hot = hot              # probabilita' di partire sulla traiettoria di un'altra freccia
        self.edge_short = edge_short  # frecce lunghe fino a questo valore non possono puntare fuori dal bordo
        self.fill_min = fill_min    # lunghezza minima nel riempimento finale


def generate(mask, w, h, p, rng):
    """Piazza una freccia alla volta. A ogni passo confronta p.greed proposte e tiene quella
    che blocca piu' traiettorie gia' esistenti: cosi' le frecce si incastrano tra loro."""
    occ = {}            # cella -> indice freccia
    arrows = []         # (celle coda->punta, d)
    ray_owner = {}      # cella -> quante frecce gia' piazzate la attraversano con la traiettoria

    def free(c):
        return c in mask and c not in occ

    def ray_clear(head, d, own):
        return all(c not in occ and c not in own for c in ray(head, d, w, h))

    def walk(start, target):
        cells, steps = [start], []
        used = {start}
        cur = rng.randrange(4)
        turns = 0
        while len(cells) < target:
            cand = [cur]
            if steps and (p.zigzag or turns == 0) and rng.random() < p.turn_p:
                t = [(cur + 1) % 4, (cur + 3) % 4]
                rng.shuffle(t)
                cand = t + [cur]
            elif not steps:
                cand = [cur, (cur + 1) % 4, (cur + 3) % 4, (cur + 2) % 4]
            moved = False
            for nd in cand:
                nxt = (cells[-1][0] + DIRS[nd][0], cells[-1][1] + DIRS[nd][1])
                if free(nxt) and nxt not in used:
                    if steps and nd != cur:
                        turns += 1
                    cur = nd
                    cells.append(nxt)
                    steps.append(nd)
                    used.add(nxt)
                    moved = True
                    break
            if not moved:
                break
        return cells, steps

    def propose(start, min_len):
        target = rng.randint(min_len, p.max_len)
        cells, steps = walk(start, target)
        for n in range(len(cells), 0, -1):
            if n < min_len:
                break
            part = cells[:n]
            own = set(part)
            if n >= 2:
                options = [steps[n - 2]]
            else:
                options = sorted(range(4), key=lambda dd: -sum(1 for c in ray(part[-1], dd, w, h) if c in mask) - rng.random())
            for d in options:
                hx, hy = part[-1]
                nx, ny = hx + DIRS[d][0], hy + DIRS[d][1]
                on_edge_out = not (0 <= nx < w and 0 <= ny < h)
                if on_edge_out and n <= p.edge_short:
                    continue  # freccia corta sul bordo verso l'esterno: troppo facile
                if ray_clear(part[-1], d, own):
                    return part, d
        return None

    ray_max = {}        # cella -> indice piu' alto tra le frecce la cui strada passa di li'
    unblocked = set()   # frecce la cui strada e' ancora tutta vuota
    watch = {}          # cella -> frecce non bloccate la cui strada passa di li'

    def place(part, d):
        idx = len(arrows)
        arrows.append((part, d))
        for c in part:
            occ[c] = idx
            for j in watch.pop(c, ()):
                unblocked.discard(j)
        cells_ahead = [c for c in ray(part[-1], d, w, h) if c in mask and c not in occ]
        for c in ray(part[-1], d, w, h):
            ray_owner[c] = ray_owner.get(c, 0) + 1
            ray_max[c] = max(ray_max.get(c, -1), idx)
        if cells_ahead:
            unblocked.add(idx)
            for c in cells_ahead:
                watch.setdefault(c, set()).add(idx)

    def gain(part):
        hit = set()
        for c in part:
            for j in watch.get(c, ()):
                if j in unblocked:
                    hit.add(j)
        return len(hit)

    misses = 0
    while misses < 8:
        empty = [c for c in mask if c not in occ]
        if not empty:
            break
        best, best_score = None, None
        hot = [c for c in empty if watch.get(c)]
        for _ in range(p.greed):
            # partenza preferita sulla strada di una freccia ancora libera (per bloccarla)
            start = rng.choice(hot) if hot and rng.random() < p.hot else rng.choice(empty)
            prop = propose(start, p.min_len)
            if prop is None:
                continue
            part, d = prop
            reach = sum(1 for c in ray(part[-1], d, w, h) if c in mask and c not in occ)
            if reach == 0 and len(arrows) > 2:
                continue  # resterebbe libera per sempre
            sc = gain(part) * 5 + min(reach, 6) * 0.6 + len(part) * 0.3 + rng.random()
            if best is None or sc > best_score:
                best, best_score = prop, sc
        if best is None:
            misses += 1
            continue
        misses = 0
        place(*best)
    # riempimento a crescita: le code delle frecce si allungano dentro i buchi.
    # Una coda puo' entrare nella cella c solo se c non sta sulla strada di una freccia
    # piazzata dopo (che uscirebbe prima e resterebbe bloccata) ne' sulla propria strada.
    cap = p.max_len + 1

    def grow():
        changed = True
        while changed:
            changed = False
            for c in sorted((c for c in mask if c not in occ), key=lambda _: rng.random()):
                opts = []
                for dx, dy in DIRS:
                    nb = (c[0] + dx, c[1] + dy)
                    i = occ.get(nb)
                    if i is None:
                        continue
                    cells, d = arrows[i]
                    if len(cells) == 1 and (nb[0] - c[0], nb[1] - c[1]) != DIRS[d]:
                        continue  # una freccia da 1 cresce solo all'indietro (la punta non cambia verso)
                    if cells[0] == nb and len(cells) < cap and ray_max.get(c, -1) < i:
                        opts.append((len(cells) + rng.random(), i))
                if opts:
                    opts.sort()
                    i = opts[0][1]  # allunga la freccia piu' corta
                    arrows[i][0].insert(0, c)
                    occ[c] = i
                    changed = True

    for _ in range(3):
        grow()
        for c in sorted(mask, key=lambda _: rng.random()):
            if c not in occ:
                prop = propose(c, 2) or propose(c, p.fill_min)
                if prop:
                    place(*prop)
    grow()
    return arrows


def generate_dense(mask, w, h, p, rng):
    """Griglia piena al 100%: prima copre tutte le celle con frecce (cammini casuali),
    poi ripara i blocchi senza uscita girando o spezzando le frecce coinvolte."""
    occ = {}
    paths = []
    order = sorted(mask, key=lambda _: rng.random())
    for start in order:
        if start in occ:
            continue
        target = rng.randint(p.min_len, p.max_len)
        cells = [start]
        occ[start] = len(paths)
        cur = rng.randrange(4)
        turns = 0
        while len(cells) < target:
            cand = [cur]
            if len(cells) > 1 and (p.zigzag or turns == 0) and rng.random() < p.turn_p:
                t = [(cur + 1) % 4, (cur + 3) % 4]
                rng.shuffle(t)
                cand = t + [cur]
            elif len(cells) == 1:
                cand = rng.sample(range(4), 4)
            moved = False
            for nd in cand:
                nxt = (cells[-1][0] + DIRS[nd][0], cells[-1][1] + DIRS[nd][1])
                if nxt in mask and nxt not in occ:
                    if len(cells) > 1 and nd != cur:
                        turns += 1
                    cur = nd
                    cells.append(nxt)
                    occ[nxt] = len(paths)
                    moved = True
                    break
            if not moved:
                break
        paths.append(cells)

    # le frecce da 1 casella si uniscono a un vicino quando possibile (estremo libero)
    def merge_singles():
        changed = True
        while changed:
            changed = False
            for i, cells in enumerate(paths):
                if len(cells) != 1:
                    continue
                c = cells[0]
                for dx, dy in rng.sample(DIRS, 4):
                    nb = (c[0] + dx, c[1] + dy)
                    j = occ.get(nb)
                    if j is None or j == i or len(paths[j]) >= p.max_len + 1 or not paths[j]:
                        continue
                    if paths[j][0] == nb:
                        paths[j].insert(0, c)
                    elif paths[j][-1] == nb:
                        paths[j].append(c)
                    else:
                        continue
                    occ[c] = j
                    paths[i] = []
                    changed = True
                    break
    merge_singles()
    arrows = []
    for cells in paths:
        if not cells:
            continue
        if len(cells) >= 2 and rng.random() < 0.5:
            cells = list(reversed(cells))
        d = head_dir(cells, rng.randrange(4))
        arrows.append([cells, d])

    def is_edge_out(cells, d):
        hx, hy = cells[-1]
        nx, ny = hx + DIRS[d][0], hy + DIRS[d][1]
        return not (0 <= nx < w and 0 <= ny < h)

    # orienta le frecce corte verso l'interno quando sul bordo
    for a in arrows:
        if len(a[0]) <= p.edge_short and is_edge_out(a[0], a[1]):
            if len(a[0]) >= 2:
                a[0] = list(reversed(a[0]))
                a[1] = head_dir(a[0], 0)
            else:
                a[1] = (a[1] + 2) % 4

    # riparazione: simula l'uscita; quando nessuna freccia puo' uscire, ne gira o spezza una
    for _ in range(4000):
        occ = {}
        for i, (cells, d) in enumerate(arrows):
            for c in cells:
                occ[c] = i
        alive = set(range(len(arrows)))
        while True:
            free = [i for i in alive if all(occ.get(c, -1) == -1 for c in ray(arrows[i][0][-1], arrows[i][1], w, h))]
            if not free:
                break
            for i in free:
                alive.discard(i)
                for c in arrows[i][0]:
                    occ.pop(c, None)
        if not alive:
            return [(cells, d) for cells, d in arrows]
        stuck = list(alive)
        rng.shuffle(stuck)
        fixed = False
        # 1) girare una freccia bloccata (la punta passa all'altra estremita')
        for i in stuck:
            cells, d = arrows[i]
            if len(cells) >= 2:
                rc = list(reversed(cells))
                rd = head_dir(rc, 0)
            else:
                continue
            own = set(cells)
            if all((occ.get(c, -1) == -1) for c in ray(rc[-1], rd, w, h)):
                arrows[i] = [rc, rd]
                fixed = True
                break
        if fixed:
            continue
        # 2) freccia da 1 casella: un'altra direzione libera
        for i in stuck:
            cells, d = arrows[i]
            if len(cells) == 1:
                for nd in rng.sample(range(4), 4):
                    if all(occ.get(c, -1) == -1 for c in ray(cells[0], nd, w, h)):
                        arrows[i] = [cells, nd]
                        fixed = True
                        break
            if fixed:
                break
        if fixed:
            continue
        # 3) spezza: la cella rimasta piu' in alto (o in un altro estremo) ha sempre la strada libera
        side = rng.randrange(4)
        key = [lambda c: c[1], lambda c: -c[0], lambda c: -c[1], lambda c: c[0]][side]
        cell = min((c for c, i in occ.items()), key=lambda c: (key(c), rng.random()))
        i = occ[cell]
        cells, d = arrows[i]
        k = cells.index(cell)
        before, after = cells[:k], cells[k + 1:]
        # la parte che contiene la cella diventa la punta, verso il lato libero
        if k >= 1 and head_dir(cells[:k + 1], 0) == side:
            arrows[i] = [cells[:k + 1], side]
            if after:
                arrows.append([after, head_dir(after, d) if len(after) >= 2 else d])
        else:
            arrows[i] = [[cell], side]
            if before:
                arrows.append([before, head_dir(before, 0) if len(before) >= 2 else head_dir(cells[:k + 1], 0)])
            if after:
                arrows.append([after, head_dir(after, d) if len(after) >= 2 else d])
    raise RuntimeError("riparazione non riuscita")


def solve(arrows, w, h):
    """Toglie a ondate le frecce libere. Restituisce (risolto, ondate, libere_iniziali)."""
    alive = set(range(len(arrows)))
    occ = {}
    for i, (cells, _) in enumerate(arrows):
        for c in cells:
            occ[c] = i
    waves, first_free = 0, None
    while alive:
        free = []
        for i in alive:
            cells, d = arrows[i]
            hd = head_dir(cells, d)
            if all(occ.get(c, -1) == -1 for c in ray(cells[-1], hd, w, h)):
                free.append(i)
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


def score(arrows, w, h, mask):
    ok, waves, free = solve(arrows, w, h)
    if not ok:
        return None
    n = len(arrows)
    cells = sum(len(c) for c, _ in arrows)
    blocked = 1 - free / n
    avg_len = cells / n
    return waves * 1.0 + blocked * 4 + avg_len * 0.8


# ---------------------------------------------------------------- piano
SHAPES_EARLY = ["square", "diamond", "plus", "circle"]
SHAPES_MID = ["square", "circle", "heart", "hourglass", "diamond", "triangle", "plus", "arrow"]
SHAPES_LATE = ["square", "circle", "heart", "star", "ring", "hourglass", "triangle", "house", "cross", "arrow", "diamond", "plus"]


def lerp(a, b, t):
    return a + (b - a) * max(0.0, min(1.0, t))


def plan_for(lid):
    """Griglia, parametri delle frecce e quota massima di frecce libere in partenza."""
    boss = lid % 10 == 0
    if lid <= 2:
        w, h = 6, 8  # primi livelli: per imparare
    elif lid <= 10:
        t = (lid - 3) / 7
        w, h = round(lerp(7, 9, t)), round(lerp(10, 13, t))
    elif lid <= 30:
        t = (lid - 11) / 19
        w, h = round(lerp(10, 12, t)), round(lerp(14, 17, t))
    elif lid <= 60:
        t = (lid - 31) / 29
        w, h = round(lerp(13, 14, t)), round(lerp(18, 21, t))
    else:
        t = (lid - 61) / 39
        w, h = round(lerp(15, 16, t)), round(lerp(22, 26, t))

    t = (lid - 1) / 99
    max_len = round(lerp(3, 7, t ** 0.7))
    min_len = 2 if lid < 30 else 3
    turn_p = 0.0 if lid <= 3 else (0.3 if lid <= 10 else round(lerp(0.4, 0.6, t), 2))
    zigzag = lid > 10
    greed = round(lerp(4, 14, t ** 0.6))
    hot = 0.4 if lid <= 3 else 0.75
    fill_min = 1
    target = lerp(0.5, 0.25, (lid - 1) / 29) if lid <= 30 else lerp(0.25, 0.12, (lid - 30) / 70)
    if boss:
        w, h = w + 1, h + 1
        max_len += 2
        min_len = max(3, min_len)
        turn_p = min(0.7, turn_p + 0.15)
        zigzag = True
        greed += 4
        target *= 0.6
    prm = Params(max_len, turn_p, zigzag, min_len, greed, hot, edge_short=2 if lid > 3 else 1, fill_min=fill_min)
    return w, h, prm, boss, target


SHAPES = {
    "early": ["square", "diamond", "circle", "plus"],
    "mid": ["square", "circle", "heart", "hourglass", "diamond", "triangle", "plus", "arrow", "house"],
    "late": ["square", "circle", "heart", "star", "ring", "hourglass", "triangle", "house", "cross", "arrow", "diamond", "plus"],
}


def shape_for(lid, boss):
    if boss:
        return "square"  # i boss usano sempre la griglia piena: sono i piu' densi
    if lid % 3 != 0:
        return "square"  # griglia piena a rettangolo: la piu' densa di frecce
    group = "early" if lid <= 10 else ("mid" if lid <= 45 else "late")
    lst = SHAPES[group]
    return lst[(lid * 7) % len(lst)]


def build_levels(seed=20261110):
    rng = random.Random(seed)
    raw = []
    for lid in range(1, LEVEL_COUNT + 1):
        w, h, prm, boss, target = plan_for(lid)
        shape = shape_for(lid, boss)
        if shape != "square":
            w, h = w + 2, h + 3  # le forme hanno meno caselle: griglia un po' piu' grande
        mask = shape_mask(shape, w, h)
        cands = []
        for _ in range(24 if boss else 14):
            arrows = generate_dense(mask, w, h, prm, rng)
            covered = sum(len(c) for c, _ in arrows)
            if covered < len(mask) * 0.92:
                continue
            ok, waves, free = solve(arrows, w, h)
            if not ok:
                continue
            share = free / len(arrows)
            singles = sum(1 for c, _ in arrows if len(c) == 1) / len(arrows)
            if singles > 0.22:
                continue
            # piu' vicino alla quota di libere, poi piu' ondate
            cov = covered / len(mask)
            key = (max(0.0, share - target) * 10 + abs(share - target) + (1 - cov) * 2 - min(waves, 20) * 0.04,)
            cands.append((key, arrows, waves, share))
        if not cands:
            raise SystemExit(f"nessun candidato per il livello {lid}")
        cands.sort(key=lambda t: t[0])
        _, arrows, waves, share = cands[0]
        n = len(arrows)
        avg = sum(len(c) for c, _ in arrows) / n
        diff = waves * 1.0 + (1 - share) * 4 + avg * 0.8
        lv = {
            "id": lid, "shape": shape, "w": w, "h": h, "boss": boss,
            "arrows": [{"c": [v for cell in cells for v in cell], "d": head_dir(cells, d)} for cells, d in arrows],
            "difficulty": round(diff, 2),
        }
        if boss:
            # tempo proporzionato alle frecce: circa 0,9 s l'una, minimo 45 s, a passi di 5
            lv["time"] = int(max(45, round(n * 0.9 / 5) * 5))
        raw.append(lv)
        print(f"  generato {lid}", file=sys.stderr, end="\r")
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


def preview(lv):
    g = [["." for _ in range(lv["w"])] for _ in range(lv["h"])]
    for i, a in enumerate(lv["arrows"]):
        c = a["c"]
        cells = [(c[k], c[k + 1]) for k in range(0, len(c), 2)]
        for (x, y) in cells[:-1]:
            g[y][x] = "abcdefghijklmnopqrstuvwxyz"[i % 26]
        hx, hy = cells[-1]
        g[hy][hx] = "^>v<"[a["d"]]
    return "\n".join(" ".join(r) for r in g)


def main():
    levels = build_levels()
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "app", "src", "main", "assets", "levels.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        json.dump({"version": 4, "levels": levels}, f, separators=(",", ":"))
    for lv in levels:
        lens = [len(a["c"]) // 2 for a in lv["arrows"]]
        cells = [([(a["c"][k], a["c"][k + 1]) for k in range(0, len(a["c"]), 2)], a["d"]) for a in lv["arrows"]]
        _, waves, free = solve(cells, lv["w"], lv["h"])
        tag = f" BOSS {lv['time']}s" if lv["boss"] else ""
        print(f"{lv['id']:>3} {lv['shape']:<9} {lv['w']}x{lv['h']:<3} frecce={len(lens):>2} "
              f"lung.max={max(lens)} media={sum(lens)/len(lens):.1f} libere={free/len(lens):.0%} ondate={waves} diff={lv['difficulty']}{tag}")
    print("scritto", out, os.path.getsize(out), "byte")


if __name__ == "__main__":
    main()
