# ADR-0007: Parallelise by row blocks, default to one thread

**Status:** Accepted · **Date:** 2026-11-03

## Context
The FPS requirement is met single-threaded, but the course covers concurrency (weeks 4–5,
"Java: thread pools") and the viewer benefits from lower latency. Rows of the output image are
independent and write disjoint pixel ranges.

## Decision
`ParallelProjector` decorates any projector: it splits the `H` rows into at most `threads`
contiguous blocks (≥ 8 rows each) and runs them on a fixed daemon thread pool, then waits for
all blocks. The result is bit-identical to the single-threaded projector (tested). The CLI
default is **1 thread**, so the numbers we report are the ones the sheet asks for;
`--threads N|auto` enables the pool; the viewer uses all cores.

## Consequences
+ Near-linear speed-up on the memory-friendly nearest-neighbour path (2 cores: 281 → 395 FPS).
+ No locks, no races: each pixel is written by exactly one thread.
− A pool per projector; the viewer shuts it down when the projector is replaced.
