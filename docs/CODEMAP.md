# CODEMAP

Paths relative to `app/src/main/java/org/qosp/notes/` (= `J/`) and `app/src/main/res/` (= `R/`).
Tests: `app/src/test/java/org/qosp/notes/` (unit, JVM/Robolectric) and `app/src/androidTest/java/org/qosp/notes/` (device).

| Feature | Main files |
|---|---|
| App start, Koin setup, media folder const | `J/App.kt`, `J/Config.kt` |
| Launcher / welcome screen (Compose) | `J/ui/launcher/LauncherActivity.kt`, `WelcomeScreen.kt` |
| Main activity, nav drawer | `J/ui/MainActivity.kt`, `J/ui/ActivityViewModel.kt`, `R/navigation/nav_graph.xml`, `R/menu/nav_drawer.xml` |
| Note list (home, design 1A "Soft") | `J/ui/main/MainFragment.kt` (search bar, notebook chips, quick-action pill), `MainViewModel.kt`, `J/ui/common/AbstractNotesFragment.kt`, `R/layout/fragment_main.xml`, `layout_appbar_main.xml`, `chip_notebook_filter.xml`, styles `Widget.BlackPearl.*` in `R/values*/styles_blackpearl.xml` |
| Note card in lists (color, preview) | `J/ui/common/recycler/NoteViewHolder.kt`, `NoteRecyclerAdapter.kt`, `J/ui/utils/views/NoteCardView.kt`, `R/layout/layout_note.xml` |
| Editor (text, markdown toolbar, menu) | `J/ui/editor/EditorFragment.kt` (large), `EditorViewModel.kt`, `R/layout/fragment_editor.xml`, `R/menu/editor_top.xml` (toolbar icons + "more"), `editor_bottom.xml`; note options and picture sheets `J/ui/editor/NoteSheets.kt` |
| Bottom sheets (Material 3) | Compose sheets `J/ui/common/OptionsSheet.kt` (QuickActions, SheetGroup, SheetSwitch, SheetExpandable, SheetChoice), list long-press `NoteListSheet.kt` (`AbstractNotesFragment.showMenuForNote`); simple list sheets `J/ui/utils/views/BottomSheet.kt` (styles `BottomSheetHeader/Action` in `R/values/styles.xml`) |
| Markdown rendering | `J/di/MarkwonModule.kt`, `J/ui/editor/markdown/*`, `J/ui/utils/coil/CoilImagesPlugin.kt` |
| Repositionable images (feature 1) | `J/ui/editor/markdown/ImageBlocks.kt` (format + move/align/size/remove, pure), `ImageLayoutPlugin.kt` (render + click), `EditorFragment` (`setupImageLayout`, `editContent`, `showImageActions`, attachment menu "Insert in text"); tests `ImageBlocksTest`, `data/ImageLayoutPersistenceTest` |
| Insert image / link / table (markdown) | `J/ui/editor/dialog/InsertImageDialog.kt`, `InsertHyperlinkDialog.kt`, `InsertTableDialog.kt` |
| Checklists (tasks) | `J/data/model/NoteTask.kt`, `J/ui/tasks/*`, `R/layout/layout_task.xml` |
| Note colors | `J/data/model/NoteColor.kt`, picker `J/ui/common/NoteColorDialog.kt` (editor menu + list multi-select `action_color_selected` → `ActivityViewModel.colorNotes`), tints `colorNote*` in `R/values*/themes.xml`, `J/ui/utils/ContextUtils.kt`, `NoteViewHolder.kt`; tests `NoteColorContrastTest`, `data/NoteColorPersistenceTest` |
| Attachments (images, audio, video, files) | `J/data/model/Attachment.kt`, `J/ui/attachments/*`, `J/components/MediaStorageManager.kt`, `R/xml/file_provider_paths.xml` |
| Offline OCR (feature 3) | `J/ui/ocr/OcrEngine.kt` (Tesseract, model install, EXIF decode), `OcrText.kt` (cleanup, insert), `EditorFragment` (`action_scan_text`, `runOcr`, `showOcrResult`), models `app/src/main/assets/tessdata/`, R8 rules `app/proguard-rules.pro`; tests `OcrTextTest`, device `androidTest/.../OcrEngineTest` |
| PDF export (feature 4) | `J/ui/pdf/NotePdfExporter.kt` (A4 PdfDocument renderer), `PdfBlocks.kt` (note → text/image/task blocks), `Paginator.kt`; `EditorFragment` (`action_export_pdf`, `showPdfSheet`, `exportPdf`); provider `cache/pdf/`; tests `PdfLayoutTest`, device `NotePdfExporterTest` |
| Camera photo | `J/ui/utils/ActivityResultUtils.kt` (`TakePictureContract`), `EditorFragment.takePhotoLauncher` |
| Media viewer / audio player / recorder | `J/ui/media/*`, `J/ui/recorder/*` |
| Reminders | `J/data/model/Reminder.kt`, `J/ui/reminders/*` |
| Notebooks, tags | `J/ui/notebooks/*`, `J/ui/tags/*`, `J/data/model/Notebook.kt`, `Tag.kt`, `NoteTagJoin.kt` |
| Archive, recycle bin | `J/ui/archive/*`, `J/ui/deleted/*`, `J/components/workers/BinCleaningWorker.kt` |
| Search | `J/ui/search/*` |
| Share / export note (text, markdown files) | `J/ui/utils/ShareUtils.kt`, `EditorFragment` (`exportNotesLauncher`), `J/ui/utils/ActivityResultUtils.kt` |
| Database (Room) + migrations | `J/data/AppDatabase.kt`, `J/data/DatabaseConverters.kt`, `J/data/dao/*`, schemas `app/schemas/` |
| Repositories | `J/data/repo/*` |
| Backup / restore (zip: `backup.json` + media) | `J/data/Backup.kt`, `J/components/backup/BackupManager.kt`, `BackupService.kt`, test `data/BackupTest.kt` |
| Import from other apps (Keep, Evernote, Joplin, Simplenote, Standard Notes, Fossify, Markdown, TXT, HTML, note JSON) | `J/components/importer/*`: `NoteImporter.kt` (dispatch, titles, dates, colors), `ImportArchives.kt` (zip/tar/tgz/jex), `JsonFormats.kt`, `EnexFormat.kt`, `JoplinFormat.kt`, `TextFormats.kt` (md/txt/html, front matter, links), `PlainTextMarkdown.kt`, `HtmlMarkdown.kt`, `OtherAppsImport.kt` (Android: copy files, save via `BackupManager.restoreNotesFromBackup`); `BackupService` (`Action.IMPORT`); drawer item `nav_import_export` in `MainActivity`; tests `components/importer/NoteImporterTest` + samples in `app/src/test/resources/import/`, device `ImportOtherAppsTest` |
| Sync core | `J/data/sync/core/*` (`SynchronizeNotes.kt`), `J/components/workers/SyncWorker.kt` |
| Sync: Nextcloud Notes | `J/data/sync/nextcloud/*`, `J/ui/sync/nextcloud/*` |
| Sync: local folder (SAF, .md/.txt files) | `J/data/sync/fs/StorageBackend.kt`, `StorageConfig.kt` |
| Settings / preferences | `J/ui/settings/*`, `J/preferences/*` |
| Theme (Material 3, light/dark/AMOLED) | `R/values*/themes.xml` (app attrs → M3 roles), `J/ui/BaseActivity.kt` (applies overlays), `J/ui/theme/Theme.kt` (Compose reads View theme) |
| Themes (21 palettes, groups, descriptions) | generated `R/values*/palettes.xml` ← `tools/palette-gen/GenPalettes.java` (table) + `J/ui/theme/ThemeRoles.java` (role math, shared with the app); enum `ColorScheme`/`ThemeGroup` in `J/preferences/PreferenceEnums.kt` |
| "My theme" (3 colors, Android 11+) | `J/ui/theme/CustomTheme.kt` + `com/google/android/material/color/BlackPearlColorResources.kt` (Material resources loader, no Material You overlay), generated `CustomThemeResources.kt` + `R/values/colors_custom_theme.xml`, `Palette.Custom`; colors in `PreferenceRepository.getCustomTheme()`; tests `ThemeRolesTest` |
| Settings > Appearance (Compose) | `J/ui/appearance/AppearanceFragment.kt`, `AppearanceScreen.kt` (gallery, mode, shapes, fonts, FAB size), `ThemeEditorScreen.kt` (HSV picker, contrast, preview) |
| Shapes / note font / FAB size | `R/values/styles_blackpearl.xml`, `attrs_blackpearl.xml`, enums `CardShape`/`NoteFont`/`FabSize`; FAB size applied in `J/ui/main/MainFragment.kt` |
| Black Pearl strings | `R/values/strings_blackpearl.xml`, `R/values-it/strings_blackpearl.xml` (keep new strings here, not in upstream `strings.xml`) |
| Home-screen widget | `J/ui/widget/*`, `R/xml/notes_widget_info.xml` |
| DI modules (Koin) | `J/di/*` |
| About screen, links | `J/ui/about/AboutFragment.kt`, `R/values/no_translate_strings.xml` |
| App name, app id, version | `R/values/no_translate_strings.xml` (`app_name`), `app/build.gradle.kts` |
| Launcher icon | `R/drawable-v24/ic_launcher_*.xml`, `R/mipmap-*/`, source `graphics/black-pearl/icon-source.png` |
| Static shortcuts / deep link | `R/xml-v25/shortcuts.xml`, `nav_graph.xml` (`blackpearl://notes/...`) |
| CI | `.github/workflows/build.yml`, `release.yml`, `ui-preview.yml` (+ `scripts/capture-ui.sh`), `.github/dependabot.yml` |
