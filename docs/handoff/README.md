# Handoff — start here (updated 2026-11-11, after release 0.8.0)

For the next coding agent. Read this folder after `AGENTS.md` and `docs/CODEMAP.md`.
Files: this README (state + next steps), `APP_LOCK_PLAN.md` (design of the app lock, now implemented — kept as
background), `RELEASE.md` (how to publish), `ENVIRONMENT.md` (tools, CI, gotchas), `OPEN_ISSUES.md` (known limits).

## The owner

- Leonardo (GitHub `Mancio24x`), Italian, **not a programmer**, usually on the phone (Claude Code Remote Control).
- Write to him in **Italian, plain words**, short; explain trade-offs and recommend one option (see `CLAUDE.md`).
- He **merges PRs himself** (ruleset bypass) and then writes "Fatto". Since 2026-11-11 he wants every version that
  passes CI **published without asking** (see `RELEASE.md`; from the cloud use the manual Release workflow).
- He likes: Material 3, rounded UI, few and simple menu items, things tested on the emulator with screenshots.

## Where things are

- `main` = **v0.8.0** (2026-11-11). Since 0.6.0: notification icon + notes hidden outside the app when locked
  (#31), OCR on real photos (#32), "Convert to list" makes real checkboxes (#33), redundant menus removed (#34),
  recipes sample note (#35), editor design 2 (#36), dead code removed (#37), release from cloud sessions (#38),
  **linked notes and Network** (#39, Obsidian `[[links]]`; spec and plan in `docs/superpowers/`, design canvas
  https://claude.ai/artifact/QNAmdLWMrpWjKYuYC5Ap48), tappable task squares, shorter formatting bar and designed
  Vivid themes (#40).
- Tests run only on GitHub CI from cloud sessions (no Android SDK, toolchain download blocked): push to a PR branch,
  `build` = unit tests + lint + debug APK, `preview` = all device tests + screenshots.

## Open pull requests

See GitHub. Dependabot PRs are not requested work.

## Next work

Ask the owner. Known candidates:
1. Linked notes, later versions: update links when a note is renamed, embeds `![[Note]]`, aliases, block links.
2. Check on his phone what CI cannot: fingerprint unlock, Nextcloud sync, the Network gestures, the `[[`
   suggestion strip with his keyboard.
3. fastlane store texts are still Quillpad's; release v0.1.0 is an old unpublished draft (leave it).

## How work is done here (summary of AGENTS.md + what proved useful)

- One branch per feature from updated `main`; PR; CI green (build + `UI preview` emulator run); owner merges (squash).
  Do **not** stack PRs. If a branch was built on another one that got squash-merged, merge `main` into it
  (normal push, never force-push) and keep "ours" where the content is identical.
- Prove bugs with a failing test first when possible (Robolectric for Android-ish logic, device tests in CI).
- Look at the emulator screenshots yourself before saying a UI works (`Read` the PNGs, shrink with `sips -Z 900`).
- After each task append to `docs/CHANGELOG-AGENTS.md`; update `docs/CODEMAP.md` / `FEATURES.md` / `DECISIONS.md`
  when relevant (decision numbers: see the last one in `DECISIONS.md`).
- Strings: new ones only in `values/strings_blackpearl.xml` (English) + `values-it/strings_blackpearl.xml` (Italian).
