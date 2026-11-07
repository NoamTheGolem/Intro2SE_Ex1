package ex1.app;

import ex1.app.ui.Theme;
import ex1.core.IntensityWindow;
import ex1.core.Plane;
import ex1.core.Vec3;
import ex1.core.Volume;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The 3-D view: the scanned object as a solid, cut open by the plane so its inside shows on the
 * cut face (like a pie cut in the middle), with the volume's box, the outline of the triangle /
 * trapezoid and its points. Software rendered ({@link CutawayRenderer} into a {@link Raster}):
 * drag to orbit, wheel to move the plane through the object, Ctrl+wheel to zoom, double-click to
 * reset the camera, click to place a point while picking.
 *
 * Frames are drawn on a thread of their own, never on the Swing thread (ADR-0014): a change asks
 * for a frame, the newest request wins, and the window only copies the last finished frame to the
 * screen - so the video, the buttons and the mouse never wait for the 3-D picture. While something
 * moves the frame is drawn at a coarser resolution, and sharp a moment after it stops.
 */
final class VolumeView3D extends JComponent
{
    /** Receives gestures that change the plane or place points (the app owns the plane). */
    interface Host
    {
        /**
         * Called when the wheel moves the plane.
         * @param (double) distance Voxels to move the plane along its normal.
         */
        void slidePlane(double distance);

        /**
         * Called when a point of the object is clicked.
         * @param (Vec3) index The clicked point of the object, in voxel indices.
         */
        void modelClicked(Vec3 index);
    }

    /** Version of the serialized form (Swing components are Serializable). */
    private static final long serialVersionUID = 1L;

    /** Colours: background, box, and the plane's outline, in front of and behind the object. */
    private static final int BACKGROUND   = 0xFF000000 | Theme.WELL.getRGB();
    private static final int BOX_COLOUR   = 0xFF5F6878;
    private static final int PLANE_COLOUR = 0xFF000000 | Theme.PLANE.getRGB();
    private static final int PLANE_HIDDEN = 0xFF7A4E12;

    /**
     * The mouse: degrees per pixel dragged, voxels and zoom per wheel notch, and a click's slack.
     */
    private static final double ORBIT_DEGREES_PER_PIXEL = 0.4;
    private static final double SLIDE_VOXELS_PER_TICK   = 2.0;
    private static final double ZOOM_PER_TICK           = 1.15;
    private static final int    CLICK_SLOP              = 3;   // pixels a click may move and still be a click

    /**
     * The view fits this share of the volume's diagonal: the object fills it, not the box's
     * corners.
     */
    private static final double FIT_DIAGONAL = 0.85;

    /** lookAtPlane looks into the cut from this far to the side and from above, in degrees. */
    private static final double LOOK_TURN  = 28;
    private static final double LOOK_RAISE = 14;

    /** How long nothing must move before the sharp frame is drawn. */
    private static final int SHARPEN_DELAY_MS = 160;

    /** Above this many pixels a moving frame takes one ray per 3 x 3 block instead of 2 x 2. */
    private static final long COARSE_LIMIT = 900000L;

    /** Above this many pixels even a still frame takes one ray per 2 x 2 block (a huge window). */
    private static final long SHARP_LIMIT = 2200000L;

    /** The camera, who hears the gestures, and the threads that draw. */
    private final transient Camera3D        camera = new Camera3D();
    private final transient Host            host;
    private final transient CutawayRenderer renderer;
    private final transient ExecutorService frames;

    /** The state on show (set on the Swing thread; every frame takes a copy). */
    private transient Volume          volume;
    private transient IntensityWindow window;
    private transient VolumeGeometry  geometry;
    private transient SolidModel      model;
    private transient Plane           plane;
    private transient List<Vec3>      points     = new ArrayList<Vec3>();
    private transient List<String>    pointNames = new ArrayList<String>();
    private transient String          message;

    /** Asks for the sharp frame once nothing has moved for a moment. */
    private final transient Timer sharpen;

    /** Modes: cut open, picking points, coarse frames while something moves. */
    private boolean cutOpen = true;
    private boolean picking;
    private boolean coarse;

    /** The camera is fitted to the volume again when the view changes size. */
    private boolean fitted;
    private int     fittedWidth;
    private int     fittedHeight;

