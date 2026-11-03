package ex1.app;

import ex1.core.Plane;
import ex1.core.Vec3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates demo CSVs (SRS FR-12):
 * java -cp Ex0.jar ex1.app.SweepGenerator MODE SX SY SZ COUNT out.csv
 *
 * Modes (all planes are centred in the volume, rails point along +y):
 *   sweep     rectangles at z = 0 .. SZ-1                 (classic slice-by-slice)
 *   rotate    rectangles rotating 0..180 deg around the y axis
 *   tilt      rectangles tilting -60..60 deg around the x axis
 *   triangle  isosceles triangles rotating 0..360 deg around the z axis
 */
public final class SweepGenerator
{
    private static final double DEG = Math.PI / 180.0;
    private static final double TILT_RANGE = 60.0;

    private SweepGenerator()
    {
    }

    // ===================================================================== ENTRY

    /**
     * @param (String[]) args mode, sx, sy, sz, count, output csv
     */
    public static void main(String[] args)
    {
        if (args.length != 6)
        {
            System.err.println("Usage: SweepGenerator sweep|rotate|tilt|triangle SX SY SZ COUNT out.csv");
            System.exit(1);
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        double sx = Double.parseDouble(args[1]);
        double sy = Double.parseDouble(args[2]);
        double sz = Double.parseDouble(args[3]);
        int count = Integer.parseInt(args[4]);
        File out = new File(args[5]);
        try
        {
            write(mode, sx, sy, sz, count, out);
            System.out.println("Wrote " + count + " planes (" + mode + ") to " + out);
        }
        catch (IOException | IllegalArgumentException e)
        {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // ============================================================ BUILD PIPELINE

    /**
     * @param (String) mode sweep, rotate, tilt or triangle
     * @param (double) sx volume size x
     * @param (double) sy volume size y
     * @param (double) sz volume size z
     * @param (int) count number of planes
     * @param (File) out destination CSV
     * @throws (IOException) on write errors
     */
    static void write(String mode, double sx, double sy, double sz, int count, File out) throws IOException
    {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8));
        try
        {
            w.println("# Ex1 planes, mode=" + mode + ", volume " + (int) sx + "x" + (int) sy + "x" + (int) sz);
            List<Plane> planes = generate(mode, sx, sy, sz, count);
            for (int i = 0; i < planes.size(); i++)
            {
                w.println(csvRow(planes.get(i)));
            }
        }
        finally
        {
            w.close();
        }
    }

    /**
     * Builds the planes of one demo mode, all centred in the volume.
     *
     * @param (String) mode sweep, rotate, tilt or triangle
     * @param (double) sx volume size x
     * @param (double) sy volume size y
     * @param (double) sz volume size z
     * @param (int) count number of planes (>= 1)
     * @return the planes in order
     */
    public static List<Plane> generate(String mode, double sx, double sy, double sz, int count)
    {
        if (count < 1)
        {
            throw new IllegalArgumentException("COUNT must be >= 1");
        }
        List<Plane> planes = new ArrayList<Plane>();
        Vec3 centre = new Vec3((sx - 1) / 2.0, (sy - 1) / 2.0, (sz - 1) / 2.0);
        Vec3 down = new Vec3(0, 1, 0);
        for (int i = 0; i < count; i++)
        {
            planes.add(plane(mode.toLowerCase(Locale.ROOT), fraction(i, count), sx, sy, sz, centre, down));
        }
        return planes;
    }

    /**
     * @param (String) mode generation mode
     * @param (double) t position in the sequence, 0..1
     * @param (double) sx volume size x
     * @param (double) sy volume size y
     * @param (double) sz volume size z
     * @param (Vec3) centre volume centre
     * @param (Vec3) down +y unit vector
     * @return the t-th plane
     */
    private static Plane plane(String mode, double t, double sx, double sy, double sz, Vec3 centre, Vec3 down)
    {
        if (mode.equals("sweep"))
        {
            Vec3 c = new Vec3(centre.x, centre.y, t * (sz - 1));
            return rectangle(c, new Vec3(1, 0, 0), down, sx - 1, sy - 1);
        }
        if (mode.equals("rotate"))
        {
            double a = t * 180.0 * DEG;
            Vec3 across = new Vec3(Math.cos(a), 0, Math.sin(a));
            double width = Math.sqrt(sx * sx + sz * sz);
            return rectangle(centre, across, down, width, sy - 1);
        }
        if (mode.equals("tilt"))
        {
            double a = (t * 2 - 1) * TILT_RANGE * DEG;
            Vec3 tilted = new Vec3(0, Math.cos(a), Math.sin(a));
            double height = Math.sqrt(sy * sy + sz * sz);
            return rectangle(centre, new Vec3(1, 0, 0), tilted, sx - 1, height);
        }
        if (mode.equals("triangle"))
        {
            double a = t * 360.0 * DEG;
            Vec3 axis = new Vec3(Math.sin(a), Math.cos(a), 0);
            Vec3 side = new Vec3(Math.cos(a), -Math.sin(a), 0);
            double r = Math.min(sx, sy) * 0.45;
            Vec3 apex = new Vec3(centre.x, centre.y, centre.z).sub(axis.scale(r));
            Vec3 baseMid = centre.add(axis.scale(r));
            return Plane.triangle(apex, baseMid.sub(side.scale(r)), baseMid.add(side.scale(r)));
        }
        throw new IllegalArgumentException("unknown mode " + mode);
    }

    // ========================================================= LOW-LEVEL HELPERS

    /**
     * A rectangle is an isosceles trapezoid with |a1a2| = |bc|.
     *
     * @param (Vec3) centre centre point
     * @param (Vec3) across unit vector along the width
     * @param (Vec3) along unit vector along the rails (image "down")
     * @param (double) width full width
     * @param (double) height full height (rail length)
     * @return the plane
     */
    private static Plane rectangle(Vec3 centre, Vec3 across, Vec3 along, double width, double height)
    {
        Vec3 hw = across.scale(width / 2.0);
        Vec3 hh = along.scale(height / 2.0);
        Vec3 a1 = centre.sub(hw).sub(hh);
        Vec3 a2 = centre.add(hw).sub(hh);
        Vec3 b = centre.sub(hw).add(hh);
        Vec3 c = centre.add(hw).add(hh);
        return Plane.trapezoid(a1, a2, b, c);
    }

    /**
     * @param (int) i index
     * @param (int) count total
     * @return i/(count-1), or 0 for a single plane
     */
    private static double fraction(int i, int count)
    {
        if (count == 1)
        {
            return 0.0;
        }
        return i / (double) (count - 1);
    }

    /**
     * @param (Plane) p plane
     * @return CSV row of 9 or 12 numbers
     */
    private static String csvRow(Plane p)
    {
        StringBuilder sb = new StringBuilder();
        append(sb, p.a1());
        if (!p.isTriangle())
        {
            sb.append(',');
            append(sb, p.a2());
        }
        sb.append(',');
        append(sb, p.b());
        sb.append(',');
        append(sb, p.c());
        return sb.toString();
    }

    /**
     * @param (StringBuilder) sb destination
     * @param (Vec3) v point
     */
    private static void append(StringBuilder sb, Vec3 v)
    {
        sb.append(String.format(Locale.ROOT, "%.6f,%.6f,%.6f", v.x, v.y, v.z));
    }
}
