# ADR-0008: Edit planes as centre + angles + size; draw the 3-D view with a software renderer

**Status:** Accepted · **Date:** 2026-11-03 · part 2 (what the model view shows) is superseded by
[ADR-0009](0009-cut-open-solid-and-point-picking.md); part 3 revised the same day; since
[ADR-0011](0011-the-video-is-what-play-shows.md) (v3.2) the app no longer offers interpolated
frames between list entries — `lerp` stays as tested code — and the CSV leaves the app only with
an export

## Context
The exercise is an *application*: load a model, cut it with a plane the user positions, see the
cut, collect the planes of a use case and extract its video. Two questions had no obvious
answer:

1. **How does a user move a plane?** The sheet defines a plane by 3 or 4 points, and the points
   must form an isosceles triangle / trapezoid within `EPS = 0.001`. Dragging one point breaks
   that rule immediately; typing twelve coordinates is not "positioning".
2. **How do we draw the model and the plane on it?** JavaFX 3D is no longer part of the JDK,
   OpenGL (JOGL/LWJGL) needs native jars per OS, and a volume-rendering library would be the
   largest dependency of the project. The build must stay offline and JDK-only (SRS C-3).

## Decision
1. `PlaneSpec` is the movable form of a plane: **centre (x, y, z), yaw / pitch / roll, width,
   height, top/bottom ratio** (0 % = triangle, 100 % = rectangle). `toPlane()` builds the sheet's
   points from it — by construction the rails are equal, the bases parallel and the top no wider
   than the bottom, so every slider position is a valid plane. `fromPlane()` is the inverse (a
   CSV row or three picked points load back into the sliders), and **interpolation between
   planes happens in this space** (`lerp`: shortest arc for the angles, linear for the rest),
   which keeps every in-between frame valid. The points themselves stay editable too (typed or
   picked, ADR-0009); `makeIsosceles` turns clicked points into a valid plane.
2. Everything in the 3-D view is drawn in software into `int[]` buffers (`Raster`: colour +
   depth, lines and triangles; `Camera3D`: orthographic orbit camera), blitted once into a
   `BufferedImage`. *v3.0 drew the volume as three textured mid-slices plus the plane textured
   with its cut; v3.1 replaces that by the cut-open solid of ADR-0009, on the same buffers.*
3. Planes that leave the app (v3.0: Save CSV and Export; since v3.2: the CSV written with an export) are written with **exact numbers** — the
   shortest decimal that reads back to the same `double`
   (`new BigDecimal(Double.toString(v)).toPlainString()`). The saved CSV therefore reads back to
   bit-identical planes, and the exported video equals the demo's rendering of that CSV byte for
   byte. `PlaneCsvWriter.quantize` (the plane exactly as the CSV reads back) stays the one place
   that guarantees this; with exact numbers it returns an equal plane. *(v3.0 rounded to 6
   decimals and rendered the rounded planes; the app's export of the lecturer's `head.csv` then
   differed from his rows in one frame, so exact numbers replaced the rounding.)*

## Consequences
+ The sliders cannot produce an invalid plane; picked points are made valid with one stated
  change ("c moved along a→c").
+ Smooth videos from a handful of key planes (REQ-23) without a single invalid frame.
+ Runs on any JDK 17+, Linux/Windows/macOS; headless CI builds it; `Raster`, `Camera3D`,
  `PlaneSpec` port to Android with `core` (only the final blit is AWT).
− Software rendering is CPU-bound; ADR-0009 lists the measures that keep the 3-D view
  interactive.
− Euler angles have a singularity at pitch ±90° (yaw and roll coincide); the sliders clamp pitch
  to ±90° and `fromPlane` resolves the ambiguity deterministically, which is enough for editing.
