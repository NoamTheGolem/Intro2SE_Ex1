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
    private FocusListener focusListener;

    /**
     * @param (JComponent) c the field
     * @return a new UI
     */
    public static ComponentUI createUI(JComponent c)
    {
        return new FlatTextFieldUI();
    }

    @Override
    protected void installListeners()
    {
        super.installListeners();
        final JTextComponent c = getComponent();
        focusListener = new FocusListener()
        {
            @Override
            public void focusGained(FocusEvent e)
            {
                c.repaint();
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                c.repaint();
            }
        };
        c.addFocusListener(focusListener);
    }

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

    @Override
    protected void paintBackground(Graphics g)
    {
        JTextComponent c = getComponent();
        Graphics2D g2 = (Graphics2D) g.create();
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
        private static final Insets PADDING = new Insets(5, 8, 5, 8);

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

        @Override
        public Insets getBorderInsets(Component c)
        {
            return new Insets(PADDING.top, PADDING.left, PADDING.bottom, PADDING.right);
        }

        @Override
        public boolean isBorderOpaque()
        {
            return false;
        }
    }
}
