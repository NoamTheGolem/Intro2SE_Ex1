package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.core.VoxelType;
import ex1.io.NrrdWriter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The FPS (ADR-0014): how it is measured (the median of recent projections), how it is written
 * (three significant digits), and that it is on the frames of a video - in the corner, never on a
 * black frame, never on a frame too small to read it - unless the command line says --no-fps.
 */
public class FpsTest
{
    private static final int SIZE = 160;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void oneSlowFrameDoesNotMoveTheFps()
    {
        FpsMeter m = new FpsMeter();
        assertFalse(m.hasValue());
        assertEquals(0.0, m.fps(), 0.0);
        for (int i = 0; i < FpsMeter.SAMPLES - 1; i++)
        {
            m.add(1000000L);
        }
        // One frame 50 times slower (garbage collection): the median is still 1 ms.
        m.add(50000000L);
        assertTrue(m.hasValue());
        assertEquals(1000.0, m.fps(), 1e-9);
        assertEquals("1,000 FPS", m.text());
        m.reset();
        assertFalse(m.hasValue());
        // A whole run: the median frame, not the mean (which the slow frame would halve).
        long[] run = {1000000L, 1000000L, 1000000L, 50000000L, 1000000L};
        assertEquals(1000.0, FpsMeter.fpsOf(run, run.length), 1e-9);
        assertEquals(0.0, FpsMeter.fpsOf(run, 0), 0.0);
    }

    @Test
    public void theNumberIsShownToThreeSignificantDigits()
    {
        assertEquals("24,800 FPS", FpsMeter.format(24837));
        assertEquals("2,110 FPS", FpsMeter.format(2111));
        assertEquals("512 FPS", FpsMeter.format(512.4));
        assertEquals("100 FPS", FpsMeter.format(99.6));
        assertEquals("1,000 FPS", FpsMeter.format(999.6));
        assertEquals("4.2 FPS", FpsMeter.format(4.2));
    }

    @Test
    public void exportedFramesShowTheFpsInTheCornerAndBlackFramesStayBlack()
    {
        Volume volume = PhantomGenerator.build("spheres", VoxelType.UINT8, SIZE);
        IntensityWindow window = IntensityWindow.fromPercentiles(volume,
                IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
        List<Plane> planes = new ArrayList<Plane>();
        planes.add(apexUp(SIZE / 2.0));
        planes.add(null);
        Projector nn = Projector.create(Projector.Interpolation.NEAREST);
        FrameRenderer clean = new FrameRenderer(volume, planes, window, nn);
        FrameRenderer labelled = new FrameRenderer(volume, planes, window, nn);
        labelled.setShowFps(true);
        byte[] a = clean.render(0).clone();
        byte[] b = labelled.render(0);
        int w = clean.width();
        int h = clean.height();
        int changed = 0;
        for (int i = 0; i < a.length; i++)
        {
            if (a[i] != b[i])
            {
                changed++;
                assertTrue("pixel " + (i % w) + "," + (i / w) + " changed outside the label",
                        i % w < w * 0.6 && i / w < h / 3);
            }
        }
        assertTrue(changed + " pixels changed", changed > 100);
        byte[] black = labelled.render(1);
        for (int i = 0; i < black.length; i++)
        {
            assertEquals("pixel " + i + " of the black frame", 0, black[i]);
        }
    }

    @Test
    public void aFrameTooSmallToReadTheLabelGetsNone()
    {
        FpsOverlay overlay = new FpsOverlay();
        byte[] tiny = new byte[40 * 30];
        assertFalse(overlay.draw(tiny, 40, 30, "2,110 FPS"));
        for (int i = 0; i < tiny.length; i++)
        {
            assertEquals(0, tiny[i]);
        }
        byte[] big = new byte[300 * 200];
        assertTrue(overlay.draw(big, 300, 200, "2,110 FPS"));
        int bright = 0;
        for (int i = 0; i < big.length; i++)
        {
            if ((big[i] & 0xFF) > 200)
            {
                bright++;
            }
        }
        assertTrue(bright + " bright text pixels", bright > 30);
    }

    @Test
    public void commandLineWritesTheFpsUnlessNoFps() throws Exception
    {
        Volume volume = PhantomGenerator.build("spheres", VoxelType.UINT8, SIZE);
        File nrrd = tmp.newFile("phantom.nrrd");
        NrrdWriter.write(volume, nrrd, true, ByteOrder.LITTLE_ENDIAN);
        Plane p = apexUp(SIZE / 2.0);
        String row = "3," + p.a1().x + "," + p.a1().y + "," + p.a1().z + "," + p.b().x + "," + p.b().y + "," + p.b().z
                + "," + p.c().x + "," + p.c().y + "," + p.c().z + ",,,\n";
        File csv = tmp.newFile("one.csv");
        FileOutputStream out = new FileOutputStream(csv);
        out.write(("Number of Points (3 or 4),x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4\n" + row)
                .getBytes(StandardCharsets.UTF_8));
        out.close();

        BufferedImage withFps = runAndReadFrame(nrrd, csv, "with", new String[0]);
        BufferedImage without = runAndReadFrame(nrrd, csv, "without", new String[] {"--no-fps"});
        assertEquals(without.getWidth(), withFps.getWidth());
        // The apex is at the top middle: the frame's top-left corner is black without the label.
        assertEquals(0, brightIn(without, 0, 0, without.getWidth() / 3, without.getHeight() / 8));
        assertTrue(brightIn(withFps, 0, 0, withFps.getWidth() / 2, withFps.getHeight() / 6) > 30);
    }

    /**
     * @param (double) z height of the plane
     * @return an isosceles triangle across the volume at that height, apex a at the top middle
     */
    private static Plane apexUp(double z)
    {
        double m = SIZE - 1;
        return Plane.triangle(new Vec3(m / 2, 2, z), new Vec3(2, m - 2, z), new Vec3(m - 2, m - 2, z));
    }

    /**
     * @param (File) nrrd volume
     * @param (File) csv planes
     * @param (String) name run name
     * @param (String[]) extra extra options
     * @return frame 0 as written by --frames
     * @throws (Exception) when the run fails
     */
    private BufferedImage runAndReadFrame(File nrrd, File csv, String name, String[] extra) throws Exception
    {
        File mp4 = new File(tmp.getRoot(), name + ".mp4");
        File frames = new File(tmp.getRoot(), name);
        String[] base = {nrrd.getPath(), csv.getPath(), mp4.getPath(), "--frames", frames.getPath()};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        Ex0.run(args);
        return ImageIO.read(new File(frames, "frame_0000.png"));
    }

    /**
     * @param (BufferedImage) img frame
     * @param (int) x0 left
     * @param (int) y0 top
     * @param (int) w width
     * @param (int) h height
     * @return number of pixels brighter than 200 in the rectangle
     */
    private static int brightIn(BufferedImage img, int x0, int y0, int w, int h)
    {
        int n = 0;
        for (int y = y0; y < y0 + h; y++)
        {
            for (int x = x0; x < x0 + w; x++)
            {
                if ((img.getRGB(x, y) & 0xFF) > 200)
                {
                    n++;
                }
            }
        }
        return n;
    }
}
