# Ex1 — Plane Projection of a 3D Volume

[![CI](https://github.com/minion-dog/Intro2SE_Ex1/actions/workflows/ci.yml/badge.svg)](https://github.com/minion-dog/Intro2SE_Ex1/actions/workflows/ci.yml)

A Java desktop app that loads a 3-D medical volume (NRRD, e.g. an MRI), **cuts it with any plane
you position on the model, shows the cut**, collects the planes of a use case into a list and
**exports that use case as an MP4 video** (plus the CSV of its planes). The projection runs at
hundreds of frames per second on one CPU core; the same code also runs as the command-line demo
the assignment sheet asks for (`Ex0.py file.nrrd file.csv file.mp4`).

**Course:** Introduction to Software Engineering (OOP/OOD), Ariel University — Exercise 1
**Team:** *<name 1 — ID>*, *<name 2 — ID>*
**Status:** complete; 76 automated tests; CI green on Linux and Windows (JDK 17 and 21)

![The app: the model (three mid-slices in its bounding box), the orange cutting plane textured with
its own cut, the cut as a 2-D image, the plane editor and the use-case list](docs/img/app_triangle.png)

---

## The problem

Given a 3-D raster `M` of size `sx × sy × sz` (`1 ≤ sx, sy, sz < 2^10`, unsigned 8/16/32-bit
entries) and a plane `p`, compute the 2-D image of the projection of `p` in `M` — the X/Y/Z
views of 3D Slicer, but along **any** plane. A plane is given by **3 points** (isosceles
triangle `a, b, c`) or **4 points** (isosceles trapezoid `a1, a2, b, c`), tolerance
`EPS = 0.001`. Required: correct, stable, **≥ 30 FPS (must) / ≥ 100 FPS (expected)** on a 500³
volume. The sheet calls the problem "not well-defined for implementation — a detailed design is
needed"; the design is the point of this repository.

## Scope

| In scope | Out of scope |
|----------|--------------|
| **The app**: open a volume (menu or drag-and-drop), see the model, place a plane on it with presets / sliders / mouse wheel, see the cut live | Physical / RAS (mm) coordinates — points are voxel indices |
| **Use cases**: a list of planes (typed in, or the course's CSV), optional smooth interpolation between them, play, **export as MP4 + CSV** | Colour / vector / 4-D volumes |
| The projection algorithm of the sheet (triangle + trapezoid), nearest-neighbour and trilinear sampling, optional multithreading | GPU, volume ray casting (the model view shows slices, not a rendered surface) |
| NRRD input: 3-D scalar, 8/16/32-bit signed/unsigned and float, raw/gzip, detached header | Networking, servers |
| The course's CSV format (`Ex0.csv`), validation per the Q&A, robust handling of bad rows; MP4 output with no external software | Android app (planned reuse of `core`) |
| Command-line demo (item 7), benchmark, dataset, tests, experiment | |

---

## Quick start

Requirements: **Java 17+** (JDK or JRE). Nothing else — the MP4 encoder is built in.

**Windows:** download / clone, double-click **`Ex1 Viewer.bat`**. The first start compiles
`build/Ex0.jar` if it is missing (needs a JDK; IntelliJ's and Android Studio's JDKs are found
automatically).
**Any OS:** `python Ex0.py` with no arguments, or `java -jar build/Ex0.jar`.

Then: **Open sample** (the MRHead volume and the course's `Ex0.csv` ship in `data/`), or
**Open NRRD…**, or drop a `.nrrd` file on the window.

### Using the app

| Where | What |
|-------|------|
| Left view — the model | The volume's bounding box with its three middle slices, the axes, and the **cutting plane in orange, textured with its own cut**. Drag to orbit, mouse wheel to slide the plane along its normal, Ctrl+wheel to zoom, double-click to reset. |
| Right view — the cut | The projection of the current plane, exactly as it goes into the video (`ALGORITHM.md`). The status line shows size, validity and the projection time. |
| Cutting plane | Presets **XY / XZ / YZ / Triangle / Oblique**, "slice through" `<` `>`, sliders for the centre, yaw / pitch / roll, width, height and top/bottom ratio (0 % = triangle, 100 % = rectangle). The **CSV row** of the plane is shown live. |
| Use case | The list of planes = the frames of the video. **Add current / Replace / Remove / Up / Down / Clear**; click a row to see it. **Load use cases (CSV)…** takes the course format (`Ex0.csv`), **Save CSV…** writes it. **Frames between planes** inserts interpolated planes for a smooth video; **Play** previews. |
| Export video… | Renders the list to an MP4 (built-in encoder, or ffmpeg when installed) and writes the CSV of the frames next to it. Running `Ex0.py volume.nrrd that.csv x.mp4` reproduces the video **byte for byte** (test `ExportRoundTripTest`). |

![Row 4 of the course's Ex0.csv in the app: a 400×500 trapezoid in the plane x = 200, reaching
outside the 256×256×130 volume](docs/img/app_row4.png)

### The command-line demo (assignment item 7)

```bash
python Ex0.py data/MRHead.nrrd data/Ex0.csv out.mp4      # Ex0.bat / ./Ex0 / java -jar build/Ex0.jar are equivalent
```

Expected output for the course's `Ex0.csv` (its rows 3–4 are deliberately irregular):

```
Warning : row 3 (line 4): side lengths differ: |ab| = 497.3932, |ac| = 476.8648 (must differ by < 0.001)
Warning : row 3 (line 4): projected anyway with the algorithm's rails a1->b, a2->c
Warning : row 4 (line 5): points are not valid in the documented order; read as two rails p1->p2 and p3->p4: ...
Volume  : MRHead.nrrd 256x256x130 INT16
Planes  : 4 from Ex0.csv
Frames  : 502x504 @ 10 fps, nearest, 1 thread(s)
Projection: 4 frames in 22.4 ms (178 fps, I/O excluded)
Wrote   : out.mp4
```

```
python Ex0.py <file.nrrd> <file.csv> <file.mp4> [options]       (no arguments: opens the app)

  file.nrrd   3-D volume (NRRD)
  file.csv    planes: one per row, "N, x1,y1,z1, ..." with N = 3 or 4 (course format),
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
java -cp build/Ex0.jar ex1.app.PhantomGenerator spheres uint8 128 my.nrrd       # known-answer volumes
scripts/render_demo_videos.sh                                                  # all videos of docs/EXPERIMENT.md
```

## Configuration

There is no configuration file. Everything is in the window or a command-line option. Memory:
the launchers start Java with `-Xmx2g`, enough for a 1000³ 8-bit volume; use `java -Xmx… -jar`
for 16/32-bit volumes of that size.

## Tests

```bash
scripts/test.sh          # Linux / macOS — build with -Xlint:all -Werror, then 76 JUnit tests
scripts\test.bat         # Windows
./gradlew test           # Gradle (downloads Gradle once; JUnit itself is vendored in lib/)
```

CI (`.github/workflows/ci.yml`) runs the tests on **ubuntu + windows × JDK 17 + 21**, then the
demo command on the course CSV with a frame-count check, a strict-mode failure check, and a
500³ benchmark gate (≥ 30 FPS single-threaded). Strategy, unit tests and the manual test
script of the app: [docs/TESTING.md](docs/TESTING.md).

## Results (details and figures: [docs/EXPERIMENT.md](docs/EXPERIMENT.md))

| 500³ volume, 2-vCPU VM | 1 thread | 2 threads |
|---|---|---|
| Nearest neighbour | **281 – 436 FPS** | 372 – 395 |
| Trilinear | **110 – 134 FPS** | 149 – 165 |

The course's `Ex0.csv` on a 500³ phantom — rows 1–4, one frame each:

![Ex0.csv frames](docs/img/ex0_phantom500.png)

## Design in one paragraph

The sheet's algorithm walks two "rails" (`a1→b`, `a2→c`) one unit per row and samples each row
one unit per pixel, centred in the image. We implement it exactly, with the ambiguities resolved
and recorded (apex/base rows included, no accumulated rounding error, no division by zero, image
size, grey window — [ALGORITHM.md §5](docs/ALGORITHM.md)). Because the row direction is constant
for an isosceles trapezoid, the inner loop is `p += dp`, a bounds check and one array read —
that is where the speed comes from. Rows are independent, so threads split them with
bit-identical output. The app is a thin layer over the same `core`: a plane is edited as
*centre + yaw/pitch/roll + width/height/top ratio* (`PlaneSpec`), converted to the sheet's
3 or 4 points for everything else, and the model view is a small software z-buffer renderer
(`Raster`) — no OpenGL, so it runs on any JDK and will port to Android with `core`.

## Project structure

```
Ex1 Viewer.bat · Ex0.py · Ex0.bat · Ex0    entry points: no arguments = the app, 3 arguments = the demo (item 7)
core/   pure Java (no java.io / AWT) -> reusable on Android
  ex1.core   Vec3, Volume (+Byte/Short/Int/Float), Plane, PlaneResolver,
             Projector (+Nearest/Trilinear/Parallel), Image2D, IntensityWindow
app/    desktop side
  ex1.io     NrrdReader/Writer, PlaneCsvReader/Writer, VideoWriter (+Mp4Writer/Ffmpeg/Png)
  ex1.app    StudioApp (the app), PlaneSpec, VolumeView3D + Raster + Camera3D, SliceView,
             Ex0 (demo main), FrameRenderer, Benchmark, SweepGenerator, PhantomGenerator
data/   dataset (data/README.md): MRHead.nrrd, phantoms, Ex0.csv, generated plane lists
docs/   SRS.md · SDD.md · ALGORITHM.md (EDD) · TESTING.md (STP/STD) · EXPERIMENT.md · AI_USAGE.md · adr/
results/ output videos      scripts/ build & test scripts      lib/ JUnit 4 jars (offline tests)
```

## Documents (To-Do items 1–8)

| Item | Document |
|------|----------|
| 1 Problem & scope | this README |
| 2 SRS | [docs/SRS.md](docs/SRS.md) — requirements with IDs, use cases, traceability matrix |
| 3 Main algorithm | [docs/ALGORITHM.md](docs/ALGORITHM.md) — EDD: exact spec, 14 design decisions, alternatives, error handling, budget |
| — Architecture | [docs/SDD.md](docs/SDD.md) — C4 diagrams, class/sequence diagrams, rationale, risks · [docs/adr/](docs/adr/) — 8 decision records |
| 4 Dataset | [data/README.md](data/README.md) |
| 5 Testing strategy | [docs/TESTING.md](docs/TESTING.md) — STP + STD (TC-01 … TC-100), incl. the demo and the app |
| 6 Implementation | `core/`, `app/`; commit history of this repository |
| 7 Demo | `python Ex0.py file.nrrd file.csv file.mp4` (above) — and the app's **Export video…** |
| 8 Experiment | [docs/EXPERIMENT.md](docs/EXPERIMENT.md) + [results/](results/) |
| AI policy | [docs/AI_USAGE.md](docs/AI_USAGE.md), PR template with disclosure checklist |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md): branch per task, PR with the template (tests, docs, AI
disclosure), review by the other team member, CI green, docs updated in the same PR.

## License

[MIT](LICENSE). `data/MRHead.nrrd` is 3D Slicer sample data (BSD-style license), included
unchanged for the course demo.
