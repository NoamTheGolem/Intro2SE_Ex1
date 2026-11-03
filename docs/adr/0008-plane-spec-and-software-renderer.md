# ADR-0008: Edit planes as centre + angles + size; draw the model with a software rasterizer

**Status:** Accepted · **Date:** 2026-11-03

## Context
The exercise is an *application*: load a model, cut it with a plane the user positions, see the
cut, collect the planes of a use case and extract its video. Two questions had no obvious
answer:

1. **How does a user move a plane?** The sheet defines a plane by 3 or 4 points, and the points
   must form an isosceles triangle / trapezoid within `EPS = 0.001`. Dragging one point breaks
   that rule immediately; typing twelve coordinates is not "positioning".
2. **How do we show the model and the plane on it?** JavaFX 3D is no longer part of the JDK,
   OpenGL (JOGL/LWJGL) needs native jars per OS, and a volume-rendering library would be the
   largest dependency of the project. The build must stay offline and JDK-only (SRS C-3).

## Decision
1. `PlaneSpec` is the editable form of a plane: **centre (x, y, z), yaw / pitch / roll, width,
   height, top/bottom ratio** (0 % = triangle, 100 % = rectangle). `toPlane()` builds the sheet's
   points from it — by construction the rails are equal, the bases parallel and the top no wider
   than the bottom, so every slider position is a valid plane. `fromPlane()` is the inverse (a
   CSV row loads back into the sliders), and **interpolation between planes happens in this
   space** (`lerp`: shortest arc for the angles, linear for the rest), which keeps every
   in-between frame valid. Presets (XY, XZ, YZ, Triangle, Oblique) are specs too.
2. The model view is a **~400-line software z-buffer renderer** (`Raster`: textured and solid
   triangles, depth-tested lines; `Camera3D`: orthographic orbit). It draws the bounding box and
   axes, the volume's three middle slices as textures, and the cutting plane **textured with its
   own cut** in its true position. Everything is `int[]` ARGB blitted once into a `BufferedImage`.
3. Planes that leave the app (Save CSV, Export) are first **quantized** to the CSV precision
   (`PlaneCsvWriter.quantize`), so the exported video equals the demo's rendering of the saved
   CSV byte for byte.

## Consequences
+ No invalid plane can be produced by the UI; no "fix my points" dialogs.
+ Smooth videos from a handful of key planes (REQ-23) without a single invalid frame.
+ Runs on any JDK 17+, Linux/Windows/macOS, headless CI builds it; `Raster`/`Camera3D`/`PlaneSpec`
  port to Android with `core` (only the final blit is AWT).
− The model is shown as three slices in a box, not a shaded surface; acceptable because the
  object of the exercise is the cut, which is shown both on the plane and as an image. Ray
  casting is future work.
− Software rendering is CPU-bound: ~5–15 ms per frame at 800×600 on one core, fine for an
  orbiting view; it would not do for a full-screen volume renderer.
− Euler angles have a singularity at pitch ±90° (yaw and roll coincide); the sliders clamp pitch
  to ±90° and `fromPlane` resolves the ambiguity deterministically, which is enough for editing.
