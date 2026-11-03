package ex1.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Built-in MP4 writer: emulation prevention, file structure, and (when ffmpeg exists) a decode
 * round trip that compares every pixel.
 */
public class Mp4WriterTest
{
    private static final int W = 34;
    private static final int H = 18;
    private static final int FRAMES = 4;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void escapeInsertsPreventionBytes()
    {
        byte[] in = {0, 0, 0, 0, 0, 1, 5, 0, 0, 3};
        byte[] expected = {0, 0, 3, 0, 0, 3, 0, 1, 5, 0, 0, 3, 3};
        assertArrayEquals(expected, Mp4Writer.escape(in));
    }

    @Test
    public void fileHasFtypMdatMoov() throws IOException
    {
        File f = write();
        byte[] data = Files.readAllBytes(f.toPath());
        String text = new String(data, StandardCharsets.ISO_8859_1);
        assertEquals("ftyp", text.substring(4, 8));
        assertTrue(text.contains("mdat"));
        assertTrue(text.contains("moov"));
        assertTrue(text.contains("avcC"));
        assertTrue(text.indexOf("mdat") < text.indexOf("moov"));
    }

    @Test
    public void ffmpegDecodesEveryPixel() throws Exception
    {
        Assume.assumeTrue("ffmpeg not installed", ffmpegAvailable());
        File f = write();
        Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-i", f.getPath(), "-f", "rawvideo",
                "-pix_fmt", "gray", "-vf", "scale=in_range=tv:out_range=pc", "-").start();
        InputStream in = p.getInputStream();
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r = in.read(buf);
        while (r >= 0)
        {
            decoded.write(buf, 0, r);
            r = in.read(buf);
        }
        p.waitFor();
        byte[] got = decoded.toByteArray();
        assertEquals(W * H * FRAMES, got.length);
        for (int k = 0; k < FRAMES; k++)
        {
            byte[] expected = pattern(k);
            for (int i = 0; i < expected.length; i++)
            {
                int diff = Math.abs((expected[i] & 0xFF) - (got[k * W * H + i] & 0xFF));
                assertTrue("frame " + k + " pixel " + i + " off by " + diff, diff <= 1);
            }
        }
    }

    /**
     * @return a small video of known frames (one all-black, to exercise emulation prevention)
     * @throws (IOException) on write errors
     */
    private File write() throws IOException
    {
        File f = new File(tmp.getRoot(), "t.mp4");
        Mp4Writer w = new Mp4Writer(f, W, H, 10);
        for (int k = 0; k < FRAMES; k++)
        {
            w.writeFrame(pattern(k));
        }
        w.close();
        return f;
    }

    /**
     * @param (int) k frame number
     * @return test frame k
     */
    private static byte[] pattern(int k)
    {
        byte[] g = new byte[W * H];
        if (k == 1)
        {
            return g;
        }
        for (int i = 0; i < g.length; i++)
        {
            g[i] = (byte) ((i % W) * 255 / (W - 1) + k * 37);
        }
        return g;
    }

    /**
     * @return true when ffmpeg runs
     */
    private static boolean ffmpegAvailable()
    {
        try
        {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
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
