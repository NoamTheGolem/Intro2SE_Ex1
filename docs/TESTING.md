# Testing Strategy — Test Plan (STP) and Test Descriptions (STD)

**Project:** Ex1 — Plane Projection of a 3D Volume · **Version:** 3.0 (adds the app: B.9 unit tests, B.10 manual script)
**Verifies:** every REQ/NFR of the [SRS](SRS.md) (traceability in SRS §5) · **Design under test:** [SDD](SDD.md), [EDD](ALGORITHM.md)

---

## Part A — Test Plan (STP)

### A.1 Scope and items under test
`core` (geometry, projection, windowing), `ex1.io` (NRRD, CSV read/write, MP4), the app
(`StudioApp` with its plane model `PlaneSpec`, the model view and the export), `ex1.app.Ex0` (the
demo of To-Do item 7), the launchers (`Ex0.py`, `Ex0.bat`, `Ex1 Viewer.bat`) and performance
(NFR-01/02).

### A.2 Goals
1. **Correctness** of the projection, checked against values computed independently of the code.
2. **Robustness:** every bad input gives a message and the right exit code; never a stack trace,
   never a half-written video.
3. **Performance:** the 30/100 FPS requirement is measured, not assumed, and gated in CI.
4. **The demo works end to end** on the course's own files, on Linux and Windows.
5. **The app and the demo agree:** a video exported from the app and the demo's rendering of the
   CSV saved next to it are the same frames, byte for byte.

### A.3 Test levels, tools and environment

