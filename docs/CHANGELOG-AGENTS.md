# Agent work log

Append one short entry per significant task: files, decisions, tests (with result), open issues.

## 2026-11-08 — Project initialization (Claude Code)
- Files: rebranding (`app/build.gradle.kts`, `settings.gradle`, `no_translate_strings.xml`, `xml-v25/shortcuts.xml`,
  `nav_graph.xml`, `ActivityResultUtils.kt`), launcher icons, `graphics/black-pearl/`, docs, `.github/workflows/*`,
  `.github/dependabot.yml`; removed Infigraph configs and upstream workflows.
- Decisions: see DECISIONS.md #1–9.
- Tests: local `assembleDebug` + `testDebugUnitTest` OK (75 tests, 0 failures); APK badging verified
  (package io.github.mancio24x.blackpearl, label "Black Pearl"); CI Build on main green. Instrumented tests not run.
- Release signing key generated and stored outside the repo (owner's Mac); GitHub secrets set.
- Open: fastlane metadata and notification icon still Quillpad's; Dependabot PRs #1–#10 to review.
