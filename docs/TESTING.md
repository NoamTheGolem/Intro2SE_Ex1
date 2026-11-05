# Testing Strategy — Test Plan (STP) and Test Descriptions (STD)

**Project:** Ex1 — Plane Projection of a 3D Volume · **Version:** 3.3 (3.0 added the app; 3.1 adds
the lecturer's data as acceptance tests — B.2, B.4 `EdgeCasesTest`, B.5 — the cut-open 3-D view
and point picking — B.10 — and a new manual script, B.11; 3.2 tests the video = what Play shows:
`SweepTest`, TC-107…TC-109; 3.3 the export on a PC with a broken ffmpeg — `VideoWritersTest`,
TC-110…TC-113 — the export's names and messages, TC-114…TC-115, and the points-only screen in B.11; 3.3.1: no Cut open / Samples, the system's file dialogs, TC-94, TC-101)
**Verifies:** every REQ/NFR of the [SRS](SRS.md) (traceability in SRS §5) · **Design under test:** [SDD](SDD.md), [EDD](ALGORITHM.md)

---

## Part A — Test Plan (STP)

### A.1 Scope and items under test
`core` (geometry, point order, projection, windowing), `ex1.io` (NRRD, CSV read/write, MP4), the
app (`StudioApp` with its plane model `PlaneSpec`, the solid model and cut-open renderer, the
slice views, picking, the export), `ex1.app.Ex0` (the demo of To-Do item 7), the launchers
(`Ex0.py`, `Ex0.bat`, `Ex1 Viewer.bat`) and performance (NFR-01/02/10).

### A.2 Goals
1. **Correctness** of the projection, checked against values computed independently of the code.
2. **The lecturer's files are the acceptance tests:** his `edge_cases.csv` must give exactly the
   frames, black frames and warnings his comment lists; his use cases must read without a note
   and come out the right way up; his rows must survive our writer bit for bit.
3. **Robustness:** every bad input gives a message and the right exit code; never a stack trace,
   never a half-written video.
4. **Performance:** the 30/100 FPS requirement is measured, not assumed, and gated in CI.
5. **The app and the demo agree:** a video exported from the app and the demo's rendering of the
   CSV saved next to it are the same frames, byte for byte.
6. **The 3-D view shows the inside:** on volumes whose answer is known, rays hit the surface and
   the cut face where geometry says, and the cut face shows the inside's grey value.

### A.3 Test levels, tools and environment

