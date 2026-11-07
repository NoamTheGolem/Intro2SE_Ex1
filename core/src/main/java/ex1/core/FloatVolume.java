package ex1.core;

/**
 * Volume of 32-bit float voxels.
 */
public final class FloatVolume extends Volume
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The samples. */
    private final float[] data;

    /**
     * Creates a float volume filled with 0.
     * @param (int) sx Size along x.
     * @param (int) sy Size along y.
     * @param (int) sz Size along z.
     */
    FloatVolume(int sx, int sy, int sz)
    {
        super(sx, sy, sz, VoxelType.FLOAT32);
        this.data = new float[sx * sy * sz];
    }

    /**
     * Gives direct access to the samples.
     * @return (float[]) Backing array (x fastest), for fast bulk loading.
     */
    public float[] data()
    {
        return data;
    }

    /**
     * Reads one sample.
     * @param (int) index Flat index.
     * @return (float) The value.
     */
    @Override
    public float valueAt(int index)
    {
        return data[index];
    }

    /**
     * Stores one sample as a float.
     * @param (int) index Flat index.
     * @param (double) value The value.
     */
    @Override
    public void setValue(int index, double value)
    {
        data[index] = (float) value;
    }
}
