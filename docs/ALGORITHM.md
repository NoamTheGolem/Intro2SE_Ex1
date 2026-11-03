# Algorithm — Engineering Design Document (EDD) of the projection component

**Component:** `ex1.core` (`Plane`, `PlaneResolver`, `Projector` family, `IntensityWindow`)
**Implements:** SRS REQ-05, REQ-06, REQ-09 … REQ-13, NFR-01, NFR-02, NFR-05
**Architecture context:** [SDD](SDD.md) §3–4 · **Tests:** [TESTING.md](TESTING.md) TC-01 … TC-63

This document turns the course's informal algorithm into an exact, implementable
specification (To-Do item 3). Every place where the original text is ambiguous is listed in
**§5 Design decisions**, with the choice we made and why; the larger choices also have an
[ADR](adr/). §8 lists the alternatives we rejected, §9 the error handling, §10 the performance
budget.

---

## 1. Inputs and outputs

| Item | Definition |
|------|------------|
| Volume `M` | `sx × sy × sz` samples, `1 ≤ sx, sy, sz < 2^10`. `M[x][y][z]` is an integer of 8, 16 or 32 bits. Stored flat: `index = x + sx·(y + sy·z)` (x varies fastest — the NRRD order). |
| Plane `p` | **Triangle** `(a, b, c)` with `|ab| ≈ |ac|`, or **trapezoid** `(a1, a2, b, c)` with `|a1 b| ≈ |a2 c|`, `|a1 a2| ≤ |b c|`, all four points coplanar. Coordinates are **voxel-index coordinates** (same space as `x, y, z` above), may be fractional. |
| Output `E` | 2D image, `W × H` pixels, one sample of `M` per pixel. |

A triangle is just a trapezoid whose top edge has zero length
(`a1 = a2 = a`), so the implementation handles **one** shape: the trapezoid.

---

## 2. The course algorithm, restated

The plane is scanned row by row:

* Two "rails" start at the top: `left` walks from `a1` to `b`, `right`
  walks from `a2` to `c`, each by **one unit per row**.
* Every row is the segment `left → right`, sampled every **one unit**.
* Each row is **centred** horizontally in the output image.

```
        a1 ●───────● a2          row 0      (short, centred)
          /         \
         /           \           row y      (width grows linearly)
        /             \
     b ●───────────────● c       last row   (width = |bc| = W)
```

---

## 3. Exact specification

```
INPUT  a1, a2, b, c   (triangle: a1 = a2 = a)

L   ← |b − a1|                       // rail length = Dab
v1  ← (b − a1) / |b − a1|            // unit step of the left rail
v2  ← (c − a2) / |c − a2|            // unit step of the right rail
H   ← floor(L) + 1                   // rows y = 0 … floor(L)
W   ← floor(max(|a2 − a1|, |c − b|)) + 1

E   ← W × H image filled with 0

for y ← 0 … H − 1 {
    left  ← a1 + y·v1                // computed directly, not accumulated
    right ← a2 + y·v2
    d     ← |right − left|           // row length (d12)
    n     ← min(floor(d) + 1, W)     // samples in this row
    if d > 0 { dp ← (right − left) / d } else { dp ← 0 }
    x0    ← (W − n) / 2              // integer division → row is centred

    p ← left
    for i ← 0 … n − 1 {
        E[x0 + i][y] ← sample(M, p)  // 0 when p is outside M
        p ← p + dp
    }
}
return E
```

### 3.1 Sampling `sample(M, p)`

* **Nearest neighbour** (default, what the course text means by `M[p]`):
  `M[round(px)][round(py)][round(pz)]`, or `0` if any rounded index is
  outside `[0, s)`.
* **Trilinear** (optional, `--interp trilinear`): weighted average of the
  8 surrounding voxels, `0` if `p` is outside `[0, s − 1]` on any axis.
  Smoother output for oblique planes, ~2–3× slower.

---

## 4. Why it is fast

### 4.1 Cost model

For an output image of `W × H` pixels the work is

* `O(H)` per-row setup (a few square roots), plus
* `O(W·H)` samples, each **3 additions + 3 compares + 1 array read**
  (nearest neighbour).

Nothing depends on the volume size — a 500³ volume costs the same per
frame as a 50³ one; only the plane size matters.

### 4.2 Key observation: the mapping is (almost) affine

For an isosceles trapezoid the row direction `dp` is the **same for every
row** (it is always parallel to `bc`), so pixel `(x, y)` maps to

