# Experiment and Results (To-Do item 8)

**Verifies:** NFR-01 (≥ 30 / 100 FPS), NFR-02 (threads), REQ-07 (course CSV), REQ-17 (dataset)
**Videos:** [`results/`](../results/) · **Reproduce:** `scripts/render_demo_videos.sh` (or `.bat`) and
`java -cp build/Ex0.jar ex1.app.Benchmark N`

## 1. Setup

| Item | Value |
|------|-------|
| Machine | Cloud Linux VM, **2 vCPU** (shared, noisy), 7 GB RAM — deliberately a weak machine; a laptop is faster |
| JVM | OpenJDK 21, default flags (`-Xmx3g` only for the 1000³ volume) |
| Timing | `System.nanoTime`; 100 warm-up frames, then 300 timed frames; file I/O and encoding excluded |
| Planes | 64 random oblique isosceles triangles with side ≈ N (large and oblique = the worst realistic case) |
| Threads | 1 (the requirement) and 2 (all cores of this VM) |

## 2. Performance (NFR-01: ≥ 30 FPS required, ≥ 100 FPS expected at 500³)

| Volume | Plane (px) | NN, 1 thread | TL, 1 thread | NN, 2 threads | TL, 2 threads | ns/pixel (NN, 1 thr) |
|-------:|-----------:|-------------:|-------------:|--------------:|--------------:|---------------------:|
| 100³ | 101×112 | 16 900 FPS | 4 489 | 9 512 | 5 600 | 5.3 |
| 250³ | 251×280 | 3 432 | 856 | 2 074 | 746 | 4.2 |
| **500³** | **501×560** | **281 – 436** | **110 – 134** | 372 – 395 | 149 – 165 | 8.2 – 12.7 |
| 1000³ | 1001×1119 | 98 | 27 | 97 | 36 | 9.2 |

Ranges at 500³ are from three runs on different days (the VM is shared); the lowest value is the
one to quote.

**Result:** at 500³, nearest neighbour runs at **≥ 280 FPS single-threaded** — 2.8× the
expected 100 FPS and 9× the required 30 — and trilinear also clears 100 FPS. Even the largest
allowed volume (~1000³) stays near 100 FPS with nearest neighbour.

**Observations**

1. *Cost per pixel rises with volume size* (4 → 9 → 13 ns) although the arithmetic is identical:
   a 100³ volume (1 MB) lives in cache, a 500³ one (125 MB) does not, and an oblique plane jumps
   across z-slices that are `sx·sy` bytes apart. The algorithm is `O(W·H)` as designed (EDD §4);
   what grows is cache misses per sample.
2. *Threads help the compute-bound path, not the memory-bound one, on this VM.* Trilinear
   (≈ 27 ns of arithmetic per pixel) gains 1.2–1.35× with 2 threads; nearest neighbour is limited
   by memory bandwidth and shows no gain (even a loss at 100³, where a frame takes 60 µs and the
   hand-off to the pool costs more than it saves — the `ParallelProjector` keeps planes under 16
   rows on the calling thread for that reason). On a laptop with 4–8 real cores and a larger
   cache the picture changes; the output is bit-identical either way (TC-56), so the default
   stays at one thread and `--threads auto` is an opt-in.

## 3. The course's example CSV (`Ex0.csv`)

```
python Ex0.py data/phantom_spheres_500.nrrd data/Ex0.csv results/Ex0_phantom500.mp4
python Ex0.py data/MRHead.nrrd             data/Ex0.csv results/Ex0_MRHead.mp4
```

Console (both runs):

```
Warning : row 3 (line 4): side lengths differ: |ab| = 497.3932, |ac| = 476.8648 (must differ by < 0.001)
Warning : row 3 (line 4): projected anyway with the algorithm's rails a1->b, a2->c
Warning : row 4 (line 5): points are not valid in the documented order; read as two rails p1->p2 and p3->p4: a1=p1, a2=p3, b=p2, c=p4
Volume  : phantom_spheres_500.nrrd 500x500x500 UINT8
Planes  : 4 from Ex0.csv
Frames  : 502x504 @ 10 fps, nearest, 1 thread(s)
```

Both videos have **exactly 4 frames** (`ffprobe`), one per CSV row, in order (REQ-07, TC-31).

