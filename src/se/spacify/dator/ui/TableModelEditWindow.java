package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TField;
import jexer.TKeypress;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.MetaRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorTable;

/**
 * Create/edit window for a table model: name, label, a free-form grid of
 * user columns (name, type, PK/Null/Unique flags as Y/N, default value,
 * reorderable), and the list's default sort column/direction. The standard
 * bookkeeping columns (parent_id, number, created, updated, deleted, uuid,
 * slug) are tracked but not rendered in the grid - they're preserved as-is
 * on save rather than editable here.
 */
public class TableModelEditWindow extends TWindow {

    private static final int COL_NAME = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_PK = 2;
    private static final int COL_NULL = 3;
    private static final int COL_UNIQUE = 4;
    private static final int COL_DEFAULT = 5;

    private static final String NO_SORT_OPTION = "(none)";

    private final DatorApplication app;
    private final DatorTable table;
    private final boolean isNew;

    private TField nameField;
    private TField labelField;
    private TComboBox sortColumnCombo;
    private TComboBox sortDirectionCombo;
    private TTableWidget grid;
    private final List<ColRow> rows = new ArrayList<>();
    private final List<ColRow> hiddenRows = new ArrayList<>();

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
                88, 28, TWindow.MODAL);
        this.app = app;
        this.isNew = (table == null);
        this.table = isNew ? new DatorTable() : table;
        loadColumns();
        setupWidgets();
    }

    private void setupWidgets() {
        addLabel("Table name:", 2, 1);
        nameField = addField(16, 1, 24, false, isNew ? "" : table.getName());
        addLabel("Display label:", 44, 1);
        labelField = addField(59, 1, 21, false, table.getLabel() == null ? "" : table.getLabel());

        addLabel("Ins=Add Column  F3=Remove  Alt+Up/Alt+Down=Reorder  Types: " +
                String.join(", ", MetaRepository.COLUMN_TYPES), 2, 3);
        addLabel("PK / Null / Unique columns: type Y or N", 2, 4);

        addLabel("Sort by", 2, 6);
        sortColumnCombo = addComboBox(2, 7, 22, sortColumnOptions(), sortColumnIndex(), 6, null);
        addLabel("Direction", 26, 6);
        sortDirectionCombo = addComboBox(26, 7, 12,
                List.of(DatorTable.SORT_ASC, DatorTable.SORT_DESC),
                DatorTable.SORT_DESC.equalsIgnoreCase(table.getSortDirection()) ? 1 : 0, 3, null);

        addButton("Save", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doSave();
            }
        });
        addButton("Add Col", 10, getHeight() - 3, new TAction() {
            public void DO() {
                addColumnRow();
            }
        });
        addButton("Remove Col", 21, getHeight() - 3, new TAction() {
            public void DO() {
                removeColumnRow();
            }
        });
        addButton("Move Up", 35, getHeight() - 3, new TAction() {
            public void DO() {
                moveColumnRow(-1);
            }
        });
        addButton("Move Down", 46, getHeight() - 3, new TAction() {
            public void DO() {
                moveColumnRow(1);
            }
        });
        addButton("Cancel", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });

        rebuildGrid();
    }

    private List<String> sortColumnOptions() {
        List<String> names = new ArrayList<>();
        names.add(NO_SORT_OPTION);
        for (ColRow r : rows) {
            names.add(r.name);
        }
        for (ColRow r : hiddenRows) {
            names.add(r.name);
        }
        return names;
    }

    private int sortColumnIndex() {
        if (table.getSortColumnId() == null) {
            return 0;
        }
        int index = 1;
        for (ColRow r : rows) {
            if (r.id == table.getSortColumnId()) {
                return index;
            }
            index++;
        }
        for (ColRow r : hiddenRows) {
            if (r.id == table.getSortColumnId()) {
                return index;
            }
            index++;
        }
        return 0;
    }

    private void loadColumns() {
        rows.clear();
        hiddenRows.clear();
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
                    if (MetaRepository.HIDDEN_LIST_COLUMNS.contains(c.getName().toLowerCase(Locale.ROOT))) {
                        hiddenRows.add(r);
                    } else {
                        rows.add(r);
                    }
                }
            } catch (Exception e) {
                app.showError(e);
            }
        }
        if (rows.isEmpty() && hiddenRows.isEmpty()) {
            ColRow idRow = new ColRow();
            idRow.name = "id";
            idRow.type = "INTEGER";
            idRow.pk = "Y";
            idRow.nullable = "N";
            rows.add(idRow);

            // Standard bookkeeping columns get added on save regardless; track
            // them here (hidden from the grid) so the sort-by combo can offer
            // them too.
            for (DatorColumn std : MetaRepository.standardColumnDefs()) {
                ColRow r = new ColRow();
                r.name = std.getName();
                r.type = std.getDataType();
                r.pk = std.isPk() ? "Y" : "N";
                r.nullable = std.isNullable() ? "Y" : "N";
                r.unique = std.isUnique() ? "Y" : "N";
                r.defaultValue = std.getDefaultValue() == null ? "" : std.getDefaultValue();
                hiddenRows.add(r);
            }
        }
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
        grid = new TabbableTable(this, 2, 9, getWidth() - 4, getHeight() - 15, 6, rows.size());
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

    private void moveColumnRow(int delta) {
        if (grid == null) {
            return;
        }
        captureGridIntoRows();
        int idx = grid.getSelectedRowNumber();
        int target = idx + delta;
        if (idx < 0 || idx >= rows.size() || target < 0 || target >= rows.size()) {
            return;
        }
        ColRow moved = rows.remove(idx);
        rows.add(target, moved);
        rebuildGrid();
        grid.setSelectedRowNumber(target);
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
        int ordinal = 0;
        for (ColRow r : rows) {
            DatorColumn c = validateAndBuild(r, ordinal + 1);
            if (c == null) {
                return;
            }
            c.setOrdinal(ordinal++);
            columns.add(c);
        }
        for (ColRow r : hiddenRows) {
            DatorColumn c = validateAndBuild(r, ordinal + 1);
            if (c == null) {
                return;
            }
            c.setOrdinal(ordinal++);
            columns.add(c);
        }

        table.setName(tableName);
        table.setLabel(tableLabel.isEmpty() ? null : tableLabel);

        try {
            DatorTable saved = app.getMetaRepository().saveTableModel(table, columns);

            String sortName = sortColumnCombo.getText();
            if (NO_SORT_OPTION.equals(sortName) || sortName.isEmpty()) {
                saved.setSortColumnId(null);
            } else {
                Integer sortId = null;
                for (DatorColumn c : app.getMetaRepository().listColumns(saved.getId())) {
                    if (c.getName().equals(sortName)) {
                        sortId = c.getId();
                        break;
                    }
                }
                saved.setSortColumnId(sortId);
            }
            saved.setSortDirection(sortDirectionCombo.getText());
            app.getMetaRepository().updateTableSort(saved);

            app.showTableList();
            close();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private DatorColumn validateAndBuild(ColRow r, int rowNum) {
        if (r.name.isEmpty()) {
            app.messageBox("Validation Error", "Row " + rowNum + ": column name is required.");
            return null;
        }
        try {
            MetaRepository.validateIdentifier(r.name);
        } catch (IllegalArgumentException e) {
            app.messageBox("Validation Error", "Row " + rowNum + ": " + e.getMessage());
            return null;
        }
        String type = r.type.toUpperCase(Locale.ROOT);
        try {
            MetaRepository.validateType(type);
        } catch (IllegalArgumentException e) {
            app.messageBox("Validation Error", "Row " + rowNum + ": " + e.getMessage());
            return null;
        }
        Boolean pk = parseYesNo(r.pk, "PK", rowNum);
        if (pk == null) {
            return null;
        }
        Boolean nullable = parseYesNo(r.nullable, "Null", rowNum);
        if (nullable == null) {
            return null;
        }
        Boolean unique = parseYesNo(r.unique, "Unique", rowNum);
        if (unique == null) {
            return null;
        }

        DatorColumn c = new DatorColumn();
        c.setId(r.id);
        c.setName(r.name);
        c.setDataType(type);
        c.setPk(pk);
        c.setNullable(nullable);
        c.setUnique(unique);
        c.setDefaultValue(r.defaultValue.isEmpty() ? null : r.defaultValue);
        return c;
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
        } else if (key.equals(TKeypress.kbAltUp)) {
            moveColumnRow(-1);
            return;
        } else if (key.equals(TKeypress.kbAltDown)) {
            moveColumnRow(1);
            return;
        } else if (key.equals(TKeypress.kbEsc)) {
            close();
            return;
        }
        super.onKeypress(event);
    }
}
