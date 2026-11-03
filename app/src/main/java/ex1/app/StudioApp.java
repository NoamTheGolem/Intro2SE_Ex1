package ex1.app;

import ex1.core.Image2D;
import ex1.core.IntensityWindow;
import ex1.core.ParallelProjector;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.io.NrrdReader;
import ex1.io.PlaneCsvReader;
import ex1.io.PlaneCsvWriter;
import ex1.io.VideoWriter;
import ex1.io.VideoWriters;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The application (the deliverable of Ex1): open a scan (NRRD), see it as a solid object, choose
 * a plane by 3 points (or 4 for a trapezoid) - typed, or clicked on the views - and see the object
 * cut open along it (the inside shows on the cut face) and the plane's projection, the image of
 * the assignment. The three standard views of 3D Slicer (axial, coronal, sagittal) show where the
 * plane crosses the scan. Planes are collected into a use case (or loaded from the lecturer's
 * CSV), played, and exported as the video, with the CSV next to it. The command-line demo (Ex0) is
 * the batch form of the same pipeline and reproduces an export byte for byte.
 */
public final class StudioApp implements VolumeView3D.Host, OrthoView.Host
{
    private static final String TITLE = "Ex1 - cut a 3-D scan with a plane";
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final int PLAY_FPS = 15;
    private static final int DEFAULT_EXPORT_FPS = 15;
    private static final int MAX_LIST_LINES_IN_DIALOG = 16;
    private static final int SLIDER_SCALE = 10;
    private static final double NANOS_PER_MS = 1e6;
    private static final String[] TRIANGLE_NAMES = {"a", "b", "c"};
    private static final String[] TRAPEZOID_NAMES = {"a1", "a2", "b", "c"};
    private static final int CONTROL_WIDTH = 430;

    /** One entry of the use-case list: a plane (null = black frame) and where it came from. */
    static final class Keyframe
    {
        final Plane plane;
        final String origin;
        final String note;

        /**
         * @param (Plane) plane plane or null
         * @param (String) origin e.g. "row 3" or "added"
         * @param (String) note first warning of the row, or null
         */
        Keyframe(Plane plane, String origin, String note)
        {
            this.plane = plane;
            this.origin = origin;
            this.note = note;
        }

        @Override
        public String toString()
        {
            if (plane == null)
            {
                return origin + ": black frame - " + note;
            }
            String text = origin + ": " + PlaneSpec.fromPlane(plane).label();
            if (note != null)
            {
                text += "  (!)";
            }
            return text;
        }
    }

    // ---- model
    private File volumeFile;
    private Volume volume;
    private IntensityWindow window;
    private VolumeGeometry geometry;
    private Projector projector;
    private PlaneSpec spec;
    private Plane plane;
    private File matchingCsv;
    private final DefaultListModel<Keyframe> keyframes = new DefaultListModel<Keyframe>();
    private List<Plane> playSequence;
    private int playIndex;
    private boolean busy;
    private boolean updatingSliders;
    private int modelGeneration;

    // ---- picking
    private String[] pickNames;
    private Vec3[] picked;
    private int pickIndex = -1;
    private int pickOnly = -1;

    // ---- views
    private final JFrame frame = new JFrame(TITLE);
    private final VolumeView3D view3d = new VolumeView3D(this, CORES);
    private final SliceView cutView = new SliceView();
    private final OrthoView.Panel[] orthos = new OrthoView.Panel[3];
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JComboBox<String> interpBox = new JComboBox<String>(new String[] {"Nearest", "Trilinear"});
    private final JCheckBox cutOpenBox = new JCheckBox("Cut open", true);
    private final JSlider surface = new JSlider(1, 254, 40);
    private final JLabel surfaceValue = new JLabel("40");

    // ---- points editor
    private final JRadioButton threePoints = new JRadioButton("3 points (triangle)", true);
    private final JRadioButton fourPoints = new JRadioButton("4 points (trapezoid)");
    private final JLabel[] rowNames = new JLabel[4];
    private final JTextField[][] fields = new JTextField[4][3];
    private final JButton[] pickButtons = new JButton[4];
    private final JLabel pointsInfo = new JLabel(" ");
    private final JButton pickAll = new JButton("Pick on the views");

    // ---- move / turn
    private final JSlider sx = slider(0, 100, 50);
    private final JSlider sy = slider(0, 100, 50);
    private final JSlider sz = slider(0, 100, 50);
    private final JSlider sYaw = slider(-180, 180, 0);
    private final JSlider sPitch = slider(-90, 90, 0);
    private final JSlider sRoll = slider(-180, 180, 0);
    private final JSlider sWidth = slider(2, 100, 50);
    private final JSlider sHeight = slider(2, 100, 50);
    private final JSlider sTop = slider(0, 100, 100);
    private final JLabel[] sliderValues = new JLabel[9];
    private final JTextField csvRow = new JTextField();

