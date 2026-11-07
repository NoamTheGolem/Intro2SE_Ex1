package ex1.app;

import java.util.Arrays;
import java.util.Locale;

/**
 * Frames per second of the projection: the number shown while the video plays and written on
 * every frame of an exported video (ADR-0014). A frame's time is the projection of its plane -
 * the assignment's algorithm, the measure its "at least 100 fps" is about - as computed for that
 * frame (nearest neighbour, one thread); drawing and saving it are not counted. The FPS is one
 * second divided by the median of the last {@link #SAMPLES} frame times, so one slow frame
 * (garbage collection) does not move it, and is shown to three significant digits, so it can be
 * read while it changes.
 */
final class FpsMeter
{
    /** How many recent frames the median is taken over. */
    static final int SAMPLES = 15;
    private static final double NANOS_PER_SECOND = 1e9;
    /** Above this the number is no longer a measurement but the timer's resolution. */
    private static final double MAX_FPS = 990000;
    private static final int SIGNIFICANT = 1000;
    private static final double SMALL = 10;

    private final long[] recent = new long[SAMPLES];
    private int count;

    /** Forgets the frames measured so far. */
    void reset()
    {
        count = 0;
    }

    /**
     * @param (long) nanos time of one frame's projection in nanoseconds
     */
    void add(long nanos)
    {
        recent[count % SAMPLES] = Math.max(1L, nanos);
        count++;
    }

    /**
     * @return true once a frame has been measured
     */
    boolean hasValue()
    {
        return count > 0;
    }

    /**
     * @return frames per second: one second over the median of the recent frame times (0 if none)
     */
    double fps()
    {
        if (count == 0)
        {
            return 0;
        }
        int n = Math.min(count, SAMPLES);
        long[] sorted = Arrays.copyOf(recent, n);
        Arrays.sort(sorted);
        return Math.min(MAX_FPS, NANOS_PER_SECOND / sorted[n / 2]);
    }

    /**
     * The same measure over a whole run, e.g. every frame of an export: the median, not the mean,
     * so a few slow frames (the first ones, garbage collection) do not halve it.
     *
     * @param (long[]) nanos frame times in nanoseconds (not changed)
     * @param (int) count how many of them are filled
     * @return frames per second, 0 if none
     */
    static double fpsOf(long[] nanos, int count)
    {
        if (count <= 0)
        {
            return 0;
        }
        long[] sorted = Arrays.copyOf(nanos, count);
        Arrays.sort(sorted);
        return Math.min(MAX_FPS, NANOS_PER_SECOND / Math.max(1L, sorted[count / 2]));
    }

    /**
     * @return the current value as shown, e.g. "2,110 FPS"
     */
    String text()
    {
        return format(fps());
    }

    /**
     * @param (double) fps frames per second
     * @return "24,800 FPS", "512 FPS", "4.2 FPS": three significant digits, thousands grouped
     */
    static String format(double fps)
    {
        if (fps < SMALL)
        {
            return String.format(Locale.ROOT, "%.1f FPS", fps);
        }
        long value = Math.round(fps);
        long unit = 1;
        while (value / unit >= SIGNIFICANT)
        {
            unit *= 10;
        }
        value = Math.round(fps / unit) * unit;
        return String.format(Locale.ROOT, "%,d FPS", Long.valueOf(value));
    }
}
