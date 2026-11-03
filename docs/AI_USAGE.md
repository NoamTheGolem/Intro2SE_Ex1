# AI Usage Log

The course allows AI coding tools and asks that their use be disclosed in pull requests and in
the project's AI usage log. This is that log. Tool: **Claude** (Anthropic), used as a coding
agent in the Claude app, directed by the team in conversation. We own every line; the "what we
did" column is how we made sure we can explain it.

| # | Date | Step | What the AI did | What we decided / checked |
|---|------|------|-----------------|---------------------------|
| 1 | 2026-10-23 | Requirements | Read the assignment, listed the ambiguities in the course algorithm (apex row, division by zero, rounding of `x`, frame size, grey mapping) | Chose Java (we plan to reuse the code in Android Studio), English docs, build locally first |
| 2 | 2026-10-23 | Design | Drafted the SRS and the algorithm specification with decisions D1–D12 | Reviewed each decision against the sheet's Q&A |
| 3 | 2026-10-23 | Architecture | Proposed the `core` / `app` split, the template-method `Projector`, the `VideoWriter` port | Agreed — needed for the Android follow-up |
| 4 | 2026-10-23 | Implementation | Wrote the Java code in our house style (Allman braces, Javadoc everywhere, no lambdas/streams) | Read the code; asked for explanations of the inner loop and the NRRD parsing |
| 5 | 2026-10-23 | Testing | Wrote JUnit tests with known-answer volumes; found and fixed a wrong benchmark checksum and a wrong test expectation (a rounding tie) | Ran the tests ourselves |
| 6 | 2026-10-23 | Experiment | Ran benchmarks (100³–1000³) and the demo on MRHead; built contact sheets | Verified frame counts with `ffprobe`, watched the videos |
| 7 | 2026-10-23 | Usability | Added the window app (drag & drop, live playback, export) and the pure-Java MP4 writer so no ffmpeg or terminal is needed; verified the writer by decoding its output with ffmpeg pixel by pixel | We asked for an app that "just runs" after ffmpeg was missing on our PC |
| 8 | 2026-10-25 | Review | Re-checked the solution against the PDF line by line and **found that it could not read the course's own `Ex0.csv`** (count column, quoted cells, two rows that break the rules). Fixed the reader, changed the policy to one frame per row with warnings (ADR-0003), added the 500³ phantom and the `Ex0` wrappers | We noticed the solution did not match the assignment and asked for a careful review |
| 9 | 2026-11-03 | Course alignment | Read the course repo, syllabus, TA slides and the four class decks; restructured the documents to the lecturer's templates (SRS with use cases + traceability, SDD, EDD, STP/STD, ADRs, README template); added `Ex0.py`, `--threads` (`ParallelProjector`), `PlaneResolver` for the point-order question (ADR-0004), GitHub Actions CI on Linux + Windows, LICENSE, CONTRIBUTING, PR template | We asked for a full review against what the course intends; we set up the GitHub repository |

## What we verified ourselves

* The projection matches the course algorithm (triangle shape, centring, apex/base rows).
* The demo command on our own PC (Windows 11, JDK from IntelliJ) and on the course CSV.
* Performance numbers by running `Benchmark` on our machine: *(fill in: CPU, FPS)*.
* We can explain every class in `core` and the error handling in `Ex0` (see the walkthrough
  we prepared for the presentation).

## Where the AI was wrong and we caught it

* v1.0 refused the course's `Ex0.csv` entirely ("no planes found") — a format guess that was
  never tested against the real file. Lesson: test against the teacher's artefacts first.
* v1.0's fail-fast policy would have produced no video for a file with one bad row; item 7c
  asks for a frame per row.
* A unit test expected `grey(150) == 128` for the window `[100, 200]`; 127.5 is a rounding tie
  that floating point resolves to 127. The test, not the code, was wrong.
