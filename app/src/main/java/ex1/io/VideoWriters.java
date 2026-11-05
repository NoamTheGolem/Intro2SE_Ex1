package ex1.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Chooses the MP4 writer: ffmpeg when it is installed and can encode H.264 (small files),
 * otherwise the built-in pure-Java {@link Mp4Writer} (always works, larger files). Both give the
 * same frames.
 *
 * Some ffmpeg builds cannot encode H.264 - LGPL builds, or copies that other programs put on the
 * PATH - and would make every export fail, so ffmpeg is asked once ("-encoders") before it is
 * trusted with a video.
 */
public final class VideoWriters
{
    /** System property naming the ffmpeg program; tests point it at a fake one. */
    public static final String FFMPEG_PROPERTY = "ex1.ffmpeg";
    private static final long PROBE_TIMEOUT_MS = 15000;
    private static final String H264_ENCODER = "libx264";

    private static Boolean ffmpegUsable;

    private VideoWriters()
    {
    }

    /**
     * @param (File) file destination .mp4
     * @param (int) width frame width (even)
     * @param (int) height frame height (even)
     * @param (int) fps frames per second
     * @return an open writer
     * @throws (IOException) if neither writer can create the file
     */
    public static VideoWriter openMp4(File file, int width, int height, int fps) throws IOException
    {
        if (ffmpegUsable())
        {
            try
            {
                return new FfmpegVideoWriter(file, width, height, fps);
            }
            catch (IOException noFfmpeg)
            {
                // Fall through to the writer that needs nothing.
            }
        }
        return new Mp4Writer(file, width, height, fps);
    }

    /**
     * The writer that needs no other program, for a second try when ffmpeg failed half-way.
     *
     * @param (File) file destination .mp4
     * @param (int) width frame width (even)
     * @param (int) height frame height (even)
     * @param (int) fps frames per second
     * @return an open built-in writer
     * @throws (IOException) if the file cannot be created
     */
    public static VideoWriter openBuiltInMp4(File file, int width, int height, int fps) throws IOException
    {
        return new Mp4Writer(file, width, height, fps);
    }

    /**
     * @param (VideoWriter) w a writer from {@link #openMp4}
     * @return true when it pipes into ffmpeg
     */
    public static boolean usesFfmpeg(VideoWriter w)
    {
        return w instanceof FfmpegVideoWriter;
    }

    /**
     * @return the ffmpeg program to run: "ffmpeg" (found on the PATH) unless the property says otherwise
     */
    static String ffmpegProgram()
    {
        return System.getProperty(FFMPEG_PROPERTY, "ffmpeg");
    }

    /**
     * Asks ffmpeg once per run whether it can encode H.264.
     *
     * @return true when "ffmpeg -hide_banner -encoders" exits normally and lists libx264
     */
    static synchronized boolean ffmpegUsable()
    {
        if (ffmpegUsable == null)
        {
            ffmpegUsable = Boolean.valueOf(probe());
        }
        return ffmpegUsable.booleanValue();
    }

    /** Forgets the answer of {@link #ffmpegUsable}, so the next video asks again (tests). */
    public static synchronized void forgetProbe()
    {
        ffmpegUsable = null;
    }

    /**
     * @return whether the ffmpeg program lists the H.264 encoder
     */
    private static boolean probe()
    {
        // The listing goes to a file, not a pipe: waiting on a pipe could block forever if the
        // program never ends; a file plus a timeout cannot.
        File listing = new File(System.getProperty("java.io.tmpdir"),
                "ex1-ffmpeg-probe-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + ".txt");
        ProcessBuilder pb = new ProcessBuilder(ffmpegProgram(), "-hide_banner", "-encoders");
        pb.redirectErrorStream(true);
        pb.redirectOutput(listing);
        try
        {
            Process p = pb.start();
            p.getOutputStream().close();
            if (!p.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            {
                p.destroyForcibly();
                return false;
            }
            String text = new String(Files.readAllBytes(listing.toPath()), StandardCharsets.UTF_8);
            return p.exitValue() == 0 && text.contains(H264_ENCODER);
        }
        catch (IOException e)
        {
            // Not installed (or not runnable): the built-in writer it is.
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
        finally
        {
            if (listing.exists() && !listing.delete())
            {
                listing.deleteOnExit();
            }
        }
    }
}
