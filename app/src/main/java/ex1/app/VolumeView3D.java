package ex1.app;

import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The 3-D view: the scanned object as a solid, cut open by the plane so its inside shows on the
 * cut face (like a pie cut in the middle), with the volume's box, the outline of the triangle /
 * trapezoid and its points. Software rendered ({@link CutawayRenderer} into a {@link Raster}):
 * drag to orbit, wheel to move the plane through the object, Ctrl+wheel to zoom, double-click to
 * reset the camera, click to place a point while picking. While something moves the view renders
 * at half resolution and sharpens itself a moment later.
 */
final class VolumeView3D extends JComponent
{
    /** Receives gestures that change the plane or place points (the app owns the plane). */
    interface Host
    {
        /**
         * @param (double) distance voxels to move the plane along its normal
         */
        void slidePlane(double distance);

        /**
         * @param (Vec3) index the clicked point of the object, in voxel indices
         */
        void modelClicked(Vec3 index);
    }

    private static final long serialVersionUID = 1L;
    private static final int BACKGROUND = 0xFF15171B;
    private static final int BOX_COLOUR = 0xFF6F7890;
    private static final int PLANE_COLOUR = 0xFFFF9F1C;
    private static final int PLANE_HIDDEN = 0xFF7A4E12;
    private static final double ORBIT_DEGREES_PER_PIXEL = 0.4;
    private static final double SLIDE_VOXELS_PER_TICK = 2.0;
    private static final int SHARPEN_DELAY_MS = 160;
    private static final int CLICK_SLOP = 3;

    private final transient Camera3D camera = new Camera3D();
    private final transient Host host;
    private final transient CutawayRenderer renderer;
    private transient Raster raster;
    private transient Volume volume;
    private transient IntensityWindow window;
    private transient VolumeGeometry geometry;
    private transient SolidModel model;
    private transient Plane plane;
    private transient List<Vec3> points = new ArrayList<Vec3>();
    private transient List<String> pointNames = new ArrayList<String>();
    private transient String message;
    private final transient Timer sharpen;
    private boolean cutOpen = true;
    private boolean picking;
    private boolean coarse;
    private boolean fitted;
    private int pressX;
    private int pressY;
    private int lastX;
    private int lastY;
    private long lastRenderNanos;

