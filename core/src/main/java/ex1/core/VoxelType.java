package ex1.core;

/**
 * Storage type of one voxel. Determines which {@link Volume} subclass holds the data.
 */
public enum VoxelType
{
    INT8(1, true),
    UINT8(1, false),
    INT16(2, true),
    UINT16(2, false),
    INT32(4, true),
    UINT32(4, false),
    FLOAT32(4, true);

    /** Bytes per voxel, and whether the values can be negative. */
    private final int     bytes;
    private final boolean signed;

    /**
     * Creates a type.
     * @param (int) bytes Size of one voxel in bytes.
     * @param (boolean) signed Whether the integer type is signed.
     */
    VoxelType(int bytes, boolean signed)
    {
        this.bytes = bytes;
        this.signed = signed;
    }

    /**
     * Gives the size of one voxel.
     * @return (int) Size of one voxel in bytes.
     */
    public int bytes()
    {
        return bytes;
    }

    /**
     * Tells whether the values can be negative.
     * @return (boolean) True for signed types (and float).
     */
    public boolean isSigned()
    {
        return signed;
    }
}
