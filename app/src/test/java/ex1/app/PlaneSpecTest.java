package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.core.VoxelType;

import org.junit.Test;

/**
 * The editable plane model behind the app: every spec is a valid plane, specs survive a round
 * trip through Plane, and interpolation between specs stays valid (smooth videos).
 */
public class PlaneSpecTest
{
    private static final double EPS = Plane.DEFAULT_EPS;

    @Test
    public void presetsAreValidAndAxisAligned()
    {
        Volume v = Volume.create(VoxelType.UINT8, 100, 80, 60);
        Plane xy = PlaneSpec.xy(v).toPlane();
        Plane xz = PlaneSpec.xz(v).toPlane();
        Plane yz = PlaneSpec.yz(v).toPlane();
        assertTrue(xy.validate(EPS).isEmpty());
        assertTrue(xz.validate(EPS).isEmpty());
        assertTrue(yz.validate(EPS).isEmpty());
        // XY: constant z, columns along x, rows along y.
        assertEquals(29.5, xy.a1().z, 1e-9);
        assertEquals(29.5, xy.c().z, 1e-9);
        assertEquals(100, xy.imageWidth());
        assertEquals(80, xy.imageHeight());
        // XZ: constant y.
        assertEquals(39.5, xz.a1().y, 1e-9);
        assertEquals(100, xz.imageWidth());
        assertEquals(60, xz.imageHeight());
        // YZ: constant x.
        assertEquals(49.5, yz.b().x, 1e-9);
        assertEquals(60, yz.imageWidth());
        assertEquals(80, yz.imageHeight());
        assertTrue(PlaneSpec.triangle(v).toPlane().isTriangle());
        assertTrue(PlaneSpec.oblique(v).toPlane().validate(EPS).isEmpty());
    }

    @Test
    public void anySpecIsAValidPlane()
    {
        double[][] cases = {
            {10, 20, 30, 0, 0, 0, 50, 1.0, 40},
            {10, 20, 30, 123, -45, 77, 50, 0.5, 40},
            {-5, 300, 2.5, -170, 89, -180, 7, 0.0, 300},
            {0, 0, 0, 45, 45, 45, 2, 0.01, 2},
        };
        for (int i = 0; i < cases.length; i++)
        {
            double[] c = cases[i];
            PlaneSpec s = new PlaneSpec(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
            Plane p = s.toPlane();
            assertTrue("case " + i + ": " + p.validate(EPS), p.validate(EPS).isEmpty());
            assertEquals("case " + i, c[7] <= 0.0, p.isTriangle());
        }
    }

    @Test
    public void fromPlaneRecoversTheSpec()
    {
        PlaneSpec s = new PlaneSpec(101.5, 77.25, 64.0, 33, -21, 12, 171, 0.62, 203);
        PlaneSpec back = PlaneSpec.fromPlane(s.toPlane());
        assertEquals(s.cx, back.cx, 1e-6);
        assertEquals(s.cy, back.cy, 1e-6);
        assertEquals(s.cz, back.cz, 1e-6);
        assertEquals(s.yaw, back.yaw, 1e-6);
        assertEquals(s.pitch, back.pitch, 1e-6);
        assertEquals(s.roll, back.roll, 1e-6);
        assertEquals(s.width, back.width, 1e-6);
        assertEquals(s.topRatio, back.topRatio, 1e-6);
        assertEquals(s.height, back.height, 1e-6);
    }

    @Test
    public void fromPlaneHandlesTheCourseRows()
    {
        // Row 1 of Ex0.csv (triangle in the plane y = 200) and row 4 read as rails.
        Plane row1 = Plane.triangle(new Vec3(200, 200, 0), new Vec3(0, 200, 400), new Vec3(400, 200, 400));
        PlaneSpec s1 = PlaneSpec.fromPlane(row1);
        assertEquals(0.0, s1.topRatio, 1e-9);
        assertEquals(400.0, s1.width, 1e-6);
        assertEquals(400.0, s1.height, 1e-6);
        assertEquals(200.0, s1.cy, 1e-6);
        Plane again = s1.toPlane();
        assertEquals(row1.a1().x, again.a1().x, 1e-6);
        assertEquals(row1.c().z, again.c().z, 1e-6);
        Plane row4 = Plane.trapezoid(new Vec3(200, 50, 0), new Vec3(200, 350, 0), new Vec3(200, 0, 500), new Vec3(200, 400, 500));
        PlaneSpec s4 = PlaneSpec.fromPlane(row4);
        assertEquals(0.75, s4.topRatio, 1e-9);
        assertEquals(400.0, s4.width, 1e-6);
        assertEquals(500.0, s4.height, 1e-6);
    }

    @Test
    public void interpolationStaysValidAndEndsAtTheKeyframes()
    {
        PlaneSpec a = new PlaneSpec(10, 20, 30, 170, -40, 10, 100, 0.0, 80);
        PlaneSpec b = new PlaneSpec(90, 60, 30, -170, 40, -10, 60, 1.0, 120);
        for (int k = 0; k <= 10; k++)
        {
            PlaneSpec m = PlaneSpec.lerp(a, b, k / 10.0);
            assertTrue(m.toPlane().validate(EPS).isEmpty());
        }
        PlaneSpec half = PlaneSpec.lerp(a, b, 0.5);
        // 170 -> -170 goes the short way through 180, not through 0.
        assertEquals(180.0, Math.abs(half.yaw), 1e-9);
        assertEquals(0.5, half.topRatio, 1e-9);
        assertEquals(a.cx, PlaneSpec.lerp(a, b, 0).cx, 0.0);
        assertEquals(b.cx, PlaneSpec.lerp(a, b, 1).cx, 0.0);
    }

    @Test
    public void slideMovesAlongTheNormalOnly()
    {
        PlaneSpec s = PlaneSpec.xy(Volume.create(VoxelType.UINT8, 10, 10, 10));
        PlaneSpec moved = s.slide(3.0);
        assertEquals(s.cx, moved.cx, 1e-9);
        assertEquals(s.cy, moved.cy, 1e-9);
        assertEquals(s.cz + 3.0, moved.cz, 1e-9);
        assertFalse(moved.toPlane().isTriangle());
    }
}
