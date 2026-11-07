package ex1.core;

/**
 * Nearest-neighbour sampling: v = M[round(p)], 0 outside the volume.
 * This is the course's "v = M[p]" and the fast default.
 */
public final class NearestProjector extends Projector
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Rounds to the nearest voxel. */
    private static final double HALF = 0.5;

    @Override
    protected void sampleRow(Volume volume, double px, double py, double pz,
                             double dx, double dy, double dz,
                             int n, float[] out, int offset)
    {
        int sx  = volume.sizeX();
        int sy  = volume.sizeY();
        int sz  = volume.sizeZ();
        int sxy = sx * sy;

        // Shifting by +0.5 turns rounding into truncation; the range checks below guarantee
        // the shifted values are non-negative, where (int) truncation equals floor.
        double qx = px + HALF;
        double qy = py + HALF;
        double qz = pz + HALF;

        for (int i = 0; i < n; i++)
        {
            if (qx >= 0.0 && qx < sx && qy >= 0.0 && qy < sy && qz >= 0.0 && qz < sz)
            {
                int index = (int) qx + sx * (int) qy + sxy * (int) qz;
                out[offset + i] = volume.valueAt(index);
            }
            qx += dx;
            qy += dy;
            qz += dz;
        }
    }
}
