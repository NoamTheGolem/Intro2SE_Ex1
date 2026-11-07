package ex1.core;

import java.util.Arrays;

/**
 * Projects a volume onto a plane (docs/ALGORITHM.md §3).
 *
 * Template method: this class walks the rows of the trapezoid and centres each row in the
 * output image; subclasses only decide how one row of points is sampled
 * (nearest neighbour or trilinear). Stateless and therefore thread-safe; rows never overlap in
 * the output, which is what lets {@link ParallelProjector} split them across threads.
 */
public abstract class Projector
{
    /** Interpolation modes offered on the command line. */
    public enum Interpolation
    {
        NEAREST,
        TRILINEAR
    }

    /**
     * Makes a projector for a sampling mode.
     * @param (Interpolation) mode Sampling mode.
     * @return (Projector) Single-threaded projector for that mode.
     */
    public static Projector create(Interpolation mode)
    {
        if (mode == Interpolation.TRILINEAR)
        {
            return new TrilinearProjector();
        }
        return new NearestProjector();
    }

    /**
     * Makes a projector for a sampling mode on a number of threads.
     * @param (Interpolation) mode Sampling mode.
     * @param (int) threads Worker threads; 1 = run on the calling thread.
     * @return (Projector) Projector, wrapped in a {@link ParallelProjector} when threads &gt; 1.
     */
    public static Projector create(Interpolation mode, int threads)
    {
        Projector single = create(mode);
        if (threads <= 1)
        {
            return single;
        }
        return new ParallelProjector(single, threads);
    }

    /**
     * Projects into a newly allocated image of size plane.imageWidth() x plane.imageHeight().
     * @param (Volume) volume Source volume.
     * @param (Plane) plane Plane to sample (should be valid, see Plane.validate).
     * @return (Image2D) Projected image.
     */
    public Image2D project(Volume volume, Plane plane)
    {
        Image2D image = new Image2D(plane.imageWidth(), plane.imageHeight());
        project(volume, plane, image);
        return image;
    }

    /**
     * Projects into an existing image, so a frame loop can reuse one buffer (no garbage per frame).
     * The image must be at least plane.imageWidth() x plane.imageHeight(); the plane is drawn in
     * its top-left corner region and everything else is cleared to 0.
     * @param (Volume) volume Source volume.
     * @param (Plane) plane Plane to sample.
     * @param (Image2D) image Destination.
     */
    public void project(Volume volume, Plane plane, Image2D image)
    {
        int w = plane.imageWidth();
        int h = plane.imageHeight();
        if (image.width() < w || image.height() < h)
        {
            throw new IllegalArgumentException("image " + image.width() + "x" + image.height()
                    + " is smaller than plane " + w + "x" + h);
        }
        Arrays.fill(image.pixels(), 0f);
        projectRows(volume, plane, image, 0, h);
    }

    /**
     * Projects rows [fromRow, toRow) of the plane into the image. Rows are independent, so a
     * parallel projector can call this for different row ranges on different threads; the image
     * must already be cleared and large enough (see project()).
     * @param (Volume) volume Source volume.
     * @param (Plane) plane Plane to sample.
     * @param (Image2D) image Destination (not cleared here).
     * @param (int) fromRow First row, inclusive.
     * @param (int) toRow Last row, exclusive (at most plane.imageHeight()).
     */
    public void projectRows(Volume volume, Plane plane, Image2D image, int fromRow, int toRow)
    {
        int     w   = plane.imageWidth();
        float[] out = image.pixels();
        Vec3    a1  = plane.a1();
        Vec3    a2  = plane.a2();
        Vec3    v1  = plane.b().sub(a1).normalized();
        Vec3    v2  = plane.c().sub(a2).normalized();

        for (int y = fromRow; y < toRow; y++)
        {
            // Rail points are computed from y, not accumulated, so rounding error cannot grow (D2).
            double lx = a1.x + y * v1.x;
            double ly = a1.y + y * v1.y;
            double lz = a1.z + y * v1.z;
            double rx = a2.x + y * v2.x;
            double ry = a2.y + y * v2.y;
            double rz = a2.z + y * v2.z;

            double ex = rx - lx;
            double ey = ry - ly;
            double ez = rz - lz;
            double d  = Math.sqrt(ex * ex + ey * ey + ez * ez);

            int n = (int) Math.floor(d) + 1;
            if (n > w)
            {
                n = w;
            }
            double dx = 0.0;
            double dy = 0.0;
            double dz = 0.0;
            if (d > 0.0)
            {
                dx = ex / d;
                dy = ey / d;
                dz = ez / d;
            }
            int x0     = (w - n) / 2;
            int offset = x0 + y * image.width();
            sampleRow(volume, lx, ly, lz, dx, dy, dz, n, out, offset);
        }
    }

    /**
     * Samples n points p_i = start + i * step and writes them to out[offset .. offset + n).
     * Points outside the volume must produce 0.
     * @param (Volume) volume Source volume.
     * @param (double) px Start x.
     * @param (double) py Start y.
     * @param (double) pz Start z.
     * @param (double) dx Step x.
     * @param (double) dy Step y.
     * @param (double) dz Step z.
     * @param (int) n Number of samples.
     * @param (float[]) out Destination pixels.
     * @param (int) offset Index of the first destination pixel.
     */
    protected abstract void sampleRow(Volume volume, double px, double py, double pz,
                                      double dx, double dy, double dz,
                                      int n, float[] out, int offset);
}
