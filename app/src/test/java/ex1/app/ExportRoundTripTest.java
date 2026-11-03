package ex1.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
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
    @Test
    public void appFramesEqualCommandLineFramesOfTheSavedCsv() throws IOException
    {
        Volume volume = PhantomGenerator.build("spheres", VoxelType.UINT8, 64);
        IntensityWindow window = IntensityWindow.fromPercentiles(volume,
                IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
        PlaneSpec from = new PlaneSpec(31.5, 31.5, 31.5, 30, -20, 0, 50, 1.0, 44);
        PlaneSpec to = new PlaneSpec(40.25, 20.75, 33.0, -75, 40, 25, 38, 0.0, 61);
        List<Plane> inApp = new ArrayList<Plane>();
        for (int k = 0; k <= 12; k++)
        {
            // As StudioApp.expandedPlanes(): interpolate, then round to the CSV precision.
            inApp.add(PlaneCsvWriter.quantize(PlaneSpec.lerp(from, to, k / 12.0).toPlane()));
        }

        File csv = File.createTempFile("ex1-export", ".csv");
        csv.deleteOnExit();
        PlaneCsvWriter.write(inApp, csv);
        PlaneCsvReader.Result back = PlaneCsvReader.read(csv, Plane.DEFAULT_EPS);
        assertTrue(back.errors.toString(), back.isValid());
        assertTrue(back.warnings().toString(), back.warnings().isEmpty());
        assertEquals(inApp.size(), back.planes().size());

        Projector nn = Projector.create(Projector.Interpolation.NEAREST);
        FrameRenderer app = new FrameRenderer(volume, inApp, window, nn);
        FrameRenderer cli = new FrameRenderer(volume, back.planes(), window, nn);
        assertEquals(app.width(), cli.width());
        assertEquals(app.height(), cli.height());
        for (int i = 0; i < inApp.size(); i++)
        {
            assertArrayEquals("frame " + i, app.render(i), cli.render(i));
        }
    }
}
