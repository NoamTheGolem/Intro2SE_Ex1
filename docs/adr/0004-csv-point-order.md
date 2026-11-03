# ADR-0004: Read 4 points in the documented order, fall back to the rails order

**Status:** Accepted · **Date:** 2026-11-03

## Context
The Q&A names the four points `(a1, a2, b, c)` with rails `a1→b` and `a2→c`; the CSV only has
columns `x1 … x4`. In the course's `Ex0.csv`, row 2 is a rectangle (valid in any reading) but
row 4, `(200,50,0) (200,0,500) (200,350,0) (200,400,500)`, is **not** isosceles as
`(a1, a2, b, c)` (rails 300 vs 400) and **is** a perfect isosceles trapezoid when the points are
read as two rails listed one after the other, `(a1, b) (a2, c)`: rails 502.49 each, bases
300 ≤ 400, coplanar. The sheet's own wording, "two vectors on the same plane with the same
length: a'→b and a''→c", suggests exactly that listing.

## Decision
`PlaneResolver` tries the **documented order first**; only if it violates the rules does it try
the other readings of the same four points (rails `p1→p2, p3→p4`, then `p1→p4, p2→p3`, each
oriented with the shorter base on top) and, for three points, the other two apex choices. The
first valid reading is used and reported as a note; if none is valid the documented order is
kept and the violations are reported (ADR-0003).

## Consequences
+ The teacher's row 4 is projected as the trapezoid he evidently meant, with a note saying so.
+ Valid files are never reinterpreted (documented order wins when it is valid).
− For rectangles both readings are valid, so the documented order decides the image
  orientation (rails along `a1→b`); the rails reading would give the transposed image.
  Documented in SDD §10 as an open question for the course staff.
