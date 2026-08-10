package se.spacify.dator.ui;

import jexer.bits.CellAttributes;
import jexer.bits.Color;
import jexer.bits.ColorTheme;

/**
 * Overrides Jexer's default color theme with an ISPF/3270-style look:
 * green text on a black background everywhere, with a reverse-video
 * (black-on-green) highlight for whatever is focused/active/selected,
 * exactly like a green-screen terminal.
 */
final class IspfTheme {

    private IspfTheme() {
    }

    private static CellAttributes attr(Color fore, Color back, boolean bold) {
        CellAttributes c = new CellAttributes();
        c.setForeColor(fore);
        c.setBackColor(back);
        c.setBold(bold);
        return c;
    }

    static void apply(ColorTheme theme) {
        CellAttributes normal = attr(Color.GREEN, Color.BLACK, false);
        CellAttributes bright = attr(Color.GREEN, Color.BLACK, true);
        CellAttributes highlight = attr(Color.BLACK, Color.GREEN, true);

        String[] normalKeys = {
                "twindow.border.inactive", "twindow.background.inactive",
                "twindow.background.modal", "twindow.border.modal.inactive",
                "twindow.background.modal.inactive", "twindow.background.windowmove",
                "tdesktop.background", "tbutton.inactive", "tbutton.disabled",
                "tlabel", "ttext", "tfield.inactive", "tcheckbox.inactive",
                "tcombobox.inactive", "tspinner.inactive", "tcalendar.background",
                "tcalendar.day", "tradiobutton.inactive", "tradiogroup.inactive",
                "tmenu", "tmenu.disabled", "tprogressbar.incomplete", "ttreeview",
                "ttreeview.unreadable", "ttreeview.inactive", "ttreeview.selected.inactive",
                "tlist", "tlist.unreadable", "tlist.inactive", "tlist.selected.inactive",
                "tstatusbar.text", "teditor", "ttable.inactive", "ttable.border",
                "tsplitpane", "thelpwindow.background", "thelpwindow.text",
        };
        for (String key : normalKeys) {
            theme.setColor(key, new CellAttributes(normal));
        }

        String[] brightKeys = {
                "twindow.border", "twindow.background", "twindow.border.modal",
                "tbutton.mnemonic", "tbutton.mnemonic.pulse", "tbutton.pulse",
                "tlabel.mnemonic", "tfield.pulse", "tcheckbox.mnemonic",
                "tcheckbox.pulse", "tcalendar.arrow", "tcalendar.title",
                "tradiobutton.mnemonic", "tradiobutton.pulse", "tradiogroup.active",
                "tmenu.mnemonic", "tscroller.bar", "ttreeview.expandbutton",
                "tstatusbar.button", "teditor.margin", "ttable.active", "ttable.label",
                "thelpwindow.border", "thelpwindow.link",
        };
        for (String key : brightKeys) {
            theme.setColor(key, new CellAttributes(bright));
        }

        String[] highlightKeys = {
                "twindow.border.modal.windowmove", "twindow.border.windowmove",
                "tbutton.active", "tbutton.mnemonic.highlighted", "tfield.active",
                "tcheckbox.active", "tcheckbox.mnemonic.highlighted", "tcombobox.active",
                "tspinner.active", "tcalendar.day.selected", "tradiobutton.active",
                "tradiobutton.mnemonic.highlighted", "tmenu.highlighted",
                "tmenu.mnemonic.highlighted", "tprogressbar.complete", "tscroller.arrows",
                "ttreeview.selected", "tlist.selected", "tstatusbar.selected",
                "teditor.selected", "ttable.selected", "ttable.label.selected",
                "thelpwindow.windowmove", "thelpwindow.link.active",
        };
        for (String key : highlightKeys) {
            theme.setColor(key, new CellAttributes(highlight));
        }
    }
}
