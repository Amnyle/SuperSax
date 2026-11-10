# Build the Sax Practice app (Android, Kotlin): the whole project

You are Claude Code. Build the complete Android app described below, from an empty folder to a working debug APK: code, tests, content data files and documentation. Work autonomously, phase by phase. This prompt is long on purpose: it is the whole spec. Write real code, not stubs.

---

## 0. How to work

- The current folder is the project root.
- If a `README.md` already exists here, it is an earlier draft of this spec. **This prompt wins wherever they differ.** Fold anything useful from it into the final README.
- If this prompt came from a file, keep that file as `docs/SPEC.md`. If it was pasted, save it verbatim as `docs/SPEC.md` first.
- Maintain these files throughout:
  - `README.md`: what the app is, features, how to build and run, progress checklist (phases and acceptance checks), summary of decisions.
  - `docs/PROGRESS.md`: after each phase, what is done, what you verified (built, tests run), what is not done and why. If your context resets, read `docs/SPEC.md` and `docs/PROGRESS.md` and resume.
  - `docs/DECISIONS.md`: every decision you made where this spec is silent, one line of reasoning each.
  - `docs/CONTENT.md`: author, source and licence of every bundled piece, lesson text and chart.
  - `docs/DEVICE_CHECKS.md`: things that can only be verified on a real phone (microphone latency, altissimo detection, speaker and headphone behaviour).
- Do not stop to ask questions unless you are truly blocked (tooling you cannot install, a real contradiction in the spec). Otherwise decide, log it in `docs/DECISIONS.md`, and continue.
- Initialise git if needed. Commit at the end of every phase, only when the build is green. Use clear commit messages.
- After every phase: build and run the unit tests. Never claim something works if you did not run it. If something can only be checked on a device, put it in `docs/DEVICE_CHECKS.md` and say so.
- Idiomatic Kotlin, small files, unidirectional data flow, state hoisted in ViewModels, KDoc on the domain logic. Use subagents for independent content writing (lessons, pieces) if you have them.
- Every in-scope feature must be real. If you truly cannot finish one, list it under "Not done" in `docs/PROGRESS.md` with the reason. Do not hide it.

## 1. Environment check (do this first)

1. Check JDK (17 or newer), Android SDK (`ANDROID_HOME` or `ANDROID_SDK_ROOT`), `sdkmanager`, `adb`, and whether an emulator or device is available.
2. If the SDK is missing and you have network access, install the command-line tools and the platform and build-tools you need.
3. If you cannot get an SDK: still write all the code and the Gradle wrapper, say clearly in `docs/PROGRESS.md` that the build was not verified, and list exactly what the owner must install.
4. Optional reference: if you have network access you may clone https://github.com/MamaLama0/SuperHealth into a temporary folder **outside** this project, only to read how it structures onboarding, generator and session engine (see section 5). Read it, do not copy code from it, and delete the clone when done.

## 2. Product

An Android app that helps a beginner alto saxophonist set goals for, plan, run and track practice.

- The owner is a beginner who already knows the notes. Maybe published later.
- The app asks onboarding questions, then **generates a personalised practice program** (long tones, scales, new material, play for fun) or lets the owner **build one manually**. Generated and manual programs are the same object, edited in the same editor.
- Sessions are **timer-first**: start the session, the app runs the blocks and logs it.
- Phone-only sessions (no sax needed): sheet-music reading, scale recognition, music theory lessons.
- A **sheet music library** the app can assign pieces from.
- A **rank system** from Iron to Master.
- A **tuner for long tones** that listens and reports sharp or flat.

### Decided, do not change

- Android, Kotlin. **Alto saxophone (E♭) only.** The instrument is stored as a setting so tenor could be added later.
- Timer-first logging.
- Four goal types: **time**, **tempo**, **song milestones**, **recordings**.
- Local storage only, plus an export/import backup file. No account.
- Two taps from opening the app to a running session.
- Programs, exercises, lessons, pieces and rank thresholds are **data files**, not hard-coded.
- Privacy: no `INTERNET` permission. No analytics, no ads, no network calls of any kind.

