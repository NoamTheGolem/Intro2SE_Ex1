package ex1.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Choosing the MP4 writer: an export must not fail because of the ffmpeg a computer happens to
 * have. No ffmpeg, or one without the H.264 encoder, gives the built-in writer; an ffmpeg that
 * fails half-way reports it, so the app can write the frames again with the built-in writer.
 */
public class VideoWritersTest
{
    @Rule

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Files of one test, deleted after it. */
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Points the app back at the real ffmpeg for the other tests. */
    @After
    public void restore()
    {
        System.clearProperty(VideoWriters.FFMPEG_PROPERTY);
        VideoWriters.forgetProbe();
    }

    /**
     * Checks that without ffmpeg the export gets the built-in writer, and that it writes the file
     * (TC-110).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void missingFfmpegGivesTheBuiltInWriter() throws IOException
    {
        useFfmpeg(new File(tmp.getRoot(), "no-such-ffmpeg").getPath());
        File        mp4 = new File(tmp.getRoot(), "a.mp4");
        VideoWriter w   = VideoWriters.openMp4(mp4, 32, 16, 10);
        assertFalse(VideoWriters.usesFfmpeg(w));
        w.writeFrame(new byte[32 * 16]);
        w.close();
        assertTrue(mp4.length() > 0);
    }

    /**
     * Checks that an ffmpeg without the H.264 encoder (an LGPL build) is not used (TC-111).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void ffmpegWithoutTheH264EncoderIsNotUsed() throws IOException
    {
        Assume.assumeFalse("the fake ffmpeg is a shell script", isWindows());
        useFfmpeg(fakeFfmpeg("lgpl", "echo ' V....D mpeg4                MPEG-4 part 2'").getPath());
        VideoWriter w = VideoWriters.openMp4(new File(tmp.getRoot(), "b.mp4"), 32, 16, 10);
        assertFalse(VideoWriters.usesFfmpeg(w));
        w.close();
    }

    /**
     * Checks that an ffmpeg that lists H.264 but fails while encoding is reported as an
     * IOException, after which the built-in writer still writes the file (TC-112).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void ffmpegThatFailsWhileEncodingIsReportedAndTheBuiltInWriterStillWorks() throws IOException
    {
        Assume.assumeFalse("the fake ffmpeg is a shell script", isWindows());
        // Lists libx264, then dies when asked to encode (as a broken or blocked copy would).
        useFfmpeg(fakeFfmpeg("broken", "echo ' V....D libx264              H.264 / AVC'").getPath());
        File        mp4 = new File(tmp.getRoot(), "c.mp4");
        VideoWriter w   = VideoWriters.openMp4(mp4, 32, 16, 10);
        assertTrue(VideoWriters.usesFfmpeg(w));
        try
        {
            w.writeFrame(new byte[32 * 16]);
            w.close();
            fail("a failing ffmpeg must be reported");
        }
        catch (IOException expected)
        {
            // StudioApp catches this and writes the same frames with the built-in writer:
            VideoWriter again = VideoWriters.openBuiltInMp4(mp4, 32, 16, 10);
            again.writeFrame(new byte[32 * 16]);
            again.close();
            assertTrue(mp4.length() > 0);
        }
    }

    /**
     * Checks that a real ffmpeg with H.264, when installed, passes the probe and is used (TC-112).
     */
    @Test
    public void realFfmpegWithH264IsUsed()
    {
        Assume.assumeTrue("ffmpeg not installed", runs("ffmpeg", "-version"));
        VideoWriters.forgetProbe();
        assertTrue(VideoWriters.ffmpegUsable());
    }

    /**
     * Points the app at another ffmpeg and forgets what it learned about the last one.
     * @param (String) program The ffmpeg to try from now on.
     */
    private static void useFfmpeg(String program)
    {
        System.setProperty(VideoWriters.FFMPEG_PROPERTY, program);
        VideoWriters.forgetProbe();
    }

    /**
     * Writes a fake ffmpeg: a script that lists the given encoders and fails when asked to encode.
     * @param (String) name File name.
     * @param (String) encodersLine What "-encoders" prints.
     * @return (File) An executable script: prints that line for "-hide_banner -encoders", else
     *                exits 1.
     * @throws (IOException) If it cannot be written.
     */
    private File fakeFfmpeg(String name, String encodersLine) throws IOException
    {
        File   f      = new File(tmp.getRoot(), name);
        String script = "#!/bin/sh\nif [ \"$2\" = \"-encoders\" ]; then\n  " + encodersLine + "\n  exit 0\nfi\nexit 1\n";
        Files.write(f.toPath(), script.getBytes(StandardCharsets.US_ASCII));
        assertTrue(f.setExecutable(true));
        return f;
    }

    /**
     * Tells whether the tests run on Windows.
     * @return (boolean) True on Windows.
     */
    private static boolean isWindows()
    {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Tells whether a command runs and succeeds.
     * @param (String...) cmd A command.
     * @return (boolean) Whether it runs and exits with 0.
     */
    private static boolean runs(String... cmd)
    {
        try
        {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor() == 0;
        }
        catch (IOException e)
        {
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
