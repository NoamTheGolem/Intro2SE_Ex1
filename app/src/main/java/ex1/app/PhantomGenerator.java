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
 *        dice      the die of the course's edge-case file (fixed size 200 x 180 x 160 uint32,
 *                  TYPE and N are ignored): cube x 40..159, y 30..149, z 20..139 = 100000,
 *                  pips = 200000, 1 on top (z = 139), 6 at the bottom, 4 in front (y = 30),
 *                  3 at the back, 2 on the left (x = 40), 5 on the right
 * TYPE:  uint8 | uint16 | uint32
 */
public final class PhantomGenerator
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Shapes of the phantoms. */
    private static final int CHECKER_BLOCK = 16;   // voxels per checkerboard square
    private static final int RINGS         = 6;    // shells of the spheres phantom, brightest inside

    /** The die of the lecturer's edge_cases.csv: the volume, the cube in it, and its pips. */
    private static final int   DICE_SX  = 200;
    private static final int   DICE_SY  = 180;
    private static final int   DICE_SZ  = 160;
    private static final int[] DICE_MIN = {40, 30, 20};      // first voxel of the cube per axis
    private static final int[] DICE_MAX = {159, 149, 139};   // last voxel of the cube per axis

    /** Values of the body and of the pips (the pips are brighter). */
    private static final double DICE_BODY = 100000;
    private static final double DICE_PIP  = 200000;

    /** Size and spacing of the pips, in voxels. */
    private static final double PIP_RADIUS = 7.0;
    private static final int    PIP_DEPTH  = 4;      // how deep a pip goes into its face
    private static final double PIP_OFFSET = 30.0;   // from the face's centre to an outer pip

    /** A program: main and its helpers only; never instantiated. */
    private PhantomGenerator()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Program entry point: writes a phantom volume as a gzip NRRD file.
     * @param (String[]) args Kind, type, N, output file.
     */
    public static void main(String[] args)
    {
        if (args.length != 4)
        {
            System.err.println("Usage: PhantomGenerator gradient|spheres|checker|dice uint8|uint16|uint32 N out.nrrd");
            System.exit(1);
        }
        try
        {
            VoxelType type = VoxelType.valueOf(args[1].toUpperCase(Locale.ROOT));
            int       n    = Integer.parseInt(args[2]);
            Volume    v    = build(args[0].toLowerCase(Locale.ROOT), type, n);
            NrrdWriter.write(v, new File(args[3]), true, ByteOrder.LITTLE_ENDIAN);
            System.out.println("Wrote " + args[3] + " (" + v + ")");
        }
        catch (IOException | IllegalArgumentException e)
        {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Builds a phantom of a known shape: spheres, a checkerboard or a gradient.
     * @param (String) kind Phantom kind.
     * @param (VoxelType) type Storage type.
     * @param (int) n Edge length.
     * @return (Volume) The phantom volume.
     */
    static Volume build(String kind, VoxelType type, int n)
    {
        if (kind.equals("dice"))
        {
            return buildDice();
        }
        Volume v        = Volume.create(type, n, n, n);
        double maxValue = maxValue(type);
        double c        = (n - 1) / 2.0;
        double maxR     = n / 2.0;
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
                        double r    = Math.sqrt((x - c) * (x - c) + (y - c) * (y - c) + (z - c) * (z - c));
                        int    ring = (int) (r / maxR * RINGS);
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
     * Builds the die described by the course's edge_cases.csv ("DATA/synthetic/dice.nrrd"), so that
     * its 16 expected frames can be checked: opposite faces add up to 7, as on a real die.
     * @return (Volume) 200 x 180 x 160 uint32 volume.
     */
    static Volume buildDice()
    {
        Volume   v      = Volume.create(VoxelType.UINT32, DICE_SX, DICE_SY, DICE_SZ);
        double[] centre = new double[3];
        for (int a = 0; a < 3; a++)
        {
            centre[a] = (DICE_MIN[a] + DICE_MAX[a]) / 2.0;
        }
        // Faces: axis, which end (0 = min, 1 = max), number of pips.
        int[][] faces = {{2, 1, 1}, {2, 0, 6}, {1, 0, 4}, {1, 1, 3}, {0, 0, 2}, {0, 1, 5}};
        for (int z = DICE_MIN[2]; z <= DICE_MAX[2]; z++)
        {
            for (int y = DICE_MIN[1]; y <= DICE_MAX[1]; y++)
            {
                for (int x = DICE_MIN[0]; x <= DICE_MAX[0]; x++)
                {
                    int[]  p     = {x, y, z};
                    double value = DICE_BODY;
                    for (int f = 0; f < faces.length; f++)
                    {
                        if (onPip(p, centre, faces[f][0], faces[f][1], faces[f][2]))
                        {
                            value = DICE_PIP;
                        }
                    }
                    v.setValue(v.index(x, y, z), value);
                }
            }
        }
        return v;
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Tests whether a voxel of a die's face lies on one of that face's pips.
     * @param (int[]) p Voxel.
     * @param (double[]) centre Centre of the die.
     * @param (int) axis Axis the face is normal to.
     * @param (int) end 0 for the face at the minimum, 1 for the maximum.
     * @param (int) pips Number of pips on that face.
     * @return (boolean) True when p lies in one of the face's pips.
     */
    private static boolean onPip(int[] p, double[] centre, int axis, int end, int pips)
    {
        int face = DICE_MIN[axis];
        if (end == 1)
        {
            face = DICE_MAX[axis];
        }
        if (Math.abs(p[axis] - face) >= PIP_DEPTH)
        {
            return false;
        }
        int        u     = (axis + 1) % 3;
        int        w     = (axis + 2) % 3;
        double     du    = p[u] - centre[u];
        double     dw    = p[w] - centre[w];
        double[][] spots = pipSpots(pips);
        for (int i = 0; i < spots.length; i++)
        {
            double eu = du - spots[i][0] * PIP_OFFSET;
            double ew = dw - spots[i][1] * PIP_OFFSET;
            if (eu * eu + ew * ew <= PIP_RADIUS * PIP_RADIUS)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives the pip layout of a die face.
     * @param (int) pips 1 to 6.
     * @return (double[][]) Pip centres in units of PIP_OFFSET from the face centre.
     */
    private static double[][] pipSpots(int pips)
    {
        if (pips == 1)
        {
            return new double[][] {{0, 0}};
        }
        if (pips == 2)
        {
            return new double[][] {{-1, -1}, {1, 1}};
        }
        if (pips == 3)
        {
            return new double[][] {{-1, -1}, {0, 0}, {1, 1}};
        }
        if (pips == 4)
        {
            return new double[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        }
        if (pips == 5)
        {
            return new double[][] {{-1, -1}, {-1, 1}, {0, 0}, {1, -1}, {1, 1}};
        }
        return new double[][] {{-1, -1}, {-1, 0}, {-1, 1}, {1, -1}, {1, 0}, {1, 1}};
    }

    /**
     * Gives the brightest value a phantom uses for a storage type: the most an unsigned 8- or
     * 16-bit sample holds, else one beyond the signed 32-bit range.
     * @param (VoxelType) type Storage type.
     * @return (double) Largest value used for that type.
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
