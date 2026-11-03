package ex1.io;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Writes each frame as frame_0000.png, frame_0001.png, ... in a folder
 * (option --frames; useful for reports and when ffmpeg is unavailable).
 */
public final class PngFrameWriter implements VideoWriter
{
    private final File dir;
    private final int width;
    private final int height;
    private int index;

    /**
     * @param (File) dir destination folder (created if missing)
     * @param (int) width frame width
     * @param (int) height frame height
     * @throws (IOException) if the folder cannot be created
     */
    public PngFrameWriter(File dir, int width, int height) throws IOException
    {
        if (!dir.isDirectory() && !dir.mkdirs())
        {
            throw new IOException("cannot create folder " + dir);
        }
        this.dir = dir;
        this.width = width;
        this.height = height;
        this.index = 0;
    }

    @Override
    public void writeFrame(byte[] grey) throws IOException
    {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(grey, 0, dst, 0, dst.length);
        File f = new File(dir, String.format("frame_%04d.png", index));
        if (!ImageIO.write(img, "png", f))
        {
            throw new IOException("no PNG writer available");
        }
        index++;
    }

    @Override
    public void close()
    {
        // Each PNG is complete when written; nothing to finish.
    }
}
