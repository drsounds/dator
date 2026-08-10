package se.spacify.dator.ui;

import java.util.List;

import jexer.TAction;
import jexer.TCheckBox;
import jexer.TComboBox;
import jexer.TKeypress;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

/**
 * Lets the user switch between the MS-DOS, ISPF, and Jexer default themes,
 * customize the ISPF theme's foreground/background colors, and turn off
 * window shadows/transparency/desktop pattern for a flat mainframe look.
 */
public class PreferencesWindow extends TWindow {

    private static final String DOS_OPTION = "MS-DOS (grey/blue)";
    private static final String ISPF_OPTION = "ISPF (green screen)";
    private static final String DEFAULT_OPTION = "Jexer Default";
    private static final List<String> THEME_OPTIONS = List.of(DOS_OPTION, ISPF_OPTION, DEFAULT_OPTION);

    private static final String DARK_GRAY_OPTION = "Dark Grey";
    private static final String WHITE_OPTION = "White";
    private static final List<String> BUTTON_OPTIONS = List.of(DARK_GRAY_OPTION, WHITE_OPTION);

    private static final String FRAME_SURFACE_OPTION = "Match surface";
    private static final String FRAME_WHITE_OPTION = "White (on blue)";
    private static final List<String> FRAME_OPTIONS = List.of(FRAME_SURFACE_OPTION, FRAME_WHITE_OPTION);

    private final DatorApplication app;
    private TComboBox themeCombo;
    private TComboBox dosButtonCombo;
    private TComboBox dosFrameCombo;
    private TComboBox fgCombo;
    private TComboBox bgCombo;
    private TCheckBox flatUiCheckBox;
    private TCheckBox dosFrameLinesCheckBox;

    public PreferencesWindow(DatorApplication app) {
        super(app, "Preferences", 60, 25, TWindow.MODAL);
        this.app = app;
        setupWidgets();
    }

    private void setupWidgets() {
        PreferencesStore prefs = app.getPreferences();

        addLabel("Theme", 2, 1);
        int themeIndex;
        if (PreferencesStore.THEME_DEFAULT.equals(prefs.theme)) {
            themeIndex = 2;
        } else if (PreferencesStore.THEME_ISPF.equals(prefs.theme)) {
            themeIndex = 1;
        } else {
            themeIndex = 0;
        }
        themeCombo = addComboBox(2, 2, 26, THEME_OPTIONS, themeIndex, 4, null);

        addLabel("MS-DOS button color", 2, 4);
        int dosButtonIndex = prefs.dosDarkButtons ? 0 : 1;
        dosButtonCombo = addComboBox(2, 5, 20, BUTTON_OPTIONS, dosButtonIndex, 3, null);

        addLabel("MS-DOS window frame color", 2, 7);
        int dosFrameIndex = prefs.dosWhiteFrame ? 1 : 0;
        dosFrameCombo = addComboBox(2, 8, 20, FRAME_OPTIONS, dosFrameIndex, 3, null);

        dosFrameLinesCheckBox = addCheckBox(2, 10,
                "MS-DOS: show window frame lines", prefs.dosShowFrameLines);

        addLabel("ISPF foreground color", 2, 12);
        int fgIndex = Math.max(0, ColorNames.NAMES.indexOf(prefs.ispfForeground.toUpperCase(java.util.Locale.ROOT)));
        fgCombo = addComboBox(2, 13, 20, ColorNames.NAMES, fgIndex, 8, null);

        addLabel("ISPF background color", 2, 15);
        int bgIndex = Math.max(0, ColorNames.NAMES.indexOf(prefs.ispfBackground.toUpperCase(java.util.Locale.ROOT)));
        bgCombo = addComboBox(2, 16, 20, ColorNames.NAMES, bgIndex, 8, null);

        addLabel("(each setting above only applies to its own theme)", 2, 18);

        flatUiCheckBox = addCheckBox(2, 20,
                "Flat mainframe look (no shadows, transparency, or desktop pattern)", prefs.flatUi);

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
        String chosenTheme = themeCombo.getText();
        if (DEFAULT_OPTION.equals(chosenTheme)) {
            prefs.theme = PreferencesStore.THEME_DEFAULT;
        } else if (ISPF_OPTION.equals(chosenTheme)) {
            prefs.theme = PreferencesStore.THEME_ISPF;
        } else {
            prefs.theme = PreferencesStore.THEME_DOS;
        }
        prefs.ispfForeground = fgCombo.getText();
        prefs.ispfBackground = bgCombo.getText();
        prefs.dosDarkButtons = DARK_GRAY_OPTION.equals(dosButtonCombo.getText());
        prefs.dosWhiteFrame = FRAME_WHITE_OPTION.equals(dosFrameCombo.getText());
        prefs.dosShowFrameLines = dosFrameLinesCheckBox.isChecked();
        prefs.flatUi = flatUiCheckBox.isChecked();
        app.applyTheme();
        app.applyEffects();

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
