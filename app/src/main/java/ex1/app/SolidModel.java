package ex1.app;

import ex1.core.IntensityWindow;
import ex1.core.Volume;

/**
 * The volume as a solid object for the 3-D view: what is "inside" the scanned thing (head, cake,
 * onion), so a cut through it can show its inside like a cut through a pie.
 *
 * Built once per volume (and again when the surface threshold changes):
 * <ol>
 *   <li>grey values through the video's intensity window, averaged down to at most
 *       {@link #MAX_SIDE} voxels per side (the 3-D view does not need more);</li>
 *   <li>a light 1-2-1 smoothing, so scanner noise does not turn into a rough skin;</li>
 *   <li>a surface threshold, by default Otsu's split between background and object;</li>
 *   <li>tiny bright specks in the air removed (connected pieces below a minimum size);</li>
 *   <li>everything the outside air cannot reach from the border of the volume is solid: dark
 *       regions inside the object (ventricles, the dark bone of an MRI skull) stay part of it, so
 *       the cut face shows them as they are. The air may not squeeze through gaps narrower than
 *       {@link #CLOSING} voxels (a morphological closing), so nostrils and ear canals do not let
 *       it flood the inside of a head;</li>
 * </ol>
 * Plus 8^3 blocks that record whether they contain any solid voxel, which lets rays skip the empty
 * air quickly.
 */
final class SolidModel
{
    static final int MAX_SIDE = 256;
    static final int BLOCK = 8;

    private static final byte BELOW = 0;
    private static final byte ABOVE = 1;
    private static final byte OUTSIDE = 2;
    private static final byte KEPT = 3;
    private static final int GREY_LEVELS = 256;
    private static final int MIN_SPECK = 64;
    static final int CLOSING = 3;
    private static final int SPECK_FRACTION = 4000;

    final int factor;
    final int rx;
    final int ry;
    final int rz;
    private final byte[] smooth;
    private final byte[] state;
    private boolean[] blockSolid;
    private int bx;
    private int by;
    private int bz;
    private int threshold;
    private final int autoThreshold;
    private int solidCount;

    /**
     * @param (int) factor original voxels per model voxel along each axis
     * @param (int) rx model size x
     * @param (int) ry model size y
     * @param (int) rz model size z
     * @param (byte[]) smooth smoothed grey values
     */
    private SolidModel(int factor, int rx, int ry, int rz, byte[] smooth)
    {
        this(factor, rx, ry, rz, smooth, otsu(smooth));
    }

    /**
     * @param (int) factor original voxels per model voxel along each axis
     * @param (int) rx model size x
     * @param (int) ry model size y
     * @param (int) rz model size z
     * @param (byte[]) smooth smoothed grey values (shared, never changed)
     * @param (int) autoThreshold Otsu's threshold of smooth
     */
    private SolidModel(int factor, int rx, int ry, int rz, byte[] smooth, int autoThreshold)
    {
        this.factor = factor;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.smooth = smooth;
        this.state = new byte[smooth.length];
        this.autoThreshold = autoThreshold;
    }

    /**
     * A copy with another surface threshold (the original stays usable, e.g. by the view that is
     * still painting it while the new one is built in the background).
     *
     * @param (int) t grey level
     * @return the new model
     */
    SolidModel withThreshold(int t)
    {
        SolidModel m = new SolidModel(factor, rx, ry, rz, smooth, autoThreshold);
        m.setThreshold(t);
        return m;
    }

