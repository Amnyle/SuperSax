#!/usr/bin/env python3
"""Validates the piece files in app/src/main/assets/pieces/ (format: docs/PIECE_FORMAT.md).

Checks: required fields, unique ids, token syntax, measure lengths against the time signature
(a pickup first measure and a matching short last measure are allowed), concert range of the alto
(D-flat 3 = MIDI 49 to A5 = MIDI 81), sections inside the piece. Prints a summary table.
Run: python3 tools/validate_pieces.py
"""
import json, os, re, sys
from fractions import Fraction

ROOT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'pieces')
LETTERS = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
ACC = {'': 0, 'n': 0, '#': 1, '##': 2, 'x': 2, 'b': -1, 'bb': -2}
TOKEN = re.compile(r'^(r|[A-G](?:bb|b|##|#|x|n)?-?\d+):(\d+)/(\d+)(~?)$')
PITCH = re.compile(r'^([A-G])(bb|b|##|#|x|n)?(-?\d+)$')
REQUIRED = ['id', 'title', 'composer', 'source', 'licence', 'credit', 'tags', 'reviewed', 'bundled']
LOW, HIGH = 49, 81


def midi(p):
    m = PITCH.match(p)
    return (int(m.group(3)) + 1) * 12 + LETTERS[m.group(1)] + ACC[m.group(2) or '']


def check(path, errors):
    name = os.path.basename(path)
    try:
        d = json.load(open(path, encoding='utf-8'))
    except Exception as e:
        errors.append(f'{name}: invalid JSON: {e}'); return None
    for k in REQUIRED:
        if k not in d: errors.append(f'{name}: missing {k}')
    for k in ('title',):
        if not isinstance(d.get(k), dict) or not d[k].get('en') or not d[k].get('fr'):
            errors.append(f'{name}: {k} needs en and fr')
    if d.get('id') + '.json' != name: errors.append(f'{name}: id must match the file name')
    if not d.get('bundled', True):
        return {'id': d['id'], 'notes': 0, 'range': '-', 'measures': 0}
    for k in ('key', 'time', 'tempo', 'measures'):
        if k not in d: errors.append(f'{name}: missing {k}')
    if errors and errors[-1].startswith(name): return None
    if not -6 <= d['key'] <= 6: errors.append(f'{name}: key {d["key"]} out of -6..6')
    num, den = map(int, d['time'].split('/'))
    bar = Fraction(num, den)
    lengths, mids = [], []
    for i, m in enumerate(d['measures']):
        total = Fraction(0)
        for t in m.split():
            tm = TOKEN.match(t)
            if not tm: errors.append(f'{name}: measure {i + 1}: bad token "{t}"'); continue
            dur = Fraction(int(tm.group(2)), int(tm.group(3)))
            total += dur
            if tm.group(1) != 'r':
                n = midi(tm.group(1)); mids.append(n)
                if not LOW <= n <= HIGH: errors.append(f'{name}: measure {i + 1}: {tm.group(1)} outside the alto range (concert Db3..A5)')
        lengths.append(total)
    pickup = d.get('pickup', False)
    for i, L in enumerate(lengths):
        if i == 0 and pickup:
            if not 0 < L < bar: errors.append(f'{name}: pickup measure length {L} must be shorter than {bar}')
        elif i == len(lengths) - 1 and pickup and L + lengths[0] == bar:
            pass
        elif L != bar:
            errors.append(f'{name}: measure {i + 1} lasts {L}, expected {bar}')
    for s in d.get('sections', []):
        if not (1 <= s['from'] <= s['to'] <= len(d['measures'])): errors.append(f'{name}: section {s["id"]} out of range')
        if not s.get('title', {}).get('en') or not s.get('title', {}).get('fr'): errors.append(f'{name}: section {s["id"]} needs a bilingual title')
    names = 'C C# D Eb E F F# G G# A Bb B'.split()
    rng = f'{names[min(mids) % 12]}{min(mids) // 12 - 1}-{names[max(mids) % 12]}{max(mids) // 12 - 1}' if mids else '-'
    return {'id': d['id'], 'notes': len(mids), 'range': rng, 'measures': len(d['measures']), 'key': d['key'], 'time': d['time'], 'tempo': d['tempo']}


def main():
    errors, rows, ids = [], [], set()
    for f in sorted(os.listdir(ROOT)):
        if not f.endswith('.json'): continue
        r = check(os.path.join(ROOT, f), errors)
        if r:
            if r['id'] in ids: errors.append(f'duplicate id {r["id"]}')
            ids.add(r['id']); rows.append(r)
    for r in rows: print(r)
    print(f'{len(rows)} pieces, {len(errors)} errors')
    for e in errors: print('ERROR', e)
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
