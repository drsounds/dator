package se.spacify.dator.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jexer.bits.Color;

/** Jexer only offers the 8 base ANSI colors; this maps names to/from them. */
final class ColorNames {

    private ColorNames() {
    }

    private static final Map<String, Color> BY_NAME = new LinkedHashMap<>();
    static {
        BY_NAME.put("BLACK", Color.BLACK);
        BY_NAME.put("RED", Color.RED);
        BY_NAME.put("GREEN", Color.GREEN);
        BY_NAME.put("YELLOW", Color.YELLOW);
        BY_NAME.put("BLUE", Color.BLUE);
        BY_NAME.put("MAGENTA", Color.MAGENTA);
        BY_NAME.put("CYAN", Color.CYAN);
        BY_NAME.put("WHITE", Color.WHITE);
    }

    static final List<String> NAMES = List.copyOf(BY_NAME.keySet());

    static Color colorOf(String name) {
        Color c = (name == null) ? null : BY_NAME.get(name.toUpperCase(Locale.ROOT));
        return c == null ? Color.GREEN : c;
    }
}
