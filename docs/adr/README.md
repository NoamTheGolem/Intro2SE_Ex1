# Architecture Decision Records

One short record per decision that shaped the design (course deck 1: "ADR — why did we make
this one decision?"). Format: context → decision → consequences. Newest decisions get the next
number; superseded ones are kept and marked.

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-java-with-python-launcher.md) | Implement in Java, keep the sheet's `python Ex0.py` entry point as a launcher | Accepted |
| [0002](0002-core-app-split.md) | Split into a pure-Java `core` and a desktop `app` | Accepted |
| [0003](0003-invalid-rows-policy.md) | A CSV row that breaks the rules is reported and still becomes a frame | Accepted |
| [0004](0004-csv-point-order.md) | Read 4 points in the documented order, fall back to the rails order | Accepted |
| [0005](0005-built-in-mp4-writer.md) | Ship a dependency-free MP4 writer, use ffmpeg when present | Accepted |
| [0006](0006-global-intensity-window.md) | One percentile window per video | Accepted |
| [0007](0007-row-parallelism.md) | Parallelise by row blocks, default to one thread | Accepted |
