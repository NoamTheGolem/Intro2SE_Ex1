package ex1.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.core.VoxelType;
import ex1.io.PlaneCsvReader;
import ex1.io.PlaneCsvWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * The app's "Export video" and the Ex0 command line must agree: the frames the app encodes
 * (rendered from its in-memory planes) are the frames Ex0 renders from the CSV the app saved
 * next to the video. Byte-for-byte, on a phantom, for an interpolated sequence of oblique planes.
 */
public class ExportRoundTripTest
{
    /**
     * Checks that the app's video and the command line's rendering of the CSV saved next to it are
     * the same frames, byte for byte (TC-86).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void appFramesEqualCommandLineFramesOfTheSavedCsv() throws IOException
    {
        Volume volume = PhantomGenerator.build("spheres", VoxelType.UINT8, 64);
        IntensityWindow window = IntensityWindow.fromPercentiles(volume,
                IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
        PlaneSpec   from   = new PlaneSpec(31.5, 31.5, 31.5, 30, -20, 0, 50, 1.0, 44);
        PlaneSpec   to     = new PlaneSpec(40.25, 20.75, 33.0, -75, 40, 25, 38, 0.0, 61);
        List<Plane> source = new ArrayList<Plane>();
        for (int k = 0; k <= 12; k++)
        {
            source.add(PlaneSpec.lerp(from, to, k / 12.0).toPlane());
        }
        // As StudioApp.videoPass(): round to the CSV precision.
        List<Plane> inApp = StudioApp.videoFrames(source);

        File csv = File.createTempFile("ex1-export", ".csv");
        csv.deleteOnExit();
        PlaneCsvWriter.write(inApp, csv);
        PlaneCsvReader.Result back = PlaneCsvReader.read(csv, Plane.DEFAULT_EPS);
        assertTrue(back.errors.toString(), back.isValid());
        assertTrue(back.warnings().toString(), back.warnings().isEmpty());
        assertEquals(inApp.size(), back.planes().size());

        Projector     nn  = Projector.create(Projector.Interpolation.NEAREST);
        FrameRenderer app = new FrameRenderer(volume, inApp, window, nn);
        FrameRenderer cli = new FrameRenderer(volume, back.planes(), window, nn);
        assertEquals(app.width(), cli.width());
        assertEquals(app.height(), cli.height());
        for (int i = 0; i < inApp.size(); i++)
        {
            assertArrayEquals("frame " + i, app.render(i), cli.render(i));
        }
    }

    /**
     * Checks the app's video: one frame per step of the sweep, and a plane that cannot be drawn
     * gives a black frame in its place (TC-109).
     */
    @Test
    public void defaultVideoHasOneFramePerSweepStepAndABadPlaneIsBlack()
    {
        // The app's video: an oblique triangle swept through the scan, here with a plane the
        // algorithm cannot draw (a = b = c) in the middle.
        Volume volume = PhantomGenerator.build("spheres", VoxelType.UINT8, 48);
        IntensityWindow window = IntensityWindow.fromPercentiles(volume,
                IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
        Plane       start  = new PlaneSpec(23.5, 23.5, 23.5, 35, -25, 10, 40, 0.0, 36).toPlane();
        List<Plane> source = new ArrayList<Plane>(Sweep.through(start, 48, 48, 48).planes);
        int         black  = source.size() / 2;
        Vec3        o      = new Vec3(5, 5, 5);
        source.set(black, Plane.triangle(o, o, o));
        List<Plane> frames = StudioApp.videoFrames(source);
        assertEquals(source.size(), frames.size());
        assertEquals(null, frames.get(black));

        Projector     nn         = Projector.create(Projector.Interpolation.NEAREST);
        FrameRenderer video      = new FrameRenderer(volume, frames, window, nn);
        byte[]        blackFrame = video.render(black).clone();
        for (int i = 0; i < blackFrame.length; i++)
        {
            assertEquals("pixel " + i, 0, blackFrame[i]);
        }
        // The other frames show the scan: the sweep passes through the phantom's spheres.
        int bright = 0;
        for (int i = 0; i < frames.size(); i++)
        {
            byte[] f = video.render(i);
            for (int k = 0; k < f.length; k++)
            {
                if (f[k] != 0)
                {
                    bright++;
                    break;
                }
            }
        }
        assertTrue(bright + " of " + frames.size() + " frames show something", bright > frames.size() / 2);
    }
}
