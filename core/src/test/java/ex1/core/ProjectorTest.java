package ex1.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Correctness of the projection: exact slices, out-of-volume handling, triangle shape,
 * and the trilinear interpolation.
 */
public class ProjectorTest
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Tolerance of float comparisons. */
    private static final float DELTA = 1e-3f;

    /**
     * Makes a volume whose every voxel says where it is.
     * @param (int) n Edge length.
     * @return (Volume) Volume with a unique value per voxel: v = x + 100y + 10000z.
     */
    private static Volume codedVolume(int n)
    {
        Volume v = Volume.create(VoxelType.INT32, n, n, n);
        for (int z = 0; z < n; z++)
        {
            for (int y = 0; y < n; y++)
            {
                for (int x = 0; x < n; x++)
                {
                    v.setValue(v.index(x, y, z), x + 100 * y + 10000 * z);
                }
            }
        }
        return v;
    }

    /** Checks that a plane on a slice gives that slice exactly, voxel for pixel (TC-50). */
    @Test
    public void axisAlignedRectangleReproducesSliceExactly()
    {
        int    n = 20;
        Volume v = codedVolume(n);
        int    z = 7;
        Plane p = Plane.trapezoid(new Vec3(0, 0, z), new Vec3(n - 1, 0, z),
                new Vec3(0, n - 1, z), new Vec3(n - 1, n - 1, z));
        Image2D img = Projector.create(Projector.Interpolation.NEAREST).project(v, p);
        assertEquals(n, img.width());
        assertEquals(n, img.height());
        for (int y = 0; y < n; y++)
        {
            for (int x = 0; x < n; x++)
            {
                assertEquals(v.get(x, y, z), img.get(x, y), DELTA);
            }
        }
    }

    /** Checks that both sampling modes agree on voxel centres (TC-51). */
    @Test
    public void trilinearEqualsNearestOnGridPoints()
    {
        int    n = 12;
        Volume v = codedVolume(n);
        Plane p = Plane.trapezoid(new Vec3(0, 3, 0), new Vec3(n - 1, 3, 0),
                new Vec3(0, 3, n - 1), new Vec3(n - 1, 3, n - 1));
        Image2D a = Projector.create(Projector.Interpolation.NEAREST).project(v, p);
        Image2D b = Projector.create(Projector.Interpolation.TRILINEAR).project(v, p);
        for (int i = 0; i < a.pixels().length; i++)
        {
            assertEquals(a.pixels()[i], b.pixels()[i], DELTA);
        }
    }

    /** Checks that trilinear sampling reproduces a linear volume exactly, at any point (TC-52). */
    @Test
    public void trilinearIsExactForLinearData()
    {
        // A linear function is reproduced exactly by trilinear interpolation at any point.
        int    n = 16;
        Volume v = Volume.create(VoxelType.FLOAT32, n, n, n);
        for (int z = 0; z < n; z++)
        {
            for (int y = 0; y < n; y++)
            {
                for (int x = 0; x < n; x++)
                {
                    v.setValue(v.index(x, y, z), 2 * x + 3 * y + 5 * z);
                }
            }
        }
        // Oblique isosceles triangle with fractional coordinates, fully inside the volume.
        Vec3    a    = new Vec3(1.3, 2.7, 0.4);
        Vec3    axis = new Vec3(10.8, 3.5, 9.5).normalized();
        Vec3    perp = axis.cross(new Vec3(0, 0, 1)).normalized();
        Vec3    mid  = a.add(axis.scale(10));
        Vec3    b    = mid.sub(perp.scale(3));
        Vec3    c    = mid.add(perp.scale(3));
        Plane   p    = Plane.triangle(a, b, c);
        Image2D img  = Projector.create(Projector.Interpolation.TRILINEAR).project(v, p);

        // Apex row: the apex itself, centred.
        assertEquals(f(a), img.get(img.width() / 2, 0), 1e-3);

        // Last row: recompute every sample point with the formula of ALGORITHM.md §3.
        int    y     = img.height() - 1;
        Vec3   left  = a.add(b.sub(a).normalized().scale(y));
        Vec3   right = a.add(c.sub(a).normalized().scale(y));
        double d     = right.dist(left);
        int    count = (int) Math.floor(d) + 1;
        int    x0    = (img.width() - count) / 2;
        Vec3   dp    = right.sub(left).normalized();
        for (int i = 0; i < count; i++)
        {
            Vec3 q = left.add(dp.scale(i));
            assertEquals("sample " + i, f(q), img.get(x0 + i, y), 1e-3);
        }
    }

    /**
     * Evaluates the linear function the trilinear test volume is made of.
     * @param (Vec3) q Point.
     * @return (double) The linear test function 2x + 3y + 5z.
     */
    private static double f(Vec3 q)
    {
        return 2 * q.x + 3 * q.y + 5 * q.z;
    }

    /** Checks that points outside the volume read as 0 ("if p is NOT in M, v = 0") (TC-54). */
    @Test
    public void pointsOutsideVolumeAreZero()
    {
        Volume v = Volume.create(VoxelType.UINT8, 10, 10, 10);
        for (int i = 0; i < v.voxelCount(); i++)
        {
            v.setValue(i, 9);
        }
        // Rectangle from x = -5 to x = 14: only x in [0, 9] is inside.
        Plane p = Plane.trapezoid(new Vec3(-5, 0, 5), new Vec3(14, 0, 5), new Vec3(-5, 9, 5), new Vec3(14, 9, 5));
        Image2D img = Projector.create(Projector.Interpolation.NEAREST).project(v, p);
        assertEquals(0f, img.get(0, 4), DELTA);
        assertEquals(0f, img.get(4, 4), DELTA);
        assertEquals(9f, img.get(5, 4), DELTA);
        assertEquals(9f, img.get(14, 4), DELTA);
        assertEquals(0f, img.get(15, 4), DELTA);
    }

    /** Checks that a plane entirely outside the volume gives a black image (TC-54). */
    @Test
    public void planeCompletelyOutsideGivesBlackImage()
    {
        Volume  v   = codedVolume(5);
        Plane   p   = Plane.triangle(new Vec3(100, 100, 100), new Vec3(90, 110, 100), new Vec3(110, 110, 100));
        Image2D img = Projector.create(Projector.Interpolation.TRILINEAR).project(v, p);
        for (int i = 0; i < img.pixels().length; i++)
        {
            assertEquals(0f, img.pixels()[i], 0f);
        }
    }

    /**
     * Checks the triangle's shape in the image: the apex alone in row 0, every row centred,
     * widening evenly to the base (TC-53).
     */
    @Test
    public void triangleShapeIsCentredAndWidensLinearly()
    {
        Volume v = Volume.create(VoxelType.UINT8, 30, 30, 30);
        for (int i = 0; i < v.voxelCount(); i++)
        {
            v.setValue(i, 1);
        }
        // Apex (10,0,5); base from (0,20,5) to (20,20,5): |ab| = |ac| = sqrt(500).
        Plane   p   = Plane.triangle(new Vec3(10, 0, 5), new Vec3(0, 20, 5), new Vec3(20, 20, 5));
        Image2D img = Projector.create(Projector.Interpolation.NEAREST).project(v, p);
        int     w   = img.width();
        assertEquals(21, w);
        // Row 0: only the apex, in the centre column.
        assertEquals(1f, img.get(w / 2, 0), DELTA);
        assertEquals(0f, img.get(w / 2 - 1, 0), DELTA);
        assertEquals(0f, img.get(w / 2 + 1, 0), DELTA);
        // Last row: covered almost fully.
        int last   = img.height() - 1;
        int filled = 0;
        for (int x = 0; x < w; x++)
        {
            filled += (int) img.get(x, last);
        }
        assertEquals(true, filled >= w - 2);
        // Each row is symmetric around the centre.
        for (int y = 0; y < img.height(); y++)
        {
            int count = 0;
            int first = -1;
            for (int x = 0; x < w; x++)
            {
                if (img.get(x, y) > 0)
                {
                    count++;
                    if (first < 0)
                    {
                        first = x;
                    }
                }
            }
            assertEquals("row " + y + " centred", (w - count) / 2, first);
        }
    }

    /** Checks that an image used again shows nothing of the frame before (TC-55). */
    @Test
    public void reusedImageIsClearedBetweenFrames()
    {
        Volume    v    = codedVolume(10);
        Projector proj = Projector.create(Projector.Interpolation.NEAREST);
        Image2D   img  = new Image2D(30, 30);
        proj.project(v, Plane.trapezoid(new Vec3(0, 0, 1), new Vec3(9, 0, 1), new Vec3(0, 9, 1), new Vec3(9, 9, 1)), img);
        proj.project(v, Plane.triangle(new Vec3(50, 50, 50), new Vec3(45, 55, 50), new Vec3(55, 55, 50)), img);
        assertEquals(0f, img.get(5, 5), 0f);
    }

    /** Checks that an image smaller than the plane needs is refused (TC-55). */
    @Test(expected = IllegalArgumentException.class)
    public void tooSmallTargetImageIsRejected()
    {
        Volume v = codedVolume(10);
        Plane  p = Plane.trapezoid(new Vec3(0, 0, 1), new Vec3(9, 0, 1), new Vec3(0, 9, 1), new Vec3(9, 9, 1));
        Projector.create(Projector.Interpolation.NEAREST).project(v, p, new Image2D(5, 5));
    }

    /**
     * Checks that unsigned types read back as unsigned values, also above the signed range (TC-55).
     */
    @Test
    public void unsignedTypesAreReadAsUnsigned()
    {
        Volume b = Volume.create(VoxelType.UINT8, 1, 1, 1);
        b.setValue(0, 250);
        assertEquals(250f, b.valueAt(0), 0f);
        Volume s = Volume.create(VoxelType.UINT16, 1, 1, 1);
        s.setValue(0, 65000);
        assertEquals(65000f, s.valueAt(0), 0f);
        Volume i = Volume.create(VoxelType.UINT32, 1, 1, 1);
        i.setValue(0, 4_000_000_000.0);
        assertEquals(4.0e9f, i.valueAt(0), 1e3f);
        Volume sb = Volume.create(VoxelType.INT8, 1, 1, 1);
        sb.setValue(0, -3);
        assertEquals(-3f, sb.valueAt(0), 0f);
    }

    /** Checks the assignment's size limit: 1024 voxels along an axis is refused (TC-55). */
    @Test(expected = IllegalArgumentException.class)
    public void volumeSizeLimitIsEnforced()
    {
        Volume.create(VoxelType.UINT8, 1024, 1, 1);
    }
}
