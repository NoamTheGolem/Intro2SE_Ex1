# Testing Strategy — Test Plan (STP) and Test Descriptions (STD)

**Project:** Ex1 — Plane Projection of a 3D Volume · **Version:** 2.0
**Verifies:** every REQ/NFR of the [SRS](SRS.md) (traceability in SRS §5) · **Design under test:** [SDD](SDD.md), [EDD](ALGORITHM.md)

---

## Part A — Test Plan (STP)

### A.1 Scope and items under test
`core` (geometry, projection, windowing), `ex1.io` (NRRD, CSV, MP4), `ex1.app.Ex0` (the demo of
To-Do item 7), the launchers (`Ex0.py`, `Ex0.bat`), performance (NFR-01/02) and the viewer (manual).

### A.2 Goals
1. **Correctness** of the projection, checked against values computed independently of the code.
2. **Robustness:** every bad input gives a message and the right exit code; never a stack trace,
   never a half-written video.
3. **Performance:** the 30/100 FPS requirement is measured, not assumed, and gated in CI.
4. **The demo works end to end** on the course's own files, on Linux and Windows.

### A.3 Test levels, tools and environment

| Level | What | Tool | Where it runs |
|-------|------|------|---------------|
| Unit | `Plane`, `PlaneResolver`, `Projector` (NN/TL/parallel), `IntensityWindow`, `Volume` types | JUnit 4 (vendored in `lib/`, offline) | `scripts/test.*`, Gradle, CI |
| Integration | NRRD reader/writer round trips, CSV reader, MP4 writer decoded by ffmpeg | JUnit 4 + `ffmpeg`/`ffprobe` when present (tests skip, not fail, without them) | same |
| System / acceptance | `Ex0.run` on a phantom and on the **course's `Ex0.csv`**: frame count by `ffprobe`, exit codes, `--strict`, `--threads` | JUnit 4 (`Ex0Test`) + CI shell steps through `python Ex0.py` | CI matrix: ubuntu & windows × JDK 17 & 21 |
| Performance | `Benchmark` on N³ volumes, NN/TL × 1 thread / all cores | JVM `nanoTime`, warm-up 100 frames | CI job (≥ 30 FPS gate), [EXPERIMENT.md](EXPERIMENT.md) |
| Visual / manual | phantoms with known cross-sections, MRHead videos, the viewer on Windows 11 | eyes, contact sheets in `docs/img/` | before each release |

Test data: `data/` (MRHead, three 128³ phantoms, one 500³ phantom, the course `Ex0.csv`, generated
plane lists) plus phantoms generated inside the tests (no large files needed by CI).

### A.4 Approach: correctness without a reference implementation
We use inputs whose correct output is known **by construction**:

| Oracle | Checks |
|--------|--------|
| Coded volume `v = x + 100y + 10000z` (every voxel unique) | an axis-aligned rectangle must reproduce slice `z` **exactly**, pixel by pixel (TC-50) |
| Linear volume `v = 2x + 3y + 5z` | trilinear interpolation is exact for linear data: every sample of an oblique triangle with fractional corners equals `f(p)` for `p` from the EDD §3 formula (TC-52) |
| Constant volume | the triangle mask: row 0 = apex only, every row symmetric about the centre, last row full (TC-53) |
| Same plane, two projectors | NN = TL on grid points (TC-51); single-thread = multi-thread bit-for-bit (TC-56) |
| Sphere phantom | rings at every angle (visual, EXPERIMENT §5) |
| Course `Ex0.csv` | exactly 4 frames; rows 3/4 behave as the SRS §3.2.2 says (TC-31, TC-44) |

### A.5 Entry and exit criteria
* **Entry:** code compiles with `-Xlint:all -Werror`.
* **Exit (definition of done):** all TCs pass on both OS/JDK pairs in CI; CI demo and strict steps
  green; benchmark ≥ 30 FPS NN single-thread; manual viewer check on Windows done.

### A.6 Risks to testing
ffmpeg/ffprobe absent on a machine → decode and frame-count tests skip (reported as skipped, the
CI Linux runner has them). Benchmark noise on shared CI runners → gate at the hard limit (30), not
the target (100); the target is documented from a quiet run.

### A.7 How to run
```
scripts/test.sh            # Linux/macOS (JDK 17+), no internet
scripts\test.bat           # Windows
./gradlew test             # or Gradle (downloads Gradle once; tests still offline)
java -cp build/Ex0.jar ex1.app.Benchmark 500
```
Result at submission: **67 tests, 0 failures, 0 skipped** (Linux, JDK 21, ffmpeg present).

---

## Part B — Test Descriptions (STD)

Each TC is one JUnit method; the "Verifies" column links to the SRS. Expected results are the
assertions in the code; this table is the index a reviewer reads first.

