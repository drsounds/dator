package se.spacify.dator.ui;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jexer.TApplication;
import jexer.TInputBox;
import jexer.TMessageBox;
import jexer.menu.TMenu;
import jexer.menu.TSubMenu;

import se.spacify.dator.db.AppRepository;
import se.spacify.dator.db.DataRepository;
import se.spacify.dator.db.Database;
import se.spacify.dator.db.MetaRepository;
import se.spacify.dator.model.DatorApp;
import se.spacify.dator.model.DatorFeature;
import se.spacify.dator.model.DatorTable;

/**
 * Top-level Jexer application: owns the current database connection and
 * hosts the menu bar. The table list window is the "home" window. The
 * "&amp;Apps" menu is rebuilt whenever apps/features change or the database
 * is switched, so it always lists the current database's custom panels with
 * their shortcuts, and "Jump to Shortcut..." opens one directly by code -
 * both mirroring ISPF's option-number menus and "=" jump command.
 */
public class DatorApplication extends TApplication {

    private static final int MID_OPEN_DB = 2000;
    private static final int MID_NEW_DB = 2001;
    private static final int MID_TABLE_LIST = 2010;
    private static final int MID_NEW_TABLE = 2011;
    private static final int MID_ABOUT = 2020;
    private static final int MID_MANAGE_APPS = 2030;
    private static final int MID_JUMP_SHORTCUT = 2031;

    /** Dynamic feature menu item ids start here to stay clear of the fixed MID_* constants above. */
    private static final int MID_FEATURE_BASE = 100000;

    private Database database;
    private MetaRepository metaRepository;
    private DataRepository dataRepository;
    private AppRepository appRepository;

    private TableListWindow tableListWindow;
    private TMenu appsMenu;
    private final Map<Integer, DatorFeature> menuFeatureIds = new HashMap<>();

    public DatorApplication(String dbPath) throws UnsupportedEncodingException, SQLException {
        super(BackendType.SWING);
        openDatabase(dbPath);
        buildMenu();
        tableListWindow = new TableListWindow(this);
    }

    private void openDatabase(String path) throws SQLException {
        if (database != null) {
            database.close();
        }
        database = new Database(path);
        metaRepository = new MetaRepository(database);
        dataRepository = new DataRepository(database);
        appRepository = new AppRepository(database);
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

    public AppRepository getAppRepository() {
        return appRepository;
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

        TMenu helpMenu = addMenu("&Help");
        helpMenu.addItem(MID_ABOUT, "&About");

        rebuildAppsMenu();
    }

    /**
     * Rebuilds the "&amp;Apps" menu from the current database's dator_apps /
     * dator_features tables: a management item, a jump-by-shortcut command,
     * then one submenu per app listing its features (with their shortcut, if
     * any). Call after any app/feature change and after switching databases.
     */
    public void rebuildAppsMenu() {
        if (appsMenu != null) {
            removeMenu(appsMenu);
            appsMenu = null;
        }
        menuFeatureIds.clear();

        appsMenu = addMenu("&Apps");
        appsMenu.addItem(MID_MANAGE_APPS, "&Manage Apps...");
        appsMenu.addItem(MID_JUMP_SHORTCUT, "&Jump to Shortcut...", jexer.TKeypress.kbCtrlG);
        appsMenu.addSeparator();

        try {
            List<DatorApp> apps = appRepository.listApps();
            int nextId = MID_FEATURE_BASE;
            for (DatorApp a : apps) {
                List<DatorFeature> features = appRepository.listFeatures(a.getId());
                TSubMenu sub = appsMenu.addSubMenu(a.getDisplayLabel());
                if (features.isEmpty()) {
                    sub.addItem(nextId++, "(no features yet)", false);
                    continue;
                }
                for (DatorFeature f : features) {
                    int id = nextId++;
                    String label = f.getDisplayLabel() +
                            (f.getShortcut() == null || f.getShortcut().isEmpty() ? "" : " (=" + f.getShortcut() + ")");
                    sub.addItem(id, label);
                    menuFeatureIds.put(id, f);
                }
            }
        } catch (Exception e) {
            showError(e);
        }
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
            } else if (id == MID_ABOUT) {
                messageBox("About Dator",
                        "Dator - SQLite Table Model Manager\n\n" +
                        "Define table models with columns and relations, then\n" +
                        "browse, create, edit and delete their data. Apps bundle\n" +
                        "ISPF Dialog Manager-style custom panels (dator_features)\n" +
                        "bound to those tables, reachable from the Apps menu or\n" +
                        "by shortcut (Ctrl+G).\n\n" +
                        "Database: " + getDatabasePath());
                return true;
            } else if (id == MID_MANAGE_APPS) {
                new AppListWindow(this);
                return true;
            } else if (id == MID_JUMP_SHORTCUT) {
                promptJumpToShortcut();
                return true;
            } else if (menuFeatureIds.containsKey(id)) {
                launchFeature(menuFeatureIds.get(id));
                return true;
            }
        } catch (Exception e) {
            messageBox("Error", e.getMessage() == null ? e.toString() : e.getMessage());
            return true;
        }
        return super.onMenu(event);
    }

    private void promptJumpToShortcut() throws SQLException {
        TInputBox box = inputBox("Jump to Panel", "Enter the panel's shortcut:", "");
        if (box.isCancel()) {
            return;
        }
        String shortcut = box.getText().trim();
        if (shortcut.isEmpty()) {
            return;
        }
        DatorFeature feature = appRepository.findFeatureByShortcut(shortcut);
        if (feature == null) {
            messageBox("Not Found", "No panel is bound to shortcut \"" + shortcut + "\".");
            return;
        }
        launchFeature(feature);
    }

    /** Opens the data list for a feature's bound table, edited through its custom panel. */
    public void launchFeature(DatorFeature feature) {
        try {
            DatorTable table = metaRepository.getTable(feature.getTableId());
            if (table == null) {
                messageBox("Error", "The table \"" + feature.getDisplayLabel() +
                        "\" binds to no longer exists.");
                return;
            }
            new FeatureDataWindow(this, feature, table);
        } catch (Exception e) {
            showError(e);
        }
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
        rebuildAppsMenu();
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
