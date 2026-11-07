package ex1.core;

/**
 * Projection result: a width*height grid of float samples, row-major
 * (pixel (x, y) is at index x + width * y).
 */
public final class Image2D
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Size and samples (row-major). */
    private final int     width;
    private final int     height;
    private final float[] pixels;

    /**
     * Creates an image filled with 0.
     * @param (int) width Number of columns.
     * @param (int) height Number of rows.
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
     * Reads one pixel.
     * @param (int) x Column.
     * @param (int) y Row.
     * @return (float) Pixel value.
     */
    public float get(int x, int y)
    {
        return pixels[x + width * y];
    }

    /**
     * Gives the width.
     * @return (int) Number of columns.
     */
    public int width()
    {
        return width;
    }

    /**
     * Gives the height.
     * @return (int) Number of rows.
     */
    public int height()
    {
        return height;
    }

    /**
     * Gives direct access to the pixels.
     * @return (float[]) Backing array (row-major), shared, not copied.
     */
    public float[] pixels()
    {
        return pixels;
    }
}
