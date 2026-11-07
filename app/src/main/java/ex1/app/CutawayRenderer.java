package ex1.app;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Draws the scanned object as a solid, cut open by the plane like a pie, by casting one ray per
 * pixel (ADR-0009):
 * <ul>
 *   <li>the half of the object on the viewer's side of the cutting plane is removed;</li>
 *   <li>where a ray enters the kept half through the plane inside the object, it shows the cut
 *       face: the volume's own grey values at that point, i.e. the inside of the object. Inside
 *       the triangle or trapezoid (the part that goes into the video) the face is shown as is,
 *       outside it slightly darker;</li>
 *   <li>elsewhere the ray marches to the object's surface ({@link SolidModel}) and shades it.</li>
 * </ul>
 * Rays are traced in the volume's index space (the geometry maps world millimetres to voxels),
 * empty 8^3 blocks are skipped, and the rows are split over a thread pool. Output goes straight
 * into a {@link Raster}'s colour and depth buffers, so lines drawn afterwards (box, plane outline,
 * points) are hidden behind the object where they should be.
 */
final class CutawayRenderer
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** How a ray walks through the model. */
    private static final double STEP_VOXELS  = 0.7;   // model voxels per step: under one skips none
    private static final int    REFINE_STEPS = 5;     // halvings that pin a hit to the surface

    /** The colour of the object's skin: a warm bone white. */
    private static final int BASE_R = 226;
    private static final int BASE_G = 196;
    private static final int BASE_B = 160;

    /** The light: ambient, diffuse from the viewer, a fill from above and a small highlight. */
    private static final double AMBIENT   = 0.20;
    private static final double DIFFUSE   = 0.70;
    private static final double FILL      = 0.18;
    private static final double SPECULAR  = 0.16;
    private static final int    SHININESS = 24;   // the larger, the smaller the highlight

    /** The cut face outside the shape: dimmed and a little blue, so the video's part stands out. */
    private static final double OUTSIDE_SHAPE_DIM  = 0.55;
    private static final int    OUTSIDE_SHAPE_BLUE = 18;

    /** Block rows per task when the rays are traced on several threads. */
    private static final int ROWS_PER_TASK = 8;

    /** The threads that trace the rays (no pool for one thread). */
    private final ExecutorService pool;
    private final int             threads;

    /** Everything a frame needs, fixed before the rows are traced in parallel. */
    private static final class Frame
    {
        /** The target, its pixel blocks, and what it shows (cut: the plane cuts it open). */
        Raster          raster;
        int             width;
        int             height;
        int             scale;
        SolidModel      model;
        Volume          volume;
        IntensityWindow window;
        boolean         cut;

        /**
         * The rays in voxel indices: pixel (px, py) looks along I(t) = i0 + t * di, with i0 = ic +
         * dx * (px - w/2) + dy * (py - h/2); the zoom (depth = t * zoom), the march step, and the
         * camera's axes for the light.
         */
        Vec3           ic;
        Vec3           dx;
        Vec3           dy;
        Vec3           di;
        double         zoom;
        double         stepT;
        Vec3           forward;
        Vec3           up;
        VolumeGeometry geometry;

        /**
         * The cutting plane in index space: s(I) = (I - a) . n, n towards the viewer; ds = s per
         * unit of t.
         */
        Vec3   a;
        Vec3   n;
        double ds;

        /** The triangle / trapezoid in the plane's own 2-D frame (axes ex, ey). */
        Vec3       ex;
        Vec3       ey;
        double[][] shape;

        /** Scan indices to model voxels: (i - off) * invFactor. */
        double off;
        double invFactor;
    }

    /**
     * Creates the renderer and, for more than one thread, its pool of daemon threads.
     * @param (int) threads Worker threads (1 = render on the calling thread).
     */
    CutawayRenderer(int threads)
    {
        this.threads = Math.max(1, threads);
        if (this.threads > 1)
        {
            pool = Executors.newFixedThreadPool(this.threads, new ThreadFactory()
            {
                /**
                 * Makes a ray tracing thread: a daemon, so the pool never keeps the program alive.
                 * @param (Runnable) r The thread's work.
                 * @return (Thread) The thread, not started yet.
                 */
                @Override
                public Thread newThread(Runnable r)
                {
                    Thread t = new Thread(r, "ex1-3d");
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        else
        {
            pool = null;
        }
    }

    /** Stops the worker threads. */
    void shutdown()
    {
        if (pool != null)
        {
            pool.shutdownNow();
        }
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Renders the model into the raster (which must already be cleared to the background).
     * @param (Raster) raster Destination colour + depth.
     * @param (Camera3D) camera View.
     * @param (VolumeGeometry) geometry Index/world mapping.
     * @param (SolidModel) model The solid.
     * @param (Volume) volume Original volume (values of the cut face).
     * @param (IntensityWindow) window Grey mapping.
     * @param (Plane) plane Cutting plane, or null for no cut.
     * @param (boolean) cutOpen False shows the whole object (plane drawn by the caller only).
     * @param (int) scale 1 = every pixel, 2 = one ray per 2x2 block (while dragging).
     */
    void render(Raster raster, Camera3D camera, VolumeGeometry geometry, SolidModel model, Volume volume,
                IntensityWindow window, Plane plane, boolean cutOpen, int scale)
    {
        final Frame f    = prepare(raster, camera, geometry, model, volume, window, plane, cutOpen, scale);
        int         rows = (f.height + f.scale - 1) / f.scale;
        if (pool == null || rows < 2 * ROWS_PER_TASK)
        {
            traceRows(f, 0, rows);
            return;
        }
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for (int r = 0; r < rows; r += ROWS_PER_TASK)
        {
            final int from = r;
            final int to   = Math.min(rows, r + ROWS_PER_TASK);
            tasks.add(new Callable<Void>()
            {
                /**
                 * Traces this task's block rows.
                 * @return (Void) Nothing (null).
                 */
                @Override
                public Void call()
                {
                    traceRows(f, from, to);
                    return null;
                }
            });
        }
        try
        {
            List<Future<Void>> done = pool.invokeAll(tasks);
            for (int i = 0; i < done.size(); i++)
            {
                done.get(i).get();
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e)
        {
            throw new IllegalStateException("3-D rendering failed", e.getCause());
        }
    }

    /**
     * Finds the point of the object under a pixel: on the cut face or on the surface.
     * @param (int) px Pixel x.
     * @param (int) py Pixel y.
     * @param (int) width Viewport width.
     * @param (int) height Viewport height.
     * @param (Camera3D) camera View.
     * @param (VolumeGeometry) geometry Index/world mapping.
     * @param (SolidModel) model The solid.
     * @param (Plane) plane Cutting plane, or null.
     * @param (boolean) cutOpen Whether the plane cuts the object.
     * @return (Vec3) The point in voxel indices, or null when the pixel shows background.
     */
    Vec3 pick(int px, int py, int width, int height, Camera3D camera, VolumeGeometry geometry, SolidModel model,
              Plane plane, boolean cutOpen)
    {
        Raster probe = new Raster(1, 1);
        Frame  f     = prepare(probe, camera, geometry, model, null, null, plane, cutOpen, 1);
        f.width = width;
        f.height = height;
        double[] hit = new double[1];
        Vec3     i0  = f.ic.add(f.dx.scale(px + 0.5 - width / 2.0)).add(f.dy.scale(py + 0.5 - height / 2.0));
        if (trace(f, i0, hit) == 0)
        {
            return null;
        }
        return i0.add(f.di.scale(hit[0]));
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Computes everything a frame needs once, in voxel indices: the ray of the centre pixel and the
     * steps between pixels, the march step, and the cutting plane with its shape in its own 2-D
     * frame.
     * @param (Raster) raster Destination.
     * @param (Camera3D) camera View.
     * @param (VolumeGeometry) geometry Mapping.
     * @param (SolidModel) model Solid.
     * @param (Volume) volume Original volume.
     * @param (IntensityWindow) window Grey mapping.
     * @param (Plane) plane Cutting plane or null.
     * @param (boolean) cutOpen Cut or not.
     * @param (int) scale Pixel block size.
     * @return (Frame) The frame constants.
     */
    private static Frame prepare(Raster raster, Camera3D camera, VolumeGeometry geometry, SolidModel model,
                                 Volume volume, IntensityWindow window, Plane plane, boolean cutOpen, int scale)
    {
        Frame f = new Frame();
        f.raster = raster;
        f.width = raster.width();
        f.height = raster.height();
        f.scale = Math.max(1, scale);
        f.model = model;
        f.volume = volume;
        f.window = window;
        f.geometry = geometry;
        f.zoom = camera.zoom();
        f.forward = camera.forward();
        f.up = camera.up();
        Vec3 centreWorld = camera.rayOrigin(f.width / 2.0, f.height / 2.0, f.width, f.height);
        f.ic = geometry.toIndex(centreWorld);
        f.dx = geometry.directionToIndex(camera.right().scale(1.0 / f.zoom));
        f.dy = geometry.directionToIndex(camera.up().scale(-1.0 / f.zoom));
        f.di = geometry.directionToIndex(f.forward);
        f.stepT = STEP_VOXELS * model.factor / Math.max(1e-9, f.di.length());
        f.off = (model.factor - 1) / 2.0;
        f.invFactor = 1.0 / model.factor;
        f.cut = cutOpen && plane != null && plane.isRenderable();
        if (f.cut)
        {
            f.a = plane.a1();
            Vec3 n = plane.b().sub(plane.a1()).cross(plane.c().sub(plane.a1()));
            if (!plane.isTriangle())
            {
                Vec3 n2 = plane.b().sub(plane.a1()).cross(plane.a2().sub(plane.a1()));
                if (n2.length() > n.length())
                {
                    n = n2;
                }
            }
            n = n.normalized();
            if (n.dot(f.di) > 0)
            {
                n = n.scale(-1);
            }
            f.n = n;
            f.ds = f.di.dot(n);
            Vec3 ex = plane.c().sub(plane.b());
            if (ex.length() < 1e-9)
            {
                ex = plane.a2().sub(plane.a1());
            }
            f.ex = ex.normalized();
            f.ey = n.cross(f.ex).normalized();
            Vec3[] corners;
            if (plane.isTriangle())
            {
                corners = new Vec3[] {plane.a1(), plane.c(), plane.b()};
            }
            else
            {
                corners = new Vec3[] {plane.a1(), plane.a2(), plane.c(), plane.b()};
            }
            f.shape = new double[corners.length][];
            for (int i = 0; i < corners.length; i++)
            {
                Vec3 d = corners[i].sub(f.a);
                f.shape[i] = new double[] {d.dot(f.ex), d.dot(f.ey)};
            }
        }
        return f;
    }

    /**
     * Traces one ray per block of pixels for a range of block rows and fills the block where the
     * ray hits nearer than what the depth buffer holds.
     * @param (Frame) f Frame constants.
     * @param (int) fromRow First block row.
     * @param (int) toRow One past the last block row.
     */
    private static void traceRows(Frame f, int fromRow, int toRow)
    {
        int[]    pixels = f.raster.pixels();
        float[]  depth  = f.raster.depths();
        double[] hit    = new double[2];
        int      s      = f.scale;
        for (int br = fromRow; br < toRow; br++)
        {
            int    y0 = br * s;
            double py = y0 + s / 2.0;
            for (int x0 = 0; x0 < f.width; x0 += s)
            {
                double px     = x0 + s / 2.0;
                Vec3   i0     = f.ic.add(f.dx.scale(px - f.width / 2.0)).add(f.dy.scale(py - f.height / 2.0));
                int    colour = trace(f, i0, hit);
                if (colour == 0)
                {
                    continue;
                }
                float z = (float) (hit[0] * f.zoom);
                for (int yy = y0; yy < Math.min(f.height, y0 + s); yy++)
                {
                    int row = yy * f.width;
                    for (int xx = x0; xx < Math.min(f.width, x0 + s); xx++)
                    {
                        if (z < depth[row + xx])
                        {
                            pixels[row + xx] = colour;
                            depth[row + xx] = z;
                        }
                    }
                }
            }
        }
    }

    /**
     * Follows one ray.
     * @param (Frame) f Frame constants.
     * @param (Vec3) i0 Ray point at t = 0 (index space).
     * @param (double[]) hit Receives the ray parameter of the hit in hit[0].
     * @return (int) ARGB colour of the hit, or 0 for none.
     */
    private static int trace(Frame f, Vec3 i0, double[] hit)
    {
        double[] range = boxRange(f, i0);
        if (range == null)
        {
            return 0;
        }
        double t0 = range[0];
        double t1 = range[1];
        double tc = Double.NaN;
        if (f.cut)
        {
            double s0 = i0.sub(f.a).dot(f.n);
            if (Math.abs(f.ds) < 1e-12)
            {
                if (s0 > 0)
                {
                    return 0;
                }
            }
            else
            {
                tc = -s0 / f.ds;
                if (tc > t1)
                {
                    return 0;
                }
                if (tc > t0)
                {
                    t0 = tc;
                }
                else
                {
                    tc = Double.NaN;
                }
            }
        }
        if (!Double.isNaN(tc))
        {
            Vec3 p = i0.add(f.di.scale(tc));
            if (solidAtIndex(f, p))
            {
                hit[0] = tc;
                if (f.volume == null)
                {
                    return 1;
                }
                return cutFaceColour(f, p);
            }
        }
        return march(f, i0, t0, t1, hit);
    }

    /**
     * Walks the ray through the kept half of the volume: whole empty 8^3 blocks are jumped over,
     * inside a block the ray steps until it meets solid, then the hit is refined and shaded.
     * @param (Frame) f Frame constants.
     * @param (Vec3) i0 Ray point at t = 0.
     * @param (double) t0 Start of the kept part of the ray.
     * @param (double) t1 End.
     * @param (double[]) hit Receives the hit parameter.
     * @return (int) Shaded surface colour, or 0 for none.
     */
    private static int march(Frame f, Vec3 i0, double t0, double t1, double[] hit)
    {
        SolidModel m    = f.model;
        double     rxs  = f.di.x * f.invFactor;
        double     rys  = f.di.y * f.invFactor;
        double     rzs  = f.di.z * f.invFactor;
        double     prev = t0;
        double     t    = t0;
        while (t <= t1)
        {
            double rx = (i0.x + f.di.x * t - f.off) * f.invFactor;
            double ry = (i0.y + f.di.y * t - f.off) * f.invFactor;
            double rz = (i0.z + f.di.z * t - f.off) * f.invFactor;
            int    i  = (int) Math.floor(rx + 0.5);
            int    j  = (int) Math.floor(ry + 0.5);
            int    k  = (int) Math.floor(rz + 0.5);
            if (!m.blockHasSolid(i, j, k))
            {
                double exit = blockExit(rx, ry, rz, rxs, rys, rzs, i, j, k);
                prev = t;
                t += Math.max(exit, f.stepT * 0.5) + 1e-6;
                continue;
            }
            if (m.isSolid(i, j, k))
            {
                double th = refine(f, i0, prev, t);
                hit[0] = th;
                if (f.volume == null)
                {
                    return 1;
                }
                return shade(f, i0.add(f.di.scale(th)));
            }
            prev = t;
            t += f.stepT;
        }
        return 0;
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Clips the ray to the volume's box (slab method).
     * @param (Frame) f Frame constants.
     * @param (Vec3) i0 Ray point at t = 0.
     * @return (double[]) [tEnter, tExit] of the volume box, or null when the ray misses it.
     */
    private static double[] boxRange(Frame f, Vec3 i0)
    {
        double   lo   = Double.NEGATIVE_INFINITY;
        double   hi   = Double.POSITIVE_INFINITY;
        double[] o    = {i0.x, i0.y, i0.z};
        double[] d    = {f.di.x, f.di.y, f.di.z};
        int[]    size = {f.model.rx * f.model.factor, f.model.ry * f.model.factor, f.model.rz * f.model.factor};
        for (int a = 0; a < 3; a++)
        {
            double min = -0.5;
            double max = size[a] - 0.5;
            if (Math.abs(d[a]) < 1e-12)
            {
                if (o[a] < min || o[a] > max)
                {
                    return null;
                }
                continue;
            }
            double ta = (min - o[a]) / d[a];
            double tb = (max - o[a]) / d[a];
            if (ta > tb)
            {
                double tmp = ta;
                ta = tb;
                tb = tmp;
            }
            lo = Math.max(lo, ta);
            hi = Math.min(hi, tb);
        }
        if (lo > hi)
        {
            return null;
        }
        return new double[] {lo, hi};
    }

    /**
     * Measures how far the ray runs before it leaves the 8^3 block it is in: the nearest of the
     * three block faces ahead of it.
     * @param (double) rx Model x of the ray point.
     * @param (double) ry Model y.
     * @param (double) rz Model z.
     * @param (double) dx Model x per ray unit.
     * @param (double) dy Model y per ray unit.
     * @param (double) dz Model z per ray unit.
     * @param (int) i Voxel x.
     * @param (int) j Voxel y.
     * @param (int) k Voxel z.
     * @return (double) Ray distance to the exit of the voxel's 8^3 block.
     */
    private static double blockExit(double rx, double ry, double rz, double dx, double dy, double dz, int i, int j, int k)
    {
        int    b    = SolidModel.BLOCK;
        double exit = Double.POSITIVE_INFINITY;
        exit = Math.min(exit, axisExit(rx, dx, Math.floorDiv(i, b) * b - 0.5, Math.floorDiv(i, b) * b + b - 0.5));
        exit = Math.min(exit, axisExit(ry, dy, Math.floorDiv(j, b) * b - 0.5, Math.floorDiv(j, b) * b + b - 0.5));
        exit = Math.min(exit, axisExit(rz, dz, Math.floorDiv(k, b) * b - 0.5, Math.floorDiv(k, b) * b + b - 0.5));
        return exit;
    }

    /**
     * Measures, along one axis, how far a moving point runs before it leaves an interval.
     * @param (double) p Position.
     * @param (double) d Direction.
     * @param (double) min Block start.
     * @param (double) max Block end.
     * @return (double) Distance to leave [min, max] along d.
     */
    private static double axisExit(double p, double d, double min, double max)
    {
        if (d > 1e-12)
        {
            return (max - p) / d;
        }
        if (d < -1e-12)
        {
            return (min - p) / d;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Looks up whether a point (in voxel indices) is inside the solid model.
     * @param (Frame) f Frame constants.
     * @param (Vec3) p Point in voxel indices.
     * @return (boolean) True when the point is inside the solid.
     */
    private static boolean solidAtIndex(Frame f, Vec3 p)
    {
        int i = (int) Math.floor((p.x - f.off) * f.invFactor + 0.5);
        int j = (int) Math.floor((p.y - f.off) * f.invFactor + 0.5);
        int k = (int) Math.floor((p.z - f.off) * f.invFactor + 0.5);
        return f.model.isSolid(i, j, k);
    }

    /**
     * Pins the surface down between a free and a solid point of the ray, by halving.
     * @param (Frame) f Frame constants.
     * @param (Vec3) i0 Ray point at t = 0.
     * @param (double) free Parameter known to be outside.
     * @param (double) solid Parameter known to be inside.
     * @return (double) Parameter of the surface.
     */
    private static double refine(Frame f, Vec3 i0, double free, double solid)
    {
        double a = free;
        double b = solid;
        for (int s = 0; s < REFINE_STEPS; s++)
        {
            double m = (a + b) / 2;
            if (solidAtIndex(f, i0.add(f.di.scale(m))))
            {
                b = m;
            }
            else
            {
                a = m;
            }
        }
        return b;
    }

    /**
     * Lights a surface point: the normal from the smoothed grey values, diffuse light from the
     * viewer, a fill light from above and a small highlight.
     * @param (Frame) f Frame constants.
     * @param (Vec3) p Surface point in voxel indices.
     * @return (int) Lit surface colour.
     */
    private static int shade(Frame f, Vec3 p)
    {
        SolidModel m     = f.model;
        double     rx    = (p.x - f.off) * f.invFactor;
        double     ry    = (p.y - f.off) * f.invFactor;
        double     rz    = (p.z - f.off) * f.invFactor;
        double     gx    = m.smoothAt(rx + 1, ry, rz) - m.smoothAt(rx - 1, ry, rz);
        double     gy    = m.smoothAt(rx, ry + 1, rz) - m.smoothAt(rx, ry - 1, rz);
        double     gz    = m.smoothAt(rx, ry, rz + 1) - m.smoothAt(rx, ry, rz - 1);
        Vec3       g     = f.geometry.gradientToWorld(new Vec3(gx, gy, gz));
        double     light = AMBIENT + DIFFUSE * 0.5;
        double     spec  = 0;
        if (g.length() > 1e-9)
        {
            // Outward normal: from bright (inside) to dark (air).
            Vec3   n       = g.normalized().scale(-1);
            double diffuse = Math.max(0, -n.dot(f.forward));
            double fill    = Math.max(0, n.dot(f.up));
            light = AMBIENT + DIFFUSE * diffuse + FILL * fill;
            spec = SPECULAR * Math.pow(diffuse, SHININESS);
        }
        int r  = clamp((int) (BASE_R * light + 255 * spec));
        int gg = clamp((int) (BASE_G * light + 255 * spec));
        int b  = clamp((int) (BASE_B * light + 255 * spec));
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    /**
     * Colours a point of the cut face with the scan's own grey value there, dimmed outside the
     * triangle / trapezoid so the part that goes into the video stands out.
     * @param (Frame) f Frame constants.
     * @param (Vec3) p Point on the cutting plane, inside the object (voxel indices).
     * @return (int) The volume's grey value there, darker outside the plane's shape.
     */
    private static int cutFaceColour(Frame f, Vec3 p)
    {
        Volume v    = f.volume;
        int    x    = (int) Math.floor(p.x + 0.5);
        int    y    = (int) Math.floor(p.y + 0.5);
        int    z    = (int) Math.floor(p.z + 0.5);
        int    grey = 0;
        if (v.contains(x, y, z))
        {
            grey = f.window.grey(v.valueAt(v.index(x, y, z)));
        }
        Vec3    d      = p.sub(f.a);
        boolean inside = insidePolygon(f.shape, d.dot(f.ex), d.dot(f.ey));
        int     r      = grey;
        int     g      = grey;
        int     b      = grey;
        if (!inside)
        {
            r = (int) (grey * OUTSIDE_SHAPE_DIM);
            g = (int) (grey * OUTSIDE_SHAPE_DIM);
            b = (int) (grey * OUTSIDE_SHAPE_DIM + OUTSIDE_SHAPE_BLUE);
        }
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /**
     * Tests whether a point lies inside a polygon, by the even-odd rule (works for the 3 or 4
     * corners of any plane, convex or not).
     * @param (double[][]) poly Polygon corners.
     * @param (double) x Point x.
     * @param (double) y Point y.
     * @return (boolean) True when inside.
     */
    private static boolean insidePolygon(double[][] poly, double x, double y)
    {
        boolean inside = false;
        int     n      = poly.length;
        for (int i = 0, j = n - 1; i < n; j = i++)
        {
            double  xi      = poly[i][0];
            double  yi      = poly[i][1];
            double  xj      = poly[j][0];
            double  yj      = poly[j][1];
            boolean crosses = (yi > y) != (yj > y);
            if (crosses && x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Keeps a colour component in range.
     * @param (int) c Colour component.
     * @return (int) Clamped to 0..255.
     */
    private static int clamp(int c)
    {
        return Math.max(0, Math.min(255, c));
    }

    /**
     * Gives the number of ray tracing threads.
     * @return (int) Number of worker threads.
     */
    int threads()
    {
        return threads;
    }
}
