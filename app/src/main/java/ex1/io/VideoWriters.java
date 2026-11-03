package ex1.io;

import java.io.File;
import java.io.IOException;

/**
 * Chooses the MP4 writer: ffmpeg when it is installed (small files), otherwise the built-in
 * pure-Java {@link Mp4Writer} (always works, larger files).
 */
public final class VideoWriters
{
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
        try
        {
            return new FfmpegVideoWriter(file, width, height, fps);
        }
        catch (IOException noFfmpeg)
        {
            return new Mp4Writer(file, width, height, fps);
        }
    }
}
