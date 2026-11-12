#!/usr/bin/env bash
# Launches the installed release app and fails if it crashes. Usage: scripts/startup-smoke.sh old.apk new.apk
# Case 1: fresh install of new.apk. Case 2: old.apk used once (welcome, sample notes), then updated to new.apk.
# Case 4 "update-with-notes": clean install of old.apk, filled with many long user notes, then updated to new.apk;
# records launch time and memory (smoke/summary.txt) while the first start adds the Bible Study Kit.
# Optional env: SMOKE_NOTES (notes to add, default 250), SMOKE_NOTE_KB (size of each, default 8),
# SMOKE_HEAP (e.g. 192m: lowers dalvik.vm.heapgrowthlimit for case 4 via adb root; off by default).
set -u
OLD=$1; NEW=$2
PKG=io.github.mancio24x.blackpearl
OUT=smoke; mkdir -p "$OUT"
SUMMARY=$OUT/summary.txt; : > "$SUMMARY"
NOTES=${SMOKE_NOTES:-250}; NOTE_KB=${SMOKE_NOTE_KB:-8}; HEAP=${SMOKE_HEAP:-}
FAILED=0
note() { echo "$*"; echo "$*" >> "$SUMMARY"; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  local b; b=$(grep -o "<node[^>]*\(text\|content-desc\)=\"$1\"[^>]*>" "$OUT/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
  [ -n "$b" ] || return 1
  # shellcheck disable=SC2086
  set -- $b; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
start_app() { # $1 = label; cold start of the launcher, records TotalTime
  local t; t=$(adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity | tee /dev/stderr \
    | grep -o 'TotalTime: [0-9]*' | grep -o '[0-9]*')
  note "$1: launch TotalTime ${t:-?} ms"
}
crashed() { # $1 = logcat file; true if it shows a crash or ANR of the app
  grep -q -E "FATAL EXCEPTION|ANR in $PKG" "$1"
}
launch() { # $1 = case name; starts the launcher, passes the welcome screen, waits, checks for a crash
  adb logcat -c
  start_app "$1"
  sleep 6; tap_text "Continue" || tap_text "Continua"; sleep 20
  adb exec-out screencap -p > "$OUT/$1.png"
  adb logcat -d > "$OUT/$1-logcat.txt"
  if crashed "$OUT/$1-logcat.txt"; then
    echo "::error::$1: the app crashed"
    grep -A40 -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$1-logcat.txt" | head -80
    FAILED=1
  elif [ -z "$(adb shell pidof $PKG)" ]; then
    echo "::error::$1: the app is not running"; tail -60 "$OUT/$1-logcat.txt"; FAILED=1
  else
    echo "$1: started fine"
  fi
  # a second cold start (the kit is already added, notes list filled)
  adb shell am force-stop $PKG; adb logcat -c
  start_app "$1-restart"; sleep 10
  adb logcat -d > "$OUT/$1-restart-logcat.txt"
  if crashed "$OUT/$1-restart-logcat.txt"; then
    echo "::error::$1: the app crashed on the second start"
    grep -A40 -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$1-restart-logcat.txt" | head -80
    FAILED=1
  fi
}

# --- case 4 helpers ---------------------------------------------------------------------------------------------
# How notes get into the OLD app: it is a release build (not debuggable, so no run-as and no direct database write
# through the app), but MainActivity accepts ACTION_SEND text/plain and the editor inserts the shared text as a new
# note at once (EditorViewModel.initialize -> insertNote). So each note is one explicit `am start` SEND intent with
# EXTRA_TITLE/EXTRA_TEXT, run by a script ON the device (no adb round trip per note, and the text is read from a
# pushed file so no shell quoting of the content). This goes through the real app code path. Slower options were
# ruled out: typing with `input text` (minutes per KB) and backup/import (needs the system file picker).
seed_files() { # writes $NOTES Markdown notes of ~$NOTE_KB KB each into $1 (letters, digits, punctuation only)
  mkdir -p "$1"
  awk -v n="$NOTES" -v kb="$NOTE_KB" -v dir="$1" 'BEGIN {
    srand(7); split("bread river window promise garden letter morning shadow silver market evening travel " \
      "mountain kitchen lantern harbor meadow winter summer pencil courage answer journey village forest", w, " ")
    for (i = 1; i <= n; i++) {
      f = dir "/" i ".md"; s = "# Seed note " i "\n\nSee [[Seed note " (i % n) + 1 "]] and #seed.\n\n"
      while (length(s) < kb * 1024) {
        line = "- "; for (j = 0; j < 14; j++) line = line w[int(rand() * 24) + 1] " "
        s = s line "\n"; if (rand() < 0.1) s = s "\n## Part " int(rand() * 100) "\n\n"
      }
      printf "%s", s > f; close(f)
    }
  }'
}
seed_notes() { # adds the notes to the installed (old) app through share intents
  local dir=$OUT/seed-notes; seed_files "$dir"
  du -sh "$dir" | sed 's/^/seed text: /'
  adb shell rm -rf /data/local/tmp/seed; adb push "$dir" /data/local/tmp/seed >/dev/null
  cat > "$OUT/seed.sh" <<EOF
i=1
while [ \$i -le $NOTES ]; do
  am start -W -a android.intent.action.SEND -t text/plain -n $PKG/org.qosp.notes.ui.MainActivity \\
    --es android.intent.extra.TITLE "Seed note \$i" --es android.intent.extra.TEXT "\$(cat /data/local/tmp/seed/\$i.md)" >/dev/null
  sleep 0.5
  # a fresh process every 25 notes, so editor screens do not pile up in the old app
  if [ \$((i % 25)) -eq 0 ]; then sleep 1; am force-stop $PKG; echo "seeded \$i"; fi
  i=\$((i+1))
done
sleep 2; am force-stop $PKG
EOF
  adb push "$OUT/seed.sh" /data/local/tmp/seed.sh >/dev/null
  local t0=$SECONDS; adb shell sh /data/local/tmp/seed.sh
  note "update-with-notes: $NOTES notes of ~$NOTE_KB KB added to the old app in $((SECONDS - t0)) s"
}
db_stats() { # $1 = label; database size, only readable with adb root (best effort, never fails the job)
  local s; s=$(adb shell "ls -l /data/data/$PKG/databases/ 2>/dev/null" | awk '/notes_database/ {print $NF": "$5" B"}' | tr '\n' ' ')
  [ -n "$s" ] && note "$1: database files $s"
  return 0
}
set_heap() { # $1 = heapgrowthlimit, e.g. 192m; needs adb root, restarts the Android framework
  adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
  if [ "$(adb shell id -u | tr -d '\r')" != 0 ]; then echo "::warning::adb root not available, heap left as is"; return; fi
  adb shell setprop dalvik.vm.heapgrowthlimit "$1"
  adb shell setprop sys.boot_completed 0; adb shell stop; adb shell start; sleep 15
  local i
  for i in $(seq 60); do
    [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && adb shell pm path android >/dev/null 2>&1 && break
    sleep 3
  done
  [ "$i" = 60 ] && echo "::warning::framework restart did not finish in time"
  adb shell input keyevent 82; sleep 5   # unlock the screen
}
memory_watch() { # watches the updated app for 90 s while the kit is added; records peak and final memory
  local peak_pss=0 peak_java=0 pss java i m
  for i in $(seq 9); do
    sleep 10
    if [ -z "$(adb shell pidof $PKG)" ]; then
      echo "::error::update-with-notes: the app process is gone after $((i * 10)) s"; FAILED=1; return; fi
    m=$(adb shell dumpsys meminfo $PKG); echo "$m" > "$OUT/update-with-notes-meminfo.txt"
    pss=$(echo "$m" | grep -o 'TOTAL PSS: *[0-9]*' | grep -o '[0-9]*$'); pss=${pss:-0}
    java=$(echo "$m" | grep -m1 'Java Heap:' | awk '{print $3}'); java=${java:-0}
    echo "  +$((i * 10)) s: TOTAL PSS $pss KB, Java heap $java KB"
    [ "$pss" -gt "$peak_pss" ] && peak_pss=$pss; [ "$java" -gt "$peak_java" ] && peak_java=$java
  done
  note "update-with-notes: TOTAL PSS final $pss KB, peak $peak_pss KB; Java heap final $java KB, peak $peak_java KB"
}
update_with_notes() {
  local c=update-with-notes
  adb uninstall $PKG >/dev/null 2>&1
  [ -n "$HEAP" ] && set_heap "$HEAP"
  note "device: ram $(adb shell grep MemTotal /proc/meminfo | tr -s ' ' | cut -d' ' -f2) KB," \
    "heapgrowthlimit $(adb shell getprop dalvik.vm.heapgrowthlimit | tr -d '\r'), heapsize $(adb shell getprop dalvik.vm.heapsize | tr -d '\r')"
  adb install -g "$OLD" || { echo "::error::$c: install of the old APK failed"; FAILED=1; return; }
  adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity >/dev/null
  sleep 6; tap_text "Continue" || tap_text "Continua"; sleep 10
  adb logcat -c; seed_notes; adb logcat -d > "$OUT/$c-seed-logcat.txt"
  crashed "$OUT/$c-seed-logcat.txt" && echo "::warning::$c: the OLD app crashed while notes were added (see seed logcat)"
  adb root >/dev/null 2>&1; sleep 3; adb wait-for-device; db_stats "$c before update"
  adb install -r -g "$NEW" || { echo "::error::$c: update to the new APK failed"; FAILED=1; return; }
  adb logcat -c
  start_app "$c"
  sleep 6; tap_text "Continue" || tap_text "Continua"
  memory_watch
  adb exec-out screencap -p > "$OUT/$c.png"
  adb logcat -d > "$OUT/$c-logcat.txt"
  if crashed "$OUT/$c-logcat.txt"; then
    echo "::error::$c: the app crashed"; grep -A40 -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$c-logcat.txt" | head -80
    FAILED=1
  elif [ -z "$(adb shell pidof $PKG)" ]; then
    echo "::error::$c: the app is not running"; tail -60 "$OUT/$c-logcat.txt"; FAILED=1
  else
    echo "$c: started fine"
  fi
  db_stats "$c after kit"
  adb shell am force-stop $PKG; adb logcat -c
  start_app "$c-restart"; sleep 10
  adb logcat -d > "$OUT/$c-restart-logcat.txt"
  if crashed "$OUT/$c-restart-logcat.txt" || [ -z "$(adb shell pidof $PKG)" ]; then
    echo "::error::$c: the app crashed or stopped on the second start"
    grep -A40 -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$c-restart-logcat.txt" | head -80
    FAILED=1
  fi
}

adb install -g "$NEW" || { echo "::error::install of the new APK failed"; exit 1; }
launch fresh
adb uninstall $PKG
adb install -g "$OLD" || { echo "::error::install of the old APK failed"; exit 1; }
launch previous-release
adb install -r -g "$NEW" || { echo "::error::update to the new APK failed"; exit 1; }
launch update
update_with_notes
echo "=== summary ==="; cat "$SUMMARY"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && { echo '```'; cat "$SUMMARY"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
exit $FAILED
