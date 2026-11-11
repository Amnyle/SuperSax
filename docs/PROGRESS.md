# Progress

If the context resets: read `docs/SPEC.md`, then this file, then `docs/DECISIONS.md`.

Build environment used: JDK 21, Android SDK at `/opt/android-sdk` (platform 37, build-tools 37.0.0), no emulator
(no KVM in the container), so on-device checks are listed in `docs/DEVICE_CHECKS.md`.

## Phase 1: Core — done

Built:
- Gradle project (version catalog, single `app` module), theme (calm brass/slate, dark mode follows system or setting), Navigation Compose, EN + FR strings (`tools/add_strings.py` keeps both files in sync).
- Domain: spelled pitch and alto transposition, key signatures, note naming (letters / solfège), scale generation (12 keys × major, natural minor, 5 modes, 2 arpeggios), program model, generator (split table from `assets/config/generator.json`, interpolation, drop order, focus, limiter hooks, phone-drill cap), adaptation rules, session planner (rotation, targets, endurance chunks, rests), session run state machine, goals, backup codec + zip.
- Data: Room (16 tables, schema exported to `app/schemas`), repositories, DataStore settings, asset loader, backup export/import (staged, confirmed, transactional).
- UI: onboarding (9 questions + result), Today (Start in one tap, short-on-time cut, off-day handling), session player (countdown on monotonic time, pause, +1 min, next, lips tired with enforced rest, clean-run logging with immediate bump offer, breathing pacer, stand mode, keep screen on, optional Do Not Disturb), end-of-session summary (planned vs actual per block, tiredness 1–5, note), programs list (activate, generate, manual, import/export JSON, delete) and the shared editor, history, goals (time, tempo; song and recording goals by title/key), settings (language, note names, theme, reference pitch, rest, Do Not Disturb, experimental, re-run onboarding, backup).

Verified:
- `./gradlew assembleDebug` green; `./gradlew testDebugUnitTest`: 41 tests, 0 failures; `./gradlew lintDebug`: 0 errors.
- Not verified on a device (no emulator here): see `docs/DEVICE_CHECKS.md`.

Not done in phase 1 (by design, later phases): audio, library pieces, rank (the limiter hook returns null), phone drills.

## Phase 2: Listening and reading — done

Built:
- Pure audio domain: YIN (FFT difference, adaptive dip refinement), pitch tracker with median smoothing, hold analysis (segmentation, trims, drift, slope, trace, recompute at another tolerance), live hold tracker, tone synth (sine / soft), sample-accurate metronome, melody player.
- Android audio: `AudioEngine` (one AudioTrack mixing metronome, drone, tones, melodies), `MicCapture` (AudioRecord → YIN frames, visible errors: no permission, busy, failure, with retry).
- Tuner screen (written note large, concert small, needle with in-tune zone, "+12¢ sharp"), target note, reference tone, drone with headphone warning, hold report (trace with ±10¢ band, "in tune for 9 of 12 s", "ends 22¢ flat"), per-note history (last 4 weeks vs earlier). Microphone permission asked at first use with an explanation.
- Match-the-note drill (8 notes, finite), metronome & drone screen with tempo trainer, metronome inside scale blocks (follows the tempo bump), optional tuner inside long-tone blocks (holds saved; drift feeds the long-tone growth rule).
- Staff renderer (Canvas + Bravura), reading drill (15 notes, adaptive levels, note-name keypad in the chosen naming, accidental toggle, calm "done for today").
- Weekly review (facts + EN/FR text), copy and share.
- Home-screen widget (Glance) starting today's session; opt-in reminders (WorkManager, POST_NOTIFICATIONS asked only when turned on).

Verified:
- `assembleDebug` green; 67 unit tests, 0 failures (YIN across every semitone 139–1397 Hz with detuning, noise and harmonics; hold analysis cases; metronome tick positions; synth pitch; melody; reading drill; weekly review text EN/FR; staff layout); lint 0 errors.
- Not verified on a device: audio latency, real microphone behaviour, widget, reminders, staff glyph placement on screen (see `docs/DEVICE_CHECKS.md`).

## Phase 3: Progress — done

Built: rank calculation (`computeRank`, config in `assets/rank/rank_config.json`), rank screen (staircase, six-axis radar with the limiter, pillar details with every next-tier requirement and progress, self-reported range notes, verified/self-reported counts), snapshots; the generator uses the limiter (Regenerate) and the library (piece choice for new material and play for fun). Library: piece format, difficulty, matching states with reasons, 32 bundled pieces + the Littleroot Town metadata entry, piece view (written/concert toggle, Listen with note highlight, stages for piece and sections), song-milestone goals linked to pieces (also from onboarding), MusicXML and PDF import, PDF viewer. Recordings (record, tag, benchmark, A/B comparison, evidence), reed and gear log.

## Phase 4: Learning content — done

Built: 13 bilingual theory lessons with free-form quizzes (text, number, note keypad, note sequences, tap-on-staff), scale recognition (name from key signature, from notes, build from root; adaptive levels), fingering trainer (drawn key diagram, both directions, chart with alternates, altissimo warning), phone-session scheduling with the 20 percent cap using the four phone drills, Musicianship fed by reading, scale recognition and lessons.

Verified (phases 3 and 4 were committed together, see git log): `assembleDebug` green; 86 unit tests, 0 failures (rank: unranked, each pillar, averages and divisions, Musicianship cap, limiter tie-break, consistency window and floor, verified-only altissimo, recompute after config change; difficulty ordering on synthetic and real pieces; matching states and choice; MusicXML import; every bundled piece and lesson loads, renders and its answers are accepted); lint 0 errors.

Not done: by-ear scale recognition (spec: "if time allows").
