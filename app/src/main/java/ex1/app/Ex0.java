package ex1.app;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Volume;
import ex1.io.NrrdReader;
import ex1.io.PlaneCsvReader;
import ex1.io.PngFrameWriter;
import ex1.io.VideoWriter;
import ex1.io.VideoWriters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Demo: java -jar Ex0.jar file.nrrd file.csv file.mp4 [options]
 *
 * Loads a volume, projects it on every plane of the CSV (in order) and writes one video
 * frame per plane. See docs/SRS.md §3 for options and §5.2 for exit codes.
 */
public final class Ex0
{
    private static final int EXIT_OK = 0;
    private static final int EXIT_USAGE = 1;
    private static final int EXIT_BAD_INPUT = 2;
    private static final int EXIT_OUTPUT = 3;

    private static final int DEFAULT_FPS = 10;
    private static final int MAX_FPS = 120;
    private static final int MAX_THREADS = 256;
    private static final double NANOS_PER_MS = 1e6;

    private static final String USAGE =
            "Usage: java -jar Ex0.jar <file.nrrd> <file.csv> <file.mp4> [options]\n"
            + "       java -jar Ex0.jar [file.nrrd [file.csv]]   (opens the app: cut the model interactively)\n"
            + "Options:\n"
            + "  --interp nearest|trilinear   sampling (default nearest)\n"
            + "  --fps N                      video frame rate (default 10)\n"
            + "  --window LO HI               intensity window (default: 0.5-99.5 percentiles)\n"
            + "  --frames DIR                 also save every frame as PNG in DIR\n"
            + "  --threads N|auto             worker threads for the projection (default 1)\n"
            + "  --strict                     stop (no video) if any CSV row breaks the plane rules\n";

    /** Parsed command-line options. */
    private static final class Options
    {
        File nrrd;
        File csv;
        File mp4;
        Projector.Interpolation interp = Projector.Interpolation.NEAREST;
        int fps = DEFAULT_FPS;
        double[] window;
        File framesDir;
        boolean strict;
        int threads = 1;
    }

    /** Thrown for problems that map to a specific exit code. */
    static final class ExitException extends Exception
    {
        private static final long serialVersionUID = 1L;
        final int code;

        /**
         * @param (int) code process exit code
         * @param (String) message message for stderr
         */
        ExitException(int code, String message)
        {
            super(message);
            this.code = code;
        }
    }

    private Ex0()
    {
    }

    // ===================================================================== ENTRY

    /**
     * No arguments, one file (a volume) or two files (volume + CSV of planes): open the app.
     * Three files: the command-line demo of the assignment (volume, CSV, output video).
     *
     * @param (String[]) args nrrd, csv, mp4, then options
     */
    public static void main(String[] args)
    {
        if (opensTheApp(args))
        {
            if (java.awt.GraphicsEnvironment.isHeadless())
            {
                System.err.println("No display available for the window app.");
                System.err.print(USAGE);
                System.exit(EXIT_USAGE);
            }
            File volume = null;
            File csv = null;
            if (args.length >= 1)
            {
                volume = new File(args[0]);
            }
            if (args.length == 2)
            {
                csv = new File(args[1]);
            }
            StudioApp.launch(volume, csv);
            return;
        }
        int code = EXIT_OK;
        try
        {
            run(args);
        }
        catch (ExitException e)
        {
            System.err.println("Error: " + e.getMessage());
            if (e.code == EXIT_USAGE)
            {
                System.err.print(USAGE);
            }
            code = e.code;
        }
        System.exit(code);
    }

    /**
     * Whole pipeline; separate from main so tests can call it without System.exit.
     *
     * @param (String[]) args command-line arguments
     * @throws (ExitException) on any failure, carrying the exit code
     */
    static void run(String[] args) throws ExitException
    {
        Options opt = parseArgs(args);
        Volume volume = loadVolume(opt.nrrd);
        List<Plane> planes = loadPlanes(opt.csv, opt.strict);
        IntensityWindow window = chooseWindow(volume, opt);
        FrameRenderer renderer = new FrameRenderer(volume, planes, window, Projector.create(opt.interp, opt.threads));
        System.out.println("Volume  : " + opt.nrrd.getName() + " " + volume);
        System.out.println("Planes  : " + planes.size() + " from " + opt.csv.getName());
        System.out.println("Window  : " + window);
        System.out.println("Frames  : " + renderer.width() + "x" + renderer.height() + " @ " + opt.fps + " fps, "
                + opt.interp.name().toLowerCase(Locale.ROOT) + ", " + opt.threads + " thread(s)");
        renderVideo(renderer, opt);
    }

