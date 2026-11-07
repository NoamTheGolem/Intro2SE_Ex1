package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ex1.core.Plane;
import ex1.core.Vec3;

import java.util.List;

import org.junit.Test;

/**
 * The app's default video when no use case is loaded: the plane swept through the volume.
 */
public class SweepTest
{
    /**
     * Checks the sweep of an axis plane: every slice once, one voxel apart, in order, with the
     * current plane among them (TC-107).
     */
    @Test
    public void axisPlaneVisitsEverySliceOnceAndContainsTheCurrentPlane()
    {
        // The plane y = 7 in a 10 x 20 x 30 volume: 20 slices y = 0 ... 19, one voxel apart.
        Plane p = Plane.trapezoid(new Vec3(0, 7, 0), new Vec3(9, 7, 0), new Vec3(0, 7, 29), new Vec3(9, 7, 29));
        Sweep.Pass pass = Sweep.through(p, 10, 20, 30);
        assertEquals(20, pass.planes.size());
        assertEquals(7.0, pass.planes.get(pass.current).a1().y, 1e-9);
        for (int i = 0; i < pass.planes.size(); i++)
        {
            Plane q = pass.planes.get(i);
            // Sorted along the normal, whatever its sign; every frame is a valid plane, only moved.
            assertEquals(Math.abs(q.a1().y - pass.planes.get(0).a1().y), i, 1e-9);
            assertEquals(0.0, q.a1().x, 1e-9);
            assertEquals(29.0, q.b().z, 1e-9);
            assertTrue(q.validate(Plane.DEFAULT_EPS).isEmpty());
        }
    }

    /**
     * Checks that a large volume is swept in larger steps (hundreds of frames, not thousands) and
     * that a triangle stays a triangle (TC-108).
     */
    @Test
    public void bigVolumesUseLargerStepsAndATriangleStaysATriangle()
    {
        // An oblique triangle in a 500^3 volume: about TARGET_FRAMES frames, never thousands.
        Plane t = Plane.triangle(new Vec3(250, 100, 250), new Vec3(100, 400, 250), new Vec3(400, 400, 260));
        Sweep.Pass pass = Sweep.through(t, 500, 500, 500);
        List<Plane> planes = pass.planes;
        assertTrue(planes.size() + " frames", planes.size() > Sweep.TARGET_FRAMES / 2 && planes.size() < 2 * Sweep.TARGET_FRAMES);
        assertTrue(planes.get(0).isTriangle());
        Plane current = planes.get(pass.current);
        assertEquals(t.a1().x, current.a1().x, 1e-9);
        assertEquals(t.c().z, current.c().z, 1e-9);
    }

    /**
     * Checks that points on one line (no normal to sweep along) give a video of that one plane
     * (TC-108).
     */
    @Test
    public void planeWithoutANormalIsTheWholeVideo()
    {
        Plane      line = Plane.triangle(new Vec3(0, 0, 0), new Vec3(5, 5, 5), new Vec3(10, 10, 10));
        Sweep.Pass pass = Sweep.through(line, 20, 20, 20);
        assertEquals(1, pass.planes.size());
        assertEquals(0, pass.current);
    }
}
