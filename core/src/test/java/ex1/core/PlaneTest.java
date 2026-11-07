package ex1.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Validation rules of ALGORITHM.md §6 and the image size rules D1/D5.
 */
public class PlaneTest
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The assignment's EPS. */
    private static final double EPS = Plane.DEFAULT_EPS;

    /** Checks that an isosceles triangle breaks no rule (TC-01). */
    @Test
    public void validIsoscelesTriangle()
    {
        Plane p = Plane.triangle(new Vec3(5, 0, 0), new Vec3(0, 10, 0), new Vec3(10, 10, 0));
        assertTrue(p.validate(EPS).isEmpty());
    }

    /** Checks that a triangle with unequal sides is reported (TC-02). */
    @Test
    public void rejectsNonIsoscelesTriangle()
    {
        Plane        p      = Plane.triangle(new Vec3(0, 0, 0), new Vec3(0, 10, 0), new Vec3(20, 10, 0));
        List<String> errors = p.validate(EPS);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("side lengths differ"));
    }

    /** Checks that sides that differ by less than EPS count as equal (TC-03). */
    @Test
    public void acceptsDifferenceJustBelowEps()
    {
        Plane p = Plane.triangle(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(0, 10.0009, 0));
        assertTrue(p.validate(EPS).isEmpty());
    }

    /** Checks that three points on one line are reported as collinear (TC-04). */
    @Test
    public void rejectsCollinearTriangle()
    {
        Plane p = Plane.triangle(new Vec3(5, 5, 5), new Vec3(0, 5, 5), new Vec3(10, 5, 5));
        assertTrue(p.validate(EPS).toString().contains("collinear"));
    }

    /** Checks that a plane smaller than a voxel is reported as too small (TC-05). */
    @Test
    public void rejectsTinyPlane()
    {
        Plane p = Plane.triangle(new Vec3(0, 0, 0), new Vec3(0.3, 0.3, 0), new Vec3(0.3, -0.3, 0));
        assertTrue(p.validate(EPS).toString().contains("too small"));
    }

    /** Checks that a coordinate that is not a number is reported (TC-06). */
    @Test
    public void rejectsNaN()
    {
        Plane p = Plane.triangle(new Vec3(Double.NaN, 0, 0), new Vec3(0, 10, 0), new Vec3(10, 10, 0));
        assertTrue(p.validate(EPS).toString().contains("finite"));
    }

    /** Checks that a rectangle is a valid trapezoid (TC-07). */
    @Test
    public void validRectangleAsTrapezoid()
    {
        Plane p = Plane.trapezoid(new Vec3(0, 0, 3), new Vec3(10, 0, 3), new Vec3(0, 20, 3), new Vec3(10, 20, 3));
        assertTrue(p.validate(EPS).isEmpty());
        assertEquals(11, p.imageWidth());
        assertEquals(21, p.imageHeight());
    }

    /** Checks that a top wider than its base is reported (TC-08). */
    @Test
    public void rejectsTopWiderThanBase()
    {
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(20, 0, 0), new Vec3(5, 10, 0), new Vec3(15, 10, 0));
        assertTrue(p.validate(EPS).toString().contains("must be <="));
    }

    /** Checks that four points not in one plane are reported (TC-09). */
    @Test
    public void rejectsNonCoplanarTrapezoid()
    {
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(0, 10, 0), new Vec3(10, 10, 5));
        assertTrue(p.validate(EPS).toString().contains("coplanar"));
    }

    /**
     * Checks that the image includes both ends of every side: a side of length 10 gives 11 pixels
     * (TC-07).
     */
    @Test
    public void imageSizeIncludesBothEnds()
    {
        // |ab| = 10 -> rows 0..10, |bc| = 12 -> columns 0..12 (decisions D1, D5).
        Plane p = Plane.triangle(new Vec3(6, 0, 0), new Vec3(0, 8, 0), new Vec3(12, 8, 0));
        assertEquals(13, p.imageWidth());
        assertEquals(11, p.imageHeight());
    }

    /** Checks that 9 numbers make a triangle and 12 a trapezoid (TC-07). */
    @Test
    public void fromValuesBuildsBothShapes()
    {
        assertTrue(Plane.fromValues(new double[9]).isTriangle());
        assertFalse(Plane.fromValues(new double[12]).isTriangle());
    }

    /** Checks that any other count of numbers is refused (TC-07). */
    @Test(expected = IllegalArgumentException.class)
    public void fromValuesRejectsWrongCount()
    {
        Plane.fromValues(new double[10]);
    }

    /** Checks that a plane that is not isosceles is invalid but can still be drawn (TC-08). */
    @Test
    public void nonIsoscelesPlaneIsInvalidButRenderable()
    {
        // Row 3 of the course's Ex0.csv.
        Plane p = Plane.triangle(new Vec3(200, 200, -30), new Vec3(-50, 200, 400), new Vec3(400, 250, 400));
        assertFalse(p.validate(EPS).isEmpty());
        assertTrue(p.isRenderable());
    }

    /** Checks that the image is wide enough for every row of a plane whose rails differ (TC-08). */
    @Test
    public void imageWidthCoversEveryRowOfANonIsoscelesPlane()
    {
        // Row 4 of Ex0.csv: left rail 300 long, right rail 400 long.
        Plane p = Plane.trapezoid(new Vec3(200, 50, 0), new Vec3(200, 0, 500),
                new Vec3(200, 350, 0), new Vec3(200, 400, 500));
        assertEquals(301, p.imageHeight());
        assertTrue(p.imageWidth() >= 503);
    }

    /** Checks that a plane of one point, or one far too large, cannot be drawn (TC-09). */
    @Test
    public void degenerateOrHugePlanesAreNotRenderable()
    {
        assertFalse(Plane.triangle(new Vec3(1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 1)).isRenderable());
        assertFalse(Plane.triangle(new Vec3(0, 0, 0), new Vec3(-1e9, 1e9, 0), new Vec3(1e9, 1e9, 0)).isRenderable());
        assertFalse(Plane.triangle(new Vec3(Double.NaN, 0, 0), new Vec3(0, 1, 0), new Vec3(1, 1, 0)).isRenderable());
    }

    /** Checks that rails that cross (a bow-tie, not a trapezoid) are bad (TC-09). */
    @Test
    public void crossedRailsAreRejected()
    {
        // Equal "rails" that are the diagonals of a rectangle: a bow-tie, not a trapezoid.
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(10, 20, 0), new Vec3(0, 20, 0));
        assertTrue(p.validate(EPS).toString().contains("cross"));
        // The same points as a proper rectangle are fine.
        assertTrue(Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(0, 20, 0), new Vec3(10, 20, 0))
                .validate(EPS).isEmpty());
    }

    /** Checks that a top not parallel to its base is doubtful but drawn (TC-08). */
    @Test
    public void topNotParallelToBaseIsDoubtfulButDrawn()
    {
        // edge_cases.csv frame 8 in the course order: equal legs of 130, a2 16 lower than a1.
        Plane p = Plane.trapezoid(new Vec3(100, 40, 150), new Vec3(100, 112, 134),
                new Vec3(100, -10, 30), new Vec3(100, 190, 30));
        assertTrue(p.validate(EPS).toString(), p.validate(EPS).toString().contains("not parallel"));
        assertTrue(p.isRenderable());
    }

    /**
     * Checks the lecturer's bad planes (edge cases 12, 13, 14 and 16): not in one plane, on one
     * line, far too large (TC-09).
     */
    @Test
    public void nonCoplanarCollinearAndHugePlanesAreBad()
    {
        // edge_cases.csv frames 12, 13, 14, 16.
        Plane same = Plane.triangle(new Vec3(99.5, 0, 80), new Vec3(150, 179, 80), new Vec3(150, 179, 80));
        Plane line = Plane.triangle(new Vec3(0, 0, 80), new Vec3(100, 90, 80), new Vec3(200, 180, 80));
        Plane lifted = Plane.trapezoid(new Vec3(30, 20, 20), new Vec3(169, 20, 30), new Vec3(0, 160, 20),
                new Vec3(199, 160, 20));
        Plane huge = Plane.triangle(new Vec3(99.5, 0, 80), new Vec3(-5000, 179, 80), new Vec3(5199, 179, 80));
        assertFalse(same.isRenderable());
        assertEquals("points b and c are the same point", same.whyNotRenderable());
        assertFalse(line.isRenderable());
        assertTrue(line.whyNotRenderable().contains("collinear"));
        assertFalse(lifted.isRenderable());
        assertTrue(lifted.whyNotRenderable().contains("not on one plane"));
        assertFalse(huge.isRenderable());
        assertTrue(huge.whyNotRenderable().contains("10200"));
        // A valid plane outside the volume is still renderable (it is simply black).
        assertTrue(Plane.triangle(new Vec3(99.5, 0, 300), new Vec3(0, 179, 300), new Vec3(199, 179, 300)).isRenderable());
    }

    /**
     * Checks that a top and a base that cross each other are bad, though the rails do not touch
     * (TC-09).
     */
    @Test
    public void bowTieWhoseBasesCrossIsBad()
    {
        // Rails do not touch, but the top a1-a2 and the base b-c cross.
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(10, 20, 0), new Vec3(0, 20, 0), new Vec3(10, 0, 0));
        assertFalse(p.isRenderable());
        assertEquals("the sides cross each other", p.whyNotRenderable());
    }
}
