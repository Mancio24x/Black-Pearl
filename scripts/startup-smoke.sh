#!/usr/bin/env bash
# Launches the installed release app and fails if it crashes. Usage: scripts/startup-smoke.sh old.apk new.apk
# Case 1: fresh install of new.apk. Case 2: old.apk used once (welcome, sample notes), then updated to new.apk.
set -u
OLD=$1; NEW=$2
PKG=io.github.mancio24x.blackpearl
OUT=smoke; mkdir -p "$OUT"
FAILED=0
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  local b; b=$(grep -o "<node[^>]*\(text\|content-desc\)=\"$1\"[^>]*>" "$OUT/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
  [ -n "$b" ] || return 1
  set -- $b; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
launch() { # $1 = case name; starts the launcher, passes the welcome screen, waits, checks for a crash
  adb logcat -c
  adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity
  sleep 6; tap_text "Continue" || tap_text "Continua"; sleep 20
  adb exec-out screencap -p > "$OUT/$1.png"
  adb logcat -d > "$OUT/$1-logcat.txt"
  if grep -q -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$1-logcat.txt"; then
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
  adb shell am start -W -n $PKG/org.qosp.notes.ui.launcher.LauncherActivity; sleep 10
  adb logcat -d > "$OUT/$1-restart-logcat.txt"
  if grep -q -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$1-restart-logcat.txt"; then
    echo "::error::$1: the app crashed on the second start"
    grep -A40 -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/$1-restart-logcat.txt" | head -80
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
exit $FAILED
