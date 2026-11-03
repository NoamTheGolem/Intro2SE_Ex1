package ex1.app;

import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.util.Locale;

/**
 * Where the volume sits in physical space, for the viewer only: the projection itself works in
 * voxel indices, as the assignment defines it.
 *
 * World coordinates are millimetres in an LPS frame centred on the volume: +X = patient's left,
 * +Y = posterior, +Z = superior (up). The NRRD header gives one physical step vector per index
 * axis ("space directions"); for the course's data, index x runs posterior, y inferior and z to
 * the right, with 1.3 mm between MRHead's z slices. With that the 3-D model stands upright with
 * the right proportions, and the three slice views come out as the lecturer's images show them:
 * axial from above (A up, R left), coronal from the front (S up, R left), sagittal from the side
 * (S up, A left). A volume without geometry gets the identity (1 mm voxels, x = L, y = P, z = S).
 */
final class VolumeGeometry
{
    /** Anatomical axes = world axes. */
    static final int LEFT = 0;
    static final int POSTERIOR = 1;
    static final int SUPERIOR = 2;

    /** The three standard views. */
    static final int AXIAL = 0;
    static final int CORONAL = 1;
    static final int SAGITTAL = 2;

    private static final String[] VIEW_NAMES = {"Axial", "Coronal", "Sagittal"};
    private static final String[] VIEW_HINTS = {"from above", "from the front", "from the side"};

    /**
     * Per view: constant anatomical axis; horizontal axis and the direction that is "screen
     * right"; vertical axis and the direction that is "screen down".
     */
    private static final int[][] VIEWS = {
        {SUPERIOR, LEFT, 1, POSTERIOR, 1},
        {POSTERIOR, LEFT, 1, SUPERIOR, -1},
        {LEFT, POSTERIOR, 1, SUPERIOR, -1},
    };

    /** Top, bottom, left, right labels per view. */
    private static final String[][] LABELS = {
        {"A", "P", "R", "L"},
        {"S", "I", "R", "L"},
        {"S", "I", "A", "P"},
    };

    private final int[] size;
    private final double[][] m;
    private final double[][] inv;
    private final Vec3 centre;
    private final boolean known;
    private final int[] indexAxisOf = new int[3];
    private final int[] signOf = new int[3];
    private final double[] spacing = new double[3];

    /**
     * @param (int[]) size volume size per index axis
     * @param (double[][]) m index-to-world matrix, m[row][indexAxis]
     * @param (boolean) known true when the anatomical orientation comes from the file
     */
    private VolumeGeometry(int[] size, double[][] m, boolean known)
    {
        this.size = size;
        this.m = m;
        this.inv = invert(m);
        this.centre = new Vec3((size[0] - 1) / 2.0, (size[1] - 1) / 2.0, (size[2] - 1) / 2.0);
        boolean permutation = true;
        boolean[] used = new boolean[3];
        for (int c = 0; c < 3; c++)
        {
            int best = 0;
            for (int r = 1; r < 3; r++)
            {
                if (Math.abs(m[r][c]) > Math.abs(m[best][c]))
                {
                    best = r;
                }
            }
            if (used[best])
            {
                permutation = false;
            }
            used[best] = true;
            indexAxisOf[best] = c;
            signOf[best] = 1;
            if (m[best][c] < 0)
            {
                signOf[best] = -1;
            }
            spacing[c] = Math.sqrt(m[0][c] * m[0][c] + m[1][c] * m[1][c] + m[2][c] * m[2][c]);
        }
        if (!permutation)
        {
            for (int a = 0; a < 3; a++)
            {
                indexAxisOf[a] = a;
                signOf[a] = 1;
            }
        }
        this.known = known && permutation;
    }

    /**
     * @param (Volume) v volume (its header geometry, when any)
     * @return the geometry
     */
    static VolumeGeometry of(Volume v)
    {
        int[] size = {v.sizeX(), v.sizeY(), v.sizeZ()};
        double[][] dirs = v.spaceDirections();
        if (dirs == null || !invertible(transpose(dirs)))
        {
            return new VolumeGeometry(size, identity(), false);
        }
        String space = v.space();
        double sx = 1;
        double sy = 1;
        boolean known = false;
        if (space != null)
        {
            String s = space.trim().toLowerCase(Locale.ROOT);
            if (s.equals("left-posterior-superior") || s.equals("lps"))
            {
                known = true;
            }
            else if (s.equals("right-anterior-superior") || s.equals("ras"))
            {
                sx = -1;
                sy = -1;
                known = true;
            }
            else if (s.equals("left-anterior-superior") || s.equals("las"))
            {
                sy = -1;
                known = true;
            }
        }
        double[][] m = new double[3][3];
        for (int c = 0; c < 3; c++)
        {
            m[0][c] = sx * dirs[c][0];
            m[1][c] = sy * dirs[c][1];
            m[2][c] = dirs[c][2];
        }
        return new VolumeGeometry(size, m, known);
    }

