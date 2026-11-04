package ex1.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes an H.264 MP4 by piping raw grey frames into an ffmpeg process.
 *
 * Why ffmpeg: no pure-Java H.264 encoder ships with the JDK, and a process pipe keeps the
 * project free of third-party jars. ffmpeg's messages go to a temp log that is shown only
 * when encoding fails.
 */
public final class FfmpegVideoWriter implements VideoWriter
{
    private static final String CRF = "18";

    private final File output;
    private final int frameBytes;
    private final Process process;
    private final OutputStream stdin;
    private final File log;

    /**
     * Starts ffmpeg.
     *
     * @param (File) output destination .mp4
     * @param (int) width frame width (must be even for yuv420p)
     * @param (int) height frame height (must be even for yuv420p)
     * @param (int) fps frames per second of the video
     * @throws (IOException) if ffmpeg cannot be started
     */
    public FfmpegVideoWriter(File output, int width, int height, int fps) throws IOException
    {
        if (width % 2 != 0 || height % 2 != 0)
        {
            throw new IllegalArgumentException("H.264 needs even frame sizes, got " + width + "x" + height);
        }
        this.output = output;
        this.frameBytes = width * height;
        // A plain unique name instead of File.createTempFile: that one seeds a SecureRandom first,
        // which enumerates the network adapters - slow on some Windows machines, for a log name.
        this.log = new File(System.getProperty("java.io.tmpdir"),
                "ex1-ffmpeg-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + ".log");
        this.log.deleteOnExit();

        List<String> cmd = new ArrayList<String>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("gray");
        cmd.add("-s");
        cmd.add(width + "x" + height);
        cmd.add("-r");
        cmd.add(Integer.toString(fps));
        cmd.add("-i");
        cmd.add("-");
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-crf");
        cmd.add(CRF);
        // yuv420p: the only pixel format every player (Windows, browsers, phones) decodes.
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add(output.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(log);
        try
        {
            this.process = pb.start();
        }
        catch (IOException e)
        {
            throw new IOException("cannot start ffmpeg - is it installed and on the PATH? ("
                    + e.getMessage() + ")");
        }
        this.stdin = new BufferedOutputStream(process.getOutputStream(), 1 << 20);
    }

    @Override
    public void writeFrame(byte[] grey) throws IOException
    {
        if (grey.length != frameBytes)
        {
            throw new IllegalArgumentException("frame has " + grey.length + " bytes, expected " + frameBytes);
        }
        try
        {
            stdin.write(grey);
        }
        catch (IOException e)
        {
            throw new IOException("ffmpeg stopped accepting frames: " + readLog());
        }
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            stdin.close();
        }
        catch (IOException e)
        {
            // Reported below through the exit code and log.
        }
        int code;
        try
        {
            code = process.waitFor();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException("interrupted while waiting for ffmpeg");
        }
        if (code != 0)
        {
            throw new IOException("ffmpeg failed (exit " + code + ") writing " + output + ": " + readLog());
        }
    }

    /**
     * @return ffmpeg's error output, trimmed
     */
    private String readLog()
    {
        try
        {
            return new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8).trim();
        }
        catch (IOException e)
        {
            return "(no ffmpeg log)";
        }
    }
}
