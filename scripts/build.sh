#!/usr/bin/env bash
# Builds build/Ex0.jar with plain javac (JDK 17+). No internet, no Gradle needed.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/classes && mkdir -p build/classes
javac --release 17 -encoding UTF-8 -Xlint:all -Werror -d build/classes \
    $(find core/src/main/java app/src/main/java -name "*.java")
jar --create --file build/Ex0.jar --main-class ex1.app.Ex0 -C build/classes .
echo "Built build/Ex0.jar"
