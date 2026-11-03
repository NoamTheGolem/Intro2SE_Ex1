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
    private static final double EPS = Plane.DEFAULT_EPS;

    @Test
    public void validIsoscelesTriangle()
    {
        Plane p = Plane.triangle(new Vec3(5, 0, 0), new Vec3(0, 10, 0), new Vec3(10, 10, 0));
        assertTrue(p.validate(EPS).isEmpty());
    }

    @Test
    public void rejectsNonIsoscelesTriangle()
    {
        Plane p = Plane.triangle(new Vec3(0, 0, 0), new Vec3(0, 10, 0), new Vec3(20, 10, 0));
        List<String> errors = p.validate(EPS);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("side lengths differ"));
    }

    @Test
    public void acceptsDifferenceJustBelowEps()
    {
        Plane p = Plane.triangle(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(0, 10.0009, 0));
        assertTrue(p.validate(EPS).isEmpty());
    }

    @Test
    public void rejectsCollinearTriangle()
    {
        Plane p = Plane.triangle(new Vec3(5, 5, 5), new Vec3(0, 5, 5), new Vec3(10, 5, 5));
        assertTrue(p.validate(EPS).toString().contains("collinear"));
    }

    @Test
    public void rejectsTinyPlane()
    {
        Plane p = Plane.triangle(new Vec3(0, 0, 0), new Vec3(0.3, 0.3, 0), new Vec3(0.3, -0.3, 0));
        assertTrue(p.validate(EPS).toString().contains("too small"));
    }

    @Test
    public void rejectsNaN()
    {
        Plane p = Plane.triangle(new Vec3(Double.NaN, 0, 0), new Vec3(0, 10, 0), new Vec3(10, 10, 0));
        assertTrue(p.validate(EPS).toString().contains("finite"));
    }

    @Test
    public void validRectangleAsTrapezoid()
    {
        Plane p = Plane.trapezoid(new Vec3(0, 0, 3), new Vec3(10, 0, 3), new Vec3(0, 20, 3), new Vec3(10, 20, 3));
        assertTrue(p.validate(EPS).isEmpty());
        assertEquals(11, p.imageWidth());
        assertEquals(21, p.imageHeight());
    }

    @Test
    public void rejectsTopWiderThanBase()
    {
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(20, 0, 0), new Vec3(5, 10, 0), new Vec3(15, 10, 0));
        assertTrue(p.validate(EPS).toString().contains("must be <="));
    }

    @Test
    public void rejectsNonCoplanarTrapezoid()
    {
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(0, 10, 0), new Vec3(10, 10, 5));
        assertTrue(p.validate(EPS).toString().contains("coplanar"));
    }

    @Test
    public void imageSizeIncludesBothEnds()
    {
        // |ab| = 10 -> rows 0..10, |bc| = 12 -> columns 0..12 (decisions D1, D5).
        Plane p = Plane.triangle(new Vec3(6, 0, 0), new Vec3(0, 8, 0), new Vec3(12, 8, 0));
        assertEquals(13, p.imageWidth());
        assertEquals(11, p.imageHeight());
    }

    @Test
    public void fromValuesBuildsBothShapes()
    {
        assertTrue(Plane.fromValues(new double[9]).isTriangle());
        assertFalse(Plane.fromValues(new double[12]).isTriangle());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromValuesRejectsWrongCount()
    {
        Plane.fromValues(new double[10]);
    }

    @Test
    public void nonIsoscelesPlaneIsInvalidButRenderable()
    {
        // Row 3 of the course's Ex0.csv.
        Plane p = Plane.triangle(new Vec3(200, 200, -30), new Vec3(-50, 200, 400), new Vec3(400, 250, 400));
        assertFalse(p.validate(EPS).isEmpty());
        assertTrue(p.isRenderable());
    }

    @Test
    public void imageWidthCoversEveryRowOfANonIsoscelesPlane()
    {
        // Row 4 of Ex0.csv: left rail 300 long, right rail 400 long.
        Plane p = Plane.trapezoid(new Vec3(200, 50, 0), new Vec3(200, 0, 500),
                new Vec3(200, 350, 0), new Vec3(200, 400, 500));
        assertEquals(301, p.imageHeight());
        assertTrue(p.imageWidth() >= 503);
    }

    @Test
    public void degenerateOrHugePlanesAreNotRenderable()
    {
        assertFalse(Plane.triangle(new Vec3(1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 1)).isRenderable());
        assertFalse(Plane.triangle(new Vec3(0, 0, 0), new Vec3(-1e9, 1e9, 0), new Vec3(1e9, 1e9, 0)).isRenderable());
        assertFalse(Plane.triangle(new Vec3(Double.NaN, 0, 0), new Vec3(0, 1, 0), new Vec3(1, 1, 0)).isRenderable());
    }

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

    @Test
    public void topNotParallelToBaseIsDoubtfulButDrawn()
    {
        // edge_cases.csv frame 8 in the course order: equal legs of 130, a2 16 lower than a1.
        Plane p = Plane.trapezoid(new Vec3(100, 40, 150), new Vec3(100, 112, 134),
                new Vec3(100, -10, 30), new Vec3(100, 190, 30));
        assertTrue(p.validate(EPS).toString(), p.validate(EPS).toString().contains("not parallel"));
        assertTrue(p.isRenderable());
    }

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
        assertTrue(same.whyNotRenderable().contains("collinear"));
        assertFalse(line.isRenderable());
        assertFalse(lifted.isRenderable());
        assertTrue(lifted.whyNotRenderable().contains("not on one plane"));
        assertFalse(huge.isRenderable());
        assertTrue(huge.whyNotRenderable().contains("10200"));
        // A valid plane outside the volume is still renderable (it is simply black).
        assertTrue(Plane.triangle(new Vec3(99.5, 0, 300), new Vec3(0, 179, 300), new Vec3(199, 179, 300)).isRenderable());
    }

    @Test
    public void bowTieWhoseBasesCrossIsBad()
    {
        // Rails do not touch, but the top a1-a2 and the base b-c cross.
        Plane p = Plane.trapezoid(new Vec3(0, 0, 0), new Vec3(10, 20, 0), new Vec3(0, 20, 0), new Vec3(10, 0, 0));
        assertFalse(p.isRenderable());
        assertEquals("the sides cross each other", p.whyNotRenderable());
    }
}
