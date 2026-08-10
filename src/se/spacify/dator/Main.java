package se.spacify.dator;

import se.spacify.dator.ui.DatorApplication;

/**
 * Entry point. Usage: java se.spacify.dator.Main [path-to-sqlite-file]
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "dator.sqlite";
        DatorApplication app = new DatorApplication(dbPath);
        app.run();
    }
}
