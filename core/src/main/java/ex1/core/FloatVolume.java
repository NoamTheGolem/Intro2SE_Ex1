package ex1.core;

/**
 * Volume of 32-bit float voxels.
 */
public final class FloatVolume extends Volume
{
    private final float[] data;

    /**
     * @param (int) sx size along x
     * @param (int) sy size along y
     * @param (int) sz size along z
     */
    FloatVolume(int sx, int sy, int sz)
    {
        super(sx, sy, sz, VoxelType.FLOAT32);
        this.data = new float[sx * sy * sz];
    }

    /**
     * @return backing array (x fastest), for fast bulk loading
     */
    public float[] data()
    {
        return data;
    }

    @Override
    public float valueAt(int index)
    {
        return data[index];
    }

    @Override
    public void setValue(int index, double value)
    {
        data[index] = (float) value;
    }
}