    /**
     * @param (Host) host receives plane gestures and clicks
     * @param (int) threads rendering threads
     */
    VolumeView3D(Host host, int threads)
    {
        this.host = host;
        this.renderer = new CutawayRenderer(threads);
        setOpaque(true);
        setBackground(new Color(BACKGROUND));
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        sharpen = new Timer(SHARPEN_DELAY_MS, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                coarse = false;
                repaint();
            }
        });
        sharpen.setRepeats(false);
        MouseAdapter mouse = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                pressX = e.getX();
                pressY = e.getY();
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                camera.orbit((e.getX() - lastX) * ORBIT_DEGREES_PER_PIXEL, -(e.getY() - lastY) * ORBIT_DEGREES_PER_PIXEL);
                lastX = e.getX();
                lastY = e.getY();
                moving();
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                boolean click = Math.abs(e.getX() - pressX) <= CLICK_SLOP && Math.abs(e.getY() - pressY) <= CLICK_SLOP;
                if (click && e.getClickCount() == 1 && model != null && VolumeView3D.this.host != null)
                {
                    Vec3 p = renderer.pick(e.getX(), e.getY(), getWidth(), getHeight(), camera, geometry, model, plane, cutOpen);
                    if (p != null)
                    {
                        VolumeView3D.this.host.modelClicked(p);
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && !picking)
                {
                    camera.setAngles(Camera3D.DEFAULT_YAW, Camera3D.DEFAULT_PITCH);
                    fitted = false;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (e.isControlDown())
                {
                    double factor = 1.15;
                    if (e.getWheelRotation() > 0)
                    {
                        factor = 1 / 1.15;
                    }
                    camera.zoomBy(factor);
                    moving();
                }
                else if (VolumeView3D.this.host != null)
                {
                    VolumeView3D.this.host.slidePlane(-e.getWheelRotation() * SLIDE_VOXELS_PER_TICK);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    // ------------------------------------------------------------------ data in

    /**
     * @param (Volume) v volume (null clears the view)
     * @param (IntensityWindow) w grey mapping
     * @param (VolumeGeometry) g geometry
     */
    void setVolume(Volume v, IntensityWindow w, VolumeGeometry g)
    {
        volume = v;
        window = w;
        geometry = g;
        model = null;
        fitted = false;
        message = null;
        if (v != null)
        {
            camera.setCentre(new Vec3(0, 0, 0));
            camera.setAngles(Camera3D.DEFAULT_YAW, Camera3D.DEFAULT_PITCH);
            message = "building the 3-D model ...";
        }
        repaint();
    }

    /**
     * @param (SolidModel) m the solid, once built (null while building)
     */
    void setModel(SolidModel m)
    {
        model = m;
        message = null;
        if (m != null && m.solidCount() == 0)
        {
            message = "nothing above the surface threshold - lower it";
        }
        repaint();
    }

    /**
     * @return the solid model shown, or null
     */
    SolidModel model()
    {
        return model;
    }

    /**
     * @param (Plane) p cutting plane (null hides it)
     * @param (List) pts points to mark
     * @param (List) names their labels
     */
    void setPlane(Plane p, List<Vec3> pts, List<String> names)
    {
        plane = p;
        points = new ArrayList<Vec3>(pts);
        pointNames = new ArrayList<String>(names);
        moving();
    }

    /**
     * Turns the camera to look into the cut: from the side of the plane it already faces, a
     * little from the side and from above.
     *
     * @param (Plane) p the plane to look at
     */
    void lookAtPlane(Plane p)
    {
        if (geometry == null || p == null || !p.isRenderable())
        {
            return;
        }
        Vec3 n = geometry.gradientToWorld(OrthoView.normalOf(p));
        if (n.length() < 1e-9)
        {
            return;
        }
        n = n.normalized();
        if (n.dot(camera.towardsViewer()) < 0)
        {
            n = n.scale(-1);
        }
        camera.lookFrom(n, 28, 14);
        moving();
    }

    /**
     * @param (boolean) on cut the object open along the plane
     */
    void setCutOpen(boolean on)
    {
        cutOpen = on;
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
        repaint();
    }

    /**
     * @return milliseconds the last full repaint of the model took
     */
    double lastRenderMillis()
    {
        return lastRenderNanos / 1e6;
    }

    /** Stops the rendering threads. */
    void dispose()
    {
        renderer.shutdown();
    }

    /** Something changed: draw coarse now, sharp in a moment. */
    private void moving()
    {
        coarse = true;
        sharpen.restart();
        repaint();
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0)
    {
        Graphics2D g = (Graphics2D) g0;
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        if (raster == null || raster.width() != w || raster.height() != h)
        {
            raster = new Raster(w, h);
            fitted = false;
        }
        raster.clear(BACKGROUND);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (volume == null)
        {
            g.drawImage(raster.image(), 0, 0, null);
            g.setColor(new Color(150, 156, 168));
            String hint = "3-D model: open a volume (Open NRRD..., or drop a .nrrd file here)";
            g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint)) / 2, h / 2);
            return;
        }
        if (!fitted)
        {
            camera.fit(geometry.worldDiagonal() * 0.85, w, h);
            fitted = true;
        }
        if (model != null)
        {
            long t0 = System.nanoTime();
            int scale = 1;
            if (coarse)
            {
                scale = 2;
            }
            renderer.render(raster, camera, geometry, model, volume, window, plane, cutOpen, scale);
            if (!coarse)
            {
                lastRenderNanos = System.nanoTime() - t0;
            }
        }
        drawBox();
        drawPlaneOutline();
        g.drawImage(raster.image(), 0, 0, null);
        drawPoints(g);
        drawAxes(g, w, h);
        g.setFont(getFont());
        g.setColor(new Color(150, 156, 168));
        String hint = "drag: rotate   wheel: move the plane   ctrl+wheel: zoom   double-click: reset";
        if (picking)
        {
            hint = "click on the object (surface or cut face) to place the point";
            g.setColor(new Color(0xFF9F1C));
        }
        g.drawString(hint, 8, h - 8);
        if (message != null)
        {
            g.setColor(new Color(230, 230, 230));
            g.drawString(message, (w - g.getFontMetrics().stringWidth(message)) / 2, 22);
        }
        else if (model != null && lastRenderNanos > 0)
        {
            g.setColor(new Color(110, 116, 128));
            String info = String.format(java.util.Locale.ROOT, "%.0f ms, %d thread(s), surface at grey %d",
                    lastRenderNanos / 1e6, renderer.threads(), model.threshold());
            g.drawString(info, w - g.getFontMetrics().stringWidth(info) - 8, 16);
        }
    }

    /** The 12 edges of the volume's bounding box, hidden behind the object where they pass. */
    private void drawBox()
    {
        int w = getWidth();
        int h = getHeight();
        double sx = volume.sizeX() - 1;
        double sy = volume.sizeY() - 1;
        double sz = volume.sizeZ() - 1;
        Raster.Vertex[] c = new Raster.Vertex[8];
        for (int i = 0; i < 8; i++)
        {
            double x = 0;
            double y = 0;
            double z = 0;
            if ((i & 1) != 0)
            {
                x = sx;
            }
            if ((i & 2) != 0)
            {
                y = sy;
            }
            if ((i & 4) != 0)
            {
                z = sz;
            }
            c[i] = camera.project(geometry.toWorld(new Vec3(x, y, z)), w, h, 0, 0);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int i = 0; i < edges.length; i++)
        {
            raster.line(c[edges[i][0]], c[edges[i][1]], BOX_COLOUR, 1, true);
        }
    }

    /** The triangle / trapezoid: bright where visible, dim where the object hides it. */
    private void drawPlaneOutline()
    {
        if (plane == null || !plane.isRenderable())
        {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        Raster.Vertex a1 = camera.project(geometry.toWorld(plane.a1()), w, h, 0, 0);
        Raster.Vertex a2 = camera.project(geometry.toWorld(plane.a2()), w, h, 0, 0);
        Raster.Vertex b = camera.project(geometry.toWorld(plane.b()), w, h, 0, 0);
        Raster.Vertex c = camera.project(geometry.toWorld(plane.c()), w, h, 0, 0);
        Raster.Vertex[][] sides = {{a1, b}, {b, c}, {c, a2}, {a2, a1}};
        for (int i = 0; i < sides.length; i++)
        {
            raster.line(sides[i][0], sides[i][1], PLANE_HIDDEN, 1, false);
        }
        for (int i = 0; i < sides.length; i++)
        {
            raster.line(sides[i][0], sides[i][1], PLANE_COLOUR, 2, true);
        }
    }

    /**
     * @param (Graphics2D) g graphics
     */
    private void drawPoints(Graphics2D g)
    {
        int w = getWidth();
        int h = getHeight();
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        for (int i = 0; i < points.size(); i++)
        {
            Raster.Vertex v = camera.project(geometry.toWorld(points.get(i)), w, h, 0, 0);
            g.setColor(new Color(0xFF9F1C));
            g.fillOval((int) v.x - 5, (int) v.y - 5, 10, 10);
            g.setColor(Color.BLACK);
            g.drawOval((int) v.x - 5, (int) v.y - 5, 10, 10);
            g.setColor(new Color(0xFFD08A));
            g.drawString(pointNames.get(i), (int) v.x + 8, (int) v.y - 7);
        }
    }

    /**
     * Orientation arrows in the corner: L / P / S when the file says where they are, else x / y / z.
     *
     * @param (Graphics2D) g graphics
     * @param (int) w width
     * @param (int) h height
     */
    private void drawAxes(Graphics2D g, int w, int h)
    {
        int ox = 46;
        int oy = h - 52;
        double len = 30;
        Vec3[] axes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        String[] names = {"x", "y", "z"};
        if (geometry.known())
        {
            names = new String[] {"L", "P", "S"};
        }
        Color[] colours = {new Color(0xE05555), new Color(0x5FBF5F), new Color(0x5C8DFF)};
        g.setStroke(new BasicStroke(2f));
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        for (int i = 0; i < 3; i++)
        {
            Vec3 d = axes[i];
            if (!geometry.known())
            {
                // Without geometry the world axes are the index axes.
                d = geometry.toWorld(axes[i]).sub(geometry.toWorld(new Vec3(0, 0, 0))).normalized();
            }
            double ex = d.dot(camera.right()) * len;
            double ey = -d.dot(camera.up()) * len;
            g.setColor(colours[i]);
            g.drawLine(ox, oy, (int) (ox + ex), (int) (oy + ey));
            g.drawString(names[i], (int) (ox + ex * 1.25) - 4, (int) (oy + ey * 1.25) + 5);
        }
    }
}
