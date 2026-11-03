package ex1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the raw points of one CSV row into the plane that will be projected (ADR-0004).
 *
 * The assignment names the points (a, b, c) and (a1, a2, b, c), but a CSV row only has columns
 * x1..x4, so the order is a convention. The course's own example file (Ex0.csv) lists its
 * 4-point rows as two rails, (a1, b) then (a2, c): read in the documented order its row 4 is
 * not isosceles, read as rails it is a perfect trapezoid. This resolver therefore:
 * <ol>
 *   <li>uses the documented order when it satisfies the Q&amp;A rules;</li>
 *   <li>otherwise tries the other readings of the same points (rails order for 4 points,
 *       another apex for 3 points) and uses the first valid one, noting the reordering;</li>
 *   <li>otherwise keeps the documented order and reports the rule violations; the algorithm is
 *       still well defined, so the caller may project the plane anyway.</li>
 * </ol>
 */
public final class PlaneResolver
{
    /** What the resolver decided for one row. */
    public static final class Resolved
    {
        /** The plane to project, in the chosen reading. */
        public final Plane plane;
        /** How the points were read, e.g. "a1,a2,b,c" or "a1,b,a2,c"; null when the documented order was used. */
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
     * @param (double[]) coords 9 or 12 coordinates of the row
     * @param (double) eps geometric tolerance
     * @return the chosen reading (never null)
     */
    public static Resolved resolve(double[] coords, double eps)
    {
        Plane documented = Plane.fromValues(coords);
        List<String> problems = documented.validate(eps);
        if (problems.isEmpty())
        {
            return new Resolved(documented, null, problems);
        }
        List<Plane> alternatives = new ArrayList<Plane>();
        List<String> names = new ArrayList<String>();
        if (documented.isTriangle())
        {
            Vec3 p1 = documented.a1();
            Vec3 p2 = documented.b();
            Vec3 p3 = documented.c();
            alternatives.add(Plane.triangle(p2, p1, p3));
            names.add("apex is point 2: a=p2, b=p1, c=p3");
            alternatives.add(Plane.triangle(p3, p1, p2));
            names.add("apex is point 3: a=p3, b=p1, c=p2");
        }
        else
        {
            Vec3 p1 = documented.a1();
            Vec3 p2 = documented.a2();
            Vec3 p3 = documented.b();
            Vec3 p4 = documented.c();
            // Two rails listed one after the other: (p1 -> p2) and (p3 -> p4).
            alternatives.add(orient(p1, p3, p2, p4));
            names.add("two rails p1->p2 and p3->p4: a1=p1, a2=p3, b=p2, c=p4");
            // Rails (p1 -> p4) and (p2 -> p3).
            alternatives.add(orient(p1, p2, p4, p3));
            names.add("two rails p1->p4 and p2->p3: a1=p1, a2=p2, b=p4, c=p3");
        }
        for (int i = 0; i < alternatives.size(); i++)
        {
            Plane candidate = alternatives.get(i);
            List<String> candidateProblems = candidate.validate(eps);
            if (candidateProblems.isEmpty())
            {
                return new Resolved(candidate, names.get(i), candidateProblems);
            }
        }
        return new Resolved(documented, null, problems);
    }

    /**
     * Builds the trapezoid with the shorter base on top (the Q&amp;A requires |a1a2| &lt;= |bc|),
     * flipping both rails when needed.
     *
     * @param (Vec3) a1 start of rail 1
     * @param (Vec3) a2 start of rail 2
     * @param (Vec3) b end of rail 1
     * @param (Vec3) c end of rail 2
     * @return trapezoid (a1, a2, b, c) or its flipped version (b, c, a1, a2)
     */
    private static Plane orient(Vec3 a1, Vec3 a2, Vec3 b, Vec3 c)
    {
        if (a1.dist(a2) <= b.dist(c))
        {
            return Plane.trapezoid(a1, a2, b, c);
        }
        return Plane.trapezoid(b, c, a1, a2);
    }
}
