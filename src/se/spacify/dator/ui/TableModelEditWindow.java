package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jexer.TAction;
import jexer.TField;
import jexer.TKeypress;
import jexer.TMessageBox;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.MetaRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorTable;

/**
 * Create/edit window for a table model: name, label, and a free-form grid of
 * columns (name, type, PK/Null/Unique flags as Y/N, default value).
 */
public class TableModelEditWindow extends TWindow {

    private static final int COL_NAME = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_PK = 2;
    private static final int COL_NULL = 3;
    private static final int COL_UNIQUE = 4;
    private static final int COL_DEFAULT = 5;

    private final DatorApplication app;
    private final DatorTable table;
    private final boolean isNew;

    private TField nameField;
    private TField labelField;
    private TTableWidget grid;
    private final List<ColRow> rows = new ArrayList<>();

    private static class ColRow {
        int id;
        String name = "";
        String type = "TEXT";
        String pk = "N";
        String nullable = "Y";
        String unique = "N";
        String defaultValue = "";
    }

    public TableModelEditWindow(DatorApplication app, DatorTable table) {
        super(app, table == null ? "New Table Model" : "Edit Table Model: " + table.getName(),
                84, 24, TWindow.MODAL);
        this.app = app;
        this.isNew = (table == null);
        this.table = isNew ? new DatorTable() : table;
        setupWidgets();
        loadColumns();
    }

