package se.spacify.dator.ui;

import jexer.TKeypress;
import jexer.TTableWidget;
import jexer.TWidget;
import jexer.event.TKeypressEvent;

/**
 * TTableWidget swallows Tab/Shift-Tab outright ("they don't make sense in
 * the grid context"), which traps keyboard focus inside any grid once it is
 * reached - the only way out is the mouse. This subclass forwards Tab and
 * Shift-Tab to the normal parent widget-switching instead, so every grid in
 * the app participates in Tab order exactly like a field, button, or combo
 * box: no Enter, no click needed to "activate" it first.
 */
class TabbableTable extends TTableWidget {

    TabbableTable(TWidget parent, int x, int y, int width, int height, int gridColumns, int gridRows) {
        super(parent, x, y, width, height, gridColumns, gridRows);
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        if (event.getKey().equals(TKeypress.kbTab)) {
            getParent().switchWidget(true);
            return;
        } else if (event.getKey().equals(TKeypress.kbShiftTab)) {
            getParent().switchWidget(false);
            return;
        }
        super.onKeypress(event);
    }
}
