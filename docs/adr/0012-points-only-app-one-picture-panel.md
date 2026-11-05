# ADR-0012: The app is "choose 3 points (or 4)" — no CSV, no sliders, one picture panel; Export never fails because of ffmpeg

**Status:** Accepted · **Date:** 2026-11-05 · supersedes the list / CSV parts of
[ADR-0011](0011-the-video-is-what-play-shows.md) (decisions 1 and 3) and part 1 of
[ADR-0008](0008-plane-spec-and-software-renderer.md) as far as the app's sliders go

## Context
The team tried v3.2 and asked, in their words: no need to slide the surface; *"CSV — I don't know
if necessary, I assume not"* (asked which part: **all CSV in the app**, the lecturer's use cases
only through `python Ex0.py`); the sliders should be checked against the teacher's instructions,
*"cuz from what I understood is just choose 3 points (or 4)"*; *"it doesn't let me use 4 points
when I click that option"*; *"the 3 photos below should be options: I have 1 (the triangle), then I
can move right or left to a different one; at the end it loops back to the triangle"*; and Export
showed an error message on their PC.

The assignment sheet itself defines only the algorithm and the command-line demo
(`python Ex0.py file.nrrd file.csv file.mp4`); the app is the lecturer's request in class:
choose 3 points and see the object's inside on that cut. Nothing asks for sliders, a threshold
control or a list of planes in the app.

The "4 points" bug: choosing it first stopped picking, which redrew the old (triangle) plane, and
the redraw set the buttons back to the plane's shape — so the switch undid itself every time.

The export error could not be reproduced here (Wine without ffmpeg writes a correct file). One
cause fits a PC like theirs: an `ffmpeg` on the PATH that cannot encode H.264 (LGPL builds, copies
inside other programs) — v3.2 trusted any `ffmpeg` it could start, and a failure was the end of
the export. Reproduced under Wine with such an `ffmpeg.exe`: "ffmpeg stopped accepting frames:
Unknown encoder 'libx264'". Other Windows-only causes: a video still open in a player cannot be
replaced; a protected folder refuses the write.

## Decision
1. **The plane comes from its points only**: 3 (triangle) or 4 (trapezoid), clicked on the views or
   on the object, or typed; the mouse wheel still slides it along its normal. The nine sliders,
   the Axial / Coronal / Sagittal preset buttons and the surface slider are gone (the surface stays
   automatic, Otsu's threshold, ADR-0009). `PlaneSpec` stays: it is how the wheel slides a plane,
   how "4 points" turns a triangle into a trapezoid, and how clicked points are made isosceles.
2. **No CSV in the app.** The list of planes, "Load its use case", "Load use case", "Add current
   plane" and the CSV written with an export are gone; a dropped `.csv` gets a hint with the
   command. CSVs — the lecturer's use cases and edge cases — are read by `Ex0` only, unchanged.
   The app's video is always the chosen plane swept through the scan (`Sweep`, ADR-0011).
3. **One picture panel** replaces the cut and the row of three slice views: picture 1 is the cut
   (the triangle), `◀ ▶` flip to Axial, Coronal and Sagittal, and after the last back to the cut.
   The arrows are drawn, not font glyphs (some Windows UI fonts lack them). When picking starts on
   the cut, which cannot be clicked, the panel turns to the Axial view and back when done — unless
   the user flipped it meanwhile.
4. **"4 points" reads the choice before anything is redrawn** (`changeShape`).
5. **Export never fails because of another program:** `VideoWriters` asks ffmpeg once per run
   (`-hide_banner -encoders`, 15 s timeout, output to a file so it cannot hang) and uses it only if
   it lists `libx264`; if it still fails half-way, the app and `Ex0` write the same frames again with
   the built-in encoder and say so. The proposed name is never taken (`<scan>_cut.mp4`, `_cut_2` …);
   a file chosen anyway is checked before replacing (a player may hold it); failures are explained
   in words (folder refused, file in use, disk full) with the technical detail below for a
   screenshot; a half-written file is deleted; "no program opened the video" is information, not
   an error.
6. The window is never larger than the screen (a laptop at 150 % scaling has about 1280 × 680).

## Alternatives considered
| Alternative | Why not |
|-------------|---------|
| Keep CSV loading in the app (v3.2) | The team chose "all CSV out": the app is the 3-point tool, the CLI is the use-case tool; one way each |
| Keep the presets as buttons | "Just choose 3 points (or 4)"; the slice views are one flip away and the points can be clicked there |
| Show the four pictures as tabs | Tabs need their labels' width in a narrow panel; arrows wrap around, as asked |
| Bundle ffmpeg | ≈ 100 MB more, a GPL binary in the repository; the built-in encoder already gives the same frames |
| Always use the built-in encoder | 10–20× larger files where a working ffmpeg exists |

## Consequences
+ The screen is the 3-D object, one picture, and two short panels: the points and the video.
+ "4 points" works; picking switches to a clickable view by itself.
+ An export on a PC with a broken ffmpeg succeeds (`VideoWritersTest`, `Ex0Test`
  `brokenFfmpegStillGivesTheVideo`; under Wine with a fake failing `ffmpeg.exe`: the app and the
  CLI both wrote the video).
− The app cannot play the lecturer's use cases any more; `python Ex0.py` renders them (as before).
− Precise moves (turn by 5°) are no longer in the app: retype or re-pick the points.