| Level | What | Tool | Where it runs |
|-------|------|------|---------------|
| Unit | `Plane` (doubtful vs bad), `PlaneResolver` (course order), `Projector` (NN/TL/parallel), `IntensityWindow`, `Volume` types, `PlaneSpec` (presets, round trip, interpolation, make-isosceles), `VolumeGeometry`, `SolidModel`, `CutawayRenderer` | JUnit 4 (vendored in `lib/`, offline) | `scripts/test.*`, Gradle, CI |
| Integration | NRRD reader/writer round trips (+ `space directions`), CSV reader + writer round trip (incl. the lecturer's 8-decimal rows), MP4 writer decoded by ffmpeg, app export ≡ demo render (`ExportRoundTripTest`) | JUnit 4 + `ffmpeg`/`ffprobe` when present (tests skip, not fail, without them) | same |
| System / acceptance | `Ex0.run` on a phantom, on the **course's `Ex0.csv`** and on the **lecturer's `edge_cases.csv` with the die** (`EdgeCasesTest`: 16 frames, black and warned exactly as he wrote, pips counted on frames 1–3); exit codes, `--strict`, `--threads` | JUnit 4 + CI shell steps through `python Ex0.py` | CI matrix: ubuntu & windows × JDK 17 & 21 |
| Performance | `Benchmark` on N³ volumes, NN/TL × 1 thread / all cores; model build and cut-open render times | JVM `nanoTime`, warm-up | CI job (≥ 30 FPS gate), [EXPERIMENT.md](EXPERIMENT.md) |
| GUI | the app driven by `java.awt.Robot` on a virtual display (open each lecturer scan, presets, pick 3 points, load his use case, play, export; screenshots) | Robot script on Xvfb (Linux); the same steps by hand on Windows (B.11) | before each release |
| Visual / manual | the five lecturer scans cut open, phantoms with known cross-sections, MRHead videos, the app on Windows 11 | eyes, screenshots in `docs/img/` | before each release |

Test data: `data/` (MRHead, phantoms, the die, the course `Ex0.csv`, the lecturer's CSVs in
`data/course/`, generated plane lists) plus volumes generated inside the tests. The lecturer's
scans are not stored in the repository (size); the manual script and EXPERIMENT §7 use them from
his Drive folder.

### A.4 Approach: correctness without a reference implementation
We use inputs whose correct output is known **by construction** — and the lecturer's own
expectations where he wrote them down:

| Oracle | Checks |
|--------|--------|
| Coded volume `v = x + 100y + 10000z` (every voxel unique) | an axis-aligned rectangle must reproduce slice `z` **exactly**, pixel by pixel (TC-50) |
| Linear volume `v = 2x + 3y + 5z` | trilinear interpolation is exact for linear data (TC-52) |
| Constant volume | the triangle mask: row 0 = apex only, every row symmetric, last row full (TC-53) |
| Same plane, two projectors | NN = TL on grid points (TC-51); single-thread = multi-thread bit-for-bit (TC-56) |
| The lecturer's `edge_cases.csv` + the die (`PhantomGenerator.buildDice`, as described in his file) | 16 frames; black: 6, 9–16; warnings: 7–16; 1, 6 and 4 pips on frames 1, 2, 3 (TC-38, TC-49) |
| The lecturer's use cases | every row valid in the course order, no note; `head.csv` slices the right way up (TC-16); his rows written back bit for bit (TC-48) |
| Course `Ex0.csv` | exactly 4 frames; row 3 doubtful (drawn + warned), row 4 valid (TC-31, TC-44) |
| Hollow ball (bright shell, dark inside) | the solid includes the dark inside; a ray hits the surface at `r = 18`; cut open, it stops on the plane and shows the inside's grey 30 (TC-88, TC-89) |
| App ↔ CSV ↔ demo | a plane edited in the app → its CSV row → read back → the **same** plane (TC-48, TC-82); an interpolated sequence exported by the app → its CSV → rendered by `FrameRenderer` → **identical bytes** (TC-86) |

### A.5 Entry and exit criteria
* **Entry:** code compiles with `-Xlint:all -Werror`.
* **Exit (definition of done):** all TCs pass on both OS/JDK pairs in CI; CI demo and strict steps
  green; benchmark ≥ 30 FPS NN single-thread; the app's manual script (B.11) passed on Windows.

### A.6 Risks to testing
ffmpeg/ffprobe absent on a machine → decode and frame-count tests skip (reported as skipped; the
CI Linux runner has them). Benchmark noise on shared CI runners → gate at the hard limit (30), not
the target (100); the target is documented from a quiet run. The look of the 3-D view (shading,
threshold) is judged by eye; the tests pin down its geometry, not its colours.

### A.7 How to run
```
scripts/test.sh            # Linux/macOS (JDK 17+), no internet
scripts\test.bat           # Windows
./gradlew test             # or Gradle (downloads Gradle once; tests still offline)
java -cp build/Ex0.jar ex1.app.Benchmark 500
```
Result at submission: **101 tests, 0 failures, 0 skipped** (Linux, JDK 21, ffmpeg present). On the
CI Windows runner the demo step goes through `python Ex0.py`, which uses the bundled `runtime/`.

---

## Part B — Test Descriptions (STD)

Each TC is one or a few JUnit methods; the "Verifies" column links to the SRS. Expected results
are the assertions in the code; this table is the index a reviewer reads first.

### B.1 Geometry — `PlaneTest` (core)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-01 | `validIsoscelesTriangle` | REQ-06 | `a=(5,0,0) b=(0,10,0) c=(10,10,0)` → no problems |
| TC-02 | `rejectsNonIsoscelesTriangle` | REQ-06 | `|ab|=10, |ac|=22.4` → "side lengths differ" |
| TC-03 | `acceptsDifferenceJustBelowEps` | REQ-06 | difference 0.0009 → valid |
| TC-04 | `rejectsCollinearTriangle` | REQ-06 | three points on a line → "collinear" |
| TC-05 | `rejectsTinyPlane` | REQ-06 | rail 0.42 → "too small" |
| TC-06 | `rejectsNaN` | NFR-04 | NaN coordinate → "finite" |
| TC-07 | `validRectangleAsTrapezoid`, `imageSizeIncludesBothEnds`, `fromValuesBuildsBothShapes`, `fromValuesRejectsWrongCount` | REQ-06, REQ-09 (D1, D5) | 10×20 rectangle → 11×21 image; `|ab|=10,|bc|=12` → 13×11; 9/12 values → triangle/trapezoid |
| TC-08 | **doubtful but drawn:** `rejectsTopWiderThanBase`, `nonIsoscelesPlaneIsInvalidButRenderable`, `imageWidthCoversEveryRowOfANonIsoscelesPlane`, `topNotParallelToBaseIsDoubtfulButDrawn` | REQ-06, REQ-07, D5 | "must be <="; Ex0 row 3 renderable; width ≥ 503 for rails 300/400; edge case 8 (top 16 off the parallel) → "not parallel", renderable |
| TC-09 | **bad → black:** `rejectsNonCoplanarTrapezoid`, `crossedRailsAreRejected`, `bowTieWhoseBasesCrossIsBad`, `degenerateOrHugePlanesAreNotRenderable`, `nonCoplanarCollinearAndHugePlanesAreBad` | REQ-06, REQ-07 | "coplanar"; rails crossing / top and base crossing → "the sides cross each other"; edge cases 12–14, 16 → "points b and c are the same point", "collinear", "not on one plane", "10200"; a valid plane outside the volume stays renderable |

### B.2 Point order — `PlaneResolverTest` (core)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-10 | `fourPointsAreTwoRailsInTheCourseOrder` | REQ-05 | Ex0 row 2 → `(a1, b, a2, c)`: rails along `z`, 401×501 image, no note |
| TC-11 | `ex0Row4IsAValidTrapezoidWithoutAnyNote` | REQ-05 | Ex0 row 4 → valid, rails 502.49, top 300, base 400, no note |
| TC-12 | `ex0Row3StaysDoubtfulAndDrawable` | REQ-05, REQ-07 | Ex0 row 3 → invalid, not reordered, renderable |
| TC-13 | `triangleIsNeverReordered` | REQ-05 | `(b, a, c)` → kept as given, doubtful, renderable |
| TC-14 | `crossingSidesAreBadInEveryReading` | REQ-05, REQ-07 | edge case 15 (`a'` and `a''` swapped) → not renderable, "the sides cross each other" |
| TC-15 | `qaOrderIsUsedOnlyWhenItIsTheValidReading` | REQ-05 | a trapezoid written as `(a1, a2, b, c)` → Q&A order used, note "Q&A order" |
| TC-16 | `headSlicesComeOutTheRightWayUp` | REQ-05 | `head.csv` row 1 (`z = 0`) → rows along `y`, columns along `x` |

### B.3 NRRD — `NrrdReaderTest` (io)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-20 | `roundTripAllTypesEncodingsAndEndians` | REQ-02 | 7 types × raw/gzip × LE/BE = 28 files → identical voxels |
| TC-21 | `readsDetachedHeader` | REQ-02 | `.nhdr` + `data file:` → values read |
| TC-22 | `readsRealMRHeadIfPresent` | REQ-02 | `data/MRHead.nrrd` → 256×256×130 INT16 |
| TC-23 … TC-28 | `rejectsNonNrrd`, `rejectsUnsupportedDimension`, `rejectsUnsupportedEncoding`, `rejectsDoubleType`, `rejectsTruncatedData`, `rejectsMissingField` | REQ-03, NFR-04 | message names the problem ("not a NRRD", "dimension", "encoding", "type", "ended early", "'type'") |
| TC-29 | `readsSpaceDirectionsForTheViewer` | REQ-02, REQ-25 | `space: left-posterior-superior` + `space directions` tuples → kept on the volume for the display |

### B.4 Demo — `Ex0Test`, `EdgeCasesTest` (app, system level)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-30 | `demoWritesOneFramePerCsvRow` | REQ-01, REQ-13, REQ-17, UC-01 | 40³ phantom + 7 rotating planes, `--interp trilinear --frames DIR` → MP4 with **7 frames** (ffprobe), 7 PNGs |
| TC-31 | `courseExampleCsvGivesOneFramePerRow` | REQ-04, REQ-07 | course `Ex0.csv` → **4 frames** |
| TC-32 | `invalidPlanesStillGiveAVideoButStrictStops` (lenient half) | REQ-07 | one doubtful row of two → 2 frames |
| TC-33 | same test (strict half) | REQ-08 | `--strict` → exit 2 **and no MP4 file** |
| TC-34 | `builtInMp4WriterWorksWithoutFfmpeg` | REQ-14 | `Mp4Writer` 5 frames → ffprobe counts 5 |
| TC-35 | `wrongArgumentsGiveExitCode1`, `missingFileGivesExitCode2` | REQ-15, NFR-04 | one file, bad `--interp`, reversed `--window`, unknown option → exit 1; missing files → exit 2 |
| TC-36 | `threadsOptionGivesSameVideoSize` | REQ-15, NFR-02 | `--threads auto` → 5 frames; `--threads 0`/`lots` → exit 1 |
| TC-37 | `csvWithoutUsablePlaneGivesExitCode2` | REQ-07 | CSV `1,2,3` → exit 2 |
| TC-38 | `EdgeCasesTest.demoGivesTheFramesTheCourseExpects` | REQ-04, REQ-06, REQ-07, REQ-12, REQ-17, NFR-04 | **the lecturer's `edge_cases.csv` on the die**, through `Ex0.run` with `--frames`: 16 PNGs; frames 6 and 9–16 all black, the others not; a warning for exactly rows 7–16; 1, 6, 4 pips on frames 1, 2, 3; "row 9 (line 35): the count says 4 points but 3 points are given" |

### B.5 CSV — `PlaneCsvReaderTest` (io)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-40 | `readsCourseExampleFile` | REQ-04 | the course file verbatim (quotes, count column, empty cells) → 4 rows, shapes 3/4/3/4, coordinates intact |
| TC-41 | `readsPlainTrianglesAndTrapezoids` | REQ-04 | 9- and 12-number rows, spaces as separators → 2 rows, line numbers kept |
| TC-42 | `skipsHeaderCommentsBlankAndEmptySpreadsheetRows` | REQ-04 | BOM + header + `#` + blank + `,,,,` → 1 row at line 5 |
| TC-43 | `countMustMatchThePointsGiven` | REQ-04, REQ-07 | `4,`+9 numbers, `3,`+12, `5,`+9 → black frames, "the count says 4 points but 3 …"; 11 numbers → "got 11 numbers" |
| TC-44 | `courseFileRow3IsDoubtfulAndRow4IsAValidTrapezoid` | REQ-05, REQ-07 | row 3 → "side lengths differ" + "drawn as given", plane kept; row 4 → no warning, valid; messages start with "row 3 (line 4):" |
| TC-45 | `badRowsBecomeBlackFramesInPlace` | REQ-07 | `1,2,3,4` and `abc` between good rows → null planes **in place**, 4 rows total |
| TC-46 | `degeneratePlaneIsBlackFrame` | REQ-07 | all points equal → black frame, file still valid |
| TC-47 | `fileWithoutUsablePlaneIsAnError` | REQ-07, NFR-04 | only comments / only junk → error |
| TC-48 | `writerOutputSurvivesARoundTrip`, `lecturerRowsSurviveAWriteReadCycleBitForBit` | REQ-22, REQ-24 | an oblique fractional trapezoid and a triangle from `PlaneSpec` → `PlaneCsvWriter.row` (course order) → read back with **no warning**, same size and corners; the lecturer's 8-decimal `head.csv` row and a plane of π, e, ⅓, 1e-7 → written and read back **bit for bit** |
| TC-49 | `lecturerEdgeCasesGiveTheExpectedFrames` | REQ-04, REQ-07 | `data/course/edge_cases.csv` → 16 rows; rows 1–8 have a plane, 9–16 none; rows 7–16 warned |

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

(The few-valued rule of REQ-12 — `[min, max]` for ≤ 16 grey levels — is exercised by TC-38: the
die has three values, and frames 1–3 must show its pips.)

### B.8 Video — `Mp4WriterTest` (io)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-70 | `escapeInsertsPreventionBytes` | REQ-14 | `00 00 00 00 00 01 05 00 00 03` → emulation-prevention bytes inserted exactly per H.264 §7.4.1 |
| TC-71 | `fileHasFtypMdatMoov` | REQ-14 | boxes present, `mdat` before `moov`, `avcC` present |
| TC-72 | `ffmpegDecodesEveryPixel` | REQ-14 | 4 frames 34×18 (one all-black) → ffmpeg decodes 4 frames, every pixel within ±1 |

### B.9 The app's plane model, video and export — `PlaneSpecTest`, `SweepTest`, `ExportRoundTripTest`, `ExportMessagesTest`, `VideoWritersTest`

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-80 | `presetsAreValidAndAxisAligned` | ADR-0008 (`PlaneSpec`; the app's default triangle) | 100×80×60 volume: the three axis presets lie on the middle slice of their axis with the full slice as image; Triangle preset is a triangle; Oblique valid |
| TC-81 | `anySpecIsAValidPlane` | REQ-21, REQ-06 | four arbitrary specs (angles up to ±180/±90, width 2, top ratio 0…1) → `validate` empty, triangle iff top ratio 0 |
| TC-82 | `fromPlaneRecoversTheSpec` | REQ-21, REQ-22 | spec → `toPlane` → `fromPlane` → every field within 1e-6 (a loaded CSV row edits as the same angles) |
| TC-83 | `fromPlaneHandlesTheCourseRows` | REQ-22 | Ex0 row 1 → triangle 400×400; Ex0 row 4 → top ratio 0.75 |
| TC-84 | `interpolationStaysValidAndEndsAtTheKeyframes` | ADR-0008 (the planes of TC-86) | 11 steps between two specs → all valid; yaw 170 → −170 passes through ±180 (short arc); ends equal the keyframes |
| TC-85 | `slideMovesAlongTheNormalOnly` | REQ-21 | axis preset slid by 3 → only its constant coordinate changes, by 3 |
| TC-86 | `appFramesEqualCommandLineFramesOfTheSavedCsv` | REQ-24, NFR-05 | 13 interpolated oblique planes on a 64³ phantom → CSV → read back with no warning → `FrameRenderer` frames **`assertArrayEquals`** with the app's frames, every frame |
| TC-107 | `axisPlaneVisitsEverySliceOnceAndContainsTheCurrentPlane` | REQ-23 | the plane `y = 7` in a 10×20×30 volume → the sweep has 20 frames, one voxel apart, in order; the current plane is one of them; every frame valid |
| TC-108 | `bigVolumesUseLargerStepsAndATriangleStaysATriangle`, `planeWithoutANormalIsTheWholeVideo` | REQ-23 | an oblique triangle in 500³ → between 75 and 300 frames (not thousands), all triangles, the current plane among them; collinear points → a video of that one plane |
| TC-109 | `defaultVideoHasOneFramePerSweepStepAndABadPlaneIsBlack` | REQ-23, REQ-24 | the app's video (an oblique triangle swept through a 48³ phantom) with a plane `a = b = c` in the middle → `videoFrames` keeps one frame per step, that one null → `FrameRenderer` renders it all black; most other frames show the spheres |
| TC-110 | `missingFfmpegGivesTheBuiltInWriter` | REQ-14 | ffmpeg pointed at a file that does not exist → `openMp4` gives the built-in writer, which writes a file |
| TC-111 | `ffmpegWithoutTheH264EncoderIsNotUsed` | REQ-14 | a fake ffmpeg whose `-encoders` lists only `mpeg4` (an LGPL build) → the built-in writer |
| TC-112 | `ffmpegThatFailsWhileEncodingIsReportedAndTheBuiltInWriterStillWorks`, `realFfmpegWithH264IsUsed` | REQ-14 | a fake ffmpeg that lists `libx264` but exits 1 when encoding → the ffmpeg writer is chosen, its failure is an `IOException`, the built-in writer then writes the file; the real ffmpeg (when installed) passes the probe |
| TC-113 | `brokenFfmpegStillGivesTheVideo` (`Ex0Test`) | REQ-14, REQ-01 | the demo with that broken ffmpeg → "Warning : … writing the video with the built-in encoder instead", exit 0, 3 frames (`ffprobe`) |
| TC-114 | `proposedNameIsNeverTaken` (`ExportMessagesTest`) | REQ-24 | `MRHead_cut.mp4` free → proposed; taken → `MRHead_cut_2.mp4`; both taken → `_3` |
| TC-115 | `failuresAreExplainedWithTheTechnicalDetailBelow` | REQ-24 | "Access is denied" → "Windows did not allow the app to write in that folder" + "Details (FileNotFoundException)"; "cannot overwrite" → "open in another program"; anything else → "Try again" + the message |

### B.10 The 3-D view and picking — `CutawayTest` (app)

| TC | Test | Verifies | Input → expected |
|----|------|----------|------------------|
| TC-87 | `courseGeometryGivesTheLecturersViews`, `unknownGeometryIsTheIdentity` | REQ-25 | the lecturer's axes (`x` = posterior, `y` = inferior, `z` = right, 1.3 mm) → Axial keeps `y`, Coronal `x`, Sagittal `z`; sagittal shows A→P left to right and S→I top to bottom; axial has R on the left; superior is world up; index ↔ world round trip; no header → identity views without labels |
| TC-88 | `solidFillsTheDarkInsideOfAClosedShell` | REQ-19 | hollow ball (shell 200, inside 30) → the dark centre is solid, the corners are not; Otsu threshold between 30 and 200 |
| TC-89 | `rayHitsTheSurfaceAndTheCutFaceWhereExpected` | REQ-19, REQ-26 | camera through the centre: pick hits the surface at `y ≈ c − 18`; cut open by `y = 23.5` → the hit is exactly on the plane; off the object → nothing; the rendered centre pixel has the inside's grey (30 ± 2), not the shell's |
| TC-90 | `clickedTriangleIsMadeIsoscelesByMovingCOnly` | REQ-21, REQ-26 | clicked non-isosceles triangle → valid, `a` and `b` unchanged, `c` on the same ray from `a`; a clicked quadrilateral → a valid trapezoid |

### B.11 Manual script — the app (Windows 11, JDK 25 via `Ex1 Viewer.bat`; also run by Robot on Xvfb)

Data: the lecturer's scans from his Drive folder (`MRHead_1`, `onion`, `swiss_roll`,
`biscuit_cake`, `croissant`), `data/dice.nrrd`, `data/course/*.csv`.

| TC | Steps | Expected |
|----|-------|----------|
| TC-91 | double-click `Ex1 Viewer.bat` (first time without `build/Ex0.jar`) | console builds the jar, the window opens with the drop hint; second start opens at once |
| TC-92 | drop `MRHead_1.nrrd` on the window (or Open NRRD…) | status "MRHead_1.nrrd 256x256x130 INT16, voxel 1.00 x 1.00 x 1.30 mm"; picture 1/4: a default triangle's cut (Axial/Coronal/Sagittal with A/P/R/L/S/I labels one arrow away); about a second later the head in 3-D, cut open by the triangle, brain visible on the cut face |
| TC-93 | drag in the 3-D view; Ctrl+wheel; wheel; double-click | the object orbits (coarse while dragging, sharp a moment later); zooms; the plane slides through the head and the cut face changes; the camera resets |
| TC-94 | look at the toolbar; drop `onion.nrrd` from the file manager anywhere on the window (on the controls too) | only **Open NRRD…** and the FPS: no Cut open, Surface or Samples (ADR-0012, ADR-0013); the onion opens, always cut open |
| TC-95 | the toolbar's **Projection: N FPS**; slide the plane with the wheel | the speed of the current cut (≈ 1000–4000 FPS on a laptop for MRHead), steady to within a few hundred while nothing changes; all views update |
| TC-96 | the picture panel's **▶** four times, then **◀** once; move each view's slider | 1/4 the cut → 2/4 Axial → 3/4 Coronal → 4/4 Sagittal → 1/4 the cut; ◀ from the cut → 4/4 Sagittal; the slider changes that view's slice and the orange line moves with the plane |
| TC-97 | **Pick a, b, c** while the cut is on show, then click three spots on the Axial picture (or flip to another view first) | the panel turns to Axial by itself; the prompt names the next point; after the third click: "Clicks are never exact, so c was moved a little along the line a-c to make the triangle isosceles." — no numbers; the head cut open there, camera facing the cut; the panel back on the cut (stays on the view if you flipped it yourself) |
| TC-98 | **4 points (trapezoid)**; then **Pick a1, a2, b, c** and four clicks on the Axial picture | the button stays on 4 points (the 3.2 bug), rows a1, a2, b, c, the cut becomes a trapezoid; after four clicks the nearest isosceles trapezoid in that slice, its cut = the slice inside the trapezoid; **3 points** turns it back into a triangle |
| TC-99 | pick on the 3-D object (surface, then cut face); a single point's **pick**; type coordinates + Enter; type a triangle that is not isosceles; three points on one line | the point lands where clicked; only that point changes; typed values used; "Not isosceles (drawn as given): …" with a **Make isosceles** button, which fixes it and disappears; collinear points → black cut and "points a, b, c are collinear (no plane)" next to the points |
| TC-100 | drop a `.csv` on the window | "Lists of planes (CSV) are for the command line: python Ex0.py scan.nrrd that.csv out.mp4"; nothing else changes |
| TC-101 | **Open NRRD…** | the system's own dialog (Windows: Explorer's, starting in Downloads, or in the open scan's folder), showing `.nrrd`/`.nhdr` files; any drive or folder; the chosen scan opens with the default triangle. *5 Nov under Wine: the Windows dialog opened in Downloads, a pasted path `Z:\…\onion.nrrd` opened the onion; the Save dialog of Export proposed `onion_cut.mp4`, then `onion_cut_2.mp4`* |
| TC-102 | **Play**, wait, **Stop** | the plane moves through the scan from the current plane, turns at either end and runs back; "frame i of n" under the cut; Stop keeps that frame |
| TC-103 | **Export video…** → Enter; again → Enter; replace a video that is open in a player; then `ffprobe` | `MRHead_cut.mp4`, then `MRHead_cut_2.mp4` proposed; the open one → "The file is open in another program …"; 10 fps, one frame per sweep step (5 Nov: MRHead 169, 4-point trapezoid 127). Under Wine with a fake `ffmpeg.exe` that fails: the video is still written, with "The ffmpeg on this computer did not work, so the app's own encoder made the video." |
| TC-104 | drop a `.txt` file; Open NRRD… on a non-NRRD | friendly dialog, app keeps running |
| TC-105 | repeat TC-92, TC-97 and TC-102 with `onion`, `swiss_roll`, `biscuit_cake`, `croissant` | each object appears cut open with the automatic surface; the croissant (500³) model is ready within ~3 s; their use cases (CSV) through `python Ex0.py` (B.4) |
| TC-106 | **download and run** (NFR-11): on a Windows PC *without Java*, download the repository ZIP, unzip, double-click `Ex1.exe`; drop `MRHead_1.nrrd` on `Ex1.exe`; run `python Ex0.py data\dice.nrrd data\course\edge_cases.csv x.mp4` | the app opens (no console window, no question about Java); the dropped scan opens; the demo writes 16 frames. *Smoke test without such a PC (4 Nov): the bundled runtime under Wine prints `openjdk version "25.0.1"`, writes the edge-case MP4 **byte for byte equal** to the Linux JDK's, and `Ex1.exe <scan>` opens the app with the head cut open* |
| TC-116 | **Windows refuses an unsigned program** (5 Nov, the team's PC: "security warning … signed", nothing starts): unblock the downloaded zip (Properties → Unblock), extract again, `Ex1.exe`; if Smart App Control still blocks it, `Ex1 Viewer.bat` | the app opens. Checked here: every `.exe`/`.dll` in `runtime/` has a complete Authenticode signature (71 files; `java.exe`, `javaw.exe` byte-identical to Oracle's signed originals in the jmods); under Wine both `Ex1.exe` and `cmd /c "Ex1 Viewer.bat"` open the app and `java.exe -version` / the edge-case demo run. *To do on the PC* |
