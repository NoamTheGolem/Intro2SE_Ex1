package ex1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A plane section given as an isosceles trapezoid (a1, a2, b, c): the left rail runs a1 -> b,
 * the right rail runs a2 -> c. An isosceles triangle (a, b, c) is the special case a1 = a2 = a,
 * so the rest of the program only ever handles trapezoids.
 *
 * Two levels of "wrong" (ALGORITHM.md §6, ADR-0003), as the course's edge-case file expects:
 * <ul>
 *   <li>{@link #validate} lists every broken rule. A plane that only breaks the shape rules
 *       (sides of different length, top not parallel to the base, top wider than the base) is
 *       <em>doubtful</em>: the algorithm is still well defined, so it is drawn as given, with a
 *       warning.</li>
 *   <li>{@link #isRenderable} is false for a <em>bad</em> plane: non-finite numbers, a rail of
 *       length 0, collinear points, four points that are not coplanar, sides that cross, or an
 *       image too large to allocate. Such a plane becomes a black frame, with a warning
 *       ({@link #whyNotRenderable} says why).</li>
 * </ul>
 */
public final class Plane
{
    public static final double DEFAULT_EPS = 0.001;

    /** Larger sides cannot come from a volume below 1024^3 and would exhaust memory. */
    public static final double MAX_IMAGE_SIDE = 8192;

    private static final int TRIANGLE_VALUES = 9;
    private static final int TRAPEZOID_VALUES = 12;

    private final Vec3 a1;
    private final Vec3 a2;
    private final Vec3 b;
    private final Vec3 c;
    private final boolean triangle;

    /**
     * @param (Vec3) a1 start of the left rail
     * @param (Vec3) a2 start of the right rail
     * @param (Vec3) b end of the left rail
     * @param (Vec3) c end of the right rail
     * @param (boolean) triangle true when built from three points
     */
    private Plane(Vec3 a1, Vec3 a2, Vec3 b, Vec3 c, boolean triangle)
    {
        this.a1 = a1;
        this.a2 = a2;
        this.b = b;
        this.c = c;
        this.triangle = triangle;
    }

    /**
     * @param (Vec3) a apex
     * @param (Vec3) b end of the left side
     * @param (Vec3) c end of the right side
     * @return triangle plane (a, b, c)
     */
    public static Plane triangle(Vec3 a, Vec3 b, Vec3 c)
    {
        return new Plane(a, a, b, c, true);
    }

    /**
     * @param (Vec3) a1 start of the left rail
     * @param (Vec3) a2 start of the right rail
     * @param (Vec3) b end of the left rail
     * @param (Vec3) c end of the right rail
     * @return trapezoid plane (a1, a2, b, c)
     */
    public static Plane trapezoid(Vec3 a1, Vec3 a2, Vec3 b, Vec3 c)
    {
        return new Plane(a1, a2, b, c, false);
    }

    /**
     * Builds a plane from 9 (triangle) or 12 (trapezoid) coordinates, as in one CSV row.
     *
     * @param (double[]) v coordinates x,y,z of each point in order
     * @return the plane
     */
    public static Plane fromValues(double[] v)
    {
        if (v.length == TRIANGLE_VALUES)
        {
            return triangle(point(v, 0), point(v, 1), point(v, 2));
        }
        if (v.length == TRAPEZOID_VALUES)
        {
            return trapezoid(point(v, 0), point(v, 1), point(v, 2), point(v, 3));
        }
        throw new IllegalArgumentException("expected 9 or 12 numbers, got " + v.length);
    }

    /**
     * Checks the conditions of ALGORITHM.md §6.
     *
     * @param (double) eps geometric tolerance
     * @return list of problems; empty when the plane is valid
     */
    public List<String> validate(double eps)
    {
        List<String> errors = new ArrayList<String>();
        double left = a1.dist(b);
        double right = a2.dist(c);
        if (!isFinite())
        {
            errors.add("coordinates must be finite numbers");
            return errors;
        }
        if (Math.abs(left - right) >= eps)
        {
            errors.add(String.format("side lengths differ: |%s| = %.4f, |%s| = %.4f (must differ by < %s)",
                    leftName(), left, rightName(), right, eps));
        }
        if (left < 1.0)
        {
            errors.add(String.format("plane too small: |%s| = %.4f (must be >= 1)", leftName(), left));
        }
        if (triangle)
        {
            Vec3 normal = b.sub(a1).cross(c.sub(a1));
            if (normal.length() < eps * Math.max(1.0, left * left))
            {
                errors.add("points a, b, c are collinear");
            }
        }
        else
        {
            if (a1.dist(a2) > b.dist(c) + eps)
            {
                errors.add(String.format("|a1a2| = %.4f must be <= |bc| = %.4f", a1.dist(a2), b.dist(c)));
            }
            if (distanceOfCFromPlane() >= eps * Math.max(1.0, left))
            {
                errors.add(String.format("points are not coplanar (c is %.4f from plane a1,a2,b)",
                        distanceOfCFromPlane()));
            }
            else if (railsCross())
            {
                errors.add("rails a1->b and a2->c cross each other (the four points do not form a trapezoid)");
            }
            else if (topAndBaseCross())
            {
                errors.add("top a1a2 and base bc cross each other (the four points do not form a trapezoid)");
            }
            else if (topOffParallel() >= eps)
            {
                errors.add(String.format("top a1a2 is not parallel to base bc (a2 is %.4f off the parallel through a1)",
                        topOffParallel()));
            }
        }
        return errors;
    }

    /**
     * Why the algorithm cannot draw this plane at all (a "bad" plane, black frame).
     *
     * @return the reason, or null when the plane is renderable
     */
    public String whyNotRenderable()
    {
        if (!isFinite())
        {
            return "coordinates must be finite numbers";
        }
        double left = a1.dist(b);
        double right = a2.dist(c);
        if (left <= 0.0 || right <= 0.0)
        {
            return "a side has length 0";
        }
        if (triangle)
        {
            Vec3 normal = b.sub(a1).cross(c.sub(a1));
            if (normal.length() < DEFAULT_EPS * Math.max(1.0, left * left))
            {
                return "points a, b, c are collinear (no plane)";
            }
        }
        else
        {
            if (distanceOfCFromPlane() >= DEFAULT_EPS * Math.max(1.0, left))
            {
                return String.format("the four points are not on one plane (c is %.4f from plane a1,a2,b)",
                        distanceOfCFromPlane());
            }
            if (railsCross() || topAndBaseCross())
            {
                return "the sides cross each other";
            }
        }
        double sides = Math.max(left, Math.max(a1.dist(a2), b.dist(c)));
        if (sides >= MAX_IMAGE_SIDE)
        {
            return String.format("the image would be %d pixels wide or high (limit %d)",
                    (long) Math.floor(sides) + 1, (long) MAX_IMAGE_SIDE);
        }
        return null;
    }

    /**
     * Output image height: one row per unit step along the rail, both ends included (decision D1, D5).
     *
     * @return number of rows
     */
    public int imageHeight()
    {
        return (int) Math.floor(a1.dist(b)) + 1;
    }

    /**
     * Output image width: longest row, one pixel per unit step, both ends included (decision D5).
     *
     * @return number of columns
     */
    public int imageWidth()
    {
        // The distance between the rails is convex in y, so the widest row is the first or the
        // last one. Using the rails (not |bc|) keeps rows inside E even when the input is not
        // isosceles and the right rail overshoots or falls short of c.
        int last = imageHeight() - 1;
        Vec3 v1 = b.sub(a1).normalized();
        Vec3 v2 = c.sub(a2).normalized();
        double top = a1.dist(a2);
        double bottom = a1.add(v1.scale(last)).dist(a2.add(v2.scale(last)));
        double widest = Math.max(Math.max(top, bottom), b.dist(c));
        return (int) Math.floor(widest) + 1;
    }

    /**
     * Whether the projection algorithm can draw this plane: finite coordinates, rails longer than
     * 0, a real plane (not collinear, four points coplanar, sides not crossing) and an output
     * image of sane size. A plane can be renderable without being valid (e.g. not isosceles);
     * then the algorithm still has a well-defined result and the plane is drawn as given.
     *
     * @return true when project() may be called
     */
    public boolean isRenderable()
    {
        return whyNotRenderable() == null;
    }

    /**
     * @return start of the left rail (apex for a triangle)
     */
    public Vec3 a1()
    {
        return a1;
    }

    /**
     * @return start of the right rail (apex for a triangle)
     */
    public Vec3 a2()
    {
        return a2;
    }

    /**
     * @return end of the left rail
     */
    public Vec3 b()
    {
        return b;
    }

    /**
     * @return end of the right rail
     */
    public Vec3 c()
    {
        return c;
    }

    /**
     * @return true when the plane was given by three points
     */
    public boolean isTriangle()
    {
        return triangle;
    }

    @Override
    public String toString()
    {
        if (triangle)
        {
            return "Triangle[a=" + a1 + ", b=" + b + ", c=" + c + "]";
        }
        return "Trapezoid[a1=" + a1 + ", a2=" + a2 + ", b=" + b + ", c=" + c + "]";
    }

    /**
     * @param (double[]) v flat coordinates
     * @param (int) i point number
     * @return i-th point
     */
    private static Vec3 point(double[] v, int i)
    {
        return new Vec3(v[3 * i], v[3 * i + 1], v[3 * i + 2]);
    }

    /**
     * Distance of c from the plane through a1, a2, b. When a1, a2, b are collinear the
     * plane is spanned by a1->b and a2->c directly instead.
     *
     * @return distance in voxel units
     */
    private double distanceOfCFromPlane()
    {
        Vec3 normal = a2.sub(a1).cross(b.sub(a1));
        if (normal.length() < 1e-9)
        {
            // a1, a2, b on one line: any c spans a plane with them, but the rails must be
            // coplanar, which a2->c and a1->b always are in that case.
            return 0.0;
        }
        return Math.abs(c.sub(a1).dot(normal.normalized()));
    }

    /**
     * A trapezoid is a simple quadrilateral: its two rails must not intersect. Tested in the 2-D
     * coordinates of the plane itself (axes: bottom edge direction and its in-plane normal).
     *
     * @return true when segment a1->b intersects segment a2->c
     */
    private boolean railsCross()
    {
        if (triangle)
        {
            return false;
        }
        double[][] p = inPlane();
        if (p == null)
        {
            return false;
        }
        return segmentsIntersect(p[0], p[1], p[2], p[3]);
    }

    /**
     * The top a1-a2 and the base b-c of a trapezoid must not cross either (a "bow-tie" whose
     * rails do not touch but whose bases do).
     *
     * @return true when segment a1->a2 intersects segment b->c
     */
    private boolean topAndBaseCross()
    {
        if (triangle)
        {
            return false;
        }
        double[][] p = inPlane();
        if (p == null)
        {
            return false;
        }
        return segmentsIntersect(p[0], p[2], p[1], p[3]);
    }

    /**
     * How far a2 is from the parallel to the base through a1, measured in the plane: 0 for a real
     * trapezoid (top parallel to the base, pointing the same way).
     *
     * @return distance in voxel units (0 for a triangle or a top of length 0)
     */
    private double topOffParallel()
    {
        Vec3 top = a2.sub(a1);
        Vec3 base = c.sub(b);
        if (top.length() < 1e-9 || base.length() < 1e-9)
        {
            return 0.0;
        }
        Vec3 dir = base.normalized();
        double along = top.dot(dir);
        if (along < 0)
        {
            // Top pointing against the base: the shape is twisted, the whole top is "off".
            return top.length();
        }
        return top.sub(dir.scale(along)).length();
    }

    /**
     * The four points in 2-D coordinates of the plane (axes: base direction and its in-plane
     * normal), in the order a1, b, a2, c.
     *
     * @return four 2-D points, or null when the plane is degenerate
     */
    private double[][] inPlane()
    {
        Vec3 ex = c.sub(b);
        if (ex.length() < 1e-9)
        {
            ex = a2.sub(a1);
        }
        if (ex.length() < 1e-9)
        {
            return null;
        }
        ex = ex.normalized();
        Vec3 normal = ex.cross(b.sub(a1));
        if (normal.length() < 1e-9)
        {
            normal = ex.cross(c.sub(a2));
        }
        if (normal.length() < 1e-9)
        {
            return null;
        }
        Vec3 ey = normal.normalized().cross(ex);
        Vec3[] pts = {a1, b, a2, c};
        double[][] out = new double[4][];
        for (int i = 0; i < pts.length; i++)
        {
            Vec3 d = pts[i].sub(a1);
            out[i] = new double[] {d.dot(ex), d.dot(ey)};
        }
        return out;
    }

    /**
     * @param (double[]) p segment 1 start
     * @param (double[]) q segment 1 end
     * @param (double[]) r segment 2 start
     * @param (double[]) s segment 2 end
     * @return true when the open segments p-q and r-s properly intersect
     */
    private static boolean segmentsIntersect(double[] p, double[] q, double[] r, double[] s)
    {
        double d1 = cross2(r, s, p);
        double d2 = cross2(r, s, q);
        double d3 = cross2(p, q, r);
        double d4 = cross2(p, q, s);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    /**
     * @param (double[]) a line start
     * @param (double[]) b line end
     * @param (double[]) p point
     * @return 2-D cross product (b - a) x (p - a)
     */
    private static double cross2(double[] a, double[] b, double[] p)
    {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    /**
     * @return true when no coordinate is NaN or infinite
     */
    private boolean isFinite()
    {
        Vec3[] points = {a1, a2, b, c};
        for (int i = 0; i < points.length; i++)
        {
            Vec3 p = points[i];
            if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return name of the left side for messages
     */
    private String leftName()
    {
        if (triangle)
        {
            return "ab";
        }
        return "a1b";
    }

    /**
     * @return name of the right side for messages
     */
    private String rightName()
    {
        if (triangle)
        {
            return "ac";
        }
        return "a2c";
    }
}
