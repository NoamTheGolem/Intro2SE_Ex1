package ex1.app.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * A thin scroll bar for {@link Theme}: a rounded thumb on the panel's own colour, no arrow buttons.
 */
public final class FlatScrollBarUI extends BasicScrollBarUI
{
    private static final int THICKNESS = 10;
    private static final int MIN_THUMB = 32;
    private static final int INSET = 2;

    /**
     * @param (JComponent) c the scroll bar
     * @return a new UI
     */
    public static ComponentUI createUI(JComponent c)
    {
        return new FlatScrollBarUI();
    }

    @Override
    protected void configureScrollBarColors()
    {
        super.configureScrollBarColors();
        thumbColor = Theme.RAISED_HOVER;
        trackColor = Theme.SURFACE;
    }

    @Override
    protected JButton createDecreaseButton(int orientation)
    {
        return noButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation)
    {
        return noButton();
    }

    @Override
    public Dimension getPreferredSize(JComponent c)
    {
        if (scrollbar.getOrientation() == JScrollBar.VERTICAL)
        {
            return new Dimension(THICKNESS, 3 * MIN_THUMB);
        }
        return new Dimension(3 * MIN_THUMB, THICKNESS);
    }

    @Override
    protected Dimension getMinimumThumbSize()
    {
        return new Dimension(MIN_THUMB, MIN_THUMB);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle r)
    {
        Color behind = Theme.SURFACE;
        if (c.getParent() != null)
        {
            behind = c.getParent().getBackground();
        }
        g.setColor(behind);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r)
    {
        if (r.isEmpty() || !scrollbar.isEnabled())
        {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color colour = thumbColor;
        if (isThumbRollover() || isDragging)
        {
            colour = Theme.TEXT_FAINT;
        }
        g2.setColor(colour);
        int arc = Math.min(r.width, r.height) - 2 * INSET;
        g2.fillRoundRect(r.x + INSET, r.y + INSET, r.width - 2 * INSET, r.height - 2 * INSET, arc, arc);
        g2.dispose();
    }

    /**
     * @return an invisible stand-in for the arrow buttons
     */
    private static JButton noButton()
    {
        JButton b = new JButton();
        Dimension none = new Dimension(0, 0);
        b.setPreferredSize(none);
        b.setMinimumSize(none);
        b.setMaximumSize(none);
        b.setFocusable(false);
        b.setBorder(BorderFactory.createEmptyBorder());
        return b;
    }
}