### Design principles

- **No engagement hooks.** No streak guilt, no leaderboards, no rank notifications, no confetti. Reminders are opt-in, off by default, and never use guilt language.
- **Phone drills are finite**: a fixed length, then a calm "done for today" screen. No endless mode. The owner is trying to reduce phone time.
- **Honest, direct feedback.** The weekly review states plain facts (planned vs actual), without softening.
- Calm, minimal visual style. Material 3, dark mode follows the system.
- Accessibility: large touch targets, content descriptions, respects font scale. Readable from a music stand.

## 3. Tech stack (decided)

- Kotlin, Jetpack Compose with Material 3, Navigation Compose, coroutines and Flow.
- Room with KSP, DataStore for settings, kotlinx.serialization for JSON.
- Manual dependency injection with a simple `AppContainer` (no Hilt).
- Single Gradle module `app`, version catalog (`libs.versions.toml`). Use the latest stable, mutually compatible versions you can verify. `minSdk 26`, `compileSdk` and `targetSdk` the latest stable.
- Application id `app.saxpractice`, app name "Sax Practice" (kept easy to rename).
- Audio: `AudioRecord` for capture, `AudioTrack` for tones, drone and metronome. Pitch detection is **your own YIN implementation in pure Kotlin**. Do not use TarsosDSP, because it is GPL and the app may be published.
- Playback of imported or recorded audio: AndroidX Media3 ExoPlayer.
- Home-screen widget: Jetpack Glance.
- Tests: JUnit and kotlin.test, Turbine for Flow if useful.
- Package layout (adjust if you have a good reason, log it):
  - `domain/` pure Kotlin, **no `android.*` imports**: models, generator, session planner, rank, library matching, difficulty scoring, pitch and transposition, scale generation, YIN, hold analysis, weekly review text builder.
  - `data/` Room entities and DAOs, repositories, DTOs, backup, asset loaders.
  - `audio/` Android audio plumbing around the pure domain code.
  - `ui/` screens, ViewModels, components, theme.
  - `notation/` the staff renderer.
- Add a small test that scans `domain/` and fails if any file imports `android.`.

## 4. Pitch, transposition and notation (get this exactly right)

- **Concert pitch** is the standard for storage and audio analysis. The UI shows **written alto pitch**.
- Alto E♭ sounds a **major sixth below** written: written = concert up a major sixth (5 diatonic steps, 9 semitones), with correct spelling. The key signature gets three more sharps: concert C major is written A major, concert E♭ major is written C major.
- Ground truth for tests (MIDI, with A4 = 69, C4 = 60): written A4 (69) sounds concert C4 (60). Written C5 (72) sounds E♭4 (63).
- Standard alto range (written): low B♭3 (MIDI 58) to high F♯6 (MIDI 90). In concert pitch that is C♯3/D♭3 (49) to A5 (81). Altissimo is anything above written F♯6 (G6 and up).
- Cents: `1200 * log2(f / f_ref)`. Reference pitch A4 = 440 Hz by default, adjustable in settings.
- Note names setting, independent of language: letters (C D E F G A B) or solfège (Do Ré Mi Fa Sol La Si). Accidentals with ♯ and ♭.
- **Notation is drawn natively in Compose Canvas**, no WebView and no SVG pipeline. Reasons: the reading drills need a staff renderer anyway, and transposition becomes trivial. It handles treble clef, key signatures, time signatures, notes with ledger lines, accidentals, rests, bar lines, ties, single-voice melodies only. Use the Bravura music font (SIL Open Font License, SMuFL code points) bundled as a font resource if you can obtain it, and record its licence in `docs/CONTENT.md`. If you cannot obtain it, draw the clef and accidentals as vector paths.
- A piece stores its notes in concert pitch with proper spelling. The piece view shows written alto by default, with a toggle to show concert pitch.
- Imported PDFs are shown with Android's `PdfRenderer`, as they are.