    /**
     * @param (Volume) v volume
     * @param (IntensityWindow) window grey mapping of the video
     * @return the model with the automatic threshold applied
     */
    static SolidModel build(Volume v, IntensityWindow window)
    {
        int maxDim = Math.max(v.sizeX(), Math.max(v.sizeY(), v.sizeZ()));
        int f = Math.max(1, (maxDim + MAX_SIDE - 1) / MAX_SIDE);
        int nx = (v.sizeX() + f - 1) / f;
        int ny = (v.sizeY() + f - 1) / f;
        int nz = (v.sizeZ() + f - 1) / f;
        byte[] grey = new byte[nx * ny * nz];
        int step = 1;
        if (f > 2)
        {
            // Averaging every voxel of a 4x4x4 block is slow and not needed for a preview.
            step = f / 2;
        }
        for (int k = 0; k < nz; k++)
        {
            for (int j = 0; j < ny; j++)
            {
                for (int i = 0; i < nx; i++)
                {
                    int sum = 0;
                    int n = 0;
                    for (int z = k * f; z < Math.min(v.sizeZ(), k * f + f); z += step)
                    {
                        for (int y = j * f; y < Math.min(v.sizeY(), j * f + f); y += step)
                        {
                            int row = v.index(0, y, z);
                            for (int x = i * f; x < Math.min(v.sizeX(), i * f + f); x += step)
                            {
                                sum += window.grey(v.valueAt(row + x));
                                n++;
                            }
                        }
                    }
                    grey[i + nx * (j + ny * k)] = (byte) (sum / Math.max(1, n));
                }
            }
        }
        smooth121(grey, nx, ny, nz);
        SolidModel m = new SolidModel(f, nx, ny, nz, grey);
        m.setThreshold(m.autoThreshold);
        return m;
    }

    // ------------------------------------------------------------------ threshold & mask

    /**
     * @return Otsu's threshold of this volume
     */
    int autoThreshold()
    {
        return autoThreshold;
    }

    /**
     * @return the threshold in use (grey level 1..255)
     */
    int threshold()
    {
        return threshold;
    }

    /**
     * @return number of solid model voxels (0 = nothing to show)
     */
    int solidCount()
    {
        return solidCount;
    }

    /**
     * Computes the solid mask for a surface threshold.
     *
     * @param (int) t grey level; voxels at or above it are "object"
     */
    private void setThreshold(int t)
    {
        threshold = Math.max(1, Math.min(GREY_LEVELS - 1, t));
        int n = smooth.length;
        for (int i = 0; i < n; i++)
        {
            if ((smooth[i] & 0xFF) >= threshold)
            {
                state[i] = ABOVE;
            }
            else
            {
                state[i] = BELOW;
            }
        }
        removeSpecks();
        floodOutsideClosed();
        solidCount = 0;
        for (int i = 0; i < n; i++)
        {
            if (state[i] != OUTSIDE)
            {
                solidCount++;
            }
        }
        buildBlocks();
    }

    /** Pieces of "object" smaller than a speck size are scanner noise in the air: drop them. */
    private void removeSpecks()
    {
        int minSize = Math.max(MIN_SPECK, smooth.length / SPECK_FRACTION);
        IntQueue queue = new IntQueue();
        IntQueue members = new IntQueue();
        for (int start = 0; start < state.length; start++)
        {
            if (state[start] != ABOVE)
            {
                continue;
            }
            queue.clear();
            members.clear();
            state[start] = KEPT;
            queue.add(start);
            while (!queue.isEmpty())
            {
                int p = queue.poll();
                members.add(p);
                visitNeighbours(p, ABOVE, KEPT, queue);
            }
            if (members.size() < minSize)
            {
                for (int i = 0; i < members.size(); i++)
                {
                    state[members.get(i)] = BELOW;
                }
            }
        }
    }

