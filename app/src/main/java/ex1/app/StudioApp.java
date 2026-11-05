package ex1.app;

import ex1.core.Image2D;
import ex1.core.IntensityWindow;
import ex1.core.ParallelProjector;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.io.NrrdReader;
import ex1.io.PlaneCsvWriter;
import ex1.io.VideoWriter;
import ex1.io.VideoWriters;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

/**
 * The application (the deliverable of Ex1): open a scan (NRRD) from anywhere on the computer or by
 * dropping it on the window, see it as a solid object, choose the plane by its 3 points (or 4 for
 * a trapezoid) - clicked on the views or typed - and see the object cut open along it (always: the
 * inside shows on the cut face) next to the plane's projection, the image of the assignment. One picture panel flips between that cut and the three standard views
 * of 3D Slicer (axial, coronal, sagittal), on which the points are clicked.
 *
 * The video is the chosen plane moved through the whole scan: Play shows it back and forth,
 * Export video saves one pass of it as an MP4. Lists of planes in CSV files (the lecturer's use
 * cases) are the command line's job: python Ex0.py scan.nrrd planes.csv out.mp4 (ADR-0012).
 * Sampling is nearest-neighbour, the course's M[p] (ADR-0011).
 */
public final class StudioApp implements VolumeView3D.Host, OrthoView.Host
{
    private static final String TITLE = "Ex1 - cut a 3-D scan with a plane";
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    /** Play and export use one rate, the Ex0 default, so Play shows the video at its real speed. */
    static final int VIDEO_FPS = 10;
    private static final double NANOS_PER_MS = 1e6;
    private static final String[] TRIANGLE_NAMES = {"a", "b", "c"};
    private static final String[] TRAPEZOID_NAMES = {"a1", "a2", "b", "c"};
    private static final int CONTROL_WIDTH = 380;
    /** Wrap width of HTML labels in CSS px: Swing draws one CSS px as 1.3 screen pixels. */
    private static final int HTML_WIDTH = (CONTROL_WIDTH - 80) * 10 / 13;
    private static final int INFO_HEIGHT = 44;
    private static final String HINT_COLOUR = "#707070";
    /** The FPS shown is the median of this many recent projections: one slow frame (GC) is ignored. */
    private static final int FPS_SAMPLES = 15;
    /** The FPS label changes at most this often, so it can be read while playing. */
    private static final int FPS_LABEL_MS = 400;
    /** Projections run once before timing starts, so the first number is not the JIT warming up. */
    private static final int WARM_UP_PROJECTIONS = 5;
    /** Pictures of the picture panel: the cut, then the slice views 0..2 as pictures 1..3. */
    private static final int PICTURE_CUT = 0;
    private static final int PICTURES = 4;
    private static final Color CUT_BAR = new Color(0x2A2E36);
    private static final int DEFAULT_WIDTH = 1500;
    private static final int DEFAULT_HEIGHT = 900;
    private static final int MAX_NAME_TRIES = 999;
    private static final int MAX_NOTE_CHARS = 90;

    // ---- model
    private File volumeFile;
    private Volume volume;
    private IntensityWindow window;
    private VolumeGeometry geometry;
    /** Nearest neighbour on the Swing thread: one frame takes about a millisecond. */
    private final Projector projector = Projector.create(Projector.Interpolation.NEAREST);
    private PlaneSpec spec;
    private Plane plane;
    private List<Plane> playSequence;
    private int playIndex;
    private int playStep = 1;
    private boolean busy;
    private int modelGeneration;
    private final double[] recentMs = new double[FPS_SAMPLES];
    private int recentCount;
    private long fpsShownAt;

    // ---- picking
    private String[] pickNames;
    private Vec3[] picked;
    private int pickIndex = -1;
    private int pickOnly = -1;
    /** Picking switched the picture panel from the cut to a slice view; it switches back after. */
    private boolean pickMovedPicture;

    // ---- views
    private final JFrame frame = new JFrame(TITLE);
    private final VolumeView3D view3d = new VolumeView3D(this, CORES);
    private final SliceView cutView = new SliceView();
    private final OrthoView.Panel[] orthos = new OrthoView.Panel[3];
    private final CardLayout pictures = new CardLayout();
    private final JPanel pictureCards = new JPanel(pictures);
    private final JPanel pictureBar = new JPanel(new BorderLayout(6, 0));
    private final JLabel pictureTitle = new JLabel(" ", JLabel.CENTER);
    private final JLabel pictureCount = new JLabel(" ");
    private int picture = PICTURE_CUT;
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel fpsLabel = new JLabel(" ");

    // ---- points editor
    private final JRadioButton threePoints = new JRadioButton("3 points (triangle)", true);
    private final JRadioButton fourPoints = new JRadioButton("4 points (trapezoid)");
    private final JLabel[] rowNames = new JLabel[4];
    private final JTextField[][] fields = new JTextField[4][3];
    private final JButton[] pickButtons = new JButton[4];
    private final JLabel pointsInfo = new JLabel(" ");
    private final JButton pickAll = new JButton("Pick on the views");
    private final JButton fixButton = new JButton("Make isosceles");

    // ---- the video
    private final JLabel videoInfo = new JLabel(" ");
    private final JButton playButton = new JButton("Play");
    private final JButton exportButton = new JButton("Export video...");
    private final Timer playTimer;

    // ===================================================================== ENTRY

