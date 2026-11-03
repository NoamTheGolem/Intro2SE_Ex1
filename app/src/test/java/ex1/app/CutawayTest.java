package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.core.VoxelType;

import org.junit.Test;

/**
 * The 3-D model of the app (ADR-0009): the geometry of the course's files, the solid built from a
 * volume, and the cut-open renderer, on volumes whose answer is known.
 */
public class CutawayTest
{
    /**
     * A hollow ball: a bright shell (radius 14..18) with a dark inside, in a 48^3 volume.
     *
     * @return the volume
     */
    private static Volume hollowBall()
    {
        int n = 48;
        Volume v = Volume.create(VoxelType.UINT8, n, n, n);
        double c = (n - 1) / 2.0;
        for (int z = 0; z < n; z++)
        {
            for (int y = 0; y < n; y++)
            {
                for (int x = 0; x < n; x++)
                {
                    double r = Math.sqrt((x - c) * (x - c) + (y - c) * (y - c) + (z - c) * (z - c));
                    if (r >= 14 && r <= 18)
                    {
                        v.setValue(v.index(x, y, z), 200);
                    }
                    else if (r < 14)
                    {
                        v.setValue(v.index(x, y, z), 30);
                    }
                }
            }
        }
        return v;
    }

    @Test
    public void courseGeometryGivesTheLecturersViews()
    {
        // MRHead / the food scans: index x = posterior, y = inferior, z = right (1.3 mm).
        Volume v = Volume.create(VoxelType.UINT8, 256, 256, 130);
        v.setSpace("left-posterior-superior", new double[][] {{0, 1, 0}, {0, 0, -1}, {-1.3, 0, 0}});
        VolumeGeometry g = VolumeGeometry.of(v);
        assertTrue(g.known());
        assertEquals(1, g.constantAxis(VolumeGeometry.AXIAL));
        assertEquals(0, g.constantAxis(VolumeGeometry.CORONAL));
        assertEquals(2, g.constantAxis(VolumeGeometry.SAGITTAL));
        // Sagittal from the side: x (A -> P) to the right, y (S -> I) downwards, as head.csv's slices.
        assertEquals(0, g.horizontalAxis(VolumeGeometry.SAGITTAL));
        assertEquals(1, g.horizontalStep(VolumeGeometry.SAGITTAL));
        assertEquals(1, g.verticalStep(VolumeGeometry.SAGITTAL));
        // Axial from above: R on the left = high z on the left.
        assertEquals(2, g.horizontalAxis(VolumeGeometry.AXIAL));
        assertEquals(-1, g.horizontalStep(VolumeGeometry.AXIAL));
        assertEquals(1.3, g.spacing(2), 1e-12);
        Plane axial = g.slicePlane(VolumeGeometry.AXIAL, 100);
        assertEquals(129.0, axial.a1().z, 0.0);
        assertEquals(100.0, axial.a1().y, 0.0);
        assertEquals(255.0, axial.b().x, 0.0);
        // World: superior is up (+Z): index y grows downwards.
        Vec3 up = g.toWorld(new Vec3(0, 0, 0)).sub(g.toWorld(new Vec3(0, 10, 0)));
        assertEquals(10.0, up.z, 1e-9);
        Vec3 back = g.toIndex(g.toWorld(new Vec3(12.5, 7, 99)));
        assertEquals(12.5, back.x, 1e-9);
        assertEquals(99.0, back.z, 1e-9);
    }

    @Test
    public void unknownGeometryIsTheIdentity()
    {
        VolumeGeometry g = VolumeGeometry.of(Volume.create(VoxelType.UINT8, 10, 20, 30));
        assertFalse(g.known());
        assertNull(g.labels(VolumeGeometry.AXIAL));
        assertEquals(2, g.constantAxis(VolumeGeometry.AXIAL));
        assertEquals(1.0, g.spacing(1), 0.0);
    }

    @Test
    public void solidFillsTheDarkInsideOfAClosedShell()
    {
        Volume v = hollowBall();
        IntensityWindow w = new IntensityWindow(0, 255);
        SolidModel m = SolidModel.build(v, w);
        assertEquals(1, m.factor);
        // The dark centre is inside the object (the air cannot reach it), the corner is outside.
        assertTrue(m.isSolid(24, 24, 24));
        assertTrue(m.isSolid(24, 24, 24 + 16));
        assertFalse(m.isSolid(1, 1, 1));
        assertFalse(m.isSolid(24, 24, 45));
        assertTrue(m.threshold() > 30 && m.threshold() < 200);
    }

    @Test
    public void rayHitsTheSurfaceAndTheCutFaceWhereExpected()
    {
        Volume v = hollowBall();
        IntensityWindow w = new IntensityWindow(0, 255);
        SolidModel m = SolidModel.build(v, w);
        VolumeGeometry g = VolumeGeometry.of(v);
        Camera3D cam = new Camera3D();
        cam.setAngles(0, 0);
        cam.fit(80, 100, 100);
        CutawayRenderer r = new CutawayRenderer(1);
        // Looking along +y (world = index here) through the centre: the surface is at y = c - 18.
        Vec3 hit = r.pick(50, 50, 100, 100, cam, g, m, null, false);
        assertNotNull(hit);
        assertEquals(23.5 - 18, hit.y, 1.0);
        // Cut open by the plane y = 23.5 (normal along the view): the ray stops on the cut face,
        // inside the ball, where the dark inside is shown.
        Plane cut = Plane.trapezoid(new Vec3(0, 23.5, 0), new Vec3(47, 23.5, 0), new Vec3(0, 23.5, 47), new Vec3(47, 23.5, 47));
        Vec3 face = r.pick(50, 50, 100, 100, cam, g, m, cut, true);
        assertNotNull(face);
        assertEquals(23.5, face.y, 1e-6);
        // Off the object the ray finds nothing.
        assertNull(r.pick(2, 2, 100, 100, cam, g, m, cut, true));
        Raster raster = new Raster(100, 100);
        raster.clear(0xFF000000);
        r.render(raster, cam, g, m, v, w, cut, true, 1);
        int centre = raster.pixels()[50 * 100 + 50];
        // The cut face shows the inside's grey (30), not the shell (200) nor the background.
        assertEquals(30, centre & 0xFF, 2);
        r.shutdown();
    }

    @Test
    public void clickedTriangleIsMadeIsoscelesByMovingCOnly()
    {
        Plane clicked = Plane.triangle(new Vec3(10, 10, 5), new Vec3(0, 40, 5), new Vec3(30, 35, 5));
        Plane fixed = PlaneSpec.makeIsosceles(clicked);
        assertTrue(fixed.validate(Plane.DEFAULT_EPS).isEmpty());
        assertEquals(clicked.a1().x, fixed.a1().x, 0.0);
        assertEquals(clicked.b().y, fixed.b().y, 0.0);
        Vec3 dirBefore = clicked.c().sub(clicked.a1()).normalized();
        Vec3 dirAfter = fixed.c().sub(fixed.a1()).normalized();
        assertEquals(1.0, dirBefore.dot(dirAfter), 1e-12);
        Plane trapezoid = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(12, 1, 0), new Vec3(-3, 20, 0), new Vec3(16, 21, 0));
        assertTrue(PlaneSpec.makeIsosceles(trapezoid).validate(Plane.DEFAULT_EPS).isEmpty());
    }
}
