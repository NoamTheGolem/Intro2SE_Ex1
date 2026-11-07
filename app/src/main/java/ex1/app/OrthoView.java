package ex1.app;

import ex1.app.ui.Theme;
import ex1.core.Image2D;
import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * One of the three standard views of 3D Slicer (the lecturer's Figure 1): axial, coronal or
 * sagittal, with its own slice slider. On top of the slice it draws where the cutting plane
 * crosses it (orange: the part inside the triangle / trapezoid, dashed: the rest of the plane),
 * the plane's points, and the positions of the other two views. A click places the next point
 * while picking; otherwise it moves the other two views through the clicked spot. The wheel
 * scrolls through the slices.
 *
 * The slice image is computed with the same {@link Projector} as the video, on the view's own
 * rectangle plane ({@link VolumeGeometry#slicePlane}), so it is also a live demonstration of the
 * algorithm on axis-aligned planes.
 */
final class OrthoView extends JComponent
{
    /** Receives clicks and slice changes. */
    interface Host
    {
        /**
         * Called when a slice view is clicked.
         * @param (int) view Which view was clicked.
         * @param (Vec3) index The clicked point in voxel indices.
         */
        void orthoClicked(int view, Vec3 index);

        /**
         * Called when a view moves to another slice.
         * @param (int) view View whose slice changed.
         */
        void sliceChanged(int view);
    }

    /**
     * Each view's colour (axial, coronal, sagittal): its header and its line in the other views.
     */
    static final Color[] VIEW_COLOURS = {new Color(0xE05252), new Color(0x63B85F), new Color(0xE3C14A)};

    /** Version of the serialized form (Swing components are Serializable). */
    private static final long serialVersionUID = 1L;

    /** Colours of the slice, the plane and the patient directions. */
    private static final Color BACKGROUND = Theme.WELL;
    private static final Color PLANE      = new Color(0xFF9F1C);
    private static final Color LABEL      = new Color(0xF2D64B);

    /** Slices per wheel notch with Shift. */
    private static final int FAST_STEP = 10;

    /** Space around the slice, in pixels. */
    private static final int MARGIN = 14;

    /** Point marks. */
    private static final double MARK_DISTANCE = 2.5;   // mm: a point this close to the slice is drawn solid, else as a ring
    private static final double SAME_SPOT     = 6.0;   // pixels: points this close share one label

    /** Which view this is, and who hears its clicks. */
    private final int            view;
    private final transient Host host;

    /** The projector that cuts the slices, the scan, and the slice on show as an image. */
    private final transient Projector projector = Projector.create(Projector.Interpolation.NEAREST);
    private transient VolumeGeometry  geometry;
    private transient Volume          volume;
    private transient IntensityWindow window;
    private transient BufferedImage   image;

    /** The plane, its points, and where the other two views are. */
    private transient Plane        plane;
    private transient List<Vec3>   points      = new ArrayList<Vec3>();
    private transient List<String> pointNames  = new ArrayList<String>();
    private final int[]            otherSlices = new int[3];

    /** The slice on show, and whether a click places a point. */
    private int     slice;
    private boolean picking;

    /** Where the slice was drawn and how big a voxel is on the screen, to map clicks back. */
    private double drawX;
    private double drawY;
    private double pixelW = 1;
    private double pixelH = 1;

    /**
     * Creates a slice view and its mouse handling: a click places a point or moves the other views
     * there, the wheel steps through the slices (Shift: ten at a time).
     * @param (int) view VolumeGeometry.AXIAL, CORONAL or SAGITTAL.
     * @param (Host) host Receives clicks.
     */
    OrthoView(int view, Host host)
    {
        this.view = view;
        this.host = host;
        setOpaque(true);
        setBackground(BACKGROUND);
        setFont(Theme.semibold(Theme.TEXT_SIZE));
        setPreferredSize(new Dimension(260, 220));
        MouseAdapter mouse = new MouseAdapter()
        {
            /**
             * Passes a click on the drawn slice to the app, as a point in voxel indices.
             * @param (MouseEvent) e The click.
             */
            @Override
            public void mouseClicked(MouseEvent e)
            {
                Vec3 p = toIndex(e.getX(), e.getY());
                if (p != null && OrthoView.this.host != null)
                {
                    OrthoView.this.host.orthoClicked(OrthoView.this.view, p);
                }
            }

            /**
             * Steps through the slices: one per wheel notch, ten with Shift.
             * @param (MouseWheelEvent) e The wheel turn.
             */
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (volume == null)
                {
                    return;
                }
                int step = 1;
                if (e.isShiftDown())
                {
                    step = FAST_STEP;
                }
                setSlice(slice - e.getWheelRotation() * step);
                if (OrthoView.this.host != null)
                {
                    OrthoView.this.host.sliceChanged(OrthoView.this.view);
                }
            }
        };
        addMouseListener(mouse);
        addMouseWheelListener(mouse);
    }

    // ==========================================
    //                  DATA IN
    // ==========================================

    /**
     * Shows a new volume, starting at its middle slice.
     * @param (Volume) v Volume, or null.
     * @param (IntensityWindow) w Grey mapping.
     * @param (VolumeGeometry) g Geometry.
     */
    void setVolume(Volume v, IntensityWindow w, VolumeGeometry g)
    {
        volume = v;
        window = w;
        geometry = g;
        image = null;
        if (v != null)
        {
            slice = (g.size(g.constantAxis(view)) - 1) / 2;
            renderSlice();
        }
        repaint();
    }

    /**
     * Moves to another slice and draws it.
     * @param (int) k New slice index (clamped).
     */
    void setSlice(int k)
    {
        if (volume == null)
        {
            return;
        }
        int max     = geometry.size(geometry.constantAxis(view)) - 1;
        int clamped = Math.max(0, Math.min(max, k));
        if (clamped != slice || image == null)
        {
            slice = clamped;
            renderSlice();
            repaint();
        }
    }

    /**
     * Gives the slice on show.
     * @return (int) Current slice index along the constant axis.
     */
    int slice()
    {
        return slice;
    }

    /**
     * Gives how many slices the view can show.
     * @return (int) Number of slices.
     */
    int sliceCount()
    {
        if (geometry == null)
        {
            return 1;
        }
        return geometry.size(geometry.constantAxis(view));
    }

    /**
     * Shows where a new plane crosses the slice, with its points.
     * @param (Plane) p Cutting plane (null hides it).
     * @param (List<Vec3>) pts The plane's points to mark.
     * @param (List<String>) names Their labels.
     */
    void setPlane(Plane p, List<Vec3> pts, List<String> names)
    {
        plane = p;
        points = new ArrayList<Vec3>(pts);
        pointNames = new ArrayList<String>(names);
        repaint();
    }

    /**
     * Records where the other two views are, for the crosshair lines.
     * @param (int[]) slices Current slice of each view (to draw the other views' positions).
     */
    void setOtherSlices(int[] slices)
    {
        System.arraycopy(slices, 0, otherSlices, 0, 3);
        repaint();
    }

    /**
     * Switches picking mode on or off: a crosshair cursor and a hint while it is on.
     * @param (boolean) on True while the user is placing points.
     */
    void setPicking(boolean on)
    {
        picking = on;
        if (on)
        {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
        else
        {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    // ==========================================
    //                  PAINTING
    // ==========================================

    /** Projects the view's rectangle at the current slice. */
    private void renderSlice()
    {
        Plane   p    = geometry.slicePlane(view, slice);
        Image2D img  = projector.project(volume, p);
        int     w    = img.width();
        int     h    = img.height();
        byte[]  grey = new byte[w * h];
        window.toGrey(img, grey, w, h, w, h);
        BufferedImage bi  = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[]         rgb = new int[w * h];
        for (int i = 0; i < rgb.length; i++)
        {
            int g = grey[i] & 0xFF;
            rgb[i] = (g << 16) | (g << 8) | g;
        }
        bi.setRGB(0, 0, w, h, rgb, 0, w);
        image = bi;
    }

    /**
     * Draws the slice at its true proportions (mm, not voxels), then the lines of the other two
     * views, the plane's crossing with its points, and the patient directions.
     * @param (Graphics) g0 Graphics of the view.
     */
    @Override
    protected void paintComponent(Graphics g0)
    {
        Graphics2D g = (Graphics2D) g0;
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (image == null)
        {
            g.setColor(Theme.TEXT_FAINT);
            String hint = VolumeGeometry.name(view);
            g.drawString(hint, (getWidth() - g.getFontMetrics().stringWidth(hint)) / 2, getHeight() / 2);
            return;
        }
        int    h     = geometry.horizontalAxis(view);
        int    v     = geometry.verticalAxis(view);
        double physW = image.getWidth() * geometry.spacing(h);
        double physH = image.getHeight() * geometry.spacing(v);
        double scale = Math.min((getWidth() - 2.0 * MARGIN) / physW, (getHeight() - 2.0 * MARGIN) / physH);
        pixelW = geometry.spacing(h) * scale;
        pixelH = geometry.spacing(v) * scale;
        double dw = image.getWidth() * pixelW;
        double dh = image.getHeight() * pixelH;
        drawX = (getWidth() - dw) / 2;
        drawY = (getHeight() - dh) / 2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, (int) Math.round(drawX), (int) Math.round(drawY), (int) Math.round(dw), (int) Math.round(dh), null);
        drawOtherViews(g);
        drawPlane(g);
        drawPoints(g);
        drawLabels(g, dw, dh);
    }

    /**
     * Draws thin lines where the other two views cut this one (Slicer's slice intersections).
     * @param (Graphics2D) g Graphics of the view.
     */
    private void drawOtherViews(Graphics2D g)
    {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1f));
        for (int other = 0; other < 3; other++)
        {
            if (other == view)
            {
                continue;
            }
            int    axis = geometry.constantAxis(other);
            double k    = otherSlices[other];
            Color  c    = VIEW_COLOURS[other];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
            if (axis == geometry.horizontalAxis(view))
            {
                double sx = screenX(k);
                g.drawLine((int) sx, (int) drawY, (int) sx, (int) (drawY + image.getHeight() * pixelH));
            }
            else if (axis == geometry.verticalAxis(view))
            {
                double sy = screenY(k);
                g.drawLine((int) drawX, (int) sy, (int) (drawX + image.getWidth() * pixelW), (int) sy);
            }
        }
        g.setStroke(old);
    }

    /**
     * Draws where the cutting plane crosses this slice.
     * @param (Graphics2D) g Graphics of the view.
     */
    private void drawPlane(Graphics2D g)
    {
        if (plane == null || !plane.isRenderable())
        {
            return;
        }
        int    c = geometry.constantAxis(view);
        double k = slice;
        // The whole (infinite) plane: dashed.
        Vec3     n    = normalOf(plane);
        double[] line = planeLine(n, plane.a1(), c, k);
        Stroke   old  = g.getStroke();
        if (line != null)
        {
            g.setColor(new Color(255, 159, 28, 120));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 5f}, 0f));
            g.drawLine((int) line[0], (int) line[1], (int) line[2], (int) line[3]);
        }
        // The triangle / trapezoid itself: solid.
        Vec3[] poly;
        if (plane.isTriangle())
        {
            poly = new Vec3[] {plane.a1(), plane.c(), plane.b()};
        }
        else
        {
            poly = new Vec3[] {plane.a1(), plane.a2(), plane.c(), plane.b()};
        }
        List<double[]> hits = new ArrayList<double[]>();
        for (int i = 0; i < poly.length; i++)
        {
            Vec3   p  = poly[i];
            Vec3   q  = poly[(i + 1) % poly.length];
            double pc = coord(p, c) - k;
            double qc = coord(q, c) - k;
            if ((pc <= 0 && qc > 0) || (pc > 0 && qc <= 0))
            {
                double t = pc / (pc - qc);
                Vec3   x = p.add(q.sub(p).scale(t));
                hits.add(new double[] {screenX(coord(x, geometry.horizontalAxis(view))),
                    screenY(coord(x, geometry.verticalAxis(view)))});
            }
        }
        g.setColor(PLANE);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i + 1 < hits.size(); i += 2)
        {
            double[] a = hits.get(i);
            double[] b = hits.get(i + 1);
            g.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
        }
        g.setStroke(old);
    }

    /**
     * Draws the plane's points (a, b, c ...) near this slice: solid on it, a ring further away.
     * @param (Graphics2D) g Graphics of the view.
     */
    private void drawPoints(Graphics2D g)
    {
        int c = geometry.constantAxis(view);
        g.setFont(Theme.semibold(Theme.SMALL_SIZE));
        int      n  = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++)
        {
            Vec3 p = points.get(i);
            xs[i] = screenX(coord(p, geometry.horizontalAxis(view)));
            ys[i] = screenY(coord(p, geometry.verticalAxis(view)));
        }
        for (int i = 0; i < n; i++)
        {
            Vec3   p    = points.get(i);
            double dist = Math.abs(coord(p, c) - slice) * geometry.spacing(c);
            double sx   = xs[i];
            double sy   = ys[i];
            if (dist <= MARK_DISTANCE)
            {
                g.setColor(PLANE);
                g.fillOval((int) sx - 5, (int) sy - 5, 10, 10);
                g.setColor(Color.BLACK);
                g.drawOval((int) sx - 5, (int) sy - 5, 10, 10);
            }
            else
            {
                g.setColor(new Color(255, 159, 28, 110));
                g.drawOval((int) sx - 4, (int) sy - 4, 8, 8);
            }
            String label = sharedLabel(i, xs, ys);
            if (label != null)
            {
                g.setColor(PLANE);
                g.drawString(label, (int) sx + 7, (int) sy - 6);
            }
        }
    }

    /**
     * Points that land on the same spot of this view (they differ only along the view's depth)
     * share one label, "a1, b", instead of printing their names on top of each other.
     * @param (int) i Index of the point.
     * @param (double[]) xs Screen x of every point.
     * @param (double[]) ys Screen y of every point.
     * @return (String) The label to draw at point i, or null when an earlier point already carries
     *                  it.
     */
    private String sharedLabel(int i, double[] xs, double[] ys)
    {
        for (int j = 0; j < i; j++)
        {
            if (Math.abs(xs[j] - xs[i]) < SAME_SPOT && Math.abs(ys[j] - ys[i]) < SAME_SPOT)
            {
                return null;
            }
        }
        StringBuilder label = new StringBuilder(pointNames.get(i));
        for (int j = i + 1; j < xs.length; j++)
        {
            if (Math.abs(xs[j] - xs[i]) < SAME_SPOT && Math.abs(ys[j] - ys[i]) < SAME_SPOT)
            {
                label.append(", ").append(pointNames.get(j));
            }
        }
        return label.toString();
    }

    /**
     * Writes the patient directions at the edges of the slice, and the picking hint.
     * @param (Graphics2D) g Graphics.
     * @param (double) dw Drawn image width.
     * @param (double) dh Drawn image height.
     */
    private void drawLabels(Graphics2D g, double dw, double dh)
    {
        g.setFont(getFont());
        String[] labels = geometry.labels(view);
        if (labels != null)
        {
            g.setColor(LABEL);
            int cx = (int) (drawX + dw / 2);
            int cy = (int) (drawY + dh / 2);
            centred(g, labels[0], cx, (int) drawY + 14);
            centred(g, labels[1], cx, (int) (drawY + dh) - 4);
            g.drawString(labels[2], (int) drawX + 3, cy + 5);
            g.drawString(labels[3], (int) (drawX + dw) - g.getFontMetrics().stringWidth(labels[3]) - 3, cy + 5);
        }
        if (picking)
        {
            g.setColor(Theme.PLANE);
            g.setFont(Theme.font(Theme.SMALL_SIZE));
            g.drawString("Click to place the point", 10, getHeight() - 10);
        }
    }

    // ==========================================
    //                  MAPPING
    // ==========================================

    /**
     * Maps a mouse position on the drawn slice back to voxel indices.
     * @param (int) mx Mouse x.
     * @param (int) my Mouse y.
     * @return (Vec3) The voxel-index point under the mouse, or null outside the slice.
     */
    Vec3 toIndex(int mx, int my)
    {
        if (image == null)
        {
            return null;
        }
        double col = (mx - drawX) / pixelW - 0.5;
        double row = (my - drawY) / pixelH - 0.5;
        if (col < -0.5 || row < -0.5 || col > image.getWidth() - 0.5 || row > image.getHeight() - 0.5)
        {
            return null;
        }
        int      h = geometry.horizontalAxis(view);
        int      v = geometry.verticalAxis(view);
        int      c = geometry.constantAxis(view);
        double[] p = new double[3];
        p[h] = start(h, geometry.horizontalStep(view)) + col * geometry.horizontalStep(view);
        p[v] = start(v, geometry.verticalStep(view)) + row * geometry.verticalStep(view);
        p[c] = slice;
        return new Vec3(p[0], p[1], p[2]);
    }

    /**
     * Maps an index along the view's horizontal axis to a screen x.
     * @param (double) index Index along the horizontal axis.
     * @return (double) Screen x of that voxel's centre.
     */
    private double screenX(double index)
    {
        int    h   = geometry.horizontalAxis(view);
        double col = (index - start(h, geometry.horizontalStep(view))) * geometry.horizontalStep(view);
        return drawX + (col + 0.5) * pixelW;
    }

    /**
     * Maps an index along the view's vertical axis to a screen y.
     * @param (double) index Index along the vertical axis.
     * @return (double) Screen y of that voxel's centre.
     */
    private double screenY(double index)
    {
        int    v   = geometry.verticalAxis(view);
        double row = (index - start(v, geometry.verticalStep(view))) * geometry.verticalStep(view);
        return drawY + (row + 0.5) * pixelH;
    }

    /**
     * Finds the index at the start of the drawn image along one axis, which depends on the
     * direction the axis is drawn in.
     * @param (int) axis Index axis.
     * @param (int) step +1 or -1.
     * @return (double) The index at the left / top edge of the image.
     */
    private double start(int axis, int step)
    {
        if (step > 0)
        {
            return 0;
        }
        return geometry.size(axis) - 1;
    }

    /**
     * Finds where the infinite plane crosses the slice, clipped to the image rectangle.
     * @param (Vec3) n Plane normal (index space).
     * @param (Vec3) a A point of the plane.
     * @param (int) c The view's constant axis.
     * @param (double) k The slice.
     * @return (double[]) Screen segment {x1, y1, x2, y2} or null.
     */
    private double[] planeLine(Vec3 n, Vec3 a, int c, double k)
    {
        int            h       = geometry.horizontalAxis(view);
        int            v       = geometry.verticalAxis(view);
        double         hMax    = geometry.size(h) - 1;
        double         vMax    = geometry.size(v) - 1;
        double[][]     corners = {{0, 0}, {hMax, 0}, {hMax, vMax}, {0, vMax}};
        List<double[]> pts     = new ArrayList<double[]>();
        for (int i = 0; i < 4; i++)
        {
            double[] p  = corners[i];
            double[] q  = corners[(i + 1) % 4];
            double   fp = side(n, a, h, p[0], v, p[1], c, k);
            double   fq = side(n, a, h, q[0], v, q[1], c, k);
            if ((fp <= 0 && fq > 0) || (fp > 0 && fq <= 0))
            {
                double t = fp / (fp - fq);
                pts.add(new double[] {p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
            }
        }
        if (pts.size() < 2)
        {
            return null;
        }
        return new double[] {screenX(pts.get(0)[0]), screenY(pts.get(0)[1]), screenX(pts.get(1)[0]), screenY(pts.get(1)[1])};
    }

    /**
     * Evaluates the plane equation at a corner of the slice, to find where the plane crosses its
     * edges.
     * @param (Vec3) n Normal.
     * @param (Vec3) a Plane point.
     * @param (int) h Axis of the first coordinate.
     * @param (double) hv Its value.
     * @param (int) v Axis of the second coordinate.
     * @param (double) vv Its value.
     * @param (int) c Axis of the third coordinate.
     * @param (double) cv Its value.
     * @return (double) Signed distance of that point from the plane (times |n|).
     */
    private static double side(Vec3 n, Vec3 a, int h, double hv, int v, double vv, int c, double cv)
    {
        double[] p = new double[3];
        p[h] = hv;
        p[v] = vv;
        p[c] = cv;
        return (p[0] - a.x) * n.x + (p[1] - a.y) * n.y + (p[2] - a.z) * n.z;
    }

    /**
     * Computes a normal of the plane from its points (the longer of the two cross products for a
     * trapezoid, so a thin one does not lose precision).
     * @param (Plane) p Plane.
     * @return (Vec3) A normal of it (index space).
     */
    static Vec3 normalOf(Plane p)
    {
        Vec3 n = p.b().sub(p.a1()).cross(p.c().sub(p.a1()));
        if (!p.isTriangle())
        {
            Vec3 n2 = p.b().sub(p.a1()).cross(p.a2().sub(p.a1()));
            if (n2.length() > n.length())
            {
                n = n2;
            }
        }
        return n;
    }

    /**
     * Picks one coordinate of a point by axis number.
     * @param (Vec3) p Point.
     * @param (int) axis 0, 1, 2.
     * @return (double) The coordinate.
     */
    private static double coord(Vec3 p, int axis)
    {
        if (axis == 0)
        {
            return p.x;
        }
        if (axis == 1)
        {
            return p.y;
        }
        return p.z;
    }

    /**
     * Draws a text centred on a point.
     * @param (Graphics2D) g Graphics.
     * @param (String) s Text.
     * @param (int) cx Centre x.
     * @param (int) y Baseline.
     */
    private static void centred(Graphics2D g, String s, int cx, int y)
    {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    /** A view with its header: coloured bar, name, slice slider and position. */
    static final class Panel extends JPanel
    {
        /** Version of the serialized form (Swing components are Serializable). */
        private static final long serialVersionUID = 1L;

        /** The view, its slider and the line that says which slice is on show. */
        private final OrthoView ortho;
        private final JSlider   slider   = new JSlider(0, 1, 0);
        private final JLabel    position = new JLabel(" ");

        /** True while sync() moves the slider, so the move is not taken for the user's. */
        private boolean updating;

        /**
         * Creates the view with its slider bar.
         * @param (int) view View.
         * @param (Host) host Receives clicks.
         * @param (boolean) withName False when the panel's container already names the view.
         */
        Panel(final int view, Host host, boolean withName)
        {
            super(new BorderLayout());
            ortho = new OrthoView(view, host);
            final Host h   = host;
            JPanel     bar = new JPanel(new BorderLayout(8, 0));
            bar.setBackground(Theme.CHROME);
            bar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            JLabel name = new JLabel(VolumeGeometry.name(view) + "  " + VolumeGeometry.hint(view));
            name.setForeground(Theme.TEXT);
            name.setFont(Theme.semibold(Theme.TEXT_SIZE));
            position.setForeground(Theme.TEXT_DIM);
            position.setFont(Theme.font(Theme.SMALL_SIZE));
            slider.setOpaque(false);
            slider.setEnabled(false);
            slider.addChangeListener(new ChangeListener()
            {
                /**
                 * Follows the slider to its slice; a change made by sync() itself is not passed on
                 * again.
                 * @param (ChangeEvent) e The slider's change.
                 */
                @Override
                public void stateChanged(ChangeEvent e)
                {
                    if (!updating)
                    {
                        ortho.setSlice(slider.getValue());
                        showPosition();
                        if (h != null)
                        {
                            h.sliceChanged(view);
                        }
                    }
                }
            });
            if (withName)
            {
                bar.add(name, BorderLayout.WEST);
            }
            slider.setToolTipText("Which slice of the scan this view shows");
            bar.add(slider, BorderLayout.CENTER);
            bar.add(position, BorderLayout.EAST);
            add(bar, BorderLayout.NORTH);
            add(ortho, BorderLayout.CENTER);
        }

        /**
         * Gives the slice view inside the panel.
         * @return (OrthoView) The view.
         */
        OrthoView view()
        {
            return ortho;
        }

        /** Re-reads the slice range and position from the view. */
        void sync()
        {
            updating = true;
            slider.setMaximum(Math.max(1, ortho.sliceCount() - 1));
            slider.setValue(ortho.slice());
            slider.setEnabled(ortho.volume != null);
            updating = false;
            showPosition();
        }

        /** Updates the "k / max" label. */
        private void showPosition()
        {
            position.setText(ortho.slice() + " / " + (ortho.sliceCount() - 1));
        }
    }
}
