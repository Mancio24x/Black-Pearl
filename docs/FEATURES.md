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
| 0 | Rebranding (name, app id, icon), docs, CI | Completed | `main` |
| 1 | Repositionable images in notes | Planned | `feature/image-layout` |
| 2 | Colored notes (improve existing palette/menu/contrast) | Planned — To improve | `feature/note-colors` |
| 3 | Offline OCR ("Scan text") | Planned | `feature/offline-ocr` |
| 4 | PDF export | Planned | `feature/pdf-export` |