    // ------------------------------------------------------------------ transforms

    /**
     * @param (Vec3) index point in voxel indices
     * @return the same point in world millimetres (volume centre = origin)
     */
    Vec3 toWorld(Vec3 index)
    {
        return mul(m, index.sub(centre));
    }

    /**
     * @param (Vec3) world point in world millimetres
     * @return the same point in voxel indices
     */
    Vec3 toIndex(Vec3 world)
    {
        return mul(inv, world).add(centre);
    }

    /**
     * @param (Vec3) worldDirection direction in world millimetres
     * @return the direction in voxel indices (not normalised)
     */
    Vec3 directionToIndex(Vec3 worldDirection)
    {
        return mul(inv, worldDirection);
    }

    /**
     * Gradients (and surface normals) transform with the inverse transpose.
     *
     * @param (Vec3) indexGradient gradient with respect to voxel indices
     * @return the gradient in world coordinates (not normalised)
     */
    Vec3 gradientToWorld(Vec3 indexGradient)
    {
        double gx = inv[0][0] * indexGradient.x + inv[1][0] * indexGradient.y + inv[2][0] * indexGradient.z;
        double gy = inv[0][1] * indexGradient.x + inv[1][1] * indexGradient.y + inv[2][1] * indexGradient.z;
        double gz = inv[0][2] * indexGradient.x + inv[1][2] * indexGradient.y + inv[2][2] * indexGradient.z;
        return new Vec3(gx, gy, gz);
    }

    /**
     * @return length of the volume's diagonal in world millimetres
     */
    double worldDiagonal()
    {
        Vec3 a = toWorld(new Vec3(0, 0, 0));
        Vec3 b = toWorld(new Vec3(size[0] - 1, size[1] - 1, size[2] - 1));
        Vec3 c = toWorld(new Vec3(size[0] - 1, 0, 0));
        Vec3 d = toWorld(new Vec3(0, size[1] - 1, size[2] - 1));
        return Math.max(a.dist(b), c.dist(d));
    }

    // ------------------------------------------------------------------ anatomy

    /**
     * @return true when the orientation (and the A/P/R/L/S/I labels) come from the file
     */
    boolean known()
    {
        return known;
    }

    /**
     * @param (int) indexAxis 0, 1, 2
     * @return physical spacing along that index axis (1 when unknown)
     */
    double spacing(int indexAxis)
    {
        return spacing[indexAxis];
    }

    /**
     * @param (int) view AXIAL, CORONAL or SAGITTAL
     * @return the index axis held constant in that view
     */
    int constantAxis(int view)
    {
        return indexAxisOf[VIEWS[view][0]];
    }

    /**
     * @param (int) view view
     * @return the index axis along the screen's x
     */
    int horizontalAxis(int view)
    {
        return indexAxisOf[VIEWS[view][1]];
    }

    /**
     * @param (int) view view
     * @return the index axis along the screen's y
     */
    int verticalAxis(int view)
    {
        return indexAxisOf[VIEWS[view][3]];
    }

    /**
     * @param (int) view view
     * @return +1 when the horizontal index grows to the right, -1 when it grows to the left
     */
    int horizontalStep(int view)
    {
        return VIEWS[view][2] * signOf[VIEWS[view][1]];
    }

    /**
     * @param (int) view view
     * @return +1 when the vertical index grows downwards, -1 when it grows upwards
     */
    int verticalStep(int view)
    {
        return VIEWS[view][4] * signOf[VIEWS[view][3]];
    }

    /**
     * @param (int) indexAxis 0, 1, 2
     * @return number of voxels along that axis
     */
    int size(int indexAxis)
    {
        return size[indexAxis];
    }

