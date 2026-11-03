package ex1.app;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Tiny software renderer: z-buffered textured triangles and depth-tested lines into an ARGB
 * image. Enough to draw a volume's slices and the cutting plane in 3-D without any graphics
 * library (keeps the app dependency-free and portable).
 */
final class Raster
{
    /** A screen-space vertex: position, depth (smaller = nearer) and texture coordinates. */
    static final class Vertex
    {
        final double x;
        final double y;
        final double z;
        final double u;
        final double v;

        /**
         * @param (double) x screen x
         * @param (double) y screen y
         * @param (double) z depth
         * @param (double) u texture column
         * @param (double) v texture row
         */
        Vertex(double x, double y, double z, double u, double v)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    /** An ARGB texture. */
    static final class Texture
    {
        final int width;
        final int height;
        final int[] argb;

        /**
         * @param (int) width columns
         * @param (int) height rows
         * @param (int[]) argb pixels, row-major
         */
        Texture(int width, int height, int[] argb)
        {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }

        /**
         * @param (byte[]) grey grey pixels
         * @param (int) width columns
         * @param (int) height rows
         * @param (int) tint colour mixed into the grey (0 = none), format 0xRRGGBB
         * @param (double) tintAmount 0..1
         * @return opaque texture
         */
        static Texture fromGrey(byte[] grey, int width, int height, int tint, double tintAmount)
        {
            int[] px = new int[width * height];
            int tr = (tint >> 16) & 0xFF;
            int tg = (tint >> 8) & 0xFF;
            int tb = tint & 0xFF;
            for (int i = 0; i < px.length; i++)
            {
                int g = grey[i] & 0xFF;
                int r = (int) (g * (1 - tintAmount) + tr * tintAmount * (0.35 + 0.65 * g / 255.0));
                int gg = (int) (g * (1 - tintAmount) + tg * tintAmount * (0.35 + 0.65 * g / 255.0));
                int b = (int) (g * (1 - tintAmount) + tb * tintAmount * (0.35 + 0.65 * g / 255.0));
                px[i] = 0xFF000000 | (clamp(r) << 16) | (clamp(gg) << 8) | clamp(b);
            }
            return new Texture(width, height, px);
        }

        /**
         * @param (int) c component
         * @return clamped to 0..255
         */
        private static int clamp(int c)
        {
            return Math.max(0, Math.min(255, c));
        }
    }

    private static final double LINE_DEPTH_BIAS = 0.5;

    private final int width;
    private final int height;
    private final BufferedImage image;
    private final int[] pixels;
    private final float[] depth;

    /**
     * @param (int) width image width
     * @param (int) height image height
     */
    Raster(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        this.depth = new float[width * height];
    }

    /**
     * @param (int) background ARGB fill colour
     */
    void clear(int background)
    {
        Arrays.fill(pixels, background);
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
    }

    /**
     * @return the rendered image (shared buffer)
     */
    BufferedImage image()
    {
        return image;
    }

    /**
     * @return width
     */
    int width()
    {
        return width;
    }

    /**
     * @return height
     */
    int height()
    {
        return height;
    }

