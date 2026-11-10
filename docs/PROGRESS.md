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
