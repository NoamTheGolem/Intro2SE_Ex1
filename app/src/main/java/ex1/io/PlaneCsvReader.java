package ex1.io;

import ex1.core.Plane;
import ex1.core.PlaneResolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the plane list of the demo (assignment item 7b).
 *
 * Accepted row formats (cells may be quoted, empty cells are ignored):
 * <pre>
 *   Course format (Ex0.csv):  N, x1,y1,z1, x2,y2,z2, x3,y3,z3 [, x4,y4,z4]     N = 3 or 4
 *   Plain format:             x1,y1,z1, x2,y2,z2, x3,y3,z3 [, x4,y4,z4]
 * </pre>
 * Point order: 3 points = (a, b, c); 4 points = (a1, a2, b, c). A header line, blank lines and
 * '#' comments are skipped.
 *
 * Every data row becomes exactly one {@link Entry}, i.e. one video frame (7c: "a frame for each
 * plane, in the list order"). The points of a row are interpreted by {@link PlaneResolver}
 * (documented order first, then the other readings of the same points). A row that breaks the
 * plane rules of the Q&amp;A in every reading gets a warning but is still projected; a row that
 * cannot be projected at all (not numbers, wrong count, NaN, zero-length side) gets a warning and
 * a null plane, which becomes a black frame (decision D11 / ADR-0003).
 */
public final class PlaneCsvReader
{
    private static final int TRIANGLE_POINTS = 3;
    private static final int TRAPEZOID_POINTS = 4;
    private static final int COORDS = 3;

    /** One data row of the CSV = one frame. */
    public static final class Entry
    {
        /** 1-based data row number (header, blank and comment lines not counted) = frame number. */
        public final int row;
        /** 1-based line number in the file. */
        public final int line;
        /** The plane to project, or null when the row cannot be projected (black frame). */
        public final Plane plane;
        public final List<String> warnings;

        /**
         * @param (int) row 1-based data row number
         * @param (int) line 1-based file line number
         * @param (Plane) plane parsed plane or null
         * @param (List) warnings problems found in this row
         */
        Entry(int row, int line, Plane plane, List<String> warnings)
        {
            this.row = row;
            this.line = line;
            this.plane = plane;
            this.warnings = warnings;
        }

        /**
         * @return "row N (line M)" for messages
         */
        public String where()
        {
            return "row " + row + " (line " + line + ")";
        }
    }

    /** All rows in file order, plus fatal errors (only when nothing usable was found). */
    public static final class Result
    {
        public final List<Entry> rows = new ArrayList<Entry>();
        public final List<String> errors = new ArrayList<String>();

        /**
         * @return true when there is at least one row and no fatal error
         */
        public boolean isValid()
        {
            return errors.isEmpty();
        }

        /**
         * @return every row warning, prefixed with "row N (line M): "
         */
        public List<String> warnings()
        {
            List<String> all = new ArrayList<String>();
            for (int i = 0; i < rows.size(); i++)
            {
                Entry e = rows.get(i);
                for (int k = 0; k < e.warnings.size(); k++)
                {
                    all.add(e.where() + ": " + e.warnings.get(k));
                }
            }
            return all;
        }

        /**
         * @return the planes in order; null entries mean "black frame"
         */
        public List<Plane> planes()
        {
            List<Plane> list = new ArrayList<Plane>();
            for (int i = 0; i < rows.size(); i++)
            {
                list.add(rows.get(i).plane);
            }
            return list;
        }
    }

    private PlaneCsvReader()
    {
    }

    /**
     * @param (File) file CSV file
     * @param (double) eps geometric tolerance for plane validation
     * @return rows and errors
     * @throws (IOException) if the file cannot be read
     */
    public static Result read(File file, double eps) throws IOException
    {
        Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
        try
        {
            return read(r, eps);
        }
        finally
        {
            r.close();
        }
    }

    /**
     * @param (Reader) reader CSV text
     * @param (double) eps geometric tolerance for plane validation
     * @return rows and errors
     * @throws (IOException) on read errors
     */
    public static Result read(Reader reader, double eps) throws IOException
    {
        BufferedReader in = new BufferedReader(reader);
        Result result = new Result();
        int lineNo = 0;
        boolean seenData = false;
        String line = in.readLine();
        while (line != null)
        {
            lineNo++;
            String text = line.replace("﻿", "").replace("\"", "").trim();
            if (!text.isEmpty() && !text.startsWith("#") && !isOnlySeparators(text))
            {
                double[] values = parse(text.split("[,;\\s]+"));
                if (values == null && !seenData)
                {
                    // A non-numeric line before any data is the header; skip it.
                }
                else
                {
                    seenData = true;
                    result.rows.add(toEntry(result.rows.size() + 1, lineNo, text, values, eps));
                }
            }
            line = in.readLine();
        }
        if (result.rows.isEmpty())
        {
            result.errors.add("no planes found in CSV");
            return result;
        }
        boolean anyPlane = false;
        for (int i = 0; i < result.rows.size(); i++)
        {
            if (result.rows.get(i).plane != null)
            {
                anyPlane = true;
            }
        }
        if (!anyPlane)
        {
            result.errors.add("none of the " + result.rows.size() + " rows describes a usable plane");
        }
        return result;
    }

    /**
     * @param (int) row data row number
     * @param (int) lineNo file line number
     * @param (String) text the line, for messages
     * @param (double[]) values parsed numbers, or null if the line has non-numbers
     * @param (double) eps geometric tolerance
     * @return the row entry (plane may be null)
     */
    private static Entry toEntry(int row, int lineNo, String text, double[] values, double eps)
    {
        List<String> warnings = new ArrayList<String>();
        if (values == null)
        {
            warnings.add("not a list of numbers: \"" + text + "\" -> black frame");
            return new Entry(row, lineNo, null, warnings);
        }
        double[] coords = coordinates(values);
        if (coords == null)
        {
            warnings.add("expected \"3, 9 numbers\", \"4, 12 numbers\", 9 numbers or 12 numbers, got "
                    + values.length + " numbers -> black frame");
            return new Entry(row, lineNo, null, warnings);
        }
        PlaneResolver.Resolved resolved = PlaneResolver.resolve(coords, eps);
        Plane plane = resolved.plane;
        if (resolved.reordering != null)
        {
            warnings.add("points are not valid in the documented order; read as " + resolved.reordering);
        }
        for (int i = 0; i < resolved.problems.size(); i++)
        {
            warnings.add(resolved.problems.get(i));
        }
        if (!plane.isRenderable())
        {
            warnings.add("plane cannot be projected -> black frame");
            return new Entry(row, lineNo, null, warnings);
        }
        if (!resolved.problems.isEmpty())
        {
            warnings.add("projected anyway with the algorithm's rails a1->b, a2->c");
        }
        return new Entry(row, lineNo, plane, warnings);
    }

    /**
     * Strips the optional leading point count of the course format.
     *
     * @param (double[]) v all numbers of the row
     * @return the 9 or 12 coordinates, or null when the count does not fit any format
     */
    private static double[] coordinates(double[] v)
    {
        if (v.length == 1 + TRIANGLE_POINTS * COORDS && v[0] == TRIANGLE_POINTS)
        {
            return tail(v);
        }
        if (v.length == 1 + TRAPEZOID_POINTS * COORDS && v[0] == TRAPEZOID_POINTS)
        {
            return tail(v);
        }
        if (v.length == TRIANGLE_POINTS * COORDS || v.length == TRAPEZOID_POINTS * COORDS)
        {
            return v;
        }
        return null;
    }

    /**
     * @param (double[]) v array
     * @return v without its first element
     */
    private static double[] tail(double[] v)
    {
        double[] t = new double[v.length - 1];
        System.arraycopy(v, 1, t, 0, t.length);
        return t;
    }

    /**
     * @param (String) text line
     * @return true for lines like ",,,," (empty spreadsheet rows)
     */
    private static boolean isOnlySeparators(String text)
    {
        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (ch != ',' && ch != ';' && !Character.isWhitespace(ch))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @param (String[]) tokens split line (empty tokens are ignored)
     * @return numbers, or null if any token is not a number
     */
    private static double[] parse(String[] tokens)
    {
        int count = 0;
        for (int i = 0; i < tokens.length; i++)
        {
            if (!tokens[i].isEmpty())
            {
                count++;
            }
        }
        double[] values = new double[count];
        int k = 0;
        for (int i = 0; i < tokens.length; i++)
        {
            if (tokens[i].isEmpty())
            {
                continue;
            }
            try
            {
                values[k] = Double.parseDouble(tokens[i]);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
            k++;
        }
        return values;
    }
}
