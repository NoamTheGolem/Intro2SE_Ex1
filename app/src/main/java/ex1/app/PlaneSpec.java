package ex1.app;

import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.util.Locale;

/**
 * A plane as the user edits it: a centre, an orientation (yaw / pitch / roll in degrees) and the
 * shape of an isosceles trapezoid (bottom width, top width as a fraction of it, perpendicular
 * height). Every spec maps to a valid {@link Plane}; interpolating two specs stays valid, which
 * is what makes smooth videos between "use case" keyframes possible (interpolating corner points
 * would not keep the rails equal).
 *
 * Frame convention: the unrotated plane is the XY plane of the volume — image columns run along
 * +x (vector u), image rows along +y (vector v), normal +z (vector n). Rotation
 * R = Ry(yaw) · Rx(pitch) · Rz(roll) is applied to that frame. All lengths are voxel units.
 */
public final class PlaneSpec
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Radians per degree. */
    private static final double DEG = Math.PI / 180.0;

    /** Smallest width and height of a plane, in voxels, so it is never degenerate. */
    private static final double MIN_SIZE = 2.0;

    /** Largest pitch either way, in degrees: the plane on its edge. */
    private static final double MAX_PITCH = 90.0;

    /** The presets' angles in degrees, and their size as a share of the volume's largest side. */
    private static final double TRIANGLE_YAW   = 35;
    private static final double TRIANGLE_PITCH = 25;
    private static final double TRIANGLE_SIZE  = 0.8;
    private static final double OBLIQUE_YAW    = 30;
    private static final double OBLIQUE_PITCH  = -20;
    private static final double OBLIQUE_SIZE   = 0.9;

    /** A top ratio this close to 1 is called a rectangle. */
    private static final double RECTANGLE_RATIO = 0.999;

    /** The centre of the plane, in voxel indices. */
    public final double cx;
    public final double cy;
    public final double cz;

    /** The plane's angles in degrees: yaw about y, pitch about x, roll about z. */
    public final double yaw;
    public final double pitch;
    public final double roll;

    /** Bottom width |bc|. */
    public final double width;

    /** |a1a2| / |bc| in [0, 1]: 0 = triangle, 1 = rectangle. */
    public final double topRatio;

    /** Perpendicular distance between the top and bottom edges. */
    public final double height;

    /**
     * Creates a spec; the size is kept at least one voxel and the top ratio within 0..1, so every
     * spec is a valid plane.
     * @param (double) cx Centre x.
     * @param (double) cy Centre y.
     * @param (double) cz Centre z.
     * @param (double) yaw Rotation about the volume y axis, degrees.
     * @param (double) pitch Rotation about the volume x axis, degrees.
     * @param (double) roll Rotation about the plane normal, degrees.
     * @param (double) width Bottom width |bc| (>= 2).
     * @param (double) topRatio |a1a2| / |bc| in [0, 1].
     * @param (double) height Perpendicular height (>= 2).
     */
    public PlaneSpec(double cx, double cy, double cz, double yaw, double pitch, double roll,
                     double width, double topRatio, double height)
    {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.yaw = wrap180(yaw);
        this.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
        this.roll = wrap180(roll);
        this.width = Math.max(MIN_SIZE, width);
        this.topRatio = Math.max(0.0, Math.min(1.0, topRatio));
        this.height = Math.max(MIN_SIZE, height);
    }

    // ==========================================
    //                  PRESETS
    // ==========================================

    /**
     * Makes a rectangle through the centre of the volume, perpendicular to the z axis (the XY
     * plane).
     * @param (Volume) v Volume.
     * @return (PlaneSpec) The spec.
     */
    public static PlaneSpec xy(Volume v)
    {
        return new PlaneSpec(mid(v.sizeX()), mid(v.sizeY()), mid(v.sizeZ()), 0, 0, 0,
                v.sizeX() - 1, 1.0, v.sizeY() - 1);
    }

    /**
     * Makes a rectangle perpendicular to the y axis (the XZ plane): columns along x, rows along z.
     * @param (Volume) v Volume.
     * @return (PlaneSpec) The spec.
     */
    public static PlaneSpec xz(Volume v)
    {
        return new PlaneSpec(mid(v.sizeX()), mid(v.sizeY()), mid(v.sizeZ()), 0, 90, 0,
                v.sizeX() - 1, 1.0, v.sizeZ() - 1);
    }

    /**
     * Makes a rectangle perpendicular to the x axis (the YZ plane): columns along z, rows along y.
     * @param (Volume) v Volume.
     * @return (PlaneSpec) The spec.
     */
    public static PlaneSpec yz(Volume v)
    {
        return new PlaneSpec(mid(v.sizeX()), mid(v.sizeY()), mid(v.sizeZ()), 90, 0, 0,
                v.sizeZ() - 1, 1.0, v.sizeY() - 1);
    }

    /**
     * Makes an oblique isosceles triangle through the centre, the shape of the assignment sheet.
     * @param (Volume) v Volume.
     * @return (PlaneSpec) The spec.
     */
    public static PlaneSpec triangle(Volume v)
    {
        double d = TRIANGLE_SIZE * Math.max(v.sizeX(), Math.max(v.sizeY(), v.sizeZ()));
        return new PlaneSpec(mid(v.sizeX()), mid(v.sizeY()), mid(v.sizeZ()), TRIANGLE_YAW, TRIANGLE_PITCH, 0, d, 0.0, d);
    }

    /**
     * Makes an oblique rectangle through the centre: the first thing the app shows, so the cut is
     * visibly different from the three model slices.
     * @param (Volume) v Volume.
     * @return (PlaneSpec) The spec.
     */
    public static PlaneSpec oblique(Volume v)
    {
        double d = OBLIQUE_SIZE * Math.max(v.sizeX(), Math.max(v.sizeY(), v.sizeZ()));
        return new PlaneSpec(mid(v.sizeX()), mid(v.sizeY()), mid(v.sizeZ()), OBLIQUE_YAW, OBLIQUE_PITCH, 0, d, 1.0, d);
    }

    // ==========================================
    //                  GEOMETRY
    // ==========================================

    /**
     * Gives the direction of the image columns in the volume.
     * @return (Vec3) Unit vector along the image columns (a1 -> a2 direction).
     */
    public Vec3 across()
    {
        return rotate(new Vec3(1, 0, 0));
    }

    /**
     * Gives the direction of the image rows in the volume.
     * @return (Vec3) Unit vector along the image rows (top edge -> bottom edge).
     */
    public Vec3 down()
    {
        return rotate(new Vec3(0, 1, 0));
    }

    /**
     * Gives the plane's unit normal.
     * @return (Vec3) Unit normal of the plane.
     */
    public Vec3 normal()
    {
        return rotate(new Vec3(0, 0, 1));
    }

    /**
     * Gives the centre of the plane.
     * @return (Vec3) Centre point.
     */
    public Vec3 centre()
    {
        return new Vec3(cx, cy, cz);
    }

    /**
     * Builds the plane of the assignment: rails a1->b and a2->c of equal length, top edge
     * |a1a2| = topRatio * width, bottom edge |bc| = width.
     * @return (Plane) The plane (a triangle when topRatio is 0).
     */
    public Plane toPlane()
    {
        Vec3   c0         = centre();
        Vec3   u          = across();
        Vec3   v          = down();
        Vec3   top        = c0.sub(v.scale(height / 2.0));
        Vec3   bottom     = c0.add(v.scale(height / 2.0));
        double halfTop    = topRatio * width / 2.0;
        double halfBottom = width / 2.0;
        Vec3   b          = bottom.sub(u.scale(halfBottom));
        Vec3   c          = bottom.add(u.scale(halfBottom));
        if (topRatio <= 0.0)
        {
            return Plane.triangle(top, b, c);
        }
        return Plane.trapezoid(top.sub(u.scale(halfTop)), top.add(u.scale(halfTop)), b, c);
    }

    /**
     * Recovers a spec from any plane, the best-effort inverse of toPlane() (also for one that is
     * not exactly isosceles, e.g. a CSV row that breaks the rules), so it can be shown in the
     * editor.
     * @param (Plane) p Plane.
     * @return (PlaneSpec) The spec.
     */
    public static PlaneSpec fromPlane(Plane p)
    {
        Vec3   topMid    = p.a1().add(p.a2()).scale(0.5);
        Vec3   bottomMid = p.b().add(p.c()).scale(0.5);
        Vec3   centre    = topMid.add(bottomMid).scale(0.5);
        Vec3   v         = bottomMid.sub(topMid);
        double height    = v.length();
        v = v.normalized();
        Vec3   u     = p.c().sub(p.b());
        double width = u.length();
        if (width < 1e-9)
        {
            u = p.a2().sub(p.a1());
            width = u.length();
        }
        u = u.normalized();
        // Make u exactly perpendicular to v (Gram-Schmidt), then n = u x v.
        u = u.sub(v.scale(u.dot(v))).normalized();
        if (u.length() < 0.5)
        {
            u = anyPerpendicular(v);
        }
        Vec3   n     = u.cross(v).normalized();
        double pitch = -Math.asin(clamp(n.y, -1, 1)) / DEG;
        double yaw   = Math.atan2(n.x, n.z) / DEG;
        // Undo yaw and pitch, what is left of u is the roll.
        Vec3   u1       = rotX(rotY(u, -yaw * DEG), -pitch * DEG);
        double roll     = Math.atan2(u1.y, u1.x) / DEG;
        double topRatio = 0.0;
        if (width > 1e-9)
        {
            topRatio = p.a1().dist(p.a2()) / width;
        }
        return new PlaneSpec(centre.x, centre.y, centre.z, yaw, pitch, roll, width, topRatio, height);
    }

    /**
     * Finds the nearest plane that follows the Q&amp;A rules, for points placed by hand (clicks are
     * never exact). A triangle keeps a and b and the direction from a to c; only c moves, to |ac| =
     * |ab|. A trapezoid becomes the isosceles trapezoid with the same centre, orientation and size.
     * @param (Plane) p Any renderable plane.
     * @return (Plane) A valid plane.
     */
    public static Plane makeIsosceles(Plane p)
    {
        if (p.isTriangle())
        {
            Vec3   a  = p.a1();
            Vec3   ac = p.c().sub(a);
            double ab = a.dist(p.b());
            if (ac.length() < 1e-9)
            {
                return p;
            }
            return Plane.triangle(a, p.b(), a.add(ac.normalized().scale(ab)));
        }
        return fromPlane(p).toPlane();
    }

    /**
     * Interpolates every parameter linearly (angles along the shorter arc).
     * @param (PlaneSpec) a Start.
     * @param (PlaneSpec) b End.
     * @param (double) t 0..1.
     * @return (PlaneSpec) Interpolated spec.
     */
    public static PlaneSpec lerp(PlaneSpec a, PlaneSpec b, double t)
    {
        return new PlaneSpec(
                a.cx + (b.cx - a.cx) * t,
                a.cy + (b.cy - a.cy) * t,
                a.cz + (b.cz - a.cz) * t,
                lerpAngle(a.yaw, b.yaw, t),
                a.pitch + (b.pitch - a.pitch) * t,
                lerpAngle(a.roll, b.roll, t),
                a.width + (b.width - a.width) * t,
                a.topRatio + (b.topRatio - a.topRatio) * t,
                a.height + (b.height - a.height) * t);
    }

    // ==========================================
    //               "WITH" COPIES
    // ==========================================

    /**
     * Moves the plane.
     * @param (double) x New centre x.
     * @param (double) y New centre y.
     * @param (double) z New centre z.
     * @return (PlaneSpec) Copy with a new centre.
     */
    public PlaneSpec withCentre(double x, double y, double z)
    {
        return new PlaneSpec(x, y, z, yaw, pitch, roll, width, topRatio, height);
    }

    /**
     * Turns the plane.
     * @param (double) newYaw Yaw.
     * @param (double) newPitch Pitch.
     * @param (double) newRoll Roll.
     * @return (PlaneSpec) Copy with a new orientation.
     */
    public PlaneSpec withAngles(double newYaw, double newPitch, double newRoll)
    {
        return new PlaneSpec(cx, cy, cz, newYaw, newPitch, newRoll, width, topRatio, height);
    }

    /**
     * Changes the plane's size and shape (top ratio 0: triangle).
     * @param (double) newWidth Bottom width.
     * @param (double) newTopRatio Top ratio.
     * @param (double) newHeight Height.
     * @return (PlaneSpec) Copy with a new shape.
     */
    public PlaneSpec withShape(double newWidth, double newTopRatio, double newHeight)
    {
        return new PlaneSpec(cx, cy, cz, yaw, pitch, roll, newWidth, newTopRatio, newHeight);
    }

    /**
     * Moves the centre along the plane normal: the "slice through the model" gesture.
     * @param (double) distance Voxels (negative = backwards).
     * @return (PlaneSpec) Moved copy.
     */
    public PlaneSpec slide(double distance)
    {
        Vec3 n = normal();
        return withCentre(cx + n.x * distance, cy + n.y * distance, cz + n.z * distance);
    }

    /**
     * Describes the spec in a few words.
     * @return (String) Short label for lists, e.g. "Triangle 250x250 @ (128,128,65)".
     */
    public String label()
    {
        String shape = "Rectangle";
        if (topRatio <= 0.0)
        {
            shape = "Triangle";
        }
        else if (topRatio < RECTANGLE_RATIO)
        {
            shape = "Trapezoid";
        }
        String text = String.format(Locale.ROOT, "%s %.0fx%.0f @ (%.0f, %.0f, %.0f) yaw %.0f pitch %.0f roll %.0f",
                shape, width, height, cx, cy, cz, yaw, pitch, roll);
        // %.0f prints -0.4 as "-0"; nobody wants to read a negative zero.
        return text.replace(" -0 ", " 0 ").replace(" -0)", " 0)").replace("(-0,", "(0,").replace("roll -0", "roll 0");
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Rotates a vector from the plane's own frame into the volume: roll, then pitch, then yaw.
     * @param (Vec3) p Vector in the unrotated frame.
     * @return (Vec3) R · p with R = Ry(yaw) · Rx(pitch) · Rz(roll).
     */
    private Vec3 rotate(Vec3 p)
    {
        return rotY(rotX(rotZ(p, roll * DEG), pitch * DEG), yaw * DEG);
    }

    /**
     * Rotates a vector about the x axis.
     * @param (Vec3) p Vector.
     * @param (double) t Angle in radians.
     * @return (Vec3) Rotation about x.
     */
    static Vec3 rotX(Vec3 p, double t)
    {
        double c = Math.cos(t);
        double s = Math.sin(t);
        return new Vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c);
    }

    /**
     * Rotates a vector about the y axis.
     * @param (Vec3) p Vector.
     * @param (double) t Angle in radians.
     * @return (Vec3) Rotation about y.
     */
    static Vec3 rotY(Vec3 p, double t)
    {
        double c = Math.cos(t);
        double s = Math.sin(t);
        return new Vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
    }

    /**
     * Rotates a vector about the z axis.
     * @param (Vec3) p Vector.
     * @param (double) t Angle in radians.
     * @return (Vec3) Rotation about z.
     */
    static Vec3 rotZ(Vec3 p, double t)
    {
        double c = Math.cos(t);
        double s = Math.sin(t);
        return new Vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z);
    }

    /**
     * Finds a vector perpendicular to v: v crossed with the x axis, or with y when v nearly lies
     * along x.
     * @param (Vec3) v Unit vector.
     * @return (Vec3) Some unit vector perpendicular to v.
     */
    private static Vec3 anyPerpendicular(Vec3 v)
    {
        Vec3 axis = new Vec3(1, 0, 0);
        if (Math.abs(v.x) > 0.9)
        {
            axis = new Vec3(0, 1, 0);
        }
        return v.cross(axis).normalized();
    }

    /**
     * Interpolates an angle the short way round (170 to -170 passes through 180, not 0).
     * @param (double) a Start angle, degrees.
     * @param (double) b End angle, degrees.
     * @param (double) t 0..1.
     * @return (double) Angle along the shorter arc.
     */
    private static double lerpAngle(double a, double b, double t)
    {
        double d = wrap180(b - a);
        return wrap180(a + d * t);
    }

    /**
     * Brings an angle into (-180, 180].
     * @param (double) a Angle in degrees.
     * @return (double) The same angle in (-180, 180].
     */
    private static double wrap180(double a)
    {
        double r = a % 360.0;
        if (r > 180.0)
        {
            r -= 360.0;
        }
        if (r <= -180.0)
        {
            r += 360.0;
        }
        return r;
    }

    /**
     * Keeps a value between two bounds.
     * @param (double) x Value.
     * @param (double) lo Lower bound.
     * @param (double) hi Upper bound.
     * @return (double) Clamped value.
     */
    private static double clamp(double x, double lo, double hi)
    {
        return Math.max(lo, Math.min(hi, x));
    }

    /**
     * Gives the index of an axis's middle voxel.
     * @param (int) size Axis size.
     * @return (double) Index of the middle voxel.
     */
    private static double mid(int size)
    {
        return (size - 1) / 2.0;
    }
}
