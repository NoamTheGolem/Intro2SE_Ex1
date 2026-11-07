#!/usr/bin/env bash
# Rebuilds runtime/ - the Java runtime that ships with the app, so Ex1.exe runs on a Windows PC
# without Java installed. jlink can link for another platform from that platform's jmods:
#
#   JMODS=<a Windows x64 JDK 25>/jmods  scripts/make_runtime.sh
#
# jlink must have the same feature version as the jmods (here: JDK 25 jlink, JDK 25.0.1 jmods).
# The app needs only java.base and java.desktop (jdeps --print-module-deps build/Ex0.jar);
# java.desktop pulls in java.datatransfer, java.prefs and java.xml. Result: ~47 MB.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JMODS:?set JMODS to the jmods folder of a Windows x64 JDK}"
JLINK="${JLINK:-jlink}"
rm -rf runtime
# --strip-java-debug-attributes, not --strip-debug: on a Linux host --strip-debug also runs objcopy
# on the Windows executables, which drops Oracle's signature from java.exe and javaw.exe - and
# Windows (Smart App Control) refuses to start an unsigned program from the internet.
"$JLINK" --module-path "$JMODS" --add-modules java.base,java.desktop \
    --strip-java-debug-attributes --no-header-files --no-man-pages --compress=zip-6 --output runtime
echo "runtime/: $(du -sh runtime | cut -f1), $(grep JAVA_VERSION runtime/release)"
