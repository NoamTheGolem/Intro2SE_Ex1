package ex1.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ex1.core.Plane;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

/**
 * CSV rules (SRS REQ-04, REQ-05, REQ-07, ADR-0003): the course's Ex0.csv format, the plain
 * format, one entry per row, doubtful rows drawn with a warning, bad rows black with a warning.
 */
public class PlaneCsvReaderTest
{
    /** The course's example file Ex0.csv, exactly as exported from the linked Google Sheet. */
    static final String COURSE_CSV =
            "\"Number of Points (3 or 4)\",\"x1\",\"y1\",\"z1\",\"x2\",\"y2\",\"z2\",\"x3\",\"y3\",\"z3\",\"x4\",\"y4\",\"z4\"\n"
            + "\"3\",\"200\",\"200\",\"0\",\"0\",\"200\",\"400\",\"400\",\"200\",\"400\",\"\",\"\",\"\"\n"
            + "\"4\",\"200\",\"0\",\"0\",\"200\",\"0\",\"500\",\"200\",\"400\",\"0\",\"200\",\"400\",\"500\"\n"
            + "\"3\",\"200\",\"200\",\"-30\",\"-50\",\"200\",\"400\",\"400\",\"250\",\"400\",\"\",\"\",\"\"\n"
            + "\"4\",\"200\",\"50\",\"0\",\"200\",\"0\",\"500\",\"200\",\"350\",\"0\",\"200\",\"400\",\"500\"\n";

    /** Rows: a triangle with commas, a rectangle with spaces as separators. */
    private static final String TRIANGLE = "5,0,0, 0,10,0, 10,10,0";
    private static final String RECT     = "0 0 1  10 0 1  0 20 1  10 20 1";

    /**
     * Reads CSV text.
     * @param (String) text CSV content.
     * @return (PlaneCsvReader.Result) Parse result.
     * @throws (IOException) Never for a StringReader.
     */
    private static PlaneCsvReader.Result read(String text) throws IOException
    {
        return PlaneCsvReader.read(new StringReader(text), Plane.DEFAULT_EPS);
    }

