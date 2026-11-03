package ex1.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Reading the points of a CSV row (ADR-0004): documented order first, other readings only when
 * the documented one breaks the rules.
 */
public class PlaneResolverTest
{
    private static final double EPS = Plane.DEFAULT_EPS;

    @Test
    public void validDocumentedOrderIsKept()
    {
        // Ex0.csv row 2: a rectangle, valid as (a1, a2, b, c).
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {200, 0, 0, 200, 0, 500, 200, 400, 0, 200, 400, 500}, EPS);
        assertTrue(r.isValid());
        assertNull(r.reordering);
        assertEquals(200.0, r.plane.a2().x, 0.0);
        assertEquals(500.0, r.plane.a2().z, 0.0);
    }

    @Test
    public void ex0Row4IsReadAsTwoRails()
    {
        // Ex0.csv row 4: invalid as (a1, a2, b, c) [300 vs 400], valid as rails (p1->p2, p3->p4).
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {200, 50, 0, 200, 0, 500, 200, 350, 0, 200, 400, 500}, EPS);
        assertTrue(r.problems.toString(), r.isValid());
        assertNotNull(r.reordering);
        assertTrue(r.reordering, r.reordering.contains("p1->p2 and p3->p4"));
        assertEquals(502.49, r.plane.a1().dist(r.plane.b()), 0.01);
        assertEquals(502.49, r.plane.a2().dist(r.plane.c()), 0.01);
        // Shorter base on top: |a1a2| = 300 <= |bc| = 400.
        assertEquals(300.0, r.plane.a1().dist(r.plane.a2()), 1e-9);
        assertEquals(400.0, r.plane.b().dist(r.plane.c()), 1e-9);
    }

    @Test
    public void ex0Row3StaysInvalidInEveryReading()
    {
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {200, 200, -30, -50, 200, 400, 400, 250, 400}, EPS);
        assertFalse(r.isValid());
        assertNull(r.reordering);
        assertTrue(r.plane.isRenderable());
    }

    @Test
    public void triangleApexIsFoundWhenListedSecond()
    {
        // (b, a, c): apex is the second point.
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {0, 10, 0, 5, 0, 0, 10, 10, 0}, EPS);
        assertTrue(r.isValid());
        assertNotNull(r.reordering);
        assertEquals(5.0, r.plane.a1().x, 0.0);
        assertEquals(0.0, r.plane.a1().y, 0.0);
    }

    @Test
    public void crossedRailsAreNotAccepted()
    {
        // Rails p1->p4 and p2->p3 of a rectangle are its diagonals: equal, but the rectangle's own
        // reading is valid and must win.
        PlaneResolver.Resolved r = PlaneResolver.resolve(
                new double[] {0, 0, 0, 10, 0, 0, 0, 20, 0, 10, 20, 0}, EPS);
        assertNull(r.reordering);
        assertEquals(21, r.plane.imageHeight());
    }
}
