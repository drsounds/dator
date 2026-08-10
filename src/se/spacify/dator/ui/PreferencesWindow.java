package se.spacify.dator.ui;

import java.util.List;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TKeypress;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

/**
 * Lets the user switch between the ISPF theme and Jexer's built-in default
 * theme, and customize the ISPF theme's foreground/background colors.
 */
public class PreferencesWindow extends TWindow {

    private static final String ISPF_OPTION = "ISPF (green screen)";
    private static final String DEFAULT_OPTION = "Jexer Default";
    private static final List<String> THEME_OPTIONS = List.of(ISPF_OPTION, DEFAULT_OPTION);

    private final DatorApplication app;
    private TComboBox themeCombo;
    private TComboBox fgCombo;
    private TComboBox bgCombo;

    public PreferencesWindow(DatorApplication app) {
        super(app, "Preferences", 60, 17, TWindow.MODAL);
        this.app = app;
        setupWidgets();
    }

    private void setupWidgets() {
        PreferencesStore prefs = app.getPreferences();

        addLabel("Theme", 2, 1);
        int themeIndex = PreferencesStore.THEME_DEFAULT.equals(prefs.theme) ? 1 : 0;
        themeCombo = addComboBox(2, 2, 26, THEME_OPTIONS, themeIndex, 3, null);

        addLabel("ISPF foreground color", 2, 4);
        int fgIndex = Math.max(0, ColorNames.NAMES.indexOf(prefs.ispfForeground.toUpperCase(java.util.Locale.ROOT)));
        fgCombo = addComboBox(2, 5, 20, ColorNames.NAMES, fgIndex, 8, null);

        addLabel("ISPF background color", 2, 7);
        int bgIndex = Math.max(0, ColorNames.NAMES.indexOf(prefs.ispfBackground.toUpperCase(java.util.Locale.ROOT)));
        bgCombo = addComboBox(2, 8, 20, ColorNames.NAMES, bgIndex, 8, null);

        addLabel("(colors above only apply to the ISPF theme)", 2, 10);

        addButton("Apply", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doApply(false);
            }
        });
        addButton("Save", 13, getHeight() - 3, new TAction() {
            public void DO() {
                doApply(true);
            }
        });
        addButton("Close", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private void doApply(boolean persist) {
        if (fgCombo.getText().equals(bgCombo.getText())) {
            app.messageBox("Validation Error", "Foreground and background must be different colors.");
            return;
        }

        PreferencesStore prefs = app.getPreferences();
        prefs.theme = DEFAULT_OPTION.equals(themeCombo.getText())
                ? PreferencesStore.THEME_DEFAULT : PreferencesStore.THEME_ISPF;
        prefs.ispfForeground = fgCombo.getText();
        prefs.ispfBackground = bgCombo.getText();
        app.applyTheme();

        if (persist) {
            prefs.save();
            app.messageBox("Preferences", "Preferences saved.");
        }
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        if (event.getKey().equals(TKeypress.kbEsc)) {
            close();
            return;
        }
        super.onKeypress(event);
    }
}
