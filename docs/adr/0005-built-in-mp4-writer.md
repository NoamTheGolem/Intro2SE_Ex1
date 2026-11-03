# ADR-0005: Ship a dependency-free MP4 writer, use ffmpeg when present

**Status:** Accepted · **Date:** 2026-10-23

## Context
The JDK has no video encoder. Version 0 piped frames into ffmpeg, which failed on the team's
Windows machine ("ffmpeg not found") and would fail the same way on a grader's machine. Maven
libraries (JCodec) would break the offline build and add an unreviewed dependency.

## Decision
Write `Mp4Writer`: H.264 Constrained Baseline where every frame is an IDR picture of **I_PCM**
macroblocks (raw samples, no transform) in a minimal ISO-BMFF container (`ftyp`, `mdat`,
`moov` with `avcC`). Every player decodes it. `VideoWriters.openMp4` prefers
`FfmpegVideoWriter` (x264, small files) when `ffmpeg` is on the PATH and falls back to the
built-in writer otherwise. Correctness is proven by decoding the output with ffmpeg in a test
and comparing every pixel.

## Consequences
+ `python Ex0.py …` works on a clean machine with only Java; no install step for the grader.
+ No third-party jars; the build stays offline and reviewable.
− Built-in files are large (~1.5 bytes/pixel/frame, a 4-frame 500² video ≈ 1.5 MB, a 130-frame
  256² video ≈ 13 MB); documented, and ffmpeg is used automatically when available.