| Level | What | Tool | Where it runs |
|-------|------|------|---------------|
| Unit | `Plane`, `PlaneResolver`, `Projector` (NN/TL/parallel), `IntensityWindow`, `Volume` types, `PlaneSpec` (the app's plane model: presets, round trip, interpolation) | JUnit 4 (vendored in `lib/`, offline) | `scripts/test.*`, Gradle, CI |
| Integration | NRRD reader/writer round trips, CSV reader + writer round trip, MP4 writer decoded by ffmpeg, app export ≡ demo render (`ExportRoundTripTest`) | JUnit 4 + `ffmpeg`/`ffprobe` when present (tests skip, not fail, without them) | same |
| System / acceptance | `Ex0.run` on a phantom and on the **course's `Ex0.csv`**: frame count by `ffprobe`, exit codes, `--strict`, `--threads` | JUnit 4 (`Ex0Test`) + CI shell steps through `python Ex0.py` | CI matrix: ubuntu & windows × JDK 17 & 21 |
| Performance | `Benchmark` on N³ volumes, NN/TL × 1 thread / all cores | JVM `nanoTime`, warm-up 100 frames | CI job (≥ 30 FPS gate), [EXPERIMENT.md](EXPERIMENT.md) |
| GUI | the app driven by `java.awt.Robot` on a virtual display (load sample + `Ex0.csv`, select rows, presets, add, interpolate, play, export; then `cmp` of the decoded export against the demo's render of the saved CSV) | Robot script on Xvfb (Linux); same script by hand on Windows (B.10) | before each release |
| Visual / manual | phantoms with known cross-sections, MRHead videos, the app on Windows 11 | eyes, contact sheets and screenshots in `docs/img/` | before each release |

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
| App ↔ CSV ↔ demo | a plane edited in the app → its CSV row → read back → the **same** valid plane (TC-48, TC-82); an interpolated sequence exported by the app → its CSV → rendered by `FrameRenderer` → **identical bytes** (TC-86) |

### A.5 Entry and exit criteria
* **Entry:** code compiles with `-Xlint:all -Werror`.
* **Exit (definition of done):** all TCs pass on both OS/JDK pairs in CI; CI demo and strict steps
  green; benchmark ≥ 30 FPS NN single-thread; the app's manual script (B.10) passed on Windows.

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
Result at submission: **76 tests, 0 failures, 0 skipped** (Linux, JDK 21, ffmpeg present).

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
| TC-08 | `rejectsTopWiderThanBase`, `rejectsNonCoplanarTrapezoid`, `crossedRailsAreRejected` | REQ-06 | → "must be <=", "coplanar"; the diagonals of a rectangle as rails (a bow-tie) → "cross", the rectangle itself → valid |
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
| TC-43 | `countColumnDisagreementsAreRepairedWithANote` | REQ-04, REQ-07 | `3,`+12 numbers → 4th point ignored; `4,`+9 numbers → triangle; `5,`+9 → points used, note; 11 numbers → black frame |
| TC-44 | `courseFileRow3IsWarnedButProjectedAndRow4IsReadAsRails` | REQ-05, REQ-07 | row 3 warned + plane kept; row 4 one note, valid; messages start with "row 3 (line 4):" |
| TC-45 | `badRowsBecomeBlackFramesInPlace` | REQ-07 | `1,2,3,4` and `abc` between good rows → null planes **in place**, 4 rows total |
| TC-46 | `degeneratePlaneIsBlackFrame` | REQ-07 | all points equal → black frame, file still valid |
| TC-47 | `fileWithoutUsablePlaneIsAnError` | REQ-07, NFR-04 | only comments / only junk → error |
| TC-48 | `writerOutputSurvivesARoundTrip` | REQ-22, REQ-24 | an oblique fractional trapezoid and a triangle from `PlaneSpec` → `PlaneCsvWriter.row` → read back with **no warning**, same size and corners (3-decimal output would already break the EPS rule and flip the reading to crossed rails) |

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

### B.9 The app's plane model and export — `PlaneSpecTest`, `ExportRoundTripTest` (app)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-80 | `presetsAreValidAndAxisAligned` | REQ-21 | 100×80×60 volume: XY preset → z = 29.5, 100×80 image; XZ → y = 39.5, 100×60; YZ → x = 49.5, 60×80; Triangle preset is a triangle; Oblique valid |
| TC-81 | `anySpecIsAValidPlane` | REQ-21, REQ-06 | four arbitrary specs (angles up to ±180/±90, width 2, top ratio 0…1) → `validate` empty, triangle iff top ratio 0 |
| TC-82 | `fromPlaneRecoversTheSpec` | REQ-21, REQ-22 | spec → `toPlane` → `fromPlane` → every field within 1e-6 (so a loaded CSV row edits as the same angles) |
| TC-83 | `fromPlaneHandlesTheCourseRows` | REQ-22 | Ex0 row 1 → triangle 400×400 centred at y = 200, rebuilt corners equal; Ex0 row 4 → top ratio 0.75, 400×500 |
| TC-84 | `interpolationStaysValidAndEndsAtTheKeyframes` | REQ-23 | 11 steps between two specs → all valid; yaw 170 → −170 passes through ±180 (short arc); ends equal the keyframes |
| TC-85 | `slideMovesAlongTheNormalOnly` | REQ-21 | XY preset slid by 3 → only z changes by 3 |
| TC-86 | `appFramesEqualCommandLineFramesOfTheSavedCsv` | REQ-24, NFR-05 | 13 interpolated oblique planes on a 64³ phantom → CSV → read back with no warning → `FrameRenderer` frames **`assertArrayEquals`** with the app's frames, every frame |

### B.10 Manual script — the app (Windows 11, JDK 25 via `Ex1 Viewer.bat`; also run by Robot on Xvfb)

| TC | Steps | Expected |
|----|-------|----------|
| TC-90 | double-click `Ex1 Viewer.bat` (first time without `build/Ex0.jar`) | console builds the jar, the window opens with the drop hint; second start opens at once |
| TC-91 | drop `data/MRHead.nrrd` on the window (or Open sample) | status "MRHead.nrrd 256x256x130 INT16"; model with box, axes and mid-slices; an oblique orange plane; its cut on the right |
| TC-92 | drag in the model view; Ctrl+wheel; double-click | the model orbits; zooms; resets |
| TC-93 | wheel over the model; `<` `>` | the plane slides along its normal, the cut and the texture on the plane follow |
| TC-94 | move every slider; watch the status line | both views update immediately; "valid"; projection time ≈ 1–10 ms |
| TC-95 | presets XY / XZ / YZ / Triangle / Oblique; Top/bottom 0 % and 100 % | axis slices equal the model's mid-slices; triangle ↔ rectangle; CSV row shows 3 or 4 points |
| TC-96 | Load use cases (CSV)… → `data/Ex0.csv` | dialog with the warnings of rows 3–4; 4 lines in the list; clicking row 4 shows the trapezoid leaving the box (`docs/img/app_row4.png`) |
| TC-97 | Add current / Replace / Remove / Up / Down / Clear; Save CSV… | list follows; the saved file reopens with no new warnings |
| TC-98 | Frames between planes = 5, Play | the plane glides between the entries; Stop stops |
| TC-99 | Export video… → default name; then `Ex0 data/MRHead.nrrd <name>.csv x.mp4` | progress bar, MP4 + CSV written, "open now?" plays; the demo prints the same frame count and size; decoded frames identical (`ffmpeg -f rawvideo` + `cmp`) |
| TC-100 | drop a `.txt` file; Open NRRD… on a non-NRRD | friendly dialog, app keeps running |
