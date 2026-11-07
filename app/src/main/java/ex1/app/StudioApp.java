package ex1.app;

import ex1.app.ui.Badge;
import ex1.app.ui.Icons;
import ex1.app.ui.Theme;
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
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.border.Border;

/**
 * The application (the deliverable of Ex1): open a scan (NRRD) from anywhere on the computer or by
 * dropping it on the window, see it as a solid object, choose the plane by its 3 points (or 4 for a
 * trapezoid) - clicked on the views or typed - and see the object cut open along it (always: the
 * inside shows on the cut face) next to the plane's projection, the image of the assignment. One
 * picture panel flips between that cut and the three standard views of 3D Slicer (axial, coronal,
 * sagittal), on which the points are clicked.
 *
 * The video is the chosen plane moved through the whole scan: Play shows it back and forth, Export
 * video saves one pass of it as an MP4. Lists of planes in CSV files (the lecturer's use cases) are
 * the command line's job: python Ex0.py scan.nrrd planes.csv out.mp4 (ADR-0012). Sampling is
 * nearest-neighbour, the course's M[p] (ADR-0011).
 *
 * The FPS - how many cuts per second the projection computes - is measured on the frames of the
 * video while it plays, shown in the toolbar and on the picture, and written on every frame of an
 * exported video; while nothing plays there is nothing to measure and it says so (ADR-0014). The
 * look is {@link Theme}: dark graphite, amber for the plane.
 */
