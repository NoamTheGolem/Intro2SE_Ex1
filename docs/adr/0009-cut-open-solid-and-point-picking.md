# ADR-0009: Show the scan as a solid cut open by the plane; choose the plane by picking 3 points on Slicer's three views

**Status:** Accepted · **Date:** 2026-11-03 · supersedes part 2 of
[ADR-0008](0008-plane-spec-and-software-renderer.md)

## Context
The sheet's Figure 1 is 3D Slicer: three orthogonal views (axial, coronal, sagittal) and a 3-D
view. The lecturer described what our app adds to that picture: you **choose 3 points**, and you
see **how the 3-D object looks inside that cut — like a pie cut in the middle, where you see the
part that got cut from the inside**. His Drive folder has five scans for it (an MRI head, an
onion, a croissant, a swiss roll, a biscuit cake).

App v3.0 drew the volume as three textured mid-slices in a box plus the plane textured with its
cut. That shows the cut, but not the *object* around it: you cannot see where the head is cut or
what the inside looks like next to the outside. And a plane chosen by sliders or typed numbers
is not "choosing 3 points".

## Decision
1. **A solid model** (`SolidModel`), built once per scan in a background thread:
   grey values through the video's intensity window, averaged down to at most 256 voxels per
   side; a 1-2-1 smoothing; a surface threshold (Otsu's split by default, the **Surface** slider
   changes it); bright specks in the air removed; then everything the outside air cannot reach
   from the border of the volume is solid, with the air not allowed through gaps narrower than
   3 voxels (a morphological closing). So dark parts *inside* the object — the ventricles and the
   dark bone of an MRI skull, the air pockets of a croissant — stay part of it and appear on the
   cut face as they are. 8³ blocks remember whether they contain anything, for empty-space
   skipping.
2. **A cut-open ray caster** (`CutawayRenderer`): one ray per pixel, orthographic, traced in the
   volume's index space. The half of the object on the viewer's side of the cutting plane is
   removed. Where a ray enters the kept half *through the plane* inside the object, the pixel is
   the scan's own grey value at that point (full resolution, nearest voxel) — the inside of the
   pie; inside the triangle/trapezoid (what goes into the video) as is, outside it darker and
   bluish. Elsewhere the ray stops at the surface, which is shaded (ambient + diffuse + fill +
   a little specular, normal from the smoothed grey gradient). Rows are split over a thread pool
   (all cores — the 3-D view is not the measured projection); while anything moves the view
   renders at half resolution and sharpens 160 ms after the last change. The ray caster writes
   the depth buffer of `Raster`, so the box, the plane's outline and its points are hidden behind
   the object where they should be. **Cut open** can be switched off to see the whole object.
3. **The patient's axes** (`VolumeGeometry`): the NRRD's `space` and `space directions` give
   millimetres and anatomical directions, so the views are **Axial** (from above), **Coronal**
   (from the front) and **Sagittal** (from the side) with A/P, R/L, S/I labels, as in Slicer and
   the lecturer's images. Without that header everything falls back to the index axes. The CSV
   stays in voxel indices (EDD D9) — only the display uses millimetres.
4. **Three slice views** (`OrthoView`) with a slider each; on them the line where the cutting
   plane crosses that slice, the points (filled when close to the slice) and the crosshair of the
   other two slices.
5. **Choosing the plane by points.** *Pick a, b, c* (or *a1, a2, b, c*): click the points on any
   slice view or on the 3-D object (its surface or the cut face). Clicks are never exactly
   isosceles, so `PlaneSpec.makeIsosceles` keeps `a` and `b` and slides `c` along `a→c` until
   `|ac| = |ab|`; for 4 points it takes the nearest isosceles trapezoid (same centre, turn and
   size). The app says what it changed. Each point can also be re-picked alone or typed.
   *Axial / Coronal / Sagittal* put the plane on the current slice of that view and turn the
   camera to face it.

## Alternatives considered
| Alternative | Why not |
|-------------|---------|
| Marching cubes mesh + z-buffer | A 256³ head gives 1–2 M triangles: slow to build and draw in software, and the cut needs a "cap" polygon filled with the scan's grey — exactly what the ray caster gets for free |
| Direct volume rendering (transfer functions, semi-transparent) | Pretty, but the inside is a translucent fog, not a clean cut face; needs per-dataset tuning |
| Clip only the triangle's area out of the surface | Looks like a window, not a cut; the lecturer's picture is the pie cut through |
| OpenGL / JavaFX | Rejected in ADR-0008 (native jars, not in the JDK) |
| Picking with a 3-D gizmo | Harder to place accurately than clicking on slices, which is how Slicer users place points |

## Consequences
+ The user sees the object, the cut through it and its inside, from any angle — on all five of
  the lecturer's scans with the default threshold (`docs/img/cut_open_gallery.png`).
+ Measured on a 2-vCPU VM (EXPERIMENT §7): model 0.55–0.92 s for 256×256×130, 2.6 s for 500³;
  a 520×340 frame 14–26 ms with 2 threads once warm (4–7 ms at half resolution).
+ JDK only, `int[]` buffers — the same Android path as ADR-0008.
− The surface is a threshold: thin or noisy parts can look rough or have holes; the Surface slider
  is the escape hatch.
− Large scans are modelled at reduced size (croissant 500³ → 250³), so its skin is coarser; the
  cut face is still sampled from the full scan.
− One more representation of the scan in memory (≤ 256³ bytes ×2 ≈ 32 MB).
