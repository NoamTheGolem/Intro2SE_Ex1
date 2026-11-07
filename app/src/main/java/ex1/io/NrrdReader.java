package ex1.io;

import ex1.core.ByteVolume;
import ex1.core.FloatVolume;
import ex1.core.IntVolume;
import ex1.core.ShortVolume;
import ex1.core.Volume;
import ex1.core.VoxelType;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Reads 3D scalar NRRD volumes (format spec: http://teem.sourceforge.net/nrrd/format.html).
 *
 * Supported: dimension 3; types int8/uint8/int16/uint16/int32/uint32/float; encodings raw and
 * gzip; little/big endian; attached data or one detached data file; "byte skip" >= 0.
 * Anything else is rejected with a message naming the field (SRS FR-2, FR-3).
 */
public final class NrrdReader
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Start of every NRRD file (then a version digit). */
    private static final String MAGIC = "NRRD000";

    /** Bytes read at a time, and the longest header before the file is called broken. */
    private static final int CHUNK            = 1 << 20;
    private static final int MAX_HEADER_BYTES = 1 << 16;

    /** Static methods only; never instantiated. */
    private NrrdReader()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Reads a volume from a .nrrd (attached) or .nhdr (detached) file.
     * @param (File) file The NRRD file.
     * @return (Volume) The volume.
     * @throws (IOException) On I/O errors or unsupported / malformed files.
     */
    public static Volume read(File file) throws IOException
    {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try
        {
            Map<String, String> fields   = readHeader(in, file.getName());
            VoxelType           type     = parseType(require(fields, "type"));
            int[]               sizes    = parseSizes(fields);
            ByteOrder           order    = parseEndian(fields, type);
            boolean             gzip     = parseEncoding(fields);
            long                byteSkip = parseByteSkip(fields);

            InputStream data     = in;
            String      dataFile = fields.get("data file");
            if (dataFile == null)
            {
                dataFile = fields.get("datafile");
            }
            if (dataFile != null)
            {
                in.close();
                File detached = resolveDataFile(file, dataFile);
                in = new BufferedInputStream(new FileInputStream(detached));
                data = in;
            }
            if (gzip)
            {
                data = new GZIPInputStream(data, CHUNK);
            }
            skipFully(data, byteSkip);

            Volume volume = Volume.create(type, sizes[0], sizes[1], sizes[2]);
            readSamples(data, volume, order);
            volume.setSpace(fields.get("space"), parseDirections(fields));
            return volume;
        }
        finally
        {
            in.close();
        }
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Reads header lines up to the empty line that separates header and data.
     * @param (InputStream) in Stream positioned at the file start.
     * @param (String) name File name for messages.
     * @return (Map<String, String>) Field name (lower case) to value.
     * @throws (IOException) If the file is not NRRD.
     */
    private static Map<String, String> readHeader(InputStream in, String name) throws IOException
    {
        String magic = readLine(in);
        if (magic == null || !magic.startsWith(MAGIC))
        {
            throw new IOException(name + ": not a NRRD file (missing NRRD000x magic)");
        }
        Map<String, String> fields      = new HashMap<String, String>();
        int                 headerBytes = magic.length();
        while (true)
        {
            String line = readLine(in);
            if (line == null || line.isEmpty())
            {
                // Empty line (or EOF for a detached header) ends the header.
                break;
            }
            headerBytes += line.length();
            if (headerBytes > MAX_HEADER_BYTES)
            {
                throw new IOException(name + ": header too long, file is probably corrupt");
            }
            if (line.startsWith("#"))
            {
                continue;
            }
            int sep = line.indexOf(": ");
            if (sep < 0)
            {
                // "key:=value" lines are key/value comments, not fields.
                continue;
            }
            String key = line.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            fields.put(key, line.substring(sep + 2).trim());
        }
        return fields;
    }

    /**
     * Reads the "type" field.
     * @param (String) t NRRD type string.
     * @return (VoxelType) Matching voxel type.
     * @throws (IOException) For unsupported types (e.g. double, 64-bit).
     */
    private static VoxelType parseType(String t) throws IOException
    {
        String s = t.toLowerCase(Locale.ROOT).trim();
        if (s.equals("signed char") || s.equals("int8") || s.equals("int8_t"))
        {
            return VoxelType.INT8;
        }
        if (s.equals("uchar") || s.equals("unsigned char") || s.equals("uint8") || s.equals("uint8_t"))
        {
            return VoxelType.UINT8;
        }
        if (s.equals("short") || s.equals("short int") || s.equals("signed short")
                || s.equals("signed short int") || s.equals("int16") || s.equals("int16_t"))
        {
            return VoxelType.INT16;
        }
        if (s.equals("ushort") || s.equals("unsigned short") || s.equals("unsigned short int")
                || s.equals("uint16") || s.equals("uint16_t"))
        {
            return VoxelType.UINT16;
        }
        if (s.equals("int") || s.equals("signed int") || s.equals("int32") || s.equals("int32_t"))
        {
            return VoxelType.INT32;
        }
        if (s.equals("uint") || s.equals("unsigned int") || s.equals("uint32") || s.equals("uint32_t"))
        {
            return VoxelType.UINT32;
        }
        if (s.equals("float"))
        {
            return VoxelType.FLOAT32;
        }
        throw new IOException("unsupported NRRD type: " + t);
    }

    /**
     * Reads the "dimension" and "sizes" fields.
     * @param (Map<String, String>) fields Header fields.
     * @return (int[]) Sizes {sx, sy, sz}.
     * @throws (IOException) Unless dimension is 3 with 3 positive sizes.
     */
    private static int[] parseSizes(Map<String, String> fields) throws IOException
    {
        String dim = require(fields, "dimension").trim();
        if (!dim.equals("3"))
        {
            throw new IOException("unsupported NRRD dimension: " + dim + " (only 3D scalar volumes)");
        }
        String[] parts = require(fields, "sizes").trim().split("\\s+");
        if (parts.length != 3)
        {
            throw new IOException("NRRD sizes must have 3 values, got: " + fields.get("sizes"));
        }
        int[] sizes = new int[3];
        for (int i = 0; i < 3; i++)
        {
            try
            {
                sizes[i] = Integer.parseInt(parts[i]);
            }
            catch (NumberFormatException e)
            {
                throw new IOException("bad NRRD size: " + parts[i]);
            }
            if (sizes[i] < 1 || sizes[i] >= Volume.MAX_SIZE)
            {
                throw new IOException("NRRD size " + sizes[i] + " outside supported range [1, "
                        + Volume.MAX_SIZE + ")");
            }
        }
        return sizes;
    }

    /**
     * Reads the "endian" field (not needed for one-byte samples).
     * @param (Map<String, String>) fields Header fields.
     * @param (VoxelType) type Voxel type (1-byte types need no endian field).
     * @return (ByteOrder) Byte order of the samples.
     * @throws (IOException) On an unknown or missing endian field.
     */
    private static ByteOrder parseEndian(Map<String, String> fields, VoxelType type) throws IOException
    {
        String e = fields.get("endian");
        if (e == null)
        {
            if (type.bytes() == 1)
            {
                return ByteOrder.LITTLE_ENDIAN;
            }
            throw new IOException("NRRD field 'endian' is required for multi-byte types");
        }
        e = e.trim().toLowerCase(Locale.ROOT);
        if (e.equals("little"))
        {
            return ByteOrder.LITTLE_ENDIAN;
        }
        if (e.equals("big"))
        {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new IOException("unsupported NRRD endian: " + e);
    }

    /**
     * Reads the "encoding" field.
     * @param (Map<String, String>) fields Header fields.
     * @return (boolean) True for gzip, false for raw.
     * @throws (IOException) For other encodings (ascii, hex, bzip2).
     */
    private static boolean parseEncoding(Map<String, String> fields) throws IOException
    {
        String enc = require(fields, "encoding").trim().toLowerCase(Locale.ROOT);
        if (enc.equals("raw"))
        {
            return false;
        }
        if (enc.equals("gzip") || enc.equals("gz"))
        {
            return true;
        }
        throw new IOException("unsupported NRRD encoding: " + enc + " (raw and gzip only)");
    }

    /**
     * Reads the "byte skip" field (0 when missing).
     * @param (Map<String, String>) fields Header fields.
     * @return (long) Bytes to skip before the samples.
     * @throws (IOException) For "byte skip: -1" or bad numbers.
     */
    private static long parseByteSkip(Map<String, String> fields) throws IOException
    {
        String s = fields.get("byte skip");
        if (s == null)
        {
            s = fields.get("byteskip");
        }
        if (s == null)
        {
            return 0;
        }
        long skip;
        try
        {
            skip = Long.parseLong(s.trim());
        }
        catch (NumberFormatException e)
        {
            throw new IOException("bad NRRD byte skip: " + s);
        }
        if (skip < 0)
        {
            throw new IOException("unsupported NRRD byte skip: " + s);
        }
        return skip;
    }

    /**
     * Finds the data file of a detached header.
     * @param (File) header The header file.
     * @param (String) dataFile Value of the "data file" field.
     * @return (File) The data file, resolved relative to the header's folder.
     * @throws (IOException) For multi-file data (LIST or format patterns).
     */
    private static File resolveDataFile(File header, String dataFile) throws IOException
    {
        if (dataFile.startsWith("LIST") || dataFile.contains("%"))
        {
            throw new IOException("unsupported NRRD data file (multi-file): " + dataFile);
        }
        File f = new File(dataFile);
        if (!f.isAbsolute())
        {
            f = new File(header.getAbsoluteFile().getParentFile(), dataFile);
        }
        return f;
    }

    /**
     * Fills the volume's typed array chunk by chunk, so peak memory is the volume plus 1 MB
     * instead of twice the volume.
     * @param (InputStream) in Sample bytes.
     * @param (Volume) volume Destination.
     * @param (ByteOrder) order Sample byte order.
     * @throws (IOException) If the data ends early.
     */
    private static void readSamples(InputStream in, Volume volume, ByteOrder order) throws IOException
    {
        int        bytesPerVoxel  = volume.type().bytes();
        int        total          = volume.voxelCount();
        int        voxelsPerChunk = CHUNK / bytesPerVoxel;
        byte[]     chunk          = new byte[voxelsPerChunk * bytesPerVoxel];
        ByteBuffer buffer         = ByteBuffer.wrap(chunk).order(order);

        int done = 0;
        while (done < total)
        {
            int n = Math.min(voxelsPerChunk, total - done);
            readFully(in, chunk, n * bytesPerVoxel);
            buffer.clear();
            copyChunk(buffer, volume, done, n);
            done += n;
        }
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Copies one chunk of samples into the volume's typed array.
     * @param (ByteBuffer) buffer Chunk bytes in the file's byte order.
     * @param (Volume) volume Destination.
     * @param (int) start First voxel index to fill.
     * @param (int) n Number of voxels.
     */
    private static void copyChunk(ByteBuffer buffer, Volume volume, int start, int n)
    {
        if (volume instanceof ByteVolume)
        {
            buffer.get(((ByteVolume) volume).data(), start, n);
        }
        else if (volume instanceof ShortVolume)
        {
            buffer.asShortBuffer().get(((ShortVolume) volume).data(), start, n);
        }
        else if (volume instanceof IntVolume)
        {
            buffer.asIntBuffer().get(((IntVolume) volume).data(), start, n);
        }
        else
        {
            buffer.asFloatBuffer().get(((FloatVolume) volume).data(), start, n);
        }
    }

    /**
     * Reads a field that must be there.
     * @param (Map<String, String>) fields Header fields.
     * @param (String) key Required field name.
     * @return (String) Its value.
     * @throws (IOException) If missing.
     */
    private static String require(Map<String, String> fields, String key) throws IOException
    {
        String v = fields.get(key);
        if (v == null)
        {
            throw new IOException("NRRD header is missing required field '" + key + "'");
        }
        return v;
    }

    /**
     * Reads "space directions: (0,1,0) (0,0,-1) (-1.3,0,0)" or the older "spacings: 1 1 1.3".
     * Geometry is optional (only the viewer uses it), so anything unreadable gives null instead
     * of an error.
     * @param (Map<String, String>) fields Header fields.
     * @return (double[][]) One physical step vector per index axis, or null.
     */
    static double[][] parseDirections(Map<String, String> fields)
    {
        String dirs = fields.get("space directions");
        try
        {
            if (dirs != null)
            {
                Matcher    m   = Pattern.compile("\\(([^)]*)\\)").matcher(dirs);
                double[][] out = new double[3][];
                int        n   = 0;
                while (m.find() && n < 3)
                {
                    String[] parts = m.group(1).split(",");
                    if (parts.length != 3)
                    {
                        return null;
                    }
                    out[n] = new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                            Double.parseDouble(parts[2].trim())};
                    n++;
                }
                if (n != 3)
                {
                    return null;
                }
                return out;
            }
            String spacings = fields.get("spacings");
            if (spacings != null)
            {
                String[] parts = spacings.trim().split("\\s+");
                if (parts.length != 3)
                {
                    return null;
                }
                double[][] out = new double[3][3];
                for (int i = 0; i < 3; i++)
                {
                    double s = Double.parseDouble(parts[i]);
                    if (!(s > 0) || Double.isInfinite(s))
                    {
                        return null;
                    }
                    out[i][i] = s;
                }
                return out;
            }
        }
        catch (NumberFormatException e)
        {
            return null;
        }
        return null;
    }

    /**
     * Reads one header line byte by byte (a Reader would buffer past the header into the data).
     * @param (InputStream) in Stream.
     * @return (String) Line without the line terminator, or null at end of stream.
     * @throws (IOException) On I/O errors.
     */
    private static String readLine(InputStream in) throws IOException
    {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int                   b    = in.read();
        if (b < 0)
        {
            return null;
        }
        while (b >= 0 && b != '\n')
        {
            if (b != '\r')
            {
                line.write(b);
            }
            b = in.read();
        }
        return line.toString("ISO-8859-1");
    }

    /**
     * Reads exactly len bytes (a gzip stream may give fewer at a time).
     * @param (InputStream) in Stream.
     * @param (byte[]) buf Destination.
     * @param (int) len Bytes to read.
     * @throws (IOException) If the stream ends first.
     */
    private static void readFully(InputStream in, byte[] buf, int len) throws IOException
    {
        int off = 0;
        while (off < len)
        {
            int r = in.read(buf, off, len - off);
            if (r < 0)
            {
                throw new EOFException("NRRD data ended early (file truncated or sizes wrong)");
            }
            off += r;
        }
    }

    /**
     * Skips exactly n bytes.
     * @param (InputStream) in Stream.
     * @param (long) n Bytes to skip.
     * @throws (IOException) If the stream ends first.
     */
    private static void skipFully(InputStream in, long n) throws IOException
    {
        long left = n;
        while (left > 0)
        {
            long s = in.skip(left);
            if (s <= 0)
            {
                if (in.read() < 0)
                {
                    throw new EOFException("NRRD byte skip goes past end of data");
                }
                s = 1;
            }
            left -= s;
        }
    }
}
