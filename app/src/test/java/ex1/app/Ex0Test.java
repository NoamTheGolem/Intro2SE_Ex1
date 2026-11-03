package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ex1.core.Volume;
import ex1.core.VoxelType;
import ex1.io.NrrdWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

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
    public TemporaryFolder tmp = new TemporaryFolder();

    private File nrrd;

    @Before
    public void makePhantom() throws IOException
    {
        Volume v = PhantomGenerator.build("spheres", VoxelType.UINT16, 40);
        nrrd = tmp.newFile("phantom.nrrd");
        NrrdWriter.write(v, nrrd, true, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void demoWritesOneFramePerCsvRow() throws Exception
    {
        Assume.assumeTrue("ffmpeg not installed", toolAvailable("ffmpeg"));
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File csv = tmp.newFile("planes.csv");
        SweepGenerator.write("rotate", 40, 40, 40, 7, csv);
        File mp4 = new File(tmp.getRoot(), "out.mp4");
        File frames = new File(tmp.getRoot(), "frames");

        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath(), "--frames", frames.getPath(),
                "--interp", "trilinear"});

        assertTrue(mp4.length() > 0);
        assertEquals("7", ffprobeFrameCount(mp4));
        assertEquals(7, frames.list().length);
    }

    @Test
    public void courseExampleCsvGivesOneFramePerRow() throws Exception
    {
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File csv = writeText("Ex0.csv", ex1.io.PlaneCsvReaderTestAccess.courseCsv());
        File mp4 = new File(tmp.getRoot(), "course.mp4");
        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath()});
        assertEquals("4", ffprobeFrameCount(mp4));
    }

    @Test
    public void builtInMp4WriterWorksWithoutFfmpeg() throws Exception
    {
        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File mp4 = new File(tmp.getRoot(), "builtin.mp4");
        ex1.io.VideoWriter w = new ex1.io.Mp4Writer(mp4, 40, 40, 10);
        for (int i = 0; i < 5; i++)
        {
            w.writeFrame(new byte[40 * 40]);
        }
        w.close();
        assertEquals("5", ffprobeFrameCount(mp4));
    }

    @Test
    public void invalidPlanesStillGiveAVideoButStrictStops() throws Exception
    {
        File csv = writeText("bad.csv", "5,0,0, 0,10,0, 10,10,0\n0,0,0, 0,10,0, 30,10,0\n");
        File strictMp4 = new File(tmp.getRoot(), "never.mp4");
        expectExit(2, new String[] {nrrd.getPath(), csv.getPath(), strictMp4.getPath(), "--strict"});
        assertTrue(!strictMp4.exists());

        Assume.assumeTrue("ffprobe not installed", toolAvailable("ffprobe"));
        File mp4 = new File(tmp.getRoot(), "lenient.mp4");
        Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath()});
        assertEquals("2", ffprobeFrameCount(mp4));
    }

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

    @Test
    public void csvWithoutUsablePlaneGivesExitCode2() throws IOException
    {
        File csv = writeText("none.csv", "1,2,3\n");
        expectExit(2, new String[] {nrrd.getPath(), csv.getPath(), new File(tmp.getRoot(), "x.mp4").getPath()});
    }

    @Test
    public void missingFileGivesExitCode2() throws IOException
    {
        expectExit(2, new String[] {"nope.nrrd", "nope.csv", "out.mp4"});
    }

    @Test
    public void wrongArgumentsGiveExitCode1()
    {
        expectExit(1, new String[] {"only-one.nrrd"});
        expectExit(1, new String[] {"a", "b", "c", "--interp", "cubic"});
        expectExit(1, new String[] {"a", "b", "c", "--window", "5", "1"});
        expectExit(1, new String[] {"a", "b", "c", "--bogus"});
    }

    /**
     * @param (int) code expected exit code
     * @param (String[]) args arguments for Ex0.run
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
     * @param (String) name file name
     * @param (String) text content
     * @return the file
     * @throws (IOException) on I/O errors
     */
    private File writeText(String name, String text) throws IOException
    {
        File f = tmp.newFile(name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.close();
        return f;
    }

    /**
     * @param (String) tool executable name
     * @return true when "tool -version" runs
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
     * @param (File) mp4 video
     * @return number of decoded frames as reported by ffprobe
     * @throws (Exception) on process errors
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