    /**
     * Checks the course's file as it is (quotes, the count column, empty cells): 4 rows of 3, 4, 3
     * and 4 points, the coordinates intact (TC-40).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void readsCourseExampleFile() throws IOException
    {
        PlaneCsvReader.Result r = read(COURSE_CSV);
        assertTrue(r.errors.toString(), r.isValid());
        assertEquals(4, r.rows.size());
        assertTrue(r.rows.get(0).plane.isTriangle());
        assertFalse(r.rows.get(1).plane.isTriangle());
        assertTrue(r.rows.get(2).plane.isTriangle());
        assertFalse(r.rows.get(3).plane.isTriangle());
        assertEquals(200.0, r.rows.get(0).plane.a1().x, 0.0);
        assertEquals(500.0, r.rows.get(3).plane.c().z, 0.0);
    }

    /**
     * Checks the course's row 3, doubtful but drawn as given, and row 4, a valid trapezoid with no
     * warning; the messages say where the row is (TC-44).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void courseFileRow3IsDoubtfulAndRow4IsAValidTrapezoid() throws IOException
    {
        PlaneCsvReader.Result r = read(COURSE_CSV);
        assertTrue(r.rows.get(0).warnings.isEmpty());
        assertTrue(r.rows.get(1).warnings.isEmpty());
        // Row 3: |ab| = 497.39, |ac| = 476.86 -> warned, drawn as given.
        assertTrue(r.rows.get(2).warnings.get(0).contains("side lengths differ"));
        assertTrue(r.rows.get(2).warnings.get(1).contains("drawn as given"));
        assertNotNull(r.rows.get(2).plane);
        // Row 4: in the course order (a1, b, a2, c) a perfect isosceles trapezoid, no warning.
        assertTrue(r.rows.get(3).warnings.toString(), r.rows.get(3).warnings.isEmpty());
        assertNotNull(r.rows.get(3).plane);
        assertTrue(r.rows.get(3).plane.validate(Plane.DEFAULT_EPS).isEmpty());
        assertEquals(4, r.planes().size());
        assertEquals(2, r.warnings().size());
        assertTrue(r.warnings().get(0).startsWith("row 3 (line 4):"));
        assertEquals(3, r.rows.get(2).row);
        assertEquals(4, r.rows.get(2).line);
    }

    /**
     * Checks rows of 9 and 12 numbers, spaces as separators, with their line numbers kept (TC-41).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void readsPlainTrianglesAndTrapezoids() throws IOException
    {
        PlaneCsvReader.Result r = read(TRIANGLE + "\n" + RECT + "\n");
        assertTrue(r.errors.toString(), r.isValid());
        assertEquals(2, r.rows.size());
        assertTrue(r.rows.get(0).plane.isTriangle());
        assertFalse(r.rows.get(1).plane.isTriangle());
        assertEquals(2, r.rows.get(1).line);
        assertTrue(r.warnings().isEmpty());
    }

    /**
     * Checks that a byte order mark, the header, comments, blank lines and the empty rows a
     * spreadsheet leaves are skipped (TC-42).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void skipsHeaderCommentsBlankAndEmptySpreadsheetRows() throws IOException
    {
        PlaneCsvReader.Result r = read("﻿ax,ay,az,bx,by,bz,cx,cy,cz\n# comment\n\n,,,,\n" + TRIANGLE + "\n");
        assertTrue(r.errors.toString(), r.isValid());
        assertEquals(1, r.rows.size());
        assertEquals(5, r.rows.get(0).line);
    }

    /**
     * Checks that bad rows between good ones become black frames in their place, so the frames keep
     * the rows' order (TC-45).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void badRowsBecomeBlackFramesInPlace() throws IOException
    {
        PlaneCsvReader.Result r = read(TRIANGLE + "\n1,2,3,4\nabc\n" + TRIANGLE + "\n");
        assertTrue(r.isValid());
        assertEquals(4, r.rows.size());
        assertNotNull(r.rows.get(0).plane);
        assertNull(r.rows.get(1).plane);
        assertNull(r.rows.get(2).plane);
        assertNotNull(r.rows.get(3).plane);
        assertTrue(r.rows.get(1).warnings.get(0).contains("got 4 numbers"));
        assertTrue(r.rows.get(2).warnings.get(0).contains("not a list of numbers"));
    }

    /**
     * Checks that a point count that does not match the numbers given makes a black frame, with a
     * message that says both (TC-43).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void countMustMatchThePointsGiven() throws IOException
    {
        // As in edge_cases.csv frame 9: "4" with the 9 numbers of a triangle is a bad row.
        PlaneCsvReader.Result r = read(TRIANGLE + "\n4," + TRIANGLE + "\n3," + TRIANGLE + ",0,0,0\n5," + TRIANGLE + "\n");
        assertNotNull(r.rows.get(0).plane);
        assertNull(r.rows.get(1).plane);
        assertTrue(r.rows.get(1).warnings.get(0), r.rows.get(1).warnings.get(0).contains("the count says 4 points but 3"));
        assertNull(r.rows.get(2).plane);
        assertTrue(r.rows.get(2).warnings.get(0).contains("the count says 3 points but 4"));
        assertNull(r.rows.get(3).plane);
        // Something that fits no format at all is a black frame too.
        PlaneCsvReader.Result bad = read(TRIANGLE + "\n1,2,3,4,5,6,7,8,9,10,11\n");
        assertNull(bad.rows.get(1).plane);
        assertTrue(bad.rows.get(1).warnings.get(0).contains("got 11 numbers"));
    }

    /**
     * Checks the lecturer's edge_cases.csv: 16 rows, a plane in rows 1-8 and none in 9-16, warnings
     * for rows 7-16 (TC-49).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void lecturerEdgeCasesGiveTheExpectedFrames() throws IOException
    {
        File                  f = new File("data/course/edge_cases.csv");
        PlaneCsvReader.Result r = PlaneCsvReader.read(f, Plane.DEFAULT_EPS);
        assertTrue(r.errors.toString(), r.isValid());
        // "Expected: 16 frames; black: frames 6, 9 to 16; a warning for frames 7 to 16."
        assertEquals(16, r.rows.size());
        for (int i = 0; i < 16; i++)
        {
            int                  frame = i + 1;
            PlaneCsvReader.Entry e     = r.rows.get(i);
            assertEquals("frame " + frame + " drawn", frame <= 8, e.plane != null);
            assertEquals("frame " + frame + " warned: " + e.warnings, frame >= 7, !e.warnings.isEmpty());
        }
    }

    /**
     * Checks that a row whose points are all the same is a black frame, and the file still valid
     * (TC-46).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void degeneratePlaneIsBlackFrame() throws IOException
    {
        PlaneCsvReader.Result r = read(TRIANGLE + "\n3, 1,1,1, 1,1,1, 1,1,1\n");
        assertNull(r.rows.get(1).plane);
        assertTrue(r.isValid());
    }

    /**
     * Checks that a file with only comments or only junk is an error, not a video of black frames
     * (TC-47).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void fileWithoutUsablePlaneIsAnError() throws IOException
    {
        assertFalse(read("# nothing\n").isValid());
        assertFalse(read("1,2,3\nabc\n").isValid());
    }

    /**
     * Checks that oblique, fractional planes written by the app read back with no warning, as the
     * same shapes (TC-48).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void writerOutputSurvivesARoundTrip() throws IOException
    {
        // Oblique, fractional planes (as the app's interpolation produces) must read back as the
        // same valid planes, not as a different reading of the points.
        ex1.app.PlaneSpec spec = new ex1.app.PlaneSpec(101.234567, 77.7, 64.25, 33.3, -21.7, 12.1, 171.3, 0.62, 203.9);
        Plane original = spec.toPlane();
        assertTrue(original.validate(Plane.DEFAULT_EPS).toString(), original.validate(Plane.DEFAULT_EPS).isEmpty());
        String row = ex1.io.PlaneCsvWriter.row(original);
        // Course order: a1, b, a2, c (the second point written is b).
        String[] cells = row.split(",");
        assertEquals("4", cells[0]);
        assertEquals(original.b().x, Double.parseDouble(cells[4]), 1e-6);
        assertEquals(original.a2().z, Double.parseDouble(cells[9]), 1e-6);
        PlaneCsvReader.Result r = read(row + "\n");
        assertTrue(r.rows.get(0).warnings.toString(), r.rows.get(0).warnings.isEmpty());
        assertEquals(original.imageWidth(), r.rows.get(0).plane.imageWidth());
        assertEquals(original.imageHeight(), r.rows.get(0).plane.imageHeight());
        assertEquals(original.a1().x, r.rows.get(0).plane.a1().x, 1e-6);
        // The same plane rounded to 3 decimals (the old writer) would already break the EPS rule.
        ex1.app.PlaneSpec     tri = new ex1.app.PlaneSpec(50.5, 60.5, 70.5, 10, 20, 30, 120.7, 0.0, 99.9);
        PlaneCsvReader.Result t   = read(ex1.io.PlaneCsvWriter.row(tri.toPlane()) + "\n");
        assertTrue(t.rows.get(0).warnings.toString(), t.rows.get(0).warnings.isEmpty());
        assertTrue(t.rows.get(0).plane.isTriangle());
    }

    /**
     * Checks that the lecturer's 8-decimal rows and a plane of 17 significant digits survive a
     * write and a read bit for bit (TC-48).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void lecturerRowsSurviveAWriteReadCycleBitForBit() throws IOException
    {
        // head.csv row 280 (8 decimals) and an interpolated plane (17 significant digits).
        String lecturer = "3,41.64058498,78.64882498,32.22425753,162.61453132,112.46805458,161.14890225,178.2448837,191.38312044,0.12684021,,,";
        Plane  p        = read(lecturer + "\n").rows.get(0).plane;
        Plane  q        = read(ex1.io.PlaneCsvWriter.row(p) + "\n").rows.get(0).plane;
        assertEquals(p.a1().x, q.a1().x, 0.0);
        assertEquals(p.b().y, q.b().y, 0.0);
        assertEquals(p.c().z, q.c().z, 0.0);
        assertTrue(ex1.io.PlaneCsvWriter.row(p), ex1.io.PlaneCsvWriter.row(p).startsWith("3,41.64058498,78.64882498,"));
        Plane odd = Plane.triangle(new ex1.core.Vec3(Math.PI, Math.E, 1.0 / 3.0), new ex1.core.Vec3(0, 100, 0.1),
                new ex1.core.Vec3(1e-7, 100, 120.5));
        Plane back = read(ex1.io.PlaneCsvWriter.row(odd) + "\n").rows.get(0).plane;
        assertEquals(odd.a1().x, back.a1().x, 0.0);
        assertEquals(odd.a1().z, back.a1().z, 0.0);
        assertEquals(odd.b().x, back.b().x, 0.0);
        assertEquals(odd.c().x, back.c().x, 0.0);
    }
}