## 5. Reference app: SuperHealth

Borrow the structure and ideas, not the code. Reference paths, under `app/src/superhealth/`:

| File | What it shows |
|---|---|
| `java/app/superhealth/training/domain/Generator.kt` | Rule-based generator. Ordered slots, the first ones are kept when time is short. |
| `.../training/domain/Models.kt` | Program model: targets that progress per cycle. |
| `.../ui/onboarding/Onboarding.kt` | One decision per page, re-runnable from settings. |
| `assets/programs/arms-30-days.json` | A prebuilt program as JSON: program, days, blocks, exercises, targets. |
| `SessionEngine.kt`, `SessionScreen.kt` | Running a session. |
| `PlanEditor.kt`, `ProgramEdit.kt`, `ProgramTransfer.kt` | Manual editing, import and export of programs. |

Translation to sax: no sets and reps. Targets are durations, BPM, clean runs, note range and key.

## 6. Program model, generator and session planner

### Program model

**Program > days > blocks > exercises > targets**, as a JSON-serialisable template. A program is a template. At the start of each session, a `SessionPlanner` resolves it into a concrete session using the player's current state (clean-run history per scale and BPM, unlocked scales, long-tone hold target, stamina). Manual programs can pin explicit targets, which override the automatic progression.

Exercise types: long tone, scale (major, natural minor, modes, arpeggios), new material (a piece or a section), play for fun, breathing pacer (optional, only added manually in the editor, never auto-generated), and phone drills (reading, scale recognition, theory lesson, fingering).

Scale note sequences are **generated programmatically** in the domain code from the scale definition, never typed by hand. Cover all 12 major keys, natural minors, the common modes, and arpeggios.

### Generator (rule-based, pure functions, deterministic)

Each session is a **priority-ordered list of blocks**, cut to the available minutes. Block position (playing order) is separate from priority (what is dropped first).

- Playing order: long tones, then scales, then new material, then play for fun.
- Drop order when time is short: new material first. Play for fun is protected.
- Starting time split (placeholder values, in `assets` config, not in code):

| Session length | Long tones | Scales | New material | Play for fun |
|---|---|---|---|---|
| 10 min | 3 | 4 | none | 3 |
| 20 min | 4 | 6 | 5 | 5 |
| 30 min | 5 | 8 | 9 | 8 |

For other lengths, interpolate and round sensibly.

