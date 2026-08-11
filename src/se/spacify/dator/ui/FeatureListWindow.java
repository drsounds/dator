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
import se.spacify.dator.model.DatorFeature;
import se.spacify.dator.model.DatorTable;

/**
 * Lists the features (panel + bound table + shortcut) owned by one app, and
 * lets the user create, edit, delete or launch one.
 */
public class FeatureListWindow extends TWindow {

    private final DatorApplication app;
    private final DatorApp datorApp;
    private TTableWidget grid;
    private List<DatorFeature> features = new ArrayList<>();

    public FeatureListWindow(DatorApplication app, DatorApp datorApp) {
        super(app, "Features: " + datorApp.getDisplayLabel(), 90, 24);
        this.app = app;
        this.datorApp = datorApp;
        setupWidgets();
        reload();
    }

    private void setupWidgets() {
        addLabel("Ins=New  F4=Edit  Enter/F7=Launch  Del=Delete  F5=Refresh  Esc=Close", 2, 1);

        addButton("New Feature", 2, getHeight() - 3, new TAction() {
            public void DO() {
                newFeature();
            }
        });
        addButton("Edit", 16, getHeight() - 3, new TAction() {
            public void DO() {
                editSelected();
            }
        });
        addButton("Launch", 26, getHeight() - 3, new TAction() {
            public void DO() {
                launchSelected();
            }
        });
        addButton("Delete", 37, getHeight() - 3, new TAction() {
            public void DO() {
                deleteSelected();
            }
        });
        addButton("Refresh", 47, getHeight() - 3, new TAction() {
            public void DO() {
                reload();
            }
        });
        addButton("Close", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    public void reload() {
        try {
            features = app.getAppRepository().listFeatures(datorApp.getId());
        } catch (Exception e) {
            app.showError(e);
            features = new ArrayList<>();
        }

        if (grid != null) {
            grid.remove();
        }

        int rowCount = Math.max(features.size(), 1);
        grid = new TTableWidget(this, 1, 3, getWidth() - 4, getHeight() - 9, 4, rowCount);
        grid.setColumnLabel(0, "Name");
        grid.setColumnLabel(1, "Label");
        grid.setColumnLabel(2, "Table");
        grid.setColumnLabel(3, "Shortcut");
        grid.setColumnWidth(0, 18);
        grid.setColumnWidth(1, 24);
        grid.setColumnWidth(2, 18);
        grid.setColumnWidth(3, 12);
        grid.setHighlightRow(true);

        if (features.isEmpty()) {
            grid.setCellText(0, 0, "(no features yet - press Ins)");
        } else {
            for (int i = 0; i < features.size(); i++) {
                DatorFeature f = features.get(i);
                grid.setCellText(0, i, f.getName());
                grid.setCellText(1, i, f.getLabel() == null ? "" : f.getLabel());
                grid.setCellText(2, i, tableNameOf(f));
                grid.setCellText(3, i, f.getShortcut() == null ? "" : f.getShortcut());
            }
        }
        for (int c = 0; c < 4; c++) {
            grid.setColumnReadOnly(c, true);
        }
        activate(grid);
    }

    private String tableNameOf(DatorFeature f) {
        try {
            DatorTable t = app.getMetaRepository().getTable(f.getTableId());
            return t == null ? "?" : t.getDisplayLabel();
        } catch (Exception e) {
            return "?";
        }
    }

    private DatorFeature selectedFeature() {
        if (features.isEmpty() || grid == null) {
            return null;
        }
        int row = grid.getSelectedRowNumber();
        if (row < 0 || row >= features.size()) {
            return null;
        }
        return features.get(row);
    }

    private void newFeature() {
        new FeatureEditWindow(app, datorApp, null, new TAction() {
            public void DO() {
                reload();
                app.rebuildAppsMenu();
            }
        });
    }

    private void editSelected() {
        DatorFeature f = selectedFeature();
        if (f == null) {
            return;
        }
        new FeatureEditWindow(app, datorApp, f, new TAction() {
            public void DO() {
                reload();
                app.rebuildAppsMenu();
            }
        });
    }

    private void launchSelected() {
        DatorFeature f = selectedFeature();
        if (f == null) {
            return;
        }
        app.launchFeature(f);
    }

    private void deleteSelected() {
        DatorFeature f = selectedFeature();
        if (f == null) {
            return;
        }
        TMessageBox result = app.messageBox("Delete Feature",
                "Delete feature \"" + f.getDisplayLabel() + "\"? This cannot be undone.",
                TMessageBox.Type.YESNO);
        if (!result.isYes()) {
            return;
        }
        try {
            app.getAppRepository().deleteFeature(f);
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
            newFeature();
            return;
        } else if (key.equals(TKeypress.kbF4)) {
            editSelected();
            return;
        } else if (key.equals(TKeypress.kbEnter) || key.equals(TKeypress.kbF7)) {
            launchSelected();
            return;
        } else if (key.equals(TKeypress.kbDel)) {
            deleteSelected();
            return;
        } else if (key.equals(TKeypress.kbF5)) {
            reload();
            return;
        } else if (key.equals(TKeypress.kbEsc)) {
            close();
            return;
        }
        super.onKeypress(event);
    }
}
