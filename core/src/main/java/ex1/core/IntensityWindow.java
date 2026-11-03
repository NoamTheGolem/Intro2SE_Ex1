package ex1.core;

/**
 * Linear map from voxel values to 8-bit grey: lo -> 0, hi -> 255, clamped (decision D7).
 * One window is used for a whole video so the same tissue keeps the same grey in every frame.
 */
public final class IntensityWindow
{
    public static final double DEFAULT_LOW_PERCENTILE = 0.5;
    public static final double DEFAULT_HIGH_PERCENTILE = 99.5;

    private static final int MAX_SAMPLES = 2_000_000;
    private static final int FEW_VALUES = 16;
    private static final int BINS = 4096;
    private static final int GREY_MAX = 255;

    private final double lo;
    private final double hi;
    private final double scale;

    /**
     * @param (double) lo value mapped to black
     * @param (double) hi value mapped to white (must be > lo)
     */
    public IntensityWindow(double lo, double hi)
    {
        if (!(hi > lo))
        {
            throw new IllegalArgumentException("window must have hi > lo, got [" + lo + ", " + hi + "]");
        }
        this.lo = lo;
        this.hi = hi;
        this.scale = GREY_MAX / (hi - lo);
    }

    /**
     * Robust window from percentiles of the volume. At most MAX_SAMPLES voxels are visited
     * (evenly strided), so this stays fast on 1000^3 volumes.
     *
     * @param (Volume) volume source volume
     * @param (double) lowPercentile percentile mapped to black, e.g. 0.5
     * @param (double) highPercentile percentile mapped to white, e.g. 99.5
     * @return the window
     */
    public static IntensityWindow fromPercentiles(Volume volume, double lowPercentile, double highPercentile)
    {
        int count = volume.voxelCount();
        int stride = Math.max(1, count / MAX_SAMPLES);

        // Pass 1: range of the sampled values.
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int samples = 0;
        for (int i = 0; i < count; i += stride)
        {
            double v = volume.valueAt(i);
            if (v < min)
            {
                min = v;
            }
            if (v > max)
            {
                max = v;
            }
            samples++;
        }
        if (!(max > min))
        {
            // Constant volume: any window containing the value shows it as black, avoid div by 0.
            return new IntensityWindow(min, min + 1.0);
        }

        // Pass 2: histogram, then walk it to the requested percentiles.
        long[] hist = new long[BINS];
        double binWidth = (max - min) / BINS;
        for (int i = 0; i < count; i += stride)
        {
            int bin = (int) ((volume.valueAt(i) - min) / binWidth);
            if (bin >= BINS)
            {
                bin = BINS - 1;
            }
            hist[bin]++;
        }
        if (occupiedBins(hist) <= FEW_VALUES)
        {
            // Synthetic or labelled data (a die, phantoms): a handful of values that must all stay
            // distinguishable, so the whole range is used. Percentiles would map a small bright
            // class (the pips of a die) to the same white as the large class around it.
            return new IntensityWindow(min, max);
        }
        double lo = valueAtPercentile(hist, samples, lowPercentile, min, binWidth);
        double hi = valueAtPercentile(hist, samples, highPercentile, min, binWidth) + binWidth;
        if (!(hi > lo))
        {
            hi = lo + binWidth;
        }
        return new IntensityWindow(lo, hi);
    }

    /**
     * Writes an image into a grey frame, centred; frame pixels outside the image stay unchanged.
     *
     * @param (Image2D) image projected image
     * @param (byte[]) frame destination, frameWidth*frameHeight unsigned grey bytes
     * @param (int) frameWidth frame columns
     * @param (int) frameHeight frame rows
     * @param (int) imageWidth number of image columns to copy (the plane's width)
     * @param (int) imageHeight number of image rows to copy (the plane's height)
     */
    public void toGrey(Image2D image, byte[] frame, int frameWidth, int frameHeight,
                       int imageWidth, int imageHeight)
    {
        int ox = (frameWidth - imageWidth) / 2;
        int oy = (frameHeight - imageHeight) / 2;
        float[] src = image.pixels();
        int srcStride = image.width();
        for (int y = 0; y < imageHeight; y++)
        {
            int s = y * srcStride;
            int d = (y + oy) * frameWidth + ox;
            for (int x = 0; x < imageWidth; x++)
            {
                frame[d + x] = (byte) grey(src[s + x]);
            }
        }
    }

    /**
     * @param (double) value voxel value
     * @return grey level in [0, 255]
     */
    public int grey(double value)
    {
        double g = (value - lo) * scale;
        if (g <= 0.0)
        {
            return 0;
        }
        if (g >= GREY_MAX)
        {
            return GREY_MAX;
        }
        return (int) (g + 0.5);
    }

    /**
     * @return value mapped to black
     */
    public double low()
    {
        return lo;
    }

    /**
     * @return value mapped to white
     */
    public double high()
    {
        return hi;
    }

    @Override
    public String toString()
    {
        return String.format("[%.2f, %.2f]", lo, hi);
    }

    /**
     * @param (long[]) hist histogram
     * @param (int) total number of samples in the histogram
     * @param (double) percentile 0..100
     * @param (double) min value at the start of bin 0
     * @param (double) binWidth width of one bin
     * @return value at the start of the bin where the cumulative count reaches the percentile
     */
    private static double valueAtPercentile(long[] hist, int total, double percentile,
                                            double min, double binWidth)
    {
        double target = total * percentile / 100.0;
        long cumulative = 0;
        for (int bin = 0; bin < hist.length; bin++)
        {
            cumulative += hist[bin];
            if (cumulative >= target)
            {
                return min + bin * binWidth;
            }
        }
        return min + hist.length * binWidth;
    }

    /**
     * @param (long[]) hist histogram
     * @return number of bins holding at least one sample
     */
    private static int occupiedBins(long[] hist)
    {
        int n = 0;
        for (int i = 0; i < hist.length; i++)
        {
            if (hist[i] > 0)
            {
                n++;
            }
        }
        return n;
    }
}
