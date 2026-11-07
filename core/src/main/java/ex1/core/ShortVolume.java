package ex1.core;

/**
 * Volume of 16-bit voxels (signed or unsigned).
 */
public final class ShortVolume extends Volume
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The samples, and whether they are read as unsigned. */
    private final short[] data;
    private final boolean unsigned;

    /**
     * Creates a 16-bit volume filled with 0.
     * @param (int) sx Size along x.
     * @param (int) sy Size along y.
     * @param (int) sz Size along z.
     * @param (VoxelType) type INT16 or UINT16.
     */
    ShortVolume(int sx, int sy, int sz, VoxelType type)
    {
        super(sx, sy, sz, type);
        this.data = new short[sx * sy * sz];
        this.unsigned = !type.isSigned();
    }

    /**
     * Gives direct access to the samples.
     * @return (short[]) Backing array (x fastest), for fast bulk loading.
     */
    public short[] data()
    {
        return data;
    }

    /**
     * Reads one sample (an unsigned type as 0..65535).
     * @param (int) index Flat index.
     * @return (float) The value.
     */
    @Override
    public float valueAt(int index)
    {
        if (unsigned)
        {
            return data[index] & 0xFFFF;
        }
        return data[index];
    }

    /**
     * Stores one sample, cut to 16 bits.
     * @param (int) index Flat index.
     * @param (double) value The value.
     */
    @Override
    public void setValue(int index, double value)
    {
        data[index] = (short) (long) value;
    }
}
