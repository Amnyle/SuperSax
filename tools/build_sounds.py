#!/usr/bin/env python3
"""Builds app/src/main/assets/sounds/*.bin (format 2: zones with key and loudness ranges).

Sources (see docs/licenses): MuseScore General (MIT), Salamander Grand Piano V3 (CC BY 3.0, Alexander Holm, from the
npm packages @audio-samples/piano-velocity7/13), tonejs-instruments alto saxophone and electric bass (npm
tonejs-instrument-*-mp3), AVL Drumkits Blonde Bop and Black Pearl (CC BY-SA 3.0, Glen MacArthur, Debian
avldrums.lv2-soundfont).

Usage: python3 tools/build_sounds.py SOURCES_DIR   (SOURCES_DIR holds the unpacked packages, see SOURCES below)
Each file: "SXSB", version 2, rate, zone count, zones (lo, hi, velLo, velHi, root, gain, loopStart, loopEnd, length,
offset), then mono 16-bit PCM (big-endian) at RATE.
"""
import glob, os, re, struct, subprocess, sys
import numpy as np
from math import gcd
from scipy.signal import resample_poly
sys.path.insert(0, os.path.dirname(__file__))
import sf2

RATE = 32000
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'sounds')
NOTE = re.compile(r'([A-G])(#|s)?(-?\d)(?:v\d+)?\.(?:mp3|ogg|wav)$')
SEMI = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(RATE), '-'],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype='<f4').copy()


def trim(y, cap, fade=0.25):
    y = y[:int(cap * RATE)].copy()
    f = min(len(y), int(fade * RATE))
    y[len(y) - f:] *= np.linspace(1, 0, f) ** 2
    return y


def start_at_sound(y, thresh=0.01):
    """Drops the silence before the attack (keeps 2 ms)."""
    peak = np.max(np.abs(y)) or 1
    idx = np.argmax(np.abs(y) > thresh * peak)
    return y[max(0, idx - int(0.002 * RATE)):]


def make_loop(y, midi, start=0.6, length=0.6, xfade=0.12):
    """A crossfaded sustain loop of a whole number of periods, so a held note can last as long as needed."""
    period = RATE / (440.0 * 2 ** ((midi - 69) / 12))
    n = max(1, round(length * RATE / period))
    ls = int(start * RATE)
    le = ls + int(round(n * period))
    x = int(xfade * RATE)
    y = y[:le].copy()
    a = np.linspace(0, 1, x)
    y[le - x:le] = y[le - x:le] * (1 - a) + y[ls - x:ls] * a
    return y, ls, le


def folder(path, layers=((0, 127),), cap=2.2, sustain=False, glob_pat='*', keep=None):
    """Zones from files named after their note (A4.mp3, As3.mp3, D#4v7.ogg). layers: one folder per loudness range."""
    zones = []
    for (vlo, vhi), p in zip(layers, path if isinstance(path, (list, tuple)) else [path]):
        files = []
        for f in glob.glob(os.path.join(p, glob_pat)):
            m = NOTE.search(os.path.basename(f))
            if not m: continue
            midi = 12 * (int(m.group(3)) + 1) + SEMI[m.group(1)] + (1 if m.group(2) else 0)
            if keep and not (keep[0] <= midi <= keep[1]): continue
            files.append((midi, f))
        files.sort()
        for i, (midi, f) in enumerate(files):
            lo = 0 if i == 0 else (files[i - 1][0] + midi) // 2 + 1
            hi = 127 if i == len(files) - 1 else (midi + files[i + 1][0]) // 2
            y = start_at_sound(decode(f))
            if sustain:
                y, ls, le = make_loop(y, midi)
            else:
                y, ls, le = trim(y, cap), -1, -1
            zones.append(dict(lo=lo, hi=hi, vlo=vlo, vhi=vhi, root=float(midi), gain=1.0, ls=ls, le=le, pcm=y))
    return zones


