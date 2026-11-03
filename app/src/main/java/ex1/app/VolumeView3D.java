package ex1.app;

import ex1.core.Image2D;
import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JComponent;

/**
 * The 3-D view: the volume's bounding box, its three mid-slices (the "model", as 3D Slicer shows
 * it) and the cutting plane with the live cross-section mapped onto it. Software rendered with
 * {@link Raster}; drag to orbit, wheel to slice through the model, Ctrl+wheel to zoom,
 * double-click to reset the camera.
 */
final class VolumeView3D extends JComponent
{
    /** Receives gestures that change the plane (the app owns the plane). */
    interface Host
    {
        /**
         * @param (double) distance voxels to move the plane along its normal
         */
        void slidePlane(double distance);
    }

    private static final long serialVersionUID = 1L;
    private static final int BACKGROUND = 0xFF15171B;
    private static final int BOX_COLOUR = 0xFF8A93A6;
    private static final int PLANE_COLOUR = 0xFFFF9F1C;
    private static final int PLANE_TINT = 0xFF9F1C;
    private static final double PLANE_TINT_AMOUNT = 0.28;
    private static final double SLICE_ALPHA = 1.0;
    private static final double ORBIT_DEGREES_PER_PIXEL = 0.4;
    private static final double SLIDE_VOXELS_PER_TICK = 2.0;
    private static final int STRIPS = 110;

    private final transient Camera3D camera = new Camera3D();
    private final transient Host host;
    private transient Raster raster;
    private transient Volume volume;
    private transient Raster.Texture[] sliceTextures;
    private transient Plane[] slicePlanes;
    private transient Plane plane;
    private transient Raster.Texture planeTexture;
    private boolean showSlices = true;
    private boolean showPlane = true;
    private int lastX;
    private int lastY;
    private boolean fitted;