**On the 500³ phantom** (the coordinates of `Ex0.csv` go up to 500, so this is the box they
describe). Left to right: row 1 isosceles triangle in the plane `y = 200`; row 2 rectangle
`x = 200`; row 3 the non-isosceles triangle — visibly lopsided, exactly as its rails define it;
row 4 the trapezoid read as two rails — symmetric, short base on top, as an isosceles trapezoid
should look:

![Ex0.csv on 500^3](img/ex0_phantom500.png)

**On MRHead (256×256×130):** most of each plane lies outside the volume and is black ("if p is
NOT in M, v = 0"); the part inside shows the head:

![Ex0.csv on MRHead](img/ex0_mrhead.png)

*The FPS that `Ex0` prints for a 4-frame run (60–180) includes JIT warm-up of a cold JVM; the
steady-state figure is the benchmark in §2.*

## 4. Demo runs on real data (MRHead.nrrd, 256×256×130, int16, from 3D Slicer)

```
python Ex0.py data/MRHead.nrrd data/planes_<mode>.csv results/MRHead_<mode>.mp4
```

| CSV | Planes | Frame size | Projection time | Video |
|-----|-------:|-----------:|----------------:|-------|
| `planes_sweep.csv` (rectangles, z = 0…129) | 130 | 256×256 | 100 ms (1 300 FPS) | `results/MRHead_sweep.mp4` |
| `planes_rotate.csv` (0…180° around y) | 90 | 288×256 | 148 ms (607 FPS) | `results/MRHead_rotate.mp4` |
| `planes_tilt.csv` (−60…60° around x) | 90 | 256×288 | 81 ms (1 112 FPS) | `results/MRHead_tilt.mp4` |
| `planes_triangle.csv` (triangles, 0…360°) | 72 | 232×258 | 57 ms (1 274 FPS) | `results/MRHead_triangle.mp4` |
| `planes_tilt.csv`, `--interp trilinear` | 90 | 256×288 | 252 ms (357 FPS) | `results/MRHead_tilt_trilinear.mp4` |

![MRHead frames](img/mrhead_contact.png)

*Rows: sweep (sagittal slices), rotate (sagittal → coronal), tilt, rotating triangles — the
triangle row shows the exact shape the course algorithm defines.*

## 5. Synthetic dataset (phantoms with known answers)

| File | Type | Content | Expected cross-section | Seen |
|------|------|---------|------------------------|------|
| `phantom_spheres_u8.nrrd` | uint8 | nested balls | concentric rings at **every** angle | ✔ |
| `phantom_checker_u16.nrrd` | uint16 | 16³ checkerboard | squares when axis-aligned, stretched stripes when rotated | ✔ |
| `phantom_gradient_u32.nrrd` | uint32 (values up to 4·10⁹) | `x + y + z` ramp | smooth diagonal ramp | ✔ |

![Phantoms](img/phantoms.png)

## 6. Nearest neighbour vs trilinear

Nearest neighbour shows stair-step edges on oblique planes (each pixel snaps to one voxel);
trilinear is visibly smoother at ~3× the cost. Both meet the target at 500³, so trilinear is a
free quality upgrade when there is FPS headroom.

![Nearest (left) vs trilinear (right), zoomed ×3](img/nearest_vs_trilinear.png)

## 7. Other observations

1. **Anisotropic voxels (EDD D10).** MRHead has 1.3 mm spacing on one axis. Distances are
   measured in voxel units, as the assignment defines, so coronal views in the rotate video look
   ~23 % narrower than reality. Future work: scale axes by the NRRD `space directions`.
2. **Course algorithm's off-by-one (EDD D1).** Implemented literally, step 5a skips the apex row
   and never reaches `b`. We sample rows `0 … floor(Dab)`, so both ends appear (TC-53).
3. **Global window (ADR-0006).** One window per video avoids brightness flicker between frames;
   the 0.5/99.5 % percentiles ignore the few very bright voxels (fat, vessels).
4. **Built-in MP4 size (ADR-0005).** `Ex0_MRHead.mp4` is 20 KB with ffmpeg and ~1.5 MB with the
   built-in lossless writer; the 130-frame sweep grows from 1.8 MB to ~13 MB. Acceptable for a
   demo; ffmpeg is picked up automatically when installed.

## 8. Future work
* GPU (CUDA) version — the course's Ex5 option 5.1; the row structure maps directly to a 2-D kernel.
* Physical (mm) coordinates from `space origin` / `space directions`.
* Android front end reusing `core` unchanged (interactive plane control as a game).
