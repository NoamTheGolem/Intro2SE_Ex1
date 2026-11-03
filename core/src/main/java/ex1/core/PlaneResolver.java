package ex1.core;

import java.util.List;

/**
 * Turns the raw points of one CSV row into the plane that will be projected (ADR-0004).
 *
 * A CSV row only has columns x1..x4, so the order of the points is a convention. The course's
 * data files use one convention throughout, written down in its edge-case file
 * ("3 for a triangle a, b, c, or 4 for a trapezoid a', b, a'', c"): a trapezoid is listed as its
 * two rails, a1 -> b then a2 -> c. That is the order every file of the lecturer's data set
 * (Ex0.csv, head.csv, biscuit_cake.csv, edge_cases.csv, ...) is valid in, and the order in which
 * head.csv's slices come out the right way up. The resolver therefore:
 * <ol>
 *   <li>reads 3 points as (a, b, c) and 4 points as (a1, b, a2, c), the course order;</li>
 *   <li>if a 4-point row breaks the rules in the course order but is a valid trapezoid when read
 *       in the order the Q&amp;A names the points, (a1, a2, b, c), uses that reading and says so;</li>
 *   <li>otherwise keeps the course order: the caller draws a doubtful plane as given (with
 *       warnings) and turns a bad one into a black frame.</li>
 * </ol>
 * It never guesses beyond that (no other apex, no other pairing of the points): "in the face of
 * ambiguity, refuse the temptation to guess".
 */
public final class PlaneResolver
{
    /** The order the course's files list four points in. */
    public static final String COURSE_ORDER = "a1, b, a2, c";

    /** The order the assignment's Q&amp;A names four points in. */
    public static final String QA_ORDER = "a1, a2, b, c";

    /** What the resolver decided for one row. */
    public static final class Resolved
    {
        /** The plane to project, in the chosen reading. */
        public final Plane plane;
        /** A note when the points were not read in the course order; null otherwise. */
        public final String reordering;
        /** Rule violations of the chosen reading; empty when it is valid. */
        public final List<String> problems;

        /**
         * @param (Plane) plane chosen plane
         * @param (String) reordering description of a non-default reading, or null
         * @param (List) problems remaining rule violations
         */
        Resolved(Plane plane, String reordering, List<String> problems)
        {
            this.plane = plane;
            this.reordering = reordering;
            this.problems = problems;
        }

        /**
         * @return true when the chosen reading satisfies the Q&amp;A rules
         */
        public boolean isValid()
        {
            return problems.isEmpty();
        }
    }

    private PlaneResolver()
    {
    }

    /**
     * @param (double[]) coords 9 or 12 coordinates of the row, points in file order
     * @param (double) eps geometric tolerance
     * @return the chosen reading (never null)
     */
    public static Resolved resolve(double[] coords, double eps)
    {
        Plane course = fromCourseOrder(coords);
        List<String> problems = course.validate(eps);
        if (problems.isEmpty() || course.isTriangle())
        {
            return new Resolved(course, null, problems);
        }
        Plane qa = Plane.fromValues(coords);
        if (qa.validate(eps).isEmpty())
        {
            return new Resolved(qa, "points read in the Q&A order (" + QA_ORDER + "); in the course order ("
                    + COURSE_ORDER + ") they are not a valid trapezoid", qa.validate(eps));
        }
        return new Resolved(course, null, problems);
    }

    /**
     * @param (double[]) coords 9 or 12 coordinates, points in file order
     * @return triangle (p1, p2, p3) or trapezoid a1 = p1, b = p2, a2 = p3, c = p4
     */
    public static Plane fromCourseOrder(double[] coords)
    {
        if (coords.length == 12)
        {
            Vec3 p1 = new Vec3(coords[0], coords[1], coords[2]);
            Vec3 p2 = new Vec3(coords[3], coords[4], coords[5]);
            Vec3 p3 = new Vec3(coords[6], coords[7], coords[8]);
            Vec3 p4 = new Vec3(coords[9], coords[10], coords[11]);
            return Plane.trapezoid(p1, p3, p2, p4);
        }
        return Plane.fromValues(coords);
    }
}
