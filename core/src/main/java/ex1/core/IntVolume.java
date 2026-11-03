package ex1.core;

/**
 * Volume of 32-bit integer voxels (signed or unsigned).
 */
public final class IntVolume extends Volume
{
    private static final long UINT32_MASK = 0xFFFFFFFFL;

    private final int[] data;
    private final boolean unsigned;

    /**
     * @param (int) sx size along x
     * @param (int) sy size along y
     * @param (int) sz size along z
     * @param (VoxelType) type INT32 or UINT32
     */
    IntVolume(int sx, int sy, int sz, VoxelType type)
    {
        super(sx, sy, sz, type);
        this.data = new int[sx * sy * sz];
        this.unsigned = !type.isSigned();
    }

    /**
     * @return backing array (x fastest), for fast bulk loading
     */
    public int[] data()
    {
        return data;
    }

    @Override
    public float valueAt(int index)
    {
        if (unsigned)
        {
            return data[index] & UINT32_MASK;
        }
        return data[index];
    }

    @Override
    public void setValue(int index, double value)
    {
        data[index] = (int) (long) value;
    }
}
