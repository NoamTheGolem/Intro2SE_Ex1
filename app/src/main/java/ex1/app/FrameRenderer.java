package ex1.app;

import ex1.core.Image2D;
import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Volume;

import java.util.Arrays;
import java.util.List;

/**
 * Turns plane number i into one 8-bit grey frame. Shared by the command-line demo (Ex0) and
 * the app (StudioApp: export), so both produce identical frames.
 *
 * All frames have one size: the largest plane, rounded up to even (decision D8); each plane is
 * centred on black. A null plane (a CSV row that cannot be projected) gives an all-black frame,
 * so frame i always belongs to row i. Buffers are reused, so rendering allocates nothing per frame.
 *
 * With {@link #setShowFps} on, every frame that was projected also shows the FPS of the
 * projection in its top-left corner ({@link FpsMeter}, {@link FpsOverlay}; ADR-0014); a black
 * frame stays black.
 */
public final class FrameRenderer
{
    private static final int WARM_UP_PROJECTIONS = 5;

    private final Volume volume;
    private final List<Plane> planes;
    private final IntensityWindow window;
    private final Projector projector;
    private final int width;
    private final int height;
    private final Image2D image;
    private final byte[] frame;
    private long lastNanos;
    private boolean showFps;
    private boolean warmedUp;
    private final FpsMeter meter = new FpsMeter();
    private final FpsOverlay overlay = new FpsOverlay();

    /**
     * @param (Volume) volume source volume
     * @param (List) planes planes in order; null entries render as black frames
     * @param (IntensityWindow) window grey mapping
     * @param (Projector) projector sampling method
     */
    public FrameRenderer(Volume volume, List<Plane> planes, IntensityWindow window, Projector projector)
    {
        if (planes.isEmpty())
        {
            throw new IllegalArgumentException("no planes to render");
        }
        this.volume = volume;
        this.planes = planes;
        this.window = window;
        this.projector = projector;
        int w = 0;
        int h = 0;
        for (int i = 0; i < planes.size(); i++)
        {
            Plane p = planes.get(i);
            if (p != null)
            {
                w = Math.max(w, p.imageWidth());
                h = Math.max(h, p.imageHeight());
            }
        }
        if (w == 0)
        {
            throw new IllegalArgumentException("no plane can be projected");
        }
        this.width = w + (w & 1);
        this.height = h + (h & 1);
        this.image = new Image2D(width, height);
        this.frame = new byte[width * height];
    }

    /**
     * @param (boolean) on true writes the projection's FPS on every projected frame
     */
    public void setShowFps(boolean on)
    {
        showFps = on;
    }

    /**
     * Renders plane i. The returned array is reused by the next call.
     *
     * @param (int) i plane index
     * @return width*height grey bytes, row-major
     */
    public byte[] render(int i)
    {
        Plane plane = planes.get(i);
        Arrays.fill(frame, (byte) 0);
        if (plane == null)
        {
            lastNanos = 0;
            return frame;
        }
        if (showFps && !warmedUp)
        {
            warmUp();
        }
        long t0 = System.nanoTime();
        projector.project(volume, plane, image);
        lastNanos = System.nanoTime() - t0;
        window.toGrey(image, frame, width, height, plane.imageWidth(), plane.imageHeight());
        if (showFps)
        {
            meter.add(lastNanos);
            overlay.draw(frame, width, height, meter.text());
        }
        return frame;
    }

    /**
     * Projects the first plane a few times untimed, so the FPS on the first frames is the
     * algorithm's speed and not the Java compiler warming up.
     */
    private void warmUp()
    {
        warmedUp = true;
        for (int i = 0; i < planes.size(); i++)
        {
            if (planes.get(i) != null)
            {
                for (int k = 0; k < WARM_UP_PROJECTIONS; k++)
                {
                    projector.project(volume, planes.get(i), image);
                }
                return;
            }
        }
    }

    /**
     * @return time of the last projection in nanoseconds (excludes grey mapping)
     */
    public long lastProjectionNanos()
    {
        return lastNanos;
    }

    /**
     * @return number of planes / frames
     */
    public int frameCount()
    {
        return planes.size();
    }

    /**
     * @return frame width (even)
     */
    public int width()
    {
        return width;
    }

    /**
     * @return frame height (even)
     */
    public int height()
    {
        return height;
    }
}
