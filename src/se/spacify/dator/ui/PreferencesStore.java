package se.spacify.dator.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * User-level app preferences (theme choice, ISPF theme colors), persisted
 * to a properties file under the user's home directory so they survive
 * across databases and restarts.
 */
class PreferencesStore {

    static final String THEME_ISPF = "ispf";
    static final String THEME_DEFAULT = "default";

    String theme = THEME_ISPF;
    String ispfForeground = "GREEN";
    String ispfBackground = "BLACK";
    /** Flat mainframe look: no window shadows, no transparency, no desktop hatch pattern. */
    boolean flatUi = true;

    private static File file() {
        String home = System.getProperty("user.home", ".");
        return new File(home + File.separator + ".dator", "preferences.properties");
    }

    static PreferencesStore load() {
        PreferencesStore p = new PreferencesStore();
        File f = file();
        if (f.exists()) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
                p.theme = props.getProperty("theme", p.theme);
                p.ispfForeground = props.getProperty("ispf.foreground", p.ispfForeground);
                p.ispfBackground = props.getProperty("ispf.background", p.ispfBackground);
                p.flatUi = Boolean.parseBoolean(props.getProperty("flatUi", String.valueOf(p.flatUi)));
            } catch (IOException ignored) {
                // fall back to defaults
            }
        }
        return p;
    }

    void save() {
        File f = file();
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Properties props = new Properties();
        props.setProperty("theme", theme);
        props.setProperty("ispf.foreground", ispfForeground);
        props.setProperty("ispf.background", ispfBackground);
        props.setProperty("flatUi", String.valueOf(flatUi));
        try (OutputStream out = new FileOutputStream(f)) {
            props.store(out, "Dator preferences");
        } catch (IOException ignored) {
            // best effort
        }
    }
}
