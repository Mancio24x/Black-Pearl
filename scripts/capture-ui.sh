#!/usr/bin/env bash
# Captures screenshots of key screens on a running emulator/device. Output: ui-shots/
# Usage: scripts/capture-ui.sh [apk]   (APK built with -PTESTLAB_BUILD=true skips the welcome screen)
set -uo pipefail
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
for mode in no yes; do
  adb shell cmd uimode night $mode; adb shell am force-stop $PKG
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  shot "main-night_$mode"
  adb shell am start -a android.intent.action.VIEW -d "blackpearl://notes/editor_create?newNoteIsList=false" $PKG >/dev/null
  adb shell input text "Black%sPearl%stest"; shot "editor-night_$mode"
  adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK
  tap_text "Open navigation drawer" || adb shell input swipe 5 800 600 800; shot "drawer-night_$mode"
  tap_text "Settings" && shot "settings-night_$mode"
  tap_text "Color scheme" && shot "palettes-night_$mode"
  adb shell input keyevent KEYCODE_BACK
done