def sf2_zones(path, bank, prog, layers, keys=None, keyrange=None, cap=2.2, cymbal_cap=2.6):
    """Zones from a SoundFont preset: for each loudness range, the sample the font plays at that range's velocity."""
    s = sf2.SF2(path)
    zones = []
    for vlo, vhi, vel in layers:
        cand = [g for g in s.zones(bank, prog) if g.get(44, (0, 127))[0] <= vel <= g.get(44, (0, 127))[1]
                and s.sample(g[53])['type'] in (1, 4)]
        chosen = []
        if keys:
            for k in keys:
                z = next((g for g in cand if g.get(43, (0, 127))[0] <= k <= g.get(43, (0, 127))[1]), None)
                if z is not None: chosen.append((k, k, z))
        else:
            for g in cand:
                lo, hi = g.get(43, (0, 127))
                if any(not (hi < a or lo > b) for a, b, _ in chosen): continue
                chosen.append((lo, hi, g))
            chosen.sort(key=lambda c: c[0])
        for lo, hi, g in chosen:
            if keyrange and (hi < keyrange[0] or lo > keyrange[1]): continue
            smp = s.sample(g[53])
            start = smp['start'] + g.get(0, 0) + 32768 * g.get(4, 0)
            end = smp['end'] + g.get(1, 0) + 32768 * g.get(12, 0)
            lsr = smp['loop_start'] + g.get(2, 0) + 32768 * g.get(45, 0) - start
            ler = smp['loop_end'] + g.get(3, 0) + 32768 * g.get(50, 0) - start
            looped = g.get(54, 0) in (1, 3) and 0 < lsr < ler <= end - start
            x = s.pcm(start, end)
            k = gcd(RATE, smp['rate'])
            y = resample_poly(x, RATE // k, smp['rate'] // k).astype(np.float32)
            ratio = RATE / smp['rate']
            ls, le = int(round(lsr * ratio)), int(round(ler * ratio))
            c = cymbal_cap if keys and lo in (49, 51, 52, 53, 55, 57, 59, 60) else cap
            if looped and le <= c * RATE and le - ls > 200:
                y = y[:le]
            else:
                ls = le = -1
                y = trim(y, c)
            root = g.get(58, smp['key'])
            if root < 0 or root > 127: root = 60
            root = root - g.get(51, 0) - (g.get(52, 0) + smp['corr']) / 100.0
            gain = 10 ** (-(g.get(48, 0) / 10.0) * 0.4 / 20)
            zones.append(dict(lo=lo, hi=hi, vlo=vlo, vhi=vhi, root=root, gain=gain, ls=ls, le=le, pcm=y))
    if not keys and zones:
        for vlo in set(z['vlo'] for z in zones):
            layer = [z for z in zones if z['vlo'] == vlo]
            layer[0]['lo'] = 0; layer[-1]['hi'] = 127
    return zones


def write(name, zones):
    pcm, off = [], 0
    for z in zones:
        z['off'] = off; z['n'] = len(z['pcm'])
        pcm.append(z['pcm'] * z['gain']); off += z['n']
    allpcm = np.concatenate(pcm)
    norm = 0.95 / (np.max(np.abs(allpcm)) or 1)
    data = struct.pack('>4siii', b'SXSB', 2, RATE, len(zones))
    for z in zones:
        data += struct.pack('>BBBBffiiii', z['lo'], z['hi'], z['vlo'], z['vhi'], z['root'], z['gain'] * norm, z['ls'], z['le'], z['n'], z['off'])
    data += (np.clip(allpcm * norm, -1, 1) * 32767).astype('>i2').tobytes()
    with open(os.path.join(OUT, name + '.bin'), 'wb') as f: f.write(data)
    print(f'{name}: {len(zones)} zones, {len(allpcm) / RATE:.1f} s, {len(data) / 1e6:.2f} MB')


KIT = [36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 47, 49, 51, 53, 57, 59]
LAYERS3 = [(0, 63, 40), (64, 104, 90), (105, 127, 120)]

if __name__ == '__main__':
    src = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    ms = os.path.join(src, 'MuseScore_General_Full.sf2')
    avl = os.path.join(src, 'avl')
    write('piano', folder([os.path.join(src, 'pv7/package/audio'), os.path.join(src, 'pv13/package/audio')],
                          layers=((0, 79), (80, 127)), cap=2.0, keep=(35, 93)))
    write('epiano', sf2_zones(ms, 0, 4, [(0, 127, 85)], keyrange=(36, 96), cap=2.6))
    write('bass', sf2_zones(ms, 0, 32, [(0, 127, 100)], keyrange=(24, 60), cap=2.0))
    write('ebass', folder(os.path.join(src, 'ebass'), cap=1.6))
    write('altosax', folder(os.path.join(src, 'sax'), sustain=True))
    write('jazzkit', sf2_zones(os.path.join(avl, 'Blonde_Bop_LV2.sf2'), 0, 0, LAYERS3, keys=KIT, cap=1.2))
    write('brushkit', sf2_zones(ms, 128, 40, [(0, 127, 100)], keys=KIT, cap=2.2))
    write('funkkit', sf2_zones(os.path.join(avl, 'Black_Pearl_4_LV2.sf2'), 0, 0, LAYERS3, keys=KIT, cap=1.0, cymbal_cap=2.0))
