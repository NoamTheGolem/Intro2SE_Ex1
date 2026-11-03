# ADR-0003: A CSV row that breaks the rules is reported and still becomes a frame

**Status:** Accepted · **Date:** 2026-10-25 (supersedes the fail-fast rule of v1.0)

## Context
Item 7c: the video has "a frame for each corresponding plane coordinate as presented in the csv
file (in the list order)". The course's own `Ex0.csv` contains a triangle that is not isosceles
(row 3). Version 1.0 refused the whole file in that case, which would have produced no video
for the teacher's example. The algorithm itself is well defined for any two rails.

## Decision
Every data row produces exactly one frame, so frame *i* always belongs to row *i*:
* a row that violates the Q&A rules in every reading → **warning** (`row N (line M): …`) and the
  frame is computed with the algorithm's rails `a1→b`, `a2→c`;
* a row that cannot be projected at all (non-numbers, wrong count, NaN, zero-length rail,
  absurd size) → **warning** and a **black frame**;
* `--strict` restores the fail-fast behaviour (exit 2, no video) for callers who want it;
* exit 2 when no row at all is usable.

## Consequences
+ The teacher's file renders; robustness is visible in the warnings instead of a crash.
+ Frame numbering is stable, which the viewer and the tests rely on.
− A careless user might not read the warnings; `--strict` and the exit codes exist for scripts.
