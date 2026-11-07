package ex1.io;

import ex1.core.Volume;
import ex1.core.VoxelType;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a volume as an attached NRRD file. Used to build the synthetic test dataset
 * (phantoms) and by the reader's round-trip tests.
 */
public final class NrrdWriter
{
    /** Static methods only; never instantiated. */
    private NrrdWriter()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Writes a volume as an attached NRRD file: the text header, then the samples, raw or gzip.
     * @param (Volume) volume Volume to write.
     * @param (File) file Destination .nrrd.
     * @param (boolean) gzip True for gzip encoding, false for raw.
     * @param (ByteOrder) order Sample byte order.
     * @throws (IOException) On I/O errors.
     */
    public static void write(Volume volume, File file, boolean gzip, ByteOrder order) throws IOException
    {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try
        {
            String header = "NRRD0004\n"
                    + "# written by Ex1 NrrdWriter\n"
                    + "type: " + nrrdType(volume.type()) + "\n"
                    + "dimension: 3\n"
                    + "sizes: " + volume.sizeX() + " " + volume.sizeY() + " " + volume.sizeZ() + "\n"
                    + "endian: " + endianName(order) + "\n"
                    + "encoding: " + encodingName(gzip) + "\n"
                    + "\n";
            out.write(header.getBytes(StandardCharsets.ISO_8859_1));
            OutputStream data = out;
            if (gzip)
            {
                data = new GZIPOutputStream(out);
            }
            int        bytes = volume.type().bytes();
            ByteBuffer buf   = ByteBuffer.allocate(bytes).order(order);
            int        n     = volume.voxelCount();
            for (int i = 0; i < n; i++)
            {
                buf.clear();
                putValue(buf, volume.type(), volume.valueAt(i));
                data.write(buf.array(), 0, bytes);
            }
            if (gzip)
            {
                ((GZIPOutputStream) data).finish();
            }
        }
        finally
        {
            out.close();
        }
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Writes one sample in the volume's storage type.
     * @param (ByteBuffer) buf Destination.
     * @param (VoxelType) type Storage type.
     * @param (double) v Value.
     */
    private static void putValue(ByteBuffer buf, VoxelType type, double v)
    {
        switch (type)
        {
            case INT8:
            case UINT8:
                buf.put((byte) (long) v);
                break;
            case INT16:
            case UINT16:
                buf.putShort((short) (long) v);
                break;
            case INT32:
            case UINT32:
                buf.putInt((int) (long) v);
                break;
            default:
                buf.putFloat((float) v);
                break;
        }
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Gives the NRRD name of a storage type.
     * @param (VoxelType) type Storage type.
     * @return (String) NRRD type name.
     */
    private static String nrrdType(VoxelType type)
    {
        switch (type)
        {
            case INT8:
                return "int8";
            case UINT8:
                return "uint8";
            case INT16:
                return "int16";
            case UINT16:
                return "uint16";
            case INT32:
                return "int32";
            case UINT32:
                return "uint32";
            default:
                return "float";
        }
    }

    /**
     * Gives the NRRD name of a byte order.
     * @param (ByteOrder) order Byte order.
     * @return (String) "little" or "big".
     */
    private static String endianName(ByteOrder order)
    {
        if (order == ByteOrder.BIG_ENDIAN)
        {
            return "big";
        }
        return "little";
    }

    /**
     * Gives the NRRD name of an encoding.
     * @param (boolean) gzip Encoding flag.
     * @return (String) "gzip" or "raw".
     */
    private static String encodingName(boolean gzip)
    {
        if (gzip)
        {
            return "gzip";
        }
        return "raw";
    }
}