    // ---- use case
    private final JList<Keyframe> list = new JList<Keyframe>(keyframes);
    private final JSpinner betweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
    private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_EXPORT_FPS, 1, 60, 1));
    private final JButton playButton = new JButton("Play");
    private final JButton matchingButton = new JButton("Load its use case");
    private final Timer playTimer;

    // ===================================================================== ENTRY

    /**
     * Opens the window on the Swing thread.
     *
     * @param (File) initialVolume NRRD to open at start, or null
     * @param (File) initialCsv CSV of planes to load after the volume, or null
     */
    public static void launch(final File initialVolume, final File initialCsv)
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
                    app.openVolume(initialVolume, initialCsv);
                }
            }
        });
    }

    /** Builds the window; call on the Swing thread. */
    private StudioApp()
    {
        playTimer = new Timer(1000 / PLAY_FPS, new ActionListener()
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

    /** Lays out the toolbar, the five views and the control column. */
    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 700));
        frame.setSize(new Dimension(1500, 920));
        frame.setLocationRelativeTo(null);

        for (int v = 0; v < 3; v++)
        {
            orthos[v] = new OrthoView.Panel(v, this);
        }
        JSplitPane topRow = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, titled(view3d, "3-D model - cut open by the plane"),
                titled(cutView, "The cut - projection of the plane (one video frame)"));
        topRow.setResizeWeight(0.58);
        topRow.setBorder(null);
        JPanel slices = new JPanel(new GridLayout(1, 3, 3, 0));
        for (int v = 0; v < 3; v++)
        {
            slices.add(orthos[v]);
        }
        JSplitPane views = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topRow, slices);
        views.setResizeWeight(0.62);
        views.setBorder(null);

        JPanel controls = new ColumnPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        controls.add(buildPointsPanel());
        controls.add(buildMovePanel());
        controls.add(buildUseCasePanel());
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
        rebuildProjector();
        updateEnabled();
        status.setText("Open a scan: \"Open NRRD...\", \"Samples\", or drop a .nrrd file on the window.");
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
        final JButton samples = new JButton("Samples");
        samples.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                showSamplesMenu(samples);
            }
        });
        top.add(samples);
        top.add(Box.createHorizontalStrut(10));
        top.add(button("Load use case (CSV)...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                chooseCsv();
            }
        }));
        top.add(button("Save CSV...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                saveCsv();
            }
        }));
        JButton export = button("Export video...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportVideo();
            }
        });
        export.setFont(export.getFont().deriveFont(Font.BOLD));
        top.add(export);
        top.add(Box.createHorizontalStrut(10));
        top.add(new JLabel("Sampling:"));
        top.add(interpBox);
        interpBox.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                rebuildProjector();
                refreshViews();
            }
        });
        top.add(Box.createHorizontalStrut(10));
        cutOpenBox.setToolTipText("Remove the half of the object in front of the plane, so its inside shows");
        cutOpenBox.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                view3d.setCutOpen(cutOpenBox.isSelected());
            }
        });
        top.add(cutOpenBox);
        top.add(new JLabel("Surface:"));
        surface.setPreferredSize(new Dimension(130, 24));
        surface.setToolTipText("Grey level where the object's surface is (Otsu's threshold by default)");
        surface.setEnabled(false);
        surface.addChangeListener(new ChangeListener()
        {
            @Override
            public void stateChanged(ChangeEvent e)
            {
                surfaceValue.setText(Integer.toString(surface.getValue()));
                if (!surface.getValueIsAdjusting())
                {
                    changeSurface(surface.getValue());
                }
            }
        });
        top.add(surface);
        top.add(surfaceValue);
        return top;
    }

    /**
     * @return the "plane by points" editor
     */
    private JPanel buildPointsPanel()
    {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("1. The plane - its points (voxel x, y, z)"));
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
                showPointRows();
                stopPicking();
                if (plane != null && plane.isTriangle() == fourPoints.isSelected())
                {
                    // Switch the current plane to the other shape, keeping its place and size.
                    double top = 0.0;
                    if (fourPoints.isSelected())
                    {
                        top = 0.5;
                    }
                    applySpec(spec.withShape(spec.width, top, spec.height));
                }
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
        JPanel buttons = new JPanel(new GridLayout(1, 3, 3, 0));
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
        buttons.add(small(pickAll));
        buttons.add(small(button("Apply", apply)));
        buttons.add(small(button("Make isosceles", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (plane != null && plane.isRenderable())
                {
                    applyPlane(PlaneSpec.makeIsosceles(plane));
                }
            }
        })));
        g.gridx = 0;
        g.gridy = 6;
        g.gridwidth = 5;
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(buttons, g);
        g.gridy = 7;
        g.fill = GridBagConstraints.HORIZONTAL;
        pointsInfo.setFont(pointsInfo.getFont().deriveFont(Font.PLAIN, 11f));
        // Fixed size: a long message must wrap, not widen the panel (which would squeeze the fields).
        pointsInfo.setPreferredSize(new Dimension(CONTROL_WIDTH - 60, 30));
        pointsInfo.setMinimumSize(new Dimension(CONTROL_WIDTH - 60, 30));
        pointsInfo.setVerticalAlignment(JLabel.TOP);
        p.add(pointsInfo, g);
        showPointRows();
        return p;
    }

    /**
     * @return the "move / turn the plane" editor: presets and sliders
     */
    private JPanel buildMovePanel()
    {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("2. Move / turn the plane"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 3, 0, 3);
        g.anchor = GridBagConstraints.WEST;
        JPanel presets = new JPanel(new GridLayout(2, 3, 3, 3));
        presets.add(presetButton("Axial", 0));
        presets.add(presetButton("Coronal", 1));
        presets.add(presetButton("Sagittal", 2));
        presets.add(presetButton("Triangle", 3));
        presets.add(presetButton("Oblique", 4));
        JPanel through = new JPanel(new GridLayout(1, 2, 2, 0));
        presets.add(through);
        through.add(small(button("< back", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                slidePlane(-5);
            }
        })));
        through.add(small(button("fwd >", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                slidePlane(5);
            }
        })));
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 3;
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(presets, g);
        g.gridwidth = 1;
        String[] names = {"Centre x", "Centre y", "Centre z", "Yaw", "Pitch", "Roll", "Width", "Height", "Top / base"};
        JSlider[] sliders = {sx, sy, sz, sYaw, sPitch, sRoll, sWidth, sHeight, sTop};
        ChangeListener onSlide = new ChangeListener()
        {
            @Override
            public void stateChanged(ChangeEvent e)
            {
                if (!updatingSliders)
                {
                    specFromSliders();
                }
            }
        };
        for (int i = 0; i < sliders.length; i++)
        {
            g.gridy = 1 + i;
            g.gridx = 0;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            p.add(new JLabel(names[i]), g);
            g.gridx = 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            sliders[i].setPreferredSize(new Dimension(150, 20));
            sliders[i].addChangeListener(onSlide);
            p.add(sliders[i], g);
            g.gridx = 2;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            sliderValues[i] = new JLabel("0");
            sliderValues[i].setPreferredSize(new Dimension(44, 16));
            p.add(sliderValues[i], g);
        }
        g.gridx = 0;
        g.gridy = 10;
        g.gridwidth = 3;
        g.fill = GridBagConstraints.HORIZONTAL;
        csvRow.setEditable(false);
        csvRow.setToolTipText("The current plane as a row of the course CSV (points in the order a, b, c or a1, b, a2, c)");
        JPanel rowPanel = new JPanel(new BorderLayout(4, 0));
        rowPanel.add(new JLabel("CSV row:"), BorderLayout.WEST);
        rowPanel.add(csvRow, BorderLayout.CENTER);
        p.add(rowPanel, g);
        return p;
    }

    /**
     * @return the use-case list with its buttons
     */
    private JPanel buildUseCasePanel()
    {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("3. Use case - the frames of the video"));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(7);
        list.addListSelectionListener(new ListSelectionListener()
        {
            @Override
            public void valueChanged(ListSelectionEvent e)
            {
                if (!e.getValueIsAdjusting() && !playTimer.isRunning())
                {
                    showSelectedKeyframe();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(CONTROL_WIDTH - 40, 130));
        p.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 3, 3, 3));
        buttons.add(button("Add current", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (plane != null)
                {
                    keyframes.addElement(new Keyframe(plane, "added", firstProblem(plane)));
                    list.setSelectedIndex(keyframes.size() - 1);
                    list.ensureIndexIsVisible(keyframes.size() - 1);
                    updateEnabled();
                }
            }
        }));
        buttons.add(button("Replace", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                int i = list.getSelectedIndex();
                if (i >= 0 && plane != null)
                {
                    keyframes.set(i, new Keyframe(plane, keyframes.get(i).origin, firstProblem(plane)));
                }
            }
        }));
        buttons.add(button("Remove", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                int i = list.getSelectedIndex();
                if (i >= 0)
                {
                    keyframes.remove(i);
                    updateEnabled();
                }
            }
        }));
        buttons.add(button("Up", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                move(-1);
            }
        }));
        buttons.add(button("Down", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                move(1);
            }
        }));
        buttons.add(button("Clear", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                keyframes.clear();
                updateEnabled();
            }
        }));
        JPanel north = new JPanel(new BorderLayout());
        matchingButton.setEnabled(false);
        matchingButton.setToolTipText("The lecturer's CSV for this scan, when it is next to it (csv/<name>.csv) or in data/course");
        matchingButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (matchingCsv != null)
                {
                    loadCsv(matchingCsv);
                }
            }
        });
        north.add(matchingButton, BorderLayout.CENTER);
        p.add(north, BorderLayout.NORTH);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(buttons);
        JPanel play = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        play.add(new JLabel("Frames between:"));
        play.add(betweenSpinner);
        play.add(new JLabel("fps:"));
        play.add(fpsSpinner);
        playButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                togglePlay();
            }
        });
        play.add(playButton);
        south.add(play);
        JButton export = button("Export video (MP4 + CSV)...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportVideo();
            }
        });
        export.setAlignmentX(Component.LEFT_ALIGNMENT);
        export.setFont(export.getFont().deriveFont(Font.BOLD));
        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        exportRow.add(export);
        south.add(exportRow);
        p.add(south, BorderLayout.SOUTH);
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
        l.setBackground(new Color(0x2A2E36));
        l.setForeground(new Color(0xE6E6E6));
        l.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        p.add(l, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------------ volume

    /** Shows a file chooser for NRRD files. */
    private void chooseVolume()
    {
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Open a 3-D scan");
        chooser.setFileFilter(new FileNameExtensionFilter("NRRD volumes (*.nrrd, *.nhdr)", "nrrd", "nhdr"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
        {
            openVolume(chooser.getSelectedFile(), null);
        }
    }

    /**
     * @param (Component) anchor the button the menu opens under
     */
    private void showSamplesMenu(Component anchor)
    {
        JPopupMenu menu = new JPopupMenu();
        String[][] samples = {
            {"MRHead (MRI head) + the lecturer's example CSV", "MRHead.nrrd", "Ex0.csv"},
            {"Dice + the lecturer's edge cases", "dice.nrrd", "course/edge_cases.csv"},
            {"Spheres phantom (500^3, performance case)", "phantom_spheres_500.nrrd", ""},
        };
        for (int i = 0; i < samples.length; i++)
        {
            final String nrrd = samples[i][1];
            final String csv = samples[i][2];
            JMenuItem item = new JMenuItem(samples[i][0]);
            item.setEnabled(findInProject("data", nrrd) != null);
            item.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    File csvFile = null;
                    if (!csv.isEmpty())
                    {
                        csvFile = findInProject("data", csv);
                    }
                    openVolume(findInProject("data", nrrd), csvFile);
                }
            });
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    /**
     * Loads a volume in the background, shows it, then builds the 3-D model.
     *
     * @param (File) file NRRD file
     * @param (File) csvAfter CSV to load once the volume is in, or null
     */
    void openVolume(final File file, final File csvAfter)
    {
        if (busy || file == null)
        {
            return;
        }
        playTimer.stop();
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
                    configureSliderRanges();
                    view3d.setVolume(volume, window, geometry);
                    for (int v = 0; v < 3; v++)
                    {
                        orthos[v].view().setVolume(volume, window, geometry);
                        orthos[v].sync();
                    }
                    sliceChanged(0);
                    keyframes.clear();
                    applySpec(PlaneSpec.triangle(volume));
                    view3d.lookAtPlane(plane);
                    findMatchingCsv(file);
                    buildModel(-1);
                    updateEnabled();
                    if (csvAfter != null && csvAfter.isFile())
                    {
                        loadCsv(csvAfter);
                    }
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

    /**
     * Builds (or re-thresholds) the solid model in the background.
     *
     * @param (int) threshold grey level, or -1 for the automatic one
     */
    private void buildModel(final int threshold)
    {
        final Volume v = volume;
        final IntensityWindow w = window;
        final SolidModel base = view3d.model();
        final int generation = ++modelGeneration;
        if (threshold < 0)
        {
            view3d.setModel(null);
        }
        status.setText("Building the 3-D model ...");
        SwingWorker<SolidModel, Void> worker = new SwingWorker<SolidModel, Void>()
        {
            @Override
            protected SolidModel doInBackground()
            {
                if (threshold >= 0 && base != null)
                {
                    return base.withThreshold(threshold);
                }
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
                    SolidModel m = get();
                    view3d.setModel(m);
                    surface.setValue(m.threshold());
                    surfaceValue.setText(Integer.toString(m.threshold()));
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
     * @param (int) t new surface threshold from the toolbar
     */
    private void changeSurface(int t)
    {
        SolidModel m = view3d.model();
        if (m == null || volume == null || t == m.threshold())
        {
            return;
        }
        buildModel(t);
    }

    /**
     * Looks for the lecturer's use case of this scan: csv/NAME.csv or NAME.csv next to it, else
     * data/course/NAME.csv of the project (MRHead uses head.csv).
     *
     * @param (File) nrrd the scan
     */
    private void findMatchingCsv(File nrrd)
    {
        String base = nrrd.getName().replaceFirst("\\.[^.]*$", "");
        List<String> names = new ArrayList<String>();
        names.add(base + ".csv");
        if (base.toLowerCase(Locale.ROOT).startsWith("mrhead"))
        {
            names.add("head.csv");
        }
        if (base.toLowerCase(Locale.ROOT).startsWith("dice"))
        {
            names.add("edge_cases.csv");
        }
        matchingCsv = null;
        File dir = nrrd.getAbsoluteFile().getParentFile();
        for (int i = 0; i < names.size() && matchingCsv == null; i++)
        {
            File[] candidates = {new File(new File(dir, "csv"), names.get(i)), new File(dir, names.get(i)),
                findInProject("data/course", names.get(i))};
            for (int k = 0; k < candidates.length && matchingCsv == null; k++)
            {
                if (candidates[k] != null && candidates[k].isFile())
                {
                    matchingCsv = candidates[k];
                }
            }
        }
        if (matchingCsv != null)
        {
            matchingButton.setText("Load its use case: " + matchingCsv.getName());
        }
        else
        {
            matchingButton.setText("Load its use case");
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
        slidersFromSpec();
        refreshViews();
    }

    /**
     * Shows an arbitrary plane (a CSV row, typed or clicked points): drawn as it is; the sliders
     * show the closest spec.
     *
     * @param (Plane) p plane
     */
    private void applyPlane(Plane p)
    {
        plane = p;
        spec = PlaneSpec.fromPlane(p);
        slidersFromSpec();
        refreshViews();
    }

    /** Reads the sliders into a new spec and refreshes. */
    private void specFromSliders()
    {
        if (volume == null)
        {
            return;
        }
        double top = sTop.getValue() / 100.0;
        if (threePoints.isSelected())
        {
            top = 0.0;
        }
        PlaneSpec s = new PlaneSpec(
                sx.getValue() / (double) SLIDER_SCALE, sy.getValue() / (double) SLIDER_SCALE, sz.getValue() / (double) SLIDER_SCALE,
                sYaw.getValue(), sPitch.getValue(), sRoll.getValue(),
                sWidth.getValue(), top, sHeight.getValue());
        spec = s;
        plane = s.toPlane();
        showSliderValues();
        refreshViews();
    }

    /** Pushes the spec into the sliders without triggering a rebuild. */
    private void slidersFromSpec()
    {
        updatingSliders = true;
        sx.setValue((int) Math.round(spec.cx * SLIDER_SCALE));
        sy.setValue((int) Math.round(spec.cy * SLIDER_SCALE));
        sz.setValue((int) Math.round(spec.cz * SLIDER_SCALE));
        sYaw.setValue((int) Math.round(spec.yaw));
        sPitch.setValue((int) Math.round(spec.pitch));
        sRoll.setValue((int) Math.round(spec.roll));
        sWidth.setValue((int) Math.round(spec.width));
        sHeight.setValue((int) Math.round(spec.height));
        sTop.setValue((int) Math.round(spec.topRatio * 100));
        updatingSliders = false;
        showSliderValues();
    }

    /** Slider ranges depend on the volume size; the centre may leave the box by half a size. */
    private void configureSliderRanges()
    {
        int diag = (int) Math.ceil(Math.sqrt((double) volume.sizeX() * volume.sizeX()
                + (double) volume.sizeY() * volume.sizeY() + (double) volume.sizeZ() * volume.sizeZ()));
        updatingSliders = true;
        sx.setMinimum(-volume.sizeX() * SLIDER_SCALE / 2);
        sx.setMaximum(volume.sizeX() * SLIDER_SCALE * 3 / 2);
        sy.setMinimum(-volume.sizeY() * SLIDER_SCALE / 2);
        sy.setMaximum(volume.sizeY() * SLIDER_SCALE * 3 / 2);
        sz.setMinimum(-volume.sizeZ() * SLIDER_SCALE / 2);
        sz.setMaximum(volume.sizeZ() * SLIDER_SCALE * 3 / 2);
        sWidth.setMaximum(2 * diag);
        sHeight.setMaximum(2 * diag);
        updatingSliders = false;
    }

    /** Writes the numeric value next to each slider. */
    private void showSliderValues()
    {
        if (spec == null)
        {
            return;
        }
        double[] values = {spec.cx, spec.cy, spec.cz, spec.yaw, spec.pitch, spec.roll, spec.width, spec.height, spec.topRatio * 100};
        for (int i = 0; i < values.length; i++)
        {
            String text = String.format(Locale.ROOT, "%.0f", values[i] + 0.0);
            if (text.equals("-0"))
            {
                text = "0";
            }
            if (i >= 3 && i <= 5)
            {
                text += "°";
            }
            if (i == 8)
            {
                text += "%";
            }
            sliderValues[i].setText(text);
        }
    }

    @Override
    public void slidePlane(double distance)
    {
        if (spec == null || busy)
        {
            return;
        }
        applySpec(spec.slide(distance));
    }

    /**
     * @param (String) text button label
     * @param (int) which 0 axial, 1 coronal, 2 sagittal, 3 triangle, 4 oblique rectangle
     * @return the button
     */
    private JButton presetButton(String text, final int which)
    {
        return button(text, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (volume == null)
                {
                    return;
                }
                if (which <= 2)
                {
                    // The view's own slice: the cut then looks exactly like that view.
                    fourPoints.setSelected(true);
                    showPointRows();
                    applyPlane(geometry.slicePlane(which, orthos[which].view().slice()));
                }
                else if (which == 3)
                {
                    threePoints.setSelected(true);
                    showPointRows();
                    applySpec(PlaneSpec.triangle(volume));
                }
                else
                {
                    fourPoints.setSelected(true);
                    showPointRows();
                    applySpec(PlaneSpec.oblique(volume));
                }
                view3d.lookAtPlane(plane);
            }
        });
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
        csvRow.setText(PlaneCsvWriter.row(plane));
        csvRow.setCaretPosition(0);
        String bad = plane.whyNotRenderable();
        if (bad != null)
        {
            cutView.setImage(null, 0, 0, "bad plane: " + bad + " (a black frame in the video)");
            view3d.setPlane(null, pts, names);
            pointsInfo.setText(html("Bad plane (a black frame in the video): " + bad, "#c03030"));
            return;
        }
        long t0 = System.nanoTime();
        Image2D img = projector.project(volume, plane);
        double ms = (System.nanoTime() - t0) / NANOS_PER_MS;
        int w = img.width();
        int h = img.height();
        byte[] grey = new byte[w * h];
        window.toGrey(img, grey, w, h, w, h);
        List<String> problems = plane.validate(Plane.DEFAULT_EPS);
        String valid = "valid";
        if (!problems.isEmpty())
        {
            valid = "doubtful: " + problems.get(0);
        }
        String shape = "trapezoid a1, a2, b, c";
        if (plane.isTriangle())
        {
            shape = "triangle a, b, c";
        }
        cutView.setImage(grey, w, h, String.format(Locale.ROOT, "%s  %dx%d px  |  %s  |  projection %.2f ms (%.0f fps, %d thread(s))",
                shape, w, h, valid, ms, 1000.0 / Math.max(ms, 1e-3), CORES));
        cutView.setCornerLabels(plane.isTriangle());
        view3d.setPlane(plane, pts, names);
        showPointsInfo(problems);
        updateStatus();
    }

    /**
     * @param (List) problems rule violations of the current plane
     */
    private void showPointsInfo(List<String> problems)
    {
        if (pickIndex >= 0)
        {
            return;
        }
        if (problems.isEmpty())
        {
            if (plane.isTriangle())
            {
                pointsInfo.setText(html(String.format(Locale.ROOT, "Isosceles: |ab| = |ac| = %.2f, |bc| = %.2f",
                        plane.a1().dist(plane.b()), plane.b().dist(plane.c())), null));
            }
            else
            {
                pointsInfo.setText(html(String.format(Locale.ROOT, "Isosceles trapezoid: rails %.2f, top %.2f <= base %.2f",
                        plane.a1().dist(plane.b()), plane.a1().dist(plane.a2()), plane.b().dist(plane.c())), null));
            }
            return;
        }
        pointsInfo.setText(html("Doubtful (drawn as given): " + problems.get(0) + ". \"Make isosceles\" fixes it.", "#b06000"));
    }

    /** Rebuilds the projector after a sampling change (and releases the old thread pool). */
    private void rebuildProjector()
    {
        if (projector instanceof ParallelProjector)
        {
            ((ParallelProjector) projector).shutdown();
        }
        projector = Projector.create(currentMode(), CORES);
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
     * Starts placing points with the mouse.
     *
     * @param (int) only row to pick alone, or -1 for all points in order
     */
    private void startPicking(int only)
    {
        if (volume == null)
        {
            return;
        }
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
        pointsInfo.setText(html("Click point " + pickNames[pickIndex] + " on the 3-D model or on a slice view.", "#c06000"));
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
            Plane fixed = PlaneSpec.makeIsosceles(p0);
            applyPlane(fixed);
            pointsInfo.setText(html("Made isosceles for the algorithm: " + describeFix(p0, fixed), null));
            return;
        }
        applyPlane(p0);
    }

    /**
     * @param (Plane) before clicked plane
     * @param (Plane) after corrected plane
     * @return what moved
     */
    private static String describeFix(Plane before, Plane after)
    {
        if (before.isTriangle())
        {
            return String.format(Locale.ROOT, "c moved along a->c, |ac| %.1f -> %.1f = |ab|",
                    before.a1().dist(before.c()), after.a1().dist(after.c()));
        }
        return "the nearest isosceles trapezoid (same centre, turn and size)";
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

    // ------------------------------------------------------------------ use cases (list)

    /** Shows the selected list entry in the views. */
    private void showSelectedKeyframe()
    {
        int i = list.getSelectedIndex();
        if (i < 0 || volume == null)
        {
            return;
        }
        Keyframe k = keyframes.get(i);
        if (k.plane != null)
        {
            applyPlane(k.plane);
        }
        else
        {
            status.setText(k.origin + " is a black frame: " + k.note);
        }
    }

    /**
     * @param (int) delta -1 moves the selected entry up, +1 down
     */
    private void move(int delta)
    {
        int i = list.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= keyframes.size())
        {
            return;
        }
        Keyframe k = keyframes.get(i);
        keyframes.set(i, keyframes.get(j));
        keyframes.set(j, k);
        list.setSelectedIndex(j);
    }

    /** Shows a file chooser for a CSV of planes. */
    private void chooseCsv()
    {
        if (volume == null)
        {
            error("Open a scan first", "Load a .nrrd file before a CSV of planes.");
            return;
        }
        File start = startFolder();
        if (matchingCsv != null)
        {
            start = matchingCsv.getParentFile();
        }
        JFileChooser chooser = new JFileChooser(start);
        chooser.setDialogTitle("Load a use case: a CSV of planes (the course format)");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV planes (*.csv)", "csv"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
        {
            loadCsv(chooser.getSelectedFile());
        }
    }

    /**
     * Reads a CSV into the list; doubtful rows are drawn as given and bad rows become black
     * frames, both reported (ADR-0003).
     *
     * @param (File) file CSV file
     */
    private void loadCsv(File file)
    {
        try
        {
            PlaneCsvReader.Result r = PlaneCsvReader.read(file, Plane.DEFAULT_EPS);
            if (!r.isValid())
            {
                error(file.getName() + " has problems", listText(r.errors));
                return;
            }
            keyframes.clear();
            for (int i = 0; i < r.rows.size(); i++)
            {
                PlaneCsvReader.Entry e = r.rows.get(i);
                String note = null;
                if (!e.warnings.isEmpty())
                {
                    note = e.warnings.get(0);
                }
                keyframes.addElement(new Keyframe(e.plane, "row " + e.row, note));
            }
            List<String> warnings = r.warnings();
            updateEnabled();
            if (keyframes.size() > 0)
            {
                list.setSelectedIndex(0);
            }
            status.setText("Loaded " + file.getName() + ": " + r.rows.size() + " planes = " + r.rows.size()
                    + " frames, " + warnings.size() + " note(s)");
            if (!warnings.isEmpty())
            {
                JOptionPane.showMessageDialog(frame, listText(warnings)
                        + "\n\nDoubtful rows are drawn as given; bad rows are black frames (as the course's edge cases expect).",
                        file.getName() + ": " + warnings.size() + " note(s)", JOptionPane.WARNING_MESSAGE);
            }
        }
        catch (IOException e)
        {
            error("Cannot read " + file.getName(), message(e));
        }
    }

    /** Writes the list as a CSV in the course format. */
    private void saveCsv()
    {
        if (keyframes.isEmpty())
        {
            return;
        }
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Save the use case as CSV");
        chooser.setSelectedFile(new File(baseName() + "_usecase.csv"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }
        File target = withExtension(chooser.getSelectedFile(), ".csv");
        try
        {
            PlaneCsvWriter.write(expandedPlanes(), target);
            status.setText("Saved " + target.getName());
        }
        catch (IOException e)
        {
            error("Cannot save", message(e));
        }
    }

    /**
     * The frames of the video: the list entries, with the chosen number of interpolated planes
     * between consecutive entries (0 = exactly the list, as the CSV demo renders it). Every plane
     * is rounded to the CSV precision first, so the exported video, the saved CSV and an Ex0 run on
     * that CSV all describe exactly the same planes.
     *
     * @return planes in order; null entries are black frames
     */
    private List<Plane> expandedPlanes()
    {
        int between = ((Integer) betweenSpinner.getValue()).intValue();
        List<Plane> out = new ArrayList<Plane>();
        for (int i = 0; i < keyframes.size(); i++)
        {
            Plane a = keyframes.get(i).plane;
            out.add(PlaneCsvWriter.quantize(a));
            if (between > 0 && i + 1 < keyframes.size())
            {
                Plane b = keyframes.get(i + 1).plane;
                if (a != null && b != null)
                {
                    PlaneSpec sa = PlaneSpec.fromPlane(a);
                    PlaneSpec sb = PlaneSpec.fromPlane(b);
                    for (int k = 1; k <= between; k++)
                    {
                        Plane mid = PlaneSpec.lerp(sa, sb, k / (double) (between + 1)).toPlane();
                        out.add(PlaneCsvWriter.quantize(mid));
                    }
                }
            }
        }
        return out;
    }

    /** Starts or stops stepping through the expanded sequence in the views. */
    private void togglePlay()
    {
        if (playTimer.isRunning())
        {
            playTimer.stop();
            playButton.setText("Play");
            return;
        }
        if (keyframes.isEmpty() || volume == null)
        {
            return;
        }
        stopPicking();
        playSequence = expandedPlanes();
        playIndex = 0;
        playButton.setText("Stop");
        playTimer.start();
    }

    /** One tick of playback. */
    private void stepPlayback()
    {
        if (playSequence == null || playSequence.isEmpty())
        {
            togglePlay();
            return;
        }
        Plane p = playSequence.get(playIndex);
        if (p != null)
        {
            applyPlane(p);
        }
        status.setText("Playing frame " + (playIndex + 1) + " / " + playSequence.size());
        playIndex = (playIndex + 1) % playSequence.size();
    }

    // ------------------------------------------------------------------ export

    /** Renders the expanded sequence to an MP4 in the background, and its CSV next to it. */
    private void exportVideo()
    {
        if (volume == null || busy)
        {
            return;
        }
        if (keyframes.isEmpty())
        {
            error("Nothing to export", "Add planes to the use case first (\"Add current\", or load a CSV).");
            return;
        }
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Export the use case as a video");
        chooser.setFileFilter(new FileNameExtensionFilter("MP4 video (*.mp4)", "mp4"));
        chooser.setSelectedFile(new File(baseName() + "_usecase.mp4"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }
        final File target = withExtension(chooser.getSelectedFile(), ".mp4");
        final List<Plane> planes = expandedPlanes();
        final int fps = ((Integer) fpsSpinner.getValue()).intValue();
        final Projector exportProjector = Projector.create(currentMode(), CORES);
        final FrameRenderer renderer;
        try
        {
            renderer = new FrameRenderer(volume, planes, window, exportProjector);
        }
        catch (IllegalArgumentException e)
        {
            error("Nothing to export", message(e));
            return;
        }
        playTimer.stop();
        playButton.setText("Play");
        setBusy(true, "Exporting " + target.getName() + " ...");
        progress.setMaximum(renderer.frameCount());
        progress.setValue(0);
        progress.setVisible(true);
        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>()
        {
            @Override
            protected Void doInBackground() throws Exception
            {
                VideoWriter out = VideoWriters.openMp4(target, renderer.width(), renderer.height(), fps);
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
                return null;
            }

            @Override
            protected void process(List<Integer> chunks)
            {
                progress.setValue(chunks.get(chunks.size() - 1).intValue());
            }

            @Override
            protected void done()
            {
                if (exportProjector instanceof ParallelProjector)
                {
                    ((ParallelProjector) exportProjector).shutdown();
                }
                progress.setVisible(false);
                setBusy(false, " ");
                try
                {
                    get();
                    File csv = replaceExtension(target, ".csv");
                    String extra = "";
                    try
                    {
                        PlaneCsvWriter.write(planes, csv);
                        extra = "\nThe planes were also saved as " + csv.getName()
                                + " (python Ex0.py <scan> " + csv.getName() + " out.mp4 reproduces the video).";
                    }
                    catch (IOException e)
                    {
                        extra = "\n(The CSV next to it could not be written: " + e.getMessage() + ")";
                    }
                    int answer = JOptionPane.showConfirmDialog(frame,
                            "Saved " + renderer.frameCount() + " frames (" + renderer.width() + "x" + renderer.height()
                                    + ", " + fps + " fps) to\n" + target.getAbsolutePath() + extra + "\n\nOpen the video now?",
                            "Export finished", JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION)
                    {
                        openInSystem(target);
                    }
                }
                catch (ExecutionException e)
                {
                    error("Export failed", message(e.getCause()));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
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
        String style = "width:" + (CONTROL_WIDTH - 80) + "px";
        if (colour != null)
        {
            style += ";color:" + colour;
        }
        return "<html><body style='" + style + "'>" + safe + "</body></html>";
    }

    /**
     * @param (Plane) p plane
     * @return the first rule it breaks, or null
     */
    private static String firstProblem(Plane p)
    {
        String bad = p.whyNotRenderable();
        if (bad != null)
        {
            return bad;
        }
        List<String> problems = p.validate(Plane.DEFAULT_EPS);
        if (problems.isEmpty())
        {
            return null;
        }
        return problems.get(0);
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
     * @return the sampling mode chosen in the toolbar
     */
    private Projector.Interpolation currentMode()
    {
        if (interpBox.getSelectedIndex() == 1)
        {
            return Projector.Interpolation.TRILINEAR;
        }
        return Projector.Interpolation.NEAREST;
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
        JSlider[] sliders = {sx, sy, sz, sYaw, sPitch, sRoll, sWidth, sHeight, sTop};
        for (int i = 0; i < sliders.length; i++)
        {
            sliders[i].setEnabled(haveVolume);
        }
        sTop.setEnabled(haveVolume && fourPoints.isSelected());
        surface.setEnabled(haveVolume && view3d.model() != null);
        playButton.setEnabled(haveVolume && !keyframes.isEmpty());
        matchingButton.setEnabled(haveVolume && matchingCsv != null);
        pickAll.setEnabled(haveVolume);
        updateStatus();
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
        status.setText(volumeFile.getName() + "  " + volume + spacing + "   |   " + keyframes.size()
                + " plane(s) in the use case");
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
     * @param (int) min minimum
     * @param (int) max maximum
     * @param (int) value initial value
     * @return a horizontal slider
     */
    private static JSlider slider(int min, int max, int value)
    {
        JSlider s = new JSlider(min, max, value);
        s.setEnabled(false);
        return s;
    }

    /**
     * @param (List) lines messages
     * @return the first lines, one per line, plus a count of the rest
     */
    private static String listText(List<String> lines)
    {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(lines.size(), MAX_LIST_LINES_IN_DIALOG);
        for (int i = 0; i < shown; i++)
        {
            sb.append(lines.get(i)).append('\n');
        }
        if (lines.size() > shown)
        {
            sb.append("... and ").append(lines.size() - shown).append(" more");
        }
        return sb.toString().trim();
    }

    /**
     * @return name of the volume file without extension
     */
    private String baseName()
    {
        if (volumeFile == null)
        {
            return "planes";
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
     * @param (File) f file
     * @param (String) ext new extension with dot
     * @return f with its extension replaced (or appended when it has none)
     */
    private static File replaceExtension(File f, String ext)
    {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0)
        {
            name = name.substring(0, dot);
        }
        return new File(f.getParentFile(), name + ext);
    }

    /**
     * Looks for a project file near the jar or the working folder.
     *
     * @param (String) dir sub-folder (may contain "/")
     * @param (String) name file name (may contain "/")
     * @return the file, or null
     */
    private static File findInProject(String dir, String name)
    {
        List<File> roots = new ArrayList<File>();
        roots.add(new File(".").getAbsoluteFile());
        try
        {
            File jar = new File(StudioApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File d = jar.getParentFile();
            while (d != null && roots.size() < 6)
            {
                roots.add(d);
                d = d.getParentFile();
            }
        }
        catch (Exception e)
        {
            // Fall back to the working folder only.
        }
        for (int i = 0; i < roots.size(); i++)
        {
            File f = new File(new File(roots.get(i), dir), name);
            if (f.isFile())
            {
                return f;
            }
        }
        return null;
    }

    /**
     * @return folder to start file choosers in
     */
    private File startFolder()
    {
        if (volumeFile != null)
        {
            return volumeFile.getAbsoluteFile().getParentFile();
        }
        File sample = findInProject("data", "MRHead.nrrd");
        if (sample != null)
        {
            return sample.getParentFile();
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * @param (File) f file to open with the system's default app
     */
    private void openInSystem(File f)
    {
        try
        {
            Desktop.getDesktop().open(f);
        }
        catch (Exception e)
        {
            error("Cannot open video", "The video was saved to\n" + f.getAbsolutePath());
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

    /** Accepts files dragged from the file manager: .nrrd/.nhdr volumes or .csv plane lists. */
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
                    if (volume == null)
                    {
                        error("Open a scan first", "Drop or open a .nrrd file before a CSV of planes.");
                        return false;
                    }
                    loadCsv(f);
                    return true;
                }
                if (!name.endsWith(".nrrd") && !name.endsWith(".nhdr"))
                {
                    error("Unsupported file", f.getName() + " is not a .nrrd, .nhdr or .csv file.");
                    return false;
                }
                openVolume(f, null);
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
