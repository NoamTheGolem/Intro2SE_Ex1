# Contributing

Small project, two people, but we work the way the course teaches.

## Workflow

1. One task = one branch: `feat/<topic>`, `fix/<topic>`, `docs/<topic>`.
2. Small commits with a message that says *why*, not only what.
3. Open a pull request; the template asks for tests, docs and the **AI disclosure**.
4. The other team member reviews (reads every line) before merge. CI must be green.
5. Merge with a merge commit (keeps the review history visible).

## Definition of done

A change is done when:

* `scripts/test.sh` (or `.bat`) passes and CI is green on Linux and Windows;
* the demo command `python Ex0.py file.nrrd file.csv file.mp4` still works;
* the documents that describe the behaviour are updated in the same PR (SRS, SDD,
  ALGORITHM, TESTING, EXPERIMENT) and a new design decision got an ADR in `docs/adr/`;
* every public method has Javadoc; `javac -Xlint:all -Werror` is clean (the build enforces it).

## Code style

* Java 17 language level, no third-party runtime dependencies.
* `core/` stays pure Java (`java.lang`, `java.util`, `java.util.concurrent` only) so it can be
  reused on Android unchanged. Desktop APIs (`java.io.File`, AWT, Swing) live in `app/`.
* Allman braces, one statement per line, named constants for meaningful values, comments
  explain *why*.

## AI policy

AI assistants are allowed (course policy) and were used here. Every PR says which tool did
what, and `docs/AI_USAGE.md` keeps the running log. You own what you merge: if you cannot
explain a line, do not merge it.
