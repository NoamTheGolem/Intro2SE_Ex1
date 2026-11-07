package ex1.app;

import ex1.core.ByteVolume;
import ex1.core.Image2D;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.core.VoxelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Measures projection speed (SRS NFR-1): java -cp Ex0.jar ex1.app.Benchmark [N] [frames]
 *
 * Builds a synthetic N^3 8-bit volume (default 500^3) and projects a fixed set of random
 * oblique planes through it, reporting frames per second for each interpolation mode.
 * File I/O and video encoding are excluded: this measures the algorithm only.
 */
public final class Benchmark
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The default run: an N^3 volume, frames timed after a warm-up. */
    private static final int DEFAULT_N      = 500;
    private static final int DEFAULT_FRAMES = 300;
    private static final int WARMUP_FRAMES  = 100;   // untimed, so the JIT has compiled the loops

    /** The planes: random oblique triangles, the same ones on every run. */
    private static final int  PLANE_COUNT = 64;
    private static final long SEED        = 42L;

    /** The speeds the assignment asks for (30 FPS) and hopes for (100 FPS). */
    private static final double REQUIRED_FPS = 30.0;
    private static final double TARGET_FPS   = 100.0;

    /** A program: main and its helpers only; never instantiated. */
    private Benchmark()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Program entry point: prints the FPS table for a synthetic N^3 volume (nearest and trilinear,
     * one thread and all cores).
     * @param (String[]) args Optional N and number of timed frames.
     */
    public static void main(String[] args)
    {
        int n      = DEFAULT_N;
        int frames = DEFAULT_FRAMES;
        if (args.length > 0)
        {
            n = Integer.parseInt(args[0]);
        }
        if (args.length > 1)
        {
            frames = Integer.parseInt(args[1]);
        }
        System.out.println("Building " + n + "^3 uint8 volume ("
                + ((long) n * n * n / (1024 * 1024)) + " MB) ...");
        Volume      volume = buildVolume(n);
        List<Plane> planes = randomPlanes(n, PLANE_COUNT, new Random(SEED));
        System.out.println("Planes: " + PLANE_COUNT + " random oblique isosceles triangles, side ~" + n
                + ", up to " + maxWidth(planes) + "x" + maxHeight(planes) + " px");

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Threads: 1 (the requirement is single-threaded) and " + cores + " (all cores)");
        runCase("nearest   x1 ", volume, planes, Projector.create(Projector.Interpolation.NEAREST), frames);
        runCase("trilinear x1 ", volume, planes, Projector.create(Projector.Interpolation.TRILINEAR), frames);
        if (cores > 1)
        {
            runCase("nearest   x" + cores, volume, planes,
                    Projector.create(Projector.Interpolation.NEAREST, cores), frames);
            runCase("trilinear x" + cores, volume, planes,
                    Projector.create(Projector.Interpolation.TRILINEAR, cores), frames);
        }
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Builds a busy volume (not a constant one), so branch prediction and caches see realistic
     * data.
     * @param (int) n Edge length.
     * @return (Volume) Synthetic volume.
     */
    private static Volume buildVolume(int n)
    {
        ByteVolume v = (ByteVolume) Volume.create(VoxelType.UINT8, n, n, n);
        byte[]     d = v.data();
        int        i = 0;
        for (int z = 0; z < n; z++)
        {
            for (int y = 0; y < n; y++)
            {
                for (int x = 0; x < n; x++)
                {
                    d[i] = (byte) ((x ^ y) + z);
                    i++;
                }
            }
        }
        return v;
    }

    /**
     * Isosceles triangles with the apex near one face of the cube and the base across the
     * opposite side, in random orientations: the worst realistic case (large and oblique).
     * @param (int) n Edge length.
     * @param (int) count Number of planes.
     * @param (Random) rnd Random source.
     * @return (List<Plane>) Planes.
     */
    private static List<Plane> randomPlanes(int n, int count, Random rnd)
    {
        List<Plane> planes = new ArrayList<Plane>();
        Vec3        centre = new Vec3(n / 2.0, n / 2.0, n / 2.0);
        while (planes.size() < count)
        {
            Vec3 axis = randomUnit(rnd);
            Vec3 side = randomUnit(rnd).cross(axis).normalized();
            if (side.length() < 0.5)
            {
                continue;
            }
            Vec3  a       = centre.sub(axis.scale(n * 0.5));
            Vec3  baseMid = centre.add(axis.scale(n * 0.5));
            Vec3  b       = baseMid.sub(side.scale(n * 0.5));
            Vec3  c       = baseMid.add(side.scale(n * 0.5));
            Plane p       = Plane.triangle(a, b, c);
            if (p.validate(Plane.DEFAULT_EPS).isEmpty())
            {
                planes.add(p);
            }
        }
        return planes;
    }

    /**
     * Warms up the JIT, then times the requested number of frames.
     * @param (String) name Label.
     * @param (Volume) volume Volume.
     * @param (List<Plane>) planes Planes, used round-robin.
     * @param (Projector) projector Projector under test.
     * @param (int) frames Frames to time.
     */
    private static void runCase(String name, Volume volume, List<Plane> planes, Projector projector, int frames)
    {
        Image2D image    = new Image2D(maxWidth(planes), maxHeight(planes));
        double  checksum = 0;
        for (int i = 0; i < WARMUP_FRAMES; i++)
        {
            Plane p = planes.get(i % planes.size());
            projector.project(volume, p, image);
            checksum += image.get(p.imageWidth() / 2, p.imageHeight() / 2);
        }
        long t0     = System.nanoTime();
        long pixels = 0;
        for (int i = 0; i < frames; i++)
        {
            Plane p = planes.get(i % planes.size());
            projector.project(volume, p, image);
            checksum += image.get(p.imageWidth() / 2, p.imageHeight() / 2);
            pixels += (long) p.imageWidth() * p.imageHeight();
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        double fps     = frames / seconds;
        String verdict = "FAIL (< 30)";
        if (fps >= TARGET_FPS)
        {
            verdict = "PASS (>= 100 target)";
        }
        else if (fps >= REQUIRED_FPS)
        {
            verdict = "PASS (>= 30 required)";
        }
        System.out.printf(Locale.ROOT, "%-14s: %7.1f fps  %6.2f ms/frame  %5.2f ns/pixel  %s  [checksum %.0f]%n",
                name, fps, 1000.0 / fps, seconds * 1e9 / pixels, verdict, checksum);
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Draws a direction uniformly on the unit sphere (Gaussian components, normalised).
     * @param (Random) rnd Random source.
     * @return (Vec3) Uniformly random unit vector.
     */
    private static Vec3 randomUnit(Random rnd)
    {
        while (true)
        {
            Vec3   v   = new Vec3(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
            double len = v.length();
            if (len > 0.1 && len <= 1.0)
            {
                return v.scale(1.0 / len);
            }
        }
    }

    /**
     * Finds the widest image among the planes, to size the shared output image.
     * @param (List<Plane>) planes Planes.
     * @return (int) Largest image width.
     */
    private static int maxWidth(List<Plane> planes)
    {
        int w = 1;
        for (int i = 0; i < planes.size(); i++)
        {
            w = Math.max(w, planes.get(i).imageWidth());
        }
        return w;
    }

    /**
     * Finds the tallest image among the planes, to size the shared output image.
     * @param (List<Plane>) planes Planes.
     * @return (int) Largest image height.
     */
    private static int maxHeight(List<Plane> planes)
    {
        int h = 1;
        for (int i = 0; i < planes.size(); i++)
        {
            h = Math.max(h, planes.get(i).imageHeight());
        }
        return h;
    }
}
