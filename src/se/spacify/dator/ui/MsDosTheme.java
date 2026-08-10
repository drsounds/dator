package se.spacify.dator.ui;

import jexer.bits.CellAttributes;
import jexer.bits.Color;
import jexer.bits.ColorTheme;

/**
 * Classic MS-DOS / Turbo Vision look: a blue desktop, grey (WHITE,
 * non-bold) window surfaces, cyan input fields and selection highlights,
 * red mnemonic (hotkey) letters, and a dark-grey or white button face
 * (user's choice, dark grey by default).
 */
final class MsDosTheme {

    /** Jexer's own "bright black" RGB - a true dark grey, distinct from the WHITE surface. */
    static final int DARK_GRAY_RGB = 0x545454;
    /** Jexer's own "bright white" RGB - a true white, distinct from the WHITE (light grey) surface. */
    static final int WHITE_RGB = 0xfcfcfc;

    private MsDosTheme() {
    }

    private static CellAttributes attr(Color fore, Color back, boolean bold) {
        CellAttributes c = new CellAttributes();
        c.setForeColor(fore);
        c.setBackColor(back);
        c.setBold(bold);
        return c;
    }

    private static CellAttributes attrRgbBack(Color fore, int backRgb, boolean bold) {
        CellAttributes c = new CellAttributes();
        c.setForeColor(fore);
        c.setBackColorRGB(backRgb);
        c.setBold(bold);
        return c;
    }

    private static CellAttributes attrRgb(int foreRgb, int backRgb, boolean bold) {
        CellAttributes c = new CellAttributes();
        c.setForeColorRGB(foreRgb);
        c.setBackColorRGB(backRgb);
        c.setBold(bold);
        return c;
    }

    static void apply(ColorTheme theme) {
        apply(theme, true);
    }

    /** @param darkButtons true for a dark-grey button face, false for white. */
    static void apply(ColorTheme theme, boolean darkButtons) {
        // Window frame sits in the blue desktop area.
        CellAttributes windowBorder = attr(Color.WHITE, Color.BLUE, true);
        CellAttributes windowBorderInactive = attr(Color.CYAN, Color.BLUE, false);
        CellAttributes desktop = attr(Color.WHITE, Color.BLUE, false);

        // Light grey window body ("surface"): plain WHITE reads as light
        // grey against a true-color background, matching classic DOS UIs.
        CellAttributes surface = attr(Color.BLACK, Color.WHITE, false);
        CellAttributes surfaceAccent = attr(Color.BLUE, Color.WHITE, true);

        // Editable widgets get a distinct cyan face; focus pops with a
        // strong blue/white reverse.
        CellAttributes field = attr(Color.BLACK, Color.CYAN, false);
        CellAttributes fieldFocus = attr(Color.WHITE, Color.BLUE, true);

        // Button face: dark grey or true white (user's choice), cyan when focused.
        CellAttributes button = darkButtons
                ? attrRgb(WHITE_RGB, DARK_GRAY_RGB, true)
                : attrRgb(0x000000, WHITE_RGB, false);
        CellAttributes buttonMnemonic = darkButtons
                ? attrRgbBack(Color.RED, DARK_GRAY_RGB, true)
                : attrRgbBack(Color.RED, WHITE_RGB, true);
        CellAttributes buttonFocus = attr(Color.BLACK, Color.CYAN, true);

        // Red hotkey letters, as in classic Turbo Vision.
        CellAttributes mnemonic = attr(Color.RED, Color.WHITE, true);
        CellAttributes mnemonicFocus = attr(Color.RED, Color.CYAN, true);
        CellAttributes menuMnemonicFocus = attr(Color.RED, Color.BLUE, true);

        CellAttributes selection = attr(Color.BLACK, Color.CYAN, true);
        CellAttributes menu = attr(Color.BLACK, Color.WHITE, false);
        CellAttributes menuFocus = attr(Color.WHITE, Color.BLUE, true);

        set(theme, surface,
                "twindow.background", "twindow.background.inactive",
                "twindow.background.modal", "twindow.background.modal.inactive",
                "twindow.background.windowmove", "tlabel", "ttext",
                "tcheckbox.inactive", "tcalendar.background", "tcalendar.day",
                "tradiobutton.inactive", "tradiogroup.inactive",
                "tprogressbar.incomplete", "ttreeview", "ttreeview.unreadable",
                "ttreeview.inactive", "ttreeview.selected.inactive",
                "tlist", "tlist.unreadable", "tlist.inactive", "tlist.selected.inactive",
                "teditor", "ttable.inactive", "tsplitpane",
                "thelpwindow.background", "thelpwindow.text");

        set(theme, surfaceAccent,
                "tcalendar.arrow", "tcalendar.title", "tradiogroup.active",
                "tscroller.bar", "ttreeview.expandbutton", "teditor.margin",
                "ttable.active", "ttable.border", "thelpwindow.link");

        set(theme, windowBorder,
                "twindow.border", "twindow.border.modal",
                "twindow.border.modal.windowmove", "twindow.border.windowmove",
                "thelpwindow.windowmove", "thelpwindow.border");

        set(theme, windowBorderInactive,
                "twindow.border.inactive", "twindow.border.modal.inactive");

        set(theme, desktop, "tdesktop.background");

        set(theme, field, "tfield.inactive", "tcombobox.inactive", "tspinner.inactive");
        set(theme, fieldFocus, "tfield.active", "tcombobox.active", "tspinner.active",
                "tfield.pulse");

        set(theme, button, "tbutton.inactive", "tbutton.disabled");
        set(theme, buttonFocus, "tbutton.active", "tbutton.pulse");
        set(theme, buttonMnemonic, "tbutton.mnemonic");

        set(theme, mnemonic, "tlabel.mnemonic", "tcheckbox.mnemonic",
                "tradiobutton.mnemonic", "tmenu.mnemonic", "tstatusbar.button");
        set(theme, mnemonicFocus, "tbutton.mnemonic.highlighted", "tbutton.mnemonic.pulse",
                "tcheckbox.mnemonic.highlighted", "tradiobutton.mnemonic.highlighted");
        set(theme, menuMnemonicFocus, "tmenu.mnemonic.highlighted");

        set(theme, selection, "tcheckbox.active", "tcheckbox.pulse",
                "tradiobutton.active", "tradiobutton.pulse",
                "tcalendar.day.selected", "tprogressbar.complete", "tscroller.arrows",
                "ttreeview.selected", "tlist.selected", "teditor.selected",
                "ttable.selected", "thelpwindow.link.active");

        set(theme, menu, "tmenu", "tmenu.disabled", "tstatusbar.text", "ttable.label");
        set(theme, menuFocus, "tmenu.highlighted", "tstatusbar.selected", "ttable.label.selected");
    }

    private static void set(ColorTheme theme, CellAttributes color, String... keys) {
        for (String key : keys) {
            theme.setColor(key, new CellAttributes(color));
        }
    }
}
