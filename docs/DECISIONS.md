# Decisions

Decisions taken where the spec (`docs/SPEC.md`) is silent. One line of reasoning each.

## Tooling and build

- **Versions**: AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.13, Gradle 9.9.1, compileSdk/targetSdk 37, Compose BOM 2026.09.00, Room 2.8.5, Navigation 2.10.2, DataStore 1.3.0, Media3 1.11.1, Glance 1.2.0, WorkManager 2.12.0: the latest stable releases on Google Maven / Maven Central on 2026-11-10, verified by building.
- **AGP 9 built-in Kotlin**: no `org.jetbrains.kotlin.android` plugin; the Compose compiler plugin pins the Kotlin version.
- **Gradle configuration cache off**: KSP 2.3.13 fails to serialise its classpath into the cache on this Gradle; a slower configuration phase is a fair price.
- **Maven Central mirror**: `settings.gradle.kts` lists Google's mirror of Maven Central before Central itself, because Central answered 429 (rate limit) during builds.
- **Unit tests read real assets**: `src/main/assets` is added to the test resources, so tests check the shipped config, pieces and lessons, not copies.
- **`android:allowBackup=false` + data extraction rules excluding everything**: the spec wants local data only; the app's own zip backup is the backup path.

## Architecture

- **Package layout as in the spec**, plus `domain/common` (Room-independent records shared by repositories and backup) and `data/repo/SessionManager` (the running session lives outside any screen).
- **Records instead of a third DTO layer**: domain `*Record` classes are the DTO layer; Room entities map to them; backup serialises them. This keeps "independent of Room" while avoiding a duplicate class per table.
- **Program stored as one JSON document per row**: a program template is always edited and resolved as a whole; normalising days/blocks/exercises/targets into 5 tables would add joins and no queries.
- **Player state and onboarding answers** live in a key-value table (`kv`) as JSON: one row each, versioned with the backup.
- **Settings in DataStore as one JSON document** (same shape as in the backup), read blocking only in `attachBaseContext` to apply the language before the first frame.
- **Language switch** wraps the activity context and recreates the activity; works on every API from 26 without AppCompat.
- **Architecture test** also forbids `androidx.` imports in `domain/` (stricter than the spec).

## Pitch and notation

- **Scale ids are stored in concert pitch** (e.g. `MAJOR:Eb` is what the player sees as written C major), shown with written names everywhere.
- **Concert keys normalised to 6 flats .. 5 sharps** so the 12 written alto keys are D♭..F♯ (at most 6 accidentals).
- **Written keys above six sharps are respelled** with the diminished seventh (enharmonic twin of the major sixth): concert E major is written D♭ major, not C♯ major.
- **Default chromatic spelling for detected notes**: C C♯ D E♭ E F F♯ G G♯ A B♭ B.
- **Alto register boundaries** (written): low B♭3..C♯5, middle D5..C♯6, high D6..F♯6, altissimo G6 and up.

## Generator and planner

- **Interpolation**: linear between the table rows, scaled from the nearest row outside the table, then largest-remainder rounding so the total is exact (ties go to the earlier block in playing order).
- **Focus and limiter shifts**: 10 percent of the session each (config), taken from donors in drop order (new material first); play for fun never donates; a block that already received minutes does not give them back to a second focus.
- **Reading and Theory focus** add a phone-drill session per week instead of sax minutes; the Musicianship limiter does the same.
- **Consistency limiter** adds a suggestion (session length × 0.67, rounded to 5 minutes) shown on Today; it never changes the program on its own.
- **Short on time**: the first kind in the drop order goes entirely, then the rest shrinks proportionally (each block at least 1 minute), then the next droppable kind goes. Cutting a 20-minute day to 10 minutes gives exactly the 10-minute row.
- **Rests are added on top of the planned playing minutes** (the split table sums to the session length); Today shows the total including rests.
- **Endurance chunking** limits continuous playing, also across consecutive exercises of a block; rests between blocks are always inserted.
- **Block playing order is fixed by block kind** (breathing, long tones, scales, new material, play for fun), also in manual programs; this keeps planner output predictable.
- **Scale rotation**: least recently practised unlocked scale first; a 6+ minute scale block gets a second ("review") scale.
- **Unpinned explicit targets**: a manual exercise can name a scale without pinning; the tempo still follows progression. Pinning freezes tempo / hold time.
- **Solid scale** = a clean pair was played at or above 60 BPM (config). The unlock order (config) follows written keys by increasing accidentals, alternating majors and relative minors.
- **Long-tone growth**: +1 s when the long-tone work was not cut short by "lips tired", tiredness was 3 or less and (when tuner data exists) mean absolute drift was under 20 cents; tiring early lowers it by 1 s.
- **Stamina ramp**: the endurance cap grows 30 s after 3 sessions in a row without "lips tired".
- **Phone drill days** are spread over the week (Tue/Thu/Sat for three); a shorter last session (at least 3 minutes) is allowed to use the 20 percent budget.

