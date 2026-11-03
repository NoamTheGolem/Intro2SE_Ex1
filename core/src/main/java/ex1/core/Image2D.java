package ex1.core;

/**
 * Projection result: a width*height grid of float samples, row-major
 * (pixel (x, y) is at index x + width * y).
 */
public final class Image2D
{
    private final int width;
    private final int height;
    private final float[] pixels;

    /**
     * Creates an image filled with 0.
     *
     * @param (int) width number of columns
     * @param (int) height number of rows
     */
    public Image2D(int width, int height)
    {
        if (width < 1 || height < 1)
        {
            throw new IllegalArgumentException("image size must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.pixels = new float[width * height];
    }

    /**
     * @param (int) x column
     * @param (int) y row
     * @return pixel value
     */
    public float get(int x, int y)
    {
        return pixels[x + width * y];
    }

    /**
     * @return number of columns
     */
    public int width()
    {
        return width;
    }

    /**
     * @return number of rows
     */
    public int height()
    {
        return height;
    }

    /**
     * @return backing array (row-major), shared, not copied
     */
    public float[] pixels()
    {
        return pixels;
    }
}
