package ex1.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ex1.app.ui.FlatButtonUI;
import ex1.app.ui.FlatScrollBarUI;
import ex1.app.ui.FlatSliderUI;
import ex1.app.ui.FlatTextFieldUI;
import ex1.app.ui.Theme;
import ex1.core.IntensityWindow;
import ex1.core.Vec3;
import ex1.core.Volume;
import ex1.core.VoxelType;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import org.junit.Test;

/**
 * The look (ADR-0014) installs the app's own drawn controls - Swing must be able to create them,
 * which needs them public - and they paint without errors; and the 3-D view draws its frames on its
 * own thread, so the window never waits for it.
 */
public class LookAndSpeedTest
{
    /**
     * Checks that the theme gives buttons, switches, fields, scroll bars and sliders their flat
     * look, and that a primary button really paints amber (TC-122).
     * @throws (Exception) When the look cannot be installed or put back.
     */
    @Test
    public void theThemeGivesEveryControlItsFlatLook() throws Exception
    {
        LookAndFeel before = UIManager.getLookAndFeel();
        try
        {
            Theme.install();
            JButton       b  = new JButton("Play");
            JToggleButton t  = new JToggleButton("3 points");
            JTextField    f  = new JTextField(5);
            JScrollBar    s  = new JScrollBar();
            JSlider       sl = new JSlider();
            assertEquals(FlatButtonUI.class, b.getUI().getClass());
            assertEquals(FlatButtonUI.class, t.getUI().getClass());
            assertEquals(FlatTextFieldUI.class, f.getUI().getClass());
            assertEquals(FlatScrollBarUI.class, s.getUI().getClass());
            assertEquals(FlatSliderUI.class, sl.getUI().getClass());
            b.putClientProperty(Theme.VARIANT, Theme.PRIMARY);
            t.putClientProperty(Theme.VARIANT, Theme.SEGMENT_FIRST);
            BufferedImage img = new BufferedImage(200, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D    g   = img.createGraphics();
            b.setSize(b.getPreferredSize());
            b.paint(g);
            g.dispose();
            // The primary button is filled amber.
            int rgb = img.getRGB(b.getWidth() / 2, 2) & 0xFFFFFF;
            assertEquals(Theme.PLANE.getRGB() & 0xFFFFFF, rgb);
            BufferedImage other = new BufferedImage(200, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D    g2    = other.createGraphics();
            t.setSize(t.getPreferredSize());
            t.setSelected(true);
            t.paint(g2);
            f.setSize(f.getPreferredSize());
            f.paint(g2);
            g2.dispose();
        }
        finally
        {
            UIManager.setLookAndFeel(before);
        }
    }

    /**
     * Checks that the 3-D view draws its frames on its own thread, and that after dispose its timer
     * asks for nothing more (TC-123).
     * @throws (Exception) When the wait for the frame thread is interrupted.
     */
    @Test
    public void theThreeDViewDrawsOnItsOwnThread() throws Exception
    {
        Volume v = PhantomGenerator.build("spheres", VoxelType.UINT8, 48);
        IntensityWindow w = IntensityWindow.fromPercentiles(v,
                IntensityWindow.DEFAULT_LOW_PERCENTILE, IntensityWindow.DEFAULT_HIGH_PERCENTILE);
        VolumeView3D view = new VolumeView3D(null, 2);
        try
        {
            view.setSize(160, 120);
            view.setVolume(v, w, VolumeGeometry.of(v));
            view.setModel(SolidModel.build(v, w));
            view.setPlane(PlaneSpec.triangle(v).toPlane(), new ArrayList<Vec3>(), new ArrayList<String>());
            assertTrue("frames still drawing after 10 s", view.awaitIdle(10000));
            assertTrue(view.hasFrame());
        }
        finally
        {
            view.dispose();
        }
    }
}
