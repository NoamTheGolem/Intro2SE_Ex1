package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ex1.core.Volume;
import ex1.io.NrrdWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The course's own edge-case file (data/course/edge_cases.csv) on the die it was written for:
 * "Expected: 16 frames; black: frames 6, 9 to 16; a warning for frames 7 to 16." Checked through
 * the real demo entry point, frame by frame, including what frames 1 to 3 show.
 */
public class EdgeCasesTest
{
    @Rule

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Files of one test, deleted after it. */
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Runs the demo on the lecturer's edge_cases.csv and the die, and checks every frame against
     * his comments: 16 frames, frames 6 and 9-16 black, warnings for exactly rows 7-16, and 1, 6
     * and 4 pips on frames 1, 2 and 3 (TC-38).
     * @throws (IOException) When a test file cannot be written or read.
     */
    @Test
    public void demoGivesTheFramesTheCourseExpects() throws IOException
    {
        File   nrrd = tmp.newFile("dice.nrrd");
        Volume dice = PhantomGenerator.buildDice();
        NrrdWriter.write(dice, nrrd, true, ByteOrder.LITTLE_ENDIAN);
        File csv    = new File("data/course/edge_cases.csv");
        File mp4    = new File(tmp.getRoot(), "edge.mp4");
        File frames = new File(tmp.getRoot(), "frames");

        ByteArrayOutputStream err    = new ByteArrayOutputStream();
        PrintStream           oldErr = System.err;
        System.setErr(new PrintStream(err, true, "UTF-8"));
        try
        {
            // Clean frames: the pips are counted, and the FPS label is light text too.
            Ex0.run(new String[] {nrrd.getPath(), csv.getPath(), mp4.getPath(), "--frames", frames.getPath(), "--no-fps"});
        }
        catch (Ex0.ExitException e)
        {
            throw new AssertionError("demo stopped with exit code " + e.code + ": " + e.getMessage());
        }
        finally
        {
            System.setErr(oldErr);
        }
        String   warnings = new String(err.toByteArray(), StandardCharsets.UTF_8);
        String[] names    = frames.list();
        assertEquals(16, names.length);
        for (int frame = 1; frame <= 16; frame++)
        {
            BufferedImage img         = ImageIO.read(new File(frames, String.format("frame_%04d.png", frame - 1)));
            boolean       black       = isBlack(img);
            boolean       expectBlack = frame == 6 || frame >= 9;
            assertEquals("frame " + frame + " black", expectBlack, black);
            boolean warned = warnings.contains("row " + frame + " (");
            assertEquals("frame " + frame + " warned", frame >= 7, warned);
        }
        // Frame 1: the top face with one pip in the middle; frame 3: the front face, four pips.
        assertEquals(1, pips(ImageIO.read(new File(frames, "frame_0000.png"))));
        assertEquals(6, pips(ImageIO.read(new File(frames, "frame_0001.png"))));
        assertEquals(4, pips(ImageIO.read(new File(frames, "frame_0002.png"))));
        assertTrue(warnings, warnings.contains("row 9 (line 35): the count says 4 points but 3 points are given"));
    }

    /**
     * Tests whether a frame is all black.
     * @param (BufferedImage) img Frame.
     * @return (boolean) True when every pixel is black.
     */
    private static boolean isBlack(BufferedImage img)
    {
        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++)
            {
                if ((img.getRGB(x, y) & 0xFF) > 8)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Counts the white blobs (pips: value 200000 = white, the die's body is mid grey).
     * @param (BufferedImage) img Frame.
     * @return (int) Number of 4-connected white regions of at least 20 pixels.
     */
    private static int pips(BufferedImage img)
    {
        int       w     = img.getWidth();
        int       h     = img.getHeight();
        boolean[] seen  = new boolean[w * h];
        int       count = 0;
        int[]     stack = new int[w * h];
        for (int i = 0; i < w * h; i++)
        {
            if (seen[i] || (img.getRGB(i % w, i / w) & 0xFF) < 240)
            {
                continue;
            }
            int top  = 0;
            int size = 0;
            stack[top++] = i;
            seen[i] = true;
            while (top > 0)
            {
                int p = stack[--top];
                size++;
                int       px   = p % w;
                int       py   = p / w;
                int[]     next = {p - 1, p + 1, p - w, p + w};
                boolean[] ok   = {px > 0, px < w - 1, py > 0, py < h - 1};
                for (int k = 0; k < 4; k++)
                {
                    if (ok[k] && !seen[next[k]] && (img.getRGB(next[k] % w, next[k] / w) & 0xFF) >= 240)
                    {
                        seen[next[k]] = true;
                        stack[top++] = next[k];
                    }
                }
            }
            if (size >= 20)
            {
                count++;
            }
        }
        return count;
    }
}
