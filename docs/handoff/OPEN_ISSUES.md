# Open issues and unverified points (updated 2026-11-10, v0.6.0)

## Not verified

- **Nothing has been tried on the owner's phone.** All checks are unit tests, CI device tests on an Android 15
  emulator and screenshots. Ask him to report anything odd.
- Import from other apps: sample exports in `app/src/test/resources/import/` were written from each app's export
  format, not exported from the real apps. Live Nextcloud sync, real camera capture and the share sheet UI are untested.

## Known limits

- App lock: the database is not encrypted (the lock protects the screens; widget and reminder notifications hide
  note content while the lock is on). Biometric unlock never ran in CI (no sensor on the emulator).
- App lock with timeout "At once": coming back from the camera / file picker / share sheet asks for the PIN again
  (expected; the default is 1 minute).

- Moving a picture by drag maps rendered lines to source lines; around fenced code blocks and tables the drop can be
  one line off (it always moves in the right direction). See `ui/editor/markdown/ImageGestures.kt`.
- The `ImageClickSpan` "standalone" rule and `ImageBlocks.blocks` can disagree for pictures inside list items
  ("- ![x](y)"): occurrence indexes could point to the wrong picture when the same file appears twice that way.
- Picture sheet: "100 %" sets the natural size (no width in the title), the drag handle stores an explicit 100.
- `scripts/capture-ui.sh` fails to tap the "#0E1720" preset in the My theme editor (screenshots still useful);
  device tests cover dark My theme.
- Simple list sheets still in use: `TagsFragment`, `ManageNotebooksFragment`, reminders, attachments, Export, Scan
  text use `BottomSheet` (M3 look, plain list layout). Inside its action lambdas `requireContext()` etc. are the
  sheet's: call a function of the screen for anything asynchronous (#27).

## Leftovers from Quillpad

- fastlane store metadata is still Quillpad's.
- Release `v0.1.0` is an old unpublished draft: leave it.

## Linked notes

Renaming a note leaves its existing references untouched. Duplicate titles resolve to the most recently modified matching note. Embeds, block links and front-matter aliases are outside this release.

## HTML viewer

- Restored: localStorage (also if WebView lost it), form fields (not password/file/hidden), scroll position, and
  attribute changes made by scripts (class, style, disabled, hidden, open, aria-*) on elements that still sit at the
  same place in the page. IndexedDB is kept by WebView itself, not mirrored.
- **Not restored:** text or elements created by scripts after load, JavaScript variables (scores, timers), canvas
  drawings, `sessionStorage`, custom widgets that keep state only in memory. A page that does this keeps its own
  look only if it also saves to localStorage / IndexedDB.
- Restore does not fire events, so a page's own counters may not match restored fields until the user interacts.
- Single `.html` has no access to sibling files; use a `.zip` for pages with local images/scripts. External
  (https) libraries load only if the page asks and the phone is online.
- Progress is not included in Black Pearl backups. Not run yet on a device: see CHANGELOG-AGENTS.