## Session player

- **Timer** stores step start and pauses in `SystemClock.elapsedRealtime()` and is persisted after each change; on restart it catches up (steps whose time ran out are logged at full length).
- **Lips tired** ends the step, inserts (or extends) a rest of the configured length and makes it unskippable until it runs out.
- **Immediate tempo bump** after two clean runs applies to the current and later steps of the same scale.
- **Do Not Disturb** uses `INTERRUPTION_FILTER_ALARMS` (alarms still ring) and the previous filter is kept in DataStore so it is restored at the next launch if the app was killed.
- **Tiredness and note** are saved with the session; a session can also be discarded from the summary.

## Goals

- **Tempo goal progress** = best clean pair BPM for that scale / target BPM.
- **Song goals from onboarding** are created by title; they can be linked to a library piece later.
- **Deadlines** are entered as "weeks from now" (a stepper), simpler than a date picker for a music stand.

## Backup

- **Migration policy**: `schemaVersion` bump + one JSON-rewrite step per version in `BackupCodec.migrations`; newer files are refused, unknown fields ignored, missing fields defaulted. Tested with a simulated v2.
- **Import is staged**: the zip is read and validated into a cache folder first; data is replaced only after the confirmation dialog, in one Room transaction.
- **Recordings are optional in exports**; importing a backup without recordings keeps the files already on the phone.

## Audio (phase 2)

- **YIN window 3072 samples (70 ms) at 44.1 kHz, hop 512 (11.6 ms)**: the lowest alto note (concert D♭3, 139 Hz) gets about ten periods, which made low notes stable in noise; latency stays acceptable for long tones.
- **Difference function by FFT** (d(τ) = e1 + e2(τ) − 2·r(τ), r by FFT): O(W log W) per frame instead of O(W·τ), so the longer window is cheap.
- **Search 100–1500 Hz, threshold 0.12, RMS gate 0.01 (≈ −40 dBFS)**; confidence = 1 − d′(τ); frames under 0.8 confidence are ignored by the tuner and hold analysis.
- **Dip refinement**: in noise, d(τ) has wiggles and the classic "walk down to the local minimum" can stop a few lags early (measured: 10+ cents at 139 Hz with 18 dB SNR). The minimum is now searched within ±τ/16, then a least-squares parabola is fitted over a neighbourhood that grows while d stays quadratic (rise under 20 percent of the signal energy). Measured worst errors over every semitone 139–1397 Hz detuned ±50 cents: pure sine 0.3¢, harmonic-rich 0.7¢, strong 2nd harmonic 0.3¢, 24 dB SNR noise 0.4¢, 18 dB SNR noise 1.4¢; no octave errors.
- **Median smoothing over 5 frames**, in log-frequency, reset on silence.
- **Hold analysis**: onset 150 ms, release gap 200 ms, attack trim 300 ms, release trim 200 ms, tolerance ±10¢ by default, trace at 10 Hz; drift = mean of the last 0.5 s minus mean of the first 0.5 s (or a third of the hold if shorter); direction from the mean offset with a ±tolerance/2 dead zone.
- **Tuner holds of at least 1 s within ±50¢ write a tuner-verified Range note** (the written note), used by the Range pillar.
- **Microphone source**: `UNPROCESSED` (no AGC or noise suppression) when the device supports it, otherwise `VOICE_RECOGNITION`.
- **One output AudioTrack (float, mono, 44.1 kHz, low-latency mode)** mixes metronome, drone, reference tones and melody playback; the thread stops after ~0.2 s of silence.
- **Metronome clicks** are 25 ms decaying sine bursts (2000 Hz accent, 1300 Hz beat, quieter subdivisions), written at exact sample positions (fractional position carried between buffers).
- **Soft timbre** = fundamental plus harmonics 2–5 at 0.45, 0.22, 0.10, 0.05.
- **Match-the-note**: within ±25¢ for 1 s counts; 8 notes per round from the player's range, limited to written C4–C6.

