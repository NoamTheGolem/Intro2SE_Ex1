package ex1.core;

/**
 * Immutable 3D vector / point with double precision.
 * Used for plane geometry only; the hot projection loop works on raw doubles.
 */
public final class Vec3
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The coordinates. */
    public final double x;
    public final double y;
    public final double z;

    /**
     * Creates a vector.
     * @param (double) x X component.
     * @param (double) y Y component.
     * @param (double) z Z component.
     */
    public Vec3(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Adds a vector.
     * @param (Vec3) o Other vector.
     * @return (Vec3) This + o.
     */
    public Vec3 add(Vec3 o)
    {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    /**
     * Subtracts a vector.
     * @param (Vec3) o Other vector.
     * @return (Vec3) This - o.
     */
    public Vec3 sub(Vec3 o)
    {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    /**
     * Multiplies by a number.
     * @param (double) k Scale factor.
     * @return (Vec3) This * k.
     */
    public Vec3 scale(double k)
    {
        return new Vec3(x * k, y * k, z * k);
    }

    /**
     * Computes the dot product.
     * @param (Vec3) o Other vector.
     * @return (double) Dot product.
     */
    public double dot(Vec3 o)
    {
        return x * o.x + y * o.y + z * o.z;
    }

    /**
     * Computes the cross product.
     * @param (Vec3) o Other vector.
     * @return (Vec3) Cross product this × o.
     */
    public Vec3 cross(Vec3 o)
    {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    /**
     * Computes the length.
     * @return (double) Euclidean length.
     */
    public double length()
    {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Computes the distance to another point.
     * @param (Vec3) o Other point.
     * @return (double) Distance between this and o.
     */
    public double dist(Vec3 o)
    {
        return sub(o).length();
    }

    /**
     * Gives the unit vector in the same direction; the zero vector stays zero
     * (degenerate rows of a triangle must not produce NaN).
     * @return (Vec3) Normalized copy.
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

    /**
     * Describes the vector for messages.
     * @return (String) "(x, y, z)".
     */
    @Override
    public String toString()
    {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
