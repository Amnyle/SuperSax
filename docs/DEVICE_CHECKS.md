# Device checks

Things that can only be verified on a real phone. No emulator was available while building (no KVM in the
build container), so **every UI flow below is unverified on a device** until the owner checks it.

## Phase 1
- [ ] Cold start → Today → Start: the session runs after two taps (app icon, Start).
- [ ] Session timer keeps correct time after locking the screen, switching apps, and after force-stopping the app mid-session (it resumes on the right step).
- [ ] Stand mode turns the screen to landscape with large text; the screen stays on during a session.
- [ ] "Silence notifications": first toggle opens the Do Not Disturb access screen; during a session notifications are silenced; afterwards the previous mode is back; if the app is killed mid-session, the previous mode is restored at next launch.
- [ ] Switching the language in Settings changes every string (activity recreates); switching note names changes every note label.
- [ ] Backup: export to a file, clear app data (system settings), import: all sessions, programs, goals, settings back.
- [ ] Font scale at 200 percent: no clipped buttons on Today, the player and the summary.

## Phase 2
- [ ] Tuner: needle follows a held note smoothly; reading matches a trusted tuner within a few cents across low B♭ to high F♯ (written).
- [ ] Tuner latency feels immediate (70 ms window + 5-frame median ≈ 130 ms).
- [ ] Microphone denied → visible message with "Open settings"; mic busy (e.g. during a call or another recorder) → visible message with Retry.
- [ ] Drone and metronome through the speaker are heard by the microphone: the warning is shown; with headphones the tuner is clean.
- [ ] Metronome sounds steady at 30, 120 and 240 BPM with no drift against a reference metronome over 2 minutes; accent and subdivisions audible.
- [ ] Reference tones and drone are at the right pitch (check with another tuner), including after changing the reference pitch (e.g. 442 Hz).
- [ ] Staff: treble clef, key signatures, accidentals, ledger lines, flags, dots, ties and rests are placed correctly (compare with printed music) at several font scales.
- [ ] Reading drill and match-the-note end after a fixed number of questions with the "done for today" screen.
- [ ] Widget: add it to the home screen; one tap opens the player with today's session running.
- [ ] Reminders: enable, set a time a few minutes ahead on a planned day → one neutral notification; nothing on unplanned days or after playing.

## Phases 3 and 4
- [ ] Rank screen: staircase and radar readable in light and dark mode; tapping a pillar lists the next-tier requirements.
- [ ] Library: Listen plays every bundled piece at the right pitch with the note highlight following; written/concert toggle redraws correctly (check a double sharp in "caprice-minor").
- [ ] MusicXML import of a real file exported from MuseScore (alto part with transposition) shows the same written notes as MuseScore.
- [ ] PDF import and viewing of a multi-page score; the file survives backup export/import.
- [ ] Recording: start/stop, playback, A/B comparison stays in sync; files under app storage; included in backup when chosen.
- [ ] Fingering diagram: keys are easy to tap with a finger; key positions make sense to a sax player (owner to judge).
- [ ] Lesson quizzes: the tap-on-staff input picks the intended line or space at normal and large font sizes.

## Phase 5
- [ ] Altissimo: detection of real altissimo notes (harmonically rich, can be unstable) on written G6, A6, C7 with the owner's horn; note how often a correctly played note fails to verify.
- [ ] Overtones: the 2nd and 3rd partials of low B♭, C and D are recognised; a wrong partial shows "another partial is sounding".
- [ ] Slow-down player: 50 percent speed keeps the pitch (compare with a tuner); A–B loop wraps without a gap you notice; a file moved after adding shows a clear error.
- [ ] Scale checker (Settings → Experimental): with a real scale at 60–120 BPM, how often the "looks clean" verdict matches what the owner heard. It must never log without the confirmation tap.
- [ ] TalkBack: main buttons, the cents needle, staff and charts read sensible descriptions.
