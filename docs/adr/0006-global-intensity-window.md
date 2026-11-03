# ADR-0006: One percentile window per video

**Status:** Accepted · **Date:** 2026-10-23

## Context
Voxels are 8/16/32-bit (MRHead: int16, values 0…~1000 with rare bright outliers); video frames
are 8-bit. Per-frame min/max scaling makes the same tissue change brightness between frames
(flicker) and lets one hot voxel darken a whole frame.

## Decision
Compute one linear window for the whole run from the volume's 0.5 % and 99.5 % percentiles
(histogram of at most 2 M strided samples, 4096 bins), map linearly to 0…255 with clamping.
`--window LO HI` overrides it; a constant volume gets a 1-unit window.

## Consequences
+ Stable brightness across frames; outliers ignored; costs ~20 ms once per volume.
− A plane through an unusually dark/bright region uses less of the grey range than a
  per-frame window would; acceptable for a video, and the override exists.