    private void setupWidgets() {
        addLabel("Table name:", 2, 1);
        nameField = addField(16, 1, 24, false, isNew ? "" : table.getName());
        addLabel("Display label:", 44, 1);
        labelField = addField(59, 1, 21, false, table.getLabel() == null ? "" : table.getLabel());

        addLabel("Ins=Add Column  F3=Remove Selected Column  Types: " +
                String.join(", ", MetaRepository.COLUMN_TYPES), 2, 3);
        addLabel("PK / Null / Unique columns: type Y or N", 2, 4);

        addButton("Save", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doSave();
            }
        });
        addButton("Add Column", 12, getHeight() - 3, new TAction() {
            public void DO() {
                addColumnRow();
            }
        });
        addButton("Remove Column", 27, getHeight() - 3, new TAction() {
            public void DO() {
                removeColumnRow();
            }
        });
        addButton("Cancel", getWidth() - 14, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private void loadColumns() {
        rows.clear();
        if (!isNew) {
            try {
                for (DatorColumn c : app.getMetaRepository().listColumns(table.getId())) {
                    ColRow r = new ColRow();
                    r.id = c.getId();
                    r.name = c.getName();
                    r.type = c.getDataType();
                    r.pk = c.isPk() ? "Y" : "N";
                    r.nullable = c.isNullable() ? "Y" : "N";
                    r.unique = c.isUnique() ? "Y" : "N";
                    r.defaultValue = c.getDefaultValue() == null ? "" : c.getDefaultValue();
                    rows.add(r);
                }
            } catch (Exception e) {
                app.showError(e);
            }
        }
        if (rows.isEmpty()) {
            ColRow r = new ColRow();
            r.name = "id";
            r.type = "INTEGER";
            r.pk = "Y";
            r.nullable = "N";
            rows.add(r);
        }
        rebuildGrid();
    }

    private void captureGridIntoRows() {
        if (grid == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            ColRow r = rows.get(i);
            r.name = grid.getCellText(COL_NAME, i).trim();
            r.type = grid.getCellText(COL_TYPE, i).trim();
            r.pk = grid.getCellText(COL_PK, i).trim();
            r.nullable = grid.getCellText(COL_NULL, i).trim();
            r.unique = grid.getCellText(COL_UNIQUE, i).trim();
            r.defaultValue = grid.getCellText(COL_DEFAULT, i).trim();
        }
    }

    private void rebuildGrid() {
        int selected = (grid != null) ? grid.getSelectedRowNumber() : 0;
        if (grid != null) {
            grid.remove();
        }
        grid = new TTableWidget(this, 2, 6, getWidth() - 4, getHeight() - 12, 6, rows.size());
        grid.setColumnLabel(COL_NAME, "Name");
        grid.setColumnLabel(COL_TYPE, "Type");
        grid.setColumnLabel(COL_PK, "PK");
        grid.setColumnLabel(COL_NULL, "Null");
        grid.setColumnLabel(COL_UNIQUE, "Uniq");
        grid.setColumnLabel(COL_DEFAULT, "Default");
        grid.setColumnWidth(COL_NAME, 18);
        grid.setColumnWidth(COL_TYPE, 10);
        grid.setColumnWidth(COL_PK, 5);
        grid.setColumnWidth(COL_NULL, 6);
        grid.setColumnWidth(COL_UNIQUE, 6);
        grid.setColumnWidth(COL_DEFAULT, 16);
        grid.setHighlightRow(true);

        for (int i = 0; i < rows.size(); i++) {
            ColRow r = rows.get(i);
            grid.setCellText(COL_NAME, i, r.name);
            grid.setCellText(COL_TYPE, i, r.type);
            grid.setCellText(COL_PK, i, r.pk);
            grid.setCellText(COL_NULL, i, r.nullable);
            grid.setCellText(COL_UNIQUE, i, r.unique);
            grid.setCellText(COL_DEFAULT, i, r.defaultValue);
        }

        if (selected >= 0 && selected < rows.size()) {
            grid.setSelectedRowNumber(selected);
        }
        activate(grid);
    }

    private void addColumnRow() {
        captureGridIntoRows();
        rows.add(new ColRow());
        rebuildGrid();
        grid.setSelectedRowNumber(rows.size() - 1);
    }

    private void removeColumnRow() {
        if (rows.size() <= 1) {
            app.messageBox("Cannot Remove", "A table needs at least one column.");
            return;
        }
        captureGridIntoRows();
        int idx = grid.getSelectedRowNumber();
        if (idx < 0 || idx >= rows.size()) {
            return;
        }
        rows.remove(idx);
        rebuildGrid();
    }

    private void doSave() {
        captureGridIntoRows();

        String tableName = nameField.getText().trim();
        String tableLabel = labelField.getText().trim();
        if (tableName.isEmpty()) {
            app.messageBox("Validation Error", "Table name is required.");
            return;
        }

        List<DatorColumn> columns = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ColRow r = rows.get(i);
            int rowNum = i + 1;
            if (r.name.isEmpty()) {
                app.messageBox("Validation Error", "Row " + rowNum + ": column name is required.");
                return;
            }
            try {
                MetaRepository.validateIdentifier(r.name);
            } catch (IllegalArgumentException e) {
                app.messageBox("Validation Error", "Row " + rowNum + ": " + e.getMessage());
                return;
            }
            String type = r.type.toUpperCase(Locale.ROOT);
            try {
                MetaRepository.validateType(type);
            } catch (IllegalArgumentException e) {
                app.messageBox("Validation Error", "Row " + rowNum + ": " + e.getMessage());
                return;
            }
            Boolean pk = parseYesNo(r.pk, "PK", rowNum);
            if (pk == null) return;
            Boolean nullable = parseYesNo(r.nullable, "Null", rowNum);
            if (nullable == null) return;
            Boolean unique = parseYesNo(r.unique, "Unique", rowNum);
            if (unique == null) return;

            DatorColumn c = new DatorColumn();
            c.setId(r.id);
            c.setName(r.name);
            c.setDataType(type);
            c.setPk(pk);
            c.setNullable(nullable);
            c.setUnique(unique);
            c.setDefaultValue(r.defaultValue.isEmpty() ? null : r.defaultValue);
            c.setOrdinal(i);
            columns.add(c);
        }

        table.setName(tableName);
        table.setLabel(tableLabel.isEmpty() ? null : tableLabel);

        try {
            app.getMetaRepository().saveTableModel(table, columns);
            app.showTableList();
            close();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private Boolean parseYesNo(String value, String fieldName, int rowNum) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.equals("Y") || v.equals("YES")) {
            return Boolean.TRUE;
        }
        if (v.equals("N") || v.equals("NO") || v.isEmpty()) {
            return Boolean.FALSE;
        }
        app.messageBox("Validation Error", "Row " + rowNum + ": " + fieldName + " must be Y or N.");
        return null;
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        TKeypress key = event.getKey();
        if (key.equals(TKeypress.kbIns)) {
            addColumnRow();
            return;
        } else if (key.equals(TKeypress.kbF3)) {
            removeColumnRow();
            return;
        } else if (key.equals(TKeypress.kbEsc)) {
            close();
            return;
        }
        super.onKeypress(event);
    }
}
