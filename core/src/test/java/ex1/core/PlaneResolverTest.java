package ex1.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Reading the points of a CSV row (ADR-0004): the course order (a, b, c) / (a1, b, a2, c) first,
 * the Q&amp;A order (a1, a2, b, c) only when it is valid and the course order is not, nothing else.
 */
public class PlaneResolverTest
{
    private static final double EPS = Plane.DEFAULT_EPS;

    @Test
    public void fourPointsAreTwoRailsInTheCourseOrder()
    {
        // Ex0.csv row 2: p1 -> p2 and p3 -> p4 are the rails (along z), the bases run along y.
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {200, 0, 0, 200, 0, 500, 200, 400, 0, 200, 400, 500}, EPS);
        assertTrue(r.isValid());
        assertNull(r.reordering);
        assertEquals(500.0, r.plane.b().z, 0.0);
        assertEquals(400.0, r.plane.a2().y, 0.0);
        assertEquals(501, r.plane.imageHeight());
        assertEquals(401, r.plane.imageWidth());
    }

    @Test
    public void ex0Row4IsAValidTrapezoidWithoutAnyNote()
    {
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {200, 50, 0, 200, 0, 500, 200, 350, 0, 200, 400, 500}, EPS);
        assertTrue(r.problems.toString(), r.isValid());
        assertNull(r.reordering);
        assertEquals(502.49, r.plane.a1().dist(r.plane.b()), 0.01);
        assertEquals(300.0, r.plane.a1().dist(r.plane.a2()), 1e-9);
        assertEquals(400.0, r.plane.b().dist(r.plane.c()), 1e-9);
    }

    @Test
    public void headSlicesComeOutTheRightWayUp()
    {
        // head.csv row 1: z = 0, rows must run along y (image down = +y), columns along x.
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {0, 0, 0, 0, 255, 0, 255, 0, 0, 255, 255, 0}, EPS);
        assertTrue(r.isValid());
        assertEquals(255.0, r.plane.b().y, 0.0);
        assertEquals(0.0, r.plane.b().x, 0.0);
        assertEquals(255.0, r.plane.a2().x, 0.0);
    }

    @Test
    public void qaOrderIsUsedOnlyWhenItIsTheValidReading()
    {
        // A trapezoid written as (a1, a2, b, c): top 10..20 at y = 0, base 0..30 at y = 20.
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {10, 0, 0, 20, 0, 0, 0, 20, 0, 30, 20, 0}, EPS);
        assertTrue(r.problems.toString(), r.isValid());
        assertNotNull(r.reordering);
        assertTrue(r.reordering, r.reordering.contains("Q&A order"));
        assertEquals(20.0, r.plane.a2().x, 0.0);
    }

    @Test
    public void triangleIsNeverReordered()
    {
        // (b, a, c): the apex is listed second. The course order is kept and the row is doubtful.
        PlaneResolver.Resolved r = PlaneResolver.resolve(new double[] {0, 10, 0, 5, 0, 0, 10, 10, 0}, EPS);
        assertFalse(r.isValid());
        assertNull(r.reordering);
        assertEquals(0.0, r.plane.a1().x, 0.0);
        assertTrue(r.plane.isRenderable());
    }

    @Test
    public void ex0Row3StaysDoubtfulAndDrawable()
    {
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {200, 200, -30, -50, 200, 400, 400, 250, 400}, EPS);
        assertFalse(r.isValid());
        assertNull(r.reordering);
        assertTrue(r.plane.isRenderable());
    }

    @Test
    public void crossingSidesAreBadInEveryReading()
    {
        // edge_cases.csv frame 15: a' and a'' swapped, so the rails cross.
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {169, 20, 20, 0, 160, 20, 30, 20, 20, 199, 160, 20}, EPS);
        assertFalse(r.isValid());
        assertFalse(r.plane.isRenderable());
        assertEquals("the sides cross each other", r.plane.whyNotRenderable());
    }
}
