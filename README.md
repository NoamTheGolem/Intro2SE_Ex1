# Ex1 — Plane Projection of a 3D Volume

[![CI](https://github.com/minion-dog/Intro2SE_Ex1/actions/workflows/ci.yml/badge.svg)](https://github.com/minion-dog/Intro2SE_Ex1/actions/workflows/ci.yml)

A Java desktop app in the spirit of 3D Slicer: open a 3-D scan (NRRD — an MRI head, an onion, a
croissant …), **choose a cutting plane by clicking 3 points**, and **see the object cut open at
that plane — like a pie cut through the middle, with its inside showing on the cut face** — next
to the Axial, Coronal and Sagittal views and the cut itself as a 2-D image. Collect the planes of
a use case (or load the lecturer's) and **export it as an MP4 video** plus the CSV of its planes.
The projection runs at hundreds of frames per second on one CPU core; the same code also runs as
the command-line demo the assignment asks for (`python Ex0.py file.nrrd file.csv file.mp4`).

**Course:** Introduction to Software Engineering (OOP/OOD), Ariel University — Exercise 1
**Team:** *<name 1 — ID>*, *<name 2 — ID>*
**Status:** complete; 90 automated tests incl. the lecturer's own edge cases; CI on Linux and Windows (JDK 17 and 21)

![The app: three points picked (a on the sagittal view, b and c on the axial view); the head cut
open there with the brain on the cut face; the cut as the video frame; Axial, Coronal and
Sagittal with the plane's line and points](docs/img/app_head_picked.png)

---

## The problem

Given a 3-D raster `M` of size `sx × sy × sz` (`1 ≤ sx, sy, sz < 2^10`, unsigned 8/16/32-bit
entries) and a plane `p`, compute the 2-D image of the projection of `p` in `M` — the X/Y/Z
views of 3D Slicer, but along **any** plane. A plane is given by **3 points** (isosceles
triangle `a, b, c`) or **4 points** (isosceles trapezoid; in a CSV row listed rail by rail,
`a1, b, a2, c`), tolerance `EPS = 0.001`. Required: correct, stable, **≥ 30 FPS (must) / ≥ 100 FPS
(expected)** on a 500³ volume. The sheet calls the problem "not well-defined for implementation —
a detailed design is needed"; the design is the point of this repository.

## Scope

| In scope | Out of scope |
|----------|--------------|
| **The app**: open a scan (menu, drag-and-drop, samples); the object as a solid **cut open by the plane**; **Axial / Coronal / Sagittal** views; **pick the 3 (or 4) points** on them or on the object; move/turn the plane with presets, sliders and the mouse wheel; the cut live | CSV points in millimetres — points are voxel indices, as in every course file (the app's display does use millimetres) |
| **Use cases**: a list of planes (picked, typed, or the lecturer's CSV — found automatically for his scans), smooth interpolation, play, **export as MP4 + CSV** | Colour / vector / 4-D volumes |
| The projection algorithm of the sheet (triangle + trapezoid), nearest-neighbour and trilinear sampling, optional multithreading | GPU |
| The course's CSV format and rules: valid / doubtful (drawn + warned) / bad (black + warned) rows, exactly as the lecturer's `edge_cases.csv` expects | Networking, servers |
| NRRD input: 3-D scalar, 8/16/32-bit signed/unsigned and float, raw/gzip, detached header, space directions | Android app (planned reuse of `core`) |
| Command-line demo (item 7), benchmark, dataset, tests, experiment; MP4 output with no external software | |

---

## Quick start

Requirements: **Java 17+** (JDK or JRE). Nothing else — the MP4 encoder is built in.

**Windows:** download / clone, double-click **`Ex1 Viewer.bat`**. The first start compiles
`build/Ex0.jar` if it is missing (needs a JDK; IntelliJ's and Android Studio's JDKs are found
automatically). **Any OS:** `python Ex0.py` with no arguments, or `java -jar build/Ex0.jar`.

Then open a scan: **Open NRRD…**, drop a `.nrrd` on the window, or **Samples** (MRHead + the
course's `Ex0.csv`; the die + the lecturer's edge cases; a 500³ phantom). The lecturer's five test
scans are in his Drive folder (see [data/README.md](data/README.md)); their use cases ship in
`data/course/`.

### Choose a cut and look inside — 4 clicks

1. Open a scan (e.g. his `MRHead_1.nrrd`). After about a second the head appears in 3-D.
2. Click **Pick a, b, c** (under "1. The plane").
3. Click three spots — on the Axial, Coronal or Sagittal view (move their sliders to the slice you
   want) or on the 3-D object itself.
4. Done: the app makes the triangle isosceles (it moves only `c`, along `a→c`, and says so), cuts
   the object open there and turns the camera to the cut. Drag to look from another side; turn the
   mouse wheel to slide the cut through the object.

### Using the app

| Where | What |
|-------|------|
| Toolbar | **Open NRRD…**, **Samples**, **Load use case (CSV)…**, **Save CSV…**, **Export video…**; **Sampling** nearest/trilinear; **Cut open** on/off; **Surface** = the threshold that defines the object's skin (automatic by default) |
| **3-D model — cut open by the plane** (top left) | The scan as a solid: the half between you and the plane is removed, so the cut face shows the inside (inside the triangle at full brightness, the rest of the plane darker); the outside is shaded. Box, plane outline and points on top. Drag = orbit, wheel = move the plane through the object, Ctrl+wheel = zoom, double-click = reset; while picking, a click places the point on the surface or on the cut face. |
| **The cut** (top right) | The projection of the plane — exactly one frame of the video ([ALGORITHM.md](docs/ALGORITHM.md)), with `a`/`a1 a2`, `b`, `c` at its corners; size, validity and projection time below. |
| **Axial / Coronal / Sagittal** (bottom) | Slicer's three views with A/P, R/L, S/I labels and a slider each; the orange line is where the plane crosses that slice, the dots are its points (filled when near the slice). Click to place a point while picking. |
| **1. The plane — its points** | 3 points (triangle) or 4 (trapezoid); type them, **pick** one, **Pick a, b, c** (all), **Apply**, **Make isosceles**. The line below says *valid*, *doubtful (drawn as given)* or *bad (a black frame)* and why. |
| **2. Move / turn the plane** | Presets **Axial / Coronal / Sagittal** (the current slice of that view, camera facing it), **Triangle**, **Oblique**, `< back` `fwd >`; sliders for centre, yaw / pitch / roll, width, height and top/base (0 % = triangle, 100 % = rectangle) — every position is a valid plane. The **CSV row** of the plane is shown live, in the course order. |
| **3. Use case — the frames of the video** | **Load its use case: NAME.csv** (appears when the lecturer's CSV for this scan is found); the list of planes; **Add current / Replace / Remove / Up / Down / Clear**; **Frames between** for a smooth video; **fps**; **Play**; **Export video (MP4 + CSV)…** — `Ex0.py scan.nrrd that.csv x.mp4` reproduces the exported video **byte for byte**. |

![The lecturer's scans cut open: head, onion, swiss roll, croissant × (picked triangle, Axial,
Coronal, Sagittal)](docs/img/cut_open_gallery.png)

### The command-line demo (assignment item 7)

```bash
python Ex0.py data/MRHead.nrrd data/Ex0.csv out.mp4      # Ex0.bat / ./Ex0 / java -jar build/Ex0.jar are equivalent
```

Expected output for the course's `Ex0.csv` (its row 3 is deliberately not isosceles):

```
Warning : row 3 (line 4): side lengths differ: |ab| = 497.3932, |ac| = 476.8648 (must differ by < 0.001)
Warning : row 3 (line 4): drawn as given, with the algorithm's rails a1->b, a2->c
Volume  : MRHead.nrrd 256x256x130 INT16
Planes  : 4 from Ex0.csv
Window  : [0.00, 141.06]
Frames  : 462x504 @ 10 fps, nearest, 1 thread(s)
Projection: 4 frames in 25.5 ms (157 fps, I/O excluded)
Wrote   : out.mp4
```

And the lecturer's edge cases — `python Ex0.py data/dice.nrrd data/course/edge_cases.csv edge.mp4`
— give exactly what his file says: 16 frames, frames 6 and 9–16 black, a warning for frames 7–16
([EXPERIMENT.md §4](docs/EXPERIMENT.md)).

```
python Ex0.py <file.nrrd> <file.csv> <file.mp4> [options]       (no arguments: opens the app)

  file.nrrd   3-D volume (NRRD)
  file.csv    planes: one per row, "N, x1,y1,z1, ..." with N = 3 (a, b, c) or 4 (a1, b, a2, c),
              or plain rows of 9 / 12 numbers
  file.mp4    output video, one frame per CSV row, in order

Options
  --interp nearest|trilinear   sampling (default nearest)
  --fps N                      video frame rate, 1-120 (default 10)
  --window LO HI               grey window (default: 0.5-99.5 percentiles of the volume)
  --frames DIR                 also save every frame as PNG
  --threads N|auto             worker threads for the projection (default 1)
  --strict                     stop with exit 2 (no video) if any row breaks the plane rules
```

Exit codes: `0` ok · `1` usage · `2` invalid input · `3` output error · `4` no Java found (launcher).

More commands:

```bash
java -cp build/Ex0.jar ex1.app.Benchmark 500                                   # FPS table, NN/TL × threads
java -cp build/Ex0.jar ex1.app.SweepGenerator rotate 256 256 130 90 my.csv      # demo plane lists
java -cp build/Ex0.jar ex1.app.PhantomGenerator spheres uint8 128 my.nrrd       # known-answer volumes (also: dice)
scripts/render_demo_videos.sh                                                  # all videos of docs/EXPERIMENT.md
LECTURER=~/Downloads/his_folder scripts/render_demo_videos.sh                  # + his five use cases
```

## Configuration

There is no configuration file. Everything is in the window or a command-line option. Memory:
`Ex0.py` / `Ex0.bat` / `Ex0` start Java with `-Xmx2g`, enough for a 1000³ 8-bit volume; the app
(`Ex1 Viewer.bat`) uses Java's default, a quarter of the RAM — plenty for the lecturer's 500³
croissant. Use `java -Xmx… -jar build/Ex0.jar` for 16/32-bit volumes of 1000³.

## Tests

```bash
scripts/test.sh          # Linux / macOS — build with -Xlint:all -Werror, then 90 JUnit tests
scripts\test.bat         # Windows
./gradlew test           # Gradle (downloads Gradle once; JUnit itself is vendored in lib/)
```

The lecturer's files are acceptance tests: his `edge_cases.csv` on the die must give exactly the
frames, black frames and warnings his comment lists (`EdgeCasesTest`, frame by frame, pips
counted); his use cases must read without a note and survive our CSV writer bit for bit. CI
(`.github/workflows/ci.yml`) runs the tests on **ubuntu + windows × JDK 17 + 21**, then the demo
command on the course CSV and the edge cases with frame-count checks, a strict-mode failure check,
and a 500³ benchmark gate (≥ 30 FPS single-threaded). Strategy, unit tests and the manual test
script of the app: [docs/TESTING.md](docs/TESTING.md).

## Results (details and figures: [docs/EXPERIMENT.md](docs/EXPERIMENT.md))

| 500³ volume, 2-vCPU VM | 1 thread | 2 threads |
|---|---|---|
| Nearest neighbour | **281 – 436 FPS** | 372 – 456 |
| Trilinear | **110 – 136 FPS** | 149 – 167 |

The lecturer's use cases through the demo: 604–1 299 FPS on his 256×256×130 scans, **238 FPS on
his 500³ croissant**, all 640 rows without a warning. The app's 3-D view (cut open) redraws in
14–26 ms with 2 threads, 4–7 ms at the half resolution used while dragging; its solid model is
ready 0.55–0.92 s after opening a 256×256×130 scan (2.6 s for 500³).

The course's `Ex0.csv` on a 500³ phantom — rows 1–4, one frame each:

![Ex0.csv frames](docs/img/ex0_phantom500.png)

## Design in one paragraph

The sheet's algorithm walks two "rails" (`a1→b`, `a2→c`) one unit per row and samples each row
one unit per pixel, centred in the image. We implement it exactly, with the ambiguities resolved
and recorded (apex/base rows included, no accumulated rounding error, no division by zero, image
size, grey window — [ALGORITHM.md §5](docs/ALGORITHM.md)); the lecturer's files settled the rest
(points listed `a1, b, a2, c`; valid / doubtful / bad rows — ADR-0003, ADR-0004). Because the row
direction is constant for an isosceles trapezoid, the inner loop is `p += dp`, a bounds check and
one array read — that is where the speed comes from. Rows are independent, so threads split them
with bit-identical output. The app is a thin layer over the same `core`: a plane is *chosen* by its
points (picked on Slicer's three views or on the object, made isosceles with one stated change)
and *moved* as centre + angles + size (`PlaneSpec`); the 3-D view is a CPU ray caster over a
solid model of the scan (`SolidModel`, `CutawayRenderer`) that removes the half in front of the
plane and paints the scan's own grey values on the cut face — no OpenGL, so it runs on any JDK and
will port to Android with `core`.

## Project structure

```
Ex1 Viewer.bat · Ex0.py · Ex0.bat · Ex0    entry points: no arguments = the app, 3 arguments = the demo (item 7)
core/   pure Java (no java.io / AWT) -> reusable on Android
  ex1.core   Vec3, Volume (+Byte/Short/Int/Float), Plane, PlaneResolver,
             Projector (+Nearest/Trilinear/Parallel), Image2D, IntensityWindow
app/    desktop side
  ex1.io     NrrdReader/Writer, PlaneCsvReader/Writer, VideoWriter (+Mp4Writer/Ffmpeg/Png)
  ex1.app    StudioApp (the app), PlaneSpec, VolumeGeometry, SolidModel, CutawayRenderer,
             VolumeView3D + Raster + Camera3D, OrthoView, SliceView,
             Ex0 (demo main), FrameRenderer, Benchmark, SweepGenerator, PhantomGenerator
data/   dataset (data/README.md): MRHead.nrrd, dice.nrrd, phantoms, Ex0.csv, course/ (the lecturer's CSVs), plane lists
docs/   SRS.md · SDD.md · ALGORITHM.md (EDD) · TESTING.md (STP/STD) · EXPERIMENT.md · AI_USAGE.md · adr/
results/ output videos      scripts/ build & test scripts      lib/ JUnit 4 jars (offline tests)
```

## Documents (To-Do items 1–8)

| Item | Document |
|------|----------|
| 1 Problem & scope | this README |
| 2 SRS | [docs/SRS.md](docs/SRS.md) — requirements with IDs, use cases, traceability matrix |
| 3 Main algorithm | [docs/ALGORITHM.md](docs/ALGORITHM.md) — EDD: exact spec, 14 design decisions, validation rules, alternatives, error handling, budget |
| — Architecture | [docs/SDD.md](docs/SDD.md) — C4 diagrams, class/sequence diagrams, rationale, risks · [docs/adr/](docs/adr/) — 9 decision records |
| 4 Dataset | [data/README.md](data/README.md) — incl. the lecturer's test data |
| 5 Testing strategy | [docs/TESTING.md](docs/TESTING.md) — STP + STD (TC-01 … TC-105), incl. the lecturer's edge cases, the demo and the app |
| 6 Implementation | `core/`, `app/`; commit history of this repository |
| 7 Demo | `python Ex0.py file.nrrd file.csv file.mp4` (above) — and the app's **Export video…** |
| 8 Experiment | [docs/EXPERIMENT.md](docs/EXPERIMENT.md) + [results/](results/) |
| AI policy | [docs/AI_USAGE.md](docs/AI_USAGE.md), PR template with disclosure checklist |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md): branch per task, PR with the template (tests, docs, AI
disclosure), review by the other team member, CI green, docs updated in the same PR.

## License

[MIT](LICENSE). `data/MRHead.nrrd` is 3D Slicer sample data (BSD-style license), included
unchanged for the course demo. The lecturer's CSVs in `data/course/` are his course material,
included unchanged as test data.
