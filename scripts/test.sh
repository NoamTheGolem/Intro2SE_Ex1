#!/usr/bin/env bash
# Builds the project and runs all JUnit 4 tests (jars in lib/, no internet needed).
set -euo pipefail
cd "$(dirname "$0")/.."
scripts/build.sh
CP="build/classes:lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar"
rm -rf build/test-classes && mkdir -p build/test-classes
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/test-classes \
    $(find core/src/test/java app/src/test/java -name "*.java")
TESTS=$(cd build/test-classes && find . -name "*Test.class" | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
java -cp "$CP:build/test-classes" org.junit.runner.JUnitCore $TESTS
