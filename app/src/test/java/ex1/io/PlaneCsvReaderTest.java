package ex1.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ex1.core.Plane;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

/**
 * CSV rules (SRS FR-4, FR-5, decision D11): the course's Ex0.csv format, the plain format,
 * one entry per row, warnings instead of aborts.
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

    private static final String TRIANGLE = "5,0,0, 0,10,0, 10,10,0";
    private static final String RECT = "0 0 1  10 0 1  0 20 1  10 20 1";

    /**
     * @param (String) text CSV content
     * @return parse result
     * @throws (IOException) never for a StringReader
     */
    private static PlaneCsvReader.Result read(String text) throws IOException
    {
        return PlaneCsvReader.read(new StringReader(text), Plane.DEFAULT_EPS);
    }

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

    @Test
    public void courseFileRow3IsWarnedButProjectedAndRow4IsReadAsRails() throws IOException
    {
        PlaneCsvReader.Result r = read(COURSE_CSV);
        assertTrue(r.rows.get(0).warnings.isEmpty());
        assertTrue(r.rows.get(1).warnings.isEmpty());
        // Row 3: |ab| = 497.39, |ac| = 476.86 in every reading -> warned, still projected.
        assertTrue(r.rows.get(2).warnings.get(0).contains("side lengths differ"));
        assertNotNull(r.rows.get(2).plane);
        // Row 4: not isosceles as (a1,a2,b,c) but a valid trapezoid as rails (p1->p2, p3->p4).
        assertEquals(1, r.rows.get(3).warnings.size());
        assertTrue(r.rows.get(3).warnings.get(0).contains("read as two rails"));
        assertNotNull(r.rows.get(3).plane);
        assertTrue(r.rows.get(3).plane.validate(Plane.DEFAULT_EPS).isEmpty());
        assertEquals(4, r.planes().size());
        assertTrue(r.warnings().get(0).startsWith("row 3 (line 4):"));
        assertEquals(3, r.rows.get(2).row);
        assertEquals(4, r.rows.get(2).line);
    }

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

    @Test
    public void skipsHeaderCommentsBlankAndEmptySpreadsheetRows() throws IOException
    {
        PlaneCsvReader.Result r = read("﻿ax,ay,az,bx,by,bz,cx,cy,cz\n# comment\n\n,,,,\n" + TRIANGLE + "\n");
        assertTrue(r.errors.toString(), r.isValid());
        assertEquals(1, r.rows.size());
        assertEquals(5, r.rows.get(0).line);
    }

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

    @Test
    public void countColumnMustMatch() throws IOException
    {
        // "4" followed by 9 coordinates: neither format -> black frame.
        PlaneCsvReader.Result r = read(TRIANGLE + "\n4," + TRIANGLE + "\n");
        assertNull(r.rows.get(1).plane);
    }

    @Test
    public void degeneratePlaneIsBlackFrame() throws IOException
    {
        PlaneCsvReader.Result r = read(TRIANGLE + "\n3, 1,1,1, 1,1,1, 1,1,1\n");
        assertNull(r.rows.get(1).plane);
        assertTrue(r.isValid());
    }

    @Test
    public void fileWithoutUsablePlaneIsAnError() throws IOException
    {
        assertFalse(read("# nothing\n").isValid());
        assertFalse(read("1,2,3\nabc\n").isValid());
    }
}
