package ex1.core;

/**
 * Volume of 16-bit voxels (signed or unsigned).
 */
public final class ShortVolume extends Volume
{
    private final short[] data;
    private final boolean unsigned;

    /**
     * @param (int) sx size along x
     * @param (int) sy size along y
     * @param (int) sz size along z
     * @param (VoxelType) type INT16 or UINT16
     */
    ShortVolume(int sx, int sy, int sz, VoxelType type)
    {
        super(sx, sy, sz, type);
        this.data = new short[sx * sy * sz];
        this.unsigned = !type.isSigned();
    }

    /**
     * @return backing array (x fastest), for fast bulk loading
     */
    public short[] data()
    {
        return data;
    }

    @Override
    public float valueAt(int index)
    {
        if (unsigned)
        {
            return data[index] & 0xFFFF;
        }
        return data[index];
    }

    @Override
    public void setValue(int index, double value)
    {
        data[index] = (short) (long) value;
    }
}
