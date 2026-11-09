# Handoff — start here (written 2026-11-10)

For the next coding agent. Read this folder after `AGENTS.md` and `docs/CODEMAP.md`.
Files: this README (state + next steps), `APP_LOCK_PLAN.md` (next feature, designed, not started),
`RELEASE.md` (how to publish), `ENVIRONMENT.md` (tools, CI, gotchas), `OPEN_ISSUES.md` (known limits).

## The owner

- Leonardo (GitHub `Mancio24x`), Italian, **not a programmer**, usually on the phone (Claude Code Remote Control).
- Write to him in **Italian, plain words**, short; explain trade-offs and recommend one option (see `CLAUDE.md`).
- He **merges PRs himself** (ruleset bypass) and then writes "Fatto". When he says "pubblica", publish the release
  (see `RELEASE.md`). Do not publish without his request.
- He likes: Material 3, rounded UI, few and simple menu items, things tested on the emulator with screenshots.

## Where things are (2026-11-10)

- Latest release: **v0.5.0** (versionCode 500): notes list "Soft" (1A), import from other apps, My theme fixes,
  simpler M3 menus/sheets, move/resize pictures by hand. `main` is one commit ahead (#25, Save PDF crash fix).
- The owner reported on 0.5.0: "Esporta / Condividi PDF crasha" → #25 + #27.
- Unit tests: 128 (`./gradlew testDebugUnitTest`). Device tests: 36 (run in CI, see `ENVIRONMENT.md`).

## Open pull requests

| PR | What | State | Next step |
|---|---|---|---|
| #25 `fix/pdf-export-crash` | Crash on "Save PDF" (old `BottomSheet` saved its actions when the app went to the background) | **Merged** into `main` (not released yet). Emulator log confirmed Save PDF no longer crashes | — |
| **#27** `fix/pdf-share-crash` | "Share PDF" still crashed for a second reason (`requireContext()` inside the sheet action = the closed sheet: "Fragment BottomSheet not attached to a context"); also makes the UI preview job fail on any `FATAL EXCEPTION` | Fix pushed, unit tests OK; CI emulator run was in progress | Check `gh pr checks 27 --repo Mancio24x/Black-Pearl` (the job itself now fails on a crash) and the `pdf-share*` screenshots. Then ask the owner to merge and offer release **0.5.1** (contains #25 + #27) |
| #26 `docs/handoff` | This folder | Open, docs only | Owner merges when he wants |
| #1–#10 | Dependabot updates | Untouched | Not requested. Careful with **#8 (Material 1.12 → 1.14)**: `com/google/android/material/color/BlackPearlColorResources.kt` calls Material's package-private `ResourcesLoaderUtils`; check it still exists, run the device tests (`CustomThemeTest`, `CustomThemeActivityTest`) and `tools/palette-gen` (pinned to 1.12) |

## Next work, in order

1. **Finish #27** (above), then release 0.5.1 if the owner asks.
2. **App lock** — requested by the owner: lock the app with biometrics or a 4–6 digit PIN. Fully designed in
   `APP_LOCK_PLAN.md`; nothing written yet (no branch). Start a branch `feature/app-lock` from updated `main`.
3. Release after each merged feature when the owner asks ("pubblica").

Smaller leftovers (not requested now): fastlane store texts and the notification icon are still Quillpad's;
release v0.1.0 is an old unpublished draft (leave it).

## How work is done here (summary of AGENTS.md + what proved useful)

- One branch per feature from updated `main`; PR; CI green (build + `UI preview` emulator run); owner merges (squash).
  Do **not** stack PRs. If a branch was built on another one that got squash-merged, merge `main` into it
  (normal push, never force-push) and keep "ours" where the content is identical.
- Prove bugs with a failing test first when possible (Robolectric for Android-ish logic, device tests in CI).
- Look at the emulator screenshots yourself before saying a UI works (`Read` the PNGs, shrink with `sips -Z 900`).
- After each task append to `docs/CHANGELOG-AGENTS.md`; update `docs/CODEMAP.md` / `FEATURES.md` / `DECISIONS.md`
  when relevant (decision numbers: last is **20**).
- Strings: new ones only in `values/strings_blackpearl.xml` (English) + `values-it/strings_blackpearl.xml` (Italian).
