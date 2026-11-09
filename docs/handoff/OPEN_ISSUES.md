# Open issues and unverified points (2026-11-10)

## Not verified

- **Nothing has been tried on the owner's phone.** All checks are unit tests, CI device tests on an Android 15
  emulator and screenshots. Ask him to report anything odd.
- Import from other apps: sample exports in `app/src/test/resources/import/` were written from each app's export
  format, not exported from the real apps. Live Nextcloud sync, real camera capture and the share sheet UI are untested.

## Known limits

- Moving a picture by drag maps rendered lines to source lines; around fenced code blocks and tables the drop can be
  one line off (it always moves in the right direction). See `ui/editor/markdown/ImageGestures.kt`.
- The `ImageClickSpan` "standalone" rule and `ImageBlocks.blocks` can disagree for pictures inside list items
  ("- ![x](y)"): occurrence indexes could point to the wrong picture when the same file appears twice that way.
- Picture sheet: "100 %" sets the natural size (no width in the title), the drag handle stores an explicit 100.
- `scripts/capture-ui.sh` fails to tap the "#0E1720" preset in the My theme editor (screenshots still useful);
  device tests cover dark My theme.
- Simple list sheets still in use: `TagsFragment`, `ManageNotebooksFragment`, reminders, attachments, Export, Scan
  text use `BottomSheet` (M3 look, plain list layout; its background crash is fixed by #25).

## Leftovers from Quillpad

- fastlane store metadata and the notification icon are still Quillpad's.
- Release `v0.1.0` is an old unpublished draft: leave it.