    /** Where the mouse was pressed and where it was last, to tell a click from a drag. */
    private int pressX;
    private int pressY;
    private int lastX;
    private int lastY;

    /**
     * Shared with the frame thread, under the lock: the newest request, whether the thread is busy,
     * the frame on show and its job, and the buffer for the next frame.
     */
    private final transient Object lock = new Object();
    private transient Job          pending;
    private boolean                working;
    private transient Raster       shownRaster;
    private transient Job          shownJob;
    private transient Raster       spare;

    /** Frame thread only: the job that failed and is being drawn once more (never a third time). */
    private transient Job retried;

    /** Everything one frame needs, copied on the Swing thread when the frame is asked for. */
    private static final class Job
    {
        /** The camera, the frame's size and pixel blocks, and the scene when it was asked for. */
        Camera3D        camera;
        int             width;
        int             height;
        int             scale;
        Volume          volume;
        IntensityWindow window;
        VolumeGeometry  geometry;
        SolidModel      model;
        Plane           plane;
        boolean         cutOpen;
        List<Vec3>      points;
        List<String>    names;
    }

    /**
     * Creates the view, its frame thread, the sharpen timer and the mouse handling.
     * @param (Host) host Receives plane gestures and clicks.
     * @param (int) threads Threads that trace the rays of one frame.
     */
    VolumeView3D(Host host, int threads)
    {
        this.host = host;
        this.renderer = new CutawayRenderer(threads);
        this.frames = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            /**
             * Makes the frame thread: a daemon, so it never keeps the program alive.
             * @param (Runnable) r The thread's work.
             * @return (Thread) The thread, not started yet.
             */
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "ex1-3d-frames");
                t.setDaemon(true);
                return t;
            }
        });
        setOpaque(true);
        setBackground(Theme.WELL);
        setFont(Theme.font(Theme.SMALL_SIZE));
        setToolTipText(null);
        sharpen = new Timer(SHARPEN_DELAY_MS, new ActionListener()
        {
            /**
             * Asks for the sharp frame once nothing has moved for a moment.
             * @param (ActionEvent) e The timer's tick.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                coarse = false;
                requestFrame();
            }
        });
        sharpen.setRepeats(false);
        addComponentListener(new ComponentAdapter()
        {
            /**
             * Draws coarse frames while the window is resized and a sharp one after.
             * @param (ComponentEvent) e The resize.
             */
            @Override
            public void componentResized(ComponentEvent e)
            {
                // Resizing is a movement too: coarse frames while the window is dragged.
                moving();
            }
        });
        MouseAdapter mouse = new MouseAdapter()
        {
            /**
             * Remembers where the press began, to tell a click from a drag.
             * @param (MouseEvent) e The press.
             */
            @Override
            public void mousePressed(MouseEvent e)
            {
                pressX = e.getX();
                pressY = e.getY();
                lastX = e.getX();
                lastY = e.getY();
            }

            /**
             * Turns the camera with the mouse.
             * @param (MouseEvent) e The drag.
             */
            @Override
            public void mouseDragged(MouseEvent e)
            {
                camera.orbit((e.getX() - lastX) * ORBIT_DEGREES_PER_PIXEL, -(e.getY() - lastY) * ORBIT_DEGREES_PER_PIXEL);
                lastX = e.getX();
                lastY = e.getY();
                moving();
            }

            /**
             * Passes a click (a press and release that hardly moved) on the object to the app, as
             * the point the ray from that pixel hits first.
             * @param (MouseEvent) e The release.
             */
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

            /**
             * Turns the camera back to its start on a double-click (not while picking points).
             * @param (MouseEvent) e The click.
             */
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && !picking)
                {
                    camera.setAngles(Camera3D.DEFAULT_YAW, Camera3D.DEFAULT_PITCH);
                    fitted = false;
                    moving();
                }
            }

            /**
             * Moves the plane along its normal, or zooms with Ctrl.
             * @param (MouseWheelEvent) e The wheel turn.
             */
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                if (e.isControlDown())
                {
                    double factor = ZOOM_PER_TICK;
                    if (e.getWheelRotation() > 0)
                    {
                        factor = 1 / ZOOM_PER_TICK;
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

    // ==========================================
    //                  DATA IN
    // ==========================================

    /**
     * Shows a new scan; the camera goes back to its start and the model follows once it is built.
     * @param (Volume) v Volume (null clears the view).
     * @param (IntensityWindow) w Grey mapping.
     * @param (VolumeGeometry) g Geometry.
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
            message = "Building the 3-D model ...";
        }
        synchronized (lock)
        {
            // A frame of the previous scan must not show over the new one.
            shownRaster = null;
            shownJob = null;
        }
        requestFrame();
    }

    /**
     * Shows the solid model once it is built.
     * @param (SolidModel) m The solid, once built (null while building).
     */
    void setModel(SolidModel m)
    {
        model = m;
        message = null;
        if (m != null && m.solidCount() == 0)
        {
            message = "Nothing above the surface threshold";
        }
        else if (m == null && volume != null)
        {
            message = "Building the 3-D model ...";
        }
        requestFrame();
    }

    /**
     * Gives the model on show.
     * @return (SolidModel) The solid model shown, or null.
     */
    SolidModel model()
    {
        return model;
    }

    /**
     * Cuts the object with a new plane and marks its points.
     * @param (Plane) p Cutting plane (null hides it).
     * @param (List<Vec3>) pts Points to mark.
     * @param (List<String>) names Their labels.
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
     * @param (Plane) p The plane to look at.
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
        camera.lookFrom(n, LOOK_TURN, LOOK_RAISE);
        moving();
    }

    /**
     * Switches cutting the object open on or off (the app keeps it on).
     * @param (boolean) on Cut the object open along the plane.
     */
    void setCutOpen(boolean on)
    {
        cutOpen = on;
        requestFrame();
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
        repaint();
    }

    /** Stops the frame thread and the ray tracing threads; the view draws nothing new after it. */
    void dispose()
    {
        sharpen.stop();
        frames.shutdownNow();
        renderer.shutdown();
    }

    /** Asks for a coarse frame now and a sharp one when the movement stops. */
    private void moving()
    {
        coarse = true;
        sharpen.restart();
        requestFrame();
    }

    // ==========================================
    //                   FRAMES
    // ==========================================

    /**
     * Asks the frame thread for a frame of the current state; a request still waiting is replaced,
     * so the thread always draws the newest state and never falls behind. Swing thread only.
     */
    private void requestFrame()
    {
        if (volume == null || frames.isShutdown())
        {
            repaint();
            return;
        }
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        if (!fitted || w != fittedWidth || h != fittedHeight)
        {
            camera.fit(geometry.worldDiagonal() * FIT_DIAGONAL, w, h);
            fitted = true;
            fittedWidth = w;
            fittedHeight = h;
        }
        Job job = new Job();
        job.camera = camera.copy();
        job.width = w;
        job.height = h;
        job.scale = scaleFor(w, h, coarse);
        job.volume = volume;
        job.window = window;
        job.geometry = geometry;
        job.model = model;
        job.plane = plane;
        job.cutOpen = cutOpen;
        job.points = new ArrayList<Vec3>(points);
        job.names = new ArrayList<String>(pointNames);
        synchronized (lock)
        {
            pending = job;
            if (working)
            {
                return;
            }
            working = true;
        }
        frames.execute(new Runnable()
        {
            /** Draws the waiting frames, on the frame thread. */
            @Override
            public void run()
            {
                drawPending();
            }
        });
    }

    /**
     * Chooses how many pixels one ray covers: more while something moves, and more for very large
     * views.
     * @param (int) w Width.
     * @param (int) h Height.
     * @param (boolean) moving True while something moves.
     * @return (int) Pixels per ray along each side.
     */
    private static int scaleFor(int w, int h, boolean moving)
    {
        long pixels = (long) w * h;
        if (moving)
        {
            if (pixels > COARSE_LIMIT)
            {
                return 3;
            }
            return 2;
        }
        if (pixels > SHARP_LIMIT)
        {
            return 2;
        }
        return 1;
    }

    /**
     * Draws the requests on the frame thread until none is waiting; each frame replaces the one
     * shown.
     */
    private void drawPending()
    {
        while (true)
        {
            Job    job;
            Raster target;
            synchronized (lock)
            {
                job = pending;
                pending = null;
                if (job == null)
                {
                    working = false;
                    return;
                }
                target = spare;
                spare = null;
            }
            if (target == null || target.width() != job.width || target.height() != job.height)
            {
                target = new Raster(job.width, job.height);
            }
            try
            {
                draw(target, job);
            }
            catch (RuntimeException e)
            {
                // A failed frame would leave the old one on show until the next change (a click,
                // a turn): draw it once more, unless a newer request is waiting anyway.
                System.err.println("3-D view: a frame failed: " + e);
                if (job != retried)
                {
                    synchronized (lock)
                    {
                        if (pending == null)
                        {
                            pending = job;
                            retried = job;
                        }
                    }
                }
                continue;
            }
            synchronized (lock)
            {
                // The frame replaced becomes the next one's buffer. The Swing thread copies the
                // shown frame while holding the lock, so no buffer is drawn into while it is copied.
                spare = shownRaster;
                shownRaster = target;
                shownJob = job;
            }
            repaint();
        }
    }

    /**
     * Draws one frame, on the frame thread: the object, then the box and the plane's outline
     * (hidden behind the object where they pass).
     * @param (Raster) r Buffer of the job's size.
     * @param (Job) job What to draw.
     */
    private void draw(Raster r, Job job)
    {
        r.clear(BACKGROUND);
        if (job.model != null)
        {
            renderer.render(r, job.camera, job.geometry, job.model, job.volume, job.window, job.plane, job.cutOpen, job.scale);
        }
        drawBox(r, job);
        drawPlaneOutline(r, job);
    }

    // ==========================================
    //                  PAINTING
    // ==========================================

    /**
     * Shows the newest finished frame, the plane's points and the axes on it, and the hint line.
     * @param (Graphics) g0 Graphics of the view.
     */
    @Override
    protected void paintComponent(Graphics g0)
    {
        Graphics2D g = (Graphics2D) g0;
        int        w = getWidth();
        int        h = getHeight();
        g.setColor(Theme.WELL);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont());
        if (volume == null)
        {
            g.setColor(Theme.TEXT_FAINT);
            String hint = "Open a scan with Open NRRD..., or drop a .nrrd file here";
            g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint)) / 2, h / 2);
            return;
        }
        Job job = null;
        synchronized (lock)
        {
            if (shownRaster != null)
            {
                g.drawImage(shownRaster.image(), 0, 0, null);
                job = shownJob;
            }
        }
        if (job != null)
        {
            drawPoints(g, job);
            drawAxes(g, job, h);
        }
        g.setFont(getFont());
        g.setColor(Theme.TEXT_FAINT);
        String hint = "Drag to turn it, wheel to move the plane, Ctrl+wheel to zoom";
        if (picking)
        {
            hint = "Click on the object (its surface or the cut face) to place the point";
            g.setColor(Theme.PLANE);
        }
        g.drawString(hint, 10, h - 10);
        if (message != null)
        {
            g.setColor(Theme.TEXT_DIM);
            g.drawString(message, (w - g.getFontMetrics().stringWidth(message)) / 2, 24);
        }
    }

    /**
     * Draws the 12 edges of the volume's bounding box, hidden behind the object where they pass.
     * @param (Raster) r Buffer.
     * @param (Job) job The frame.
     */
    private static void drawBox(Raster r, Job job)
    {
        double          sx = job.volume.sizeX() - 1;
        double          sy = job.volume.sizeY() - 1;
        double          sz = job.volume.sizeZ() - 1;
        Raster.Vertex[] c  = new Raster.Vertex[8];
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
            c[i] = job.camera.project(job.geometry.toWorld(new Vec3(x, y, z)), job.width, job.height, 0, 0);
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int i = 0; i < edges.length; i++)
        {
            r.line(c[edges[i][0]], c[edges[i][1]], BOX_COLOUR, 1, true);
        }
    }

    /**
     * Draws the triangle / trapezoid: bright where visible, dim where the object hides it.
     * @param (Raster) r Buffer.
     * @param (Job) job The frame.
     */
    private static void drawPlaneOutline(Raster r, Job job)
    {
        Plane p = job.plane;
        if (p == null || !p.isRenderable())
        {
            return;
        }
        Raster.Vertex     a1    = job.camera.project(job.geometry.toWorld(p.a1()), job.width, job.height, 0, 0);
        Raster.Vertex     a2    = job.camera.project(job.geometry.toWorld(p.a2()), job.width, job.height, 0, 0);
        Raster.Vertex     b     = job.camera.project(job.geometry.toWorld(p.b()), job.width, job.height, 0, 0);
        Raster.Vertex     c     = job.camera.project(job.geometry.toWorld(p.c()), job.width, job.height, 0, 0);
        Raster.Vertex[][] sides = {{a1, b}, {b, c}, {c, a2}, {a2, a1}};
        for (int i = 0; i < sides.length; i++)
        {
            r.line(sides[i][0], sides[i][1], PLANE_HIDDEN, 1, false);
        }
        for (int i = 0; i < sides.length; i++)
        {
            r.line(sides[i][0], sides[i][1], PLANE_COLOUR, 2, true);
        }
    }

    /**
     * Draws the plane's points with their names, where the shown frame's camera puts them.
     * @param (Graphics2D) g Graphics.
     * @param (Job) job The shown frame.
     */
    private void drawPoints(Graphics2D g, Job job)
    {
        g.setFont(Theme.semibold(Theme.TEXT_SIZE));
        for (int i = 0; i < job.points.size(); i++)
        {
            Raster.Vertex v = job.camera.project(job.geometry.toWorld(job.points.get(i)), job.width, job.height, 0, 0);
            g.setColor(Theme.PLANE);
            g.fillOval((int) v.x - 5, (int) v.y - 5, 10, 10);
            g.setColor(Theme.WELL);
            g.drawOval((int) v.x - 5, (int) v.y - 5, 10, 10);
            g.setColor(Theme.PLANE_HOVER);
            g.drawString(job.names.get(i), (int) v.x + 8, (int) v.y - 7);
        }
    }

    /**
     * Draws orientation arrows in the corner: L / P / S when the file says where they are, else x /
     * y / z.
     * @param (Graphics2D) g Graphics.
     * @param (Job) job The shown frame (its camera).
     * @param (int) h Height.
     */
    private static void drawAxes(Graphics2D g, Job job, int h)
    {
        int      ox    = 46;
        int      oy    = h - 56;
        double   len   = 30;
        Vec3[]   axes  = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        String[] names = {"x", "y", "z"};
        if (job.geometry.known())
        {
            names = new String[] {"L", "P", "S"};
        }
        Color[] colours = {new Color(0xE05555), new Color(0x5FBF5F), new Color(0x5C8DFF)};
        g.setStroke(new BasicStroke(2f));
        g.setFont(Theme.semibold(Theme.SMALL_SIZE));
        for (int i = 0; i < 3; i++)
        {
            Vec3 d = axes[i];
            if (!job.geometry.known())
            {
                // Without geometry the world axes are the index axes.
                d = job.geometry.toWorld(axes[i]).sub(job.geometry.toWorld(new Vec3(0, 0, 0))).normalized();
            }
            double ex = d.dot(job.camera.right()) * len;
            double ey = -d.dot(job.camera.up()) * len;
            g.setColor(colours[i]);
            g.drawLine(ox, oy, (int) (ox + ex), (int) (oy + ey));
            g.drawString(names[i], (int) (ox + ex * 1.25) - 4, (int) (oy + ey * 1.25) + 5);
        }
    }

    /**
     * Waits until no frame is being drawn or waiting (for tests).
     * @param (long) timeoutMillis How long to wait at most.
     * @return (boolean) True when idle in time.
     * @throws (InterruptedException) When interrupted.
     */
    boolean awaitIdle(long timeoutMillis) throws InterruptedException
    {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end)
        {
            synchronized (lock)
            {
                if (!working && pending == null)
                {
                    return true;
                }
            }
            Thread.sleep(5);
        }
        return false;
    }

    /**
     * Tells whether a frame is on show (for tests).
     * @return (boolean) Whether a frame has been drawn and is shown.
     */
    boolean hasFrame()
    {
        synchronized (lock)
        {
            return shownRaster != null;
        }
    }
}
