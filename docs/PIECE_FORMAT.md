# Piece file format (`app/src/main/assets/pieces/<id>.json`)

One JSON file per piece. Pitches are **concert pitch** (what sounds), properly spelled; the app shows
written alto pitch (a major sixth higher, key signature +3 sharps). Validate with `python3 tools/validate_pieces.py`.

```json
{
  "id": "slur-steps-1",
  "title": { "en": "Slur Steps", "fr": "Pas liés" },
  "composer": "Super Sax (original)",
  "source": "Original composition for this app",
  "licence": "CC0",
  "credit": "Composed for Super Sax, released under CC0 1.0",
  "tags": ["etude", "slur"],
  "reviewed": false,
  "bundled": true,
  "key": -3,
  "mode": "major",
  "time": "4/4",
  "tempo": 72,
  "pickup": false,
  "measures": [
    "Eb4:1/2 F4:1/2",
    "G4:1/4 F4:1/4 Eb4:1/2~",
    "Eb4:1/1"
  ],
  "sections": [
    { "id": "a", "title": { "en": "Bars 1–2", "fr": "Mesures 1–2" }, "from": 1, "to": 2 },
    { "id": "b", "title": { "en": "Bar 3", "fr": "Mesure 3" }, "from": 3, "to": 3 }
  ]
}
```

- `key`: concert key signature as fifths (−6..6): −3 = three flats (concert E♭ major / C minor = written C major / A minor).
- `mode`: `major` or `minor` (which scale of that key signature the piece is in).
- `time`: `num/den`; `tempo`: beats per minute, the beat being `1/den` (quarter notes in 4/4, eighths in 6/8).
- `measures`: one string per bar; tokens `PITCH:DURATION` separated by spaces.
  - `PITCH`: letter, accidental (`#`, `b`, `##`, `bb`, `n` for an explicit natural), octave (C4 = middle C). `r` is a rest.
  - `DURATION`: fraction of a whole note: `1/1`, `1/2`, `1/4`, `1/8`, `1/16`, dotted `3/4`, `3/8`, `3/16`; any other value is drawn as tied notes.
  - A trailing `~` ties the note to the next one (same pitch).
  - Each bar must add up to the time signature, except a pickup first bar (`"pickup": true`) and a last bar that completes it.
- Concert range of the alto: D♭3 (MIDI 49) to A5 (MIDI 81). Comfortable beginner range: concert F3–E♭5 (written D4–C6).
- `sections`: bar ranges (1-based, inclusive) used for song-milestone goals.
- Metadata-only entries (`"bundled": false`) have no `key`/`measures`: the owner attaches a file in the app.
- `difficulty`, note range and duration are computed by the app; do not write them.
- Licences: only `CC0` (originals) or `public domain` (traditional / pre-1900 melodies reproduced exactly). `copyrighted` only for metadata-only entries.
