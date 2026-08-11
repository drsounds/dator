package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jexer.TAction;
import jexer.TKeypress;
import jexer.TMessageBox;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorFeature;
import se.spacify.dator.model.DatorTable;
import se.spacify.dator.panel.PanelDefinition;
import se.spacify.dator.panel.PanelParseException;
import se.spacify.dator.panel.PanelParser;

/**
 * The launched form of a DatorFeature: browses the rows of the feature's
 * bound table exactly like DataListWindow, but New/Edit open the feature's
 * custom ISPF-style panel (PanelRecordWindow) instead of the generic
 * auto-generated form.
 */
public class FeatureDataWindow extends TWindow {

    private final DatorApplication app;
    private final DatorFeature feature;
    private final DatorTable table;
    private List<DatorColumn> columns = new ArrayList<>();
    private List<Map<String, Object>> data = new ArrayList<>();
    private TTableWidget grid;

    public FeatureDataWindow(DatorApplication app, DatorFeature feature, DatorTable table) {
        super(app, feature.getDisplayLabel() +
                (feature.getShortcut() == null ? "" : " (=" + feature.getShortcut() + ")"), 96, 26);
        this.app = app;
        this.feature = feature;
        this.table = table;
        setupWidgets();
        reload();
    }

    private void setupWidgets() {
        addLabel("Ins=New  F4/Enter=Edit  Del=Delete  F5=Refresh  Esc=Close", 2, 1);

        addButton("New", 2, getHeight() - 3, new TAction() {
            public void DO() {
                newRecord();
            }
        });
        addButton("Edit", 12, getHeight() - 3, new TAction() {
            public void DO() {
                editSelected();
            }
        });
        addButton("Delete", 22, getHeight() - 3, new TAction() {
            public void DO() {
                deleteSelected();
            }
        });
        addButton("Refresh", 33, getHeight() - 3, new TAction() {
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
            columns = app.getMetaRepository().listColumns(table.getId());
            data = app.getDataRepository().listRows(table, columns);
        } catch (Exception e) {
            app.showError(e);
            return;
        }

        if (grid != null) {
            grid.remove();
        }

        int colCount = Math.max(columns.size(), 1);
        int rowCount = Math.max(data.size(), 1);
        grid = new TTableWidget(this, 1, 3, getWidth() - 4, getHeight() - 8, colCount, rowCount);

        if (columns.isEmpty()) {
            grid.setColumnLabel(0, "(no columns)");
        } else {
            for (int c = 0; c < columns.size(); c++) {
                DatorColumn col = columns.get(c);
                grid.setColumnLabel(c, col.getDisplayLabel());
                int width = Math.max(10, Math.min(24, col.getDisplayLabel().length() + 2));
                grid.setColumnWidth(c, width);
            }
        }
        grid.setHighlightRow(true);

        if (data.isEmpty()) {
            if (!columns.isEmpty()) {
                grid.setCellText(0, 0, "(no rows - press Ins to add one)");
            }
        } else {
            for (int r = 0; r < data.size(); r++) {
                Map<String, Object> row = data.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    grid.setCellText(c, r, formatValue(row.get(columns.get(c).getName())));
                }
            }
        }
        for (int c = 0; c < colCount; c++) {
            grid.setColumnReadOnly(c, true);
        }
        activate(grid);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[]) {
            return "<blob:" + ((byte[]) value).length + "b>";
        }
        return value.toString();
    }

    private Long selectedRowid() {
        if (data.isEmpty() || grid == null) {
            return null;
        }
        int row = grid.getSelectedRowNumber();
        if (row < 0 || row >= data.size()) {
            return null;
        }
        Object v = data.get(row).get(DataRepository.ROWID);
        return v == null ? null : ((Number) v).longValue();
    }

    private PanelDefinition parsePanel() {
        try {
            return PanelParser.parse(feature.getPanelSource());
        } catch (PanelParseException e) {
            app.messageBox("Panel Error", "The panel for \"" + feature.getDisplayLabel() +
                    "\" is invalid:\n" + e.getMessage());
            return null;
        }
    }

    private void newRecord() {
        PanelDefinition panel = parsePanel();
        if (panel == null) {
            return;
        }
        try {
            new PanelRecordWindow(app, table, columns, panel,
                    "New: " + feature.getDisplayLabel(), null, new TAction() {
                        public void DO() {
                            reload();
                        }
                    });
        } catch (PanelParseException e) {
            app.messageBox("Panel Error", e.getMessage());
        }
    }

    private void editSelected() {
        Long rowid = selectedRowid();
        if (rowid == null) {
            return;
        }
        PanelDefinition panel = parsePanel();
        if (panel == null) {
            return;
        }
        try {
            Map<String, Object> row = app.getDataRepository().getRow(table, columns, rowid);
            if (row == null) {
                app.messageBox("Not Found", "This row no longer exists.");
                reload();
                return;
            }
            new PanelRecordWindow(app, table, columns, panel,
                    "Edit: " + feature.getDisplayLabel(), row, new TAction() {
                        public void DO() {
                            reload();
                        }
                    });
        } catch (PanelParseException e) {
            app.messageBox("Panel Error", e.getMessage());
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private void deleteSelected() {
        Long rowid = selectedRowid();
        if (rowid == null) {
            return;
        }
        TMessageBox result = app.messageBox("Delete Row",
                "Delete this row? This cannot be undone.", TMessageBox.Type.YESNO);
        if (!result.isYes()) {
            return;
        }
        try {
            app.getDataRepository().deleteRow(table, rowid);
            reload();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        TKeypress key = event.getKey();
        if (key.equals(TKeypress.kbIns)) {
            newRecord();
            return;
        } else if (key.equals(TKeypress.kbF4) || key.equals(TKeypress.kbEnter)) {
            editSelected();
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
