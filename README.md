# Sax Practice

An Android app (Kotlin, Jetpack Compose) that helps a beginner **alto saxophonist** set goals for, plan, run and
track practice. Local only: no account, no internet permission, no analytics, no ads.

The full specification is in [`docs/SPEC.md`](docs/SPEC.md).

## Build and run

Requirements: JDK 17+ (21 used), Android SDK with platform 37 and build-tools 37.0.0.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r` on that path.

## Progress checklist

- [x] Phase 1: Core
- [x] Phase 2: Listening and reading
- [x] Phase 3: Progress
- [x] Phase 4: Learning content
- [ ] Phase 5: Advanced listening and polish

Phase 1 acceptance checks (to verify by hand on a phone, see `docs/DEVICE_CHECKS.md`):
- [ ] From a cold start, today's session starts in two taps. (Implemented; unit tests cover the plan.)
- [x] 10, 20 and 30 minutes give the section 6 split, and new material disappears first when time is short (unit tests).
- [ ] A manually built program and a generated one open in the same editor. (Implemented: one `ProgramEditorScreen`.)
- [ ] Export, wipe the app data, import: everything is identical. (Round trip unit-tested on the JVM.)
- [ ] Switching the note-name setting changes every note label; switching language changes every string.

## Documentation

- `docs/PROGRESS.md` – what is done, verified, not done.
- `docs/DECISIONS.md` – decisions where the spec is silent.
- `docs/CONTENT.md` – author, source and licence of bundled content.
- `docs/DEVICE_CHECKS.md` – what only a real phone can verify.