### B.1 Geometry — `PlaneTest` (core)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-01 | `validIsoscelesTriangle` | REQ-06 | `a=(5,0,0) b=(0,10,0) c=(10,10,0)` → no problems |
| TC-02 | `rejectsNonIsoscelesTriangle` | REQ-06 | `|ab|=10, |ac|=22.4` → "side lengths differ" |
| TC-03 | `acceptsDifferenceJustBelowEps` | REQ-06 | difference 0.0009 → valid |
| TC-04 | `rejectsCollinearTriangle` | REQ-06 | three points on a line → "collinear" |
| TC-05 | `rejectsTinyPlane` | REQ-06 | rail 0.42 → "too small" |
| TC-06 | `rejectsNaN` | NFR-04 | NaN coordinate → "finite" |
| TC-07 | `validRectangleAsTrapezoid` / `imageSizeIncludesBothEnds` | REQ-06, REQ-09 (D1, D5) | 10×20 rectangle → 11×21 image; `|ab|=10,|bc|=12` → 13×11 |
| TC-08 | `rejectsTopWiderThanBase`, `rejectsNonCoplanarTrapezoid` | REQ-06 | → "must be <=", "coplanar" |
| TC-09 | `nonIsoscelesPlaneIsInvalidButRenderable`, `imageWidthCoversEveryRowOfANonIsoscelesPlane`, `degenerateOrHugePlanesAreNotRenderable`, `fromValues*` | REQ-07, D5 | Ex0 rows 3–4 renderable; width ≥ 503 for rails 300/400; zero/huge/NaN not renderable |

### B.2 Point order — `PlaneResolverTest` (core)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-10 | `validDocumentedOrderIsKept` | REQ-05 | Ex0 row 2 (rectangle) → `(a1,a2,b,c)`, no note |
| TC-11 | `ex0Row4IsReadAsTwoRails` | REQ-05 | Ex0 row 4 → valid, rails 502.49 each, bases 300 ≤ 400, note "p1->p2 and p3->p4" |
| TC-12 | `ex0Row3StaysInvalidInEveryReading` | REQ-05, REQ-07 | Ex0 row 3 → invalid, no reordering, renderable |
| TC-13 | `triangleApexIsFoundWhenListedSecond` | REQ-05 | `(b,a,c)` → apex moved to `a` |
| TC-14 | `crossedRailsAreNotAccepted` | REQ-05 | rectangle → documented reading wins over its diagonals |

### B.3 NRRD — `NrrdReaderTest` (io)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-20 | `roundTripAllTypesEncodingsAndEndians` | REQ-02 | 7 types × raw/gzip × LE/BE = 28 files → identical voxels |
| TC-21 | `readsDetachedHeader` | REQ-02 | `.nhdr` + `data file:` → values read |
| TC-22 | `readsRealMRHeadIfPresent` | REQ-02 | `data/MRHead.nrrd` → 256×256×130 INT16 |
| TC-23 … TC-28 | `rejectsNonNrrd`, `rejectsUnsupportedDimension`, `rejectsUnsupportedEncoding`, `rejectsDoubleType`, `rejectsTruncatedData`, `rejectsMissingField` | REQ-03, NFR-04 | message names the problem ("not a NRRD", "dimension", "encoding", "type", "ended early", "'type'") |

### B.4 Demo — `Ex0Test` (app, system level)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-30 | `demoWritesOneFramePerCsvRow` | REQ-01, REQ-13, REQ-17, UC-01 | 40³ phantom + 7 rotating planes, `--interp trilinear --frames DIR` → MP4 with **7 frames** (ffprobe), 7 PNGs |
| TC-31 | `courseExampleCsvGivesOneFramePerRow` | REQ-04, REQ-07 | course `Ex0.csv` → **4 frames** |
| TC-32 | `invalidPlanesStillGiveAVideoButStrictStops` (lenient half) | REQ-07 | one invalid row of two → 2 frames |
| TC-33 | same test (strict half) | REQ-08 | `--strict` → exit 2 **and no MP4 file** |
| TC-34 | `builtInMp4WriterWorksWithoutFfmpeg` | REQ-14 | `Mp4Writer` 5 frames → ffprobe counts 5 |
| TC-35 | `wrongArgumentsGiveExitCode1`, `missingFileGivesExitCode2` | REQ-15, NFR-04 | one file, bad `--interp`, reversed `--window`, unknown option → exit 1; missing files → exit 2 |
| TC-36 | `threadsOptionGivesSameVideoSize` | REQ-15, NFR-02 | `--threads auto` → 5 frames; `--threads 0`/`lots` → exit 1 |
| TC-37 | `csvWithoutUsablePlaneGivesExitCode2` | REQ-07 | CSV `1,2,3` → exit 2 |

