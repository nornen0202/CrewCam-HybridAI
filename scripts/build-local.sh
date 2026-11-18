#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 or 21 installation}"
gradle_cmd=./gradlew
if [[ -x .tools/gradle-8.9/bin/gradle ]]; then
  gradle_cmd=.tools/gradle-8.9/bin/gradle
fi
exec "$gradle_cmd" "$@"
