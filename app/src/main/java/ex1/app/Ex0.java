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

import java.awt.GraphicsEnvironment;
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

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Exit codes: 0 done, 1 wrong usage, 2 bad input, 3 the video could not be written. */
    private static final int EXIT_OK        = 0;
    private static final int EXIT_USAGE     = 1;
    private static final int EXIT_BAD_INPUT = 2;
    private static final int EXIT_OUTPUT    = 3;

    /** Limits and defaults of the options. */
    private static final int DEFAULT_FPS = 10;   // frames per second of the video
    private static final int MAX_FPS     = 120;
    private static final int MAX_THREADS = 256;

    /** Nanoseconds per millisecond, for the timing line. */
    private static final double NANOS_PER_MS = 1e6;

    /** Printed after an error of usage. */
    private static final String USAGE =
            "Usage: java -jar Ex0.jar <file.nrrd> <file.csv> <file.mp4> [options]\n"
            + "       java -jar Ex0.jar [file.nrrd]   (opens the app: choose 3 points, see the model cut open)\n"
            + "Options:\n"
            + "  --interp nearest|trilinear   sampling (default nearest)\n"
            + "  --fps N                      video frame rate (default 10)\n"
            + "  --window LO HI               intensity window (default: 0.5-99.5 percentiles)\n"
            + "  --frames DIR                 also save every frame as PNG in DIR\n"
            + "  --threads N|auto             worker threads for the projection (default 1)\n"
            + "  --no-fps                     no FPS label on the frames (clean frames, e.g. to compare them)\n"
            + "  --strict                     stop (no video) if any CSV row breaks the plane rules\n";

    /** Parsed command-line options. */
    private static final class Options
    {
        /** The three files, then the options with their defaults (see USAGE). */
        File                    nrrd;
        File                    csv;
        File                    mp4;
        Projector.Interpolation interp  = Projector.Interpolation.NEAREST;
        int                     fps     = DEFAULT_FPS;
        double[]                window;
        File                    framesDir;
        boolean                 strict;
        int                     threads = 1;
        boolean                 showFps = true;
    }

    /** Thrown for problems that map to a specific exit code. */
    static final class ExitException extends Exception
    {
        /** Version of the serialized form (exceptions are Serializable). */
        private static final long serialVersionUID = 1L;

        /** The exit code the program ends with. */
        final int code;

        /**
         * Creates the exception with its exit code.
         * @param (int) code Process exit code.
         * @param (String) message Message for stderr.
         */
        ExitException(int code, String message)
        {
            super(message);
            this.code = code;
        }
    }

    /** A program: main and its helpers only; never instantiated. */
    private Ex0()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Program entry point: nothing, or one file (a volume), opens the app; three files run the
     * command-line demo of the assignment (volume, CSV of planes, output video) - CSV files are
     * read only here.
     * @param (String[]) args The NRRD, CSV and MP4 files, then the options.
     */
    public static void main(String[] args)
    {
        if (opensTheApp(args))
        {
            if (GraphicsEnvironment.isHeadless())
            {
                System.err.println("No display available for the window app.");
                System.err.print(USAGE);
                System.exit(EXIT_USAGE);
            }
            File volume = null;
            if (args.length == 1)
            {
                volume = new File(args[0]);
            }
            StudioApp.launch(volume);
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
     * Runs the whole pipeline; separate from main so tests can call it without System.exit.
     * @param (String[]) args Command-line arguments.
     * @throws (ExitException) On any failure, carrying the exit code.
     */
    static void run(String[] args) throws ExitException
    {
        Options opt = parseArgs(args);
        Volume volume = loadVolume(opt.nrrd);
        List<Plane> planes = loadPlanes(opt.csv, opt.strict);
        IntensityWindow window = chooseWindow(volume, opt);
        FrameRenderer renderer = new FrameRenderer(volume, planes, window, Projector.create(opt.interp, opt.threads));
        renderer.setShowFps(opt.showFps);
        System.out.println("Volume  : " + opt.nrrd.getName() + " " + volume);
        System.out.println("Planes  : " + planes.size() + " from " + opt.csv.getName());
        System.out.println("Window  : " + window);
        String label = ", the projection's FPS on every frame";
        if (!opt.showFps)
        {
            label = "";
        }
        System.out.println("Frames  : " + renderer.width() + "x" + renderer.height() + " @ " + opt.fps + " fps, "
                + opt.interp.name().toLowerCase(Locale.ROOT) + ", " + opt.threads + " thread(s)" + label);
        renderVideo(renderer, opt);
    }

    /**
     * Decides between the window app and the batch demo: nothing, or one file (a scan), opens the
     * app; anything more is the demo's command line.
     * @param (String[]) args Command-line arguments.
     * @return (boolean) True when the arguments ask for the window app rather than the batch demo.
     */
    private static boolean opensTheApp(String[] args)
    {
        if (args.length > 1)
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

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Reads the three files and the options.
     * @param (String[]) args Command-line arguments.
     * @return (Options) Parsed options.
     * @throws (ExitException) EXIT_USAGE on bad arguments.
     */
    private static Options parseArgs(String[] args) throws ExitException
    {
        List<String> positional = new ArrayList<String>();
        Options      opt        = new Options();
        int          i          = 0;
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
            else if (a.equals("--no-fps"))
            {
                opt.showFps = false;
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
     * Loads the NRRD volume, turning every problem into exit code 2 with a message.
     * @param (File) file NRRD file.
     * @return (Volume) Loaded volume.
     * @throws (ExitException) EXIT_BAD_INPUT if it cannot be read.
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
     * @param (File) file CSV file.
     * @param (boolean) strict True to treat warnings as errors.
     * @return (List<Plane>) Planes in file order; null = black frame.
     * @throws (ExitException) EXIT_BAD_INPUT if the file is missing or has no usable plane.
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
     * Chooses the grey window: the one given with --window, else the volume's 0.5-99.5 percentiles.
     * @param (Volume) volume Loaded volume.
     * @param (Options) opt Options (explicit --window wins).
     * @return (IntensityWindow) Intensity window for the whole video (decision D7).
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
     * for the whole run. If ffmpeg fails half-way (a broken or blocked copy on this computer),
     * the same frames are written again with the built-in encoder, so the video is never lost
     * to another program.
     * @param (FrameRenderer) renderer Turns plane i into frame i.
     * @param (Options) opt Options.
     * @throws (ExitException) EXIT_OUTPUT on write failures.
     */
    private static void renderVideo(FrameRenderer renderer, Options opt) throws ExitException
    {
        int  fw = renderer.width();
        int  fh = renderer.height();
        long projectNanos;
        try
        {
            VideoWriter mp4 = VideoWriters.openMp4(opt.mp4, fw, fh, opt.fps);
            try
            {
                projectNanos = writeFrames(renderer, opt, mp4);
            }
            catch (IOException ffmpegFailed)
            {
                if (!VideoWriters.usesFfmpeg(mp4))
                {
                    throw ffmpegFailed;
                }
                System.err.println("Warning : " + ffmpegFailed.getMessage().trim()
                        + " - writing the video with the built-in encoder instead");
                projectNanos = writeFrames(renderer, opt, VideoWriters.openBuiltInMp4(opt.mp4, fw, fh, opt.fps));
            }
        }
        catch (IOException e)
        {
            throw new ExitException(EXIT_OUTPUT, e.getMessage());
        }
        double ms = projectNanos / NANOS_PER_MS;
        System.out.printf(Locale.ROOT, "Projection: %d frames in %.1f ms (%.0f fps, I/O excluded)%n",
                renderer.frameCount(), ms, renderer.frameCount() * 1000.0 / Math.max(ms, 1e-3));
        System.out.println("Wrote   : " + opt.mp4);
    }

    /**
     * Renders every frame once and hands it to every writer (the MP4 and, with --frames, the PNGs).
     * @param (FrameRenderer) renderer Turns plane i into frame i.
     * @param (Options) opt Options (--frames adds a PNG writer).
     * @param (VideoWriter) mp4 The open MP4 writer; closed here, also on failure.
     * @return (long) Nanoseconds spent projecting.
     * @throws (IOException) When a frame cannot be written.
     */
    private static long writeFrames(FrameRenderer renderer, Options opt, VideoWriter mp4) throws IOException
    {
        List<VideoWriter> writers = new ArrayList<VideoWriter>();
        writers.add(mp4);
        long projectNanos = 0;
        try
        {
            if (opt.framesDir != null)
            {
                writers.add(new PngFrameWriter(opt.framesDir, renderer.width(), renderer.height()));
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
            throw e;
        }
        return projectNanos;
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Reads the value that follows an option.
     * @param (String[]) args Arguments.
     * @param (int) i Index of an option that needs one value.
     * @return (String) The value after it.
     * @throws (ExitException) EXIT_USAGE if missing.
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
     * Parses an option's number.
     * @param (String) s Text.
     * @param (String) option Option name for the message.
     * @return (double) Parsed number.
     * @throws (ExitException) EXIT_USAGE if not a number.
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
     * @param (List<VideoWriter>) writers Writers to close.
     * @throws (IOException) First close failure.
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
     * Closes the writers after a failure, keeping the first error as the one reported.
     * @param (List<VideoWriter>) writers Writers to close, ignoring errors (already failing).
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