    /**
     * Marks the outside air, with a closing of the object: the air is flooded from the border
     * around the object grown by CLOSING voxels, then grown back by the same amount. The grown
     * air never reaches an object voxel (every point within CLOSING of the object belongs to the
     * grown object), so the object itself is never eaten away; only narrow channels into it stay
     * closed.
     */
    private void floodOutsideClosed()
    {
        int n = state.length;
        byte[] grown = new byte[n];
        for (int i = 0; i < n; i++)
        {
            if (state[i] == KEPT || state[i] == ABOVE)
            {
                grown[i] = 1;
            }
        }
        for (int r = 0; r < CLOSING; r++)
        {
            grow(grown, (byte) 1, (byte) 0);
        }
        // Flood the air around the grown object; mark it 2 in "grown".
        IntQueue queue = new IntQueue();
        for (int k = 0; k < rz; k++)
        {
            for (int j = 0; j < ry; j++)
            {
                for (int i = 0; i < rx; i++)
                {
                    boolean border = i == 0 || j == 0 || k == 0 || i == rx - 1 || j == ry - 1 || k == rz - 1;
                    int p = i + rx * (j + ry * k);
                    if (border && grown[p] == 0)
                    {
                        grown[p] = 2;
                        queue.add(p);
                    }
                }
            }
        }
        while (!queue.isEmpty())
        {
            int p = queue.poll();
            int i = p % rx;
            int j = (p / rx) % ry;
            int k = p / (rx * ry);
            int plane = rx * ry;
            if (i > 0 && grown[p - 1] == 0)
            {
                grown[p - 1] = 2;
                queue.add(p - 1);
            }
            if (i < rx - 1 && grown[p + 1] == 0)
            {
                grown[p + 1] = 2;
                queue.add(p + 1);
            }
            if (j > 0 && grown[p - rx] == 0)
            {
                grown[p - rx] = 2;
                queue.add(p - rx);
            }
            if (j < ry - 1 && grown[p + rx] == 0)
            {
                grown[p + rx] = 2;
                queue.add(p + rx);
            }
            if (k > 0 && grown[p - plane] == 0)
            {
                grown[p - plane] = 2;
                queue.add(p - plane);
            }
            if (k < rz - 1 && grown[p + plane] == 0)
            {
                grown[p + plane] = 2;
                queue.add(p + plane);
            }
        }
        // Grow the air back over the margin that the object was grown by.
        for (int r = 0; r < CLOSING; r++)
        {
            grow(grown, (byte) 2, (byte) 1);
        }
        for (int i = 0; i < n; i++)
        {
            if (grown[i] == 2 && state[i] != KEPT && state[i] != ABOVE)
            {
                state[i] = OUTSIDE;
            }
        }
    }

    /**
     * One step of growth (6-neighbourhood): every voxel holding "from" next to a voxel holding
     * "value" becomes "value".
     *
     * @param (byte[]) m mask
     * @param (byte) value the growing label
     * @param (byte) from the label it may replace
     */
    private void grow(byte[] m, byte value, byte from)
    {
        int plane = rx * ry;
        byte[] next = m.clone();
        for (int k = 0; k < rz; k++)
        {
            for (int j = 0; j < ry; j++)
            {
                int row = rx * (j + ry * k);
                for (int i = 0; i < rx; i++)
                {
                    int p = row + i;
                    if (m[p] != from)
                    {
                        continue;
                    }
                    boolean touch = (i > 0 && m[p - 1] == value) || (i < rx - 1 && m[p + 1] == value)
                            || (j > 0 && m[p - rx] == value) || (j < ry - 1 && m[p + rx] == value)
                            || (k > 0 && m[p - plane] == value) || (k < rz - 1 && m[p + plane] == value);
                    if (touch)
                    {
                        next[p] = value;
                    }
                }
            }
        }
        System.arraycopy(next, 0, m, 0, m.length);
    }