    /**
     * Draws a textured triangle with a z-test. Texture coordinates are in texels; samples
     * outside the texture are skipped (so a trapezoid strip can address only its own span).
     *
     * @param (Vertex) a first vertex
     * @param (Vertex) b second vertex
     * @param (Vertex) c third vertex
     * @param (Texture) tex texture
     * @param (double) alpha opacity 0..1
     */
    void triangle(Vertex a, Vertex b, Vertex c, Texture tex, double alpha)
    {
        int minX = (int) Math.max(0, Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int minY = (int) Math.max(0, Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        if (minX > maxX || minY > maxY)
        {
            return;
        }
        double area = edge(a, b, c.x, c.y);
        if (Math.abs(area) < 1e-9)
        {
            return;
        }
        double inv = 1.0 / area;
        int ia = (int) Math.round(alpha * 255);
        for (int y = minY; y <= maxY; y++)
        {
            double py = y + 0.5;
            int row = y * width;
            for (int x = minX; x <= maxX; x++)
            {
                double px = x + 0.5;
                double w0 = edge(b, c, px, py) * inv;
                double w1 = edge(c, a, px, py) * inv;
                double w2 = edge(a, b, px, py) * inv;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6)
                {
                    continue;
                }
                float z = (float) (w0 * a.z + w1 * b.z + w2 * c.z);
                int i = row + x;
                if (z >= depth[i])
                {
                    continue;
                }
                int tu = (int) (w0 * a.u + w1 * b.u + w2 * c.u);
                int tv = (int) (w0 * a.v + w1 * b.v + w2 * c.v);
                if (tu < 0 || tv < 0 || tu >= tex.width || tv >= tex.height)
                {
                    continue;
                }
                int colour = tex.argb[tv * tex.width + tu];
                if (ia >= 255)
                {
                    pixels[i] = colour;
                }
                else
                {
                    pixels[i] = blend(pixels[i], colour, ia);
                }
                depth[i] = z;
            }
        }
    }

    /**
     * Draws a solid-colour triangle (no texture) with a z-test.
     *
     * @param (Vertex) a first vertex
     * @param (Vertex) b second vertex
     * @param (Vertex) c third vertex
     * @param (int) argb colour
     * @param (double) alpha opacity 0..1
     */
    void solidTriangle(Vertex a, Vertex b, Vertex c, int argb, double alpha)
    {
        int[] one = {argb};
        Texture tex = new Texture(1, 1, one);
        triangle(new Vertex(a.x, a.y, a.z, 0.5, 0.5), new Vertex(b.x, b.y, b.z, 0.5, 0.5),
                new Vertex(c.x, c.y, c.z, 0.5, 0.5), tex, alpha);
    }

    /**
     * Depth-tested line (Bresenham-free DDA), slightly biased towards the viewer so edges drawn
     * on a surface stay visible.
     *
     * @param (Vertex) a start
     * @param (Vertex) b end
     * @param (int) argb colour
     * @param (int) thickness 1 or 2
     * @param (boolean) depthTest false draws over everything
     */
    void line(Vertex a, Vertex b, int argb, int thickness, boolean depthTest)
    {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps == 0)
        {
            steps = 1;
        }
        for (int s = 0; s <= steps; s++)
        {
            double t = s / (double) steps;
            int x = (int) Math.round(a.x + dx * t);
            int y = (int) Math.round(a.y + dy * t);
            float z = (float) (a.z + (b.z - a.z) * t - LINE_DEPTH_BIAS);
            for (int oy = 0; oy < thickness; oy++)
            {
                for (int ox = 0; ox < thickness; ox++)
                {
                    plot(x + ox, y + oy, z, argb, depthTest);
                }
            }
        }
    }

    /**
     * @param (int) x column
     * @param (int) y row
     * @param (float) z depth
     * @param (int) argb colour
     * @param (boolean) depthTest whether to respect the z-buffer
     */
    private void plot(int x, int y, float z, int argb, boolean depthTest)
    {
        if (x < 0 || y < 0 || x >= width || y >= height)
        {
            return;
        }
        int i = y * width + x;
        if (depthTest && z >= depth[i])
        {
            return;
        }
        pixels[i] = argb;
        if (depthTest)
        {
            depth[i] = z;
        }
    }

    /**
     * @param (Vertex) a edge start
     * @param (Vertex) b edge end
     * @param (double) px point x
     * @param (double) py point y
     * @return signed doubled area of (a, b, p)
     */
    private static double edge(Vertex a, Vertex b, double px, double py)
    {
        return (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x);
    }

    /**
     * @param (int) dst destination ARGB
     * @param (int) src source ARGB
     * @param (int) alpha 0..255 weight of src
     * @return blended colour
     */
    private static int blend(int dst, int src, int alpha)
    {
        int ia = 255 - alpha;
        int r = (((src >> 16) & 0xFF) * alpha + ((dst >> 16) & 0xFF) * ia) / 255;
        int g = (((src >> 8) & 0xFF) * alpha + ((dst >> 8) & 0xFF) * ia) / 255;
        int b = ((src & 0xFF) * alpha + (dst & 0xFF) * ia) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
