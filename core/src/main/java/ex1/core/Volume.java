package ex1.core;

/**
 * A 3D raster M[x][y][z] of size sx*sy*sz, stored as one flat array with x varying fastest:
 * index = x + sx * (y + sy * z). This is the NRRD on-disk order, so loading needs no reshuffle.
 *
 * Subclasses hold the samples in their native width (1, 2 or 4 bytes), so a 500^3 8-bit
 * volume costs 125 MB, not 500 MB.
 */
public abstract class Volume
{
    public static final int MAX_SIZE = 1024;

    protected final int sx;
    protected final int sy;
    protected final int sz;
    private final VoxelType type;

    /** Name of the file's physical space (e.g. "left-posterior-superior"), or null. */
    private String space;
    /** Physical direction (with length = spacing) of one step along x, y, z; null when unknown. */
    private double[][] spaceDirections;

    /**
     * @param (int) sx size along x
     * @param (int) sy size along y
     * @param (int) sz size along z
     * @param (VoxelType) type storage type
     */
    protected Volume(int sx, int sy, int sz, VoxelType type)
    {
        checkSize(sx, "sx");
        checkSize(sy, "sy");
        checkSize(sz, "sz");
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.type = type;
    }

    /**
     * Allocates an empty (all zero) volume of the matching subclass.
     *
     * @param (VoxelType) type storage type
     * @param (int) sx size along x
     * @param (int) sy size along y
     * @param (int) sz size along z
     * @return new volume
     */
    public static Volume create(VoxelType type, int sx, int sy, int sz)
    {
        switch (type)
        {
            case INT8:
            case UINT8:
                return new ByteVolume(sx, sy, sz, type);
            case INT16:
            case UINT16:
                return new ShortVolume(sx, sy, sz, type);
            case INT32:
            case UINT32:
                return new IntVolume(sx, sy, sz, type);
            case FLOAT32:
                return new FloatVolume(sx, sy, sz);
            default:
                throw new IllegalArgumentException("Unknown voxel type " + type);
        }
    }

    /**
     * Value of the voxel at a flat index, converted to float
     * (unsigned types are read as unsigned).
     *
     * @param (int) index flat index, must be in [0, voxelCount())
     * @return voxel value
     */
    public abstract float valueAt(int index);

    /**
     * Stores a value at a flat index (used by generators and tests; readers fill the
     * typed arrays directly for speed).
     *
     * @param (int) index flat index
     * @param (double) value value, truncated to the storage type
     */
    public abstract void setValue(int index, double value);

    /**
     * @param (int) x x index
     * @param (int) y y index
     * @param (int) z z index
     * @return voxel value, or 0 when (x, y, z) is outside the volume
     */
    public float get(int x, int y, int z)
    {
        if (!contains(x, y, z))
        {
            return 0f;
        }
        return valueAt(index(x, y, z));
    }

    /**
     * @param (int) x x index
     * @param (int) y y index
     * @param (int) z z index
     * @return flat index of (x, y, z)
     */
    public int index(int x, int y, int z)
    {
        return x + sx * (y + sy * z);
    }

    /**
     * @param (int) x x index
     * @param (int) y y index
     * @param (int) z z index
     * @return true when the index lies inside the volume
     */
    public boolean contains(int x, int y, int z)
    {
        return x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz;
    }

    /**
     * @return size along x
     */
    public int sizeX()
    {
        return sx;
    }

    /**
     * @return size along y
     */
    public int sizeY()
    {
        return sy;
    }

    /**
     * @return size along z
     */
    public int sizeZ()
    {
        return sz;
    }

    /**
     * @return total number of voxels
     */
    public int voxelCount()
    {
        return sx * sy * sz;
    }

    /**
     * @return storage type
     */
    public VoxelType type()
    {
        return type;
    }

    @Override
    public String toString()
    {
        return sx + "x" + sy + "x" + sz + " " + type;
    }

    /**
     * @param (int) s size to check
     * @param (String) name axis name for the error message
     */
    private static void checkSize(int s, String name)
    {
        if (s < 1 || s >= MAX_SIZE)
        {
            throw new IllegalArgumentException(name + " must be in [1, " + MAX_SIZE + "), got " + s);
        }
    }

    /**
     * Physical geometry from the file header (NRRD "space" and "space directions"). Only the
     * viewer uses it (orientation labels, voxel aspect); the projection works in voxel indices,
     * as the assignment defines it.
     *
     * @param (String) spaceName e.g. "left-posterior-superior", or null
     * @param (double[][]) directions 3 vectors of 3 numbers (one per index axis), or null
     */
    public void setSpace(String spaceName, double[][] directions)
    {
        this.space = spaceName;
        if (directions == null)
        {
            this.spaceDirections = null;
            return;
        }
        this.spaceDirections = new double[3][3];
        for (int i = 0; i < 3; i++)
        {
            System.arraycopy(directions[i], 0, this.spaceDirections[i], 0, 3);
        }
    }

    /**
     * @return the physical space name, or null when the file did not give one
     */
    public String space()
    {
        return space;
    }

    /**
     * @return a copy of the per-axis physical step vectors, or null when unknown
     */
    public double[][] spaceDirections()
    {
        if (spaceDirections == null)
        {
            return null;
        }
        double[][] copy = new double[3][3];
        for (int i = 0; i < 3; i++)
        {
            System.arraycopy(spaceDirections[i], 0, copy[i], 0, 3);
        }
        return copy;
    }
}
