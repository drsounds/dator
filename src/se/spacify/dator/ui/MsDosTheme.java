package se.spacify.dator.ui;

import jexer.bits.CellAttributes;
import jexer.bits.Color;
import jexer.bits.ColorTheme;

/**
 * Classic MS-DOS / Turbo Vision look: a blue desktop, grey (WHITE,
 * non-bold) window surfaces and buttons, cyan input fields and selection
 * highlights, and red mnemonic (hotkey) letters.
 */
final class MsDosTheme {

    private MsDosTheme() {
    }

    private static CellAttributes attr(Color fore, Color back, boolean bold) {
        CellAttributes c = new CellAttributes();
        c.setForeColor(fore);
        c.setBackColor(back);
        c.setBold(bold);
        return c;
    }

    static void apply(ColorTheme theme) {
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

        // Grey buttons, cyan when focused.
        CellAttributes button = attr(Color.BLACK, Color.WHITE, false);
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

        set(theme, mnemonic, "tbutton.mnemonic", "tlabel.mnemonic", "tcheckbox.mnemonic",
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
