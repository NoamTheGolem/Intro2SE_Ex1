package ex1.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ex1.core.Volume;
import ex1.core.VoxelType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * NRRD round trips for every type/encoding/endian combination, plus error cases (SRS FR-2, FR-3).
 */
public class NrrdReaderTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * @param (VoxelType) type storage type
     * @return small volume with distinct values, including large/negative ones
     */
    private static Volume sample(VoxelType type)
    {
        Volume v = Volume.create(type, 5, 4, 3);
        for (int i = 0; i < v.voxelCount(); i++)
        {
            double value = i * 3;
            if (type == VoxelType.INT8 || type == VoxelType.INT16 || type == VoxelType.INT32)
            {
                value = i * 3 - 50;
            }
            v.setValue(i, value);
        }
        return v;
    }

    @Test
    public void roundTripAllTypesEncodingsAndEndians() throws IOException
    {
        VoxelType[] types = VoxelType.values();
        boolean[] gzips = {false, true};
        ByteOrder[] orders = {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN};
        for (int t = 0; t < types.length; t++)
        {
            for (int g = 0; g < gzips.length; g++)
            {
                for (int o = 0; o < orders.length; o++)
                {
                    Volume src = sample(types[t]);
                    File f = tmp.newFile();
                    NrrdWriter.write(src, f, gzips[g], orders[o]);
                    Volume back = NrrdReader.read(f);
                    String label = types[t] + " gzip=" + gzips[g] + " " + orders[o];
                    assertEquals(label, types[t], back.type());
                    assertEquals(label, 5, back.sizeX());
                    assertEquals(label, 4, back.sizeY());
                    assertEquals(label, 3, back.sizeZ());
                    for (int i = 0; i < src.voxelCount(); i++)
                    {
                        assertEquals(label + " voxel " + i, src.valueAt(i), back.valueAt(i), 0f);
                    }
                }
            }
        }
    }

    @Test
    public void readsDetachedHeader() throws IOException
    {
        File raw = tmp.newFile("data.raw");
        FileOutputStream out = new FileOutputStream(raw);
        out.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        out.close();
        File nhdr = writeText("vol.nhdr", "NRRD0004\ntype: uchar\ndimension: 3\nsizes: 2 2 2\n"
                + "encoding: raw\ndata file: data.raw\n");
        Volume v = NrrdReader.read(nhdr);
        assertEquals(8f, v.get(1, 1, 1), 0f);
    }

    @Test
    public void readsRealMRHeadIfPresent() throws IOException
    {
        File f = new File("../data/MRHead.nrrd");
        if (!f.isFile())
        {
            f = new File("data/MRHead.nrrd");
        }
        if (!f.isFile())
        {
            return;
        }
        Volume v = NrrdReader.read(f);
        assertEquals(256, v.sizeX());
        assertEquals(256, v.sizeY());
        assertEquals(130, v.sizeZ());
        assertEquals(VoxelType.INT16, v.type());
    }

    @Test
    public void rejectsNonNrrd() throws IOException
    {
        expectError(writeText("x.nrrd", "hello\n"), "not a NRRD");
    }

    @Test
    public void rejectsUnsupportedDimension() throws IOException
    {
        expectError(writeText("x.nrrd", "NRRD0004\ntype: uchar\ndimension: 2\nsizes: 2 2\nencoding: raw\n\n"),
                "dimension");
    }

    @Test
    public void rejectsUnsupportedEncoding() throws IOException
    {
        expectError(writeText("x.nrrd", "NRRD0004\ntype: uchar\ndimension: 3\nsizes: 2 2 2\nencoding: ascii\n\n"),
                "encoding");
    }

    @Test
    public void rejectsDoubleType() throws IOException
    {
        expectError(writeText("x.nrrd", "NRRD0004\ntype: double\ndimension: 3\nsizes: 2 2 2\nendian: little\n"
                + "encoding: raw\n\n"), "type");
    }

    @Test
    public void rejectsTruncatedData() throws IOException
    {
        expectError(writeText("x.nrrd", "NRRD0004\ntype: uchar\ndimension: 3\nsizes: 2 2 2\nencoding: raw\n\nabc"),
                "ended early");
    }

    @Test
    public void rejectsMissingField() throws IOException
    {
        expectError(writeText("x.nrrd", "NRRD0004\ndimension: 3\nsizes: 2 2 2\nencoding: raw\n\n"), "'type'");
    }

    /**
     * @param (String) name file name
     * @param (String) text content
     * @return the written file
     * @throws (IOException) on I/O errors
     */
    private File writeText(String name, String text) throws IOException
    {
        File f = tmp.newFile(name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        out.close();
        return f;
    }

    /**
     * @param (File) f file expected to fail
     * @param (String) fragment text expected in the error message
     */
    private static void expectError(File f, String fragment)
    {
        try
        {
            NrrdReader.read(f);
            fail("expected an error containing '" + fragment + "'");
        }
        catch (IOException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    @Test
    public void readsSpaceDirectionsForTheViewer()
    {
        java.util.Map<String, String> fields = new java.util.HashMap<String, String>();
        fields.put("space directions", "(0,1,0) (0,0,-1) (-1.2999954223632812,0,0)");
        double[][] d = NrrdReader.parseDirections(fields);
        assertEquals(-1.2999954223632812, d[2][0], 0.0);
        assertEquals(-1.0, d[1][2], 0.0);
        fields.clear();
        fields.put("spacings", "1 1 1.3");
        assertEquals(1.3, NrrdReader.parseDirections(fields)[2][2], 0.0);
        fields.clear();
        fields.put("space directions", "none (0,1,0) (0,0,1)");
        assertEquals(null, NrrdReader.parseDirections(fields));
    }
}
