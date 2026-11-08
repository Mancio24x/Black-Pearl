# Roadmap

Priority order. One feature at a time, each on its own branch, each merged only with green CI.

## P0 — Base (done in `main`)
- Fork/import Quillpad, rebrand, new app id, icon, agent docs, CI build + release, Dependabot.
- Open: create the GitHub repo and push; add release signing secrets; check the debug APK on a phone.

## P1 — Feature 1: repositionable images (`feature/image-layout`)
Current state: attachments are a JSON list rendered as a grid *above* the text; the Markdown `![](url)` dialog
inserts images by URL only. Sync (Nextcloud / folder) transfers text only.
Plan (needs owner approval before coding):
1. Reuse attachments for storage; place an image inline by inserting a standard Markdown/HTML reference in `content`
   at the cursor, e.g. `<img src="bp-media:FILE.jpg" width="60%" align="center">` (valid Markdown HTML, survives
   backup, export, and sync as text). Existing notes are untouched (no migration needed: no new column).
2. Editor preview renders those references through Markwon + Coil with a resolver for `bp-media:`.
3. Move up/down (paragraph swap), align left/center/right, resize presets (25/50/75/100 %, ratio kept), delete only
   the reference block — via a contextual menu on the image. Drag & drop as a later enhancement.
4. Text wrapping beside images: evaluate only after 1–3; likely not supported by Markwon without a large rewrite.
5. Tests: parser/serializer of image blocks (unit), move/align/delete keep surrounding text intact (unit), backup round-trip.
Known limit: on other devices synced via Nextcloud the image file itself is not transferred (already true today).

## P2 — Feature 2: note colors (`feature/note-colors`)
Already exists: `NoteColor` enum (12 colors), stored in `notes.color`, chosen from the editor menu, shown on cards.
Work: verify light/dark contrast (WCAG AA for text), add "remove color" if missing, add color change from the
note list selection menu, add tests for persistence and backup round-trip. No DB change expected.

## P3 — Feature 3: offline OCR (`feature/offline-ocr`)
Candidate: Tesseract via `cz.adaptech.tesseract4android` (Apache-2.0, F-Droid friendly) with **bundled**
`tessdata_fast` models `ita` + `eng` (≈ +8–10 MB APK, plus native libs per ABI; consider ABI splits).
Rejected for F-Droid: Google ML Kit (proprietary, Play Services/Firebase dependencies).
Flow: editor menu "Scan text" → camera or gallery → preprocess (grayscale, scale) → OCR on background thread →
editable preview dialog → insert into current note or create new note. No network permission used for OCR.

## P4 — Feature 4: PDF export (`feature/pdf-export`)
Use `android.graphics.pdf.PdfDocument` (no new dependency) + `StaticLayout` for text, A4 (595×842 pt), paginated,
title, markdown-rendered spans, checklists (☐/☑), images scaled to page width. Save via SAF `CreateDocument("application/pdf")`
and share via cache file + `FileProvider` + Android Sharesheet. No storage permissions.

## Later
- Fastlane/F-Droid metadata rebrand (`fastlane/metadata/android/*`).
- Notification icon (`drawable-*/ic_notification_icon.png`) still Quillpad's.
- Emulator job in CI for instrumented tests.
