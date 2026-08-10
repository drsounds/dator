package se.spacify.dator.ui;

import jexer.TApplication;
import jexer.TDesktop;
import jexer.bits.CellAttributes;

/**
 * TDesktop draws a hatch character across the whole background by default.
 * This variant fills with a plain solid color instead - no pattern, just
 * the theme's desktop color (e.g. flat DOS blue).
 */
class PlainDesktop extends TDesktop {

    PlainDesktop(TApplication application) {
        super(application);
    }

    @Override
    public void draw() {
        CellAttributes background = getTheme().getColor("tdesktop.background");
        putAll(' ', background);
    }
}
