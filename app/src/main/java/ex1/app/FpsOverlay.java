package ex1.app;

import ex1.app.ui.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Writes the FPS in the top-left corner of a grey video frame (ADR-0014): light text on a dark,
 * rounded label, sized to the frame. The label is drawn once per text into a small colour image and
 * blended into the frame's own grey values (as the video stores them), so it looks the same in
 * every player and costs a few microseconds per frame. A frame too small for a readable label gets
 * none.
 */
final class FpsOverlay
{
    private static final double TEXT_PER_HEIGHT = 1.0 / 17;
    private static final int MIN_TEXT = 9;
    private static final int MAX_TEXT = 24;
    /** The label may cover at most this share of the frame's width. */
    private static final double MAX_WIDTH_SHARE = 0.6;
    private static final int LABEL_GREY = 18;
    private static final int LABEL_ALPHA = 190;
    private static final int TEXT_GREY = 242;

    private String text;
    private int size;
    private BufferedImage stamp;
    private int[] stampPixels;
    private boolean broken;

    /**
     * @param (byte[]) frame width*height grey bytes, changed in place
     * @param (int) width frame width
     * @param (int) height frame height
     * @param (String) label the text, e.g. "2,110 FPS"
     * @return true when the label was drawn, false when the frame is too small (or this Java cannot
     *         draw text at all, e.g. a server without fonts: the video is then written without it)
     */
    boolean draw(byte[] frame, int width, int height, String label)
    {
        if (broken)
        {
            return false;
        }
        int textSize = Math.max(MIN_TEXT, Math.min(MAX_TEXT, (int) Math.round(height * TEXT_PER_HEIGHT)));
        if (stamp == null || !label.equals(text) || textSize != size)
        {
            try
            {
                stamp = makeStamp(label, textSize);
            }
            catch (RuntimeException e)
            {
                broken = true;
                return false;
            }
            catch (LinkageError e)
            {
                broken = true;
                return false;
            }
            text = label;
            size = textSize;
            stampPixels = stamp.getRGB(0, 0, stamp.getWidth(), stamp.getHeight(), null, 0, stamp.getWidth());
        }
        int margin = Math.max(3, height / 48);
        if (stamp.getWidth() > width * MAX_WIDTH_SHARE || stamp.getHeight() + 2 * margin > height)
        {
            return false;
        }
        blend(frame, width, margin, margin);
        return true;
    }

    /**
     * @param (String) label the text
     * @param (int) textSize font size in pixels
     * @return the label as an image with transparency
     */
    private static BufferedImage makeStamp(String label, int textSize)
    {
        Font font = Theme.semibold(textSize);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        int padX = Math.max(3, textSize / 2);
        int padY = Math.max(2, textSize / 4);
        int w = fm.stringWidth(label) + 2 * padX;
        int h = fm.getAscent() + fm.getDescent() + 2 * padY;
        pg.dispose();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(new Color(LABEL_GREY, LABEL_GREY, LABEL_GREY, LABEL_ALPHA));
        g.fillRoundRect(0, 0, w, h, h, h);
        g.setFont(font);
        g.setColor(new Color(TEXT_GREY, TEXT_GREY, TEXT_GREY));
        g.drawString(label, padX, padY + fm.getAscent());
        g.dispose();
        return img;
    }

    /**
     * Mixes the label into the frame: frame = frame * (1 - a) + label * a, pixel by pixel.
     *
     * @param (byte[]) frame grey bytes
     * @param (int) width frame width
     * @param (int) x0 left of the label
     * @param (int) y0 top of the label
     */
    private void blend(byte[] frame, int width, int x0, int y0)
    {
        int w = stamp.getWidth();
        int h = stamp.getHeight();
        int[] argb = stampPixels;
        for (int y = 0; y < h; y++)
        {
            int row = (y0 + y) * width + x0;
            for (int x = 0; x < w; x++)
            {
                int p = argb[y * w + x];
                int a = (p >>> 24) & 0xFF;
                if (a == 0)
                {
                    continue;
                }
                int v = (p >> 16) & 0xFF;
                int old = frame[row + x] & 0xFF;
                frame[row + x] = (byte) ((old * (255 - a) + v * a + 127) / 255);
            }
        }
    }
}
