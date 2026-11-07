package ex1.app.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * The app's look (ADR-0014): a dark graphite workspace, in which the scans' grey pictures read best
 * - as in a radiology reading room - and one colour, amber, for everything that is the plane: its
 * outline and points in the views, the frame of the cut, the Play button, the FPS of its video.
 *
 * Built on Swing's own cross-platform look (Metal) with these colours, the system's UI font (Segoe
 * UI on Windows) and a few controls drawn here ({@link FlatButtonUI}, {@link FlatTextFieldUI},
 * {@link FlatScrollBarUI}, {@link FlatSliderUI}), so it looks the same on Windows and Linux and
 * needs no library (SRS C-3). The system's own file dialogs and title bar stay as they are.
 */
public final class Theme
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Window, toolbar and status bar. */
    public static final Color CHROME = new Color(0x1B1E24);

    /** Panels: the control column, dialogs. */
    public static final Color SURFACE = new Color(0x22262E);

    /** Buttons and other raised controls. */
    public static final Color RAISED         = new Color(0x2D323C);
    public static final Color RAISED_HOVER   = new Color(0x363C48);
    public static final Color RAISED_PRESSED = new Color(0x404755);

    /** Borders and dividers. */
    public static final Color LINE = new Color(0x393F4C);

    /** Behind pictures and in text fields: darker than everything around it. */
    public static final Color WELL = new Color(0x15171B);

    /** Text: normal, dim (captions, hints) and faint (disabled, empty views). */
    public static final Color TEXT       = new Color(0xE6E8EC);
    public static final Color TEXT_DIM   = new Color(0xA3ABB8);
    public static final Color TEXT_FAINT = new Color(0x6E7685);

    /** The plane (the same amber as its outline in the views). */
    public static final Color PLANE         = new Color(0xFF9F1C);
    public static final Color PLANE_HOVER   = new Color(0xFFB347);
    public static final Color PLANE_PRESSED = new Color(0xE58A0C);

    /** Text and icons on an amber background. */
    public static final Color ON_PLANE = new Color(0x1B1E24);

    /** Messages: errors and warnings. */
    public static final Color ERROR   = new Color(0xFF7070);
    public static final Color WARNING = new Color(0xF4D35E);

    /** Keyboard focus: a cool ring, distinct from the plane's amber. */
    public static final Color FOCUS = new Color(0x8AB4F8);

    /** Client property of a button: which kind of button it is (one of the values below). */
    public static final String VARIANT = "ex1.variant";

    /** Filled amber: the main action of its panel. */
    public static final String PRIMARY = "primary";

    /** No background until the mouse is over it (arrows on a header). */
    public static final String GHOST = "ghost";

    /** The left / right half of a two-part switch (3 points | 4 points). */
    public static final String SEGMENT_FIRST = "segment-first";
    public static final String SEGMENT_LAST  = "segment-last";

    /** Corner radius of buttons, fields and badges. */
    public static final int ARC = 8;

    /** Text sizes in (scaled) pixels: normal, and small print. */
    public static final int TEXT_SIZE  = 13;
    public static final int SMALL_SIZE = 12;

    /** UI fonts in the order they are tried, and the names a semibold weight goes by. */
    private static final String[] FAMILIES = {"Segoe UI", "Inter", "Noto Sans", "Cantarell", "Ubuntu", "DejaVu Sans"};
    private static final String[] SEMIBOLD_SUFFIXES = {" Semibold", " SemiBold"};

    /** The fonts found, looked up once. */
    private static String family;
    private static String semiboldFamily;

    /** Colours, fonts and the install step only; never instantiated. */
    private Theme()
    {
    }

    // ==========================================
    //                   ENTRY
    // ==========================================

    /**
     * Turns on smooth text where the system does not say how to draw it (Linux). Must run before
     * the first window is made; Windows keeps its own ClearType settings.
     */
    public static void prepare()
    {
        if (!onWindows() && System.getProperty("awt.useSystemAAFontSettings") == null)
        {
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
        }
    }

    /**
     * Installs the look for every Swing component made from now on; call on the Swing thread
     * before building the window.
     * @throws (UnsupportedLookAndFeelException) Never in practice (Metal is always there).
     */
    public static void install() throws UnsupportedLookAndFeelException
    {
        MetalLookAndFeel.setCurrentTheme(new GraphiteMetalTheme());
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        UIDefaults d = UIManager.getDefaults();
        installFonts(d);
        installColours(d);
        installControls(d);
    }

    // ==========================================
    //               BUILD PIPELINE
    // ==========================================

    /**
     * Gives every kind of component the UI font.
     * @param (UIDefaults) d The defaults to fill.
     */
    private static void installFonts(UIDefaults d)
    {
        FontUIResource normal = new FontUIResource(font(TEXT_SIZE));
        String[] keys = {"Button.font", "ToggleButton.font", "RadioButton.font", "CheckBox.font", "Label.font",
            "TextField.font", "FormattedTextField.font", "PasswordField.font", "TextArea.font", "TextPane.font",
            "EditorPane.font", "ComboBox.font", "List.font", "Panel.font", "ProgressBar.font", "Slider.font",
            "Spinner.font", "TabbedPane.font", "Table.font", "TableHeader.font", "TitledBorder.font", "ToolTip.font",
            "OptionPane.font", "OptionPane.messageFont", "OptionPane.buttonFont", "ScrollPane.font", "Viewport.font",
            "Menu.font", "MenuItem.font", "MenuBar.font", "PopupMenu.font", "ToolBar.font", "Tree.font"};
        for (int i = 0; i < keys.length; i++)
        {
            d.put(keys[i], normal);
        }
        d.put("ToolTip.font", new FontUIResource(font(SMALL_SIZE)));
    }

    /**
     * Gives every kind of component its graphite colours.
     * @param (UIDefaults) d The defaults to fill.
     */
    private static void installColours(UIDefaults d)
    {
        put(d, "Panel.background", SURFACE);
        put(d, "Panel.foreground", TEXT);
        put(d, "Label.foreground", TEXT);
        put(d, "Label.disabledForeground", TEXT_FAINT);
        put(d, "Viewport.background", SURFACE);
        put(d, "ScrollPane.background", SURFACE);
        put(d, "OptionPane.background", SURFACE);
        put(d, "OptionPane.messageForeground", TEXT);
        put(d, "RootPane.background", SURFACE);
        put(d, "control", SURFACE);
        put(d, "text", TEXT);
        put(d, "controlText", TEXT);
        put(d, "TextField.background", WELL);
        put(d, "TextField.foreground", TEXT);
        put(d, "TextField.inactiveBackground", WELL);
        put(d, "TextField.inactiveForeground", TEXT_FAINT);
        put(d, "TextField.disabledBackground", WELL);
        put(d, "TextField.caretForeground", PLANE);
        put(d, "TextField.selectionBackground", mix(PLANE, WELL, 0.45));
        put(d, "TextField.selectionForeground", TEXT);
        put(d, "ToolTip.background", RAISED);
        put(d, "ToolTip.foreground", TEXT);
        put(d, "ToolTip.foregroundInactive", TEXT_DIM);
        put(d, "ToolTip.backgroundInactive", RAISED);
        put(d, "ProgressBar.foreground", PLANE);
        put(d, "ProgressBar.background", RAISED);
        put(d, "ProgressBar.selectionForeground", ON_PLANE);
        put(d, "ProgressBar.selectionBackground", TEXT);
        put(d, "SplitPane.background", CHROME);
        put(d, "SplitPaneDivider.draggingColor", LINE);
        put(d, "Separator.foreground", LINE);
        put(d, "Separator.background", SURFACE);
        put(d, "Button.foreground", TEXT);
        put(d, "Button.disabledText", TEXT_FAINT);
        put(d, "ToggleButton.foreground", TEXT);
        put(d, "ScrollBar.background", SURFACE);
        put(d, "ScrollBar.thumb", RAISED_HOVER);
        put(d, "ScrollBar.track", SURFACE);
        put(d, "Slider.foreground", TEXT_DIM);
        put(d, "Slider.background", CHROME);
    }

    /**
     * Gives the controls drawn here their look, borders and spacing.
     * @param (UIDefaults) d The defaults to fill.
     */
    private static void installControls(UIDefaults d)
    {
        d.put("ButtonUI", FlatButtonUI.class.getName());
        d.put("ToggleButtonUI", FlatButtonUI.class.getName());
        d.put("TextFieldUI", FlatTextFieldUI.class.getName());
        d.put("ScrollBarUI", FlatScrollBarUI.class.getName());
        d.put("SliderUI", FlatSliderUI.class.getName());
        d.put("SplitPaneUI", "javax.swing.plaf.basic.BasicSplitPaneUI");
        d.put("ProgressBarUI", "javax.swing.plaf.basic.BasicProgressBarUI");
        d.put("Button.margin", new InsetsUIResource(6, 14, 6, 14));
        d.put("ToggleButton.margin", new InsetsUIResource(6, 12, 6, 12));
        d.put("TextField.margin", new InsetsUIResource(0, 0, 0, 0));
        d.put("TextField.border", new BorderUIResource(new FlatTextFieldUI.FieldBorder()));
        d.put("ScrollPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        d.put("SplitPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        d.put("SplitPaneDivider.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        d.put("SplitPane.dividerSize", Integer.valueOf(6));
        d.put("ProgressBar.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        d.put("ProgressBar.cellSpacing", Integer.valueOf(0));
        d.put("ProgressBar.cellLength", Integer.valueOf(1));
        Border tip = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(5, 8, 5, 8));
        d.put("ToolTip.border", new BorderUIResource(tip));
        d.put("ToolTip.borderInactive", new BorderUIResource(tip));
        d.put("OptionPane.border", new BorderUIResource(BorderFactory.createEmptyBorder(18, 18, 14, 18)));
        d.put("OptionPane.buttonAreaBorder", new BorderUIResource(BorderFactory.createEmptyBorder(14, 0, 0, 0)));
        d.put("OptionPane.errorIcon", Icons.dialog(Icons.ERROR));
        d.put("OptionPane.warningIcon", Icons.dialog(Icons.WARNING));
        d.put("OptionPane.informationIcon", Icons.dialog(Icons.INFORMATION));
        d.put("OptionPane.questionIcon", Icons.dialog(Icons.QUESTION));
        d.put("OptionPane.buttonPadding", Integer.valueOf(8));
        // Dialog buttons keep the usual padding instead of Swing's narrow 2 x 8 one.
        d.put("OptionPane.setButtonMargin", Boolean.FALSE);
        d.put("ScrollBar.width", Integer.valueOf(10));
        d.put("Button.rollover", Boolean.TRUE);
        d.put("ToggleButton.rollover", Boolean.TRUE);
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /**
     * Makes the UI font in a size.
     * @param (int) size Pixel size.
     * @return (Font) The UI font, plain.
     */
    public static Font font(int size)
    {
        return new Font(family(), Font.PLAIN, size);
    }

    /**
     * Makes the UI font in a size, semibold.
     * @param (int) size Pixel size.
     * @return (Font) The UI font, semibold where the system has that weight (Segoe UI Semibold),
     *                else bold.
     */
    public static Font semibold(int size)
    {
        family();
        if (semiboldFamily != null)
        {
            return new Font(semiboldFamily, Font.PLAIN, size);
        }
        return new Font(family, Font.BOLD, size);
    }

    /**
     * Writes a colour for an HTML label.
     * @param (Color) c Colour.
     * @return (String) "#rrggbb" for HTML text.
     */
    public static String hex(Color c)
    {
        return String.format(Locale.ROOT, "#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Mixes two colours.
     * @param (Color) a First colour.
     * @param (Color) b Second colour.
     * @param (double) t 0 gives a, 1 gives b.
     * @return (Color) The colour between them.
     */
    public static Color mix(Color a, Color b, double t)
    {
        int r  = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g  = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }

    /**
     * Makes a colour see-through.
     * @param (Color) c Colour.
     * @param (int) alpha 0..255.
     * @return (Color) The colour with that opacity.
     */
    public static Color alpha(Color c, int alpha)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /**
     * Finds the UI font this computer has (once, then remembers it), and its semibold weight if
     * any.
     * @return (String) The first UI font family of {@link #FAMILIES} this computer has.
     */
    private static synchronized String family()
    {
        if (family != null)
        {
            return family;
        }
        Set<String> installed = new HashSet<String>();
        String[]    names     = new String[0];
        try
        {
            names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT);
        }
        catch (RuntimeException e)
        {
            // Headless or no fonts listed: the logical font below always works.
        }
        installed.addAll(Arrays.asList(names));
        family = Font.SANS_SERIF;
        for (int i = 0; i < FAMILIES.length; i++)
        {
            if (installed.contains(FAMILIES[i]))
            {
                family = FAMILIES[i];
                break;
            }
        }
        for (int i = 0; i < SEMIBOLD_SUFFIXES.length; i++)
        {
            if (installed.contains(family + SEMIBOLD_SUFFIXES[i]))
            {
                semiboldFamily = family + SEMIBOLD_SUFFIXES[i];
            }
        }
        return family;
    }

    /**
     * Sets a colour in Swing's defaults.
     * @param (UIDefaults) d Defaults.
     * @param (String) key Key.
     * @param (Color) c Colour.
     */
    private static void put(UIDefaults d, String key, Color c)
    {
        d.put(key, new ColorUIResource(c));
    }

    /**
     * Tells whether the app runs on Windows.
     * @return (boolean) True on Windows.
     */
    private static boolean onWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Metal's own colour scheme in graphite, for the parts of Metal still used (radio buttons in
     * dialogs, menus, focus of fields): text light on dark, highlights in amber.
     */
    private static final class GraphiteMetalTheme extends DefaultMetalTheme
    {
        /** Metal's colours in graphite and amber. */
        private static final ColorUIResource PRIMARY1   = new ColorUIResource(PLANE_PRESSED);
        private static final ColorUIResource PRIMARY2   = new ColorUIResource(PLANE);
        private static final ColorUIResource PRIMARY3   = new ColorUIResource(mix(PLANE, SURFACE, 0.55));
        private static final ColorUIResource SECONDARY1 = new ColorUIResource(WELL);
        private static final ColorUIResource SECONDARY2 = new ColorUIResource(LINE);
        private static final ColorUIResource SECONDARY3 = new ColorUIResource(SURFACE);
        private static final ColorUIResource LIGHT      = new ColorUIResource(TEXT);
        private static final ColorUIResource DARK       = new ColorUIResource(WELL);
        private static final ColorUIResource DIM        = new ColorUIResource(TEXT_FAINT);

        /**
         * Names the theme.
         * @return (String) Its name.
         */
        @Override
        public String getName()
        {
            return "Graphite";
        }

        /**
         * Gives Metal's darkest highlight colour.
         * @return (ColorUIResource) Pressed amber.
         */
        @Override
        protected ColorUIResource getPrimary1()
        {
            return PRIMARY1;
        }

        /**
         * Gives Metal's main highlight colour (selections, focus).
         * @return (ColorUIResource) Amber.
         */
        @Override
        protected ColorUIResource getPrimary2()
        {
            return PRIMARY2;
        }

        /**
         * Gives Metal's lightest highlight colour.
         * @return (ColorUIResource) Amber mixed into the surface.
         */
        @Override
        protected ColorUIResource getPrimary3()
        {
            return PRIMARY3;
        }

        /**
         * Gives Metal's darkest control colour (shadows, borders).
         * @return (ColorUIResource) The well colour.
         */
        @Override
        protected ColorUIResource getSecondary1()
        {
            return SECONDARY1;
        }

        /**
         * Gives Metal's middle control colour (lines, disabled parts).
         * @return (ColorUIResource) The line colour.
         */
        @Override
        protected ColorUIResource getSecondary2()
        {
            return SECONDARY2;
        }

        /**
         * Gives Metal's control background.
         * @return (ColorUIResource) The surface colour.
         */
        @Override
        protected ColorUIResource getSecondary3()
        {
            return SECONDARY3;
        }

        /**
         * Gives what Metal calls black, which it uses for text and lines: light, in a dark theme.
         * @return (ColorUIResource) The text colour.
         */
        @Override
        protected ColorUIResource getBlack()
        {
            return LIGHT;
        }

        /**
         * Gives what Metal calls white, which it uses for backgrounds: dark, in a dark theme.
         * @return (ColorUIResource) The well colour.
         */
        @Override
        protected ColorUIResource getWhite()
        {
            return DARK;
        }

        /**
         * Gives the colour of the text on controls.
         * @return (ColorUIResource) Light.
         */
        @Override
        public ColorUIResource getControlTextColor()
        {
            return LIGHT;
        }

        /**
         * Gives the colour of the text on disabled controls.
         * @return (ColorUIResource) Faint.
         */
        @Override
        public ColorUIResource getInactiveControlTextColor()
        {
            return DIM;
        }

        /**
         * Gives the colour of labels and titles.
         * @return (ColorUIResource) Light.
         */
        @Override
        public ColorUIResource getSystemTextColor()
        {
            return LIGHT;
        }

        /**
         * Gives the colour of the text the user types.
         * @return (ColorUIResource) Light.
         */
        @Override
        public ColorUIResource getUserTextColor()
        {
            return LIGHT;
        }

        /**
         * Gives the colour of disabled labels.
         * @return (ColorUIResource) Faint.
         */
        @Override
        public ColorUIResource getInactiveSystemTextColor()
        {
            return DIM;
        }

        /**
         * Gives the colour of menu items.
         * @return (ColorUIResource) Light.
         */
        @Override
        public ColorUIResource getMenuForeground()
        {
            return LIGHT;
        }

        /**
         * Gives the colour of selected text.
         * @return (ColorUIResource) Light.
         */
        @Override
        public ColorUIResource getHighlightedTextColor()
        {
            return LIGHT;
        }

        /**
         * Gives the font of controls: the UI font, not Metal's bold one.
         * @return (FontUIResource) The UI font.
         */
        @Override
        public FontUIResource getControlTextFont()
        {
            return new FontUIResource(font(TEXT_SIZE));
        }

        /**
         * Gives the font of labels.
         * @return (FontUIResource) The UI font.
         */
        @Override
        public FontUIResource getSystemTextFont()
        {
            return new FontUIResource(font(TEXT_SIZE));
        }

        /**
         * Gives the font of the text the user types.
         * @return (FontUIResource) The UI font.
         */
        @Override
        public FontUIResource getUserTextFont()
        {
            return new FontUIResource(font(TEXT_SIZE));
        }

        /**
         * Gives the font of menus.
         * @return (FontUIResource) The UI font.
         */
        @Override
        public FontUIResource getMenuTextFont()
        {
            return new FontUIResource(font(TEXT_SIZE));
        }

        /**
         * Gives the font of window titles.
         * @return (FontUIResource) The UI font, semibold.
         */
        @Override
        public FontUIResource getWindowTitleFont()
        {
            return new FontUIResource(semibold(TEXT_SIZE));
        }

        /**
         * Gives the font of Metal's small print, such as the shortcut keys in menus.
         * @return (FontUIResource) The small UI font.
         */
        @Override
        public FontUIResource getSubTextFont()
        {
            return new FontUIResource(font(SMALL_SIZE));
        }
    }
}
