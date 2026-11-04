# ADR-0010: Ship the app with its own Java runtime and a native launcher — download and run

**Status:** Accepted · **Date:** 2026-11-04

## Context
The requirement from the team, in their words: *"I don't need to do anything in order for it to
work — just download the app and run it."* The grader downloads the repository from GitHub and
starts the app or the demo command. Until v3.1 that needed a Java 17+ installation (the launchers
found one, or printed "install it from adoptium.net"), and a source checkout had no compiled jar
(`build/` was ignored), so the first start compiled it — which needs a JDK, not just a JRE. The
course's own prototype is Python; a grader's PC may well have no Java at all.

## Decision
1. **A Java runtime in the repository**, `runtime/`: OpenJDK 25.0.1 for Windows x64, cut down by
   `jlink` to the two modules the app uses (`java.base`, `java.desktop` — `jdeps` says so; their
   dependencies `java.datatransfer`, `java.prefs`, `java.xml` come along): 47 MB, 143 files, with
   its licence notices in `runtime/legal/`. `jlink` links for another platform from that
   platform's `jmods`, so it is reproducible from Linux (`scripts/make_runtime.sh`).
2. **The compiled jar in the repository**, `build/Ex0.jar` (the one exception in `.gitignore`), so
   nothing has to be built. CONTRIBUTING: rebuild and commit it with every code change.
3. **A native launcher, `Ex1.exe`** (≈ 60 lines of C, Win32 API, built with mingw-w64 by
   `scripts/build_launcher.sh`, with the app's icon): starts `runtime\bin\javaw.exe -jar
   build\Ex0.jar` without a console window, passes a dropped `.nrrd` on, and falls back to
   `JAVA_HOME`, IntelliJ's `.jdks` and the `PATH` if `runtime\` is missing. It imports only
   `KERNEL32`, `USER32` and `msvcrt` — present on every Windows.
4. **Every other entry point prefers the bundled runtime too:** `Ex1 Viewer.bat`, `Ex0.bat` and
   `Ex0.py` look in `runtime\bin` first, so `python Ex0.py file.nrrd file.csv file.mp4` needs only
   Python on Windows.

## Alternatives considered
| Alternative | Why not |
|-------------|---------|
| Ask the grader to install Java 17+ | Exactly the step the requirement removes |
| `jpackage` installer (.msi/.exe) | Must be built on Windows; an installer is another step; app-image output would be the same as this, built on a CI runner instead |
| A full JDK/JRE copy | 150–300 MB; jlink keeps only what the app uses |
| GraalVM native image | No AWT/Swing support worth relying on; a large toolchain |
| A GitHub Release zip only | Graders clone or download the repository; a separate asset is easy to miss — the repository itself must run |

## Consequences
+ Windows: download → unzip → double-click `Ex1.exe`. Nothing to install, nothing to build.
+ Same frames as everywhere: the bundled runtime renders the edge-case video byte for byte like
  the Linux JDK (checked under Wine; TESTING TC-106), and CI runs the demo on its Windows runner
  through `python Ex0.py`, which now uses the bundled runtime.
+ The runtime is the JDK's own code, so the "no third-party libraries" rule (SRS C-3) still holds
  for the application; the runtime's GPL v2 + Classpath Exception licence permits shipping it.
− +47 MB in the repository and in its history; a Java update means a new `runtime/` commit.
− Windows only. Linux/macOS still need any Java 17+ (`./Ex0`, `python Ex0.py`).
− `Ex1.exe` is not code-signed: on a file downloaded from the internet, SmartScreen may ask once
  ("More info → Run anyway").
− A committed build artefact can fall behind the sources; the definition of done covers it.
