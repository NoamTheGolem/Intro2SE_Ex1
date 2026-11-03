package ex1.io;

import java.io.IOException;

/**
 * Sink for 8-bit grey frames of a fixed size. Implementations: {@link FfmpegVideoWriter}
 * (desktop MP4), {@link PngFrameWriter} (frame dumps). An Android front-end would add a
 * MediaCodec implementation without touching the rest of the program.
 */
public interface VideoWriter extends AutoCloseable
{
    /**
     * @param (byte[]) grey width*height unsigned grey levels, row-major
     * @throws (IOException) on write errors
     */
    void writeFrame(byte[] grey) throws IOException;

    /**
     * Finishes the output; must be called for the file to be valid.
     *
     * @throws (IOException) on write errors
     */
    @Override
    void close() throws IOException;
}