## Reading, notation, review (phase 2)

- **Staff renderer**: pure layout in staff spaces (`notation/StaffLayout.kt`, unit-tested) + a Canvas view; Bravura glyphs drawn as text at 4 staff spaces per em. Eighth and sixteenth notes use flags (no beams), which is correct notation and simpler; non-drawable durations are split into tied notes.
- **Reading drill**: 15 notes per round; levels 1 staff only (E4–F5), 2 one ledger line (C4–A5), 3 whole alto range, 4 accidentals in the middle, 5 everything; up after 90 percent with mean answer under 4 s, down under 60 percent. E♯, B♯, F♭, C♭ are not asked. The answer must match the spelling.
- **Phone drills are logged twice**: a drill result (for Musicianship) and a phone session (for history and the weekly review). Consistency counts sax sessions only.
- **Weekly review**: planned = the active program's days up to today; skipped = planned days with no sax session; BPM progress = best clean pair this week vs before; numbers only, no encouragement wording.
- **Reminders**: one WorkManager periodic job per day at the chosen time; it posts only if today is a planned day and nothing was played yet.
- **Widget**: a Glance widget whose tap opens the app with a start flag; the app starts today's session and opens the player (one tap from the home screen).
- **App bundle language splits disabled**: the language is chosen inside the app.

## Rank (phase 3)

- **"Highest tier whose requirements are all met"** is taken literally: lower tiers are not required (e.g. Sound can be Gold from tuner holds even without the Iron long-tone block).
- **Technique**: Platinum = all 12 majors at 90+ and all 12 natural minors at 100+; Diamond adds 12 modes (any mode, any tonic) at 130+; Master adds all 24 arpeggios at 150+. Clean pairs come from the session log (two clean runs in a row), self-reported.
- **Range** (written MIDI): Iron lowest ≤ E4 and highest ≥ D5 (the middle register); Bronze ≤ B♭3, ≥ G5; Silver ≥ palm D6; Gold ≥ F♯6; Platinum ≥ G6; Emerald ≥ A6; Diamond ≥ C7; Master ≥ D7 ("controlled upper altissimo"). From Platinum only tuner-verified notes count, and the overtone milestone must be done. Onboarding answers become self-reported low/high notes; tuner holds ≥ 1 s become verified notes.
- **Sound Iron** = one long-tone block completed (actual ≥ 75 percent of planned). Bronze+ re-evaluates stored traces at the tier tolerance, so changing the config re-ranks old holds. Registers for Gold+: low < written D5, middle D5–C♯6, high D6 and up.
- **Consistency**: planned days of the active program counted day by day over the last 56 days, starting at the first sax session if more recent; today counts only once played; a planned day is "done" if any sax session happened that date. Sessions per week = sax sessions in the window / weeks in the window. Phone sessions do not count.
- **Repertoire**: whole-piece stage "clean at tempo" only (sections do not count).
- **Musicianship thresholds**: Iron any drill or lesson; Bronze reading level 2 at 80 percent + 2 lessons ≥ 60 percent; Silver reading 3 at 80 + scale recognition 70 + 4 lessons ≥ 70; Gold reading 4 at 85 under 4 s + scales level 2 at 80 + 6 lessons; Platinum reading 5 at 85 under 3.5 s + scales level 3 at 85 + 8 lessons ≥ 75; Emerald 90 / 3 s, scales level 4 at 90, 10 lessons ≥ 80; Diamond 95 / 2.5 s, scales 95, 12 lessons ≥ 85; Master 100 / 2 s, scales 100, all 13 lessons ≥ 90. A requirement is met by a single round (best round counts).
- **Musicianship cap** = floor(average of the other five) + 1.
- **Snapshots**: one rank snapshot stored whenever the rank or any pillar changes (for the weekly review); never notified.

