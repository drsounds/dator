package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;

import jexer.TAction;
import jexer.TKeypress;
import jexer.TMessageBox;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.model.DatorTable;

/**
 * Home window: lists every table model and lets the user create, edit,
 * delete a model, manage its relations, or jump into browsing its data.
 */
public class TableListWindow extends TWindow {

    private final DatorApplication app;
    private TTableWidget grid;
    private List<DatorTable> tables = new ArrayList<>();

    public TableListWindow(DatorApplication app) {
        super(app, "Table Models", 78, 22);
        this.app = app;
        setupWidgets();
        reload();
    }

    private void setupWidgets() {
        addLabel("Ins=New  F4=Edit  Enter/F7=Browse Data  F8=Relations  Del=Delete  F5=Refresh",
                2, 1);

        addButton("New Model", 2, getHeight() - 4, new TAction() {
            public void DO() {
                newModel();
            }
        });
        addButton("Edit Model", 15, getHeight() - 4, new TAction() {
            public void DO() {
                editSelected();
            }
        });
        addButton("Browse Data", 29, getHeight() - 4, new TAction() {
            public void DO() {
                browseSelected();
            }
        });
        addButton("Relations", 44, getHeight() - 4, new TAction() {
            public void DO() {
                relationsSelected();
            }
        });
        addButton("Delete", 57, getHeight() - 4, new TAction() {
            public void DO() {
                deleteSelected();
            }
        });
        addButton("Refresh", 67, getHeight() - 4, new TAction() {
            public void DO() {
                reload();
            }
        });
    }

    public void reload() {
        try {
            tables = app.getMetaRepository().listTables();
        } catch (Exception e) {
            app.showError(e);
            tables = new ArrayList<>();
        }

        if (grid != null) {
            grid.remove();
        }

        int rowCount = Math.max(tables.size(), 1);
        grid = new TTableWidget(this, 1, 3, getWidth() - 4, getHeight() - 8, 4, rowCount);
        grid.setColumnLabel(0, "Name");
        grid.setColumnLabel(1, "Label");
        grid.setColumnLabel(2, "Columns");
        grid.setColumnLabel(3, "Relations");
        grid.setColumnWidth(0, 20);
        grid.setColumnWidth(1, 20);
        grid.setColumnWidth(2, 10);
        grid.setColumnWidth(3, 10);
        grid.setHighlightRow(true);

        if (tables.isEmpty()) {
            grid.setCellText(0, 0, "(no table models yet - press Ins)");
        } else {
            for (int i = 0; i < tables.size(); i++) {
                DatorTable t = tables.get(i);
                grid.setCellText(0, i, t.getName());
                grid.setCellText(1, i, t.getLabel() == null ? "" : t.getLabel());
                try {
                    grid.setCellText(2, i, String.valueOf(app.getMetaRepository().listColumns(t.getId()).size()));
                    grid.setCellText(3, i, String.valueOf(app.getMetaRepository().listRelations(t.getId()).size()));
                } catch (Exception e) {
                    grid.setCellText(2, i, "?");
                    grid.setCellText(3, i, "?");
                }
            }
        }
        for (int c = 0; c < 4; c++) {
            grid.setColumnReadOnly(c, true);
        }
        activate(grid);
    }

    private DatorTable selectedTable() {
        if (tables.isEmpty() || grid == null) {
            return null;
        }
        int row = grid.getSelectedRowNumber();
        if (row < 0 || row >= tables.size()) {
            return null;
        }
        return tables.get(row);
    }

    private void newModel() {
        new TableModelEditWindow(app, null);
    }

    private void editSelected() {
        DatorTable t = selectedTable();
        if (t == null) {
            return;
        }
        new TableModelEditWindow(app, t);
    }

    private void browseSelected() {
        DatorTable t = selectedTable();
        if (t == null) {
            return;
        }
        new DataListWindow(app, t);
    }

    private void relationsSelected() {
        DatorTable t = selectedTable();
        if (t == null) {
            return;
        }
        new RelationEditWindow(app, t);
    }

    private void deleteSelected() {
        DatorTable t = selectedTable();
        if (t == null) {
            return;
        }
        TMessageBox result = app.messageBox("Delete Table Model",
                "Delete model \"" + t.getDisplayLabel() + "\" and its data table?\n" +
                        "This cannot be undone.",
                TMessageBox.Type.YESNO);
        if (!result.isYes()) {
            return;
        }
        try {
            app.getMetaRepository().deleteTableModel(t);
            reload();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        TKeypress key = event.getKey();
        if (key.equals(TKeypress.kbIns)) {
            newModel();
            return;
        } else if (key.equals(TKeypress.kbF4)) {
            editSelected();
            return;
        } else if (key.equals(TKeypress.kbEnter) || key.equals(TKeypress.kbF7)) {
            browseSelected();
            return;
        } else if (key.equals(TKeypress.kbF8)) {
            relationsSelected();
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
