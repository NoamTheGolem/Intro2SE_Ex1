package ex1.core;

/**
 * Volume of 8-bit voxels (signed or unsigned).
 */
public final class ByteVolume extends Volume
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The samples, and whether they are read as unsigned. */
    private final byte[]  data;
    private final boolean unsigned;

    /**
     * Creates an 8-bit volume filled with 0.
     * @param (int) sx Size along x.
     * @param (int) sy Size along y.
     * @param (int) sz Size along z.
     * @param (VoxelType) type INT8 or UINT8.
     */
    ByteVolume(int sx, int sy, int sz, VoxelType type)
    {
        super(sx, sy, sz, type);
        this.data = new byte[sx * sy * sz];
        this.unsigned = !type.isSigned();
    }

    /**
     * Gives direct access to the samples.
     * @return (byte[]) Backing array (x fastest), for fast bulk loading.
     */
    public byte[] data()
    {
        return data;
    }

    /**
     * Reads one sample (an unsigned type as 0..255).
     * @param (int) index Flat index.
     * @return (float) The value.
     */
    @Override
    public float valueAt(int index)
    {
        if (unsigned)
        {
            return data[index] & 0xFF;
        }
        return data[index];
    }

    /**
     * Stores one sample, cut to 8 bits.
     * @param (int) index Flat index.
     * @param (double) value The value.
     */
    @Override
    public void setValue(int index, double value)
    {
        data[index] = (byte) (long) value;
    }
}
