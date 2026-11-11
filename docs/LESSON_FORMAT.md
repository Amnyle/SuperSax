# Lesson file format (`app/src/main/assets/lessons/<id>.json`)

Original text only, English and French in every text field. Validate with `python3 tools/validate_lessons.py`.

```json
{
  "id": "01-staff-and-clef",
  "order": 1,
  "minutes": 3,
  "title": { "en": "The staff and the treble clef", "fr": "La portée et la clé de sol" },
  "sections": [
    { "heading": { "en": "...", "fr": "..." },
      "body": { "en": "Paragraphs separated by a blank line (\n\n).", "fr": "..." },
      "staff": { "key": 0, "time": "4/4", "measures": ["E4:1/4 G4:1/4 B4:1/4 D5:1/4"] } }
  ],
  "quiz": [ ...questions... ]
}
```

- `staff` (optional, in sections and questions) shows music on a treble staff. **Pitches here are written
  pitch** (what the alto player reads), unlike piece files. Tokens as in `docs/PIECE_FORMAT.md`
  (`E4:1/4`, `r:1/2`, `F#5:1/8`), each bar adding up to the time signature. `key` is fifths (−7..7).
- Body text: 2–3 minutes of reading per lesson (roughly 300–450 words in English). Plain text, no markdown
  except blank lines between paragraphs. You can use ♯ ♭ ♮ symbols.

## Quiz questions (free-form answers; never multiple choice)

Every question has `id`, `type`, `prompt` {en, fr}, `explanation` {en, fr} (shown after answering), optional `staff`.

| type | answer fields | how the player answers |
|---|---|---|
| `TEXT` | `accept`: {"en": [...], "fr": [...]} – accepted answers; matching ignores case, accents, punctuation and extra spaces | types a word or short phrase |
| `NUMBER` | `answer`: integer | types a number |
| `NOTE` | `answer`: pitch class in ASCII letters, e.g. `"F#"`, `"Bb"`, `"C"` (spelling matters) | note-name keypad + ♭/♮/♯ toggle (shown in letters or solfège) |
| `NOTES` | `answer`: list of pitch classes, e.g. `["D","E","F#","G","A","B","C#","D"]`; `ordered`: true/false | taps notes in sequence on the keypad (e.g. builds a scale) |
| `STAFF_NOTE` | `answer`: written pitch with octave, e.g. `"G5"`, `"Bb3"` | taps a line or space on a staff, then picks ♭/♮/♯ |

- 3 to 5 questions per lesson. Hard rather than easy: ask for reasoning results, not definitions recited.
- Notes in answers are written pitch unless the prompt explicitly says concert pitch.
- Alto transposition facts: written = concert up a major sixth; written C sounds concert E♭; written A sounds
  concert C; key signature: written = concert + 3 sharps (concert C major → written A major).
