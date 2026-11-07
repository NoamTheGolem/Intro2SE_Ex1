package ex1.io;

import ex1.core.Plane;
import ex1.core.Vec3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes planes in the course's CSV format (the one Ex0.csv and the lecturer's data set use): a
 * header row, then "N, x1,y1,z1, x2,y2,z2, x3,y3,z3[, x4,y4,z4]" with N = 3 or 4, points in the
 * course order: (a, b, c) for a triangle and (a1, b, a2, c) for a trapezoid, i.e. the two rails
 * one after the other (see {@link ex1.core.PlaneResolver}). Files written here are read back by
 * {@link PlaneCsvReader} and by the Ex0 demo, so a sequence built in the app can be rendered from
 * the command line too.
 */
public final class PlaneCsvWriter
{
    /** Static methods only; never instantiated. */
    private PlaneCsvWriter()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Writes planes as a CSV file in the course format.
     * @param (List<Plane>) planes Planes in order (null entries are skipped).
     * @param (File) file Destination.
     * @throws (IOException) On write errors.
     */
    public static void write(List<Plane> planes, File file) throws IOException
    {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        try
        {
            w.println("Number of Points (3 or 4),x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4");
            for (int i = 0; i < planes.size(); i++)
            {
                Plane p = planes.get(i);
                if (p != null)
                {
                    w.println(row(p));
                }
            }
        }
        finally
        {
            w.close();
        }
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Writes one plane as a CSV row.
     * @param (Plane) p Plane.
     * @return (String) One CSV row in the course format.
     */
    public static String row(Plane p)
    {
        StringBuilder sb = new StringBuilder();
        if (p.isTriangle())
        {
            sb.append("3,");
            append(sb, p.a1().x, p.a1().y, p.a1().z);
            sb.append(',');
            append(sb, p.b().x, p.b().y, p.b().z);
            sb.append(',');
            append(sb, p.c().x, p.c().y, p.c().z);
            sb.append(",,,");
        }
        else
        {
            sb.append("4,");
            append(sb, p.a1().x, p.a1().y, p.a1().z);
            sb.append(',');
            append(sb, p.b().x, p.b().y, p.b().z);
            sb.append(',');
            append(sb, p.a2().x, p.a2().y, p.a2().z);
            sb.append(',');
            append(sb, p.c().x, p.c().y, p.c().z);
        }
        return sb.toString();
    }

    /**
     * Gives the plane exactly as a reader of the written CSV will see it, so that what the app
     * renders is what Ex0 renders from the saved file: a video exported from these planes and a
     * video rendered by Ex0 from the CSV are identical byte for byte (nearest-neighbour sampling
     * would flip at voxel boundaries for differences far below EPS). With the exact number format
     * this returns an equal plane; it stays as the one place that guarantees it.
     * @param (Plane) p Plane.
     * @return (Plane) The same plane with every coordinate rounded to the written precision.
     */
    public static Plane quantize(Plane p)
    {
        if (p == null)
        {
            return null;
        }
        if (p.isTriangle())
        {
            return Plane.triangle(round(p.a1()), round(p.b()), round(p.c()));
        }
        return Plane.trapezoid(round(p.a1()), round(p.a2()), round(p.b()), round(p.c()));
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Rounds a point the way its CSV text will read back.
     * @param (Vec3) v Point.
     * @return (Vec3) The point as the CSV text of it reads back.
     */
    private static Vec3 round(Vec3 v)
    {
        return new Vec3(Double.parseDouble(number(v.x)), Double.parseDouble(number(v.y)), Double.parseDouble(number(v.z)));
    }

    /**
     * Appends the three coordinates of a point to a row.
     * @param (StringBuilder) sb Destination.
     * @param (double) x X.
     * @param (double) y Y.
     * @param (double) z Z.
     */
    private static void append(StringBuilder sb, double x, double y, double z)
    {
        sb.append(number(x)).append(',').append(number(y)).append(',').append(number(z));
    }

    /**
     * Writes a coordinate exactly: the shortest decimal that reads back as the same double.
     * @param (double) v Value.
     * @return (String) Compact decimal text (integers without ".0").
     */
    private static String number(double v)
    {
        if (v == Math.rint(v) && Math.abs(v) < 1e9)
        {
            return Long.toString((long) v);
        }
        // The shortest decimal that reads back as exactly the same double (Double.toString), in
        // plain notation. Exact, so a plane survives the round trip bit for bit: the lecturer's
        // 8-decimal rows come back unchanged and interpolated planes keep every rule of EPS.
        // (A first version printed 3 decimals; that moved an oblique trapezoid's rails apart by
        // 0.001 = EPS and the row read back as a different shape.)
        return new BigDecimal(Double.toString(v)).toPlainString();
    }
}
