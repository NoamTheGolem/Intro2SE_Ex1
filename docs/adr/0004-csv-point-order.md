# ADR-0004: Read the points in the course's order — `(a, b, c)` and `(a1, b, a2, c)`

**Status:** Accepted · **Date:** 2026-11-03 (revised the same day; the first version put the Q&A
order first and is superseded)

## Context
The Q&A of the sheet names the four points of a trapezoid `(a1, a2, b, c)`, with rails `a1→b`
and `a2→c`. The CSV only has columns `x1 … x4`, so which column is which point is a design
question. The first version of this ADR read the columns in the Q&A order and, when that was
invalid, tried "two rails listed one after the other" — because row 4 of the course's `Ex0.csv`
is only isosceles that way.

The lecturer's own test data (his Drive folder: five scans, their use-case CSVs and an
`edge_cases.csv`) settles it. The comment in `edge_cases.csv` says: *"Each plane starts with its
number of points: 3 for a triangle a, b, c, or 4 for a trapezoid a', b, a'', c"* — the points
are listed **rail by rail**: `p1 = a1 (a')`, `p2 = b`, `p3 = a2 (a'')`, `p4 = c`. His files agree:

* every row of `head.csv`, `onion.csv`, `swiss_roll.csv`, `biscuit_cake.csv`, `croissant.csv`
  is a valid isosceles trapezoid in that order, and the slices come out the right way up (for
  `z = k`: rows along `y`, columns along `x`, like his example images);
* `Ex0.csv` row 4 is simply valid (rails 502.49, top 300 ≤ base 400) — no reinterpretation;
* `Ex0.csv` row 2 (a rectangle) is valid in both orders, but only the course order gives his
  orientation; the Q&A order transposes the image.

## Decision
`PlaneResolver` reads **3 points as `(a, b, c)`** and **4 points as `(a1, b, a2, c)`**
(`Plane.trapezoid(p1, p3, p2, p4)`). Only when that reading breaks the rules **and** the Q&A
order `(a1, a2, b, c)` satisfies all of them is the Q&A order used, with a note
("points read in the Q&A order …"). No other permutation is tried, and a triangle is never
reordered: another apex would draw a different picture than the row asks for, so a wrong
triangle is reported (ADR-0003), not "fixed".

## Consequences
+ Every file of the lecturer reads without a single note; `edge_cases.csv` gives exactly the 16
  frames, black frames and warnings his comment lists (`EdgeCasesTest`).
+ Rectangles come out in his orientation (`PlaneResolverTest.headSlicesComeOutTheRightWayUp`).
+ A file written in the Q&A order still works when it is unambiguous.
+ The writer (`PlaneCsvWriter`) uses the same order, so the app's CSVs read back unchanged.
− A row that is valid in both orders is always read in the course order — deliberately.
− v3.0 documents and tests that described the "rails fallback" were rewritten (SRS REQ-05,
  EDD D13, TESTING B.2).
