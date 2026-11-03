# ADR-0003: Every CSV row is one frame — doubtful rows are drawn with a warning, bad rows are black with a warning

**Status:** Accepted · **Date:** 2026-10-25, revised 2026-11-03 after the lecturer's
`edge_cases.csv` (supersedes the fail-fast rule of v1.0)

## Context
Item 7c: the video has "a frame for each corresponding plane coordinate as presented in the csv
file (in the list order)". The course's own `Ex0.csv` contains a triangle that is not isosceles
(row 3); v1.0 refused the whole file, which gave no video for the teacher's example. v2/v3.0 drew
every row it could compute.

The lecturer's `edge_cases.csv` (for a die, `data/dice.nrrd`) states the expected behaviour
exactly: *"Expected: 16 frames; black: frames 6, 9 to 16; a warning for frames 7 to 16"*, with
the rows grouped as good planes (1–5), a valid plane that misses the volume (6), **doubtful**
planes — "drawn as given, with a warning" (7: legs 188.8 vs 226.6; 8: top not parallel to the
base) — and **bad** planes — "a black frame and a warning" (9: the count says 4 but 9 numbers
are given; 10: a word; 11: `inf`; 12: `b = c`; 13: collinear; 14: a point off the plane;
15: crossing sides; 16: an image 10 200 pixels wide).

## Decision
Every data row produces exactly one frame, so frame *i* always belongs to row *i*:

| Class | When (EPS = 0.001) | Frame | Warning |
|-------|--------------------|-------|---------|
| valid | isosceles, top ∥ base, top ≤ base, coplanar, sides ≥ 1 | the projection | — |
| valid, outside the volume | as above, but no sample inside `M` | black (all samples are 0) | — |
| **doubtful** | legs differ; top not parallel to the base; top wider than the base; a side shorter than 1 | **drawn as given**, with the algorithm's rails `a1→b`, `a2→c` | `row N (line M): …` + "drawn as given" |
| **bad** | count ≠ points given; not numbers; `inf`/`NaN`; a side of length 0; `b = c`; collinear; 4 points not on one plane; sides crossing; image > 8192 px | **black** | `row N (line M): … -> black frame` |

`Plane.validate` lists the doubtful problems, `Plane.whyNotRenderable` names the bad one,
`PlaneCsvReader` turns both into the messages. `--strict` restores fail-fast (exit 2, no video)
for callers who want it; a file without a single usable row is an error (exit 2).

## Consequences
+ The lecturer's 16 edge cases come out exactly as he wrote (`EdgeCasesTest`, frame by frame,
  including the pips on frames 1–3); his `Ex0.csv` renders with one warning (row 3).
+ Frame numbering is stable, which the app's list and the tests rely on.
+ A doubtful row still shows what the algorithm computes, so the user sees *why* it is doubtful.
− A careless user might not read the warnings; the app shows them in a dialog and next to the
  points, and `--strict` and the exit codes exist for scripts.
