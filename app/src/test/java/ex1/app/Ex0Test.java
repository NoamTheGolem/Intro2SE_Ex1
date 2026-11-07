package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ex1.core.Volume;
import ex1.core.VoxelType;
import ex1.io.NrrdWriter;
import ex1.io.PlaneCsvReaderTestAccess;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * End-to-end demo test (SRS acceptance criterion 1): run the pipeline on a phantom and check
 * the MP4 with ffprobe. Skipped automatically when ffmpeg/ffprobe are not installed.
 */
public class Ex0Test
{
    @Rule

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Files of one test, deleted after it. */
    public TemporaryFolder tmp = new TemporaryFolder();

    /** The phantom every test of the demo reads. */
    private File nrrd;

    /**
     * Writes the 40^3 phantom every test of the demo reads.
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Before
    public void makePhantom() throws IOException
    {
        Volume v = PhantomGenerator.build("spheres", VoxelType.UINT16, 40);
        nrrd = tmp.newFile("phantom.nrrd");
        NrrdWriter.write(v, nrrd, true, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Checks that the demo writes one frame per CSV row: 7 rotating planes give an MP4 of 7 frames
     * and 7 PNGs (TC-30).
     * @throws (Exception) When a test file cannot be written or a program cannot be run.
     */
    @Test
    public void demoWritesOneFramePerCsvRow() throws Exception
    {
        Assume.assumeTrue("ffmpeg not installed", toolAvailable("ffmpeg"));
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File csv = tmp.newFile("planes.csv");
        SweepGenerator.write("rotate", 40, 40, 40, 7, csv);
        File mp4    = new File(tmp.getRoot(), "out.mp4");
        File frames = new File(tmp.getRoot(), "frames");

        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath(), "--frames", frames.getPath(),
                "--interp", "trilinear"});

        assertTrue(mp4.length() > 0);
        assertEquals("7", ffprobeFrameCount(mp4));
        assertEquals(7, frames.list().length);
    }

    /**
     * Checks that the course's own Ex0.csv gives a video of 4 frames (TC-31).
     * @throws (Exception) When a test file cannot be written or a program cannot be run.
     */
    @Test
    public void courseExampleCsvGivesOneFramePerRow() throws Exception
    {
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File csv = writeText("Ex0.csv", PlaneCsvReaderTestAccess.courseCsv());
        File mp4 = new File(tmp.getRoot(), "course.mp4");
        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath()});
        assertEquals("4", ffprobeFrameCount(mp4));
    }

    /**
     * Checks that the built-in encoder alone writes a video ffprobe reads: 5 frames in, 5 out
     * (TC-34).
     * @throws (Exception) When a test file cannot be written or a program cannot be run.
     */
    @Test
    public void builtInMp4WriterWorksWithoutFfmpeg() throws Exception
    {
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File               mp4 = new File(tmp.getRoot(), "builtin.mp4");
        ex1.io.VideoWriter w   = new ex1.io.Mp4Writer(mp4, 40, 40, 10);
        for (int i = 0; i < 5; i++)
        {
            w.writeFrame(new byte[40 * 40]);
        }
        w.close();
        assertEquals("5", ffprobeFrameCount(mp4));
    }

    /**
     * Checks that an ffmpeg which lists H.264 but fails while encoding only costs a warning: the
     * built-in encoder writes the video, exit code 0 (TC-113).
     * @throws (Exception) When a test file cannot be written or a program cannot be run.
     */
    @Test
    public void brokenFfmpegStillGivesTheVideo() throws Exception
    {
        // An ffmpeg that lists the H.264 encoder but dies when encoding (a broken or blocked copy
        // on the grader's PATH): the demo writes the same frames with the built-in encoder.
        Assume.assumeFalse("the fake ffmpeg is a shell script",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        File   fake   = new File(tmp.getRoot(), "ffmpeg");
        String script = "#!/bin/sh\nif [ \"$2\" = \"-encoders\" ]; then\n  echo ' V....D libx264 H.264'\n  exit 0\nfi\nexit 1\n";
        Files.write(fake.toPath(), script.getBytes(StandardCharsets.US_ASCII));
        assertTrue(fake.setExecutable(true));
        System.setProperty(ex1.io.VideoWriters.FFMPEG_PROPERTY, fake.getPath());
        ex1.io.VideoWriters.forgetProbe();
        try
        {
            File csv = tmp.newFile("q.csv");
            SweepGenerator.write("tilt", 40, 40, 40, 3, csv);
            File mp4 = new File(tmp.getRoot(), "fallback.mp4");
            Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath()});
            assertTrue(mp4.length() > 0);
            if (toolAvailable("ffprobe"))
            {
                assertEquals("3", ffprobeFrameCount(mp4));
            }
        }
        finally
        {
            System.clearProperty(ex1.io.VideoWriters.FFMPEG_PROPERTY);
            ex1.io.VideoWriters.forgetProbe();
        }
    }

    /**
     * Checks that a doubtful row still gives its frame (2 frames from 2 rows), and that --strict
     * stops with exit code 2 and writes no file at all (TC-32, TC-33).
     * @throws (Exception) When a test file cannot be written or a program cannot be run.
     */
    @Test
    public void invalidPlanesStillGiveAVideoButStrictStops() throws Exception
    {
        File csv       = writeText("bad.csv", "5,0,0, 0,10,0, 10,10,0\n0,0,0, 0,10,0, 30,10,0\n");
        File strictMp4 = new File(tmp.getRoot(), "never.mp4");
        expectExit(2, new String[] {nrrd.getPath(), csv.getPath(), strictMp4.getPath(), "--strict"});
        assertTrue(!strictMp4.exists());

        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File mp4 = new File(tmp.getRoot(), "lenient.mp4");
        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath()});
        assertEquals("2", ffprobeFrameCount(mp4));
    }

    /**
     * Checks --threads: auto gives the same 5 frames; 0 or a word is wrong usage, exit code 1
     * (TC-36).
     * @throws (Exception) When a test file cannot be written or a program cannot be run.
     */
    @Test
    public void threadsOptionGivesSameVideoSize() throws Exception
    {
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File csv = tmp.newFile("p.csv");
        SweepGenerator.write("tilt", 40, 40, 40, 5, csv);
        File mp4 = new File(tmp.getRoot(), "threads.mp4");
        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath(), "--threads", "auto"});
        assertEquals("5", ffprobeFrameCount(mp4));
        expectExit(1, new String[] {"a", "b", "c", "--threads", "0"});
        expectExit(1, new String[] {"a", "b", "c", "--threads", "lots"});
    }

    /**
     * Checks that a CSV file without one usable plane stops the demo with exit code 2 (TC-37).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void csvWithoutUsablePlaneGivesExitCode2() throws IOException
    {
        File csv = writeText("none.csv", "1,2,3\n");
        expectExit(2, new String[] {nrrd.getPath(), csv.getPath(), new File(tmp.getRoot(), "x.mp4").getPath()});
    }

    /**
     * Checks that missing files stop the demo with exit code 2 (TC-35).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void missingFileGivesExitCode2() throws IOException
    {
        expectExit(2, new String[] {"nope.nrrd", "nope.csv", "out.mp4"});
    }

    /**
     * Checks that wrong usage (one file, an unknown sampling mode, a reversed window, an unknown
     * option) stops the demo with exit code 1 (TC-35).
     */
    @Test
    public void wrongArgumentsGiveExitCode1()
    {
        expectExit(1, new String[] {"only-one.nrrd"});
        expectExit(1, new String[] {"a", "b", "c", "--interp", "cubic"});
        expectExit(1, new String[] {"a", "b", "c", "--window", "5", "1"});
        expectExit(1, new String[] {"a", "b", "c", "--bogus"});
    }

    /**
     * Runs the demo and checks that it stops with the expected exit code.
     * @param (int) code Expected exit code.
     * @param (String[]) args Arguments for Ex0.run.
     */
    private static void expectExit(int code, String[] args)
    {
        try
        {
            Ex0.run(args);
            fail("expected exit code " + code);
        }
        catch (Ex0.ExitException e)
        {
            assertEquals(e.getMessage(), code, e.code);
        }
    }

    /**
     * Writes a text file into the test folder.
     * @param (String) name File name.
     * @param (String) text Content.
     * @return (File) The file.
     * @throws (IOException) On I/O errors.
     */
    private File writeText(String name, String text) throws IOException
    {
        File             f   = tmp.newFile(name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.close();
        return f;
    }

    /**
     * Tells whether a command-line tool is installed.
     * @param (String) tool Executable name.
     * @return (boolean) True when "tool -version" runs.
     */
    private static boolean toolAvailable(String tool)
    {
        try
        {
            Process p = new ProcessBuilder(tool, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        }
        catch (IOException e)
        {
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Counts the frames of a video with ffprobe.
     * @param (File) mp4 Video.
     * @return (String) Number of decoded frames as reported by ffprobe.
     * @throws (Exception) On process errors.
     */
    private static String ffprobeFrameCount(File mp4) throws Exception
    {
        Process p = new ProcessBuilder("ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
                "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", mp4.getPath()).start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        String line = r.readLine();
        p.waitFor();
        return line.trim();
    }
}
