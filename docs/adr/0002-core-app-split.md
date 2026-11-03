# ADR-0002: Split into a pure-Java `core` and a desktop `app`

**Status:** Accepted · **Date:** 2026-10-23

## Context
The algorithm is the graded part and must be testable without files or windows. The team plans
an Android port, where `java.io.File`, AWT and Swing are unavailable or unsuitable.

## Decision
Two Gradle modules. `core` contains `Volume`, `Plane`, `PlaneResolver`, the `Projector` family,
`Image2D` and `IntensityWindow`, and may use only `java.lang`, `java.util` and
`java.util.concurrent`. `app` contains every adapter (NRRD, CSV, MP4/PNG writers), the demo
main, generators, benchmark and the Swing app (`StudioApp`). `app` depends on `core`, never the reverse
(ports and adapters: `VideoWriter` is the port, three writers are adapters).

## Consequences
+ 45 of the 90 tests run on `core` alone with in-memory volumes.
+ Android: only new adapters are needed (`MediaCodec` writer, content-URI NRRD source).
− Slightly more ceremony (two source trees, `FrameRenderer` as the shared glue in `app`).
