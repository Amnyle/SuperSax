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
