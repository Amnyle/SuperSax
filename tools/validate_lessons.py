#!/usr/bin/env python3
"""Validates lesson files in app/src/main/assets/lessons/ (format: docs/LESSON_FORMAT.md)."""
import json, os, re, sys
from fractions import Fraction

ROOT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'lessons')
TOKEN = re.compile(r'^(r|[A-G](?:bb|b|##|#|x|n)?-?\d+):(\d+)/(\d+)(~?)$')
PC = re.compile(r'^[A-G](bb|b|##|#)?$')
WRITTEN = re.compile(r'^[A-G](bb|b|##|#)?-?\d$')
TYPES = {'TEXT', 'NUMBER', 'NOTE', 'NOTES', 'STAFF_NOTE'}


def bi(x):
    return isinstance(x, dict) and isinstance(x.get('en'), str) and isinstance(x.get('fr'), str) and x['en'].strip() and x['fr'].strip()


def staff(name, s, errors):
    if s is None: return
    num, den = map(int, s.get('time', '4/4').split('/'))
    if not -7 <= s.get('key', 0) <= 7: errors.append(f'{name}: staff key out of range')
    for i, m in enumerate(s.get('measures', [])):
        total = Fraction(0)
        for t in m.split():
            tm = TOKEN.match(t)
            if not tm: errors.append(f'{name}: staff bad token {t}'); continue
            total += Fraction(int(tm.group(2)), int(tm.group(3)))
        if total != Fraction(num, den) and not s.get('free', False):
            errors.append(f'{name}: staff measure {i + 1} lasts {total}, expected {num}/{den}')


def main():
    errors, words = [], []
    files = sorted(f for f in os.listdir(ROOT) if f.endswith('.json'))
    orders = set()
    for f in files:
        name = f
        try:
            d = json.load(open(os.path.join(ROOT, f), encoding='utf-8'))
        except Exception as e:
            errors.append(f'{name}: invalid JSON {e}'); continue
        if d.get('id') + '.json' != f: errors.append(f'{name}: id must match file name')
        if d.get('order') in orders: errors.append(f'{name}: duplicate order')
        orders.add(d.get('order'))
        if not bi(d.get('title')): errors.append(f'{name}: title needs en/fr')
        n_en = 0
        for s in d.get('sections', []):
            if not bi(s.get('heading')) or not bi(s.get('body')): errors.append(f'{name}: section needs heading/body en/fr')
            else: n_en += len(s['body']['en'].split())
            staff(name, s.get('staff'), errors)
        words.append((f, n_en))
        qs = d.get('quiz', [])
        if not 3 <= len(qs) <= 6: errors.append(f'{name}: {len(qs)} questions (want 3-5)')
        ids = set()
        for q in qs:
            qn = f'{name}/{q.get("id")}'
            if q.get('id') in ids: errors.append(f'{qn}: duplicate id')
            ids.add(q.get('id'))
            if q.get('type') not in TYPES: errors.append(f'{qn}: bad type'); continue
            if not bi(q.get('prompt')) or not bi(q.get('explanation')): errors.append(f'{qn}: prompt/explanation need en/fr')
            staff(qn, q.get('staff'), errors)
            t = q['type']
            if t == 'TEXT':
                a = q.get('accept', {})
                if not (a.get('en') and a.get('fr')): errors.append(f'{qn}: TEXT needs accept.en and accept.fr')
            elif t == 'NUMBER':
                if not isinstance(q.get('answer'), int): errors.append(f'{qn}: NUMBER answer must be an integer')
            elif t == 'NOTE':
                if not PC.match(str(q.get('answer'))): errors.append(f'{qn}: NOTE answer must be a pitch class like F#')
            elif t == 'NOTES':
                a = q.get('answer')
                if not isinstance(a, list) or not a or not all(PC.match(str(x)) for x in a): errors.append(f'{qn}: NOTES answer must be a list of pitch classes')
                if not isinstance(q.get('ordered'), bool): errors.append(f'{qn}: NOTES needs ordered true/false')
            elif t == 'STAFF_NOTE':
                if not WRITTEN.match(str(q.get('answer'))): errors.append(f'{qn}: STAFF_NOTE answer must be like G5')
    for f, w in words: print(f'{f}: {w} words (en)')
    print(f'{len(files)} lessons, {len(errors)} errors')
    for e in errors: print('ERROR', e)
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
