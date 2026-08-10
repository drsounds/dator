package se.spacify.dator.ui;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

import jexer.TApplication;
import jexer.TMessageBox;
import jexer.menu.TMenu;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.db.Database;
import se.spacify.dator.db.MetaRepository;

/**
 * Top-level Jexer application: owns the current database connection and
 * hosts the menu bar. The table list window is the "home" window.
 */
public class DatorApplication extends TApplication {

    private static final int MID_OPEN_DB = 2000;
    private static final int MID_NEW_DB = 2001;
    private static final int MID_TABLE_LIST = 2010;
    private static final int MID_NEW_TABLE = 2011;
    private static final int MID_ABOUT = 2020;
    private static final int MID_PREFERENCES = 2030;

    private Database database;
    private MetaRepository metaRepository;
    private DataRepository dataRepository;
    private PreferencesStore preferences;

    private TableListWindow tableListWindow;

    public DatorApplication(String dbPath) throws UnsupportedEncodingException, SQLException {
        super(BackendType.SWING);
        preferences = PreferencesStore.load();
        applyTheme();
        applyEffects();
        openDatabase(dbPath);
        buildMenu();
        tableListWindow = new TableListWindow(this);
    }

    PreferencesStore getPreferences() {
        return preferences;
    }

    /** Re-applies the current theme choice/colors to the live ColorTheme and repaints. */
    void applyTheme() {
        if (PreferencesStore.THEME_DEFAULT.equals(preferences.theme)) {
            getTheme().setDefaultTheme();
        } else {
            IspfTheme.apply(getTheme(), ColorNames.colorOf(preferences.ispfForeground),
                    ColorNames.colorOf(preferences.ispfBackground));
        }
        doRepaint();
    }

    /**
     * Applies the flat-UI preference: no window shadows, no translucent
     * windows, and no hatch-patterned desktop background - just flat color
     * fills, like a real mainframe terminal.
     */
    void applyEffects() {
        setTranslucence(!preferences.flatUi);
        setDesktop(preferences.flatUi ? null : new jexer.TDesktop(this));
        doRepaint();
    }

    private void openDatabase(String path) throws SQLException {
        if (database != null) {
            database.close();
        }
        database = new Database(path);
        metaRepository = new MetaRepository(database);
        dataRepository = new DataRepository(database);
    }

    public Database getDatabase() {
        return database;
    }

    public MetaRepository getMetaRepository() {
        return metaRepository;
    }

    public DataRepository getDataRepository() {
        return dataRepository;
    }

    public String getDatabasePath() {
        return database.getPath();
    }

    private void buildMenu() {
        TMenu fileMenu = addMenu("&File");
        fileMenu.addItem(MID_OPEN_DB, "&Open Database...");
        fileMenu.addItem(MID_NEW_DB, "&New Database...");
        fileMenu.addSeparator();
        fileMenu.addDefaultItem(TMenu.MID_EXIT);

        TMenu tablesMenu = addMenu("&Tables");
        tablesMenu.addItem(MID_NEW_TABLE, "&New Table Model...");
        tablesMenu.addItem(MID_TABLE_LIST, "&Table List");

        addWindowMenu();

        TMenu prefsMenu = addMenu("&Preferences");
        prefsMenu.addItem(MID_PREFERENCES, "&Theme...");

        TMenu helpMenu = addMenu("&Help");
        helpMenu.addItem(MID_ABOUT, "&About");
    }

    @Override
    protected boolean onMenu(jexer.event.TMenuEvent event) {
        int id = event.getId();
        try {
            if (id == MID_OPEN_DB) {
                doOpenDatabase();
                return true;
            } else if (id == MID_NEW_DB) {
                doNewDatabase();
                return true;
            } else if (id == MID_TABLE_LIST) {
                showTableList();
                return true;
            } else if (id == MID_NEW_TABLE) {
                showTableList();
                new TableModelEditWindow(this, null);
                return true;
            } else if (id == MID_PREFERENCES) {
                new PreferencesWindow(this);
                return true;
            } else if (id == MID_ABOUT) {
                messageBox("About Dator",
                        "Dator - SQLite Table Model Manager\n\n" +
                        "Define table models with columns and relations, then\n" +
                        "browse, create, edit and delete their data.\n\n" +
                        "Database: " + getDatabasePath());
                return true;
            }
        } catch (Exception e) {
            messageBox("Error", e.getMessage() == null ? e.toString() : e.getMessage());
            return true;
        }
        return super.onMenu(event);
    }

    private void doOpenDatabase() throws IOException, SQLException {
        String path = fileOpenBox(".");
        if (path == null) {
            return;
        }
        switchDatabase(path);
    }

    private void doNewDatabase() throws IOException, SQLException {
        String path = fileSaveBox(".");
        if (path == null) {
            return;
        }
        switchDatabase(path);
    }

    private void switchDatabase(String path) throws SQLException {
        for (jexer.TWindow w : new java.util.ArrayList<>(getAllWindows())) {
            w.close();
        }
        openDatabase(path);
        tableListWindow = new TableListWindow(this);
        messageBox("Database", "Now using: " + path);
    }

    public void showTableList() {
        if (tableListWindow == null || !hasWindow(tableListWindow)) {
            tableListWindow = new TableListWindow(this);
        } else {
            activateWindow(tableListWindow);
            tableListWindow.reload();
        }
    }

    public void showError(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) {
            msg = t.toString();
        }
        messageBox("Error", msg, TMessageBox.Type.OK);
    }
}
