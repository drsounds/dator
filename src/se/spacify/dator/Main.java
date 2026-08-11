package se.spacify.dator;

import jexer.TApplication.BackendType;

import se.spacify.dator.ui.DatorApplication;

/**
 * Entry point. Usage: java se.spacify.dator.Main [--swing] [path-to-sqlite-file]
 *
 * <p>By default the app renders directly in the calling terminal (ANSI
 * escape codes, like a real console app). Pass --swing to run it in a
 * separate Swing window that emulates a terminal instead.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        boolean useSwing = false;
        String dbPath = null;
        for (String arg : args) {
            if (arg.equals("--swing")) {
                useSwing = true;
            } else {
                dbPath = arg;
            }
        }
        if (dbPath == null) {
            dbPath = "dator.sqlite";
        }

        BackendType backendType = useSwing ? BackendType.SWING : BackendType.ECMA48;
        DatorApplication app = new DatorApplication(dbPath, backendType);
        app.run();
    }
}