```
p(x, y) = origin + x·dp + y·u          (u, dp constant vectors)
```

That is why the inner loop can be a pure increment `p ← p + dp` with no
per-pixel multiplications or square roots. We still recompute `left` and
`dp` once per row (cheap, `O(H)`) so the code stays correct even if the
input is only *approximately* isosceles (within `EPS`).

### 4.3 Numbers

A worst-case plane inside a 500³ volume is roughly 700 × 700 ≈ 0.5 M
pixels. At ~1–2 ns per nearest-neighbour sample that is ~1 ms per frame,
i.e. several hundred FPS on one core — comfortably above the 30 FPS
requirement and the 100 FPS target. Measured numbers are in
`docs/EXPERIMENT.md`.

---

## 5. Design decisions (where the course text is ambiguous)

| # | Ambiguity in the course text | Our decision | Reason |
|---|------------------------------|--------------|--------|
| D1 | Step 5a advances `a1` **before** the first row, so the apex `a` is never sampled and `b` is never reached. | Row `y` uses `left = a1 + y·v1`, `y = 0 … floor(L)`. | Includes both ends of the plane; image height is `floor(L)+1`. |
| D2 | `p ← p + dp` and `a1 ← a1 + v` accumulate floating-point error. | Rail points are computed directly from `y`; only the short inner loop increments. | Error stays bounded per row instead of growing over the image. |
| D3 | Row 0 of a triangle has `d12 = 0` → division by zero in `dp = vector/d12`. | If `d = 0`, `dp = 0` and the row has one sample. | Robustness. |
| D4 | `x = Dbc/2 − d12/2 + x1` can be fractional or fall outside `E`. | `x0 = (W − n) div 2` with `n = min(floor(d)+1, W)`. | Always an integer inside `[0, W)`. |
| D5 | "Size `Dab × Dbc`" is fractional, and for a non-isosceles input a row can be longer than `Dbc`. | `H = floor(Dab)+1`; `W = floor(max(row 0, last row, Dbc))+1` (the rail distance is convex in `y`, so the widest row is the first or the last). | One pixel per unit step, both ends included, every row fits. |
| D6 | Value of pixels **outside the triangle** (corners of `E`). | `0` (black), same as outside `M`. | Matches "if p is NOT in M, v = 0". |
| D7 | How to turn 16/32-bit values into a video frame. | One **global** window `[lo, hi]` for the whole video (0.5 % – 99.5 % percentiles of the volume), linear map to 0–255. User may override with `--window lo hi`. | Same tissue → same grey in every frame (no flicker); robust to outlier voxels. |
| D8 | Frames from different planes have different sizes, but a video has one size. | Video size = max `W`, max `H` over all planes (rounded up to even for H.264); each frame is centred on black. | Keeps every frame at true 1 pixel = 1 voxel scale. |
| D9 | Coordinate system of the CSV points (the course's `Ex0.csv` uses values 0…500, i.e. voxel indices of a 500³ volume). | Voxel-index space `[0, sx) × [0, sy) × [0, sz)`. | Matches the problem definition `M[x][y][z]`. Physical (mm / RAS) coordinates are out of scope. |
| D10 | Anisotropic voxels (e.g. MRHead: 1 × 1 × 1.3 mm). | Distances are measured in voxel units, as in the definition. | Faithful to the spec; the image may look slightly stretched along that axis. Listed as future work. |
| D11 | Invalid CSV rows (the course's own `Ex0.csv` has two non-isosceles rows). | Every row = one frame. Rule violations → warning with line number, frame still computed with the algorithm's rails (`a1→b`, `a2→c`, `Dab` rows). Not computable (NaN, zero-length side, wrong number count) → black frame + warning. `--strict` restores fail-fast (exit 2, no video). | Item 7c requires a frame per plane in list order; the algorithm itself is well defined even when `|ab| ≠ |ac|`, so dropping the frame would lose information. |
| D12 | `EPS` scale for coplanarity of 4 points. | Distance of `c` from plane `(a1, a2, b)` must be `< EPS · max(1, L)`. | An absolute `0.001` is meaningless for long edges; a relative tolerance is scale-free. |
| D13 | Which CSV column is which point. The sheet names `(a1, a2, b, c)`; the course's `Ex0.csv` row 4 is only isosceles when read as two rails `(p1→p2, p3→p4)`. | `PlaneResolver`: documented order if valid; otherwise the first valid alternative reading (rails order for 4 points, another apex for 3 points), reported as a note; otherwise documented order + warnings. | Accept the teacher's file as he evidently meant it, without silently changing valid inputs ([ADR-0004](adr/0004-csv-point-order.md)). |
| D14 | Single- or multi-threaded? The FPS requirement says nothing about threads. | Default 1 thread (the number we report); `--threads N|auto` splits **rows** into contiguous blocks on a fixed pool. | Rows write disjoint pixels → no locks, bit-identical output; blocks keep cache locality ([ADR-0007](adr/0007-row-parallelism.md)). |

---

## 6. Input validation (Q&A of the assignment, `EPS = 0.001`)

**Triangle** `(a, b, c)`
1. `| |ab| − |ac| | < EPS`
2. `|ab| ≥ 1` (at least two rows; otherwise the plane is degenerate)
3. `a, b, c` not collinear

**Trapezoid** `(a1, a2, b, c)`
1. `| |a1 b| − |a2 c| | < EPS`
2. `|a1 a2| ≤ |b c| + EPS`
3. `a1, a2, b, c` coplanar (decision D12)
4. `|a1 b| ≥ 1`

---

## 7. Complexity summary

| Step | Time | Memory |
|------|------|--------|
| Load volume (once) | `O(sx·sy·sz)` | one typed array (1, 2 or 4 bytes/voxel) |
| Window computation (once) | `O(sample)` ≤ 2 M voxels | 64 KB histogram |
| One projection | `O(W·H)` (÷ threads with `--threads`) | `W·H` floats |
| Map to 8-bit | `O(W·H)` | `W·H` bytes |

---

## 8. Alternatives considered

| Alternative | Why not |
|-------------|---------|
| **Inverse mapping per pixel** (`p = origin + x·dp + y·u` computed for every pixel) | Two extra multiply-adds per pixel for no gain; the incremental form is exact enough because each row restarts from a directly computed rail point (D2). |
| **Precomputed slice cache / resampling the whole volume** | Memory ×2 and no benefit: each frame touches only `W·H` voxels. |
| **Always trilinear** | 3× the cost; the sheet's `M[p]` is nearest neighbour. Kept as an option. |
| **Per-pixel tasks / parallel streams** | Task overhead dwarfs a 2–30 ns pixel; row blocks of ≥ 8 rows amortise it. |
| **GPU (CUDA/OpenCL)** | Out of scope for Ex1; listed by the course as an Ex5 option. The row structure maps 1:1 to a 2-D kernel. |
| **Reject the course's row 4** (`(a1,a2,b,c)` only) | Would turn the teacher's own example into a warning; the rails reading is unambiguous for that file. |

## 9. Error handling and edge cases

| Situation | Behaviour | Where |
|-----------|-----------|-------|
| Point outside `M` | sample = 0 | `NearestProjector`, `TrilinearProjector` |
| Row 0 of a triangle (`d = 0`) | one sample at the apex, no division | `Projector.projectRows` |
| Rail of length 0, NaN, or side > 8192 | not renderable → black frame + warning | `Plane.isRenderable`, `PlaneCsvReader` |
| Non-isosceles input | rendered with the algorithm's rails; width covers the longest row (D5) | `Plane.imageWidth` |
| Image buffer smaller than the plane | `IllegalArgumentException` (programming error, not input) | `Projector.project` |
| Constant volume (window `hi == lo`) | window widened by 1 so no division by zero | `IntensityWindow.fromPercentiles` |
| `uint32` values above 2³¹ | read through a `long` mask; float conversion keeps 24-bit precision, enough for 8-bit output | `IntVolume.valueAt` |
| Worker thread throws | exception rethrown on the caller's thread; pool is daemon so the JVM can always exit | `ParallelProjector.waitAll` |

## 10. Performance budget (500³ volume, one thread)

| Item | Budget | Measured (2-vCPU VM) |
|------|--------|----------------------|
| Nearest-neighbour sample | ≤ 10 ns | 3 ns (cache-resident) … 12.7 ns (1000³) |
| Frame of 500×560 px | ≤ 10 ms (≥ 100 FPS) | 2.5–3.6 ms (280–395 FPS) |
| Trilinear frame | ≤ 33 ms (≥ 30 FPS) | 8.4–9.1 ms (110–119 FPS) |
| Grey mapping of a frame | ≤ 1 ms | ~0.3 ms |
| Volume load, 256×256×130 gzip | ≤ 1 s | ~0.3 s |

Numbers: [EXPERIMENT.md](EXPERIMENT.md).
