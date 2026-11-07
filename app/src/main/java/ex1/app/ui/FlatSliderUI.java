package ex1.app.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * A slider for {@link Theme}: a thin rounded track, lit up to a round thumb.
 */
public final class FlatSliderUI extends BasicSliderUI
{
    private static final int TRACK = 4;
    private static final int THUMB = 12;

    /**
     * @param (JSlider) s the slider
     */
    public FlatSliderUI(JSlider s)
    {
        super(s);
    }

    /**
     * @param (JComponent) c the slider
     * @return a new UI
     */
    public static ComponentUI createUI(JComponent c)
    {
        return new FlatSliderUI((JSlider) c);
    }

    @Override
    protected Dimension getThumbSize()
    {
        return new Dimension(THUMB + 2, THUMB + 2);
    }

    @Override
    public void paintFocus(Graphics g)
    {
        // The thumb itself shows where the slider is; no dotted focus box.
    }

    @Override
    public void paintTrack(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle t = trackRect;
        g2.setColor(Theme.LINE);
        if (slider.getOrientation() == JSlider.HORIZONTAL)
        {
            int cy = t.y + t.height / 2 - TRACK / 2;
            g2.fillRoundRect(t.x, cy, t.width, TRACK, TRACK, TRACK);
            g2.setColor(litColour());
            int end = thumbRect.x + thumbRect.width / 2;
            g2.fillRoundRect(t.x, cy, Math.max(TRACK, end - t.x), TRACK, TRACK, TRACK);
        }
        else
        {
            int cx = t.x + t.width / 2 - TRACK / 2;
            g2.fillRoundRect(cx, t.y, TRACK, t.height, TRACK, TRACK);
            g2.setColor(litColour());
            int start = thumbRect.y + thumbRect.height / 2;
            g2.fillRoundRect(cx, start, TRACK, Math.max(TRACK, t.y + t.height - start), TRACK, TRACK);
        }
        g2.dispose();
    }

    @Override
    public void paintThumb(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.TEXT);
        if (!slider.isEnabled())
        {
            g2.setColor(Theme.TEXT_FAINT);
        }
        Rectangle r = thumbRect;
        g2.fillOval(r.x + (r.width - THUMB) / 2, r.y + (r.height - THUMB) / 2, THUMB, THUMB);
        g2.dispose();
    }

    /**
     * @return the colour of the track up to the thumb
     */
    private java.awt.Color litColour()
    {
        if (slider.isEnabled())
        {
            return Theme.TEXT_DIM;
        }
        return Theme.TEXT_FAINT;
    }
}
