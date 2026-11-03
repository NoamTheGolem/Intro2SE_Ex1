# ADR-0001: Implement in Java, keep `python Ex0.py` as the entry point

**Status:** Accepted · **Date:** 2026-10-23 (launcher added 2026-11-03)

## Context
The sheet allows Java, Python, C or C++ and names the demo as `python Ex0.py file.nrrd file.csv file.mp4`.
It states that "a proper Java implementation leads to 300+ fps". The team wants to reuse the
projection code in an Android app (Java/Kotlin). The course itself teaches Python tooling.

## Decision
Write the implementation in **Java 17** (no third-party runtime libraries). Provide a 100-line
**`Ex0.py`** (standard library only) that locates a JDK/JRE ≥ 17 and runs the jar, so the exact
command of the sheet works; `Ex0.bat`, `Ex0` and `java -jar` are equivalent.

## Consequences
+ Performance target met with margin on one thread (280–395 FPS at 500³).
+ `core` can be dropped into Android Studio unchanged.
+ The grader does not need to know the language: `python Ex0.py …` just works.
− Two runtimes to find (Python for the launcher, Java for the program); mitigated by the
  launcher's search list and clear messages (exit code 4 when no Java is found).
− Python-specific course tooling (pytest, ruff, mypy) does not apply; JUnit, `-Xlint -Werror`
  and GitHub Actions play the same roles.
