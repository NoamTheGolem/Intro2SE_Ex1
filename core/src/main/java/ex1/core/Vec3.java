package ex1.core;

/**
 * Immutable 3D vector / point with double precision.
 * Used for plane geometry only; the hot projection loop works on raw doubles.
 */
public final class Vec3
{
    public final double x;
    public final double y;
    public final double z;

    /**
     * Creates a vector.
     *
     * @param (double) x x component
     * @param (double) y y component
     * @param (double) z z component
     */
    public Vec3(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * @param (Vec3) o other vector
     * @return this + o
     */
    public Vec3 add(Vec3 o)
    {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    /**
     * @param (Vec3) o other vector
     * @return this - o
     */
    public Vec3 sub(Vec3 o)
    {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    /**
     * @param (double) k scale factor
     * @return this * k
     */
    public Vec3 scale(double k)
    {
        return new Vec3(x * k, y * k, z * k);
    }

    /**
     * @param (Vec3) o other vector
     * @return dot product
     */
    public double dot(Vec3 o)
    {
        return x * o.x + y * o.y + z * o.z;
    }

    /**
     * @param (Vec3) o other vector
     * @return cross product this × o
     */
    public Vec3 cross(Vec3 o)
    {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    /**
     * @return Euclidean length
     */
    public double length()
    {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * @param (Vec3) o other point
     * @return distance between this and o
     */
    public double dist(Vec3 o)
    {
        return sub(o).length();
    }

    /**
     * Unit vector in the same direction; the zero vector stays zero
     * (degenerate rows of a triangle must not produce NaN).
     *
     * @return normalized copy
     */
    public Vec3 normalized()
    {
        double len = length();
        if (len == 0.0)
        {
            return this;
        }
        return scale(1.0 / len);
    }

    @Override
    public String toString()
    {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
