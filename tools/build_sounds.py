#!/usr/bin/env python3
"""Builds app/src/main/assets/sounds/*.bin from MuseScore_General (MIT; piano public domain).

Usage: python3 tools/build_sounds.py path/to/MuseScore_General_Full.sf2
Each file: "SXSB", version, rate, zone count, zones (lo, hi, root, gain, loopStart, loopEnd, length, offset), then
mono 16-bit PCM (big-endian) at RATE. Long samples are trimmed with a fade; looped samples keep their loop.
"""
import os, struct, sys
import numpy as np
from math import gcd
from scipy.signal import resample_poly
sys.path.insert(0, os.path.dirname(__file__))
import sf2

RATE = 32000
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'sounds')

# name: (bank, program, velocity, key range kept, max seconds, keys for kits or None)
INSTRUMENTS = {
    'piano':   (0, 0, 90, (36, 96), 2.6, None),
    'epiano':  (0, 4, 85, (36, 96), 2.6, None),
    'bass':    (0, 32, 100, (24, 60), 2.0, None),
    'ebass':   (0, 33, 100, (24, 60), 1.6, None),
    'altosax': (0, 65, 100, (44, 90), 2.5, None),
    'jazzkit': (128, 32, 100, None, 2.2, [35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 47, 49, 51, 53, 57, 59]),
    'brushkit': (128, 40, 100, None, 2.2, [35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 47, 49, 51, 53, 59]),
    'funkkit': (128, 0, 100, None, 1.6, [35, 36, 37, 38, 40, 42, 44, 46, 49, 51, 57]),
}

def root_of(g, s):
    root = g.get(58, s['key'])
    if root < 0 or root > 127: root = 60
    return root - g.get(51, 0) - (g.get(52, 0) + s['corr']) / 100.0

def pick(sf, bank, prog, vel, keys):
    zones = [g for g in sf.zones(bank, prog) if g.get(44, (0, 127))[0] <= vel <= g.get(44, (0, 127))[1]]
    zones = [g for g in zones if sf.sample(g[53])['type'] in (1, 4)]  # mono or left
    chosen = []
    if keys is not None:
        for k in keys:
            z = next((g for g in zones if g.get(43, (0, 127))[0] <= k <= g.get(43, (0, 127))[1]), None)
            if z is not None: chosen.append((k, k, z))
        return chosen
    for g in zones:
        lo, hi = g.get(43, (0, 127))
        if any(not (hi < a or lo > b) for a, b, _ in chosen): continue
        chosen.append((lo, hi, g))
    return sorted(chosen, key=lambda c: c[0])

def render(sf, g, cap):
    s = sf.sample(g[53])
    start = s['start'] + g.get(0, 0) + 32768 * g.get(4, 0)
    end = s['end'] + g.get(1, 0) + 32768 * g.get(12, 0)
    ls = s['loop_start'] + g.get(2, 0) + 32768 * g.get(45, 0) - start
    le = s['loop_end'] + g.get(3, 0) + 32768 * g.get(50, 0) - start
    looped = g.get(54, 0) in (1, 3) and 0 < ls < le <= end - start
    x = sf.pcm(start, end)
    k = gcd(RATE, s['rate'])
    y = resample_poly(x, RATE // k, s['rate'] // k).astype(np.float32)
    ratio = RATE / s['rate']
    ls, le = int(round(ls * ratio)), int(round(le * ratio))
    n = int(cap * RATE)
    if looped and le <= n and le - ls > 200:
        y = y[:le]
    else:
        looped = False
        y = y[:n]
        f = min(len(y), int(0.25 * RATE))
        y[len(y) - f:] *= np.linspace(1, 0, f) ** 2
    att = g.get(48, 0) / 10.0  # centibels -> dB
    return y, (ls if looped else -1), (le if looped else -1), 10 ** (-att * 0.4 / 20)

def build(sf, name, spec):
    bank, prog, vel, keyrange, cap, keys = spec
    chosen = pick(sf, bank, prog, vel, keys)
    zones, pcm, off = [], [], 0
    for lo, hi, g in chosen:
        if keyrange and (hi < keyrange[0] or lo > keyrange[1]): continue
        y, ls, le, gain = render(sf, g, cap)
        root = root_of(g, sf.sample(g[53]))
        zones.append((lo, hi, root, gain, ls, le, len(y), off))
        pcm.append(y * gain)
        off += len(y)
    # widen the outer ranges so every note finds a zone
    if keys is None and zones:
        zones[0] = (0,) + zones[0][1:]
        zones[-1] = zones[-1][:1] + (127,) + zones[-1][2:]
    allpcm = np.concatenate(pcm)
    peak = np.max(np.abs(allpcm))
    allpcm = allpcm * (0.95 / peak)
    norm = 0.95 / peak
    data = struct.pack('>4siii', b'SXSB', 1, RATE, len(zones))
    for lo, hi, root, gain, ls, le, n, o in zones:
        data += struct.pack('>BBffiiii', lo, hi, root, gain * norm, ls, le, n, o)
    data += (np.clip(allpcm, -1, 1) * 32767).astype('>i2').tobytes()
    with open(os.path.join(OUT, name + '.bin'), 'wb') as f: f.write(data)
    print(f'{name}: {len(zones)} zones, {len(allpcm) / RATE:.1f} s, {len(data) / 1e6:.2f} MB')

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    sf = sf2.SF2(sys.argv[1])
    for name, spec in INSTRUMENTS.items(): build(sf, name, spec)