public final class StudioApp implements VolumeView3D.Host, OrthoView.Host
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The window's title. */
    private static final String TITLE = "Ex1 - cut a 3-D scan with a plane";

    /** Cores of this computer: the 3-D view traces its rays on all but one. */
    private static final int CORES = Runtime.getRuntime().availableProcessors();

    /** Play and export use one rate, so Play shows the video at its real speed: smooth motion. */
    static final int VIDEO_FPS = 30;

    /** Names of the points of a triangle and of a trapezoid. */
    private static final String[] TRIANGLE_NAMES  = {"a", "b", "c"};
    private static final String[] TRAPEZOID_NAMES = {"a1", "a2", "b", "c"};

    /** Width of the control column, in pixels. */
    private static final int CONTROL_WIDTH = 384;

    /** Width of the info texts of the control column (its padding is 18 on each side). */
    private static final int INFO_WIDTH = CONTROL_WIDTH - 46;

    /** Wrap width of HTML labels in CSS px: Swing draws one CSS px as 1.3 screen pixels. */
    private static final int HTML_WIDTH = (INFO_WIDTH - 12) * 10 / 13;

    /** Heights of the info texts and of the views' header strips. */
    private static final int INFO_HEIGHT   = 50;
    private static final int HEADER_HEIGHT = 38;

    /** Size each view asks for at the start. */
    private static final int VIEW_WIDTH  = 560;
    private static final int VIEW_HEIGHT = 480;

    /** Colours in the info texts: hints, errors, warnings, and the point being picked. */
    private static final String HINT_COLOUR    = Theme.hex(Theme.TEXT_DIM);
    private static final String ERROR_COLOUR   = Theme.hex(Theme.ERROR);
    private static final String WARNING_COLOUR = Theme.hex(Theme.WARNING);
    private static final String PICK_COLOUR    = Theme.hex(Theme.PLANE);

    /** The toolbar's FPS changes at most this often, so it can be read while playing. */
    private static final int FPS_LABEL_MS = 250;

    /** What the toolbar's badge says before a scan is open, and before Play. */
    private static final String FPS_NO_SCAN = "FPS";
    private static final String FPS_IDLE    = "Play to measure FPS";

    /** Projections run once before timing starts, so the first number is not the JIT warming up. */
    private static final int WARM_UP_PROJECTIONS = 5;

    /** Pictures of the picture panel: the cut, then the slice views 0..2 as pictures 1..3. */
    private static final int PICTURE_CUT = 0;
    private static final int PICTURES    = 4;

    /** Size of the window at the start. */
    private static final int DEFAULT_WIDTH  = 1500;
    private static final int DEFAULT_HEIGHT = 900;

    /** Names tried for a free video name: name_2 up to this. */
    private static final int MAX_NAME_TRIES = 999;

    /** Longest end of ffmpeg's log that a message quotes. */
    private static final int MAX_NOTE_CHARS = 90;

    /** The scan that is open. */
    private File            volumeFile;
    private Volume          volume;
    private IntensityWindow window;
    private VolumeGeometry  geometry;

    /** Nearest neighbour on the Swing thread: one frame takes about a millisecond. */
    private final Projector projector = Projector.create(Projector.Interpolation.NEAREST);

    /** The plane: as edited (its spec), and as the points the views and the video use. */
    private PlaneSpec spec;
    private Plane     plane;

    /** Playing: the planes of the video, the frame on show, and the direction (+1 or -1). */
    private List<Plane> playSequence;
    private int         playIndex;
    private int         playStep = 1;

    /** True while a scan loads or a video is exported. */
    private boolean busy;

    /** Counts the models asked for, so a model built for an older scan is dropped. */
    private int modelGeneration;

    /** The projection FPS while playing, and when the toolbar last showed it. */
    private final FpsMeter fps = new FpsMeter();
    private long           fpsShownAt;

    /** The cut's buffers, reused while the plane keeps its size (every frame of the video). */
    private Image2D cutImage;
    private byte[]  cutGrey;

    /**
     * Picking: the names, the points so far, the one being placed, the one row asked for (-1: all).
     */
    private String[] pickNames;
    private Vec3[]   picked;
    private int      pickIndex = -1;
    private int      pickOnly  = -1;

    /** Picking switched the picture panel from the cut to a slice view; it switches back after. */
    private boolean pickMovedPicture;

    /** The window. */
    private final JFrame frame = new JFrame(TITLE);

    /** One core is left to the window: the picture, its projection and the mouse. */
    private final VolumeView3D view3d = new VolumeView3D(this, Math.max(1, CORES - 1));

    /** The cut, and the three slice views. */
    private final SliceView         cutView = new SliceView();
    private final OrthoView.Panel[] orthos  = new OrthoView.Panel[3];

    /** The picture panel: its cards, its bar with title and count, and the picture on show. */
    private final CardLayout pictures     = new CardLayout();
    private final JPanel     pictureCards = new JPanel(pictures);
    private final JPanel     pictureBar   = new JPanel(new BorderLayout(6, 0));
    private final JLabel     pictureTitle = new JLabel(" ", JLabel.CENTER);
    private final JLabel     pictureCount = new JLabel(" ");
    private int              picture      = PICTURE_CUT;

    /** The status line, the export's progress, and the toolbar's FPS. */
    private final JLabel       status   = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final Badge        fpsBadge = new Badge();

    /** Step 1, the points: the 3 / 4 switch, a row per point, and the line for problems. */
    private final JToggleButton  threePoints = new JToggleButton("3 points (triangle)", true);
    private final JToggleButton  fourPoints  = new JToggleButton("4 points (trapezoid)");
    private final JLabel[]       rowNames    = new JLabel[4];
    private final JTextField[][] fields      = new JTextField[4][3];
    private final JButton[]      pickButtons = new JButton[4];
    private final JLabel         pointsInfo  = new JLabel(" ");
    private final JButton        pickAll     = new JButton("Pick on the views");
    private final JButton        fixButton   = new JButton("Make isosceles");

    /** Step 2, the video: its line, Play and Export, and the timer that plays it. */
    private final JLabel  videoInfo    = new JLabel(" ");
    private final JButton playButton   = new JButton("Play", Icons.play(false));
    private final JButton exportButton = new JButton("Export video...");
    private final Timer   playTimer;

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Opens the window on the Swing thread.
     * @param (File) initialVolume NRRD to open at start, or null.
     */
    public static void launch(final File initialVolume)
    {
        Theme.prepare();
        SwingUtilities.invokeLater(new Runnable()
        {
            /** Installs the look, opens the window and the scan given on the command line. */
            @Override
            public void run()
            {
                try
                {
                    Theme.install();
                }
                catch (Exception e)
                {
                    // Swing's default look still works; the app only looks plainer.
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
            /**
             * Shows the next frame of the video.
             * @param (ActionEvent) e The timer's tick.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                stepPlayback();
            }
        });
        buildWindow();
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /** Lays out the toolbar, the 3-D view, the picture panel and the control column. */
    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(960, 600));
        // Never larger than the screen (a laptop at 150 % scaling has room for about 1280 x 680).
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(new Dimension(Math.min(DEFAULT_WIDTH, screen.width), Math.min(DEFAULT_HEIGHT, screen.height)));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Theme.CHROME);

        for (int v = 0; v < 3; v++)
        {
            orthos[v] = new OrthoView.Panel(v, this, false);
        }
        JSplitPane views = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildModelPanel(), buildPicturePanel());
        views.setResizeWeight(0.5);
        views.setContinuousLayout(true);
        views.setBorder(null);
        views.setBackground(Theme.CHROME);

        JPanel controls = new ColumnPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(Theme.SURFACE);
        controls.setBorder(BorderFactory.createEmptyBorder(4, 18, 18, 18));
        controls.add(buildPointsPanel());
        controls.add(divider());
        controls.add(buildVideoPanel());
        JScrollPane controlScroll = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE));
        controlScroll.getViewport().setBackground(Theme.SURFACE);
        controlScroll.getVerticalScrollBar().setUnitIncrement(16);
        controlScroll.setPreferredSize(new Dimension(CONTROL_WIDTH, 600));

        JPanel statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setBackground(Theme.CHROME);
        statusBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(5, 14, 6, 14)));
        status.setForeground(Theme.TEXT_DIM);
        status.setFont(Theme.font(Theme.SMALL_SIZE));
        statusBar.add(status, BorderLayout.CENTER);
        progress.setVisible(false);
        progress.setStringPainted(true);
        progress.setPreferredSize(new Dimension(240, 16));
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
     * Builds the toolbar.
     * @return (JPanel) The toolbar: the app's mark, Open, the drop hint and the FPS.
     */
    private JPanel buildToolbar()
    {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        JLabel name = new JLabel("Ex1", Icons.mark(), JLabel.LEFT);
        name.setIconTextGap(9);
        name.setFont(Theme.semibold(15));
        name.setForeground(Theme.TEXT);
        left.add(name);
        left.add(Box.createHorizontalStrut(24));
        JButton open = button("Open NRRD...", new ActionListener()
        {
            /**
             * Opens a scan chosen in the system's file dialog.
             * @param (ActionEvent) e The click.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                chooseVolume();
            }
        });
        open.setToolTipText("Open a scan from anywhere on this computer");
        left.add(open);
        left.add(Box.createHorizontalStrut(12));
        JLabel orDrop = new JLabel("or drop a .nrrd file anywhere on the window");
        orDrop.setForeground(Theme.TEXT_FAINT);
        left.add(orDrop);

        fpsBadge.setToolTipText("<html>FPS: how many frames (cuts) per second this computer computes:<br>"
                + "the assignment's projection, nearest neighbour, one thread.<br>"
                + "Measured on the frames of the video while it plays, and written on every frame<br>"
                + "of an exported video. The video itself plays and is saved at " + VIDEO_FPS + " fps.</html>");
        showFpsIdle();
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.CHROME);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(8, 16, 8, 14)));
        bar.add(left, BorderLayout.CENTER);
        bar.add(fpsBadge, BorderLayout.EAST);
        return bar;
    }

    /**
     * Builds the left half of the views: the 3-D view under its header strip.
     * @return (JPanel) The 3-D view under its header.
     */
    private JPanel buildModelPanel()
    {
        JLabel title = new JLabel("3-D model, cut open by the plane");
        title.setFont(Theme.semibold(Theme.TEXT_SIZE));
        title.setForeground(Theme.TEXT);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.CHROME);
        header.setBorder(headerBorder(Theme.CHROME));
        header.setPreferredSize(new Dimension(10, HEADER_HEIGHT));
        header.add(title, BorderLayout.CENTER);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.WELL);
        // The same preferred width as the picture panel: the two views start half and half.
        p.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        p.add(header, BorderLayout.NORTH);
        p.add(view3d, BorderLayout.CENTER);
        return p;
    }

    /**
     * Builds the picture panel: one picture at a time - the cut (the triangle), then Axial, Coronal
     * and Sagittal - and arrows to flip through them; after the last one it comes back to the cut.
     * @return (JPanel) The panel.
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
            /**
             * Shows the previous picture.
             * @param (ActionEvent) e The click.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                flipPicture(-1);
            }
        });
        JButton next = arrowButton(true, "Next picture");
        next.addActionListener(new ActionListener()
        {
            /**
             * Shows the next picture.
             * @param (ActionEvent) e The click.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                flipPicture(1);
            }
        });
        pictureTitle.setForeground(Theme.TEXT);
        pictureTitle.setFont(Theme.semibold(Theme.TEXT_SIZE));
        pictureCount.setForeground(Theme.TEXT_DIM);
        pictureCount.setFont(Theme.font(Theme.SMALL_SIZE));
        JPanel middle = new JPanel(new BorderLayout(10, 0));
        middle.setOpaque(false);
        middle.add(pictureTitle, BorderLayout.CENTER);
        middle.add(pictureCount, BorderLayout.EAST);
        pictureBar.setBackground(Theme.CHROME);
        pictureBar.setPreferredSize(new Dimension(10, HEADER_HEIGHT));
        pictureBar.add(previous, BorderLayout.WEST);
        pictureBar.add(middle, BorderLayout.CENTER);
        pictureBar.add(next, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.WELL);
        p.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        p.add(pictureBar, BorderLayout.NORTH);
        p.add(pictureCards, BorderLayout.CENTER);
        showPicture(PICTURE_CUT);
        return p;
    }

    /**
     * Builds step 1 of the control column: the 3 / 4 points switch, the point rows (x, y, z, Pick),
     * Pick all, Make isosceles and the line for problems.
     * @return (JPanel) The "plane by points" editor: step 1 of the control column.
     */
    private JPanel buildPointsPanel()
    {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 5;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(12, 0, 2, 0);
        p.add(sectionTitle(1, "The plane"), g);
        g.gridy = 1;
        g.insets = new Insets(0, 29, 12, 0);
        p.add(caption("Choose 3 points, or 4 for a trapezoid."), g);

        ButtonGroup shape = new ButtonGroup();
        shape.add(threePoints);
        shape.add(fourPoints);
        ActionListener onShape = new ActionListener()
        {
            /**
             * Turns the plane into a triangle or a trapezoid, as the switch now says.
             * @param (ActionEvent) e The click on either half of the switch.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                changeShape(fourPoints.isSelected());
            }
        };
        threePoints.addActionListener(onShape);
        fourPoints.addActionListener(onShape);
        threePoints.putClientProperty(Theme.VARIANT, Theme.SEGMENT_FIRST);
        fourPoints.putClientProperty(Theme.VARIANT, Theme.SEGMENT_LAST);
        threePoints.setToolTipText("The plane of the assignment: an isosceles triangle a, b, c");
        fourPoints.setToolTipText("An isosceles trapezoid a1, a2, b, c");
        JPanel shapeRow = new JPanel(new GridLayout(1, 2, 0, 0));
        shapeRow.setOpaque(false);
        shapeRow.add(threePoints);
        shapeRow.add(fourPoints);
        g.gridy = 2;
        g.insets = new Insets(0, 0, 12, 0);
        p.add(shapeRow, g);

        g.gridwidth = 1;
        g.weightx = 0;
        g.gridy = 3;
        g.insets = new Insets(0, 3, 3, 3);
        String[] heads = {"", "x", "y", "z", ""};
        for (int c = 0; c < heads.length; c++)
        {
            g.gridx = c;
            JLabel head = new JLabel(heads[c], JLabel.CENTER);
            head.setForeground(Theme.TEXT_FAINT);
            head.setFont(Theme.font(Theme.SMALL_SIZE));
            p.add(head, g);
        }
        ActionListener apply = new ActionListener()
        {
            /**
             * Takes the typed points as the new plane.
             * @param (ActionEvent) e Enter in one of the fields.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                applyPointFields();
            }
        };
        g.insets = new Insets(3, 3, 3, 3);
        for (int r = 0; r < 4; r++)
        {
            g.gridy = 4 + r;
            g.gridx = 0;
            rowNames[r] = new JLabel("a");
            rowNames[r].setFont(Theme.semibold(Theme.TEXT_SIZE));
            rowNames[r].setForeground(Theme.PLANE);
            rowNames[r].setPreferredSize(new Dimension(22, 20));
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
            pickButtons[r] = new JButton("Pick");
            pickButtons[r].setMargin(new Insets(5, 11, 5, 11));
            pickButtons[r].setToolTipText("Click this point on the 3-D model or on a slice view");
            pickButtons[r].addActionListener(new ActionListener()
            {
                /**
                 * Waits for a click that places this row's point.
                 * @param (ActionEvent) e The click.
                 */
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    startPicking(row);
                }
            });
            p.add(pickButtons[r], g);
        }
        // The fix button only shows while the plane is doubtful (see showPointsInfo).
        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setOpaque(false);
        pickAll.setToolTipText("Click the points one after the other on the 3-D model or the slice views");
        pickAll.addActionListener(new ActionListener()
        {
            /**
             * Starts picking all the points one after the other, or stops picking.
             * @param (ActionEvent) e The click.
             */
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
            /**
             * Replaces a doubtful plane by the nearest isosceles one.
             * @param (ActionEvent) e The click.
             */
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
        g.gridy = 8;
        g.gridwidth = 5;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(10, 0, 8, 0);
        p.add(buttons, g);
        g.gridy = 9;
        g.insets = new Insets(0, 0, 0, 0);
        pointsInfo.setFont(Theme.font(Theme.SMALL_SIZE));
        pointsInfo.setForeground(Theme.TEXT_DIM);
        // Fixed size: a long message must wrap, not widen the panel (which would squeeze the fields).
        pointsInfo.setPreferredSize(new Dimension(INFO_WIDTH, INFO_HEIGHT));
        pointsInfo.setMinimumSize(new Dimension(INFO_WIDTH, INFO_HEIGHT));
        pointsInfo.setVerticalAlignment(JLabel.TOP);
        p.add(pointsInfo, g);
        showPointRows();
        return p;
    }

    /**
     * Builds step 2, the video: Play shows it, Export saves it. It is the chosen plane moved
     * through the whole scan, so it is ready as soon as a scan is open - nothing is collected by
     * hand.
     * @return (JPanel) The video panel: step 2 of the control column.
     */
    private JPanel buildVideoPanel()
    {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(12, 0, 2, 0);
        p.add(sectionTitle(2, "The video"), g);
        g.gridy = 1;
        g.insets = new Insets(0, 29, 12, 0);
        p.add(caption("The plane moving through the whole scan."), g);

        JPanel run = new JPanel(new GridLayout(1, 2, 8, 0));
        run.setOpaque(false);
        playButton.putClientProperty(Theme.VARIANT, Theme.PRIMARY);
        playButton.setFont(Theme.semibold(Theme.TEXT_SIZE));
        playButton.setIconTextGap(8);
        playButton.setToolTipText("Plays the video in the views, back and forth, at " + VIDEO_FPS + " frames per second");
        playButton.addActionListener(new ActionListener()
        {
            /**
             * Starts or stops the video.
             * @param (ActionEvent) e The click.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                togglePlay();
            }
        });
        exportButton.setToolTipText("Saves the video (one pass, first frame to last) as an MP4 file");
        exportButton.addActionListener(new ActionListener()
        {
            /**
             * Saves the video as an MP4 file.
             * @param (ActionEvent) e The click.
             */
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportVideo();
            }
        });
        run.add(playButton);
        run.add(exportButton);
        run.setPreferredSize(new Dimension(INFO_WIDTH, 36));
        g.gridy = 2;
        g.insets = new Insets(0, 0, 10, 0);
        p.add(run, g);

        videoInfo.setFont(Theme.font(Theme.SMALL_SIZE));
        videoInfo.setForeground(Theme.TEXT_DIM);
        videoInfo.setPreferredSize(new Dimension(INFO_WIDTH, INFO_HEIGHT + 16));
        videoInfo.setMinimumSize(new Dimension(INFO_WIDTH, INFO_HEIGHT + 16));
        videoInfo.setVerticalAlignment(JLabel.TOP);
        g.gridy = 3;
        g.insets = new Insets(0, 0, 0, 0);
        p.add(videoInfo, g);
        return p;
    }

    /**
     * Makes the title of a numbered section of the control column.
     * @param (int) step Its number in the order of work.
     * @param (String) title What the section is about.
     * @return (JLabel) A section title: the number in an amber ring, then the title.
     */
    private static JLabel sectionTitle(int step, String title)
    {
        JLabel l = new JLabel(title, Icons.step(step), JLabel.LEFT);
        l.setIconTextGap(9);
        l.setFont(Theme.semibold(14));
        l.setForeground(Theme.TEXT);
        return l;
    }

    /**
     * Makes the dim line under a section title.
     * @param (String) text One short line under a section title.
     * @return (JLabel) The line, dim.
     */
    private static JLabel caption(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Theme.SMALL_SIZE));
        l.setForeground(Theme.TEXT_DIM);
        return l;
    }

    /**
     * Makes the line between the two sections of the control column.
     * @return (JComponent) A thin line between the sections of the control column.
     */
    private static JComponent divider()
    {
        JPanel line = new JPanel();
        line.setBackground(Theme.LINE);
        line.setPreferredSize(new Dimension(10, 1));
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
        box.add(line, BorderLayout.CENTER);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 19));
        return box;
    }

    /**
     * Makes the border of a view's header strip: a coloured line on top, a thin line under it.
     * @param (Color) accent Colour of the line on top: which picture it is (the plane's amber for
     *                       the cut, the view's colour for a slice view).
     * @return (Border) The border of a view's header strip.
     */
    private static Border headerBorder(Color accent)
    {
        Border top    = BorderFactory.createMatteBorder(2, 0, 0, 0, accent);
        Border bottom = BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE);
        Border pad    = BorderFactory.createEmptyBorder(0, 12, 0, 6);
        return BorderFactory.createCompoundBorder(top, BorderFactory.createCompoundBorder(bottom, pad));
    }

    /**
     * Makes a previous / next button for the picture panel.
     * @param (boolean) right True for "next".
     * @param (String) tip Tooltip.
     * @return (JButton) A flat button with a drawn arrow.
     */
    private static JButton arrowButton(boolean right, String tip)
    {
        JButton b = new JButton(Icons.arrow(right));
        b.putClientProperty(Theme.VARIANT, Theme.GHOST);
        b.setToolTipText(tip);
        b.setMargin(new Insets(5, 8, 5, 8));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ==========================================
    //             THE PICTURE PANEL
    // ==========================================

    /**
     * Flips the picture panel to the next or previous picture.
     * @param (int) delta +1 next picture, -1 previous; both wrap around.
     */
    private void flipPicture(int delta)
    {
        showPicture((picture + delta + PICTURES) % PICTURES);
        pickMovedPicture = false;
    }

    /**
     * Shows one picture of the picture panel, with its title and the colour of its view.
     * @param (int) which PICTURE_CUT, or 1..3 for the Axial, Coronal and Sagittal views.
     */
    private void showPicture(int which)
    {
        picture = which;
        pictures.show(pictureCards, Integer.toString(which));
        Color  accent = Theme.PLANE;
        String title  = "The cut, one frame of the video";
        if (which != PICTURE_CUT)
        {
            int view = which - 1;
            accent = OrthoView.VIEW_COLOURS[view];
            title = VolumeGeometry.name(view) + ", " + VolumeGeometry.hint(view);
        }
        pictureBar.setBorder(headerBorder(accent));
        pictureTitle.setText(title);
        pictureCount.setText((which + 1) + " of " + PICTURES);
    }

    // ==========================================
    //                   VOLUME
    // ==========================================

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
            /**
             * Lets the dialog show only scans.
             * @param (File) dir Folder of the file.
             * @param (String) name File name.
             * @return (boolean) True for a .nrrd or .nhdr file.
             */
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
     * @param (File) file NRRD file.
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
            /**
             * Reads the scan and finds its grey window, off the Swing thread.
             * @return (Object[]) The volume and its window.
             * @throws (Exception) When the file cannot be read.
             */
            @Override
            protected Object[] doInBackground() throws Exception
            {
                Volume v = NrrdReader.read(file);
                IntensityWindow w = IntensityWindow.fromPercentiles(v,
                        IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
                return new Object[] {v, w};
            }

            /**
             * Shows the loaded scan in every view with the start triangle and starts building its
             * 3-D model, or explains why it did not load.
             */
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
                    warmUp(PlaneSpec.triangle(volume).toPlane());
                    applySpec(PlaneSpec.triangle(volume));
                    view3d.lookAtPlane(plane);
                    showPicture(PICTURE_CUT);
                    showFpsIdle();
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
        modelGeneration++;
        final Volume          v          = volume;
        final IntensityWindow w          = window;
        final int             generation = modelGeneration;
        view3d.setModel(null);
        status.setText("Building the 3-D model ...");
        SwingWorker<SolidModel, Void> worker = new SwingWorker<SolidModel, Void>()
        {
            /**
             * Builds the solid model, off the Swing thread.
             * @return (SolidModel) The model.
             */
            @Override
            protected SolidModel doInBackground()
            {
                return SolidModel.build(v, w);
            }

            /** Shows the model, unless another scan was opened while it was being built. */
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
     * Projects a plane a few times, untimed, so the FPS measured on the first frames of the video
     * is the algorithm's speed and not the Java compiler warming up.
     * @param (Plane) p A plane of the new volume.
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

    // ==========================================
    //               PLANE EDITING
    // ==========================================

    /**
     * Sets the current plane from a spec and updates everything.
     * @param (PlaneSpec) s New spec.
     */
    private void applySpec(PlaneSpec s)
    {
        spec = s;
        plane = s.toPlane();
        refreshViews();
    }

    /**
     * Shows an arbitrary plane (typed or clicked points, a frame of the video): drawn as it is.
     * @param (Plane) p Plane.
     */
    private void applyPlane(Plane p)
    {
        plane = p;
        spec = PlaneSpec.fromPlane(p);
        refreshViews();
    }

    /**
     * Switches between 3 and 4 points, keeping the plane's place and size.
     * @param (boolean) four True for 4 points (trapezoid).
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

    /**
     * Moves the plane along its normal (the wheel over the 3-D view).
     * @param (double) distance Voxels to move, signed.
     */
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
        List<Vec3>   pts   = planePoints(plane);
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
                pointsInfo.setText(html("Bad plane (a black frame in the video): " + bad, ERROR_COLOUR));
            }
            updateVideoInfo();
            return;
        }
        int w = plane.imageWidth();
        int h = plane.imageHeight();
        if (cutImage == null || cutImage.width() != w || cutImage.height() != h)
        {
            cutImage = new Image2D(w, h);
            cutGrey = new byte[w * h];
        }
        long t0 = System.nanoTime();
        projector.project(volume, plane, cutImage);
        long nanos = System.nanoTime() - t0;
        window.toGrey(cutImage, cutGrey, w, h, w, h);
        noteProjectionTime(nanos);
        cutView.setImage(cutGrey, w, h, framePrefix() + w + " x " + h + " pixels");
        cutView.setCornerLabels(plane.isTriangle());
        view3d.setPlane(plane, pts, names);
        showPointsInfo(plane.validate(Plane.DEFAULT_EPS));
        if (!playTimer.isRunning())
        {
            // While playing the plane only slides along its normal: the video stays the same.
            updateVideoInfo();
        }
        updateStatus();
    }

    /**
     * Starts the caption under the cut with the frame number while the video plays.
     * @return (String) "frame i of n   " while playing, else "".
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
     * @param (List<String>) problems Rule violations of the current plane.
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
        pointsInfo.setText(html("Not isosceles (drawn as given): " + problems.get(0) + ".", WARNING_COLOUR));
    }

    /**
     * Adds a frame's projection time to the FPS while the video plays (ADR-0014): shown on the
     * picture every frame, as in the exported video, and in the toolbar a few times a second, so
     * it can be read. A still picture is not measured: there is no frame rate to speak of.
     * @param (long) nanos Projection time of the frame just shown.
     */
    private void noteProjectionTime(long nanos)
    {
        if (!playTimer.isRunning())
        {
            return;
        }
        fps.add(nanos);
        String text = fps.text();
        cutView.setFps(text);
        long now = System.currentTimeMillis();
        if (now - fpsShownAt >= FPS_LABEL_MS || !fpsBadge.isLive())
        {
            fpsShownAt = now;
            fpsBadge.show(text, true);
        }
    }

    /**
     * Shows that nothing plays: the toolbar says how to measure the FPS, the picture shows none.
     */
    private void showFpsIdle()
    {
        if (volume == null)
        {
            fpsBadge.show(FPS_NO_SCAN, false);
        }
        else
        {
            fpsBadge.show(FPS_IDLE, false);
        }
        cutView.setFps(null);
    }

    // ==========================================
    //               POINTS EDITOR
    // ==========================================

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
                pointsInfo.setText(html("Point " + rowNames[r].getText() + ": type three numbers", ERROR_COLOUR));
                return;
            }
        }
        applyPlane(planeOf(p));
    }

    /**
     * Turns the points as the editor lists them into a plane.
     * @param (Vec3[]) p Points in editor order (a, b, c or a1, a2, b, c).
     * @return (Plane) The plane.
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
     * Reads the three fields of one point.
     * @param (int) r Row.
     * @return (Vec3) The typed point, or null when a field is not a number.
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
     * @param (int) only Row to pick alone, or -1 for all points in order.
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
     * Switches picking mode on or off in the 3-D view and the three slice views.
     * @param (boolean) on Picking mode for all views.
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
                + " (its arrows change the view, its slider the slice).", PICK_COLOUR));
    }

    /**
     * Places the point clicked while picking, then asks for the next one; after the last one the
     * points become the plane (made isosceles when the clicks were not exact).
     * @param (Vec3) p Voxel-index point.
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
        Plane  p0  = planeOf(pts);
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
     * Says, without numbers, what making the clicked plane isosceles changed.
     * @param (Plane) clicked The plane as clicked.
     * @return (String) What was changed to make it isosceles, in words.
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
     * @param (List<Vec3>) pts Markers.
     * @param (List<String>) names Labels.
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

    /**
     * Places the point being picked, or else moves the slice views through the clicked point.
     * @param (Vec3) index The clicked point in voxel indices.
     */
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

    /**
     * Places the point being picked, or else moves the other slice views through the clicked point.
     * @param (int) view The slice view clicked.
     * @param (Vec3) index The clicked point in voxel indices.
     */
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

    /**
     * Tells every slice view where the other two are, for their crosshair lines.
     * @param (int) view The view that moved.
     */
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
     * @param (Vec3) p Voxel-index point.
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

    // ==========================================
    //         THE VIDEO: PLAY AND EXPORT
    // ==========================================

    /**
     * Plans the video, frame by frame: the current plane swept through the scan, each frame rounded
     * to the precision a CSV row would keep (so the command line, given these planes, makes the
     * same frames); a plane the algorithm cannot draw becomes null, a black frame.
     * @return (Sweep.Pass) The planes in order and the frame of the current plane.
     */
    private Sweep.Pass videoPass()
    {
        Sweep.Pass sweep = Sweep.through(plane, volume.sizeX(), volume.sizeY(), volume.sizeZ());
        return new Sweep.Pass(videoFrames(sweep.planes), sweep.current);
    }

    /**
     * Prepares the planes of a video as frames.
     * @param (List<Plane>) source Planes of a video (null = black frame).
     * @return (List<Plane>) The same planes rounded to the CSV precision; one the algorithm cannot
     *                       draw is null.
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
        fps.reset();
        playButton.setText("Stop");
        playButton.setIcon(Icons.play(true));
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
        playButton.setIcon(Icons.play(false));
        showFpsIdle();
        // Redraw the last frame without its frame number.
        if (plane != null)
        {
            refreshViews();
        }
        updateEnabled();
    }

    /**
     * Plays one tick: shows the current frame, then steps to the next one, turning around at either
     * end, so the video runs back and forth (... n-1, n, n-1, ... 2, 1, 2 ...) until Stop.
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
        // One thread, as while playing: the FPS written on the frames is the same measure.
        final Projector     exportProjector = Projector.create(Projector.Interpolation.NEAREST);
        final FrameRenderer renderer;
        try
        {
            renderer = new FrameRenderer(volume, planes, window, exportProjector);
            renderer.setShowFps(true);
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
            /** Projection times of the frames projected (not the black ones), and their count. */
            private final long[] frameNanos = new long[renderer.frameCount()];
            private int          projected;

            /**
             * Writes the video, off the Swing thread; when ffmpeg fails, the built-in encoder
             * writes it again.
             * @return (String) A note for the summary: empty, or why the built-in encoder was used.
             * @throws (Exception) When the video cannot be written at all.
             */
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
             * Renders and writes every frame, and records each projection's time for the summary.
             * @param (VideoWriter) out An open writer; closed at the end, also on failure.
             * @throws (IOException) When a frame cannot be written.
             */
            private void writeAll(VideoWriter out) throws IOException
            {
                projected = 0;
                try
                {
                    for (int i = 0; i < renderer.frameCount(); i++)
                    {
                        out.writeFrame(renderer.render(i));
                        if (renderer.lastProjectionNanos() > 0)
                        {
                            frameNanos[projected] = renderer.lastProjectionNanos();
                            projected++;
                        }
                        publish(Integer.valueOf(i + 1));
                    }
                }
                finally
                {
                    out.close();
                }
            }

            /**
             * Moves the progress bar.
             * @param (List<Integer>) chunks Frames written so far; the last one counts.
             */
            @Override
            protected void process(List<Integer> chunks)
            {
                progress.setValue(chunks.get(chunks.size() - 1).intValue());
            }

            /**
             * Ends the export: the summary with the run's FPS and an offer to open the video, or
             * the reason it failed (a half-written file is deleted).
             */
            @Override
            protected void done()
            {
                shutdown(exportProjector);
                progress.setVisible(false);
                setBusy(false, " ");
                try
                {
                    String note  = get();
                    String speed = "";
                    if (projected > 0)
                    {
                        speed = "\nThe projection ran at " + FpsMeter.format(FpsMeter.fpsOf(frameNanos, projected))
                                + " (the median frame, one thread); each frame shows its FPS in the corner.";
                    }
                    int answer = JOptionPane.showConfirmDialog(frame,
                            "Saved " + renderer.frameCount() + " frames (" + renderer.width() + " x " + renderer.height()
                                    + " pixels, " + seconds(renderer.frameCount()) + " s at " + VIDEO_FPS + " fps) to\n"
                                    + target.getAbsolutePath() + speed + note + "\n\nOpen the video now?",
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
     * @param (int) frames Number of frames, for the dialog title.
     * @return (File) The file, or null when cancelled.
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
     * Proposes a file name that is not taken yet, so an export never replaces an earlier video.
     * @param (File) folder Where the file goes.
     * @param (String) base Name without extension.
     * @param (String) ext Extension with dot.
     * @return (File) base.ext, or base_2.ext, base_3.ext ... - the first that does not exist yet.
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
     * Checks whether an existing file can be replaced: on Windows a video open in a player cannot.
     * @param (File) f An existing file.
     * @return (boolean) Whether it can be opened for writing (not locked by another program, not
     *                   read-only).
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
     * Explains a failed export in plain words, with the technical detail below for a screenshot.
     * @param (File) target The video that was being written.
     * @param (Throwable) t What went wrong.
     * @return (String) What happened in plain words, then the technical message (for a screenshot).
     */
    static String exportProblem(File target, Throwable t)
    {
        String text   = message(t).toLowerCase(Locale.ROOT);
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
        videoInfo.setText(html(frames + " frames, " + seconds(frames) + " s at " + VIDEO_FPS + " fps. Play runs it back and"
                + " forth and shows the FPS; Export saves it once, with the FPS on every frame.", HINT_COLOUR));
    }

    /**
     * Shortens a long message (ffmpeg's log) to its last line.
     * @param (String) text A message, maybe long and over several lines.
     * @return (String) Its last line, at most about one dialog line long.
     */
    private static String shortText(String text)
    {
        String[] lines = text.trim().split("\\R");
        String   last  = lines[lines.length - 1].trim();
        if (last.length() > MAX_NOTE_CHARS)
        {
            last = "..." + last.substring(last.length() - MAX_NOTE_CHARS);
        }
        return last;
    }

    /**
     * Gives a video's length in seconds, as the dialogs show it.
     * @param (int) frames Number of frames.
     * @return (String) The video's length in seconds, e.g. "13" or "1.6".
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
     * Stops the threads of a projector made for one export (a one-thread projector has none).
     * @param (Projector) p A projector made for one export.
     */
    private static void shutdown(Projector p)
    {
        if (p instanceof ParallelProjector)
        {
            ((ParallelProjector) p).shutdown();
        }
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Lists the plane's points in the order the editor shows them.
     * @param (Plane) p Plane.
     * @return (List<Vec3>) Its points in editor order: a, b, c or a1, a2, b, c.
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
     * Lists the names of the plane's points in the editor's order.
     * @param (Plane) p Plane.
     * @return (List<String>) The labels of its points in editor order.
     */
    private static List<String> pointNames(Plane p)
    {
        List<String> names = new ArrayList<String>();
        String[]     src   = TRIANGLE_NAMES;
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
     * Wraps a message in HTML so the info label breaks it into lines instead of growing wider.
     * @param (String) text Message.
     * @param (String) colour CSS colour, or null for the default.
     * @return (String) The message as wrapping HTML for the narrow info label.
     */
    private static String html(String text, String colour)
    {
        String safe  = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String style = "width:" + HTML_WIDTH + "px";
        if (colour != null)
        {
            style += ";color:" + colour;
        }
        return "<html><body style='" + style + "'>" + safe + "</body></html>";
    }

    /**
     * Formats a coordinate for a field: two decimals, none for whole numbers.
     * @param (double) v Coordinate.
     * @return (String) Short text for a field.
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
     * Marks the app busy (loading, exporting) or free again: wait cursor, status text, controls.
     * @param (boolean) b Busy flag.
     * @param (String) text Status text.
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
        for (int r = 0; r < pickButtons.length; r++)
        {
            pickButtons[r].setEnabled(haveVolume);
        }
        updateVideoInfo();
        updateStatus();
    }

    /**
     * Shows or hides a control and lays its panel out again (hiding alone leaves a gap).
     * @param (Component) c The control.
     * @param (boolean) on Visible or not.
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

    /** Shows the status line of the normal state. */
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
     * Makes a button with its action.
     * @param (String) text Label.
     * @param (ActionListener) action Click handler.
     * @return (JButton) The button.
     */
    private static JButton button(String text, ActionListener action)
    {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    /**
     * Gives a button the compact padding of the control column.
     * @param (JButton) b Button.
     * @return (JButton) The same button with narrow margins (the control column is narrow).
     */
    private static JButton small(JButton b)
    {
        b.setMargin(new Insets(6, 10, 6, 10));
        return b;
    }

    /**
     * Gives the scan's file name without its extension, for the proposed video name.
     * @return (String) Name of the volume file without extension.
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
     * Adds the extension to a chosen file name when the user left it out.
     * @param (File) f Chosen file.
     * @param (String) ext Extension with dot.
     * @return (File) The file, with the extension appended when it was missing.
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
     * Chooses the folder the Open dialog starts in.
     * @return (File) Where the open dialog starts: the current scan's folder, else Downloads, else
     *                home.
     */
    private File openFolder()
    {
        if (volumeFile != null)
        {
            return volumeFile.getAbsoluteFile().getParentFile();
        }
        File home      = new File(System.getProperty("user.home"));
        File downloads = new File(home, "Downloads");
        if (downloads.isDirectory())
        {
            return downloads;
        }
        return home;
    }

    /**
     * Tells whether the app runs on Windows.
     * @return (boolean) True on Windows (its file dialog filters by a pattern in the name box).
     */
    private static boolean onWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Opens the saved video with the system's default player.
     * @param (File) f The saved video, to open with the system's default player.
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
     * Shows an error dialog.
     * @param (String) title Dialog title.
     * @param (String) text Message.
     */
    private void error(String title, String text)
    {
        JOptionPane.showMessageDialog(frame, text, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Gets a readable message from an error (its class name when it has none).
     * @param (Throwable) t Error.
     * @return (String) Readable message.
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

    /** The control column: as wide as the scroll pane, only scrolls vertically. */
    private static final class ColumnPanel extends JPanel implements Scrollable
    {
        /** Version of the serialized form (Swing components are Serializable). */
        private static final long serialVersionUID = 1L;

        /**
         * Asks for room for the whole column.
         * @return (Dimension) Its preferred size.
         */
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        /**
         * Gives the step the mouse wheel scrolls by.
         * @param (Rectangle) visible The part shown.
         * @param (int) orientation Always vertical here.
         * @param (int) direction Up or down.
         * @return (int) Pixels per step.
         */
        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
        {
            return 16;
        }

        /**
         * Gives how far a click beside the thumb scrolls: one screenful.
         * @param (Rectangle) visible The part shown.
         * @param (int) orientation Always vertical here.
         * @param (int) direction Up or down.
         * @return (int) The height shown.
         */
        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
        {
            return visible.height;
        }

        /**
         * Keeps the column exactly as wide as the scroll pane, so it never scrolls sideways.
         * @return (boolean) True.
         */
        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        /**
         * Lets the column be taller than the scroll pane, so it scrolls up and down.
         * @return (boolean) False.
         */
        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    /** Accepts a .nrrd/.nhdr volume dragged from the file manager. */
    private final class FileDrop extends TransferHandler
    {
        /** Version of the serialized form (Swing components are Serializable). */
        private static final long serialVersionUID = 1L;

        /**
         * Accepts files dragged in while the app is not busy.
         * @param (TransferSupport) support The drag.
         * @return (boolean) True when a list of files is offered and nothing else is running.
         */
        @Override
        public boolean canImport(TransferSupport support)
        {
            return !busy && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        /**
         * Opens the dropped scan; a CSV file gets a note on the command line, anything else a
         * message.
         * @param (TransferSupport) support The drop.
         * @return (boolean) True when a scan is being opened.
         */
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
                File   f    = (File) files.get(0);
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
