#!/usr/bin/env bash
# Regenerates palettes.xml (light/night), colors_custom_theme.xml and CustomThemeResources.kt.
# Needs JDK 17+ and a prior Gradle build (Material library in the Gradle cache).
set -euo pipefail
cd "$(dirname "$0")/../.."
VER=$(sed -n 's/^material = "\(.*\)"/\1/p' gradle/libs.versions.toml)
AAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches" -name "material-$VER.aar" | head -1)
[ -n "$AAR" ] || { echo "material-$VER.aar not found: run ./gradlew assembleDebug first"; exit 1; }
WORK=$(mktemp -d)
unzip -q -o "$AAR" classes.jar -d "$WORK"
javac -nowarn -d "$WORK" -cp "$WORK/classes.jar" \
  app/src/main/java/org/qosp/notes/ui/theme/ThemeRoles.java tools/palette-gen/GenPalettes.java 2>&1 | grep -v "RestrictTo\|^[0-9]* warning" || true
java -cp "$WORK:$WORK/classes.jar" GenPalettes .
