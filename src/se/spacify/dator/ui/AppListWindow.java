package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;

import jexer.TAction;
import jexer.TKeypress;
import jexer.TMessageBox;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.model.DatorApp;

/**
 * Home window for apps: lists every DatorApp and lets the user create, edit,
 * delete one, or jump into managing its features (panels + shortcuts).
 */
public class AppListWindow extends TWindow {

    private final DatorApplication app;
    private TTableWidget grid;
    private List<DatorApp> apps = new ArrayList<>();

    public AppListWindow(DatorApplication app) {
        super(app, "Apps", 78, 22);
        this.app = app;
        setupWidgets();
        reload();
    }

    private void setupWidgets() {
        addLabel("Ins=New  F4=Edit  Enter/F7=Features  Del=Delete  F5=Refresh", 2, 1);

        addButton("New App", 2, getHeight() - 4, new TAction() {
            public void DO() {
                newApp();
            }
        });
        addButton("Edit App", 15, getHeight() - 4, new TAction() {
            public void DO() {
                editSelected();
            }
        });
        addButton("Features", 29, getHeight() - 4, new TAction() {
            public void DO() {
                featuresSelected();
            }
        });
        addButton("Delete", 41, getHeight() - 4, new TAction() {
            public void DO() {
                deleteSelected();
            }
        });
        addButton("Refresh", 51, getHeight() - 4, new TAction() {
            public void DO() {
                reload();
            }
        });
    }

    public void reload() {
        try {
            apps = app.getAppRepository().listApps();
        } catch (Exception e) {
            app.showError(e);
            apps = new ArrayList<>();
        }

        if (grid != null) {
            grid.remove();
        }

        int rowCount = Math.max(apps.size(), 1);
        grid = new TTableWidget(this, 1, 3, getWidth() - 4, getHeight() - 8, 3, rowCount);
        grid.setColumnLabel(0, "Name");
        grid.setColumnLabel(1, "Label");
        grid.setColumnLabel(2, "Features");
        grid.setColumnWidth(0, 20);
        grid.setColumnWidth(1, 24);
        grid.setColumnWidth(2, 10);
        grid.setHighlightRow(true);

        if (apps.isEmpty()) {
            grid.setCellText(0, 0, "(no apps yet - press Ins)");
        } else {
            for (int i = 0; i < apps.size(); i++) {
                DatorApp a = apps.get(i);
                grid.setCellText(0, i, a.getName());
                grid.setCellText(1, i, a.getLabel() == null ? "" : a.getLabel());
                try {
                    grid.setCellText(2, i, String.valueOf(app.getAppRepository().listFeatures(a.getId()).size()));
                } catch (Exception e) {
                    grid.setCellText(2, i, "?");
                }
            }
        }
        for (int c = 0; c < 3; c++) {
            grid.setColumnReadOnly(c, true);
        }
        activate(grid);
    }

    private DatorApp selectedApp() {
        if (apps.isEmpty() || grid == null) {
            return null;
        }
        int row = grid.getSelectedRowNumber();
        if (row < 0 || row >= apps.size()) {
            return null;
        }
        return apps.get(row);
    }

    private void newApp() {
        new AppEditWindow(app, null, new TAction() {
            public void DO() {
                reload();
                app.rebuildAppsMenu();
            }
        });
    }

    private void editSelected() {
        DatorApp a = selectedApp();
        if (a == null) {
            return;
        }
        new AppEditWindow(app, a, new TAction() {
            public void DO() {
                reload();
                app.rebuildAppsMenu();
            }
        });
    }

    private void featuresSelected() {
        DatorApp a = selectedApp();
        if (a == null) {
            return;
        }
        new FeatureListWindow(app, a);
    }

    private void deleteSelected() {
        DatorApp a = selectedApp();
        if (a == null) {
            return;
        }
        TMessageBox result = app.messageBox("Delete App",
                "Delete app \"" + a.getDisplayLabel() + "\" and all of its features?\n" +
                        "This cannot be undone.",
                TMessageBox.Type.YESNO);
        if (!result.isYes()) {
            return;
        }
        try {
            app.getAppRepository().deleteApp(a);
            reload();
            app.rebuildAppsMenu();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        TKeypress key = event.getKey();
        if (key.equals(TKeypress.kbIns)) {
            newApp();
            return;
        } else if (key.equals(TKeypress.kbF4)) {
            editSelected();
            return;
        } else if (key.equals(TKeypress.kbEnter) || key.equals(TKeypress.kbF7)) {
            featuresSelected();
            return;
        } else if (key.equals(TKeypress.kbDel)) {
            deleteSelected();
            return;
        } else if (key.equals(TKeypress.kbF5)) {
            reload();
            return;
        }
        super.onKeypress(event);
    }
}