    /**
     * @param (int) p voxel index
     * @param (byte) from state a neighbour must have to be visited
     * @param (byte) to state it gets
     * @param (IntQueue) queue receives the visited neighbours
     */
    private void visitNeighbours(int p, byte from, byte to, IntQueue queue)
    {
        int i = p % rx;
        int j = (p / rx) % ry;
        int k = p / (rx * ry);
        int plane = rx * ry;
        if (i > 0 && state[p - 1] == from)
        {
            state[p - 1] = to;
            queue.add(p - 1);
        }
        if (i < rx - 1 && state[p + 1] == from)
        {
            state[p + 1] = to;
            queue.add(p + 1);
        }
        if (j > 0 && state[p - rx] == from)
        {
            state[p - rx] = to;
            queue.add(p - rx);
        }
        if (j < ry - 1 && state[p + rx] == from)
        {
            state[p + rx] = to;
            queue.add(p + rx);
        }
        if (k > 0 && state[p - plane] == from)
        {
            state[p - plane] = to;
            queue.add(p - plane);
        }
        if (k < rz - 1 && state[p + plane] == from)
        {
            state[p + plane] = to;
            queue.add(p + plane);
        }
    }

    /** Records which 8^3 blocks contain solid voxels. */
    private void buildBlocks()
    {
        bx = (rx + BLOCK - 1) / BLOCK;
        by = (ry + BLOCK - 1) / BLOCK;
        bz = (rz + BLOCK - 1) / BLOCK;
        blockSolid = new boolean[bx * by * bz];
        for (int k = 0; k < rz; k++)
        {
            for (int j = 0; j < ry; j++)
            {
                int row = rx * (j + ry * k);
                int brow = bx * (j / BLOCK + by * (k / BLOCK));
                for (int i = 0; i < rx; i++)
                {
                    if (state[row + i] != OUTSIDE)
                    {
                        blockSolid[brow + i / BLOCK] = true;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ queries

    /**
     * @param (int) i model x
     * @param (int) j model y
     * @param (int) k model z
     * @return true when the voxel is inside the object (false outside the model)
     */
    boolean isSolid(int i, int j, int k)
    {
        if (i < 0 || j < 0 || k < 0 || i >= rx || j >= ry || k >= rz)
        {
            return false;
        }
        return state[i + rx * (j + ry * k)] != OUTSIDE;
    }

    /**
     * @param (int) i model x
     * @param (int) j model y
     * @param (int) k model z
     * @return true when the 8^3 block holding the voxel contains anything solid
     */
    boolean blockHasSolid(int i, int j, int k)
    {
        if (i < 0 || j < 0 || k < 0 || i >= rx || j >= ry || k >= rz)
        {
            return false;
        }
        return blockSolid[i / BLOCK + bx * (j / BLOCK + by * (k / BLOCK))];
    }

    /**
     * Trilinear sample of the smoothed grey (clamped at the border).
     *
     * @param (double) x model x
     * @param (double) y model y
     * @param (double) z model z
     * @return grey value 0..255
     */
    double smoothAt(double x, double y, double z)
    {
        double cx = Math.max(0, Math.min(rx - 1.001, x));
        double cy = Math.max(0, Math.min(ry - 1.001, y));
        double cz = Math.max(0, Math.min(rz - 1.001, z));
        int x0 = (int) cx;
        int y0 = (int) cy;
        int z0 = (int) cz;
        double fx = cx - x0;
        double fy = cy - y0;
        double fz = cz - z0;
        int x1 = Math.min(rx - 1, x0 + 1);
        int y1 = Math.min(ry - 1, y0 + 1);
        int z1 = Math.min(rz - 1, z0 + 1);
        double c00 = lerp(g(x0, y0, z0), g(x1, y0, z0), fx);
        double c10 = lerp(g(x0, y1, z0), g(x1, y1, z0), fx);
        double c01 = lerp(g(x0, y0, z1), g(x1, y0, z1), fx);
        double c11 = lerp(g(x0, y1, z1), g(x1, y1, z1), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /**
     * @param (int) i x
     * @param (int) j y
     * @param (int) k z
     * @return smoothed grey at a model voxel
     */
    private int g(int i, int j, int k)
    {
        return smooth[i + rx * (j + ry * k)] & 0xFF;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * @param (double) a value at 0
     * @param (double) b value at 1
     * @param (double) t weight
     * @return a + (b - a) t
     */
    private static double lerp(double a, double b, double t)
    {
        return a + (b - a) * t;
    }

    /**
     * Separable 1-2-1 smoothing along x, y and z, in place.
     *
     * @param (byte[]) v grey values
     * @param (int) nx size x
     * @param (int) ny size y
     * @param (int) nz size z
     */
    private static void smooth121(byte[] v, int nx, int ny, int nz)
    {
        int[] line = new int[Math.max(nx, Math.max(ny, nz))];
        int[] strides = {1, nx, nx * ny};
        int[] sizes = {nx, ny, nz};
        for (int axis = 0; axis < 3; axis++)
        {
            int n = sizes[axis];
            if (n < 3)
            {
                continue;
            }
            int stride = strides[axis];
            int total = v.length;
            for (int start = 0; start < total; start++)
            {
                // Visit each line once: start must be the first voxel of a line along this axis.
                int coord = (start / stride) % n;
                if (coord != 0)
                {
                    continue;
                }
                for (int t = 0; t < n; t++)
                {
                    line[t] = v[start + t * stride] & 0xFF;
                }
                for (int t = 0; t < n; t++)
                {
                    int a = line[Math.max(0, t - 1)];
                    int b = line[t];
                    int c = line[Math.min(n - 1, t + 1)];
                    v[start + t * stride] = (byte) ((a + 2 * b + c + 2) / 4);
                }
            }
        }
    }

    /**
     * Otsu's method on the grey histogram: the split that best separates two classes.
     *
     * @param (byte[]) v grey values
     * @return threshold grey level
     */
    static int otsu(byte[] v)
    {
        long[] hist = new long[GREY_LEVELS];
        for (int i = 0; i < v.length; i++)
        {
            hist[v[i] & 0xFF]++;
        }
        long total = v.length;
        double sumAll = 0;
        for (int t = 0; t < GREY_LEVELS; t++)
        {
            sumAll += t * (double) hist[t];
        }
        double sumBack = 0;
        long weightBack = 0;
        double best = -1;
        int bestT = GREY_LEVELS / 4;
        for (int t = 0; t < GREY_LEVELS - 1; t++)
        {
            weightBack += hist[t];
            if (weightBack == 0)
            {
                continue;
            }
            long weightFore = total - weightBack;
            if (weightFore == 0)
            {
                break;
            }
            sumBack += t * (double) hist[t];
            double meanBack = sumBack / weightBack;
            double meanFore = (sumAll - sumBack) / weightFore;
            double between = (double) weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore);
            if (between > best)
            {
                best = between;
                bestT = t + 1;
            }
        }
        return Math.max(1, bestT);
    }

    /** A growable ring buffer of ints (breadth-first searches over millions of voxels). */
    static final class IntQueue
    {
        private int[] data = new int[1 << 16];
        private int head;
        private int count;

        /** Empties the queue. */
        void clear()
        {
            head = 0;
            count = 0;
        }

        /**
         * @return true when empty
         */
        boolean isEmpty()
        {
            return count == 0;
        }

        /**
         * @return number of elements
         */
        int size()
        {
            return count;
        }

        /**
         * @param (int) i position from the head
         * @return the element
         */
        int get(int i)
        {
            return data[(head + i) & (data.length - 1)];
        }

        /**
         * @param (int) value element to append
         */
        void add(int value)
        {
            if (count == data.length)
            {
                int[] bigger = new int[data.length * 2];
                for (int i = 0; i < count; i++)
                {
                    bigger[i] = get(i);
                }
                data = bigger;
                head = 0;
            }
            data[(head + count) & (data.length - 1)] = value;
            count++;
        }

        /**
         * @return the first element, removed
         */
        int poll()
        {
            int v = data[head];
            head = (head + 1) & (data.length - 1);
            count--;
            return v;
        }
    }
}
