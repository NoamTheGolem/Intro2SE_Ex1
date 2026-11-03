package ex1.app;

import ex1.core.Image2D;
import ex1.core.IntensityWindow;
import ex1.core.ParallelProjector;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Volume;
import ex1.io.NrrdReader;
import ex1.io.PlaneCsvReader;
import ex1.io.PlaneCsvWriter;
import ex1.io.VideoWriter;
import ex1.io.VideoWriters;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
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
 * The application: load a 3-D model (NRRD), cut it with a plane you control, see the model and
 * the cut, collect the planes of a use case into a list (or load the course's CSV), play them,
 * and export the video. The command-line demo (Ex0) is the batch form of the same pipeline.
 */
public final class StudioApp implements VolumeView3D.Host
{
    private static final String TITLE = "Ex1 Studio - cut a 3-D volume with a plane";
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final int PLAY_FPS = 15;
    private static final int DEFAULT_EXPORT_FPS = 15;
    private static final int MAX_LIST_LINES_IN_DIALOG = 15;
    private static final int SLIDER_SCALE = 10;
    private static final double NANOS_PER_MS = 1e6;

    /** One entry of the use-case list: a plane (null = black frame) and where it came from. */
    static final class Keyframe
    {
        final Plane plane;
        final String origin;

        /**
         * @param (Plane) plane plane or null
         * @param (String) origin e.g. "row 3" or "added"
         */
        Keyframe(Plane plane, String origin)
        {
            this.plane = plane;
            this.origin = origin;
        }

        @Override
        public String toString()
        {
            if (plane == null)
            {
                return origin + ": cannot be projected (black frame)";
            }
            return origin + ": " + PlaneSpec.fromPlane(plane).label();
        }
    }

    // ---- model
    private File volumeFile;
    private Volume volume;
    private IntensityWindow window;
    private Projector projector;
    private PlaneSpec spec;
    private Plane plane;
    private final DefaultListModel<Keyframe> keyframes = new DefaultListModel<Keyframe>();
    private List<Plane> playSequence;
    private int playIndex;
    private boolean busy;
    private boolean updatingSliders;

    // ---- views
    private final JFrame frame = new JFrame(TITLE);
    private final VolumeView3D view3d = new VolumeView3D(this);
    private final SliceView view2d = new SliceView();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JComboBox<String> interpBox = new JComboBox<String>(new String[] {"Nearest", "Trilinear"});
    private final JCheckBox showSlices = new JCheckBox("Model slices", true);
    private final JCheckBox showCut = new JCheckBox("Cutting plane", true);
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
    private final JList<Keyframe> list = new JList<Keyframe>(keyframes);
    private final JSpinner betweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
    private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_EXPORT_FPS, 1, 60, 1));
    private final JButton playButton = new JButton("Play");
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

    /** Lays out toolbar, the two views, the plane editor and the use-case list. */
    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 700));
        frame.setSize(new Dimension(1360, 900));
        frame.setLocationRelativeTo(null);

        // --- toolbar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(button("Open NRRD...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                chooseVolume();
            }
        }));
        top.add(button("Open sample", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                openSample();
            }
        }));
        top.add(Box.createHorizontalStrut(12));
        top.add(button("Load use cases (CSV)...", new ActionListener()
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
        top.add(button("Export video...", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportVideo();
            }
        }));
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("Sampling:"));
        top.add(interpBox);
        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("Show:"));
        top.add(showSlices);
        top.add(showCut);
        ActionListener layers = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                view3d.setLayers(showSlices.isSelected(), showCut.isSelected());
            }
        };
        showSlices.addActionListener(layers);
        showCut.addActionListener(layers);
        interpBox.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                rebuildProjector();
                refreshViews();
            }
        });

        // --- views
        JSplitPane views = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, view3d, view2d);
        views.setResizeWeight(0.6);
        views.setBorder(null);

        // --- bottom: editor + list
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        bottom.add(buildEditor(), BorderLayout.CENTER);
        bottom.add(buildKeyframePanel(), BorderLayout.EAST);

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        statusBar.add(status, BorderLayout.CENTER);
        progress.setVisible(false);
        progress.setStringPainted(true);
        statusBar.add(progress, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.add(bottom, BorderLayout.CENTER);
        south.add(statusBar, BorderLayout.SOUTH);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(views, BorderLayout.CENTER);
        frame.getContentPane().add(south, BorderLayout.SOUTH);

        FileDrop drop = new FileDrop();
        frame.getRootPane().setTransferHandler(drop);
        view3d.setTransferHandler(drop);
        view2d.setTransferHandler(drop);

        rebuildProjector();
        updateEnabled();
    }

    /**
     * @return the plane editor: presets and sliders
     */
    private JPanel buildEditor()
    {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Cutting plane"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 4, 1, 4);
        g.anchor = GridBagConstraints.WEST;

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presets.add(new JLabel("Presets:"));
        presets.add(presetButton("XY", 0));
        presets.add(presetButton("XZ", 1));
        presets.add(presetButton("YZ", 2));
        presets.add(presetButton("Triangle", 3));
        presets.add(presetButton("Oblique", 4));
        presets.add(Box.createHorizontalStrut(10));
        presets.add(new JLabel("Slice through:"));
        presets.add(button("<", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                slidePlane(-5);
            }
        }));
        presets.add(button(">", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                slidePlane(5);
            }
        }));
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 6;
        p.add(presets, g);
        g.gridwidth = 1;

        String[] names = {"Centre X", "Centre Y", "Centre Z", "Yaw", "Pitch", "Roll", "Width", "Height", "Top / bottom"};
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
            int col = (i / 3) * 3;
            int row = 1 + (i % 3);
            g.gridx = col;
            g.gridy = row;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            p.add(new JLabel(names[i]), g);
            g.gridx = col + 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            sliders[i].setPreferredSize(new Dimension(170, 22));
            sliders[i].addChangeListener(onSlide);
            p.add(sliders[i], g);
            g.gridx = col + 2;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            sliderValues[i] = new JLabel("0");
            sliderValues[i].setPreferredSize(new Dimension(44, 18));
            p.add(sliderValues[i], g);
        }
        g.gridx = 0;
        g.gridy = 4;
        g.gridwidth = 9;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        csvRow.setEditable(false);
        csvRow.setToolTipText("The current plane as a row of the course CSV (a, b, c or a1, a2, b, c)");
        JPanel rowPanel = new JPanel(new BorderLayout(6, 0));
        rowPanel.add(new JLabel("CSV row:"), BorderLayout.WEST);
        rowPanel.add(csvRow, BorderLayout.CENTER);
        p.add(rowPanel, g);
        return p;
    }

    /**
     * @return the use-case list with its buttons
     */
    private JPanel buildKeyframePanel()
    {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Use case: list of planes (one video frame each)"));
        p.setPreferredSize(new Dimension(520, 190));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);
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
        p.add(new JScrollPane(list), BorderLayout.CENTER);

        // Two columns so all six buttons fit beside a six-row list.
        JPanel buttons = new JPanel(new GridLayout(3, 2, 4, 4));
        buttons.add(button("Add current", new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (plane != null)
                {
                    keyframes.addElement(new Keyframe(plane, "added"));
                    list.setSelectedIndex(keyframes.size() - 1);
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
                    keyframes.set(i, new Keyframe(plane, keyframes.get(i).origin));
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
        JPanel east = new JPanel(new BorderLayout());
        east.add(buttons, BorderLayout.NORTH);
        p.add(east, BorderLayout.EAST);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        south.add(new JLabel("Frames between planes:"));
        south.add(betweenSpinner);
        south.add(new JLabel("Video fps:"));
        south.add(fpsSpinner);
        playButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                togglePlay();
            }
        });
        south.add(playButton);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------------ volume

    /** Shows a file chooser for NRRD files. */
    private void chooseVolume()
    {
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Open a 3-D volume");
        chooser.setFileFilter(new FileNameExtensionFilter("NRRD volumes (*.nrrd, *.nhdr)", "nrrd", "nhdr"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
        {
            openVolume(chooser.getSelectedFile(), null);
        }
    }

    /** Opens data/MRHead.nrrd shipped with the project. */
    private void openSample()
    {
        File sample = findInProject("data", "MRHead.nrrd");
        if (sample == null)
        {
            error("Sample not found", "Could not find data/MRHead.nrrd next to the program.\n"
                    + "Use \"Open NRRD...\" or drop a .nrrd file on the window.");
            return;
        }
        openVolume(sample, findInProject("data", "Ex0.csv"));
    }

    /**
     * Loads a volume in the background, then shows the XY preset (and a CSV, when given).
     *
     * @param (File) file NRRD file
     * @param (File) csvAfter CSV to load once the volume is in, or null
     */
    void openVolume(final File file, final File csvAfter)
    {
        if (busy)
        {
            return;
        }
        playTimer.stop();
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
                    volumeFile = file;
                    frame.setTitle(TITLE + " - " + file.getName() + "  (" + volume + ")");
                    configureSliderRanges();
                    view3d.setVolume(volume, window);
                    keyframes.clear();
                    applySpec(PlaneSpec.oblique(volume));
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

    // ------------------------------------------------------------------ plane editing

    /**
     * Sets the current plane from a spec and updates the sliders and both views.
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
     * Shows an arbitrary plane (e.g. a CSV row that is not isosceles): the views draw the plane
     * as it is, the sliders show the closest spec.
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
     * @param (int) which 0 = XY, 1 = XZ, 2 = YZ, 3 = triangle, 4 = oblique rectangle
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
                PlaneSpec s;
                if (which == 0)
                {
                    s = PlaneSpec.xy(volume);
                }
                else if (which == 1)
                {
                    s = PlaneSpec.xz(volume);
                }
                else if (which == 2)
                {
                    s = PlaneSpec.yz(volume);
                }
                else if (which == 3)
                {
                    s = PlaneSpec.triangle(volume);
                }
                else
                {
                    s = PlaneSpec.oblique(volume);
                }
                applySpec(s);
            }
        });
    }

    /** Projects the current plane once and hands the result to both views. */
    private void refreshViews()
    {
        if (volume == null || plane == null)
        {
            view2d.setImage(null, 0, 0, "");
            view3d.setPlane(null, null);
            csvRow.setText("");
            return;
        }
        if (!plane.isRenderable())
        {
            view2d.setImage(null, 0, 0, "this plane cannot be projected");
            view3d.setPlane(null, null);
            csvRow.setText(PlaneCsvWriter.row(plane));
            return;
        }
        long t0 = System.nanoTime();
        Image2D img = projector.project(volume, plane);
        double ms = (System.nanoTime() - t0) / NANOS_PER_MS;
        int w = img.width();
        int h = img.height();
        byte[] grey = new byte[w * h];
        window.toGrey(img, grey, w, h, w, h);
        String shape = "trapezoid";
        if (plane.isTriangle())
        {
            shape = "triangle";
        }
        List<String> problems = plane.validate(Plane.DEFAULT_EPS);
        String valid = "valid";
        if (!problems.isEmpty())
        {
            valid = "breaks the rules: " + problems.get(0);
        }
        view2d.setImage(grey, w, h, String.format(Locale.ROOT, "%s  %dx%d px  |  %s  |  projection %.2f ms (%.0f fps possible, %d thread(s))",
                shape, w, h, valid, ms, 1000.0 / Math.max(ms, 1e-3), CORES));
        view3d.setPlane(plane, grey);
        csvRow.setText(PlaneCsvWriter.row(plane));
        status.setText(volumeFile.getName() + "  " + volume + "   |   " + keyframes.size() + " plane(s) in the use case");
    }

    /** Rebuilds the projector after a sampling change (and releases the old thread pool). */
    private void rebuildProjector()
    {
        if (projector instanceof ParallelProjector)
        {
            ((ParallelProjector) projector).shutdown();
        }
        Projector.Interpolation mode = Projector.Interpolation.NEAREST;
        if (interpBox.getSelectedIndex() == 1)
        {
            mode = Projector.Interpolation.TRILINEAR;
        }
        projector = Projector.create(mode, CORES);
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
            error("Open a volume first", "Load a .nrrd file before a CSV of planes.");
            return;
        }
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Load a CSV of planes (the course format)");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV planes (*.csv)", "csv"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
        {
            loadCsv(chooser.getSelectedFile());
        }
    }

    /**
     * Reads a CSV into the list; rows that break the rules are reported and kept (ADR-0003).
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
                keyframes.addElement(new Keyframe(e.plane, "row " + e.row));
            }
            List<String> warnings = r.warnings();
            if (!warnings.isEmpty())
            {
                JOptionPane.showMessageDialog(frame, listText(warnings)
                        + "\n\nThese rows are kept: they are projected as they are, or shown as black frames.",
                        file.getName() + ": " + warnings.size() + " note(s)", JOptionPane.WARNING_MESSAGE);
            }
            updateEnabled();
            if (keyframes.size() > 0)
            {
                list.setSelectedIndex(0);
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
        chooser.setDialogTitle("Save the planes as CSV");
        chooser.setSelectedFile(new File(baseName() + "_planes.csv"));
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
        playIndex = (playIndex + 1) % playSequence.size();
    }

    // ------------------------------------------------------------------ export

    /** Renders the expanded sequence to an MP4 in the background. */
    private void exportVideo()
    {
        if (volume == null || keyframes.isEmpty() || busy)
        {
            if (volume != null && keyframes.isEmpty())
            {
                error("Nothing to export", "Add planes to the use-case list first (\"Add current\" or load a CSV).");
            }
            return;
        }
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Export video");
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
                                + " (python Ex0.py <volume> " + csv.getName() + " out.mp4 reproduces the video).";
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
        frame.setCursor(b ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
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
        playButton.setEnabled(haveVolume && !keyframes.isEmpty());
        if (volume != null)
        {
            status.setText(volumeFile.getName() + "  " + volume + "   |   " + keyframes.size() + " plane(s) in the use case");
        }
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
     * @param (String) dir sub-folder
     * @param (String) name file name
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
                        error("Open a volume first", "Drop or open a .nrrd file before a CSV of planes.");
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
