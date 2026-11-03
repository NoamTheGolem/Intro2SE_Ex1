package ex1.io;

import ex1.core.Plane;
import ex1.core.Vec3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Writes planes in the course's CSV format (the one Ex0.csv uses): a header row, then
 * "N, x1,y1,z1, x2,y2,z2, x3,y3,z3[, x4,y4,z4]" with N = 3 or 4. Files written here are read
 * back by {@link PlaneCsvReader} and by the Ex0 demo, so a sequence built in the app can be
 * rendered from the command line too.
 */
public final class PlaneCsvWriter
{
    private PlaneCsvWriter()
    {
    }

    /**
     * @param (List) planes planes in order (null entries are skipped)
     * @param (File) file destination
     * @throws (IOException) on write errors
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

    /**
     * @param (Plane) p plane
     * @return one CSV row in the course format
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
            append(sb, p.a2().x, p.a2().y, p.a2().z);
            sb.append(',');
            append(sb, p.b().x, p.b().y, p.b().z);
            sb.append(',');
            append(sb, p.c().x, p.c().y, p.c().z);
        }
        return sb.toString();
    }

    /**
     * Rounds a plane to the precision {@link #row(Plane)} writes, so that what is rendered is
     * exactly what a reader of the CSV will render: a video exported from quantized planes and a
     * video rendered by Ex0 from the saved CSV are identical byte for byte (nearest-neighbour
     * sampling would otherwise flip at voxel boundaries for differences far below EPS).
     *
     * @param (Plane) p plane
     * @return the same plane with every coordinate rounded to the written precision
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

    /**
     * @param (Vec3) v point
     * @return the point as the CSV text of it reads back
     */
    private static Vec3 round(Vec3 v)
    {
        return new Vec3(Double.parseDouble(number(v.x)), Double.parseDouble(number(v.y)), Double.parseDouble(number(v.z)));
    }

    /**
     * @param (StringBuilder) sb destination
     * @param (double) x x
     * @param (double) y y
     * @param (double) z z
     */
    private static void append(StringBuilder sb, double x, double y, double z)
    {
        sb.append(number(x)).append(',').append(number(y)).append(',').append(number(z));
    }

    /**
     * @param (double) v value
     * @return compact decimal text (integers without ".0")
     */
    private static String number(double v)
    {
        if (v == Math.rint(v) && Math.abs(v) < 1e9)
        {
            return Long.toString((long) v);
        }
        String text = String.format(Locale.ROOT, "%.6f", v);
        // Trim trailing zeros but keep enough digits for the EPS = 0.001 rules to survive a round trip.
        text = text.replaceAll("0+$", "");
        if (text.endsWith("."))
        {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
