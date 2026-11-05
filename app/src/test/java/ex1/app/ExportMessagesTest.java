package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Export video on a real computer: the proposed name never replaces an earlier video, and a
 * failure says in plain words what to do (Windows refuses a folder, a player holds the file).
 */
public class ExportMessagesTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void proposedNameIsNeverTaken() throws IOException
    {
        File dir = tmp.getRoot();
        assertEquals("MRHead_cut.mp4", StudioApp.freeName(dir, "MRHead_cut", ".mp4").getName());
        assertTrue(new File(dir, "MRHead_cut.mp4").createNewFile());
        assertEquals("MRHead_cut_2.mp4", StudioApp.freeName(dir, "MRHead_cut", ".mp4").getName());
        assertTrue(new File(dir, "MRHead_cut_2.mp4").createNewFile());
        assertEquals("MRHead_cut_3.mp4", StudioApp.freeName(dir, "MRHead_cut", ".mp4").getName());
    }

    @Test
    public void failuresAreExplainedWithTheTechnicalDetailBelow()
    {
        File video = new File(tmp.getRoot(), "x.mp4");
        String denied = StudioApp.exportProblem(video,
                new FileNotFoundException(video.getPath() + " (Access is denied)"));
        assertTrue(denied, denied.contains("did not allow the app to write in that folder"));
        assertTrue(denied, denied.contains("Details (FileNotFoundException)"));
        String inUse = StudioApp.exportProblem(video, new IOException("cannot overwrite " + video));
        assertTrue(inUse, inUse.contains("open in another program"));
        String other = StudioApp.exportProblem(video, new IOException("something else"));
        assertTrue(other, other.contains("Try again") && other.contains("something else"));
    }
}
