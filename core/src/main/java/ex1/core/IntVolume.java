package ex1.core;

/**
 * Volume of 32-bit integer voxels (signed or unsigned).
 */
public final class IntVolume extends Volume
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The low 32 bits, to read an int as unsigned. */
    private static final long UINT32_MASK = 0xFFFFFFFFL;

    /** The samples, and whether they are read as unsigned. */
    private final int[]   data;
    private final boolean unsigned;

    /**
     * Creates a 32-bit volume filled with 0.
     * @param (int) sx Size along x.
     * @param (int) sy Size along y.
     * @param (int) sz Size along z.
     * @param (VoxelType) type INT32 or UINT32.
     */
    IntVolume(int sx, int sy, int sz, VoxelType type)
    {
        super(sx, sy, sz, type);
        this.data = new int[sx * sy * sz];
        this.unsigned = !type.isSigned();
    }

    /**
     * Gives direct access to the samples.
     * @return (int[]) Backing array (x fastest), for fast bulk loading.
     */
    public int[] data()
    {
        return data;
    }

    /**
     * Reads one sample (an unsigned type as 0..2^32-1).
     * @param (int) index Flat index.
     * @return (float) The value.
     */
    @Override
    public float valueAt(int index)
    {
        if (unsigned)
        {
            return data[index] & UINT32_MASK;
        }
        return data[index];
    }

    /**
     * Stores one sample, cut to 32 bits.
     * @param (int) index Flat index.
     * @param (double) value The value.
     */
    @Override
    public void setValue(int index, double value)
    {
        data[index] = (int) (long) value;
    }
}
