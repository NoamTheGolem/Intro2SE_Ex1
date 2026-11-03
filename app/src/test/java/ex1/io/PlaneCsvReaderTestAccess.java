package ex1.io;

/**
 * Gives tests in other packages the course's example CSV (single source of truth).
 */
public final class PlaneCsvReaderTestAccess
{
    private PlaneCsvReaderTestAccess()
    {
    }

    /**
     * @return content of the course's Ex0.csv
     */
    public static String courseCsv()
    {
        return PlaneCsvReaderTest.COURSE_CSV;
    }
}
