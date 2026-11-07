package ex1.app;

import ex1.app.ui.Theme;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import javax.swing.JComponent;

/**
 * The 2-D view: the projection of the current plane (the image E of the assignment), scaled to
 * fit, one voxel per pixel at 100 %. This is the image the video frames are made of. The corners
 * are labelled with the plane's points: the rows run from the top edge (a, or a1 - a2) down to
 * the base (b - c), each row from the a1/b side on the left to the a2/c side on the right.
 * While the video plays, its FPS is shown in the picture's corner, as in the exported video.
 */
final class SliceView extends JComponent
{
    private static final long serialVersionUID = 1L;
    private static final int MARGIN = 18;
    private static final int CAPTION_SPACE = 20;
    private static final Color BLACK_FRAME_BORDER = new Color(0xC03030);
    private static final int PILL_HEIGHT = 26;
    private static final int PILL_PAD = 11;
    private static final int PILL_DOT = 7;
    private static final int PILL_INSET = 10;
    private static final int PILL_ALPHA = 225;

    private transient BufferedImage image;
    private String caption = "";
    private boolean triangle = true;
    private String fps;

    /** Dark panel. */
    SliceView()
    {
        setOpaque(true);
        setBackground(Theme.WELL);
        setFont(Theme.font(Theme.SMALL_SIZE));
    }

    /**
     * @param (byte[]) grey cross-section pixels, or null to clear
     * @param (int) width columns
     * @param (int) height rows
     * @param (String) text caption shown under the image
     */
    void setImage(byte[] grey, int width, int height, String text)
    {
        caption = text;
        if (grey == null)
        {
            image = null;
        }
        else
        {
            if (image == null || image.getWidth() != width || image.getHeight() != height)
            {
                image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            }
            byte[] dst = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(grey, 0, dst, 0, dst.length);
        }
        repaint();
    }

    /**
     * @param (boolean) isTriangle true labels the apex a, false labels a1 and a2
     */
    void setCornerLabels(boolean isTriangle)
    {
        if (triangle != isTriangle)
        {
            triangle = isTriangle;
            repaint();
        }
    }

    /**
     * @param (String) text the FPS to show on the picture, e.g. "2,110 FPS"; null shows none
     */
    void setFps(String text)
    {
        fps = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0)
    {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Theme.WELL);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont());
        if (image == null && !caption.isEmpty())
        {
            // A bad plane or row: the video gets a black frame here, so show one, with the reason.
            int side = Math.max(16, Math.min(getWidth() - 2 * MARGIN, getHeight() - 2 * MARGIN - CAPTION_SPACE));
            int x = (getWidth() - side) / 2;
            int y = (getHeight() - CAPTION_SPACE - side) / 2;
            g.setColor(Color.BLACK);
            g.fillRect(x, y, side, side);
            g.setColor(BLACK_FRAME_BORDER);
            g.drawRect(x - 1, y - 1, side + 1, side + 1);
            g.setColor(Theme.TEXT_DIM);
            String label = "black frame";
            g.drawString(label, (getWidth() - g.getFontMetrics().stringWidth(label)) / 2, y + side / 2);
            g.setColor(Theme.TEXT_FAINT);
            g.drawString(caption, 10, getHeight() - 10);
            g.dispose();
            return;
        }
        if (image == null)
        {
            g.setColor(Theme.TEXT_FAINT);
            String hint = "The cut appears here: the picture of the plane, one frame of the video";
            g.drawString(hint, (getWidth() - g.getFontMetrics().stringWidth(hint)) / 2, getHeight() / 2);
            g.dispose();
            return;
        }
        int availW = getWidth() - 2 * MARGIN;
        int availH = getHeight() - 2 * MARGIN - CAPTION_SPACE;
        double scale = Math.min(availW / (double) image.getWidth(), availH / (double) image.getHeight());
        int w = Math.max(1, (int) (image.getWidth() * scale));
        int h = Math.max(1, (int) (image.getHeight() * scale));
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - CAPTION_SPACE - h) / 2;
        if (scale >= 1.0)
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        else
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.drawImage(image, x, y, w, h, null);
        g.setColor(Theme.PLANE);
        g.drawRect(x - 1, y - 1, w + 1, h + 1);
        g.setFont(Theme.semibold(Theme.TEXT_SIZE));
        if (triangle)
        {
            g.drawString("a", x + w / 2 - 3, y - 5);
        }
        else
        {
            g.drawString("a1", x - 2, y - 5);
            g.drawString("a2", x + w - 14, y - 5);
        }
        g.drawString("b", x - 13, y + h + 5);
        g.drawString("c", x + w + 5, y + h + 5);
        if (fps != null)
        {
            drawFps(g, x + PILL_INSET, y + PILL_INSET);
        }
        g.setFont(getFont());
        g.setColor(Theme.TEXT_FAINT);
        g.drawString(caption, 10, getHeight() - 10);
        g.dispose();
    }

    /**
     * The FPS label over the picture: a dark pill with an amber dot, as in the toolbar.
     *
     * @param (Graphics2D) g graphics
     * @param (int) x left
     * @param (int) y top
     */
    private void drawFps(Graphics2D g, int x, int y)
    {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setFont(Theme.semibold(Theme.TEXT_SIZE));
        FontMetrics fm = p.getFontMetrics();
        int w = PILL_PAD * 2 + PILL_DOT + PILL_PAD / 2 + 2 + fm.stringWidth(fps);
        p.setColor(Theme.alpha(Theme.CHROME, PILL_ALPHA));
        p.fillRoundRect(x, y, w, PILL_HEIGHT, PILL_HEIGHT, PILL_HEIGHT);
        p.setColor(Theme.PLANE);
        p.fillOval(x + PILL_PAD, y + (PILL_HEIGHT - PILL_DOT) / 2, PILL_DOT, PILL_DOT);
        p.setColor(Theme.TEXT);
        p.drawString(fps, x + PILL_PAD + PILL_DOT + PILL_PAD / 2 + 2, y + (PILL_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);
        p.dispose();
    }

    /**
     * @return the FPS shown on the picture, or null
     */
    String fps()
    {
        return fps;
    }
}
