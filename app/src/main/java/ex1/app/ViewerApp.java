package ex1.app;

import ex1.core.IntensityWindow;
import ex1.core.ParallelProjector;
import ex1.core.Plane;
import ex1.core.Projector;
import ex1.core.Volume;
import ex1.io.NrrdReader;
import ex1.io.PlaneCsvReader;
import ex1.io.VideoWriter;
import ex1.io.VideoWriters;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Window app: open or drop an NRRD file and watch the plane projections play live;
 * export the sequence as MP4. Uses the same core and FrameRenderer as the Ex0 demo.
 */
public final class ViewerApp
{
    private static final String TITLE = "Ex1 - Plane Projection Viewer";
    private static final String[] MODES = {"Rotate", "Sweep", "Tilt", "Triangle", "From CSV..."};
    private static final int CSV_MODE = 4;
    private static final int DEFAULT_FRAMES = 90;
    private static final int MAX_FRAMES = 2000;
    private static final int PLAY_FPS = 30;
    private static final int EXPORT_FPS = 15;
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final int MS_PER_SECOND = 1000;
    private static final double NANOS_PER_MS = 1e6;
    private static final Color BACKGROUND = new Color(24, 26, 30);
    private static final Color HINT = new Color(150, 156, 168);

    private final JFrame frame = new JFrame(TITLE);
    private final ViewPanel view = new ViewPanel();
    private final JLabel fileLabel = new JLabel("No volume loaded");
    private final JComboBox<String> modeBox = new JComboBox<String>(MODES);
    private final JComboBox<String> interpBox = new JComboBox<String>(new String[] {"Nearest", "Trilinear"});
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_FRAMES, 1, MAX_FRAMES, 10));
    private final JButton playButton = new JButton("Pause");
    private final JButton exportButton = new JButton("Export MP4...");
    private final JSlider slider = new JSlider(0, 0, 0);
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final Timer timer;

    private File volumeFile;
    private Volume volume;
    private IntensityWindow window;
    private List<Plane> csvPlanes;
    private String csvName;
    private FrameRenderer renderer;
    private Projector liveProjector;
    private BufferedImage shown;
    private boolean busy;
    private boolean ignoreSlider;
    private boolean ignoreMode;

    // ===================================================================== ENTRY

    /**
     * Opens the window on the Swing thread.
     *
     * @param (File) initial NRRD to open at start, or null
     */
    public static void launch(final File initial)
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
                    // Default look and feel is fine.
                }
                ViewerApp app = new ViewerApp();
                app.frame.setVisible(true);
                if (initial != null)
                {
                    app.openVolume(initial);
                }
            }
        });
    }

    /**
     * Builds the window; call on the Swing thread.
     */
    private ViewerApp()
    {
        timer = new Timer(MS_PER_SECOND / PLAY_FPS, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                advance();
            }
        });
        buildWindow();
    }

    // ============================================================ BUILD PIPELINE

    /** Lays out toolbar, view and status bar, and wires all events. */
    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(820, 600));
        frame.setSize(new Dimension(1000, 760));
        frame.setLocationRelativeTo(null);

        JButton openButton = new JButton("Open NRRD...");
        JButton sampleButton = new JButton("Open sample");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(openButton);
        top.add(sampleButton);
        top.add(new JLabel("   Planes:"));
        top.add(modeBox);
        top.add(new JLabel("Frames:"));
        top.add(countSpinner);
        top.add(new JLabel("Sampling:"));
        top.add(interpBox);
        top.add(playButton);
        top.add(exportButton);

        JPanel bottom = new JPanel(new BorderLayout(8, 4));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        bottom.add(slider, BorderLayout.NORTH);
        JPanel info = new JPanel(new BorderLayout(8, 0));
        info.add(fileLabel, BorderLayout.WEST);
        info.add(status, BorderLayout.CENTER);
        progress.setVisible(false);
        progress.setStringPainted(true);
        info.add(progress, BorderLayout.EAST);
        bottom.add(info, BorderLayout.SOUTH);
        status.setHorizontalAlignment(JLabel.RIGHT);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(view, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);

        // Dropping a file anywhere in the window opens it.
        FileDrop drop = new FileDrop();
        frame.getRootPane().setTransferHandler(drop);
        view.setTransferHandler(drop);

        openButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                chooseVolume();
            }
        });
        sampleButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                openSample();
            }
        });
        view.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (volume == null && !busy)
                {
                    chooseVolume();
                }
            }
        });
        ActionListener rebuild = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                rebuildPlanes();
            }
        };
        modeBox.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (ignoreMode)
                {
                    return;
                }
                if (modeBox.getSelectedIndex() == CSV_MODE && !chooseCsv())
                {
                    return;
                }
                rebuildPlanes();
            }
        });
        interpBox.addActionListener(rebuild);
        countSpinner.addChangeListener(new ChangeListener()
        {
            @Override
            public void stateChanged(ChangeEvent e)
            {
                rebuildPlanes();
            }
        });
        playButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                togglePlay();
            }
        });
        exportButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportMp4();
            }
        });
        slider.addChangeListener(new ChangeListener()
        {
            @Override
            public void stateChanged(ChangeEvent e)
            {
                if (!ignoreSlider && renderer != null)
                {
                    show(slider.getValue());
                }
            }
        });
        updateControls();
    }

    /** Shows a file chooser for NRRD files. */
    private void chooseVolume()
    {
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Open a 3D volume");
        chooser.setFileFilter(new FileNameExtensionFilter("NRRD volumes (*.nrrd, *.nhdr)", "nrrd", "nhdr"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
        {
            openVolume(chooser.getSelectedFile());
        }
    }

    /** Opens data/MRHead.nrrd shipped with the project. */
    private void openSample()
    {
        File sample = findSample();
        if (sample == null)
        {
            error("Sample not found", "Could not find data" + File.separator + "MRHead.nrrd next to the program.\n"
                    + "Use \"Open NRRD...\" or drop a .nrrd file on the window.");
            return;
        }
        openVolume(sample);
    }

    /**
     * Loads a volume in the background, then builds planes and starts playing.
     *
     * @param (File) file NRRD file
     */
    void openVolume(final File file)
    {
        if (busy)
        {
            return;
        }
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
        {
            loadCsv(file);
            return;
        }
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
                    fileLabel.setText(file.getName() + "   " + volume);
                    frame.setTitle(TITLE + " - " + file.getName());
                    csvPlanes = null;
                    selectMode(0);
                    if (renderer != null && !timer.isRunning())
                    {
                        togglePlay();
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
                    error("Not enough memory", "The volume is too large for the memory given to Java.");
                }
            }
        };
        worker.execute();
    }

    /**
     * Asks for a CSV of planes.
     *
     * @return true when a valid CSV was loaded
     */
    private boolean chooseCsv()
    {
        JFileChooser chooser = new JFileChooser(startFolder());
        chooser.setDialogTitle("Open a CSV of planes (9 or 12 numbers per line)");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV planes (*.csv)", "csv"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION)
        {
            selectMode(0);
            return false;
        }
        return loadCsv(chooser.getSelectedFile());
    }

    /**
     * Reads and validates a CSV; on errors shows them and stays in the previous mode.
     *
     * @param (File) file CSV file
     * @return true on success
     */
    private boolean loadCsv(File file)
    {
        try
        {
            PlaneCsvReader.Result r = PlaneCsvReader.read(file, Plane.DEFAULT_EPS);
            if (!r.isValid())
            {
                error(file.getName() + " has problems", listText(r.errors));
                selectMode(0);
                return false;
            }
            csvPlanes = r.planes();
            List<String> warnings = r.warnings();
            if (!warnings.isEmpty())
            {
                // Same policy as Ex0: report, but still give one frame per CSV row.
                JOptionPane.showMessageDialog(frame, listText(warnings)
                        + "\n\nThese rows are still shown (black frame if they cannot be projected).",
                        file.getName() + ": " + warnings.size() + " warning(s)", JOptionPane.WARNING_MESSAGE);
            }
            csvName = file.getName();
            selectMode(CSV_MODE);
            return true;
        }
        catch (Exception e)
        {
            error("Cannot read " + file.getName(), message(e));
            selectMode(0);
            return false;
        }
    }

    /** Recreates the plane list and renderer from the current controls. */
    private void rebuildPlanes()
    {
        if (volume == null)
        {
            updateControls();
            return;
        }
        List<Plane> planes;
        if (modeBox.getSelectedIndex() == CSV_MODE)
        {
            if (csvPlanes == null)
            {
                return;
            }
            planes = csvPlanes;
        }
        else
        {
            int count = ((Integer) countSpinner.getValue()).intValue();
            String mode = MODES[modeBox.getSelectedIndex()];
            planes = SweepGenerator.generate(mode, volume.sizeX(), volume.sizeY(), volume.sizeZ(), count);
        }
        if (liveProjector instanceof ParallelProjector)
        {
            // Release the previous pool before the new one is created (ADR-0007).
            ((ParallelProjector) liveProjector).shutdown();
        }
        liveProjector = Projector.create(interpolation(), CORES);
        renderer = new FrameRenderer(volume, planes, window, liveProjector);
        shown = new BufferedImage(renderer.width(), renderer.height(), BufferedImage.TYPE_BYTE_GRAY);
        ignoreSlider = true;
        slider.setMaximum(renderer.frameCount() - 1);
        slider.setValue(Math.min(slider.getValue(), renderer.frameCount() - 1));
        ignoreSlider = false;
        updateControls();
        show(slider.getValue());
    }

    /** Next frame (called by the play timer), looping at the end. */
    private void advance()
    {
        if (renderer == null)
        {
            return;
        }
        int next = slider.getValue() + 1;
        if (next >= renderer.frameCount())
        {
            next = 0;
        }
        ignoreSlider = true;
        slider.setValue(next);
        ignoreSlider = false;
        show(next);
    }

    /**
     * Renders one frame live and repaints.
     *
     * @param (int) i frame index
     */
    private void show(int i)
    {
        byte[] grey = renderer.render(i);
        byte[] dst = ((DataBufferByte) shown.getRaster().getDataBuffer()).getData();
        System.arraycopy(grey, 0, dst, 0, dst.length);
        view.setImage(shown);
        double ms = renderer.lastProjectionNanos() / NANOS_PER_MS;
        String planeSource = MODES[modeBox.getSelectedIndex()];
        if (modeBox.getSelectedIndex() == CSV_MODE)
        {
            planeSource = csvName;
        }
        status.setText(String.format(Locale.ROOT, "%s  |  frame %d / %d  |  %dx%d px  |  projection %.2f ms (%.0f fps possible)",
                planeSource, i + 1, renderer.frameCount(), renderer.width(), renderer.height(),
                ms, MS_PER_SECOND / Math.max(ms, 1e-3)));
    }

    /** Starts or pauses playback. */
    private void togglePlay()
    {
        if (timer.isRunning())
        {
            timer.stop();
        }
        else if (renderer != null)
        {
            timer.start();
        }
        updateControls();
    }

    /** Asks where to save, then writes every frame to an MP4 in the background. */
    private void exportMp4()
    {
        if (renderer == null || busy)
        {
            return;
        }
        String base = volumeFile.getName().replaceFirst("\\.[^.]*$", "");
        String mode = MODES[modeBox.getSelectedIndex()].replace("...", "").replace(' ', '_');
        JFileChooser chooser = new JFileChooser(volumeFile.getAbsoluteFile().getParentFile());
        chooser.setDialogTitle("Save video");
        chooser.setFileFilter(new FileNameExtensionFilter("MP4 video (*.mp4)", "mp4"));
        chooser.setSelectedFile(new File(base + "_" + mode.toLowerCase(Locale.ROOT) + ".mp4"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }
        File chosen = chooser.getSelectedFile();
        if (!chosen.getName().toLowerCase(Locale.ROOT).endsWith(".mp4"))
        {
            chosen = new File(chosen.getPath() + ".mp4");
        }
        final File target = chosen;
        final boolean wasPlaying = timer.isRunning();
        timer.stop();

        // A separate renderer: export must not share buffers with the live view.
        final List<Plane> planes = currentPlanes();
        final Projector exportProjector = Projector.create(interpolation(), CORES);
        final FrameRenderer exporter = new FrameRenderer(volume, planes, window, exportProjector);
        setBusy(true, "Exporting " + target.getName() + " ...");
        progress.setMaximum(exporter.frameCount());
        progress.setValue(0);
        progress.setVisible(true);

        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>()
        {
            @Override
            protected Void doInBackground() throws Exception
            {
                VideoWriter out = VideoWriters.openMp4(target, exporter.width(), exporter.height(), EXPORT_FPS);
                try
                {
                    for (int i = 0; i < exporter.frameCount(); i++)
                    {
                        out.writeFrame(exporter.render(i));
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
                if (wasPlaying)
                {
                    timer.start();
                }
                updateControls();
                try
                {
                    get();
                    int answer = JOptionPane.showConfirmDialog(frame,
                            "Saved " + exporter.frameCount() + " frames to\n" + target.getAbsolutePath()
                                    + "\n\nOpen the video now?", "Export finished", JOptionPane.YES_NO_OPTION);
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
     * Changes the plane mode from code (without opening the CSV dialog) and rebuilds.
     *
     * @param (int) index mode index
     */
    private void selectMode(int index)
    {
        ignoreMode = true;
        modeBox.setSelectedIndex(index);
        ignoreMode = false;
        rebuildPlanes();
    }

    /**
     * @return planes currently shown
     */
    private List<Plane> currentPlanes()
    {
        if (modeBox.getSelectedIndex() == CSV_MODE && csvPlanes != null)
        {
            return csvPlanes;
        }
        int count = ((Integer) countSpinner.getValue()).intValue();
        return SweepGenerator.generate(MODES[modeBox.getSelectedIndex()],
                volume.sizeX(), volume.sizeY(), volume.sizeZ(), count);
    }

    /**
     * @return selected interpolation
     */
    private Projector.Interpolation interpolation()
    {
        if (interpBox.getSelectedIndex() == 1)
        {
            return Projector.Interpolation.TRILINEAR;
        }
        return Projector.Interpolation.NEAREST;
    }

    /**
     * @param (boolean) b true while loading/exporting
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
        view.setBusyText(b, text);
        updateControls();
    }

    /** Enables controls that make sense in the current state. */
    private void updateControls()
    {
        boolean ready = renderer != null && !busy;
        playButton.setEnabled(ready);
        exportButton.setEnabled(ready);
        slider.setEnabled(ready);
        countSpinner.setEnabled(!busy && modeBox.getSelectedIndex() != CSV_MODE);
        if (timer.isRunning())
        {
            playButton.setText("Pause");
        }
        else
        {
            playButton.setText("Play");
        }
    }

    /**
     * @return data/MRHead.nrrd near the jar or the working folder, or null
     */
    private static File findSample()
    {
        List<File> roots = new ArrayList<File>();
        roots.add(new File(".").getAbsoluteFile());
        try
        {
            File jar = new File(ViewerApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = jar.getParentFile();
            while (dir != null && roots.size() < 6)
            {
                roots.add(dir);
                dir = dir.getParentFile();
            }
        }
        catch (Exception e)
        {
            // Fall back to the working folder only.
        }
        for (int i = 0; i < roots.size(); i++)
        {
            File f = new File(new File(roots.get(i), "data"), "MRHead.nrrd");
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
        File sample = findSample();
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
     * @param (List) lines messages
     * @return the first 15 lines, one per line, plus a count of the rest
     */
    private static String listText(List<String> lines)
    {
        StringBuilder sb = new StringBuilder();
        int shownLines = Math.min(lines.size(), 15);
        for (int i = 0; i < shownLines; i++)
        {
            sb.append(lines.get(i)).append('\n');
        }
        if (lines.size() > shownLines)
        {
            sb.append("... and ").append(lines.size() - shownLines).append(" more");
        }
        return sb.toString().trim();
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

    /** Accepts files dragged from Explorer: .nrrd/.nhdr volumes or .csv plane lists. */
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

    /** Draws the current frame scaled to fit, or the drop hint when nothing is loaded. */
    private static final class ViewPanel extends JComponent
    {
        private static final long serialVersionUID = 1L;
        private static final int MARGIN = 24;

        private transient BufferedImage image;
        private boolean busy;
        private String busyText = "";

        /** Dark background panel. */
        ViewPanel()
        {
            setOpaque(true);
            setBackground(BACKGROUND);
        }

        /**
         * @param (BufferedImage) img frame to draw (may be updated in place)
         */
        void setImage(BufferedImage img)
        {
            image = img;
            repaint();
        }

        /**
         * @param (boolean) b busy flag
         * @param (String) text busy message
         */
        void setBusyText(boolean b, String text)
        {
            busy = b;
            busyText = text;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (image != null)
            {
                double scale = Math.min((getWidth() - 2.0 * MARGIN) / image.getWidth(),
                        (getHeight() - 2.0 * MARGIN) / image.getHeight());
                int w = (int) (image.getWidth() * scale);
                int h = (int) (image.getHeight() * scale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            }
            else
            {
                drawHint(g);
            }
            if (busy)
            {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.WHITE);
                centred(g, busyText, getHeight() / 2, 18f);
            }
            g.dispose();
        }

        /**
         * @param (Graphics2D) g graphics
         */
        private void drawHint(Graphics2D g)
        {
            int bw = Math.min(520, getWidth() - 2 * MARGIN);
            int bh = Math.min(260, getHeight() - 2 * MARGIN);
            int x = (getWidth() - bw) / 2;
            int y = (getHeight() - bh) / 2;
            g.setColor(HINT);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    new float[] {10f, 8f}, 0f));
            g.drawRoundRect(x, y, bw, bh, 24, 24);
            g.setColor(new Color(220, 224, 232));
            centred(g, "Drop a .nrrd file here", y + bh / 2 - 14, 22f);
            g.setColor(HINT);
            centred(g, "or click here / \"Open NRRD...\"  -  or try \"Open sample\"", y + bh / 2 + 20, 14f);
        }

        /**
         * @param (Graphics2D) g graphics
         * @param (String) text text
         * @param (int) baseline y of the baseline
         * @param (float) size font size
         */
        private void centred(Graphics2D g, String text, int baseline, float size)
        {
            Font f = getFont();
            if (f == null)
            {
                f = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
            g.setFont(f.deriveFont(size));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, baseline);
        }
    }
}
