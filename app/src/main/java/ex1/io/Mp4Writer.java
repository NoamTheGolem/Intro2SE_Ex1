package ex1.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Pure-Java MP4 writer: no ffmpeg, no libraries.
 *
 * Video is H.264 Baseline where every frame is an IDR picture made of I_PCM macroblocks
 * (raw samples, no prediction or transform). That is the simplest stream the standard allows,
 * and every player decodes it (Windows Media Player, browsers, phones). The price is size:
 * about 1.5 bytes per pixel per frame (a 256x256 frame is ~100 KB). When ffmpeg is installed,
 * {@link FfmpegVideoWriter} gives much smaller files.
 *
 * Layout: ftyp | mdat (frames, 4-byte length-prefixed NAL units) | moov (index).
 */
public final class Mp4Writer implements VideoWriter
{
    private static final int MB = 16;
    private static final int MB_CHROMA = 8;
    private static final int I_PCM = 25;
    private static final int NAL_SPS = 7;
    private static final int NAL_PPS = 8;
    private static final int NAL_IDR = 5;
    private static final int PROFILE_BASELINE = 66;
    private static final int CONSTRAINT_FLAGS = 0xC0;
    private static final int LEVEL = 51;
    private static final int GREY_CHROMA = 128;
    private static final int LUMA_BLACK = 16;
    private static final int LUMA_RANGE = 219;
    private static final int MAX_IDR_ID = 65535;
    private static final int MOVIE_TIMESCALE = 1000;
    private static final long MAX_32 = 0xFFFFFFFFL;

    private final RandomAccessFile out;
    private final int width;
    private final int height;
    private final int fps;
    private final int mbCols;
    private final int mbRows;
    private final byte[] sps;
    private final byte[] pps;
    private final IntList sampleSizes = new IntList();
    private final long mdatStart;
    private final byte[] lumaMap = new byte[256];

