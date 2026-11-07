package ex1.app.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.JTextComponent;

/**
 * Text fields for {@link Theme}: a dark, rounded well; the border turns blue while the field has
 * the keyboard focus, so it is clear where typing goes.
 */
public final class FlatTextFieldUI extends BasicTextFieldUI
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Repaints the border when the focus comes and goes. */
    private FocusListener focusListener;

    /**
     * Creates the look of one text field (called by Swing).
     * @param (JComponent) c The field.
     * @return (ComponentUI) A new UI.
     */
    public static ComponentUI createUI(JComponent c)
    {
        return new FlatTextFieldUI();
    }

    /** Listens to the focus, to repaint the border in its focus colour. */
    @Override
    protected void installListeners()
    {
        super.installListeners();
        final JTextComponent c = getComponent();
        focusListener = new FocusListener()
        {
            /**
             * Repaints the border blue.
             * @param (FocusEvent) e The focus change.
             */
            @Override
            public void focusGained(FocusEvent e)
            {
                c.repaint();
            }

            /**
             * Repaints the border grey.
             * @param (FocusEvent) e The focus change.
             */
            @Override
            public void focusLost(FocusEvent e)
            {
                c.repaint();
            }
        };
        c.addFocusListener(focusListener);
    }

    /** Removes the focus listener when the field gets another look. */
    @Override
    protected void uninstallListeners()
    {
        super.uninstallListeners();
        if (focusListener != null)
        {
            getComponent().removeFocusListener(focusListener);
            focusListener = null;
        }
    }

    /**
     * Paints the rounded well; outside its corners the panel behind shows.
     * @param (Graphics) g Graphics of the field.
     */
    @Override
    protected void paintBackground(Graphics g)
    {
        JTextComponent c  = getComponent();
        Graphics2D     g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Outside the rounded corners the panel behind shows.
        Color outside = Theme.SURFACE;
        if (c.getParent() != null)
        {
            outside = c.getParent().getBackground();
        }
        g2.setColor(outside);
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
        g2.setColor(c.getBackground());
        g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, Theme.ARC, Theme.ARC);
        g2.dispose();
    }

    /** The rounded outline of a field; blue while it has the focus. */
    static final class FieldBorder implements Border, UIResource
    {
        /** Space between the border and the text. */
        private static final Insets PADDING = new Insets(5, 8, 5, 8);

        /**
         * Draws the rounded outline: blue with the focus, faint when disabled.
         * @param (Component) c The field.
         * @param (Graphics) g Graphics of the field.
         * @param (int) x Left.
         * @param (int) y Top.
         * @param (int) width Width.
         * @param (int) height Height.
         */
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color colour = Theme.LINE;
            if (c.hasFocus())
            {
                colour = Theme.FOCUS;
            }
            else if (!c.isEnabled())
            {
                colour = Theme.mix(Theme.LINE, Theme.SURFACE, 0.4);
            }
            g2.setColor(colour);
            g2.drawRoundRect(x, y, width - 1, height - 1, Theme.ARC, Theme.ARC);
            g2.dispose();
        }

        /**
         * Gives the padding around the text.
         * @param (Component) c The field.
         * @return (Insets) A new copy (Swing may change the one it gets).
         */
        @Override
        public Insets getBorderInsets(Component c)
        {
            return new Insets(PADDING.top, PADDING.left, PADDING.bottom, PADDING.right);
        }

        /**
         * Tells Swing that the corners are see-through.
         * @return (boolean) False.
         */
        @Override
        public boolean isBorderOpaque()
        {
            return false;
        }
    }
}
