# ADR-0014: An FPS that means something, shown on the video; a window that never waits for the 3-D view; a modern look

**Status:** Accepted · **Date:** 2026-11-07 · supersedes decisions 4 (10 fps) and 5 (the toolbar's
projection FPS) of [ADR-0011](0011-the-video-is-what-play-shows.md)

## Context
The team ran v3.3.2 on their PC and said, in their words: Windows SmartScreen stopped the exe
(*"Windows protected your PC … prevented an unrecognized app from starting"*); *"the view does work
but it shows 25000 FPS while it's static and it's really really slow — it seems like random
numbers"*; *"the FPS also needs to be presented in the video"*; and *"the overall look is like a
Windows XP app"*.

What the number was: from the moment a scan was open the toolbar showed *Projection: N FPS* — the
projection's speed for the current plane (nearest neighbour, one thread), measured again after each
change. For a small cut on a fast PC 25 000 is a true value (40 µs a frame), but it stood there while
nothing moved, next to a video that played at 10 frames per second — so it read as a random number.

Why it felt slow: Play ran at 10 fps (≈ 150 frames, 2–3 voxels a jump), and the 3-D view drew its
frames **on the Swing thread**. Every Play frame, drag and resize waited for a ray-cast frame:
measured on a 2-vCPU VM, 9–55 ms at half resolution and 37–209 ms sharp for 0.45–1.8 megapixels.
On a slower PC the buttons, the mouse and the video all waited for the 3-D picture.

The look was the system look and feel: on Windows Swing's classic controls and titled borders.

SmartScreen: an unsigned program downloaded from the internet is "unrecognized" until it has a
reputation; only a code-signing certificate removes the warning. *More info → Run anyway*, or
*Unblock* on the zip before extracting, opens the app.

## Decision
1. **FPS = how many frames (cuts) per second the projection computes**, measured on the frames being
   made: one second over the median of the last 15 frames' projection times — nearest neighbour,
   one thread; drawing, the 3-D view and saving are not counted — shown to three significant
   digits (`FpsMeter`). It is the measure of the assignment's "≥ 100 fps on 500³".
2. **Shown only while frames are being made**: while the video plays, in the toolbar (with an amber
   dot, a few times a second) and in the corner of the picture (every frame). While nothing plays
   the toolbar says *Play to measure FPS* and the picture shows none.
3. **In the video**: every exported frame that was projected carries its FPS (the same meter) in its
   top-left corner — light text on a dark rounded label, sized to the frame (`FpsOverlay`). A black
   frame (a bad plane) stays black; a frame too small for a readable label gets none. The command
   line does the same by default — the assignment's video carries its own speed — and `--no-fps`
   writes clean frames (the tests that compare pixels use it). The app exports with the one-thread
   projector, so its numbers are the Play numbers.
4. **The video at 30 fps**: Play and Export run at 30 frames per second, and a sweep has about 200
   frames (step = range / 200 voxels, at least 1): 6–9 s a pass, smooth. The command line keeps
   `--fps` 10 as its default: there each CSV row is a different plane.
5. **The window never waits for the 3-D view**: its frames are drawn on a thread of their own. A
   change asks for a frame, a newer request replaces one still waiting, and the window only copies
   the last finished frame to the screen, with the camera it was drawn with (so the points sit on
   it). Coarse while something moves — resizing too — and sharp 160 ms after the last change; very
   large views coarser. One core is left to the window: the 3-D ray threads are cores − 1.
6. **A modern look, without a library** (`ex1.app.ui.Theme`): Swing's cross-platform look with our
   colours, the system's UI font (Segoe UI on Windows) and a few drawn controls — flat rounded
   buttons, a two-part switch for 3 / 4 points, rounded fields with a focus ring, thin scroll bars,
   a flat slider, round dialog icons. **Dark graphite**, because the grey scans read best on dark,
   as in a reading room, and the views were dark already; **amber, the plane's colour in the views,
   for everything that is the plane**: the names of its points, the frame of the cut, Play, the FPS
   dot, the numbered steps. The system's file dialogs and title bar stay native. SRS C-3 (no
   third-party runtime libraries) holds.

## Alternatives considered
| Alternative | Why not |
|-------------|---------|
| FPS = frames painted on screen per second | 30 while playing (the video's own rate) and 0 when still: says nothing about the algorithm, and a grader would read 30 as failing "≥ 100" |
| Play as fast as the computer can, FPS = frames shown per second | A sweep would pass in a fraction of a second; to be watchable the motion would have to follow the clock, and the export (a fixed frame rate) would no longer be what Play shows |
| Keep the FPS in the toolbar all the time | The complaint itself: a number that stands while nothing happens reads as random |
| FPS on screen only, not in the files | The team asked for it in the video; the video is what the grader keeps |
| One average FPS for the whole video, printed | Kept: the command line prints it; on the frames a running value shows that each frame was computed that fast |
| FlatLaf (a third-party look and feel) | A modern look in one line, but a third-party runtime library (C-3), a jar to vendor and native DLLs it unpacks on Windows — for the dozen components this window has |
| A light theme | The views are dark (grey scans on black); a light window around them glares and splits the screen in two |
| A code-signing certificate | Removes SmartScreen's warning, but costs a yearly fee and a registered identity — not for a course exercise |

## Consequences
+ The number moves when the picture moves, and it is the assignment's measure; the exported video
  carries the same number, and `Ex0` prints its average.
+ Play stays at ~30 fps on a 2-vCPU VM while the 3-D view is dragged; the mouse and the buttons
  never wait for a 3-D frame.
+ The app looks like a current desktop program, the same on Windows and Linux, with no new
  dependency (`LookAndSpeedTest` checks that every drawn control is installed and paints).
− The FPS changes during a sweep: frames through air are cheaper than frames through the object
  (about 10 000 vs 2 300 FPS on the head, on the VM). It is a true value; the tooltip says what it is.
− Exported and command-line frames now differ between runs in the label's digits; `--no-fps`
  gives the reproducible frames (the byte-for-byte comparisons of two runs, TC-106, use it).
− The 3-D view can show the previous frame for a moment while the next one is drawn.
