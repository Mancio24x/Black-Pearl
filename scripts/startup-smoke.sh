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
# How notes get into the OLD app: written straight into its Room database with adb root (google_apis emulator
# images allow it). Share intents were tried first but the old app saved none of them (database ~140 KB after 250
# "notes"), so the case measured only the kit. Now: stop the app, pull notes_database (+ -wal/-shm), insert the rows
# on the host with python3's sqlite3, checkpoint, push it back with the app's owner and SELinux label, and COUNT the
# rows to prove they are there. The colour value is copied from a row the old app wrote itself (its sample note).
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
DBDIR=/data/data/$PKG/databases
db_pull() { # $1 = local dir; copies the database files of the stopped app
  rm -rf "$1"; mkdir -p "$1"
  for f in notes_database notes_database-wal notes_database-shm; do adb pull "$DBDIR/$f" "$1/$f" >/dev/null 2>&1; done
  [ -s "$1/notes_database" ]
}
db_count() { # $1 = local database file; prints "total kit-free" note counts
  python3 - "$1" <<'EOF'
import sqlite3, sys
c = sqlite3.connect(sys.argv[1])
print(c.execute("SELECT COUNT(*) FROM notes").fetchone()[0], c.execute("SELECT COUNT(*) FROM notes WHERE title LIKE 'Seed note %'").fetchone()[0])
EOF
}
seed_notes() { # adds $NOTES notes to the installed (old, stopped) app's database
  local dir=$OUT/seed-notes db=$OUT/seed-db; seed_files "$dir"
  du -sh "$dir" | sed 's/^/seed text: /'
  adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
  adb shell am force-stop $PKG; sleep 1
  if ! db_pull "$db"; then echo "::warning::update-with-notes: cannot read the old app's database (no adb root?)"; return 1; fi
  python3 - "$db/notes_database" "$dir" "$NOTES" <<'EOF'
import os, sqlite3, sys, time
path, src, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
c = sqlite3.connect(path)
row = c.execute("SELECT color FROM notes LIMIT 1").fetchone()
color = row[0] if row else '"Default"'
now = int(time.time())
with c:
    for i in range(1, n + 1):
        text = open(os.path.join(src, f"{i}.md")).read()
        c.execute("""INSERT INTO notes (title, content, isList, taskList, isArchived, isDeleted, isPinned, isHidden,
            isMarkdownEnabled, isLocalOnly, isCompactPreview, screenAlwaysOn, creationDate, modifiedDate, deletionDate,
            attachments, color, notebookId) VALUES (?, ?, 0, '[]', 0, 0, 0, 0, 1, 1, 0, 0, ?, ?, NULL, '[]', ?, NULL)""",
            (f"Seed note {i}", text, now - n + i, now - n + i, color))
c.execute("PRAGMA wal_checkpoint(TRUNCATE)")
c.close()
EOF
  local owner; owner=$(adb shell stat -c %u:%g "$DBDIR" | tr -d '\r')
  adb shell rm -f "$DBDIR/notes_database-wal" "$DBDIR/notes_database-shm"
  adb push "$db/notes_database" "$DBDIR/notes_database" >/dev/null
  adb shell chown "$owner" "$DBDIR/notes_database"; adb shell restorecon "$DBDIR/notes_database"
  # read it back from the device: the count proves the notes are really there
  db_pull "$db" && note "update-with-notes before update: notes total/seeded $(db_count "$db/notes_database")"
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
  seed_notes || echo "::warning::$c: notes could not be added; this case measures the kit alone"
  db_stats "$c before update"
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
  adb shell am force-stop $PKG; sleep 1
  db_pull "$OUT/after-db" && note "update-with-notes after kit: notes total/seeded $(db_count "$OUT/after-db/notes_database")"
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