    /**
     * @param (String[]) args command-line arguments
     * @return true when the arguments ask for the window app rather than the batch demo
     */
    private static boolean opensTheApp(String[] args)
    {
        if (args.length > 2)
        {
            return false;
        }
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].startsWith("--"))
            {
                return false;
            }
        }
        return true;
    }

    // ============================================================ BUILD PIPELINE

    /**
     * @param (String[]) args command-line arguments
     * @return parsed options
     * @throws (ExitException) EXIT_USAGE on bad arguments
     */
    private static Options parseArgs(String[] args) throws ExitException
    {
        List<String> positional = new ArrayList<String>();
        Options opt = new Options();
        int i = 0;
        while (i < args.length)
        {
            String a = args[i];
            if (a.equals("--interp"))
            {
                String v = value(args, i).toLowerCase(Locale.ROOT);
                if (v.equals("nearest"))
                {
                    opt.interp = Projector.Interpolation.NEAREST;
                }
                else if (v.equals("trilinear"))
                {
                    opt.interp = Projector.Interpolation.TRILINEAR;
                }
                else
                {
                    throw new ExitException(EXIT_USAGE, "--interp must be nearest or trilinear");
                }
                i += 2;
            }
            else if (a.equals("--fps"))
            {
                opt.fps = (int) number(value(args, i), "--fps");
                if (opt.fps < 1 || opt.fps > MAX_FPS)
                {
                    throw new ExitException(EXIT_USAGE, "--fps must be in [1, " + MAX_FPS + "]");
                }
                i += 2;
            }
            else if (a.equals("--window"))
            {
                if (i + 2 >= args.length)
                {
                    throw new ExitException(EXIT_USAGE, "--window needs two numbers");
                }
                double lo = number(args[i + 1], "--window");
                double hi = number(args[i + 2], "--window");
                if (!(hi > lo))
                {
                    throw new ExitException(EXIT_USAGE, "--window needs LO < HI");
                }
                opt.window = new double[] {lo, hi};
                i += 3;
            }
            else if (a.equals("--threads"))
            {
                String v = value(args, i).toLowerCase(Locale.ROOT);
                if (v.equals("auto"))
                {
                    opt.threads = Runtime.getRuntime().availableProcessors();
                }
                else
                {
                    opt.threads = (int) number(v, "--threads");
                    if (opt.threads < 1 || opt.threads > MAX_THREADS)
                    {
                        throw new ExitException(EXIT_USAGE, "--threads must be in [1, " + MAX_THREADS + "] or auto");
                    }
                }
                i += 2;
            }
            else if (a.equals("--strict"))
            {
                opt.strict = true;
                i++;
            }
            else if (a.equals("--frames"))
            {
                opt.framesDir = new File(value(args, i));
                i += 2;
            }
            else if (a.startsWith("--"))
            {
                throw new ExitException(EXIT_USAGE, "unknown option " + a);
            }
            else
            {
                positional.add(a);
                i++;
            }
        }
        if (positional.size() != 3)
        {
            throw new ExitException(EXIT_USAGE, "expected 3 files (nrrd, csv, mp4), got " + positional.size());
        }
        opt.nrrd = new File(positional.get(0));
        opt.csv = new File(positional.get(1));
        opt.mp4 = new File(positional.get(2));
        return opt;
    }

    /**
     * @param (File) file NRRD file
     * @return loaded volume
     * @throws (ExitException) EXIT_BAD_INPUT if it cannot be read
     */
    private static Volume loadVolume(File file) throws ExitException
    {
        if (!file.isFile())
        {
            throw new ExitException(EXIT_BAD_INPUT, "NRRD file not found: " + file);
        }
        try
        {
            return NrrdReader.read(file);
        }
        catch (IOException e)
        {
            throw new ExitException(EXIT_BAD_INPUT, file.getName() + ": " + e.getMessage());
        }
        catch (OutOfMemoryError e)
        {
            throw new ExitException(EXIT_BAD_INPUT, "not enough memory for " + file.getName()
                    + " - run java with a larger -Xmx");
        }
    }

    /**
     * Reads every CSV row (one row = one frame). Rows that break the plane rules are reported
     * as warnings and still projected; rows that cannot be projected become black frames
     * (decision D11). With --strict any warning stops the run instead.
     *
     * @param (File) file CSV file
     * @param (boolean) strict true to treat warnings as errors
     * @return planes in file order; null = black frame
     * @throws (ExitException) EXIT_BAD_INPUT if the file is missing or has no usable plane
     */
    private static List<Plane> loadPlanes(File file, boolean strict) throws ExitException
    {
        if (!file.isFile())
        {
            throw new ExitException(EXIT_BAD_INPUT, "CSV file not found: " + file);
        }
        PlaneCsvReader.Result result;
        try
        {
            result = PlaneCsvReader.read(file, Plane.DEFAULT_EPS);
        }
        catch (IOException e)
        {
            throw new ExitException(EXIT_BAD_INPUT, file.getName() + ": " + e.getMessage());
        }
        List<String> warnings = result.warnings();
        if (!result.isValid() || (strict && !warnings.isEmpty()))
        {
            List<String> problems = new ArrayList<String>(result.errors);
            problems.addAll(warnings);
            StringBuilder sb = new StringBuilder();
            sb.append(file.getName()).append(" has ").append(problems.size()).append(" problem(s):");
            for (int i = 0; i < problems.size(); i++)
            {
                sb.append("\n  ").append(problems.get(i));
            }
            throw new ExitException(EXIT_BAD_INPUT, sb.toString());
        }
        for (int i = 0; i < warnings.size(); i++)
        {
            System.err.println("Warning : " + warnings.get(i));
        }
        return result.planes();
    }

    /**
     * @param (Volume) volume loaded volume
     * @param (Options) opt options (explicit --window wins)
     * @return intensity window for the whole video (decision D7)
     */
    private static IntensityWindow chooseWindow(Volume volume, Options opt)
    {
        if (opt.window != null)
        {
            return new IntensityWindow(opt.window[0], opt.window[1]);
        }
        return IntensityWindow.fromPercentiles(volume,
                IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
    }

    /**
     * Projects every plane and writes the frames. One image and one frame buffer are reused
     * for the whole run.
     *
     * @param (FrameRenderer) renderer turns plane i into frame i
     * @param (Options) opt options
     * @throws (ExitException) EXIT_OUTPUT on write failures
     */
    private static void renderVideo(FrameRenderer renderer, Options opt) throws ExitException
    {
        int fw = renderer.width();
        int fh = renderer.height();
        List<VideoWriter> writers = new ArrayList<VideoWriter>();
        long projectNanos = 0;
        try
        {
            writers.add(VideoWriters.openMp4(opt.mp4, fw, fh, opt.fps));
            if (opt.framesDir != null)
            {
                writers.add(new PngFrameWriter(opt.framesDir, fw, fh));
            }
            for (int i = 0; i < renderer.frameCount(); i++)
            {
                byte[] frame = renderer.render(i);
                projectNanos += renderer.lastProjectionNanos();
                for (int k = 0; k < writers.size(); k++)
                {
                    writers.get(k).writeFrame(frame);
                }
            }
            closeAll(writers);
        }
        catch (IOException e)
        {
            closeQuietly(writers);
            throw new ExitException(EXIT_OUTPUT, e.getMessage());
        }
        double ms = projectNanos / NANOS_PER_MS;
        System.out.printf(Locale.ROOT, "Projection: %d frames in %.1f ms (%.0f fps, I/O excluded)%n",
                renderer.frameCount(), ms, renderer.frameCount() * 1000.0 / Math.max(ms, 1e-3));
        System.out.println("Wrote   : " + opt.mp4);
    }

    // ========================================================= LOW-LEVEL HELPERS

    /**
     * @param (String[]) args arguments
     * @param (int) i index of an option that needs one value
     * @return the value after it
     * @throws (ExitException) EXIT_USAGE if missing
     */
    private static String value(String[] args, int i) throws ExitException
    {
        if (i + 1 >= args.length)
        {
            throw new ExitException(EXIT_USAGE, args[i] + " needs a value");
        }
        return args[i + 1];
    }

    /**
     * @param (String) s text
     * @param (String) option option name for the message
     * @return parsed number
     * @throws (ExitException) EXIT_USAGE if not a number
     */
    private static double number(String s, String option) throws ExitException
    {
        try
        {
            return Double.parseDouble(s);
        }
        catch (NumberFormatException e)
        {
            throw new ExitException(EXIT_USAGE, option + ": not a number: " + s);
        }
    }

    /**
     * Closes all writers, reporting the first failure after trying every one.
     *
     * @param (List) writers writers to close
     * @throws (IOException) first close failure
     */
    private static void closeAll(List<VideoWriter> writers) throws IOException
    {
        IOException first = null;
        for (int i = 0; i < writers.size(); i++)
        {
            try
            {
                writers.get(i).close();
            }
            catch (IOException e)
            {
                if (first == null)
                {
                    first = e;
                }
            }
        }
        if (first != null)
        {
            throw first;
        }
    }

    /**
     * @param (List) writers writers to close, ignoring errors (already failing)
     */
    private static void closeQuietly(List<VideoWriter> writers)
    {
        try
        {
            closeAll(writers);
        }
        catch (IOException e)
        {
            // The original error is the one worth reporting.
        }
    }
}