    /**
     * The view's slice at index k of the constant axis, as a plane of the assignment whose image
     * is oriented like the view: a1 = top-left, a2 = top-right, b = bottom-left, c = bottom-right.
     *
     * @param (int) view view
     * @param (double) k index along the constant axis
     * @return the rectangle
     */
    Plane slicePlane(int view, double k)
    {
        int h = horizontalAxis(view);
        int v = verticalAxis(view);
        int c = constantAxis(view);
        double left = 0;
        double right = size[h] - 1;
        if (horizontalStep(view) < 0)
        {
            left = size[h] - 1;
            right = 0;
        }
        double top = 0;
        double bottom = size[v] - 1;
        if (verticalStep(view) < 0)
        {
            top = size[v] - 1;
            bottom = 0;
        }
        Vec3 tl = point(h, left, v, top, c, k);
        Vec3 tr = point(h, right, v, top, c, k);
        Vec3 bl = point(h, left, v, bottom, c, k);
        Vec3 br = point(h, right, v, bottom, c, k);
        return Plane.trapezoid(tl, tr, bl, br);
    }

    /**
     * @param (int) view view
     * @return the view's name, e.g. "Axial"
     */
    static String name(int view)
    {
        return VIEW_NAMES[view];
    }

    /**
     * @param (int) view view
     * @return how the view looks, e.g. "from above"
     */
    static String hint(int view)
    {
        return VIEW_HINTS[view];
    }

    /**
     * @param (int) view view
     * @return top, bottom, left, right labels, or null when the orientation is not known
     */
    String[] labels(int view)
    {
        if (!known)
        {
            return null;
        }
        return LABELS[view];
    }

    // ------------------------------------------------------------------ helpers

    /**
     * @param (int) a1 first axis
     * @param (double) v1 its value
     * @param (int) a2 second axis
     * @param (double) v2 its value
     * @param (int) a3 third axis
     * @param (double) v3 its value
     * @return the point with those coordinates
     */
    private static Vec3 point(int a1, double v1, int a2, double v2, int a3, double v3)
    {
        double[] p = new double[3];
        p[a1] = v1;
        p[a2] = v2;
        p[a3] = v3;
        return new Vec3(p[0], p[1], p[2]);
    }

    /**
     * @param (double[][]) a matrix
     * @param (Vec3) v vector
     * @return a * v
     */
    private static Vec3 mul(double[][] a, Vec3 v)
    {
        return new Vec3(a[0][0] * v.x + a[0][1] * v.y + a[0][2] * v.z,
                a[1][0] * v.x + a[1][1] * v.y + a[1][2] * v.z,
                a[2][0] * v.x + a[2][1] * v.y + a[2][2] * v.z);
    }

    /**
     * @return the 3x3 identity
     */
    private static double[][] identity()
    {
        return new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    }

    /**
     * @param (double[][]) a matrix
     * @return its transpose
     */
    private static double[][] transpose(double[][] a)
    {
        double[][] t = new double[3][3];
        for (int r = 0; r < 3; r++)
        {
            for (int c = 0; c < 3; c++)
            {
                t[r][c] = a[c][r];
            }
        }
        return t;
    }

    /**
     * @param (double[][]) a matrix
     * @return its determinant
     */
    private static double det(double[][] a)
    {
        return a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1])
                - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
                + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]);
    }

    /**
     * @param (double[][]) a matrix
     * @return true when the matrix can be inverted
     */
    private static boolean invertible(double[][] a)
    {
        double d = det(a);
        return Double.isFinite(d) && Math.abs(d) > 1e-12;
    }

    /**
     * @param (double[][]) a invertible matrix
     * @return its inverse
     */
    private static double[][] invert(double[][] a)
    {
        double d = det(a);
        double[][] r = new double[3][3];
        r[0][0] = (a[1][1] * a[2][2] - a[1][2] * a[2][1]) / d;
        r[0][1] = (a[0][2] * a[2][1] - a[0][1] * a[2][2]) / d;
        r[0][2] = (a[0][1] * a[1][2] - a[0][2] * a[1][1]) / d;
        r[1][0] = (a[1][2] * a[2][0] - a[1][0] * a[2][2]) / d;
        r[1][1] = (a[0][0] * a[2][2] - a[0][2] * a[2][0]) / d;
        r[1][2] = (a[0][2] * a[1][0] - a[0][0] * a[1][2]) / d;
        r[2][0] = (a[1][0] * a[2][1] - a[1][1] * a[2][0]) / d;
        r[2][1] = (a[0][1] * a[2][0] - a[0][0] * a[2][1]) / d;
        r[2][2] = (a[0][0] * a[1][1] - a[0][1] * a[1][0]) / d;
        return r;
    }
}
