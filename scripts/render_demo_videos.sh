#!/usr/bin/env bash
# Renders the demo videos of docs/EXPERIMENT.md into results/. Needs only Java 17+.
set -euo pipefail
cd "$(dirname "$0")/.."
./Ex0 data/phantom_spheres_500.nrrd data/Ex0.csv results/Ex0_phantom500.mp4
./Ex0 data/MRHead.nrrd data/Ex0.csv results/Ex0_MRHead.mp4
for m in rotate sweep tilt triangle; do ./Ex0 data/MRHead.nrrd data/planes_$m.csv results/MRHead_$m.mp4; done
./Ex0 data/MRHead.nrrd data/planes_tilt.csv results/MRHead_tilt_trilinear.mp4 --interp trilinear
for p in spheres_u8 checker_u16 gradient_u32; do ./Ex0 data/phantom_$p.nrrd data/planes_phantom_rotate.csv results/phantom_$p.mp4; done