    /**
     * Opens the window on the Swing thread.
     *
     * @param (File) initialVolume NRRD to open at start, or null
     */
    public static void launch(final File initialVolume)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
                catch (Exception e)
                {
                    // The default look and feel is fine.
                }
                StudioApp app = new StudioApp();
                app.frame.setVisible(true);
                if (initialVolume != null)
                {
                    app.openVolume(initialVolume);
                }
            }
        });
    }

    /** Builds the window; call on the Swing thread. */
    private StudioApp()
    {
        playTimer = new Timer(1000 / VIDEO_FPS, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                stepPlayback();
            }
        });
        buildWindow();
    }

    // ============================================================ BUILD PIPELINE

    /** Lays out the toolbar, the 3-D view, the picture panel and the control column. */
    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(960, 600));
        // Never larger than the screen (a laptop at 150 % scaling has room for about 1280 x 680).
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(new Dimension(Math.min(DEFAULT_WIDTH, screen.width), Math.min(DEFAULT_HEIGHT, screen.height)));
        frame.setLocationRelativeTo(null);

        for (int v = 0; v < 3; v++)
        {
            orthos[v] = new OrthoView.Panel(v, this, false);
        }
        JSplitPane views = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, titled(view3d, "3-D model - cut open by the plane"),
                buildPicturePanel());
        views.setResizeWeight(0.5);
        views.setBorder(null);

        JPanel controls = new ColumnPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        controls.add(buildPointsPanel());
        controls.add(buildVideoPanel());
        JScrollPane controlScroll = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.setBorder(null);
        controlScroll.getVerticalScrollBar().setUnitIncrement(16);
        controlScroll.setPreferredSize(new Dimension(CONTROL_WIDTH, 600));

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        statusBar.add(status, BorderLayout.CENTER);
        progress.setVisible(false);
        progress.setStringPainted(true);
        statusBar.add(progress, BorderLayout.EAST);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(buildToolbar(), BorderLayout.NORTH);
        frame.getContentPane().add(views, BorderLayout.CENTER);
        frame.getContentPane().add(controlScroll, BorderLayout.EAST);
        frame.getContentPane().add(statusBar, BorderLayout.SOUTH);

        FileDrop drop = new FileDrop();
        frame.getRootPane().setTransferHandler(drop);
        view3d.setTransferHandler(drop);
        cutView.setTransferHandler(drop);
        for (int v = 0; v < 3; v++)
        {
            orthos[v].view().setTransferHandler(drop);
        }
        updateEnabled();
        status.setText("Open a scan: \"Open NRRD...\" (anywhere on the computer), or drop a .nrrd file on the window.");
    }

    /**
     * @return the toolbar
     */
    private JPanel buildToolbar()
    {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        top.add(button("Open NRRD...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                chooseVolume();
            }
        }));
        JLabel orDrop = new JLabel("or drop a .nrrd file anywhere on the window");
        orDrop.setForeground(new Color(0x707070));
        top.add(orDrop);

        fpsLabel.setFont(fpsLabel.getFont().deriveFont(Font.BOLD, fpsLabel.getFont().getSize2D() + 1f));
        fpsLabel.setToolTipText("<html>How many cuts (video frames) per second the projection computes for the current plane:<br>"
                + "nearest-neighbour sampling, one thread, drawing the views not counted.<br>"
                + "The video itself plays and is saved at " + VIDEO_FPS + " frames per second.</html>");
        fpsLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        JPanel bar = new JPanel(new BorderLayout());
        bar.add(top, BorderLayout.CENTER);
        bar.add(fpsLabel, BorderLayout.EAST);
        return bar;
    }

    /**
     * The picture panel: one picture at a time - the cut (the triangle), then Axial, Coronal and
     * Sagittal - and arrows to flip through them; after the last one it comes back to the cut.
     *
     * @return the panel
     */
    private JPanel buildPicturePanel()
    {
        pictureCards.add(cutView, Integer.toString(PICTURE_CUT));
        for (int v = 0; v < 3; v++)
        {
            pictureCards.add(orthos[v], Integer.toString(v + 1));
        }
        JButton previous = arrowButton(false, "Previous picture");
        previous.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                flipPicture(-1);
            }
        });
        JButton next = arrowButton(true, "Next picture");
        next.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                flipPicture(1);
            }
        });
        pictureTitle.setForeground(Color.WHITE);
        pictureTitle.setFont(pictureTitle.getFont().deriveFont(Font.BOLD));
        pictureCount.setForeground(new Color(0xE6E6E6));
        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(pictureTitle, BorderLayout.CENTER);
        middle.add(pictureCount, BorderLayout.EAST);
        pictureBar.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        pictureBar.add(previous, BorderLayout.WEST);
        pictureBar.add(middle, BorderLayout.CENTER);
        pictureBar.add(next, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout());
        p.add(pictureBar, BorderLayout.NORTH);
        p.add(pictureCards, BorderLayout.CENTER);
        showPicture(PICTURE_CUT);
        return p;
    }

    /**
     * @return the "plane by points" editor
     */
    private JPanel buildPointsPanel()
    {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("1. The plane - choose 3 points (or 4)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 3, 1, 3);
        g.anchor = GridBagConstraints.WEST;
        ButtonGroup shape = new ButtonGroup();
        shape.add(threePoints);
        shape.add(fourPoints);
        ActionListener onShape = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                changeShape(fourPoints.isSelected());
            }
        };
        threePoints.addActionListener(onShape);
        fourPoints.addActionListener(onShape);
        JPanel shapeRow = new JPanel(new GridLayout(1, 2, 2, 0));
        shapeRow.add(threePoints);
        shapeRow.add(fourPoints);
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 5;
        p.add(shapeRow, g);
        g.gridwidth = 1;
        g.gridy = 1;
        String[] heads = {"", "x", "y", "z", ""};
        for (int c = 0; c < heads.length; c++)
        {
            g.gridx = c;
            p.add(new JLabel(heads[c]), g);
        }
        ActionListener apply = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                applyPointFields();
            }
        };
        for (int r = 0; r < 4; r++)
        {
            g.gridy = 2 + r;
            g.gridx = 0;
            rowNames[r] = new JLabel("a");
            rowNames[r].setFont(rowNames[r].getFont().deriveFont(Font.BOLD));
            p.add(rowNames[r], g);
            for (int c = 0; c < 3; c++)
            {
                g.gridx = 1 + c;
                fields[r][c] = new JTextField(5);
                fields[r][c].setMinimumSize(fields[r][c].getPreferredSize());
                fields[r][c].setToolTipText("Type a number and press Enter");
                fields[r][c].addActionListener(apply);
                p.add(fields[r][c], g);
            }
            g.gridx = 4;
            final int row = r;
            pickButtons[r] = new JButton("pick");
            pickButtons[r].setMargin(new Insets(1, 6, 1, 6));
            pickButtons[r].setToolTipText("Click this point on the 3-D model or on a slice view");
            pickButtons[r].addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    startPicking(row);
                }
            });
            p.add(pickButtons[r], g);
        }
        // The fix button only shows while the plane is doubtful (see showPointsInfo).
        JPanel buttons = new JPanel(new BorderLayout(3, 0));
        pickAll.setToolTipText("Click the points one after the other on the 3-D model or the slice views");
        pickAll.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (pickIndex >= 0)
                {
                    stopPicking();
                }
                else
                {
                    startPicking(-1);
                }
            }
        });
        fixButton.setToolTipText("Keep a and b, move c along a->c (triangle), or take the nearest isosceles trapezoid");
        fixButton.setVisible(false);
        fixButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (plane != null && plane.isRenderable())
                {
                    stopPlay();
                    applyPlane(PlaneSpec.makeIsosceles(plane));
                }
            }
        });
        buttons.add(small(pickAll), BorderLayout.CENTER);
        buttons.add(small(fixButton), BorderLayout.EAST);
        g.gridx = 0;
        g.gridy = 6;
        g.gridwidth = 5;
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(buttons, g);
        g.gridy = 7;
        pointsInfo.setFont(pointsInfo.getFont().deriveFont(Font.PLAIN, 11f));
        // Fixed size: a long message must wrap, not widen the panel (which would squeeze the fields).
        pointsInfo.setPreferredSize(new Dimension(CONTROL_WIDTH - 60, INFO_HEIGHT));
        pointsInfo.setMinimumSize(new Dimension(CONTROL_WIDTH - 60, INFO_HEIGHT));
        pointsInfo.setVerticalAlignment(JLabel.TOP);
        p.add(pointsInfo, g);
        showPointRows();
        return p;
    }

    /**
     * The video: Play shows it, Export saves it. It is the chosen plane moved through the whole
     * scan, so it is ready as soon as a scan is open - nothing is collected by hand.
     *
     * @return the video panel
     */
    private JPanel buildVideoPanel()
    {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createTitledBorder("2. The video"));
        videoInfo.setFont(videoInfo.getFont().deriveFont(Font.PLAIN, 11f));
        videoInfo.setPreferredSize(new Dimension(CONTROL_WIDTH - 60, INFO_HEIGHT));
        videoInfo.setMinimumSize(new Dimension(CONTROL_WIDTH - 60, INFO_HEIGHT));
        videoInfo.setVerticalAlignment(JLabel.TOP);
        p.add(videoInfo, BorderLayout.NORTH);

        JPanel run = new JPanel(new GridLayout(1, 2, 6, 0));
        run.setBorder(BorderFactory.createEmptyBorder(0, 3, 3, 3));
        playButton.setToolTipText("Plays the video in the views, back and forth, at " + VIDEO_FPS + " frames per second");
        playButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                togglePlay();
            }
        });
        exportButton.setToolTipText("Saves the video (one pass, first frame to last) as an MP4 file");
        exportButton.setFont(exportButton.getFont().deriveFont(Font.BOLD));
        exportButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportVideo();
            }
        });
        run.add(playButton);
        run.add(exportButton);
        run.setPreferredSize(new Dimension(CONTROL_WIDTH - 40, 34));
        p.add(run, BorderLayout.CENTER);
        return p;
    }

    /**
     * @param (Component) c a view
     * @param (String) title its caption
     * @return the view with a small title bar
     */
    private static JPanel titled(Component c, String title)
    {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(" " + title);
        l.setOpaque(true);
        l.setBackground(CUT_BAR);
        l.setForeground(new Color(0xE6E6E6));
        l.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        p.add(l, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    /**
     * @param (boolean) right true for "next"
     * @param (String) tip tooltip
     * @return a flat button with a drawn arrow
     */
    private static JButton arrowButton(boolean right, String tip)
    {
        JButton b = new JButton(new ArrowIcon(right));
        b.setToolTipText(tip);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 10, 2, 10));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ------------------------------------------------------------------ the picture panel

    /**
     * @param (int) delta +1 next picture, -1 previous; both wrap around
     */
    private void flipPicture(int delta)
    {
        showPicture((picture + delta + PICTURES) % PICTURES);
        pickMovedPicture = false;
    }

    /**
     * @param (int) which PICTURE_CUT, or 1..3 for the Axial, Coronal and Sagittal views
     */
    private void showPicture(int which)
    {
        picture = which;
        pictures.show(pictureCards, Integer.toString(which));
        Color bar = CUT_BAR;
        String title = "The cut - one frame of the video";
        if (which != PICTURE_CUT)
        {
            int view = which - 1;
            bar = OrthoView.VIEW_COLOURS[view].darker();
            title = VolumeGeometry.name(view) + " - " + VolumeGeometry.hint(view);
        }
        pictureBar.setBackground(bar);
        pictureTitle.setText(title);
        pictureCount.setText((which + 1) + " / " + PICTURES + "  ");
    }

    // ------------------------------------------------------------------ volume

    /**
     * Asks for a scan with the system's own file dialog (on Windows the Explorer one: Desktop,
     * Downloads, This PC ...), so a scan can come from anywhere on the computer.
     */
    private void chooseVolume()
    {
        FileDialog dialog = new FileDialog(frame, "Open a 3-D scan (.nrrd)", FileDialog.LOAD);
        dialog.setDirectory(openFolder().getAbsolutePath());
        if (onWindows())
        {
            // The Windows dialog takes a pattern in the file name box as its filter.
            dialog.setFile("*.nrrd;*.nhdr");
        }
        dialog.setFilenameFilter(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                String n = name.toLowerCase(Locale.ROOT);
                return n.endsWith(".nrrd") || n.endsWith(".nhdr");
            }
        });
        dialog.setVisible(true);
        if (dialog.getFile() == null)
        {
            return;
        }
        openVolume(new File(dialog.getDirectory(), dialog.getFile()));
    }

    /**
     * Loads a volume in the background, shows it, then builds the 3-D model.
     *
     * @param (File) file NRRD file
     */
    void openVolume(final File file)
    {
        if (busy || file == null)
        {
            return;
        }
        stopPlay();
        stopPicking();
        setBusy(true, "Loading " + file.getName() + " ...");
        SwingWorker<Object[], Void> worker = new SwingWorker<Object[], Void>()
        {
            @Override
            protected Object[] doInBackground() throws Exception
            {
                Volume v = NrrdReader.read(file);
                IntensityWindow w = IntensityWindow.fromPercentiles(v,
                        IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
                return new Object[] {v, w};
            }

            @Override
            protected void done()
            {
                setBusy(false, " ");
                try
                {
                    Object[] r = get();
                    volume = (Volume) r[0];
                    window = (IntensityWindow) r[1];
                    geometry = VolumeGeometry.of(volume);
                    volumeFile = file;
                    frame.setTitle(TITLE + " - " + file.getName() + "  (" + volume + ")");
                    view3d.setVolume(volume, window, geometry);
                    for (int v = 0; v < 3; v++)
                    {
                        orthos[v].view().setVolume(volume, window, geometry);
                        orthos[v].sync();
                    }
                    sliceChanged(0);
                    recentCount = 0;
                    warmUp(PlaneSpec.triangle(volume).toPlane());
                    applySpec(PlaneSpec.triangle(volume));
                    view3d.lookAtPlane(plane);
                    showPicture(PICTURE_CUT);
                    buildModel();
                    updateEnabled();
                }
                catch (ExecutionException e)
                {
                    error("Cannot open " + file.getName(), message(e.getCause()));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                catch (OutOfMemoryError e)
                {
                    error("Not enough memory", "The volume is too large for the memory given to Java (-Xmx).");
                }
            }
        };
        worker.execute();
    }

    /** Builds the solid model in the background (its surface is found automatically). */
    private void buildModel()
    {
        final Volume v = volume;
        final IntensityWindow w = window;
        final int generation = ++modelGeneration;
        view3d.setModel(null);
        status.setText("Building the 3-D model ...");
        SwingWorker<SolidModel, Void> worker = new SwingWorker<SolidModel, Void>()
        {
            @Override
            protected SolidModel doInBackground()
            {
                return SolidModel.build(v, w);
            }

            @Override
            protected void done()
            {
                if (generation != modelGeneration || v != volume)
                {
                    return;
                }
                try
                {
                    view3d.setModel(get());
                    updateEnabled();
                }
                catch (ExecutionException e)
                {
                    error("3-D model failed", message(e.getCause()));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
    }

    /**
     * Projects a plane a few times, untimed, so the FPS shown from the first cut on is the
     * algorithm's speed and not the Java compiler warming up.
     *
     * @param (Plane) p a plane of the new volume
     */
    private void warmUp(Plane p)
    {
        if (!p.isRenderable())
        {
            return;
        }
        for (int i = 0; i < WARM_UP_PROJECTIONS; i++)
        {
            projector.project(volume, p);
        }
    }

    // ------------------------------------------------------------------ plane editing

    /**
     * Sets the current plane from a spec and updates everything.
     *
     * @param (PlaneSpec) s new spec
     */
    private void applySpec(PlaneSpec s)
    {
        spec = s;
        plane = s.toPlane();
        refreshViews();
    }

    /**
     * Shows an arbitrary plane (typed or clicked points, a frame of the video): drawn as it is.
     *
     * @param (Plane) p plane
     */
    private void applyPlane(Plane p)
    {
        plane = p;
        spec = PlaneSpec.fromPlane(p);
        refreshViews();
    }

    /**
     * Switches between 3 and 4 points, keeping the plane's place and size.
     *
     * @param (boolean) four true for 4 points (trapezoid)
     */
    private void changeShape(boolean four)
    {
        // The choice is read before anything is redrawn: stopping play or picking redraws the old
        // plane, which sets the buttons back to its shape (that was why "4 points" did nothing).
        stopPlay();
        stopPicking();
        if (plane != null && plane.isTriangle() == four)
        {
            double top = 0.0;
            if (four)
            {
                top = 0.5;
            }
            applySpec(spec.withShape(spec.width, top, spec.height));
            return;
        }
        threePoints.setSelected(!four);
        fourPoints.setSelected(four);
        showPointRows();
    }

    @Override
    public void slidePlane(double distance)
    {
        if (spec == null || busy)
        {
            return;
        }
        stopPlay();
        applySpec(spec.slide(distance));
    }

    /** Projects the current plane once and hands the result to every view. */
    private void refreshViews()
    {
        if (volume == null || plane == null)
        {
            cutView.setImage(null, 0, 0, "");
            return;
        }
        if (plane.isTriangle() != threePoints.isSelected())
        {
            threePoints.setSelected(plane.isTriangle());
            fourPoints.setSelected(!plane.isTriangle());
            showPointRows();
        }
        List<Vec3> pts = planePoints(plane);
        List<String> names = pointNames(plane);
        addPickedPoints(pts, names);
        for (int v = 0; v < 3; v++)
        {
            orthos[v].view().setPlane(plane, pts, names);
        }
        fieldsFromPlane();
        String bad = plane.whyNotRenderable();
        if (bad != null)
        {
            cutView.setImage(null, 0, 0, framePrefix() + "bad plane, a black frame in the video: " + bad);
            view3d.setPlane(null, pts, names);
            show(fixButton, false);
            if (pickIndex < 0)
            {
                pointsInfo.setText(html("Bad plane (a black frame in the video): " + bad, "#c03030"));
            }
            updateVideoInfo();
            return;
        }
        long t0 = System.nanoTime();
        Image2D img = projector.project(volume, plane);
        noteProjectionTime((System.nanoTime() - t0) / NANOS_PER_MS);
        int w = img.width();
        int h = img.height();
        byte[] grey = new byte[w * h];
        window.toGrey(img, grey, w, h, w, h);
        cutView.setImage(grey, w, h, framePrefix() + w + " x " + h + " pixels");
        cutView.setCornerLabels(plane.isTriangle());
        view3d.setPlane(plane, pts, names);
        showPointsInfo(plane.validate(Plane.DEFAULT_EPS));
        updateVideoInfo();
        updateStatus();
    }

    /**
     * @return "frame i of n   " while playing, else ""
     */
    private String framePrefix()
    {
        if (!playTimer.isRunning() || playSequence == null)
        {
            return "";
        }
        return "frame " + (playIndex + 1) + " of " + playSequence.size() + "   ";
    }

    /**
     * Shows a warning only when there is something to fix; a valid plane needs no numbers.
     *
     * @param (List) problems rule violations of the current plane
     */
    private void showPointsInfo(List<String> problems)
    {
        show(fixButton, !problems.isEmpty() && pickIndex < 0);
        if (pickIndex >= 0)
        {
            return;
        }
        if (problems.isEmpty())
        {
            pointsInfo.setText(html("Type the points and press Enter, or pick them with the mouse on the views.", HINT_COLOUR));
            return;
        }
        pointsInfo.setText(html("Not isosceles (drawn as given): " + problems.get(0) + ".", "#b06000"));
    }

    /**
     * Records how long one projection took and, now and then, shows the speed in the toolbar.
     *
     * @param (double) ms projection time of one frame in milliseconds
     */
    private void noteProjectionTime(double ms)
    {
        recentMs[recentCount % FPS_SAMPLES] = ms;
        recentCount++;
        long now = System.currentTimeMillis();
        if (now - fpsShownAt < FPS_LABEL_MS && recentCount > 1)
        {
            return;
        }
        fpsShownAt = now;
        int n = Math.min(recentCount, FPS_SAMPLES);
        double[] sorted = Arrays.copyOf(recentMs, n);
        Arrays.sort(sorted);
        double median = sorted[n / 2];
        fpsLabel.setText(String.format(Locale.ROOT, "Projection: %.0f FPS", 1000.0 / Math.max(median, 1e-3)));
    }

    // ------------------------------------------------------------------ points editor

    /** Shows 3 or 4 rows of point fields. */
    private void showPointRows()
    {
        String[] names = TRIANGLE_NAMES;
        if (fourPoints.isSelected())
        {
            names = TRAPEZOID_NAMES;
        }
        for (int r = 0; r < 4; r++)
        {
            boolean on = r < names.length;
            rowNames[r].setVisible(on);
            pickButtons[r].setVisible(on);
            for (int c = 0; c < 3; c++)
            {
                fields[r][c].setVisible(on);
            }
            if (on)
            {
                rowNames[r].setText(names[r]);
            }
        }
        pickAll.setText("Pick " + String.join(", ", names));
    }

    /** Writes the current plane's points into the fields. */
    private void fieldsFromPlane()
    {
        List<Vec3> pts = planePoints(plane);
        for (int r = 0; r < pts.size() && r < 4; r++)
        {
            Vec3 p = pts.get(r);
            fields[r][0].setText(format(p.x));
            fields[r][1].setText(format(p.y));
            fields[r][2].setText(format(p.z));
        }
    }

    /** Builds the plane from the typed points, exactly as given. */
    private void applyPointFields()
    {
        if (volume == null)
        {
            return;
        }
        stopPlay();
        int n = 3;
        if (fourPoints.isSelected())
        {
            n = 4;
        }
        Vec3[] p = new Vec3[n];
        for (int r = 0; r < n; r++)
        {
            p[r] = readRow(r);
            if (p[r] == null)
            {
                pointsInfo.setText(html("Point " + rowNames[r].getText() + ": type three numbers", "#c03030"));
                return;
            }
        }
        applyPlane(planeOf(p));
    }

    /**
     * @param (Vec3[]) p points in editor order (a, b, c or a1, a2, b, c)
     * @return the plane
     */
    private static Plane planeOf(Vec3[] p)
    {
        if (p.length == 3)
        {
            return Plane.triangle(p[0], p[1], p[2]);
        }
        return Plane.trapezoid(p[0], p[1], p[2], p[3]);
    }

    /**
     * @param (int) r row
     * @return the typed point, or null when a field is not a number
     */
    private Vec3 readRow(int r)
    {
        double[] v = new double[3];
        for (int c = 0; c < 3; c++)
        {
            try
            {
                v[c] = Double.parseDouble(fields[r][c].getText().trim());
            }
            catch (NumberFormatException e)
            {
                return null;
            }
            if (!Double.isFinite(v[c]))
            {
                return null;
            }
        }
        return new Vec3(v[0], v[1], v[2]);
    }

    /**
     * Starts placing points with the mouse. The cut cannot be clicked on, so when it is the
     * picture on show, the picture panel turns to the Axial view (and back afterwards).
     *
     * @param (int) only row to pick alone, or -1 for all points in order
     */
    private void startPicking(int only)
    {
        if (volume == null)
        {
            return;
        }
        stopPlay();
        pickNames = TRIANGLE_NAMES;
        if (fourPoints.isSelected())
        {
            pickNames = TRAPEZOID_NAMES;
        }
        picked = new Vec3[pickNames.length];
        pickOnly = only;
        pickIndex = 0;
        if (only >= 0)
        {
            pickIndex = only;
        }
        if (picture == PICTURE_CUT)
        {
            showPicture(1);
            pickMovedPicture = true;
        }
        setPickingViews(true);
        pickAll.setText("Stop picking");
        promptPick();
        refreshViews();
    }

    /** Ends picking mode. */
    private void stopPicking()
    {
        pickIndex = -1;
        pickOnly = -1;
        picked = null;
        setPickingViews(false);
        showPointRows();
        if (pickMovedPicture)
        {
            pickMovedPicture = false;
            showPicture(PICTURE_CUT);
        }
        if (plane != null)
        {
            refreshViews();
        }
    }

    /**
     * @param (boolean) on picking mode for all views
     */
    private void setPickingViews(boolean on)
    {
        view3d.setPicking(on);
        for (int v = 0; v < 3; v++)
        {
            orthos[v].view().setPicking(on);
        }
    }

    /** Tells the user which point to click next. */
    private void promptPick()
    {
        pointsInfo.setText(html("Click point " + pickNames[pickIndex] + " on the 3-D model or on the slice picture"
                + " (its arrows change the view, its slider the slice).", "#c06000"));
    }

    /**
     * A point was clicked while picking.
     *
     * @param (Vec3) p voxel-index point
     */
    private void pointPicked(Vec3 p)
    {
        picked[pickIndex] = p;
        fields[pickIndex][0].setText(format(p.x));
        fields[pickIndex][1].setText(format(p.y));
        fields[pickIndex][2].setText(format(p.z));
        if (pickOnly >= 0)
        {
            stopPicking();
            applyPointFields();
            return;
        }
        pickIndex++;
        if (pickIndex < pickNames.length)
        {
            promptPick();
            refreshViews();
            return;
        }
        Vec3[] pts = picked;
        stopPicking();
        Plane p0 = planeOf(pts);
        String bad = p0.whyNotRenderable();
        if (bad != null)
        {
            applyPlane(p0);
            return;
        }
        if (!p0.validate(Plane.DEFAULT_EPS).isEmpty())
        {
            // Clicks are never exact: keep a and b, move c so the triangle is isosceles.
            applyPlane(PlaneSpec.makeIsosceles(p0));
            pointsInfo.setText(html(fixMessage(p0), null));
            return;
        }
        applyPlane(p0);
    }

    /**
     * @param (Plane) clicked the plane as clicked
     * @return what was changed to make it isosceles, in words
     */
    private static String fixMessage(Plane clicked)
    {
        if (clicked.isTriangle())
        {
            return "Clicks are never exact, so c was moved a little along the line a-c to make the triangle isosceles.";
        }
        return "Clicks are never exact, so the points were moved to the nearest isosceles trapezoid.";
    }

    /**
     * Adds the points clicked so far (while picking) to the markers.
     *
     * @param (List) pts markers
     * @param (List) names labels
     */
    private void addPickedPoints(List<Vec3> pts, List<String> names)
    {
        if (pickIndex < 0 || picked == null)
        {
            return;
        }
        pts.clear();
        names.clear();
        for (int i = 0; i < picked.length; i++)
        {
            if (picked[i] != null)
            {
                pts.add(picked[i]);
                names.add(pickNames[i]);
            }
        }
    }

    @Override
    public void modelClicked(Vec3 index)
    {
        if (pickIndex >= 0)
        {
            pointPicked(index);
            return;
        }
        navigateTo(index);
    }

    @Override
    public void orthoClicked(int view, Vec3 index)
    {
        if (pickIndex >= 0)
        {
            pointPicked(index);
            return;
        }
        navigateTo(index);
    }

    @Override
    public void sliceChanged(int view)
    {
        int[] slices = new int[3];
        for (int v = 0; v < 3; v++)
        {
            slices[v] = orthos[v].view().slice();
        }
        for (int v = 0; v < 3; v++)
        {
            orthos[v].view().setOtherSlices(slices);
            orthos[v].sync();
        }
    }

    /**
     * Moves the three slice views through a point (Slicer's crosshair).
     *
     * @param (Vec3) p voxel-index point
     */
    private void navigateTo(Vec3 p)
    {
        double[] c = {p.x, p.y, p.z};
        for (int v = 0; v < 3; v++)
        {
            int axis = geometry.constantAxis(v);
            orthos[v].view().setSlice((int) Math.round(c[axis]));
        }
        sliceChanged(0);
        status.setText(String.format(Locale.ROOT, "Point (%.1f, %.1f, %.1f): the slice views now go through it", p.x, p.y, p.z));
    }

    // ------------------------------------------------------------------ the video: play and export

    /**
     * The video, frame by frame: the current plane swept through the scan, each frame rounded to
     * the precision a CSV row would keep (so the command line, given these planes, makes the same
     * frames); a plane the algorithm cannot draw becomes null, a black frame.
     *
     * @return the planes in order and the frame of the current plane
     */
    private Sweep.Pass videoPass()
    {
        Sweep.Pass sweep = Sweep.through(plane, volume.sizeX(), volume.sizeY(), volume.sizeZ());
        return new Sweep.Pass(videoFrames(sweep.planes), sweep.current);
    }

    /**
     * @param (List) source planes of a video (null = black frame)
     * @return the same planes rounded to the CSV precision; one the algorithm cannot draw is null
     */
    static List<Plane> videoFrames(List<Plane> source)
    {
        List<Plane> out = new ArrayList<Plane>();
        for (int i = 0; i < source.size(); i++)
        {
            Plane p = PlaneCsvWriter.quantize(source.get(i));
            if (p != null && !p.isRenderable())
            {
                p = null;
            }
            out.add(p);
        }
        return out;
    }

    /** Starts or stops playing the video in the views. */
    private void togglePlay()
    {
        if (playTimer.isRunning())
        {
            stopPlay();
            return;
        }
        if (volume == null || plane == null || busy)
        {
            return;
        }
        stopPicking();
        Sweep.Pass pass = videoPass();
        playSequence = pass.planes;
        playIndex = pass.current;
        playStep = 1;
        playButton.setText("Stop");
        playTimer.start();
        stepPlayback();
    }

    /** Stops playing; the frame on screen stays, as the current plane. */
    private void stopPlay()
    {
        if (!playTimer.isRunning())
        {
            return;
        }
        playTimer.stop();
        playButton.setText("Play");
        // Redraw the last frame without its frame number.
        if (plane != null)
        {
            refreshViews();
        }
        updateEnabled();
    }

    /**
     * One tick: shows the current frame, then steps to the next one, turning around at either end,
     * so the video runs back and forth (... n-1, n, n-1, ... 2, 1, 2 ...) until Stop.
     */
    private void stepPlayback()
    {
        if (playSequence == null || playSequence.isEmpty() || volume == null)
        {
            stopPlay();
            return;
        }
        Plane p = playSequence.get(playIndex);
        if (p != null)
        {
            applyPlane(p);
        }
        else
        {
            refreshViews();
        }
        status.setText("Playing frame " + (playIndex + 1) + " of " + playSequence.size() + ", back and forth at "
                + VIDEO_FPS + " fps. Stop keeps the frame on screen.");
        if (playSequence.size() > 1)
        {
            if (playIndex + playStep < 0 || playIndex + playStep >= playSequence.size())
            {
                playStep = -playStep;
            }
            playIndex += playStep;
        }
    }

    /**
     * Saves the video as an MP4 in the background: one pass, first frame to last, at the rate Play
     * shows it. If ffmpeg on this computer fails, the same frames are written again with the
     * built-in encoder, so an export never fails because of another program.
     */
    private void exportVideo()
    {
        if (volume == null || plane == null || busy)
        {
            return;
        }
        stopPlay();
        stopPicking();
        final List<Plane> planes = videoPass().planes;
        final Projector exportProjector = Projector.create(Projector.Interpolation.NEAREST, CORES);
        final FrameRenderer renderer;
        try
        {
            renderer = new FrameRenderer(volume, planes, window, exportProjector);
        }
        catch (IllegalArgumentException e)
        {
            shutdown(exportProjector);
            error("Nothing to export", "The points do not make a plane (" + message(e) + "), so every frame would be black.\n"
                    + "Choose 3 points that are not on one line.");
            return;
        }
        final File target = chooseVideoFile(planes.size());
        if (target == null)
        {
            shutdown(exportProjector);
            return;
        }
        setBusy(true, "Exporting " + target.getName() + " ...");
        progress.setMaximum(renderer.frameCount());
        progress.setValue(0);
        progress.setVisible(true);
        SwingWorker<String, Integer> worker = new SwingWorker<String, Integer>()
        {
            @Override
            protected String doInBackground() throws Exception
            {
                VideoWriter out = VideoWriters.openMp4(target, renderer.width(), renderer.height(), VIDEO_FPS);
                try
                {
                    writeAll(out);
                    return "";
                }
                catch (IOException ffmpegFailed)
                {
                    if (!VideoWriters.usesFfmpeg(out))
                    {
                        throw ffmpegFailed;
                    }
                    writeAll(VideoWriters.openBuiltInMp4(target, renderer.width(), renderer.height(), VIDEO_FPS));
                    return "\n\nThe ffmpeg on this computer did not work, so the app's own encoder made the video.\n("
                            + shortText(message(ffmpegFailed)) + ")";
                }
            }

            /**
             * @param (VideoWriter) out an open writer; closed at the end, also on failure
             * @throws (IOException) when a frame cannot be written
             */
            private void writeAll(VideoWriter out) throws IOException
            {
                try
                {
                    for (int i = 0; i < renderer.frameCount(); i++)
                    {
                        out.writeFrame(renderer.render(i));
                        publish(Integer.valueOf(i + 1));
                    }
                }
                finally
                {
                    out.close();
                }
            }

            @Override
            protected void process(List<Integer> chunks)
            {
                progress.setValue(chunks.get(chunks.size() - 1).intValue());
            }

            @Override
            protected void done()
            {
                shutdown(exportProjector);
                progress.setVisible(false);
                setBusy(false, " ");
                try
                {
                    String note = get();
                    int answer = JOptionPane.showConfirmDialog(frame,
                            "Saved " + renderer.frameCount() + " frames (" + renderer.width() + " x " + renderer.height()
                                    + " pixels, " + seconds(renderer.frameCount()) + " s at " + VIDEO_FPS + " fps) to\n"
                                    + target.getAbsolutePath() + note + "\n\nOpen the video now?",
                            "Export finished", JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION)
                    {
                        openInSystem(target);
                    }
                }
                catch (ExecutionException e)
                {
                    // A half-written file is no video; better none than a broken one.
                    if (target.isFile() && !target.delete())
                    {
                        target.deleteOnExit();
                    }
                    error("Export failed", exportProblem(target, e.getCause()));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
    }

    /**
     * Asks where to save the video, with the system's own file dialog. The name proposed is not
     * taken yet; a file chosen anyway is checked first: on Windows a video open in a player cannot
     * be replaced. (The system dialog itself asks before replacing a file.)
     *
     * @param (int) frames number of frames, for the dialog title
     * @return the file, or null when cancelled
     */
    private File chooseVideoFile(int frames)
    {
        File folder = volumeFile.getAbsoluteFile().getParentFile();
        FileDialog dialog = new FileDialog(frame, "Export the video: " + frames + " frames, " + seconds(frames) + " s at "
                + VIDEO_FPS + " fps", FileDialog.SAVE);
        dialog.setDirectory(folder.getAbsolutePath());
        dialog.setFile(freeName(folder, baseName() + "_cut", ".mp4").getName());
        dialog.setVisible(true);
        if (dialog.getFile() == null)
        {
            return null;
        }
        File chosen = new File(dialog.getDirectory(), dialog.getFile());
        File target = withExtension(chosen, ".mp4");
        // The dialog asked about the name typed; ".mp4" added here may name another file that exists.
        if (!target.equals(chosen) && target.exists() && JOptionPane.showConfirmDialog(frame,
                target.getName() + " already exists. Replace it?", "Export video", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
        {
            return null;
        }
        if (target.exists() && !canReplace(target))
        {
            error("Cannot replace " + target.getName(), "The file is open in another program (a video player?).\n"
                    + "Close it there, or save the video under another name.");
            return null;
        }
        return target;
    }

    /**
     * @param (File) folder where the file goes
     * @param (String) base name without extension
     * @param (String) ext extension with dot
     * @return base.ext, or base_2.ext, base_3.ext ... - the first that does not exist yet
     */
    static File freeName(File folder, String base, String ext)
    {
        File f = new File(folder, base + ext);
        for (int i = 2; f.exists() && i <= MAX_NAME_TRIES; i++)
        {
            f = new File(folder, base + "_" + i + ext);
        }
        return f;
    }

    /**
     * @param (File) f an existing file
     * @return whether it can be opened for writing (not locked by another program, not read-only)
     */
    private static boolean canReplace(File f)
    {
        RandomAccessFile raf = null;
        try
        {
            raf = new RandomAccessFile(f, "rw");
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
        finally
        {
            if (raf != null)
            {
                try
                {
                    raf.close();
                }
                catch (IOException e)
                {
                    // It was only opened to test it.
                }
            }
        }
    }

    /**
     * @param (File) target the video that was being written
     * @param (Throwable) t what went wrong
     * @return what happened in plain words, then the technical message (for a screenshot)
     */
    static String exportProblem(File target, Throwable t)
    {
        String text = message(t).toLowerCase(Locale.ROOT);
        String advice = "Try again, or save it in another folder (for example Videos or Downloads).";
        if (text.contains("access is denied") || text.contains("permission denied") || text.contains("read-only"))
        {
            advice = "Windows did not allow the app to write in that folder.\n"
                    + "Save it in another folder (for example Videos or Downloads).";
        }
        else if (text.contains("used by another process") || text.contains("locked") || text.contains("cannot overwrite"))
        {
            advice = "The file is open in another program (a video player?). Close it there, or use another name.";
        }
        else if (text.contains("not enough space") || text.contains("no space") || text.contains("disk full"))
        {
            advice = "The disk is full. Free some space, or save it on another drive.";
        }
        else if (t instanceof OutOfMemoryError)
        {
            advice = "Java ran out of memory. Close other programs and try again.";
        }
        String name = "unknown error";
        if (t != null)
        {
            name = t.getClass().getSimpleName();
        }
        return "The video could not be saved to\n" + target.getAbsolutePath() + "\n\n" + advice
                + "\n\nDetails (" + name + "): " + message(t);
    }

    /** Says in the video panel what Play shows and Export saves. */
    private void updateVideoInfo()
    {
        if (volume == null || plane == null)
        {
            videoInfo.setText(html("Open a scan: the video is then ready to play and export.", HINT_COLOUR));
            return;
        }
        int frames = Sweep.through(plane, volume.sizeX(), volume.sizeY(), volume.sizeZ()).planes.size();
        videoInfo.setText(html("Video = the plane moving through the whole scan: " + frames + " frames, " + seconds(frames)
                + " s at " + VIDEO_FPS + " fps. Play runs it back and forth, Export saves it once.", null));
    }

    /**
     * @param (String) text a message, maybe long and over several lines
     * @return its last line, at most about one dialog line long
     */
    private static String shortText(String text)
    {
        String[] lines = text.trim().split("\\R");
        String last = lines[lines.length - 1].trim();
        if (last.length() > MAX_NOTE_CHARS)
        {
            last = "..." + last.substring(last.length() - MAX_NOTE_CHARS);
        }
        return last;
    }

    /**
     * @param (int) frames number of frames
     * @return the video's length in seconds, e.g. "13" or "1.6"
     */
    private static String seconds(int frames)
    {
        double s = frames / (double) VIDEO_FPS;
        if (s >= 10 || s == Math.rint(s))
        {
            return String.format(Locale.ROOT, "%.0f", s);
        }
        return String.format(Locale.ROOT, "%.1f", s);
    }

    /**
     * @param (Projector) p a projector made for one export
     */
    private static void shutdown(Projector p)
    {
        if (p instanceof ParallelProjector)
        {
            ((ParallelProjector) p).shutdown();
        }
    }

    // ========================================================= LOW-LEVEL HELPERS

    /**
     * @param (Plane) p plane
     * @return its points in editor order: a, b, c or a1, a2, b, c
     */
    private static List<Vec3> planePoints(Plane p)
    {
        List<Vec3> pts = new ArrayList<Vec3>();
        pts.add(p.a1());
        if (!p.isTriangle())
        {
            pts.add(p.a2());
        }
        pts.add(p.b());
        pts.add(p.c());
        return pts;
    }

    /**
     * @param (Plane) p plane
     * @return the labels of its points in editor order
     */
    private static List<String> pointNames(Plane p)
    {
        List<String> names = new ArrayList<String>();
        String[] src = TRIANGLE_NAMES;
        if (!p.isTriangle())
        {
            src = TRAPEZOID_NAMES;
        }
        for (int i = 0; i < src.length; i++)
        {
            names.add(src[i]);
        }
        return names;
    }

    /**
     * @param (String) text message
     * @param (String) colour CSS colour, or null for the default
     * @return the message as wrapping HTML for the narrow info label
     */
    private static String html(String text, String colour)
    {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String style = "width:" + HTML_WIDTH + "px";
        if (colour != null)
        {
            style += ";color:" + colour;
        }
        return "<html><body style='" + style + "'>" + safe + "</body></html>";
    }

    /**
     * @param (double) v coordinate
     * @return short text for a field
     */
    private static String format(double v)
    {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.endsWith(".00"))
        {
            s = s.substring(0, s.length() - 3);
        }
        if (s.equals("-0"))
        {
            s = "0";
        }
        return s;
    }

    /**
     * @param (boolean) b busy flag
     * @param (String) text status text
     */
    private void setBusy(boolean b, String text)
    {
        busy = b;
        status.setText(text);
        if (b)
        {
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        else
        {
            frame.setCursor(Cursor.getDefaultCursor());
        }
        updateEnabled();
    }

    /** Enables the controls that make sense in the current state. */
    private void updateEnabled()
    {
        boolean haveVolume = volume != null && !busy;
        // There is always a video once a scan is open: the current plane swept through it.
        playButton.setEnabled(haveVolume);
        exportButton.setEnabled(haveVolume);
        pickAll.setEnabled(haveVolume);
        updateVideoInfo();
        updateStatus();
    }

    /**
     * Shows or hides a control and lays its panel out again (hiding alone leaves a gap).
     *
     * @param (Component) c the control
     * @param (boolean) on visible or not
     */
    private static void show(Component c, boolean on)
    {
        if (c.isVisible() == on)
        {
            return;
        }
        c.setVisible(on);
        if (c.getParent() != null)
        {
            c.getParent().revalidate();
            c.getParent().repaint();
        }
    }

    /** The status line in the normal state. */
    private void updateStatus()
    {
        if (volume == null || busy || pickIndex >= 0 || playTimer.isRunning())
        {
            return;
        }
        String spacing = "";
        if (geometry != null && geometry.known())
        {
            spacing = String.format(Locale.ROOT, ", voxel %.2f x %.2f x %.2f mm", geometry.spacing(0), geometry.spacing(1), geometry.spacing(2));
        }
        status.setText(volumeFile.getName() + "  " + volume + spacing);
    }

    /**
     * @param (String) text label
     * @param (ActionListener) action click handler
     * @return the button
     */
    private static JButton button(String text, ActionListener action)
    {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /**
     * @param (JButton) b button
     * @return the same button with narrow margins (the control column is narrow)
     */
    private static JButton small(JButton b)
    {
        b.setMargin(new Insets(2, 4, 2, 4));
        return b;
    }

    /**
     * @return name of the volume file without extension
     */
    private String baseName()
    {
        if (volumeFile == null)
        {
            return "video";
        }
        return volumeFile.getName().replaceFirst("\\.[^.]*$", "");
    }

    /**
     * @param (File) f chosen file
     * @param (String) ext extension with dot
     * @return f with the extension appended when missing
     */
    private static File withExtension(File f, String ext)
    {
        if (f.getName().toLowerCase(Locale.ROOT).endsWith(ext))
        {
            return f;
        }
        return new File(f.getPath() + ext);
    }

    /**
     * @return where the open dialog starts: the current scan's folder, else Downloads, else home
     */
    private File openFolder()
    {
        if (volumeFile != null)
        {
            return volumeFile.getAbsoluteFile().getParentFile();
        }
        File home = new File(System.getProperty("user.home"));
        File downloads = new File(home, "Downloads");
        if (downloads.isDirectory())
        {
            return downloads;
        }
        return home;
    }

    /**
     * @return true on Windows (its file dialog filters by a pattern in the name box)
     */
    private static boolean onWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * @param (File) f the saved video, to open with the system's default player
     */
    private void openInSystem(File f)
    {
        try
        {
            Desktop.getDesktop().open(f);
        }
        catch (Exception e)
        {
            // Not an error of the export: the video is saved, the computer just has no player for it here.
            JOptionPane.showMessageDialog(frame, "The video is saved, but no program opened it:\n" + f.getAbsolutePath()
                    + "\nOpen it from its folder.", "Export video", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * @param (String) title dialog title
     * @param (String) text message
     */
    private void error(String title, String text)
    {
        JOptionPane.showMessageDialog(frame, text, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * @param (Throwable) t error
     * @return readable message
     */
    private static String message(Throwable t)
    {
        if (t == null)
        {
            return "Unknown error";
        }
        if (t.getMessage() == null)
        {
            return t.getClass().getSimpleName();
        }
        return t.getMessage();
    }

    /** A filled triangle pointing left or right: drawn, so no font needs an arrow glyph. */
    private static final class ArrowIcon implements Icon
    {
        private static final int SIZE = 16;
        private final boolean right;

        /**
         * @param (boolean) right true points right
         */
        ArrowIcon(boolean right)
        {
            this.right = right;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y)
        {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            int[] xs = {x + SIZE - 4, x + 3, x + SIZE - 4};
            if (right)
            {
                xs = new int[] {x + 4, x + SIZE - 3, x + 4};
            }
            int[] ys = {y + 1, y + SIZE / 2, y + SIZE - 1};
            g.fillPolygon(xs, ys, 3);
            g.dispose();
        }

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }
    }

    /** The control column: as wide as the scroll pane, only scrolls vertically. */
    private static final class ColumnPanel extends JPanel implements javax.swing.Scrollable
    {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction)
        {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    /** Accepts a .nrrd/.nhdr volume dragged from the file manager. */
    private final class FileDrop extends TransferHandler
    {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canImport(TransferSupport support)
        {
            return !busy && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support)
        {
            if (!canImport(support))
            {
                return false;
            }
            try
            {
                List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty())
                {
                    return false;
                }
                File f = (File) files.get(0);
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".csv"))
                {
                    JOptionPane.showMessageDialog(frame, "Lists of planes (CSV) are for the command line:\n"
                            + "python Ex0.py scan.nrrd " + f.getName() + " out.mp4", "CSV file", JOptionPane.INFORMATION_MESSAGE);
                    return false;
                }
                if (!name.endsWith(".nrrd") && !name.endsWith(".nhdr"))
                {
                    error("Unsupported file", f.getName() + " is not a .nrrd or .nhdr scan.");
                    return false;
                }
                openVolume(f);
                return true;
            }
            catch (Exception e)
            {
                error("Drop failed", message(e));
                return false;
            }
        }
    }
}