- **Embouchure endurance** from onboarding caps the longest continuous playing block. Insert rests (default 60 s) between blocks, and break a block into chunks with rests if it exceeds the cap.
- Adaptation rules (the owner's own training rules):
  - Two clean runs in a row add 5 BPM for that scale.
  - A new scale unlocks when the last two scales are solid.
  - Long tones grow only if the player did not tire early (a stamina ramp). Tuner drift data can also drive this.
- **Focus** (pick up to two: tone, technique, repertoire, reading, theory) shifts extra minutes to the matching block type. The rank system's weakest pillar (the "limiter", section 9) also gets extra time: Sound adds long-tone minutes, Technique adds scale minutes, Range adds a range-extension long-tone exercise, Repertoire adds new-material minutes, Musicianship adds phone drills, Consistency suggests shorter planned sessions (a short session done beats a long one skipped).
- **Phone drills** are scheduled as separate short sessions (default 3 a week, 5 minutes each), capped so their total never exceeds 20 percent of the weekly planned sax minutes. The generator schedules whichever drills exist at that point in the build.
- Program content informed by BetterSax-style practice advice (long tones, scales, solo practice): write **original** exercise descriptions as data files. Do not copy BetterSax text.

### Onboarding (one question per page, re-runnable from settings)

1. Days of the week available, and minutes per session.
2. Embouchure endurance (how long before the lips tire): under 3 min, 3 to 5, 5 to 10, 10 to 20, over 20.
3. Longest long-tone hold in seconds, and which registers (low, middle, high).
4. Which scales are played cleanly (multi-select across the 12 majors and minors) and a comfortable BPM for them.
5. Reading level (cannot read, reads slowly with note names, reads in C, reads accidentals, sight-reads simple tunes).
6. Theory level (similar scale of five).
7. Songs played now, and songs wanted next (free text, or pick from the library). These become song goals.
8. Focus, up to two.
9. Settings step: language (FR or EN), note names, theme.

Answers seed the player state (scales, BPM, hold time, reading and theory levels) and the program. The user can then accept the generated program or choose "build manually".

### Session player

- Today screen shows the planned session with one big Start button (two taps from launch).
- The player shows the current block, a countdown, the target (BPM, hold time, scale, piece section), and Next, Pause, +1 min, and a **"lips tired" button** that ends the block early, marks tiredness, and enforces the rest. Use monotonic timestamps (`SystemClock.elapsedRealtime`), not tick counters, so the timer survives interruptions.
- Scale blocks: metronome panel with the target BPM and a "clean run" button. Two clean runs in a row raises the BPM for next time (and offers an immediate bump).
- Long-tone blocks: optional tuner panel (section 7).
- **Stand mode**: landscape, large text, readable from a music stand. Keep the screen on during a session.
- Optional "silence notifications during sessions" using Do Not Disturb. It needs notification-policy access, so it is a setting that asks for the permission only when turned on. Restore the previous state when the session ends, and also on the next launch if the app was killed mid-session.
- End of session: planned vs actual per block, a 1 to 5 tiredness rating, an optional note, saved to the log.
- Home-screen widget (Glance): "Start today's session".

### Goals

Four types, progress computed from logs: **time** (weekly minutes), **tempo** (scale X at BPM Y by a date), **song milestones** (sections of a piece reaching a stage), **recordings** (record a benchmark take of an exercise or piece by a date). Calm completion state, no pressure language.

## 7. Audio: tuner, tone, metronome, drone

### Pure domain

- **YIN pitch detector in pure Kotlin**: difference function, cumulative mean normalised difference, absolute threshold, parabolic interpolation, a confidence value. Parameters are yours to choose and justify in `docs/DECISIONS.md`. Starting point: 44.1 kHz mono, window about 2048 samples (about 46 ms), hop 512, threshold about 0.10 to 0.15, search range about 100 Hz to 1500 Hz (it must cover the lowest alto note, concert D♭3 about 139 Hz, up into altissimo), an RMS gate, and median smoothing over a few frames.
- **Hold analysis** (pure function over a stream of pitch frames):
  - A hold starts when the signal is above the gate with enough confidence for about 150 ms, and ends when it drops for about 200 ms.
  - Ignore the first 300 ms (attack) and the last 200 ms (release), where trackers are unreliable.
  - The reference note is the target note if the exercise sets one, otherwise the nearest semitone to the median pitch of the stable part.
  - Report: stable duration, share of time inside the tolerance, mean offset in cents, drift (end minus start, and slope in cents per second), direction (sharp or flat), min and max, and a downsampled trace (about 10 points per second) so thresholds can be recomputed later when the rank config changes.

### Android

- Microphone permission requested at first use only, with a clear explanation. Audio is analysed live and **never saved** by the tuner.
- **Tuner screen**: written note name (large), concert name small, a cents needle with a shaded in-tune zone, readout like "+12¢ sharp". After the hold, a **hold report**: the pitch trace over time with the ±10¢ band, "in tune for 9 of 12 s", and "ends 22¢ flat". Per-note history over weeks, so the player sees which notes they tend to play sharp or flat.
- Detection works in concert pitch, display uses written names. Reference pitch is adjustable.
- **Tone synth** (AudioTrack): a clean sine, plus a softer additive timbre option, at any concert pitch.
- **Match-the-note drill**: the app plays a reference note (the sounding pitch for the written note shown), the player matches it, the tuner confirms. Finite rounds, then done.
- **Metronome**: sample-accurate clicks generated in an AudioTrack buffer (no `Handler` timing), 30 to 240 BPM, accent on beat one, optional subdivisions, and a tempo trainer that raises the BPM after clean runs.
- **Drone**: holds a selectable pitch for long tones. Tell the user in the UI that the tuner needs headphones when a drone or metronome plays through the speaker, because the microphone would hear them.
- Make audio errors visible and recoverable (no silent failure if the mic is busy or permission is denied).

## 8. Reading, scale recognition, theory, fingering (phone drills)

- **Reading drill**: a note is drawn on the staff (written pitch, alto range, ledger lines, then accidentals as levels progress). The player answers with a note-name keypad (using the chosen note-name system) and an accidental toggle. Fixed-length rounds, accuracy and speed recorded, difficulty adapts.
- **Scale recognition (visual first)**: name the scale from a key signature or from the notes shown, or **build** the scale from a given root using the whole-step and half-step pattern. A by-ear version using the tone synth is a later addition inside the same screen if time allows.
- **Theory lessons**: at least 12 original lessons, each 2 to 3 minutes of reading plus a short quiz. Topics: the staff and clef, note values, time signatures, key signatures, the circle of fifths, the major scale pattern, natural minor, intervals, rhythm and syncopation, dynamics and articulation, why alto sax is transposing (written A sounds as concert C), ledger lines and altissimo notation, an introduction to modes. Content is data files with English and French fields.
- **Quizzes**: one question at a time, hard rather than easy, with **free-form answers** (typed, or constructed by tapping notes on a staff or building a scale). No four-option multiple choice.
- **Fingering trainer**: note to fingering diagram, drawn natively as a schematic of the alto keys, as a drill (show the note, tap the keys; or show the diagram, name the note). Fingering data as a data file. Standard-range fingerings must be correct. Altissimo fingerings vary by instrument and player: include only ones you are confident about, mark every entry `verified: false`, and show a visible notice "check against a trusted chart or your teacher".

## 9. Rank system

Eight tiers (internal number 1 to 8): **Iron, Bronze, Silver, Gold, Platinum, Emerald, Diamond, Master.** Each tier except Master has four divisions, IV down to I. Tier 0 is "Unranked" (before the Iron requirements of a pillar are met). All thresholds live in `assets/rank/rank_config.json`, never in code.

### Pillars

| Pillar | What it measures |
|---|---|
| Consistency | Adherence to your own plan over a rolling 8 weeks. The only pillar that can fall. |
| Sound | Long-tone hold length and time in tune, measured by the tuner. |
| Technique | Number of scales at a given speed, full range, up and down. |
| Range | Lowest to highest playable note, including altissimo. |
| Repertoire | Hardest piece played cleanly at tempo. |
| Musicianship | Reading, scale recognition and theory results. |

### Thresholds (placeholders, tune later)

| Tier | Technique (major scales, BPM in eighth notes) | Range (alto, written) | Sound (hold length, tolerance) |
|---|---|---|---|
| Iron | 1 scale, any tempo | Middle register | 5 s, ±30¢ |
| Bronze | 3 scales at 70 or more | + low register to low B♭ | 8 s, ±25¢ |
| Silver | 6 scales at 80 or more | + high register to palm-key D | 12 s, ±20¢ |
| Gold | 12 scales at 90 or more | Full range to high F♯ | 16 s, ±15¢ |
| Platinum | + natural minors at 100 or more | Altissimo G | 20 s, ±12¢ |
| Emerald | Majors + minors at 110 or more | To A or B♭ | 25 s, ±10¢ |
| Diamond | All at 130 or more, plus modes | To C | 30 s, ±8¢ |
| Master | All at 150 or more, plus arpeggios | Controlled upper altissimo | 35 s, ±8¢ |

Other pillars (placeholders, put them in the config):

- **Consistency**: adherence = completed planned sessions divided by planned sessions over the last 56 days, capped at 100 percent. Tier thresholds 25, 35, 45, 55, 65, 75, 85, 90 percent. From Gold up, also require an average of at least 3 sessions per week (so planning less cannot game it).
- **Repertoire**: hardest piece at stage "clean at tempo" mapped by difficulty band: Iron 1, Bronze 2, Silver 3 to 4, Gold 5, Platinum 6, Emerald 7, Diamond 8 to 9, Master 10.
- **Musicianship**: define sensible thresholds from reading accuracy and speed, scale-recognition accuracy, and theory lessons completed with quiz results. Document your choices in `docs/DECISIONS.md`.
- **Sound** details: Iron is satisfied by completing long-tone blocks (self-reported). Bronze and up need tuner holds: at least 3 distinct notes, each hold at least the tier length with at least 80 percent of the time inside the tolerance. From Gold up the holds must cover the low, middle and high registers.
- **Range** details: a claimed note is recorded with a flag, self-reported or tuner-verified. Altissimo tiers (Platinum and up) need at least one **tuner-verified** hit of the note, and the overtone milestone (section 10) completed first. Self-report alone does not count there.
- **Technique** counts only "clean" runs: full range up and down, no mistakes, two in a row, at the logged BPM.

### Rules

- A pillar's tier is the highest tier whose requirements are **all** met.
- **Overall rank** = average of the six pillar tiers, rounded down. The division comes from the fractional remainder (four equal bands, IV lowest to I highest). An average of 8 is Master, with no divisions.
- **Musicianship cap**: its tier counts at most one tier above `floor(average of the other five pillars) + 1`. Phone drills cannot carry the rank.
- The **limiter** is the lowest pillar (ties broken in this order: Sound, Technique, Range, Repertoire, Consistency, Musicianship). It is shown and fed to the generator.
- Skill pillars never decay. Only Consistency is rolling.
- The calculation is a **pure function** `computeRank(events, config, now)`, fully unit-tested, with transparent output (what each pillar needs for the next tier).
- No rank notifications. No social features.
- **UI**: a rank "staircase" (eight ascending bars with the current tier highlighted, Master in an accent colour) and a **six-axis radar chart** (Consistency, Sound, Technique, Range, Repertoire, Musicianship) with the limiter highlighted, both drawn with Compose Canvas. Tapping a pillar shows exactly what the next tier requires and how close the player is.
- Anti-gaming: self-reported data is flagged as such, optional recordings can be attached as evidence, tuner-measured data is marked verified.

## 10. Altissimo and overtone verification, slow-down player, scale checker

- **Overtone drill**: the app shows a target partial (for example a low note fingering overblown to the 2nd or 3rd partial). The tuner checks that the detected frequency matches the expected partial within a tolerance, and marks the milestone verified.
- **Altissimo verification**: the app shows the target written note (G6 and up). The player holds it for about 2 seconds. A match within a generous tolerance writes a verified Range entry. Detection quality on real altissimo is uncertain (harmonically rich, can be unstable): list this in `docs/DEVICE_CHECKS.md`.
- **Slow-down and loop player**: the player picks one of their own audio files (storage access framework). Playback speed from about 50 to 100 percent **without changing pitch** (Media3), A–B loop, named loop points. Files stay where they are, only references are stored.
- **Scale checker (experimental, behind an Experimental toggle in settings)**: listens to a scale played, segments notes from the pitch stream (hysteresis, minimum duration), compares with the expected note sequence, estimates the speed in notes per minute. It shows its result and **asks the player to confirm** before logging. It never writes rank data on its own. Unit-test it with synthesised audio, and label it clearly as experimental.

## 11. Sheet music library

### Score format and pieces

- Pieces are JSON files in `assets/pieces/`: metadata plus a compact single-voice score (measures, notes with concert pitch and proper spelling, durations as fractions, ties, rests, key and time signature, tempo).
- Metadata: id, title, composer or source, **licence**, credit line, key, time signature, tempo, note range, difficulty (1 to 10, computed), duration, tags, `reviewed` (false until the owner proofreads it), `bundled` (true or false), sections (for milestone goals).
- **Difficulty** is computed by a documented pure function from: note range span and extremes, key signature size and accidentals, shortest note value and rhythmic variety (dotted notes, ties, syncopation), tempo, and length. Unit-test that known easier pieces score lower than known harder ones.
- **Content to bundle**: about 30 pieces spread across difficulty 1 to 10, mixing scale-based etudes, long-tone and slur etudes, and short melodies. Use only (a) originals you compose, licensed CC0, and (b) traditional or pre-1900 public-domain melodies that you can reproduce **exactly**. If you are not sure of a melody, compose an original instead. Never include copyrighted melodies. Mark everything `reviewed: false` and list it in `docs/CONTENT.md` so the owner can proofread.
- One **metadata-only** entry as an example of the mechanism: "Little Root Town" (Pokémon), `bundled: false`, licence "copyrighted", no notes. The owner can attach their own file to it.
- **User imports**: a **PDF** (shown with `PdfRenderer`) or a **MusicXML** file (monophonic part parsed into the same score format, playable and transposable). Stored locally and included in backups.
- Record the licence and credit line of every bundled item. For a future published version, bundle only CC0, public domain and CC BY material by default.

### Matching to the player

- A piece unlocks when its note range fits the player's range **and** the scale of its key (or relative minor) is clean.
- States: Locked (shows exactly what is missing), Stretch (one requirement missing by a small margin, such as the range exceeded by at most 2 semitones, or the next scale to unlock), Available, Learning, Playable, Clean at tempo.
- The generator picks "something new" from Stretch and Available pieces not yet started, and "something fun" from Playable pieces played least recently.
- Piece view: the staff (written alto by default, toggle for concert), tempo, sections with stages, a **Listen** button that plays the melody with the tone synth at a chosen tempo, and the stage buttons.

## 12. Recordings, reed and gear log, weekly review

- **Recordings**: record with `MediaRecorder` (AAC in m4a) to app-private storage, tagged with the exercise or piece, the reed and the mouthpiece. A **benchmark** is a recording of the same exercise or piece repeated monthly, and the recordings goal type refers to it. Side-by-side playback of two takes, with an A/B toggle. Attach a recording as evidence for a clean run or a piece stage.
- **Reed and gear log**: reeds (brand, strength, date started), mouthpieces, and the current reed. Hours per reed come automatically from session minutes. A mouthpiece comparison is two tagged recordings side by side. No reminders unless the user opts in.
- **Weekly review**: planned vs actual sessions and minutes per block type, what was skipped and on which days, BPM progress per scale, tuner stats (average time in tune, typical drift), rank changes. Text is generated by rules, in English and French, plain and direct.
- **Weekly summary export**: one tap copies a plain-text summary to the clipboard and offers the share sheet. No backend. It is meant to be pasted into a chat with an assistant or sent to a teacher.
- **Reminders** (opt-in, off by default): at most one per planned day, neutral wording, via WorkManager, and `POST_NOTIFICATIONS` is requested only when the user turns them on.

## 13. Data, backup, settings, localisation

- Room database with exported schemas. A clean DTO layer, independent of Room, used for backup so the round trip can be tested on the JVM.
- **Backup**: a `.zip` containing `backup.json` (with a `schemaVersion`), `recordings/` (optional), `imports/` (user pieces and PDFs). Export and import through the storage access framework. Import validates the version, and replaces the current data after a confirmation. Write and test a migration policy for future versions.
- **Draft entities** (refine as needed): Program, Day, Block, Exercise, Target, Session, SessionBlockLog, Goal, Piece, PieceSection, PieceProgress, TunerHold, RankEvent, RankSnapshot, Recording, Reed, Mouthpiece, DrillResult, LessonProgress, Settings.
- **Settings**: language (FR or EN, default follows the system), note names, theme, reference pitch, instrument (alto, fixed for now), notification silencing, reminders, rest length, experimental features, re-run onboarding, backup export and import.
- **All user-facing strings in English and French** from the start (`values/strings.xml` and `values-fr/strings.xml`), and bilingual fields in content data files. No hard-coded strings in composables.
- Permissions the app may use: `RECORD_AUDIO` (runtime, at first use), `POST_NOTIFICATIONS` (only if reminders are on), `ACCESS_NOTIFICATION_POLICY` (only if silencing is on). **No `INTERNET`.**

## 14. Build order, checkpoints and definition of done

Work in this order. Each phase ends with: build green, unit tests green, commit, update `docs/PROGRESS.md`, tick the README checklist.

### Phase 1: Core
Project scaffold, theme, navigation, localisation (EN and FR), settings and DataStore, domain models, Room, onboarding, generator, program model and manual editor, session planner, session player (timer, blocks, rests, fatigue guard, stand mode, keep-awake, optional Do Not Disturb), session log and history, time and tempo goals, backup export and import, Today screen.

### Phase 2: Listening and reading
Audio core (capture, YIN, tone synth, metronome, drone), long-tone tuner (live, hold report, per-note history), match-the-note drill, metronome and drone wired into the session player and the tempo trainer, the staff renderer, reading drills, weekly review and text export, home-screen widget, opt-in reminders.

### Phase 3: Progress
Rank system (calculation, config, staircase, radar, limiter, pillar detail), library (score format, difficulty scoring, matching, the bundled pieces, piece view with Listen, piece stages, song-milestone goals, MusicXML and PDF import), recordings with benchmarks, reed and gear log. The generator uses the limiter and the library.

### Phase 4: Learning content
Theory lessons (at least 12, bilingual, with free-form quizzes), scale recognition drills, fingering trainer with its data and diagram renderer, phone-session scheduling with the 20 percent cap, full Musicianship inputs.

### Phase 5: Advanced listening and polish
Overtone and altissimo verification, slow-down and loop player, the experimental scale checker, accessibility pass, empty states and error states, final cleanup, final docs.

### Required unit tests (at minimum)

- Transposition: written and concert for every note in the alto range with correct spelling, and key signatures (concert C to written A, concert E♭ to written C, and others).
- Scale generation for all keys and modes.
- Generator: the 10, 20 and 30 minute splits, the drop order when time is short, the endurance cap and rests, focus and limiter adjustments, determinism.
- Adaptation: +5 BPM after two clean runs, the new-scale unlock, the long-tone growth rule.
- Session planner resolving a program template into a concrete session.
- YIN: synthesised sines at every semitone from about 138 Hz to about 1400 Hz, detuned by up to ±50¢, with and without noise, and harmonic-rich waveforms, asserting a few cents of accuracy and no octave errors.
- Hold analysis: a steady note, a drifting flat note, a wobbling note, attack and release trimming, silence.
- Scale checker segmentation on synthesised audio.
- Rank: unranked state, each pillar tier, the overall average and divisions, the Musicianship cap, the limiter and tie-break, the consistency window and the sessions-per-week floor, verified-only altissimo, rank recomputed from stored hold traces after a config change.
- Difficulty scoring ordering, and library matching states.
- MusicXML import of a small monophonic file.
- Backup round trip (export, wipe, import, identical data) and version validation.
- The architecture test (no `android.` imports in `domain/`).

### Phase 1 acceptance checks (the owner will verify these by hand)

- From a cold start, today's session starts in two taps.
- Generating for 10, 20 and 30 minutes gives the time split in section 6, and new material disappears first when time is short.
- A manually built program and a generated one open in the same editor.
- Export, wipe the app data, import: everything is identical.
- Switching the note-name setting changes every note label. Switching language changes every string.

## 15. Out of scope on purpose

Leaderboards, streak notifications, XP or points, accounts and cloud sync, in-app AI, tenor or other saxophones, anything needing the network.

## 16. Final report (after phase 5)

Give a short report: what was built per phase, what you verified (build, which tests passed, lint), what could only be checked on a device, what is not done and why, and the open items the owner should decide. Keep `README.md` accurate: how to build, run, test, and where everything lives.
