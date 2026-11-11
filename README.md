# Super Sax

An Android app (Java core, Kotlin and Jetpack Compose UI, Material 3) that helps a beginner **alto saxophonist** set goals for,
plan, run and track practice. Everything stays on the phone: no account, no analytics, no ads; the only network
use is the optional update check on GitHub. English and French. The full specification is [`docs/SPEC.md`](docs/SPEC.md).

## What it does

- **Onboarding** (one question per page, re-runnable) → a **generated program** or one you **build by hand**;
  both open in the same editor (days → blocks → exercises → targets, phone sessions).
- **Today**: one big Start button. Timer-first **session player**: countdown on monotonic time, pause, +1 min,
  next, **lips tired** (enforced rest), clean-run logging with a tempo bump after two clean runs, metronome in
  scale blocks, optional tuner in long-tone blocks, the piece and its section in new-material blocks, stand mode
  (landscape, large text), screen kept on, optional Do Not Disturb. End-of-session summary with tiredness and note.
- **Adaptation**: +5 BPM after two clean runs, new scales unlock when the last two are solid, long tones grow only
  without early fatigue (and with low tuner drift), endurance cap grows over time.
- **Tuner** (own YIN pitch detection in Java): written note, cents needle, hold reports with the pitch trace,
  per-note tendencies over weeks. **Match-the-note**, **metronome** (sample-accurate) with tempo trainer, **drone**.
- **Phone drills** (finite, then "done for today"): note reading, scale recognition, 13 theory lessons with
  free-form quizzes, fingering trainer with a drawn key diagram. Scheduled at most 20 percent of sax time.
- **Library**: 32 bundled pieces (CC0 originals and public-domain melodies, all marked unreviewed), staff drawn
  natively with Bravura (written alto by default, concert on request), Listen, stages per piece and section,
  matching to your range and scales, MusicXML and PDF import.
- **Rank** from Iron to Master over six pillars (thresholds in `assets/rank/rank_config.json`), staircase, radar,
  the weakest pillar fed to the generator. No notifications, no social features.
- **Goals** (time, tempo, song milestones, recordings), **weekly review** (plain text, copy or share),
  **recordings** with benchmarks and A/B comparison, **reed and gear log**, **slow-down player** (50–100 percent,
  A–B loops), **overtone and altissimo verification**, experimental **scale checker**.
- **Backup**: a `.zip` (data, imports, optional recordings) exported and imported through the system file picker.
- Home-screen **widget** (start today's session) and opt-in **reminders** (off by default).

## Install and update

Install the APK from the [releases page](https://github.com/Amnyle/SuperSax/releases). After that the app updates
itself: Settings → **App updates** checks GitHub every day (can be turned off), downloads the new release, checks
its signature and installs it. Every push to `main` builds and publishes a new signed release
(`.github/workflows/release.yml`); it needs the repository secret `SAX_KEYSTORE_BASE64` (the release signing key,
never committed).

## Build, run, test

Requirements: JDK 17+ (21 used), Android SDK with platform 37 and build-tools 37.0.0.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

```bash
./gradlew assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lintDebug
```

The unit tests include Robolectric UI tests that run the real app on the JVM (the first run downloads the
Robolectric Android jar). Content files can be checked on their own:

```bash
python3 tools/validate_pieces.py
```

```bash
python3 tools/validate_lessons.py
```

## Where things live

| Path | What |
|---|---|
| `app/src/main/java/app/supersax/domain/` | Pure logic in Java, serializable data models in Kotlin (no Android imports, enforced by a test): pitch and transposition, scales, program model, generator, adaptation, planner, session state machine, YIN, hold analysis, metronome/synth, rank, library (score, difficulty, matching, MusicXML), drills and lessons, weekly review, backup codec |
| `.../data/` | Room database (schema in `app/schemas/`), repositories, DataStore settings, asset loader, backup, session manager, library and rank services |
| `.../audio/` | Java: AudioRecord capture, AudioTrack engine, MediaRecorder, Media3 player |
| `.../notation/` | Staff layout in Java (pure, tested); the Compose Canvas staff and staff input in Kotlin |
| `.../ui/` | Kotlin: screens and ViewModels by feature; `AppContainer.kt` is the manual dependency injection |
| `app/src/main/assets/` | Data files: generator config, exercises, rank thresholds, pieces, lessons, fingerings |
| `tools/` | String-file helper (keeps EN/FR in sync), content validators, test summary |
| `docs/` | Spec, progress, decisions, content licences, device checks, file formats, rendered widget images |

## Progress checklist

- [x] Phase 1: Core
- [x] Phase 2: Listening and reading
- [x] Phase 3: Progress
- [x] Phase 4: Learning content
- [x] Phase 5: Advanced listening and polish

Phase 1 acceptance checks:
- [x] Cold start → today's session in two taps (checked end to end in the Robolectric UI test; still to confirm on a phone).
- [x] 10, 20 and 30 minutes give the section 6 split; new material disappears first (unit tests).
- [x] Manual and generated programs open in the same editor (one `ProgramEditorScreen`).
- [ ] Export, wipe app data, import → identical (round trip unit-tested on the JVM; confirm on a phone).
- [x] Note-name and language switches change labels and strings (Robolectric UI test; confirm on a phone).

Not done: by-ear scale recognition (optional in the spec). Nothing has run on a real device yet:
see [`docs/DEVICE_CHECKS.md`](docs/DEVICE_CHECKS.md).

## Main decisions (details in `docs/DECISIONS.md`)

- Concert pitch is stored everywhere (scales, pieces, holds); the UI shows written alto pitch. Written keys above
  six sharps are respelled (concert E major is written D♭ major).
- Java core + Kotlin UI: engines and rules (pitch, YIN, synth, generator, planner, rank, library, drills, goals,
  review, backup zip, audio plumbing) are Java; Compose UI, ViewModels, Room/DataStore and serializable models are Kotlin.
- AGP 9.5 / Kotlin 2.4 / compileSdk 37; manual DI; programs stored as JSON documents in Room; settings as one
  JSON document in DataStore; domain records double as the backup DTO layer.
- YIN uses a 70 ms window with an FFT difference function and a noise-robust dip refinement (worst error
  1.4 cents at 18 dB SNR across the alto range in tests).
- Rank interpretations (Range notes per tier, Musicianship thresholds, consistency counting) are documented
  and all live in `rank_config.json`.

## Documentation

- [`docs/PROGRESS.md`](docs/PROGRESS.md) – what is done, verified, not done, per phase.
- [`docs/DECISIONS.md`](docs/DECISIONS.md) – every decision where the spec is silent.
- [`docs/CONTENT.md`](docs/CONTENT.md) – author, source and licence of all bundled content.
- [`docs/DEVICE_CHECKS.md`](docs/DEVICE_CHECKS.md) – what only a real phone can verify.
- [`docs/PIECE_FORMAT.md`](docs/PIECE_FORMAT.md), [`docs/LESSON_FORMAT.md`](docs/LESSON_FORMAT.md) – content file formats.
