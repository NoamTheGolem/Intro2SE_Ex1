package ex1.app;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import javax.swing.JComponent;

/**
 * The 2-D view: the cross-section of the current plane, scaled to fit, one voxel per pixel at
 * 100 %. This is the image the video frames are made of.
 */
final class SliceView extends JComponent
{
    private static final long serialVersionUID = 1L;
    private static final int MARGIN = 16;
    private static final Color BACKGROUND = new Color(0x15171B);
    private static final Color TEXT = new Color(150, 156, 168);

    private transient BufferedImage image;
    private String caption = "";

    /** Dark panel. */
    SliceView()
    {
        setOpaque(true);
        setBackground(BACKGROUND);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
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
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] dst = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(grey, 0, dst, 0, dst.length);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0)
    {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (image == null)
        {
            g.setColor(TEXT);
            String hint = "2-D view: the cross-section of the plane";
            g.drawString(hint, (getWidth() - g.getFontMetrics().stringWidth(hint)) / 2, getHeight() / 2);
            g.dispose();
            return;
        }
        int availW = getWidth() - 2 * MARGIN;
        int availH = getHeight() - 2 * MARGIN - 18;
        double scale = Math.min(availW / (double) image.getWidth(), availH / (double) image.getHeight());
        int w = Math.max(1, (int) (image.getWidth() * scale));
        int h = Math.max(1, (int) (image.getHeight() * scale));
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - 18 - h) / 2;
        if (scale >= 1.0)
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        else
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.drawImage(image, x, y, w, h, null);
        g.setColor(new Color(0xFF9F1C));
        g.drawRect(x - 1, y - 1, w + 1, h + 1);
        g.setColor(TEXT);
        g.drawString(caption, 8, getHeight() - 8);
        g.dispose();
    }
}
