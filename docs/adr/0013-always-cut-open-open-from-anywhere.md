# ADR-0013: Always cut open; open a scan from anywhere (the system's file dialog) or by dragging; no sample menu

**Status:** Accepted · **Date:** 2026-11-05 · follows [ADR-0012](0012-points-only-app-one-picture-panel.md)

## Context
After v3.3 the team asked, in their words: *"we can also put down the 'cut open' check box — it
will always be cut"*, *"no need for samples and such"*, and *"I don't want the ability to choose
just from a folder; I want from the whole PC, or just by dragging"*.

v3.3 opened scans with Swing's `JFileChooser`, which started in the app's own `data` folder and,
with the Windows look, looks like an old dialog — it can browse elsewhere, but does not feel like
it. The **Samples** menu offered only the scans next to the app. **Cut open** switched the 3-D
view between the cut object and the whole one; the cut is the point of the app.

## Decision
1. **The 3-D view is always cut open**; the check box is gone (`VolumeView3D` keeps its switch,
   set once).
2. **No Samples menu.** The app download still carries the lecturer's scans in `data\lecturer\`;
   they are opened like any other file.
3. **Open NRRD… uses the system's own dialog** (`java.awt.FileDialog`): on Windows the Explorer
   dialog (Desktop, Downloads, This PC, any drive), on Linux GTK's. It starts in the open scan's
   folder, else in Downloads, else the home folder, and shows only `.nrrd`/`.nhdr` files. **Export
   video…** uses the system's Save dialog too (it asks before replacing a file itself).
4. **Dragging stays the other way in:** a scan dropped anywhere on the window — or on `Ex1.exe` —
   opens; the toolbar says so next to the button.

## Consequences
+ The toolbar is one button, a hint and the FPS; nothing on screen the user did not ask for.
+ The dialogs are the ones the user knows from every other Windows program.
− The whole object can no longer be seen uncut (rotate the view, or move the plane out of it with
  the wheel).
− AWT's `FileDialog` has no "file type" list on Windows; the filter is the pattern
  `*.nrrd;*.nhdr` in the name box.
