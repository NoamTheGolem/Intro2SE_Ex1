package ex1.core;

/**
 * Volume of 8-bit voxels (signed or unsigned).
 */
public final class ByteVolume extends Volume
{
    private final byte[] data;
    private final boolean unsigned;

    /**
     * @param (int) sx size along x
     * @param (int) sy size along y
     * @param (int) sz size along z
     * @param (VoxelType) type INT8 or UINT8
     */
    ByteVolume(int sx, int sy, int sz, VoxelType type)
    {
        super(sx, sy, sz, type);
        this.data = new byte[sx * sy * sz];
        this.unsigned = !type.isSigned();
    }

    /**
     * @return backing array (x fastest), for fast bulk loading
     */
    public byte[] data()
    {
        return data;
    }

    @Override
    public float valueAt(int index)
    {
        if (unsigned)
        {
            return data[index] & 0xFF;
        }
        return data[index];
    }

    @Override
    public void setValue(int index, double value)
    {
        data[index] = (byte) (long) value;
    }
}