## Library (phase 3)

- **Piece files in compact text measures** (`Eb4:1/4`, `r:1/2`, `~` tie): exact fractions, readable, validated by `tools/validate_pieces.py` and by unit tests.
- **Difficulty** uses the written score (what the alto player reads): range span, extremes, written key size, accidentals, shortest note, dotted/ties/syncopation, notes per second, length; weights in `Difficulty` KDoc.
- **Matching**: a scale counts as clean when it has a clean pair (onboarding's "clean scales" seed one at the comfortable tempo). The key's scale or its relative counts. Stretch = exactly one requirement missing, by at most 2 semitones of range or being the next scale to unlock.
- **"Something new" continues the piece being learned** (most recently touched) before starting another, otherwise new material would change every session; then easiest Available, then easiest Stretch. Section = first section not yet playable. "Something fun" = playable piece played least recently.
- **MusicXML import**: first part only, first note of chords, grace notes and other voices skipped; `<transpose>` converts written parts to concert. DTD loading and external entities are disabled.
- **PDFs** are copied into app storage (`imports/`, included in backups) and rendered page by page with `PdfRenderer` at 1400 px width.
- **Metadata-only pieces** take attached files (PDF or MusicXML) linked by `attachedToPieceId`.
- **Listen** merges tied notes and uses the score tempo (beats of 1/den) or the chosen tempo.

## Recordings and gear (phase 3)

- **Recorder**: MediaRecorder, MIC source, AAC 128 kb/s 44.1 kHz in .m4a under `files/recordings/`.
- **A/B comparison**: both takes play in sync, the toggle switches which one is audible (volume 1/0), so the comparison is at the same point in time.
- **Evidence**: a take tagged with a piece attaches to that piece's stage; a take tagged with a scale attaches to the latest clean pair of that scale.
- **Hours per reed** from the session log (the current reed is stored with each session).

## Learning (phase 4)

- **Lessons**: 13 lessons written by a content subagent from a format spec (`docs/LESSON_FORMAT.md`), validated by script and by unit tests that check every answer is accepted by the app's checker. French uses "tu" and solfège names with the app's octave codes in brackets.
- **Quiz answers**: TEXT matching ignores case, accents and punctuation; NOTE/NOTES use the note keypad (spelling matters); STAFF_NOTE uses a tappable staff plus ♭/♮/♯. The best score per lesson is kept, attempts counted.
- **Scale recognition** levels: accidentals ≤ 1, 2, 4, 6, 6; minors from level 3, modes at level 5; questions rotate between "name from key signature", "name from notes" and "build from root". By-ear questions were not added.
- **Fingering diagram**: a schematic, not a picture of a real sax; standard fingerings follow common charts; low B♭/B shown without the right-hand pinky.
- **Phone drills available**: reading, scale recognition, theory lesson, fingering; Today links each planned phone session to its screen.

## Advanced listening and polish (phase 5)

- **Overtone milestone** = all six targets verified: low D, C and B♭ fingerings sounding their 2nd and 3rd partials (expected = fingered concert frequency × partial, ±35¢, held 1 s). Each verified target is stored as a drill result, so progress survives restarts.
- **Altissimo check**: written G6–D7, ±40¢, held 2 s → tuner-verified Range note. The YIN search range (to 1500 Hz) covers written D7 (concert F6, 1397 Hz).
- **Slow-down player**: Media3 playback speed 50–100 percent in 5 percent steps, pitch unchanged; A–B loop enforced by polling every 50 ms; files are referenced by persisted SAF URIs (not copied); named loops are stored with the reference (and backed up; the files themselves are not).
- **Scale checker**: note changes need > 60¢ for 45 ms (hysteresis), notes under 70 ms are dropped, alignment by edit distance; "clean" only if every expected note is matched with nothing wrong, missing or extra. Expected = the scale over the player's range, up and down. It writes nothing until the player taps "clean run" or "mistake"; inside a running session the run joins that session, otherwise it is saved as a short session and goes through the normal adaptation rules (a confirmed clean pair is self-reported, like any other).
- **UI tests on the JVM**: Robolectric 4.17 (SDK 35 runtime) with Compose test rules runs the real app; tests fork one JVM per class because DataStore keeps one instance per process. Robolectric downloads its Android jar from the same Maven Central mirror as the build.
- **Render checks**: `RenderCheck` draws the staff and the Canvas widgets to PNGs (copies in `docs/renders/`); this caught clipped bar lines and too-wide note spacing, now fixed.
- **Fingering diagram orientation**: seen from the player (palm keys and the left pinky table on the left, side keys and right pinky keys on the right).
- **Phases 3 and 4 were committed together** because they share wiring (container, navigation) and were finished at the same time; the build and tests were green at that commit.

## In-app updates (after phase 5, at the owner's request)

- **Network use, against the original spec**: the owner asked for in-app updates "like SuperHealth", which needs `INTERNET` and `REQUEST_INSTALL_PACKAGES`. The only network call is the GitHub releases check and the APK download; the daily check can be turned off in Settings, and the privacy note says so.
- **Source**: GitHub releases of the public repository `Amnyle/SuperSax` (no account, no token), the same model as SuperHealth.
- **Safety checks before installing**: complete download, same package name, same signing certificate as the installed app, newer version; otherwise the file is deleted and a plain message is shown.
- **Install**: PackageInstaller session; on Android 12+ later updates can install without a prompt once Super Sax installed the current version; Android asks once to allow installs from the app.
- **Signing**: a dedicated release key (RSA 4096) kept out of the repository. GitHub Actions reads it from the secret `SAX_KEYSTORE_BASE64`; the keystore password is fixed in the workflow because the keystore file itself is the secret.
- **Versions**: CI publishes `v1.0.<run number>` with versionCode 100 + run number; the first hand-built release is 1.0.0 (versionCode 100). Comparison is numeric part by part.
- **Release trigger**: every push to `main` runs the unit tests, builds the signed APK and publishes a release.

## Java core, Kotlin UI (after phase 5, at the owner's request)

- **Split**: the owner wants "Java plus Kotlin", like SuperHealth. Java holds the engines and rules: `domain/pitch`, `scale`, `audio` (YIN, pitch tracker, hold analysis, metronome, synth, scale checker), `generator`, `planner`, `rank`, `library`, `drills` (checking and quiz), `goals`, `review`, `update` (release rules), the backup zip, `notation` layout and the Android `audio/` classes.
- **Stays Kotlin**: Compose screens and ViewModels, Room/DataStore/repositories (KSP and coroutines), the Glance widget, the updater worker (coroutines), and every `@Serializable` model (records, program, player state, session run state, configs, backup data) because kotlinx.serialization needs Kotlin classes.
- **Same sources folder**: Java and Kotlin both live in `src/main/java`; AGP compiles them together, so either side calls the other.
- **API shape for Java**: Kotlin `copy(...)` became `withX(...)` methods; default arguments became overloads (Kotlin callers lost named arguments on Java methods, so calls are positional or use a dedicated method, e.g. `AudioEngine.setBeatsPerBar`); objects became `final` classes with static methods; nullable values use `org.jetbrains.annotations.Nullable` so Kotlin sees `T?`. Java value classes implement `equals`/`hashCode` so `StateFlow` still skips duplicates.
- **Behaviour unchanged**: the port was done group by group, each committed only with all 96 unit tests green; the YIN accuracy numbers are identical to the Kotlin version. `InputStream.transferTo` is avoided (API 33) because minSdk is 26.

## Name

- **Super Sax**, application ID `app.supersax` (the owner had not installed a build yet, so the ID changed with the name).

