# ADR-0011: The video is what Play shows — exported in one click; the app samples nearest neighbour only

**Status:** Accepted · **Date:** 2026-11-05

## Context
The team tried v3.1.1 of the app and said, in their words: *"there is too much in the screen now
and some things aren't necessary"*; *"the export video doesn't work — it assumes I need to take
images manually instead of just taking the Play video, and that's it"*; *"the play button should
run back and forth"*; *"write the FPS above, I don't need to set the FPS"*; on sampling, that the
lecturer did not ask for trilinear and nearest point is preferred, even if more pixelated, *"because
it also adds to the FPS"*; and *"no need to write the calculation |bc| = |ac| = …"*.

In v3.1.1 **Export video** refused to run until planes had been added to a list by hand ("Add
current" per frame) or a CSV loaded; **Play** was disabled for the same reason and looped from
the end back to the start; the frame rate was a setting (default 15, while `Ex0` defaults to 10,
so an export at the default could not be reproduced by `Ex0` without `--fps`); the toolbar had a
Sampling box (nearest / trilinear), Load / Save CSV and a second Export; the plane panel had
Apply, Triangle, Oblique, back / forward, a CSV-row field and nine sliders always open; the
points panel always printed `|ab| = |ac| = …`.

## Decision
1. **One video, two buttons.** The video is the list of planes when there is one (the lecturer's
   CSV, or planes added by hand); otherwise it is the **current plane swept through the scan**
   (`Sweep`): copies of it moved along its unit normal in whole-voxel steps, from where its plane
   first meets the volume's box to where it last does — about 150 frames whatever the scan's size
   (step = round(range / 150), at least 1), and the current plane is one of them. **Play** shows
   that video; **Export video…** saves it. Nothing has to be collected first.
2. **Play runs back and forth** (…, n−1, n, n−1, …, 2, 1, 2, …) from the current plane (or the
   selected row) until Stop, so the picture never jumps; Stop keeps the frame on screen.
3. **Export saves one pass, first frame to last**, so frame *i* of a use case is row *i* of its
   CSV, as in the course's demo and its edge cases (ADR-0003). A sweep or a hand-made list also
   gets its planes written next to the MP4 (exact numbers, ADR-0008; a black frame as a row
   `a = b = c`, which Ex0 also draws black), so `Ex0 scan.nrrd that.csv out.mp4` makes the
   identical file; a video that is exactly a loaded CSV needs no copy of it.
4. **One rate, 10 fps**, the `Ex0` default, for Play and Export: Play shows the video at its real
   speed and an export is reproducible with no options. Not a setting.
5. **The FPS that matters is shown, not set:** the toolbar shows how many cuts per second the
   projection computes for the current plane — nearest neighbour, one thread, the median of the
   last 15 projections (one slow frame does not move it), after five untimed warm-up projections
   when a scan opens.
6. **Nearest neighbour only in the app** — the sheet's `M[p]`, the fastest, and what the video
   frames are. Trilinear stays in `core` (tested) and as `--interp trilinear` of the command line,
   for the comparison in EXPERIMENT §7.
7. **Show less:** the points panel prints only problems (doubtful, with a *Make isosceles* button
   shown just then; bad, with the reason) and a one-line hint; picking says in words what it
   changed (*c was moved a little along a–c*), with no numbers. Standard planes are the three
   views' slices; the sliders are folded under "Fine-tune with sliders"; the list appears only
   when it holds planes; Save CSV, Replace, Up / Down, Frames between, the fps box, the Sampling
   box, the CSV-row field and the 3-D view's timing text are gone.

## Alternatives considered
| Alternative | Why not |
|-------------|---------|
| Keep "Add current" as the only way to make a video | Exactly the complaint: a video should not need frames collected by hand |
| Export the back-and-forth too (n → 1 → n) | Doubles the file and breaks frame *i* = row *i* for the lecturer's use cases; offered as an option if wanted |
| Sweep only the part where the triangle itself meets the box | More geometry for a few black frames at the ends, which show the cut entering the object anyway |
| A fixed number of sweep frames per voxel (step 1 always) | 500 frames for a 500³ scan, 50 s of video; ~150 frames keeps every scan at 13–17 s |
| Keep the Sampling box, nearest by default | One more control nobody asked for; trilinear is ~3× slower and not what the sheet's `M[p]` means |
| Show the projection FPS of the last frame only | Jumps by ±50 % between frames (timer resolution, GC); the median of 15 is steady and still follows real changes |

## Consequences
+ Two clicks from an open scan to an MP4 (Play to watch, Export video… → Enter); NFR-08.
+ Play, Export and `Ex0` agree frame for frame — checked by `ExportRoundTripTest` (TC-86, TC-109)
  and by MD5 of the MP4s (TC-103).
+ The screen shows one column of three short panels; the advanced controls are one click away.
− Interpolated planes between list entries (`PlaneSpec.lerp`, v3.0 REQ-23) are no longer offered
  in the app; the code and its test stay, and the sweep covers the "smooth video" case.
− On a PC without ffmpeg the built-in writer stores I_PCM frames (ADR-0005): about 75 kB per
  frame of a 200 × 230 cut, so a 17 s sweep is ~13 MB. Same frames, larger file.
