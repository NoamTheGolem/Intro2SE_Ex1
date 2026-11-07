package ex1.app.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicBorders;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * Flat, rounded buttons for {@link Theme}: graphite with a thin border (the usual button), filled
 * amber ({@link Theme#PRIMARY}, and the default button of a dialog), background only under the
 * mouse ({@link Theme#GHOST}, or a button whose content area is not filled), and the two halves
 * of a switch ({@link Theme#SEGMENT_FIRST}, {@link Theme#SEGMENT_LAST}; selected = lit). Used for
 * JButton and JToggleButton. The focus ring shows only when the focus came from the keyboard (Tab),
 * not after every click.
 */
public final class FlatButtonUI extends BasicButtonUI
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Client property: true while the focus came from the keyboard. */
    private static final String KEYBOARD_FOCUS = "ex1.keyboardFocus";

    /** The usual button's variant. */
    private static final String SECONDARY = "secondary";

    /** How much a ghost button lights up under the mouse and when pressed (of 255). */
    private static final int GHOST_HOVER_ALPHA   = 24;
    private static final int GHOST_PRESSED_ALPHA = 44;

    /** Width of the focus ring. */
    private static final float RING_WIDTH = 2f;

    /** Shows the ring only for keyboard focus. */
    private FocusListener focusListener;

    /**
     * Creates the look of one button (called by Swing).
     * @param (JComponent) c The button.
     * @return (ComponentUI) A new UI (each button has its own focus listener).
     */
    public static ComponentUI createUI(JComponent c)
    {
        return new FlatButtonUI();
    }

    /**
     * Makes the button see-through around its rounded shape, lit under the mouse, and padded by its
     * margin (so setMargin() makes a compact button).
     * @param (AbstractButton) b The button.
     */
    @Override
    protected void installDefaults(AbstractButton b)
    {
        super.installDefaults(b);
        LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);
        LookAndFeel.installProperty(b, "rolloverEnabled", Boolean.TRUE);
        if (b.getBorder() == null || b.getBorder() instanceof UIResource)
        {
            // The padding is the button's margin, so setMargin() makes a compact button.
            b.setBorder(new BorderUIResource(new BasicBorders.MarginBorder()));
        }
    }

    /**
     * Listens to the focus, to show the ring only when the focus came from the keyboard.
     * @param (AbstractButton) b The button.
     */
    @Override
    protected void installListeners(final AbstractButton b)
    {
        super.installListeners(b);
        focusListener = new FocusListener()
        {
            /**
             * Shows the ring when Tab brought the focus here, not after a click.
             * @param (FocusEvent) e The focus change.
             */
            @Override
            public void focusGained(FocusEvent e)
            {
                boolean keyboard = e.getCause().name().startsWith("TRAVERSAL");
                b.putClientProperty(KEYBOARD_FOCUS, Boolean.valueOf(keyboard));
                b.repaint();
            }

            /**
             * Hides the ring.
             * @param (FocusEvent) e The focus change.
             */
            @Override
            public void focusLost(FocusEvent e)
            {
                b.putClientProperty(KEYBOARD_FOCUS, Boolean.FALSE);
                b.repaint();
            }
        };
        b.addFocusListener(focusListener);
    }

    /**
     * Removes the focus listener when the button gets another look.
     * @param (AbstractButton) b The button.
     */
    @Override
    protected void uninstallListeners(AbstractButton b)
    {
        super.uninstallListeners(b);
        if (focusListener != null)
        {
            b.removeFocusListener(focusListener);
            focusListener = null;
        }
    }

    /**
     * Paints the button: its fill and border for its kind and state, the focus ring, then the icon
     * and the text.
     * @param (Graphics) g Graphics of the button.
     * @param (JComponent) c The button.
     */
    @Override
    public void paint(Graphics g, JComponent c)
    {
        AbstractButton b       = (AbstractButton) c;
        String         variant = variant(b);
        Graphics2D     g2      = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int   w     = c.getWidth();
        int   h     = c.getHeight();
        Shape shape = shape(variant, w, h);
        Color fill  = fill(b, variant);
        if (fill != null)
        {
            g2.setColor(fill);
            g2.fill(shape);
        }
        Color edge = edge(b, variant);
        if (edge != null)
        {
            g2.setClip(0, 0, w, h);
            g2.setColor(edge);
            g2.draw(shape(variant, w - 1, h - 1));
            if (Theme.SEGMENT_FIRST.equals(variant))
            {
                // The line between the two halves of a switch.
                g2.drawLine(w - 1, 0, w - 1, h - 1);
            }
        }
        if (Boolean.TRUE.equals(b.getClientProperty(KEYBOARD_FOCUS)) && b.isFocusPainted())
        {
            g2.setColor(Theme.FOCUS);
            g2.setStroke(new BasicStroke(RING_WIDTH));
            g2.draw(new RoundRectangle2D.Double(1, 1, w - 2.0, h - 2.0, Theme.ARC, Theme.ARC));
        }
        g2.dispose();
        super.paint(g, c);
    }

    /**
     * Writes the text in the colour of the button's kind and state.
     * @param (Graphics) g Graphics of the button.
     * @param (AbstractButton) b The button.
     * @param (Rectangle) textRect Where the text goes.
     * @param (String) text The text.
     */
    @Override
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text)
    {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(textColour(b, variant(b)));
        int shift = getTextShiftOffset();
        BasicGraphicsUtils.drawStringUnderlineCharAt(b, (Graphics2D) g, text, b.getDisplayedMnemonicIndex(),
                textRect.x + shift, textRect.y + fm.getAscent() + shift);
    }

    /**
     * Paints nothing: the pressed look is part of the fill.
     * @param (Graphics) g Graphics of the button.
     * @param (AbstractButton) b The button.
     */
    @Override
    protected void paintButtonPressed(Graphics g, AbstractButton b)
    {
        // The pressed look is part of the fill (see fill).
    }

    /**
     * Paints nothing: the focus ring is part of paint, and only for keyboard focus.
     * @param (Graphics) g Graphics of the button.
     * @param (AbstractButton) b The button.
     * @param (Rectangle) viewRect The button's inside.
     * @param (Rectangle) textRect Where the text is.
     * @param (Rectangle) iconRect Where the icon is.
     */
    @Override
    protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect)
    {
        // The focus ring is drawn in paint, only for keyboard focus.
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Finds which kind of button this is.
     * @param (AbstractButton) b Button.
     * @return (String) Its variant: the client property, else primary for a dialog's default
     *                  button, ghost for a button without a filled content area, else the usual
     *                  (secondary) button.
     */
    static String variant(AbstractButton b)
    {
        Object v = b.getClientProperty(Theme.VARIANT);
        if (v instanceof String)
        {
            return (String) v;
        }
        if (b instanceof JButton && ((JButton) b).isDefaultButton())
        {
            return Theme.PRIMARY;
        }
        if (!b.isContentAreaFilled())
        {
            return Theme.GHOST;
        }
        return SECONDARY;
    }

    /**
     * Gives the button's outline.
     * @param (String) variant Variant.
     * @param (double) w Width.
     * @param (double) h Height.
     * @return (Shape) The outline: rounded, or for a half of a switch rounded on its outer side
     *                 only.
     */
    private static Shape shape(String variant, double w, double h)
    {
        double arc = Theme.ARC;
        if (Theme.SEGMENT_FIRST.equals(variant))
        {
            return new RoundRectangle2D.Double(0, 0, w + arc, h, arc, arc);
        }
        if (Theme.SEGMENT_LAST.equals(variant))
        {
            return new RoundRectangle2D.Double(-arc, 0, w + arc, h, arc, arc);
        }
        return new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
    }

    /**
     * Chooses the background for the button's kind and state (disabled, pressed, under the mouse,
     * selected).
     * @param (AbstractButton) b Button.
     * @param (String) variant Variant.
     * @return (Color) Background colour for its state, or null for none.
     */
    private static Color fill(AbstractButton b, String variant)
    {
        ButtonModel m       = b.getModel();
        boolean     pressed = m.isArmed() && m.isPressed();
        boolean     over    = m.isRollover();
        if (Theme.GHOST.equals(variant))
        {
            if (!b.isEnabled())
            {
                return null;
            }
            if (pressed)
            {
                return Theme.alpha(Theme.TEXT, GHOST_PRESSED_ALPHA);
            }
            if (over)
            {
                return Theme.alpha(Theme.TEXT, GHOST_HOVER_ALPHA);
            }
            return null;
        }
        if (!b.isEnabled())
        {
            return Theme.mix(Theme.RAISED, Theme.SURFACE, 0.5);
        }
        if (Theme.PRIMARY.equals(variant))
        {
            if (pressed)
            {
                return Theme.PLANE_PRESSED;
            }
            if (over)
            {
                return Theme.PLANE_HOVER;
            }
            return Theme.PLANE;
        }
        if (isSegment(variant))
        {
            if (m.isSelected())
            {
                return Theme.RAISED_PRESSED;
            }
            if (pressed)
            {
                return Theme.RAISED_HOVER;
            }
            if (over)
            {
                return Theme.mix(Theme.WELL, Theme.RAISED, 0.7);
            }
            return Theme.WELL;
        }
        if (pressed)
        {
            return Theme.RAISED_PRESSED;
        }
        if (over)
        {
            return Theme.RAISED_HOVER;
        }
        return Theme.RAISED;
    }

    /**
     * Chooses the border colour (filled and ghost buttons have none).
     * @param (AbstractButton) b Button.
     * @param (String) variant Variant.
     * @return (Color) Border colour, or null for none.
     */
    private static Color edge(AbstractButton b, String variant)
    {
        if (Theme.GHOST.equals(variant) || Theme.PRIMARY.equals(variant) || !b.isBorderPainted())
        {
            return null;
        }
        if (!b.isEnabled())
        {
            return Theme.mix(Theme.LINE, Theme.SURFACE, 0.4);
        }
        return Theme.LINE;
    }

    /**
     * Chooses the text colour for the button's kind and state; its icon uses the same.
     * @param (AbstractButton) b Button.
     * @param (String) variant Variant.
     * @return (Color) Text colour for its state.
     */
    static Color textColour(AbstractButton b, String variant)
    {
        if (!b.isEnabled())
        {
            return Theme.TEXT_FAINT;
        }
        if (Theme.PRIMARY.equals(variant))
        {
            return Theme.ON_PLANE;
        }
        if (Theme.GHOST.equals(variant))
        {
            if (b.getModel().isRollover())
            {
                return Theme.TEXT;
            }
            return Theme.TEXT_DIM;
        }
        if (isSegment(variant) && !b.getModel().isSelected())
        {
            return Theme.TEXT_DIM;
        }
        return Theme.TEXT;
    }

    /**
     * Tells whether a variant is half of a two-part switch.
     * @param (String) variant Variant.
     * @return (boolean) True for either half of a switch.
     */
    private static boolean isSegment(String variant)
    {
        return Theme.SEGMENT_FIRST.equals(variant) || Theme.SEGMENT_LAST.equals(variant);
    }
}
