package ex1.app;

import ex1.core.Plane;
import ex1.core.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The video the app plays and exports when no use case is loaded: the current plane moved along
 * its normal through the whole volume, so the cut travels through the object from one side to the
 * other - the same kind of video as the lecturer's slice-by-slice use cases, for any plane.
 */
final class Sweep
{
    /** About this many frames per pass: 10-20 s of video at 10 fps, whatever the volume size. */
    static final int TARGET_FRAMES = 150;

    /** The planes of one pass, in order, and where the current plane is among them. */
    static final class Pass
    {
        final List<Plane> planes;
        final int current;

        /**
         * @param (List) planes the frames
         * @param (int) current index of the frame closest to the current plane
         */
        Pass(List<Plane> planes, int current)
        {
            this.planes = planes;
            this.current = current;
        }
    }

    /** Static helpers only. */
    private Sweep()
    {
    }

    /**
     * Copies of the plane moved along its normal in whole-voxel steps, from where it first touches
     * the volume's box to where it last does. The current plane itself is one of them (offset 0)
     * when it touches the box.
     *
     * @param (Plane) p the current plane
     * @param (int) sx volume size x
     * @param (int) sy volume size y
     * @param (int) sz volume size z
     * @return the pass; just the plane itself when it has no normal (a bad plane)
     */
    static Pass through(Plane p, int sx, int sy, int sz)
    {
        List<Plane> planes = new ArrayList<Plane>();
        Vec3 n = OrthoView.normalOf(p);
        if (!(n.length() > 1e-9))
        {
            planes.add(p);
            return new Pass(planes, 0);
        }
        n = n.normalized();
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        int[] xs = {0, sx - 1};
        int[] ys = {0, sy - 1};
        int[] zs = {0, sz - 1};
        for (int i = 0; i < 2; i++)
        {
            for (int j = 0; j < 2; j++)
            {
                for (int k = 0; k < 2; k++)
                {
                    double d = new Vec3(xs[i], ys[j], zs[k]).sub(p.a1()).dot(n);
                    lo = Math.min(lo, d);
                    hi = Math.max(hi, d);
                }
            }
        }
        int step = (int) Math.max(1, Math.round((hi - lo) / TARGET_FRAMES));
        int first = (int) Math.ceil(lo / step);
        int last = (int) Math.floor(hi / step);
        for (int k = first; k <= last; k++)
        {
            planes.add(shifted(p, n.scale((double) k * step)));
        }
        if (planes.isEmpty())
        {
            planes.add(p);
            return new Pass(planes, 0);
        }
        int current = Math.max(0, Math.min(planes.size() - 1, -first));
        return new Pass(planes, current);
    }

    /**
     * @param (Plane) p plane
     * @param (Vec3) d offset
     * @return the same plane moved by d
     */
    static Plane shifted(Plane p, Vec3 d)
    {
        if (p.isTriangle())
        {
            return Plane.triangle(p.a1().add(d), p.b().add(d), p.c().add(d));
        }
        return Plane.trapezoid(p.a1().add(d), p.a2().add(d), p.b().add(d), p.c().add(d));
    }
}
