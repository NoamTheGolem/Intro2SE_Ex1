package ex1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A plane section given as an isosceles trapezoid (a1, a2, b, c): the left rail runs a1 -> b,
 * the right rail runs a2 -> c. An isosceles triangle (a, b, c) is the special case a1 = a2 = a,
 * so the rest of the program only ever handles trapezoids.
 *
 * See docs/ALGORITHM.md §3 and §6.
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
        }
        return errors;
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
     * Whether the projection algorithm can run on this plane at all: finite coordinates, both
     * rails longer than 0, and an output image of sane size. A plane can be renderable without
     * being valid (e.g. not isosceles); then the algorithm still has a well-defined result.
     *
     * @return true when project() may be called
     */
    public boolean isRenderable()
    {
        if (!isFinite())
        {
            return false;
        }
        if (a1.dist(b) <= 0.0 || a2.dist(c) <= 0.0)
        {
            return false;
        }
        double sides = Math.max(a1.dist(b), Math.max(a1.dist(a2), b.dist(c)));
        return sides < MAX_IMAGE_SIDE;
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