    /**
     * @param (Host) host receives plane gestures
     */
    VolumeView3D(Host host)
    {
        this.host = host;
        setOpaque(true);
        setBackground(new Color(BACKGROUND));
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        MouseAdapter mouse = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                camera.orbit((e.getX() - lastX) * ORBIT_DEGREES_PER_PIXEL, (e.getY() - lastY) * ORBIT_DEGREES_PER_PIXEL);
                lastX = e.getX();
                lastY = e.getY();
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2)
                {
                    camera.setAngles(-35, 25);
                    fitted = false;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(new MouseWheelListener()
        {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (e.isControlDown())
                {
                    camera.zoomBy(e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15);
                    repaint();
                }
                else if (VolumeView3D.this.host != null)
                {
                    VolumeView3D.this.host.slidePlane(-e.getWheelRotation() * SLIDE_VOXELS_PER_TICK);
                }
            }
        });
    }

    // ------------------------------------------------------------------ data in

    /**
     * Sets the model: computes the three mid-slice textures once.
     *
     * @param (Volume) v volume (null clears the view)
     * @param (IntensityWindow) window grey mapping
     */
    void setVolume(Volume v, IntensityWindow window)
    {
        volume = v;
        sliceTextures = null;
        slicePlanes = null;
        fitted = false;
        if (v != null)
        {
            camera.setCentre(new Vec3((v.sizeX() - 1) / 2.0, (v.sizeY() - 1) / 2.0, (v.sizeZ() - 1) / 2.0));
            Projector projector = Projector.create(Projector.Interpolation.NEAREST);
            slicePlanes = new Plane[] {PlaneSpec.xy(v).toPlane(), PlaneSpec.xz(v).toPlane(), PlaneSpec.yz(v).toPlane()};
            sliceTextures = new Raster.Texture[slicePlanes.length];
            for (int i = 0; i < slicePlanes.length; i++)
            {
                Image2D img = projector.project(v, slicePlanes[i]);
                byte[] grey = new byte[img.width() * img.height()];
                window.toGrey(img, grey, img.width(), img.height(), img.width(), img.height());
                sliceTextures[i] = Raster.Texture.fromGrey(grey, img.width(), img.height(), 0, 0.0);
            }
        }
        repaint();
    }

    /**
     * Sets the cutting plane and its already-computed cross-section.
     *
     * @param (Plane) p plane (null hides it)
     * @param (byte[]) grey cross-section, plane.imageWidth() x plane.imageHeight() grey bytes
     */
    void setPlane(Plane p, byte[] grey)
    {
        plane = p;
        planeTexture = null;
        if (p != null && grey != null)
        {
            planeTexture = Raster.Texture.fromGrey(grey, p.imageWidth(), p.imageHeight(), PLANE_TINT, PLANE_TINT_AMOUNT);
        }
        repaint();
    }

    /**
     * @param (boolean) slices show the three mid-slices
     * @param (boolean) cut show the cutting plane
     */
    void setLayers(boolean slices, boolean cut)
    {
        showSlices = slices;
        showPlane = cut;
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
        if (volume == null)
        {
            g.drawImage(raster.image(), 0, 0, null);
            g.setColor(new Color(150, 156, 168));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String hint = "3-D view: open a volume";
            g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint)) / 2, h / 2);
            return;
        }
        if (!fitted)
        {
            double diag = Math.sqrt((double) volume.sizeX() * volume.sizeX() + (double) volume.sizeY() * volume.sizeY()
                    + (double) volume.sizeZ() * volume.sizeZ());
            camera.fit(diag, w, h);
            fitted = true;
        }
        if (showSlices && sliceTextures != null)
        {
            for (int i = 0; i < slicePlanes.length; i++)
            {
                drawRectangle(slicePlanes[i], sliceTextures[i], SLICE_ALPHA);
            }
        }
        if (showPlane && plane != null && planeTexture != null)
        {
            drawCuttingPlane();
        }
        drawBox();
        g.drawImage(raster.image(), 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawLabels(g);
    }

    /**
     * Draws an axis-aligned rectangle plane (a1,a2,b,c) as two textured triangles.
     *
     * @param (Plane) p rectangle
     * @param (Raster.Texture) tex its cross-section
     * @param (double) alpha opacity
     */
    private void drawRectangle(Plane p, Raster.Texture tex, double alpha)
    {
        int w = getWidth();
        int h = getHeight();
        Raster.Vertex a1 = camera.project(p.a1(), w, h, 0, 0);
        Raster.Vertex a2 = camera.project(p.a2(), w, h, tex.width - 0.01, 0);
        Raster.Vertex b = camera.project(p.b(), w, h, 0, tex.height - 0.01);
        Raster.Vertex c = camera.project(p.c(), w, h, tex.width - 0.01, tex.height - 0.01);
        raster.triangle(a1, a2, c, tex, alpha);
        raster.triangle(a1, c, b, tex, alpha);
    }

    /**
     * Draws the cutting plane as thin horizontal strips, each mapped to its own rows of the
     * cross-section image (exactly the rows the projector computed), then the outline and rails.
     */
    private void drawCuttingPlane()
    {
        int w = getWidth();
        int h = getHeight();
        int imgW = plane.imageWidth();
        int imgH = plane.imageHeight();
        Vec3 a1 = plane.a1();
        Vec3 a2 = plane.a2();
        Vec3 v1 = plane.b().sub(a1).normalized();
        Vec3 v2 = plane.c().sub(a2).normalized();
        double step = Math.max(1.0, (imgH - 1) / (double) STRIPS);
        double y0 = 0;
        while (y0 < imgH - 1)
        {
            double y1 = Math.min(imgH - 1, y0 + step);
            Vec3 l0 = a1.add(v1.scale(y0));
            Vec3 r0 = a2.add(v2.scale(y0));
            Vec3 l1 = a1.add(v1.scale(y1));
            Vec3 r1 = a2.add(v2.scale(y1));
            int n0 = Math.min(imgW, (int) Math.floor(r0.dist(l0)) + 1);
            int n1 = Math.min(imgW, (int) Math.floor(r1.dist(l1)) + 1);
            double x00 = (imgW - n0) / 2;
            double x10 = (imgW - n1) / 2;
            Raster.Vertex vl0 = camera.project(l0, w, h, x00, y0);
            Raster.Vertex vr0 = camera.project(r0, w, h, x00 + n0 - 0.01, y0);
            Raster.Vertex vl1 = camera.project(l1, w, h, x10, y1);
            Raster.Vertex vr1 = camera.project(r1, w, h, x10 + n1 - 0.01, y1);
            raster.triangle(vl0, vr0, vr1, planeTexture, 1.0);
            raster.triangle(vl0, vr1, vl1, planeTexture, 1.0);
            y0 = y1;
        }
        Raster.Vertex pa1 = camera.project(a1, w, h, 0, 0);
        Raster.Vertex pa2 = camera.project(a2, w, h, 0, 0);
        Raster.Vertex pb = camera.project(plane.b(), w, h, 0, 0);
        Raster.Vertex pc = camera.project(plane.c(), w, h, 0, 0);
        raster.line(pa1, pb, PLANE_COLOUR, 2, true);
        raster.line(pa2, pc, PLANE_COLOUR, 2, true);
        raster.line(pb, pc, PLANE_COLOUR, 2, true);
        if (!plane.isTriangle())
        {
            raster.line(pa1, pa2, PLANE_COLOUR, 2, true);
        }
    }

    /** Draws the 12 edges of the volume's bounding box. */
    private void drawBox()
    {
        int w = getWidth();
        int h = getHeight();
        double sx = volume.sizeX() - 1;
        double sy = volume.sizeY() - 1;
        double sz = volume.sizeZ() - 1;
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++)
        {
            corners[i] = new Vec3((i & 1) == 0 ? 0 : sx, (i & 2) == 0 ? 0 : sy, (i & 4) == 0 ? 0 : sz);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int i = 0; i < edges.length; i++)
        {
            Raster.Vertex a = camera.project(corners[edges[i][0]], w, h, 0, 0);
            Raster.Vertex b = camera.project(corners[edges[i][1]], w, h, 0, 0);
            raster.line(a, b, BOX_COLOUR, 1, true);
        }
        // Axis arrows at the origin corner.
        double len = 0.25 * Math.min(sx, Math.min(sy, sz));
        Vec3 o = new Vec3(0, 0, 0);
        raster.line(camera.project(o, w, h, 0, 0), camera.project(new Vec3(len, 0, 0), w, h, 0, 0), 0xFFE05555, 2, false);
        raster.line(camera.project(o, w, h, 0, 0), camera.project(new Vec3(0, len, 0), w, h, 0, 0), 0xFF5FBF5F, 2, false);
        raster.line(camera.project(o, w, h, 0, 0), camera.project(new Vec3(0, 0, len), w, h, 0, 0), 0xFF5C8DFF, 2, false);
    }

    /**
     * @param (Graphics2D) g graphics for text
     */
    private void drawLabels(Graphics2D g)
    {
        int w = getWidth();
        int h = getHeight();
        double len = 0.25 * Math.min(volume.sizeX() - 1, Math.min(volume.sizeY() - 1, volume.sizeZ() - 1)) + 6;
        label(g, camera.project(new Vec3(len, 0, 0), w, h, 0, 0), "X", new Color(0xE05555));
        label(g, camera.project(new Vec3(0, len, 0), w, h, 0, 0), "Y", new Color(0x5FBF5F));
        label(g, camera.project(new Vec3(0, 0, len), w, h, 0, 0), "Z", new Color(0x5C8DFF));
        if (plane != null && showPlane)
        {
            Color orange = new Color(PLANE_COLOUR);
            if (plane.isTriangle())
            {
                label(g, camera.project(plane.a1(), w, h, 0, 0), "a", orange);
            }
            else
            {
                label(g, camera.project(plane.a1(), w, h, 0, 0), "a1", orange);
                label(g, camera.project(plane.a2(), w, h, 0, 0), "a2", orange);
            }
            label(g, camera.project(plane.b(), w, h, 0, 0), "b", orange);
            label(g, camera.project(plane.c(), w, h, 0, 0), "c", orange);
        }
        g.setColor(new Color(150, 156, 168));
        g.drawString("drag: rotate   wheel: slice   ctrl+wheel: zoom   double-click: reset", 8, h - 8);
    }

    /**
     * @param (Graphics2D) g graphics
     * @param (Raster.Vertex) at screen position
     * @param (String) text label
     * @param (Color) colour colour
     */
    private static void label(Graphics2D g, Raster.Vertex at, String text, Color colour)
    {
        g.setColor(colour);
        g.drawString(text, (int) at.x + 4, (int) at.y - 4);
    }
}