### B.5 CSV — `PlaneCsvReaderTest` (io)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-40 | `readsCourseExampleFile` | REQ-04 | the course file verbatim (quotes, count column, empty cells) → 4 rows, shapes 3/4/3/4, coordinates intact |
| TC-41 | `readsPlainTrianglesAndTrapezoids` | REQ-04 | 9- and 12-number rows, spaces as separators → 2 rows, line numbers kept |
| TC-42 | `skipsHeaderCommentsBlankAndEmptySpreadsheetRows` | REQ-04 | BOM + header + `#` + blank + `,,,,` → 1 row at line 5 |
| TC-43 | `countColumnMustMatch` | REQ-04 | `4,` + 9 numbers → black frame |
| TC-44 | `courseFileRow3IsWarnedButProjectedAndRow4IsReadAsRails` | REQ-05, REQ-07 | row 3 warned + plane kept; row 4 one note, valid; messages start with "row 3 (line 4):" |
| TC-45 | `badRowsBecomeBlackFramesInPlace` | REQ-07 | `1,2,3,4` and `abc` between good rows → null planes **in place**, 4 rows total |
| TC-46 | `degeneratePlaneIsBlackFrame` | REQ-07 | all points equal → black frame, file still valid |
| TC-47 | `fileWithoutUsablePlaneIsAnError` | REQ-07, NFR-04 | only comments / only junk → error |

### B.6 Projection — `ProjectorTest`, `ParallelProjectorTest` (core)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-50 | `axisAlignedRectangleReproducesSliceExactly` | REQ-09 | coded 20³ volume, plane `z=7` → every pixel = `M[x][y][7]` |
| TC-51 | `trilinearEqualsNearestOnGridPoints` | REQ-11 | plane through voxel centres → NN = TL |
| TC-52 | `trilinearIsExactForLinearData` | REQ-11 | linear volume, oblique fractional triangle → apex and whole last row equal `f(p)` within 1e-3 |
| TC-53 | `triangleShapeIsCentredAndWidensLinearly` | REQ-09, REQ-10 (D1, D4, D6) | constant volume → row 0 = apex only, each row centred, last row ≥ W−2 filled |
| TC-54 | `pointsOutsideVolumeAreZero`, `planeCompletelyOutsideGivesBlackImage` | REQ-10 | x ∈ [−5, 14] over a 10-wide volume → 0 outside, 9 inside; far plane → all 0 |
| TC-55 | `reusedImageIsClearedBetweenFrames`, `tooSmallTargetImageIsRejected`, `unsignedTypesAreReadAsUnsigned`, `volumeSizeLimitIsEnforced` | NFR-04, REQ-02 | buffer reuse clean; 5×5 buffer rejected; 250/65000/4·10⁹/−3 read back; size 1024 rejected |
| TC-56 | `identicalToSingleThreadForBothModes` | NFR-02, NFR-05 | NN and TL, oblique + rectangle, 3 threads → `assertArrayEquals` with the single-thread result |
| TC-57 | `smallPlanesStayOnTheCallingThread` | NFR-02 | 4-row triangle with 4 threads → correct apex value |
| TC-58 | `oneThreadMeansNoWrapper`, `needsTwoThreads` | REQ-15 | `create(mode,1)` returns the plain projector; `new ParallelProjector(…,1)` throws |

### B.7 Grey mapping — `IntensityWindowTest` (core)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-60 | `mapsLinearlyAndClamps` | REQ-12 | window [100,200]: 50→0, 100→0, 160→153, 200→255, 1e9→255 |
| TC-61 | `percentilesIgnoreOutliers` | REQ-12 | values 0..999 + one 65000 → lo < 30, 900 < hi < 1100 |
| TC-62 | `constantVolumeDoesNotDivideByZero`, `rejectsEmptyWindow` | NFR-04 | constant volume → hi > lo; `[5,5]` → exception |
| TC-63 | `toGreyCentresImageInFrame` | REQ-13 | 2×2 image in 4×4 frame → pixels land at offset (1,1) |

### B.8 Video — `Mp4WriterTest` (io)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-70 | `escapeInsertsPreventionBytes` | REQ-14 | `00 00 00 00 00 01 05 00 00 03` → emulation-prevention bytes inserted exactly per H.264 §7.4.1 |
| TC-71 | `fileHasFtypMdatMoov` | REQ-14 | boxes present, `mdat` before `moov`, `avcC` present |
| TC-72 | `ffmpegDecodesEveryPixel` | REQ-14 | 4 frames 34×18 (one all-black) → ffmpeg decodes 4 frames, every pixel within ±1 |

### B.9 Manual tests (viewer, Windows 11, JDK 25 via `Ex1 Viewer.bat`)

| TC | Steps | Expected |
|----|-------|----------|
| TC-80 | double-click `Ex1 Viewer.bat` | window opens with the drop hint |
| TC-81 | drop `data/MRHead.nrrd` on the window | loads, "Rotate" planes play, status shows ms/frame |
| TC-82 | switch to Triangle, Trilinear, 30 frames; scrub slider | shape and smoothing change; slider follows |
| TC-83 | Planes → From CSV… → `data/Ex0.csv` | warning dialog for rows 3–4; 4 frames shown |
| TC-84 | Export MP4… → default name | file written; "open now?" plays in the system player |
| TC-85 | drop a `.txt` file | friendly "Unsupported file" dialog, app keeps running |
