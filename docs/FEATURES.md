# Features

Status legend: **Existing** (inherited from Quillpad, untouched) · **To improve** · **In development** ·
**Completed** (implemented, tests written) · **Verified** (tests run green + manual check on a device).

## Inherited from Quillpad

| Feature | Status | Notes |
|---|---|---|
| Notes create/edit/delete, autosave | Existing | |
| Markdown (headings, bold, lists, tables, code, links, images by URL) | Existing | |
| Checklists | Existing | Stored in `taskList`, not in content |
| Attachments (image, video, audio, file), camera photo, audio recording | Existing | Shown as a grid above the text, not inline |
| Note colors (12 predefined + default) | Existing | Already persisted in `notes.color`; see Black Pearl feature 2 |
| Notebooks, tags, pin, archive, recycle bin, hidden notes | Existing | |
| Reminders | Existing | |
| Search, sort, list/grid layout | Existing | |
| Sync: Nextcloud Notes, local folder | Existing | Text only |
| Backup/restore (zip) | Existing | Includes media |
| Share / export as text or markdown files | Existing | |
| Light/dark/dynamic theme, home widget, 20+ languages | Existing | |

## Black Pearl

| # | Feature | Status | Branch |
|---|---|---|---|
| 0 | Rebranding (name, app id, icon), docs, CI | Verified (CI + release) | `main` |
| 0b | Material 3 UI + customization (17 palettes, dynamic colors, my color, corners, note font, AMOLED) | Completed | `feature/material3-ui` |
| 1 | Repositionable images in notes (insert from attachments/camera/gallery, move up/down, reorder, align, resize 25–100% keeping ratio, remove from note) | Completed — device check pending | `feature/image-layout` |
| 2 | Colored notes: swatch picker with live preview, "No color", change from list multi-select, WCAG AA contrast in light/dark for all palettes | Completed — device check pending | `feature/note-colors` |
| 3 | Offline OCR ("Scan text"): camera or gallery, Italian + English, editable preview, insert here or new note, fully offline | Completed — device test in CI, phone check pending | `feature/offline-ocr` |
| 4 | PDF export: A4, title, Markdown formatting, checklists, positioned images (+ image attachments), multipage with page numbers; save anywhere (SAF) or share (Sharesheet) | Completed — device test in CI, phone check pending | `feature/pdf-export` |
| 5 | Import from other apps (drawer → Import and export): Google Keep, Evernote, Joplin, Simplenote, Standard Notes, Fossify Notes, Obsidian / Markdown, plain text (converted to Markdown), HTML, note JSON, Black Pearl/Quillpad backups; keeps checklists, pictures, tags, notebooks, colors, pinned/archived/trashed, dates | Completed — unit tests with sample exports + device test in CI, phone check pending | `feature/import-other-apps` |
| 6 | Notes list "Soft" (design 1A): big title, round menu button, search pill, notebook chips, rounder cards (extra rounded by default) with inset images, floating quick-action pill beside the round + | Completed — emulator screenshots in CI, phone check pending | `feature/notes-list-soft` |
| 7 | Simpler Material 3 menus: note options and note long-press in one rounded sheet (round buttons, short group, switches under "More settings", Delete last), picture options as segmented choices, rounded sheets and popup menus everywhere | Completed — emulator screenshots in CI, phone check pending | `feature/editor-menus` |
| 8 | Move and resize pictures by hand: tap a picture → frame with a corner handle (live size, snaps to 25/50/75/100 %) and a move handle / drag the picture (drop line, auto-scroll), small bar (align, more, remove); pictures between two text lines tappable | Completed — emulator screenshots in CI, phone check pending | `feature/photo-handles` |
| 9 | App lock: PIN of 4–6 digits (stored as a salted hash), fingerprint / face / phone screen lock, lock after 0/1/5/15 min in the background, 30 s wait after 5 wrong PINs, recents preview hidden on Android 13+ | Completed — unit tests + device test + emulator screenshots, phone check pending | `feature/app-lock` |

## Linked notes

Type `[[` in a Markdown note to choose a title. Tap a link in the preview to open or create its target. The backlinks section shows where the note is cited. Open Network in the drawer to explore, search, drag and zoom. From a node, create a new note linking back to it.
