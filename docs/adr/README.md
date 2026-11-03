# Architecture Decision Records

One short record per decision that shaped the design (course deck 1: "ADR — why did we make
this one decision?"). Format: context → decision → consequences. Newest decisions get the next
number; superseded ones are kept and marked.

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-java-with-python-launcher.md) | Implement in Java, keep the sheet's `python Ex0.py` entry point as a launcher | Accepted |
| [0002](0002-core-app-split.md) | Split into a pure-Java `core` and a desktop `app` | Accepted |
| [0003](0003-invalid-rows-policy.md) | Every CSV row is one frame: doubtful rows drawn with a warning, bad rows black with a warning | Accepted (revised after the lecturer's `edge_cases.csv`) |
| [0004](0004-csv-point-order.md) | Read the points in the course's order `(a, b, c)` / `(a1, b, a2, c)` | Accepted (revised; the first version put the Q&A order first) |
| [0005](0005-built-in-mp4-writer.md) | Ship a dependency-free MP4 writer, use ffmpeg when present | Accepted |
| [0006](0006-global-intensity-window.md) | One percentile window per video | Accepted |
| [0007](0007-row-parallelism.md) | Parallelise by row blocks, default to one thread | Accepted |
| [0008](0008-plane-spec-and-software-renderer.md) | Edit planes as centre + angles + size; draw the 3-D view with a software renderer; write exact numbers | Accepted; part 2 superseded by 0009 |
| [0009](0009-cut-open-solid-and-point-picking.md) | Show the scan as a solid cut open by the plane; choose the plane by picking 3 points on the three standard views | Accepted |
