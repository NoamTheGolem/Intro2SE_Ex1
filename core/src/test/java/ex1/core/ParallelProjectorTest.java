package ex1.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The multithreaded projector must give bit-identical results to the single-threaded one.
 */
public class ParallelProjectorTest
{
    /**
     * @param (int) n edge length
     * @return busy volume
     */
    private static Volume volume(int n)
    {
        Volume v = Volume.create(VoxelType.UINT16, n, n, n);
        for (int i = 0; i < v.voxelCount(); i++)
        {
            v.setValue(i, (i * 7919L) % 65521);
        }
        return v;
    }

    @Test
    public void identicalToSingleThreadForBothModes()
    {
        Volume v = volume(40);
        Plane oblique = Plane.triangle(new Vec3(20, 1.5, 3), new Vec3(-5, 38, 30), new Vec3(45, 38, 30));
        Plane rect = Plane.trapezoid(new Vec3(0, 0, 10.5), new Vec3(39, 0, 10.5), new Vec3(0, 39, 10.5), new Vec3(39, 39, 10.5));
        Projector.Interpolation[] modes = Projector.Interpolation.values();
        for (int m = 0; m < modes.length; m++)
        {
            Projector single = Projector.create(modes[m]);
            Projector parallel = Projector.create(modes[m], 3);
            assertTrue(parallel instanceof ParallelProjector);
            Plane[] planes = {oblique, rect};
            for (int k = 0; k < planes.length; k++)
            {
                Image2D a = single.project(v, planes[k]);
                Image2D b = parallel.project(v, planes[k]);
                assertArrayEquals(modes[m] + " plane " + k, a.pixels(), b.pixels(), 0f);
            }
            ((ParallelProjector) parallel).shutdown();
        }
    }

    @Test
    public void smallPlanesStayOnTheCallingThread()
    {
        Volume v = volume(10);
        Projector parallel = Projector.create(Projector.Interpolation.NEAREST, 4);
        Plane tiny = Plane.triangle(new Vec3(5, 0, 5), new Vec3(3, 3, 5), new Vec3(7, 3, 5));
        Image2D img = parallel.project(v, tiny);
        assertEquals(v.get(5, 0, 5), img.get(img.width() / 2, 0), 0f);
        ((ParallelProjector) parallel).shutdown();
    }

    @Test
    public void oneThreadMeansNoWrapper()
    {
        assertTrue(Projector.create(Projector.Interpolation.NEAREST, 1) instanceof NearestProjector);
        assertTrue(Projector.create(Projector.Interpolation.TRILINEAR, 0) instanceof TrilinearProjector);
    }

    @Test(expected = IllegalArgumentException.class)
    public void needsTwoThreads()
    {
        new ParallelProjector(new NearestProjector(), 1);
    }
}
