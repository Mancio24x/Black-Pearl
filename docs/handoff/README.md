# Handoff — start here (updated 2026-11-10, after release 0.6.0)

For the next coding agent. Read this folder after `AGENTS.md` and `docs/CODEMAP.md`.
Files: this README (state + next steps), `APP_LOCK_PLAN.md` (design of the app lock, now implemented — kept as
background), `RELEASE.md` (how to publish), `ENVIRONMENT.md` (tools, CI, gotchas), `OPEN_ISSUES.md` (known limits).

## The owner

- Leonardo (GitHub `Mancio24x`), Italian, **not a programmer**, usually on the phone (Claude Code Remote Control).
- Write to him in **Italian, plain words**, short; explain trade-offs and recommend one option (see `CLAUDE.md`).
- He **merges PRs himself** (ruleset bypass) and then writes "Fatto". When he says "pubblica", publish the release
  (see `RELEASE.md`). Do not publish without his request.
- He likes: Material 3, rounded UI, few and simple menu items, things tested on the emulator with screenshots.

## Where things are

- `main` = **v0.6.0** (published, latest, versionCode 600). Since 0.5.0 it added:
  - #25 + #27: crash on Export → Save PDF / Share PDF fixed (old `BottomSheet` kept lambdas in saved arguments;
    the share action used the closed sheet's context). The UI preview job now fails on any `FATAL EXCEPTION`.
  - #28: in a note, the options sheet opens from a round **+** FAB at the bottom left (mirror of the edit FAB);
    the "⋮" toolbar item is only shown for notes in the bin. Review fixes (scroll padding, API 30 check for
    My theme, picture "100 %" = full width, sheets not shown after state is saved).
  - #29: **app lock** (PIN 4–6 digits, fingerprint / face / phone screen lock, lock after 0/1/5/15 min) —
    see CODEMAP row "App lock" and DECISIONS #21.
- Unit tests: 136 (`./gradlew testDebugUnitTest`). Device tests: 37 (run in CI, see `ENVIRONMENT.md`).

## Open pull requests

| PR | What | Next step |
|---|---|---|
| #26 `docs/handoff` | This folder | Owner merges when he wants |
| #1–#10 | Dependabot updates, untouched | Not requested. Careful with **#8 (Material 1.12 → 1.14)**: `com/google/android/material/color/BlackPearlColorResources.kt` calls Material's package-private `ResourcesLoaderUtils`; check it still exists, run the device tests (`CustomThemeTest`, `CustomThemeActivityTest`) and `tools/palette-gen` (pinned to 1.12) |

## Next work

Nothing requested is pending. Ask the owner; useful candidates:
1. **Check 0.6.0 on his phone**: fingerprint unlock (the CI emulator has no sensor, so it was never exercised),
   Export / Share PDF, moving pictures.
2. App lock follow-up: hide note content in the home-screen widget and reminder notifications while locked.
3. Smaller leftovers: fastlane store texts and the notification icon are still Quillpad's; release v0.1.0 is an
   old unpublished draft (leave it).

## How work is done here (summary of AGENTS.md + what proved useful)

- One branch per feature from updated `main`; PR; CI green (build + `UI preview` emulator run); owner merges (squash).
  Do **not** stack PRs. If a branch was built on another one that got squash-merged, merge `main` into it
  (normal push, never force-push) and keep "ours" where the content is identical.
- Prove bugs with a failing test first when possible (Robolectric for Android-ish logic, device tests in CI).
- Look at the emulator screenshots yourself before saying a UI works (`Read` the PNGs, shrink with `sips -Z 900`).
- After each task append to `docs/CHANGELOG-AGENTS.md`; update `docs/CODEMAP.md` / `FEATURES.md` / `DECISIONS.md`
  when relevant (decision numbers: last is **20**).
- Strings: new ones only in `values/strings_blackpearl.xml` (English) + `values-it/strings_blackpearl.xml` (Italian).
