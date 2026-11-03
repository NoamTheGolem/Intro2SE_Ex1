package ex1.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Decorator that runs another projector's rows on a fixed pool of worker threads.
 *
 * Rows of the output image are independent and write to disjoint pixel ranges, so no locking
 * is needed: each worker gets a contiguous block of rows (good cache behaviour), and the caller
 * waits for all blocks before the frame is used. The result is bit-identical to the
 * single-threaded projector. Worker threads are daemons, so they never keep the JVM alive.
 */
public final class ParallelProjector extends Projector
{
    private static final int MIN_ROWS_PER_TASK = 8;

    private final Projector inner;
    private final int threads;
    private final ExecutorService pool;

    /**
     * @param (Projector) inner the projector that does the sampling (nearest or trilinear)
     * @param (int) threads number of worker threads (&gt;= 2)
     */
    public ParallelProjector(Projector inner, int threads)
    {
        if (threads < 2)
        {
            throw new IllegalArgumentException("ParallelProjector needs at least 2 threads, got " + threads);
        }
        this.inner = inner;
        this.threads = threads;
        this.pool = Executors.newFixedThreadPool(threads, new ThreadFactory()
        {
            private int count = 0;

            @Override
            public Thread newThread(Runnable r)
            {
                count++;
                Thread t = new Thread(r, "projector-" + count);
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public void project(final Volume volume, final Plane plane, final Image2D image)
    {
        int w = plane.imageWidth();
        int h = plane.imageHeight();
        if (image.width() < w || image.height() < h)
        {
            throw new IllegalArgumentException("image " + image.width() + "x" + image.height()
                    + " is smaller than plane " + w + "x" + h);
        }
        java.util.Arrays.fill(image.pixels(), 0f);

        int tasks = Math.min(threads, Math.max(1, h / MIN_ROWS_PER_TASK));
        if (tasks == 1)
        {
            // Too small to be worth the hand-off; stay on this thread.
            inner.projectRows(volume, plane, image, 0, h);
            return;
        }
        List<Future<?>> futures = new ArrayList<Future<?>>();
        int rowsPerTask = (h + tasks - 1) / tasks;
        for (int t = 0; t < tasks; t++)
        {
            final int from = t * rowsPerTask;
            final int to = Math.min(h, from + rowsPerTask);
            if (from >= to)
            {
                break;
            }
            futures.add(pool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    inner.projectRows(volume, plane, image, from, to);
                }
            }));
        }
        waitAll(futures);
    }

    @Override
    protected void sampleRow(Volume volume, double px, double py, double pz,
                             double dx, double dy, double dz,
                             int n, float[] out, int offset)
    {
        inner.sampleRow(volume, px, py, pz, dx, dy, dz, n, out, offset);
    }

    /**
     * @return number of worker threads
     */
    public int threads()
    {
        return threads;
    }

    /**
     * Stops the worker threads. Optional: they are daemons, but a long-running app (StudioApp)
     * should release them when it swaps projectors.
     */
    public void shutdown()
    {
        pool.shutdown();
    }

    /**
     * @param (List) futures tasks to wait for
     */
    private static void waitAll(List<Future<?>> futures)
    {
        for (int i = 0; i < futures.size(); i++)
        {
            try
            {
                futures.get(i).get();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while projecting", e);
            }
            catch (ExecutionException e)
            {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException)
                {
                    throw (RuntimeException) cause;
                }
                throw new IllegalStateException("projection failed", cause);
            }
        }
    }
}
