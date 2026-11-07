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

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Version of the serialized form (Swing components are Serializable). */
    private static final long serialVersionUID = 1L;

    /** Sizes, in pixels. */
    private static final int PAD_X   = 12;
    private static final int HEIGHT  = 28;
    private static final int DOT     = 7;
    private static final int DOT_GAP = 7;

    /** What the badge says, and whether it is live (amber dot). */
    private String  text = "";
    private boolean live;

    /** Creates an empty badge. */
    public Badge()
    {
        setFont(Theme.semibold(Theme.TEXT_SIZE));
        setOpaque(false);
    }

    /**
     * Changes what the badge says; the layout is redone only when the text (and so the width)
     * changes.
     * @param (String) newText What the badge says.
     * @param (boolean) isLive True while the number is being measured.
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
     * Gives the text on show.
     * @return (String) The text shown.
     */
    public String text()
    {
        return text;
    }

    /**
     * Tells whether the badge is live.
     * @return (boolean) True while live.
     */
    public boolean isLive()
    {
        return live;
    }

    /**
     * Asks for room for the dot and the text.
     * @return (Dimension) The badge's size.
     */
    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics fm = getFontMetrics(getFont());
        // Room for the dot always, so the badge does not jump when it turns live.
        int w = PAD_X * 2 + DOT + DOT_GAP + fm.stringWidth(text);
        return new Dimension(w, HEIGHT);
    }

    /**
     * Draws the rounded badge: the dot (amber while live), then the text.
     * @param (Graphics) g Graphics of the badge.
     */
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
