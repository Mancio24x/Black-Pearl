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
adb logcat -c
new_note() { # $1 = "true" for a checklist, then title and body lines (use %s for spaces)
  local list=$1; shift
  adb shell am start -W -n $MAIN -a android.intent.action.VIEW -d "blackpearl://notes/editor_create?newNoteIsList=$list" >/dev/null
  sleep 3
  tap_text "Title"; adb shell input text "$1"; shift
  tap_text "Take a note!" || tap_text "Task"
  for line in "$@"; do adb shell input text "$line"; adb shell input keyevent KEYCODE_ENTER; done
  adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
}
# Sample notes and a notebook, so the list screenshots show real cards and notebook chips
adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity >/dev/null; sleep 3
tap_text "Continue" && sleep 2
new_note false "Storia%sRinascimento" "Firenze,%si%sMedici%se%sla%snascita%sdella%sprospettiva."
new_note true "Spesa" "Latte" "Pane" "Caffe"
new_note false "Viaggio%sin%sSardegna" "Cala%sGoloritze%sall%salba,%spoi%sOrosei."
new_note false "Idee%sracconto" "Una%smappa,%suna%sX,%sun%staccuino%sche%snessuno%ssa%sleggere."
adb shell am force-stop $PKG
adb shell am start -W -n $MAIN >/dev/null; sleep 3
tap_text "Open navigation drawer" && tap_text "Your Notebooks" && sleep 2 && tap_text "Create a notebook" \
  && sleep 1 && adb shell input text "Scuola" && tap_text "Save"
sleep 1; adb shell input keyevent KEYCODE_BACK
for mode in no yes; do
  adb shell cmd uimode night $mode; adb shell am force-stop $PKG
  # explicit component: the debug build also has a LeakCanary launcher icon
  adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity >/dev/null; sleep 3
  shot "whatsnew-night_$mode"
  tap_text "Continue" && sleep 3
  shot "main-night_$mode"
  # Settings > Appearance, starting from the notes list
  tap_text "Open navigation drawer"; shot "drawer-night_$mode"
  if tap_text "Settings"; then
    shot "settings-night_$mode"
    if tap_text "Appearance"; then
      shot "appearance-night_$mode"
      adb shell input swipe 500 1600 500 500 400; shot "appearance-themes-night_$mode"
      adb shell input swipe 500 1600 500 400 400; shot "appearance-shapes-night_$mode"
      adb shell input swipe 500 500 500 1800 300; adb shell input swipe 500 500 500 1800 300
      if tap_text "My theme"; then
        shot "my-theme-night_$mode"
        # pick the dark "Abyss"-like background, then apply: the whole app must recolor at runtime
        tap_text "Background" && adb shell input swipe 500 1500 500 900 400 && tap_text "#0E1720"
        tap_text "Apply my theme" && sleep 3 && shot "my-theme-applied-night_$mode"
      fi
    fi
  fi
  # Editor last (a deep-linked editor may leave the app on BACK)
  adb shell am force-stop $PKG
  adb shell am start -W -n $MAIN -a android.intent.action.VIEW -d "blackpearl://notes/editor_create?newNoteIsList=false" >/dev/null
  sleep 3; adb shell input text "Black%sPearl%stest"; adb shell input keyevent KEYCODE_ENTER
  adb shell input text "Material%s3%snote"; shot "editor-night_$mode"
  # The note's options sheet (one rounded sheet instead of the long "⋮" list), then its folded settings
  adb shell input keyevent KEYCODE_BACK; sleep 1
  if tap_text "Note options"; then
    shot "note-options-night_$mode"
    tap_text "More settings" && shot "note-options-more-night_$mode"
    adb shell input keyevent KEYCODE_BACK; sleep 1
    tap_text "Export" && shot "note-export-night_$mode"
    adb shell input keyevent KEYCODE_BACK
  fi
  # Long press on a note in the list: the same Material 3 sheet style
  adb shell am force-stop $PKG
  adb shell am start -W -n $MAIN >/dev/null; sleep 3
  b=$(adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; \
    grep -o '<node[^>]*text="Idee racconto"[^>]*>' "$OUT/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
  if [ -n "$b" ]; then set -- $b; adb shell input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 800; shot "note-longpress-night_$mode"; fi
done
# Theme / crash messages of this run, for diagnosis
adb logcat -d -v time BlackPearlTheme:V ColorResLoaderCreator:V ColorResourcesTableCreator:V AndroidRuntime:E '*:S' > "$OUT/logcat.txt"
