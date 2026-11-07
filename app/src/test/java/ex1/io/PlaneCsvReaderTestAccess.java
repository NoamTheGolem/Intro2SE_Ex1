package ex1.io;

/**
 * Gives tests in other packages the course's example CSV (single source of truth).
 */
public final class PlaneCsvReaderTestAccess
{
    /** Static methods only; never instantiated. */
    private PlaneCsvReaderTestAccess()
    {
    }

    /**
     * Gives the course's own Ex0.csv, as it ships in data/.
     * @return (String) Content of the course's Ex0.csv.
     */
    public static String courseCsv()
    {
        return PlaneCsvReaderTest.COURSE_CSV;
    }
}
