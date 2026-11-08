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

## 2026-11-08 — Material 3 UI + customization (branch feature/material3-ui, PR #11)
- Files: `values*/themes.xml`, generated `values*/palettes.xml`, `styles.xml`, `styles_blackpearl.xml`, `attrs_blackpearl.xml`,
  `strings_blackpearl.xml` (en, it), layouts (M3 text appearances, outline variant, note card shape), `PreferenceEnums.kt`,
  `AppPreferences.kt`, `PreferenceRepository.kt`, `BaseActivity.kt`, `ui/theme/Theme.kt`, `ui/settings/*`, `tools/palette-gen/*`,
  `scripts/capture-ui.sh`, `.github/workflows/ui-preview.yml`.
- Fix: `showPreferenceDialog` mapped clicks to the unfiltered enum list (wrong choice when an option is unsupported).
- Tests: `assembleDebug` OK; `testDebugUnitTest` 78 tests, 0 failures (new `AppearancePreferencesTest`). Not yet checked on a device.
- Open: review UI preview screenshots; manual check on phone; note colors (feature 2) still use the old fixed tints.

## 2026-11-08 — Feature 1: repositionable images (branch feature/image-layout)
- Files: `ui/editor/markdown/ImageBlocks.kt`, `ImageLayoutPlugin.kt`, `di/MarkwonModule.kt`, `ui/editor/EditorFragment.kt`,
  `values/ids_blackpearl.xml`, `strings_blackpearl.xml` (en, it), tests `ImageBlocksTest`, `ImageLayoutPersistenceTest`.
- Data safety: edits update both the EditText (editor source of truth) and the saved note.
- Tests: `testDebugUnitTest` 90 tests, 0 failures; `assembleDebug` OK. Not yet checked on a device.
- Open: drag & drop, text wrap beside images (see DECISIONS #13).
