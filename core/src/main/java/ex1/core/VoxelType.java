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

    private final int bytes;
    private final boolean signed;

    /**
     * @param (int) bytes size of one voxel in bytes
     * @param (boolean) signed whether the integer type is signed
     */
    VoxelType(int bytes, boolean signed)
    {
        this.bytes = bytes;
        this.signed = signed;
    }

    /**
     * @return size of one voxel in bytes
     */
    public int bytes()
    {
        return bytes;
    }

    /**
     * @return true for signed types (and float)
     */
    public boolean isSigned()
    {
        return signed;
    }
}
