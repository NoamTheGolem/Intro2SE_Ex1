package ex1.app.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.plaf.UIResource;

/**
 * Small drawn icons, so the look needs no image files and no font has to have an arrow or a play
 * symbol (some Windows UI fonts lack them). Icons on buttons take their colour from the button's
 * state, like its text.
 */
public final class Icons
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** The kinds of dialog icon. */
    public static final int INFORMATION = 0;
    public static final int WARNING     = 1;
    public static final int ERROR       = 2;
    public static final int QUESTION    = 3;

    /** Sizes of the icons, in pixels. */
    private static final int DIALOG_SIZE = 30;
    private static final int ARROW_SIZE  = 16;
    private static final int PLAY_SIZE   = 12;
    private static final int MARK_SIZE   = 20;
    private static final int STEP_SIZE   = 20;

    /** Factory methods only; never instantiated. */
    private Icons()
    {
    }

    // ==========================================
    //                 FACTORIES
    // ==========================================

    /**
     * Makes the icon of a message dialog.
     * @param (int) kind INFORMATION, WARNING, ERROR or QUESTION.
     * @return (Icon) A round icon for a message dialog.
     */
    public static Icon dialog(int kind)
    {
        return new DialogIcon(kind);
    }

    /**
     * Makes a chevron.
     * @param (boolean) right True points right.
     * @return (Icon) A chevron for a "previous / next" button.
     */
    public static Icon arrow(boolean right)
    {
        return new ArrowIcon(right);
    }

    /**
     * Makes the Play / Stop icon.
     * @param (boolean) playing True draws "stop" (a square), false "play" (a triangle).
     * @return (Icon) The icon of the Play / Stop button.
     */
    public static Icon play(boolean playing)
    {
        return new PlayIcon(playing);
    }

    /**
     * Makes the app's mark for the toolbar.
     * @return (Icon) The app's mark: an isosceles triangle, the plane of the assignment.
     */
    public static Icon mark()
    {
        return new MarkIcon();
    }

    /**
     * Makes the number of a step.
     * @param (int) number Step number.
     * @return (Icon) The number in a small amber ring, for the numbered sections of the control
     *                column.
     */
    public static Icon step(int number)
    {
        return new StepIcon(number);
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Chooses an icon's colour from the component it is on, so it follows the button's state.
     * @param (Component) c The component being painted, usually a button.
     * @return (Color) The colour its icon should have now: as its text.
     */
    private static Color colourFor(Component c)
    {
        if (c instanceof AbstractButton)
        {
            AbstractButton b = (AbstractButton) c;
            return FlatButtonUI.textColour(b, FlatButtonUI.variant(b));
        }
        if (c != null && !c.isEnabled())
        {
            return Theme.TEXT_FAINT;
        }
        return Theme.TEXT;
    }

    /**
     * Prepares a graphics copy for drawing an icon.
     * @param (Graphics) g Graphics.
     * @return (Graphics2D) A copy with smooth edges.
     */
    private static Graphics2D smooth(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    // ==========================================
    //                 THE ICONS
    // ==========================================

    /** A filled circle with i, !, or ?. */
    private static final class DialogIcon implements Icon, UIResource
    {
        /** Which dialog. */
        private final int kind;

        /**
         * Creates a dialog icon.
         * @param (int) kind Which dialog.
         */
        DialogIcon(int kind)
        {
            this.kind = kind;
        }

        /**
         * Draws the coloured circle with its sign: i (information), ! (warning, error) or ?
         * (question).
         * @param (Component) c The component it is on.
         * @param (Graphics) g Graphics.
         * @param (int) x Left.
         * @param (int) y Top.
         */
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2     = smooth(g);
            Color      colour = Theme.FOCUS;
            String     glyph  = "i";
            if (kind == WARNING)
            {
                colour = Theme.WARNING;
                glyph = "!";
            }
            else if (kind == ERROR)
            {
                colour = Theme.ERROR;
                glyph = "!";
            }
            else if (kind == QUESTION)
            {
                colour = Theme.PLANE;
                glyph = "?";
            }
            g2.setColor(colour);
            g2.fillOval(x, y, DIALOG_SIZE, DIALOG_SIZE);
            g2.setColor(Theme.ON_PLANE);
            g2.setFont(Theme.semibold(18));
            FontMetrics fm = g2.getFontMetrics();
            int         tx = x + (DIALOG_SIZE - fm.stringWidth(glyph)) / 2;
            int         ty = y + (DIALOG_SIZE + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(glyph, tx, ty);
            g2.dispose();
        }

        /**
         * Gives the width.
         * @return (int) Pixels.
         */
        @Override
        public int getIconWidth()
        {
            return DIALOG_SIZE;
        }

        /**
         * Gives the height.
         * @return (int) Pixels.
         */
        @Override
        public int getIconHeight()
        {
            return DIALOG_SIZE;
        }
    }

    /** A chevron, left or right. */
    private static final class ArrowIcon implements Icon
    {
        /** True points right. */
        private final boolean right;

        /**
         * Creates a chevron.
         * @param (boolean) right True points right.
         */
        ArrowIcon(boolean right)
        {
            this.right = right;
        }

        /**
         * Draws the chevron in the colour of its button's text.
         * @param (Component) c The button it is on.
         * @param (Graphics) g Graphics.
         * @param (int) x Left.
         * @param (int) y Top.
         */
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = smooth(g);
            g2.setColor(colourFor(c));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double p = new Path2D.Double();
            double        s = ARROW_SIZE;
            if (right)
            {
                p.moveTo(x + s * 0.38, y + s * 0.2);
                p.lineTo(x + s * 0.68, y + s * 0.5);
                p.lineTo(x + s * 0.38, y + s * 0.8);
            }
            else
            {
                p.moveTo(x + s * 0.62, y + s * 0.2);
                p.lineTo(x + s * 0.32, y + s * 0.5);
                p.lineTo(x + s * 0.62, y + s * 0.8);
            }
            g2.draw(p);
            g2.dispose();
        }

        /**
         * Gives the width.
         * @return (int) Pixels.
         */
        @Override
        public int getIconWidth()
        {
            return ARROW_SIZE;
        }

        /**
         * Gives the height.
         * @return (int) Pixels.
         */
        @Override
        public int getIconHeight()
        {
            return ARROW_SIZE;
        }
    }

    /** Play (a triangle) or stop (a square). */
    private static final class PlayIcon implements Icon
    {
        /** True draws the stop square. */
        private final boolean playing;

        /**
         * Creates the Play / Stop icon.
         * @param (boolean) playing True draws the stop square.
         */
        PlayIcon(boolean playing)
        {
            this.playing = playing;
        }

        /**
         * Draws the play triangle or the stop square in the colour of its button's text.
         * @param (Component) c The button it is on.
         * @param (Graphics) g Graphics.
         * @param (int) x Left.
         * @param (int) y Top.
         */
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = smooth(g);
            g2.setColor(colourFor(c));
            double s = PLAY_SIZE;
            if (playing)
            {
                g2.fillRoundRect(x + 1, y + 1, PLAY_SIZE - 2, PLAY_SIZE - 2, 3, 3);
            }
            else
            {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(x + s * 0.15, y);
                p.lineTo(x + s, y + s * 0.5);
                p.lineTo(x + s * 0.15, y + s);
                p.closePath();
                g2.fill(p);
            }
            g2.dispose();
        }

        /**
         * Gives the width.
         * @return (int) Pixels.
         */
        @Override
        public int getIconWidth()
        {
            return PLAY_SIZE;
        }

        /**
         * Gives the height.
         * @return (int) Pixels.
         */
        @Override
        public int getIconHeight()
        {
            return PLAY_SIZE;
        }
    }

    /** The plane's triangle, outlined in amber, with its three points. */
    private static final class MarkIcon implements Icon
    {
        /**
         * Draws the triangle, see-through amber with an amber outline and a dot on each point.
         * @param (Component) c The component it is on.
         * @param (Graphics) g Graphics.
         * @param (int) x Left.
         * @param (int) y Top.
         */
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D    g2 = smooth(g);
            double        s  = MARK_SIZE;
            double        ax = x + s / 2;
            double        ay = y + 2;
            double        bx = x + 2;
            double        by = y + s - 3;
            double        cx = x + s - 2;
            Path2D.Double p  = new Path2D.Double();
            p.moveTo(ax, ay);
            p.lineTo(bx, by);
            p.lineTo(cx, by);
            p.closePath();
            g2.setColor(Theme.alpha(Theme.PLANE, 60));
            g2.fill(p);
            g2.setColor(Theme.PLANE);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(p);
            double r = 2.2;
            g2.fill(new Ellipse2D.Double(ax - r, ay - r, 2 * r, 2 * r));
            g2.fill(new Ellipse2D.Double(bx - r, by - r, 2 * r, 2 * r));
            g2.fill(new Ellipse2D.Double(cx - r, by - r, 2 * r, 2 * r));
            g2.dispose();
        }

        /**
         * Gives the width.
         * @return (int) Pixels.
         */
        @Override
        public int getIconWidth()
        {
            return MARK_SIZE;
        }

        /**
         * Gives the height.
         * @return (int) Pixels.
         */
        @Override
        public int getIconHeight()
        {
            return MARK_SIZE;
        }
    }

    /** A step number in an amber ring. */
    private static final class StepIcon implements Icon
    {
        /** The number, as text. */
        private final String number;

        /**
         * Creates a step number.
         * @param (int) number The step.
         */
        StepIcon(int number)
        {
            this.number = Integer.toString(number);
        }

        /**
         * Draws the ring and the number centred in it.
         * @param (Component) c The component it is on.
         * @param (Graphics) g Graphics.
         * @param (int) x Left.
         * @param (int) y Top.
         */
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.alpha(Theme.PLANE, 40));
            g2.fillOval(x, y, STEP_SIZE - 1, STEP_SIZE - 1);
            g2.setColor(Theme.PLANE);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(x, y, STEP_SIZE - 1, STEP_SIZE - 1);
            g2.setFont(Theme.semibold(12));
            FontMetrics fm = g2.getFontMetrics();
            int         tx = x + (STEP_SIZE - fm.stringWidth(number)) / 2;
            int         ty = y + (STEP_SIZE + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.drawString(number, tx, ty);
            g2.dispose();
        }

        /**
         * Gives the width.
         * @return (int) Pixels.
         */
        @Override
        public int getIconWidth()
        {
            return STEP_SIZE;
        }

        /**
         * Gives the height.
         * @return (int) Pixels.
         */
        @Override
        public int getIconHeight()
        {
            return STEP_SIZE;
        }
    }
}
