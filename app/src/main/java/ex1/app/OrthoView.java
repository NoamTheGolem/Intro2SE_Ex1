package ex1.app;

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
import java.awt.Font;
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
         * @param (int) view which view was clicked
         * @param (Vec3) index the clicked point in voxel indices
         */
        void orthoClicked(int view, Vec3 index);

        /**
         * @param (int) view view whose slice changed
         */
        void sliceChanged(int view);
    }

    static final Color[] VIEW_COLOURS = {new Color(0xE05252), new Color(0x63B85F), new Color(0xE3C14A)};

    private static final long serialVersionUID = 1L;
    private static final Color BACKGROUND = new Color(0x101114);
    private static final Color PLANE = new Color(0xFF9F1C);
    private static final Color LABEL = new Color(0xF2D64B);
    private static final int MARGIN = 14;
    private static final double MARK_DISTANCE = 2.5;

    private final int view;
    private final transient Host host;
    private final transient Projector projector = Projector.create(Projector.Interpolation.NEAREST);
    private transient VolumeGeometry geometry;
    private transient Volume volume;
    private transient IntensityWindow window;
    private transient BufferedImage image;
    private transient Plane plane;
    private transient List<Vec3> points = new ArrayList<Vec3>();
    private transient List<String> pointNames = new ArrayList<String>();
    private final int[] otherSlices = new int[3];
    private int slice;
    private boolean picking;

    // where the image was last drawn (for mouse mapping)
    private double drawX;
    private double drawY;
    private double pixelW = 1;
    private double pixelH = 1;

    /**
     * @param (int) view VolumeGeometry.AXIAL, CORONAL or SAGITTAL
     * @param (Host) host receives clicks
     */
    OrthoView(int view, Host host)
    {
        this.view = view;
        this.host = host;
        setOpaque(true);
        setBackground(BACKGROUND);
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        setPreferredSize(new Dimension(260, 220));
        MouseAdapter mouse = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                Vec3 p = toIndex(e.getX(), e.getY());
                if (p != null && OrthoView.this.host != null)
                {
                    OrthoView.this.host.orthoClicked(OrthoView.this.view, p);
                }
            }

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
                    step = 10;
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

    // ------------------------------------------------------------------ data in

    /**
     * @param (Volume) v volume, or null
     * @param (IntensityWindow) w grey mapping
     * @param (VolumeGeometry) g geometry
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
     * @param (int) k new slice index (clamped)
     */
    void setSlice(int k)
    {
        if (volume == null)
        {
            return;
        }
        int max = geometry.size(geometry.constantAxis(view)) - 1;
        int clamped = Math.max(0, Math.min(max, k));
        if (clamped != slice || image == null)
        {
            slice = clamped;
            renderSlice();
            repaint();
        }
    }

    /**
     * @return current slice index along the constant axis
     */
    int slice()
    {
        return slice;
    }

    /**
     * @return number of slices
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
     * @param (Plane) p cutting plane (null hides it)
     * @param (List) pts the plane's points to mark
     * @param (List) names their labels
     */
    void setPlane(Plane p, List<Vec3> pts, List<String> names)
    {
        plane = p;
        points = new ArrayList<Vec3>(pts);
        pointNames = new ArrayList<String>(names);
        repaint();
    }

    /**
     * @param (int[]) slices current slice of each view (to draw the other views' positions)
     */
    void setOtherSlices(int[] slices)
    {
        System.arraycopy(slices, 0, otherSlices, 0, 3);
        repaint();
    }

    /**
     * @param (boolean) on true while the user is placing points
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

    // ------------------------------------------------------------------ painting

    /** Projects the view's rectangle at the current slice. */
    private void renderSlice()
    {
        Plane p = geometry.slicePlane(view, slice);
        Image2D img = projector.project(volume, p);
        int w = img.width();
        int h = img.height();
        byte[] grey = new byte[w * h];
        window.toGrey(img, grey, w, h, w, h);
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[w * h];
        for (int i = 0; i < rgb.length; i++)
        {
            int g = grey[i] & 0xFF;
            rgb[i] = (g << 16) | (g << 8) | g;
        }
        bi.setRGB(0, 0, w, h, rgb, 0, w);
        image = bi;
    }

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
            g.setColor(new Color(120, 126, 138));
            String hint = VolumeGeometry.name(view);
            g.drawString(hint, (getWidth() - g.getFontMetrics().stringWidth(hint)) / 2, getHeight() / 2);
            return;
        }
        int h = geometry.horizontalAxis(view);
        int v = geometry.verticalAxis(view);
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

    /** Thin lines where the other two views cut this one (Slicer's slice intersections). */
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
            int axis = geometry.constantAxis(other);
            double k = otherSlices[other];
            Color c = VIEW_COLOURS[other];
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

    /** Where the cutting plane crosses this slice. */
    private void drawPlane(Graphics2D g)
    {
        if (plane == null || !plane.isRenderable())
        {
            return;
        }
        int c = geometry.constantAxis(view);
        double k = slice;
        // The whole (infinite) plane: dashed.
        Vec3 n = normalOf(plane);
        double[] line = planeLine(n, plane.a1(), c, k);
        Stroke old = g.getStroke();
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
            Vec3 p = poly[i];
            Vec3 q = poly[(i + 1) % poly.length];
            double pc = coord(p, c) - k;
            double qc = coord(q, c) - k;
            if ((pc <= 0 && qc > 0) || (pc > 0 && qc <= 0))
            {
                double t = pc / (pc - qc);
                Vec3 x = p.add(q.sub(p).scale(t));
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

    /** The plane's points (a, b, c ...) near this slice. */
    private void drawPoints(Graphics2D g)
    {
        int c = geometry.constantAxis(view);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        for (int i = 0; i < points.size(); i++)
        {
            Vec3 p = points.get(i);
            double dist = Math.abs(coord(p, c) - slice) * geometry.spacing(c);
            double sx = screenX(coord(p, geometry.horizontalAxis(view)));
            double sy = screenY(coord(p, geometry.verticalAxis(view)));
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
            g.setColor(PLANE);
            g.drawString(pointNames.get(i), (int) sx + 7, (int) sy - 6);
        }
    }

    /**
     * @param (Graphics2D) g graphics
     * @param (double) dw drawn image width
     * @param (double) dh drawn image height
     */
    private void drawLabels(Graphics2D g, double dw, double dh)
    {
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
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
            g.setColor(new Color(255, 159, 28));
            g.drawString("click to place the point", 8, getHeight() - 8);
        }
    }

    // ------------------------------------------------------------------ mapping

    /**
     * @param (int) mx mouse x
     * @param (int) my mouse y
     * @return the voxel-index point under the mouse, or null outside the slice
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
        int h = geometry.horizontalAxis(view);
        int v = geometry.verticalAxis(view);
        int c = geometry.constantAxis(view);
        double[] p = new double[3];
        p[h] = start(h, geometry.horizontalStep(view)) + col * geometry.horizontalStep(view);
        p[v] = start(v, geometry.verticalStep(view)) + row * geometry.verticalStep(view);
        p[c] = slice;
        return new Vec3(p[0], p[1], p[2]);
    }

    /**
     * @param (double) index index along the horizontal axis
     * @return screen x of that voxel's centre
     */
    private double screenX(double index)
    {
        int h = geometry.horizontalAxis(view);
        double col = (index - start(h, geometry.horizontalStep(view))) * geometry.horizontalStep(view);
        return drawX + (col + 0.5) * pixelW;
    }

    /**
     * @param (double) index index along the vertical axis
     * @return screen y of that voxel's centre
     */
    private double screenY(double index)
    {
        int v = geometry.verticalAxis(view);
        double row = (index - start(v, geometry.verticalStep(view))) * geometry.verticalStep(view);
        return drawY + (row + 0.5) * pixelH;
    }

    /**
     * @param (int) axis index axis
     * @param (int) step +1 or -1
     * @return the index at the left / top edge of the image
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
     * Where the infinite plane crosses the slice, clipped to the image rectangle.
     *
     * @param (Vec3) n plane normal (index space)
     * @param (Vec3) a a point of the plane
     * @param (int) c the view's constant axis
     * @param (double) k the slice
     * @return screen segment {x1, y1, x2, y2} or null
     */
    private double[] planeLine(Vec3 n, Vec3 a, int c, double k)
    {
        int h = geometry.horizontalAxis(view);
        int v = geometry.verticalAxis(view);
        double hMax = geometry.size(h) - 1;
        double vMax = geometry.size(v) - 1;
        double[][] corners = {{0, 0}, {hMax, 0}, {hMax, vMax}, {0, vMax}};
        List<double[]> pts = new ArrayList<double[]>();
        for (int i = 0; i < 4; i++)
        {
            double[] p = corners[i];
            double[] q = corners[(i + 1) % 4];
            double fp = side(n, a, h, p[0], v, p[1], c, k);
            double fq = side(n, a, h, q[0], v, q[1], c, k);
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
     * @param (Vec3) n normal
     * @param (Vec3) a plane point
     * @param (int) h axis of the first coordinate
     * @param (double) hv its value
     * @param (int) v axis of the second coordinate
     * @param (double) vv its value
     * @param (int) c axis of the third coordinate
     * @param (double) cv its value
     * @return signed distance of that point from the plane (times |n|)
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
     * @param (Plane) p plane
     * @return a normal of it (index space)
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
     * @param (Vec3) p point
     * @param (int) axis 0, 1, 2
     * @return the coordinate
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
     * @param (Graphics2D) g graphics
     * @param (String) s text
     * @param (int) cx centre x
     * @param (int) y baseline
     */
    private static void centred(Graphics2D g, String s, int cx, int y)
    {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    /** A view with its header: coloured bar, name, slice slider and position. */
    static final class Panel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private final OrthoView ortho;
        private final JSlider slider = new JSlider(0, 1, 0);
        private final JLabel position = new JLabel(" ");
        private boolean updating;

        /**
         * @param (int) view view
         * @param (Host) host receives clicks
         */
        Panel(final int view, Host host)
        {
            super(new BorderLayout());
            ortho = new OrthoView(view, host);
            final Host h = host;
            JPanel bar = new JPanel(new BorderLayout(6, 0));
            bar.setBackground(VIEW_COLOURS[view].darker());
            bar.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            JLabel name = new JLabel(VolumeGeometry.name(view) + "  " + VolumeGeometry.hint(view));
            name.setForeground(Color.WHITE);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            position.setForeground(Color.WHITE);
            slider.setOpaque(false);
            slider.setEnabled(false);
            slider.addChangeListener(new ChangeListener()
            {
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
            bar.add(name, BorderLayout.WEST);
            bar.add(slider, BorderLayout.CENTER);
            bar.add(position, BorderLayout.EAST);
            add(bar, BorderLayout.NORTH);
            add(ortho, BorderLayout.CENTER);
        }

        /**
         * @return the view
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
