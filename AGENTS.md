# AGENTS.md — Black Pearl

Instructions for every AI coding agent (Codex, Claude Code, others). Keep this file short; details live in `docs/`.

## Project in one paragraph

Black Pearl is an Android note-taking app, a GPL-3.0 fork of [Quillpad](https://github.com/quillpad/quillpad)
(base commit recorded in `docs/DECISIONS.md`). Single Gradle module `:app`, Kotlin, Views + Fragments + ViewBinding
(a few Compose screens), Room database, Koin DI, Markwon for Markdown. Kotlin package is still `org.qosp.notes`
(inherited; do not rename). The installed app id is `io.github.mancio24x.blackpearl` (never change it after the first public release).

## Where to look (read in this order, stop as soon as you know enough)

1. This file, then `docs/handoff/README.md` (current state, open PRs, next task) if it exists.
2. `docs/CODEMAP.md` — feature → file paths. **Start every task here.**
3. Only the files CODEMAP points to.
4. `docs/ARCHITECTURE.md` / `docs/DECISIONS.md` only when changing structure, data model, sync or backup.
5. `docs/TESTING.md` for build/test commands.

## Token-saving rules (mandatory)

1. **Targeted analysis.** Read AGENTS.md → CODEMAP.md → identify the feature → read only the relevant files.
   No full-repo scans unless strictly necessary.
2. **Efficient search.** Use `rg`, `git grep`, `git diff`. Print line ranges (`sed -n '120,180p'`), not whole files.
   Exclude: `build/`, `build-cache/`, `.gradle/`, `app/schemas/`, `app/src/main/res/values-*/` (translations),
   `fastlane/`, binary files (`*.png`, `*.jar`). Example: `rg -n "NoteColor" app/src/main/java`.
3. **Minimal changes.** Edit methods, not whole classes. Small, reviewable, testable diffs. Reuse existing components.
4. **No needless rewrites.** Do not replace the database (Room), UI framework, libraries, architecture or sync
   system without a demonstrable technical need recorded in `docs/DECISIONS.md`.
5. **Concise docs.** Update CODEMAP/docs only when relevant components change. No long generic prose.
6. **Proportional tests.** First run tests for the touched component (`./gradlew testDebugUnitTest --tests '*Foo*'`).
   Before merging to `main`, run the full CI set (see `docs/TESTING.md`).
7. **No unrequested changes.** No extra features, no cosmetic or architectural refactors outside the task.
8. **Context log.** After each significant task, append to `docs/CHANGELOG-AGENTS.md`: files changed, decisions,
   tests run (and results), open issues. Do not rewrite docs that are still valid.
9. **Efficient communication.** Report: changes made, tests passed, problems found, decisions needing approval.
10. **Quality over savings.** Never skip safety, data-integrity or necessary verification to save tokens.

## Hard rules (data safety)

- User notes must never be lost. Any Room schema change needs: version bump, a `Migration` in
  `app/src/main/java/org/qosp/notes/data/AppDatabase.kt`, exported schema in `app/schemas/`, and a test in
  `app/src/androidTest/java/org/qosp/notes/data/DatabaseMigrationTest.kt` with pre-existing data.
- Backup format (`data/Backup.kt`, `components/backup/BackupManager.kt`) must stay able to restore older backups.
- Note content is plain Markdown and is synced as-is to Nextcloud / local folders. Do not invent new inline syntax
  without a compatibility plan (see `docs/DECISIONS.md`).
- Never commit secrets, keystores or `local.properties`. Signing keys live only in GitHub Actions secrets.
- Never claim a test passed if it was not executed. Instrumented tests need an emulator/device.

## Workflow

- `main` is stable. One branch per feature: `feature/<name>` (e.g. `feature/pdf-export`). One feature at a time.
- Per feature: implement → tests → build → review → commit → PR → CI green → merge.
- Clear commit messages (`feat:`, `fix:`, `docs:`, `ci:`, `chore:`).
- Two agents must not edit the same files concurrently.
- PRs are squash-merged: do not stack feature PRs on each other; start each feature branch from the updated `main`.
- `upstream` remote = original Quillpad (fetch only). Never push there; never auto-merge upstream changes —
  cherry-pick reviewed fixes only.
