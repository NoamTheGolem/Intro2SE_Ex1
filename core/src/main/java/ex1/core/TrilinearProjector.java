package ex1.core;

/**
 * Trilinear sampling: weighted average of the 8 voxels around p, 0 when p lies outside
 * [0, s-1] on any axis. Smoother oblique slices at roughly 2-3x the cost of nearest neighbour.
 */
public final class TrilinearProjector extends Projector
{
    @Override
    protected void sampleRow(Volume volume, double px, double py, double pz,
                             double dx, double dy, double dz,
                             int n, float[] out, int offset)
    {
        int sx = volume.sizeX();
        int sy = volume.sizeY();
        int sz = volume.sizeZ();
        int sxy = sx * sy;
        double maxX = sx - 1;
        double maxY = sy - 1;
        double maxZ = sz - 1;

        double qx = px;
        double qy = py;
        double qz = pz;

        for (int i = 0; i < n; i++)
        {
            if (qx >= 0.0 && qx <= maxX && qy >= 0.0 && qy <= maxY && qz >= 0.0 && qz <= maxZ)
            {
                int x0 = (int) qx;
                int y0 = (int) qy;
                int z0 = (int) qz;
                double fx = qx - x0;
                double fy = qy - y0;
                double fz = qz - z0;

                // On the last voxel of an axis (or an axis of size 1) the "+1" neighbour does
                // not exist; its weight is 0 there, so we reuse the same voxel instead.
                int stepX = 1;
                int stepY = sx;
                int stepZ = sxy;
                if (x0 >= sx - 1)
                {
                    stepX = 0;
                }
                if (y0 >= sy - 1)
                {
                    stepY = 0;
                }
                if (z0 >= sz - 1)
                {
                    stepZ = 0;
                }

                int i000 = x0 + sx * y0 + sxy * z0;
                double c00 = lerp(volume.valueAt(i000), volume.valueAt(i000 + stepX), fx);
                double c10 = lerp(volume.valueAt(i000 + stepY), volume.valueAt(i000 + stepY + stepX), fx);
                double c01 = lerp(volume.valueAt(i000 + stepZ), volume.valueAt(i000 + stepZ + stepX), fx);
                double c11 = lerp(volume.valueAt(i000 + stepZ + stepY),
                        volume.valueAt(i000 + stepZ + stepY + stepX), fx);
                double c0 = lerp(c00, c10, fy);
                double c1 = lerp(c01, c11, fy);
                out[offset + i] = (float) lerp(c0, c1, fz);
            }
            qx += dx;
            qy += dy;
            qz += dz;
        }
    }

    /**
     * @param (double) a value at t = 0
     * @param (double) b value at t = 1
     * @param (double) t weight in [0, 1]
     * @return a + (b - a) * t
     */
    private static double lerp(double a, double b, double t)
    {
        return a + (b - a) * t;
    }
}
