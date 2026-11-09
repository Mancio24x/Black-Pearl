#!/usr/bin/env bash
# Captures screenshots of key screens on a running emulator/device. Output: ui-shots/
# Usage: scripts/capture-ui.sh [apk]   (APK built with -PTESTLAB_BUILD=true skips the welcome screen)
set -uo pipefail
trap "exit 0" EXIT  # screenshots are informative: never fail the job
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
PKG=io.github.mancio24x.blackpearl
OUT=ui-shots; mkdir -p "$OUT"
adb install -r -g "$APK" >/dev/null
shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }
tap_text() { # tap the first node whose text or content-desc equals $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  local b; b=$(grep -o "<node[^>]*\(text\|content-desc\)=\"$1\"[^>]*>" "$OUT/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
  [ -n "$b" ] || { echo "not found: $1"; return 1; }
  set -- $b; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
MAIN=$PKG/org.qosp.notes.ui.MainActivity
for mode in no yes; do
  adb shell cmd uimode night $mode; adb shell am force-stop $PKG
  # explicit component: the debug build also has a LeakCanary launcher icon
  adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity >/dev/null; sleep 3
  shot "whatsnew-night_$mode"
  tap_text "Continue" && sleep 3
  shot "main-night_$mode"
  adb shell am start -W -n $MAIN -a android.intent.action.VIEW -d "blackpearl://notes/editor_create?newNoteIsList=false" >/dev/null
  sleep 2; adb shell input text "Black%sPearl%stest"; adb shell input keyevent KEYCODE_ENTER
  adb shell input text "Material%s3%snote"; shot "editor-night_$mode"
  # back to the list: relaunch (BACK from a deep-linked editor may leave the app)
  adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity >/dev/null; sleep 3
  shot "list-night_$mode"
  # navigation button = top-left of the toolbar
  read -r W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' ')
  adb shell input tap $(( W * 7 / 100 )) $(( H * 9 / 100 )); shot "drawer-night_$mode"
  if tap_text "Settings"; then
    shot "settings-night_$mode"
    tap_text "Color scheme" && shot "palettes-night_$mode"
    adb shell input keyevent KEYCODE_BACK
  fi
done
