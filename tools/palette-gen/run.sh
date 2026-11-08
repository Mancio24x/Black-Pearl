#!/usr/bin/env bash
# Regenerates app/src/main/res/values{,-night}/palettes.xml. Needs JDK 17+ and a prior Gradle build
# (so that the Material library is in the Gradle cache).
set -euo pipefail
cd "$(dirname "$0")/../.."
VER=$(sed -n 's/^material = "\(.*\)"/\1/p' gradle/libs.versions.toml)
AAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches" -name "material-$VER.aar" | head -1)
[ -n "$AAR" ] || { echo "material-$VER.aar not found: run ./gradlew assembleDebug first"; exit 1; }
WORK=$(mktemp -d)
unzip -q -o "$AAR" classes.jar -d "$WORK"
javac -d "$WORK" -cp "$WORK/classes.jar" tools/palette-gen/GenPalettes.java
java -cp "$WORK:$WORK/classes.jar" GenPalettes app/src/main/res
