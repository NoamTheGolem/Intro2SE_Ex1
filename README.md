# Ex1 — Plane Projection of a 3D Volume

Intro to Software Engineering (OOP/OOD), Ariel University — Exercise 1.

**Problem.** Given a 3-D raster `M` (`sx × sy × sz`, unsigned 8/16/32-bit voxels, each side < 2^10)
and a plane `p` given by 3 points (isosceles triangle) or 4 points (isosceles trapezoid), compute
the 2-D image of the projection of `p` in `M`; a CSV list of planes becomes an MP4 video, one
frame per plane. Required: correct and stable, ≥ 30 FPS (must) / ≥ 100 FPS (expected) on 500³.

**Scope.** NRRD input, CSV planes per the course format, nearest/trilinear sampling, MP4 output
without external software, benchmark, dataset, tests. Out of scope: physical (mm) coordinates,
colour/4-D volumes, GPU.

Documents live in `docs/` (SRS, SDD, algorithm/EDD, testing, experiment, ADRs); the demo is
`python Ex0.py file.nrrd file.csv file.mp4`.
