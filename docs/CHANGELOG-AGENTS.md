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

## 2026-11-08 — Feature 2: note colors (branch feature/note-colors)
- Files: `ui/common/NoteColorDialog.kt`, `EditorFragment.kt` (picker), `AbstractNotesFragment.kt` + `menu/main_selected_notes.xml`
  (multi-select), `ActivityViewModel.colorNotes`, `values-night/themes.xml` (Cyan), strings, tests `NoteColorContrastTest`,
  `NoteColorPersistenceTest`.
- Tests: `testDebugUnitTest` 94 tests, 0 failures; `assembleDebug` OK. Not yet checked on a device.

## 2026-11-09 — Feature 3: offline OCR (branch feature/offline-ocr)
- Files: `ui/ocr/*`, `EditorFragment.kt`, `menu/editor_top.xml`, `xml/file_provider_paths.xml` (cache ocr/), `assets/tessdata/*`,
  `gradle/libs.versions.toml`, `app/build.gradle.kts` (dep, noCompress, release ABIs, proguard file), `app/proguard-rules.pro`,
  strings, tests `OcrTextTest` (JVM), `OcrEngineTest` (device, CI emulator job).
- Tests: `testDebugUnitTest` 99 tests, 0 failures; `assembleDebug` + `assembleRelease` OK (release 25 MB, Tesseract names kept by R8).
- Open: OcrEngineTest result in CI; phone check.

## 2026-11-09 — Feature 4: PDF export (branch feature/pdf-export)
- Files: `ui/pdf/*`, `EditorFragment.kt`, `menu/editor_top.xml`, `xml/file_provider_paths.xml` (cache pdf/), strings,
  tests `PdfLayoutTest` (JVM), `NotePdfExporterTest` (device), `ui-preview.yml` runs device tests.
- Tests: `testDebugUnitTest` 103 tests, 0 failures; `assembleDebug` OK. Device tests: see CI.

## 2026-11-09 — Release 0.2.0 branch
- #11 was squash-merged; #12–#15 were stacked on the pre-squash commits and conflicted with main. The 4 feature
  commits were rebased onto main (content unchanged, verified by diff) and opened as one PR with version 0.2.0
  (versionCode 4). Local: 103 unit tests OK, assembleDebug + assembleRelease OK.
- Lesson: with squash merges, do not stack PRs; branch each feature from main after the previous one is merged.

## 2026-11-09 — Themes at Atlante level (branch feature/themes-atlante)
- Design reference: Claude Design canvas "Black Pearl — Redesign" (screens 3–5).
- Files: `ui/theme/ThemeRoles.java`, `CustomTheme.kt`, generated `CustomThemeResources.kt`, `colors_custom_theme.xml`,
  `palettes.xml`; `tools/palette-gen/*`; `ui/appearance/*`; `fragment_appearance.xml`, nav graph, `MainActivity`;
  `PreferenceEnums.kt` (ThemeGroup, new themes, Pill, FabSize), `PreferenceRepository.kt`, `BaseActivity.kt`,
  `SettingsFragment.kt` + layout (one "Appearance" entry), `MainFragment.kt` (FAB size); removed `AppearanceDialogs.kt`.
- Tests: 107 unit tests OK (new `ThemeRolesTest`; contrast test aware of always-dark themes); `assembleDebug` OK.
- Open: device check of runtime "My theme"; notes list redesign (1A/1B) waiting for owner's choice.

## 2026-11-09 — QA pass (branch qa/test-and-cleanup)
- Bugs fixed: editor content source when the user emptied the text (could resurrect deleted text on image/OCR insert);
  image menu could target a non-standalone copy of the same image; "]" in image captions broke Markdown; OCR progress
  dialog leaked if the screen closed; Romanian (99% translated) was stripped from the APK and ar/ro were missing from
  the per-app language list (fa/sv stay excluded: ~10% translated); Appearance previews read Configuration via LocalContext.
- Cleanup: 250 unused strings (all locales), 8 unused drawables, unused colors.xml; Black Pearl strings marked
  English+Italian only (MissingTranslation).
- Tests: new device test `NoteLifecycleTest`; CI now runs ALL device tests. Unit tests 108 OK.
- Lint: FragmentLiveDataObserve in ExtendedListAdapter is a false positive (observes viewLifecycleOwnerLiveData).
