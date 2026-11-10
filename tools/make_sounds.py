#!/usr/bin/env python3
"""Genera i suoni di Outflow (WAV mono 16 bit) in app/src/main/res/raw/.

Tutti sintetizzati da zero: brevi, morbidi, senza diritti di terzi.
"""
import math
import os
import random
import struct
import wave

RATE = 22050
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")


def write(name, samples, gain=0.8):
    peak = max(1e-9, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * gain)) * 32000)) for s in samples)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)


def env(i, n, attack=0.004, release=None):
    t = i / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = 1.0 - i / n if release is None else math.exp(-t / release)
    return a * r


def tone(freqs, dur, release, harmonics=(1.0, 0.25, 0.08)):
    n = int(dur * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        f = freqs(t) if callable(freqs) else freqs
        s = sum(h * math.sin(2 * math.pi * f * (k + 1) * t) for k, h in enumerate(harmonics))
        out.append(s * env(i, n, release=release))
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    rnd = random.Random(7)

    # tocco: piccolo "tic" legnoso
    write("tap", tone(1250, 0.06, 0.012, (1.0, 0.35)), 0.45)

    # uscita: fruscio che sale, filtrato
    n = int(0.28 * RATE)
    out, lp = [], 0.0
    for i in range(n):
        t = i / RATE
        cutoff = 0.05 + 0.5 * (t / 0.28)
        lp += cutoff * (rnd.uniform(-1, 1) - lp)
        whistle = 0.25 * math.sin(2 * math.pi * (500 + 900 * t / 0.28) * t)
        out.append((lp + whistle) * math.sin(math.pi * i / n) ** 1.5)
    write("out", out, 0.55)

    # errore: due note basse discendenti
    a = tone(220, 0.12, 0.05, (1.0, 0.5, 0.2))
    b = tone(165, 0.18, 0.07, (1.0, 0.5, 0.2))
    write("error", a + b, 0.6)

    # vittoria: arpeggio maggiore
    seq = []
    for f in (523.25, 659.25, 783.99, 1046.5):
        seq += tone(f, 0.11, 0.09, (1.0, 0.3, 0.1))
    seq += tone(1046.5, 0.35, 0.18, (1.0, 0.3, 0.1))
    write("win", seq, 0.6)

    # livello perso: tre note discendenti morbide
    seq = []
    for f in (392.0, 329.63, 261.63):
        seq += tone(f, 0.16, 0.1, (1.0, 0.25))
    write("lose", seq, 0.55)

    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)), "byte")


if __name__ == "__main__":
    main()
