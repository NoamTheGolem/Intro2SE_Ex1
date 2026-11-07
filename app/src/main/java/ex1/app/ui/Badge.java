package ex1.app.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * A rounded label for a live number (the FPS in the toolbar): dim while there is nothing to
 * measure, bright with an amber dot while it is live.
 */
public final class Badge extends JComponent
{
    private static final long serialVersionUID = 1L;
    private static final int PAD_X = 12;
    private static final int HEIGHT = 28;
    private static final int DOT = 7;
    private static final int DOT_GAP = 7;

    private String text = "";
    private boolean live;

    /** An empty badge. */
    public Badge()
    {
        setFont(Theme.semibold(Theme.TEXT_SIZE));
        setOpaque(false);
    }

    /**
     * @param (String) newText what the badge says
     * @param (boolean) isLive true while the number is being measured
     */
    public void show(String newText, boolean isLive)
    {
        if (newText.equals(text) && isLive == live)
        {
            return;
        }
        boolean wider = !newText.equals(text);
        text = newText;
        live = isLive;
        if (wider)
        {
            revalidate();
        }
        repaint();
    }

    /**
     * @return the text shown
     */
    public String text()
    {
        return text;
    }

    /**
     * @return true while live
     */
    public boolean isLive()
    {
        return live;
    }

    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics fm = getFontMetrics(getFont());
        // Room for the dot always, so the badge does not jump when it turns live.
        int w = PAD_X * 2 + DOT + DOT_GAP + fm.stringWidth(text);
        return new Dimension(w, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = Math.min(HEIGHT, getHeight());
        int y = (getHeight() - h) / 2;
        g2.setColor(Theme.RAISED);
        g2.fillRoundRect(0, y, getWidth() - 1, h - 1, h, h);
        g2.setColor(Theme.LINE);
        g2.drawRoundRect(0, y, getWidth() - 1, h - 1, h, h);
        int x = PAD_X;
        if (live)
        {
            g2.setColor(Theme.PLANE);
        }
        else
        {
            g2.setColor(Theme.TEXT_FAINT);
        }
        g2.fillOval(x, y + (h - DOT) / 2, DOT, DOT);
        x += DOT + DOT_GAP;
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        if (live)
        {
            g2.setColor(Theme.TEXT);
        }
        else
        {
            g2.setColor(Theme.TEXT_DIM);
        }
        g2.drawString(text, x, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }
}
