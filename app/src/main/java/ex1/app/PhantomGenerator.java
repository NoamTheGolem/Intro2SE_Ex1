package ex1.app;

import ex1.core.Volume;
import ex1.core.VoxelType;
import ex1.io.NrrdWriter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Builds synthetic NRRD volumes whose cross-sections are known exactly, for the dataset
 * and for visual checks: java -cp Ex0.jar ex1.app.PhantomGenerator KIND TYPE N out.nrrd
 *
 * KIND:  gradient  value = x + y + z           (any slice is a smooth ramp)
 *        spheres   nested balls around centre  (any slice through the centre = rings)
 *        checker   3D checkerboard of 16^3     (shows orientation and aliasing)
 * TYPE:  uint8 | uint16 | uint32
 */
public final class PhantomGenerator
{
    private static final int CHECKER_BLOCK = 16;
    private static final int RINGS = 6;

    private PhantomGenerator()
    {
    }

    /**
     * @param (String[]) args kind, type, N, output file
     */
    public static void main(String[] args)
    {
        if (args.length != 4)
        {
            System.err.println("Usage: PhantomGenerator gradient|spheres|checker uint8|uint16|uint32 N out.nrrd");
            System.exit(1);
        }
        try
        {
            VoxelType type = VoxelType.valueOf(args[1].toUpperCase(Locale.ROOT));
            int n = Integer.parseInt(args[2]);
            Volume v = build(args[0].toLowerCase(Locale.ROOT), type, n);
            NrrdWriter.write(v, new File(args[3]), true, ByteOrder.LITTLE_ENDIAN);
            System.out.println("Wrote " + args[3] + " (" + v + ")");
        }
        catch (IOException | IllegalArgumentException e)
        {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * @param (String) kind phantom kind
     * @param (VoxelType) type storage type
     * @param (int) n edge length
     * @return the phantom volume
     */
    static Volume build(String kind, VoxelType type, int n)
    {
        Volume v = Volume.create(type, n, n, n);
        double maxValue = maxValue(type);
        double c = (n - 1) / 2.0;
        double maxR = n / 2.0;
        for (int z = 0; z < n; z++)
        {
            for (int y = 0; y < n; y++)
            {
                for (int x = 0; x < n; x++)
                {
                    double value;
                    if (kind.equals("gradient"))
                    {
                        value = (x + y + z) * maxValue / (3.0 * Math.max(1, n - 1));
                    }
                    else if (kind.equals("spheres"))
                    {
                        double r = Math.sqrt((x - c) * (x - c) + (y - c) * (y - c) + (z - c) * (z - c));
                        int ring = (int) (r / maxR * RINGS);
                        value = 0;
                        if (ring < RINGS)
                        {
                            value = (RINGS - ring) * maxValue / RINGS;
                        }
                    }
                    else if (kind.equals("checker"))
                    {
                        int parity = (x / CHECKER_BLOCK + y / CHECKER_BLOCK + z / CHECKER_BLOCK) & 1;
                        value = parity * maxValue;
                    }
                    else
                    {
                        throw new IllegalArgumentException("unknown phantom kind " + kind);
                    }
                    v.setValue(v.index(x, y, z), value);
                }
            }
        }
        return v;
    }

    /**
     * @param (VoxelType) type storage type
     * @return largest value used for that type
     */
    private static double maxValue(VoxelType type)
    {
        if (type == VoxelType.UINT8)
        {
            return 255;
        }
        if (type == VoxelType.UINT16)
        {
            return 65535;
        }
        // Large on purpose: exercises values above the signed 32-bit range.
        return 4_000_000_000.0;
    }
}