    /**
     * Opens the file and writes the header.
     *
     * @param (File) file destination .mp4
     * @param (int) width frame width (even)
     * @param (int) height frame height (even)
     * @param (int) fps frames per second
     * @throws (IOException) if the file cannot be written
     */
    public Mp4Writer(File file, int width, int height, int fps) throws IOException
    {
        if (width % 2 != 0 || height % 2 != 0)
        {
            throw new IllegalArgumentException("frame size must be even, got " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.mbCols = (width + MB - 1) / MB;
        this.mbRows = (height + MB - 1) / MB;
        this.sps = buildSps();
        this.pps = buildPps();
        // Players treat H.264 as "TV range": 16 = black, 235 = white.
        for (int g = 0; g < 256; g++)
        {
            lumaMap[g] = (byte) (LUMA_BLACK + (g * LUMA_RANGE + 127) / 255);
        }

        if (file.exists() && !file.delete())
        {
            throw new IOException("cannot overwrite " + file);
        }
        this.out = new RandomAccessFile(file, "rw");
        writeFtyp();
        this.mdatStart = out.getFilePointer();
        out.writeInt(0);
        out.write(fourCc("mdat"));
    }

    // ===================================================================== ENTRY

    @Override
    public void writeFrame(byte[] grey) throws IOException
    {
        if (grey.length != width * height)
        {
            throw new IllegalArgumentException("frame has " + grey.length + " bytes, expected " + width * height);
        }
        byte[] nal = escape(buildSlice(grey, sampleSizes.size() % (MAX_IDR_ID + 1)));
        out.writeInt(nal.length);
        out.write(nal);
        sampleSizes.add(nal.length + 4);
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            long mdatEnd = out.getFilePointer();
            long mdatSize = mdatEnd - mdatStart;
            if (mdatSize > MAX_32)
            {
                throw new IOException("video larger than 4 GB; use fewer or smaller frames");
            }
            out.seek(mdatStart);
            out.writeInt((int) mdatSize);
            out.seek(mdatEnd);
            out.write(buildMoov(mdatStart + 8));
        }
        finally
        {
            out.close();
        }
    }

    // ============================================================ BUILD PIPELINE

    /**
     * @return SPS NAL unit (with header, escaped)
     */
    private byte[] buildSps()
    {
        BitWriter b = new BitWriter();
        b.bits(0x60 | NAL_SPS, 8);
        b.bits(PROFILE_BASELINE, 8);
        b.bits(CONSTRAINT_FLAGS, 8);
        b.bits(LEVEL, 8);
        b.ue(0);                  // seq_parameter_set_id
        b.ue(0);                  // log2_max_frame_num_minus4
        b.ue(2);                  // pic_order_cnt_type 2: output order = decode order
        b.ue(1);                  // max_num_ref_frames
        b.bit(0);                 // gaps_in_frame_num_value_allowed_flag
        b.ue(mbCols - 1);
        b.ue(mbRows - 1);
        b.bit(1);                 // frame_mbs_only_flag
        b.bit(1);                 // direct_8x8_inference_flag
        int cropRight = (mbCols * MB - width) / 2;
        int cropBottom = (mbRows * MB - height) / 2;
        if (cropRight > 0 || cropBottom > 0)
        {
            b.bit(1);
            b.ue(0);
            b.ue(cropRight);
            b.ue(0);
            b.ue(cropBottom);
        }
        else
        {
            b.bit(0);
        }
        b.bit(0);                 // vui_parameters_present_flag
        b.trailingBits();
        return escape(b.toBytes());
    }

    /**
     * @return PPS NAL unit (with header, escaped)
     */
    private static byte[] buildPps()
    {
        BitWriter b = new BitWriter();
        b.bits(0x60 | NAL_PPS, 8);
        b.ue(0);                  // pic_parameter_set_id
        b.ue(0);                  // seq_parameter_set_id
        b.bit(0);                 // entropy_coding_mode_flag: CAVLC
        b.bit(0);                 // bottom_field_pic_order_in_frame_present_flag
        b.ue(0);                  // num_slice_groups_minus1
        b.ue(0);                  // num_ref_idx_l0_default_active_minus1
        b.ue(0);                  // num_ref_idx_l1_default_active_minus1
        b.bit(0);                 // weighted_pred_flag
        b.bits(0, 2);             // weighted_bipred_idc
        b.se(0);                  // pic_init_qp_minus26
        b.se(0);                  // pic_init_qs_minus26
        b.se(0);                  // chroma_qp_index_offset
        b.bit(1);                 // deblocking_filter_control_present_flag
        b.bit(0);                 // constrained_intra_pred_flag
        b.bit(0);                 // redundant_pic_cnt_present_flag
        b.trailingBits();
        return escape(b.toBytes());
    }

    /**
     * One frame = one IDR slice of I_PCM macroblocks. Pixels beyond the frame edge (padding
     * up to a multiple of 16) repeat the last row/column; the SPS crops them away.
     *
     * @param (byte[]) grey frame pixels
     * @param (int) idrId idr_pic_id (consecutive IDRs must differ)
     * @return raw NAL unit, not yet escaped
     */
    private byte[] buildSlice(byte[] grey, int idrId)
    {
        BitWriter b = new BitWriter();
        b.bits(0x60 | NAL_IDR, 8);
        b.ue(0);                  // first_mb_in_slice
        b.ue(7);                  // slice_type: I (all slices)
        b.ue(0);                  // pic_parameter_set_id
        b.bits(0, 4);             // frame_num (log2_max_frame_num = 4)
        b.ue(idrId);
        b.bit(0);                 // no_output_of_prior_pics_flag
        b.bit(0);                 // long_term_reference_flag
        b.se(0);                  // slice_qp_delta
        b.ue(1);                  // disable_deblocking_filter_idc: off (PCM needs none)

        for (int my = 0; my < mbRows; my++)
        {
            for (int mx = 0; mx < mbCols; mx++)
            {
                b.ue(I_PCM);
                b.alignZero();
                for (int y = 0; y < MB; y++)
                {
                    int py = Math.min(my * MB + y, height - 1);
                    int row = py * width;
                    for (int x = 0; x < MB; x++)
                    {
                        int px = Math.min(mx * MB + x, width - 1);
                        b.byteAligned(lumaMap[grey[row + px] & 0xFF]);
                    }
                }
                for (int i = 0; i < 2 * MB_CHROMA * MB_CHROMA; i++)
                {
                    b.byteAligned((byte) GREY_CHROMA);
                }
            }
        }
        b.trailingBits();
        return b.toBytes();
    }

    /**
     * @param (long) firstSampleOffset file offset of the first frame
     * @return the moov box
     * @throws (IOException) never (in-memory)
     */
    private byte[] buildMoov(long firstSampleOffset) throws IOException
    {
        int n = sampleSizes.size();
        long movieDuration = (long) n * MOVIE_TIMESCALE / fps;

        Box mvhd = new Box("mvhd");
        mvhd.u32(0).u32(0).u32(0).u32(MOVIE_TIMESCALE).u32(movieDuration);
        mvhd.u32(0x00010000).u16(0x0100).zeros(10).matrix().zeros(24).u32(2);

        Box tkhd = new Box("tkhd");
        tkhd.u32(3).u32(0).u32(0).u32(1).u32(0).u32(movieDuration).zeros(8);
        tkhd.u16(0).u16(0).u16(0).u16(0).matrix().u32((long) width << 16).u32((long) height << 16);

        Box mdhd = new Box("mdhd");
        mdhd.u32(0).u32(0).u32(0).u32(fps).u32(n).u16(0x55C4).u16(0);

        Box hdlr = new Box("hdlr");
        hdlr.u32(0).u32(0).raw(fourCc("vide")).zeros(12).raw("VideoHandler".getBytes(StandardCharsets.US_ASCII)).u8(0);

        Box vmhd = new Box("vmhd");
        vmhd.u32(1).zeros(8);

        Box url = new Box("url ");
        url.u32(1);
        Box dref = new Box("dref");
        dref.u32(0).u32(1).child(url);
        Box dinf = new Box("dinf");
        dinf.child(dref);

        Box avcC = new Box("avcC");
        avcC.u8(1).u8(PROFILE_BASELINE).u8(CONSTRAINT_FLAGS).u8(LEVEL).u8(0xFF).u8(0xE1);
        avcC.u16(sps.length).raw(sps).u8(1).u16(pps.length).raw(pps);

        Box avc1 = new Box("avc1");
        avc1.zeros(6).u16(1).zeros(16).u16(width).u16(height);
        avc1.u32(0x00480000).u32(0x00480000).u32(0).u16(1);
        byte[] name = new byte[32];
        byte[] label = "Ex1 I_PCM".getBytes(StandardCharsets.US_ASCII);
        name[0] = (byte) label.length;
        System.arraycopy(label, 0, name, 1, label.length);
        avc1.raw(name).u16(0x0018).u16(0xFFFF).child(avcC);

        Box stsd = new Box("stsd");
        stsd.u32(0).u32(1).child(avc1);
        Box stts = new Box("stts");
        stts.u32(0).u32(1).u32(n).u32(1);
        Box stsc = new Box("stsc");
        stsc.u32(0).u32(1).u32(1).u32(n).u32(1);
        Box stsz = new Box("stsz");
        stsz.u32(0).u32(0).u32(n);
        for (int i = 0; i < n; i++)
        {
            stsz.u32(sampleSizes.get(i));
        }
        Box stco = new Box("stco");
        stco.u32(0).u32(1).u32(firstSampleOffset);

        Box stbl = new Box("stbl");
        stbl.child(stsd).child(stts).child(stsc).child(stsz).child(stco);
        Box minf = new Box("minf");
        minf.child(vmhd).child(dinf).child(stbl);
        Box mdia = new Box("mdia");
        mdia.child(mdhd).child(hdlr).child(minf);
        Box trak = new Box("trak");
        trak.child(tkhd).child(mdia);
        Box moov = new Box("moov");
        moov.child(mvhd).child(trak);
        return moov.toBytes();
    }

    /**
     * @throws (IOException) on write errors
     */
    private void writeFtyp() throws IOException
    {
        Box ftyp = new Box("ftyp");
        ftyp.raw(fourCc("isom")).u32(0x200).raw(fourCc("isom")).raw(fourCc("iso2"))
                .raw(fourCc("avc1")).raw(fourCc("mp41"));
        out.write(ftyp.toBytes());
    }

    // ========================================================= LOW-LEVEL HELPERS

    /**
     * Inserts emulation-prevention bytes: 00 00 0x (x <= 3) becomes 00 00 03 0x, so the
     * payload can never imitate a start code.
     *
     * @param (byte[]) raw NAL unit
     * @return escaped NAL unit
     */
    static byte[] escape(byte[] raw)
    {
        ByteArrayOutputStream o = new ByteArrayOutputStream(raw.length + raw.length / 64 + 16);
        int zeros = 0;
        for (int i = 0; i < raw.length; i++)
        {
            int v = raw[i] & 0xFF;
            if (zeros >= 2 && v <= 3)
            {
                o.write(3);
                zeros = 0;
            }
            o.write(v);
            if (v == 0)
            {
                zeros++;
            }
            else
            {
                zeros = 0;
            }
        }
        return o.toByteArray();
    }

    /**
     * @param (String) s four ASCII characters
     * @return their bytes
     */
    private static byte[] fourCc(String s)
    {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /** Growable int array (avoids boxing one Integer per frame). */
    private static final class IntList
    {
        private int[] data = new int[256];
        private int size;

        /**
         * @param (int) v value to append
         */
        void add(int v)
        {
            if (size == data.length)
            {
                int[] bigger = new int[size * 2];
                System.arraycopy(data, 0, bigger, 0, size);
                data = bigger;
            }
            data[size] = v;
            size++;
        }

        /**
         * @param (int) i index
         * @return value
         */
        int get(int i)
        {
            return data[i];
        }

        /**
         * @return number of values
         */
        int size()
        {
            return size;
        }
    }

    /** MSB-first bit writer with Exp-Golomb codes. */
    static final class BitWriter
    {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 16);
        private int current;
        private int count;

        /**
         * @param (int) b 0 or 1
         */
        void bit(int b)
        {
            current = (current << 1) | (b & 1);
            count++;
            if (count == 8)
            {
                bytes.write(current);
                current = 0;
                count = 0;
            }
        }

        /**
         * @param (int) value value
         * @param (int) n number of low bits to write, MSB first
         */
        void bits(int value, int n)
        {
            for (int i = n - 1; i >= 0; i--)
            {
                bit(value >>> i);
            }
        }

        /**
         * Unsigned Exp-Golomb.
         *
         * @param (int) v value >= 0
         */
        void ue(int v)
        {
            int x = v + 1;
            int len = 32 - Integer.numberOfLeadingZeros(x);
            bits(0, len - 1);
            bits(x, len);
        }

        /**
         * Signed Exp-Golomb.
         *
         * @param (int) v value
         */
        void se(int v)
        {
            if (v > 0)
            {
                ue(2 * v - 1);
            }
            else
            {
                ue(-2 * v);
            }
        }

        /** Pads with zero bits to the next byte boundary. */
        void alignZero()
        {
            while (count != 0)
            {
                bit(0);
            }
        }

        /**
         * Writes a whole byte; the writer must be byte-aligned (fast path for PCM samples).
         *
         * @param (byte) v byte
         */
        void byteAligned(byte v)
        {
            bytes.write(v);
        }

        /** rbsp_trailing_bits: a 1 then zeros to the byte boundary. */
        void trailingBits()
        {
            bit(1);
            alignZero();
        }

        /**
         * @return written bytes (must be aligned)
         */
        byte[] toBytes()
        {
            return bytes.toByteArray();
        }
    }

    /** ISO-BMFF box builder: size + type + payload + children. */
    private static final class Box
    {
        private final String type;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        /**
         * @param (String) type four-character box type
         */
        Box(String type)
        {
            this.type = type;
        }

        /**
         * @param (int) v byte
         * @return this
         */
        Box u8(int v)
        {
            body.write(v);
            return this;
        }

        /**
         * @param (int) v 16-bit value
         * @return this
         */
        Box u16(int v)
        {
            body.write(v >>> 8);
            body.write(v);
            return this;
        }

        /**
         * @param (long) v 32-bit value
         * @return this
         */
        Box u32(long v)
        {
            body.write((int) (v >>> 24));
            body.write((int) (v >>> 16));
            body.write((int) (v >>> 8));
            body.write((int) v);
            return this;
        }

        /**
         * @param (int) n number of zero bytes
         * @return this
         */
        Box zeros(int n)
        {
            for (int i = 0; i < n; i++)
            {
                body.write(0);
            }
            return this;
        }

        /**
         * @param (byte[]) b bytes
         * @return this
         */
        Box raw(byte[] b)
        {
            body.write(b, 0, b.length);
            return this;
        }

        /**
         * Identity transformation matrix (fixed point 16.16 / 2.30).
         *
         * @return this
         */
        Box matrix()
        {
            u32(0x00010000).u32(0).u32(0);
            u32(0).u32(0x00010000).u32(0);
            u32(0).u32(0).u32(0x40000000);
            return this;
        }

        /**
         * @param (Box) c child box
         * @return this
         */
        Box child(Box c)
        {
            return raw(c.toBytes());
        }

        /**
         * @return complete box bytes
         */
        byte[] toBytes()
        {
            byte[] payload = body.toByteArray();
            ByteArrayOutputStream o = new ByteArrayOutputStream(payload.length + 8);
            int size = payload.length + 8;
            o.write(size >>> 24);
            o.write(size >>> 16);
            o.write(size >>> 8);
            o.write(size);
            byte[] t = type.getBytes(StandardCharsets.US_ASCII);
            o.write(t, 0, 4);
            o.write(payload, 0, payload.length);
            return o.toByteArray();
        }
    }
}
