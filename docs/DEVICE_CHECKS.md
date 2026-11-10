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
