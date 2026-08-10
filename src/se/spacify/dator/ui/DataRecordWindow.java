package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TField;
import jexer.TKeypress;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorRelation;
import se.spacify.dator.model.DatorTable;

/**
 * Create/edit form for a single row of a data table. Foreign-key columns
 * (per dator_table_relations) are rendered as a combo box of referenced
 * rows; everything else is a plain text field.
 */
public class DataRecordWindow extends TWindow {

    private final DatorApplication app;
    private final DatorTable table;
    private final List<DatorColumn> columns;
    private final Map<String, Object> existingRow;
    private final boolean isNew;
    private final TAction onSaved;

    private final DatorColumn autoPkColumn;
    private final Map<String, DatorRelation> relationByColumnName = new LinkedHashMap<>();

    private final Map<String, TField> fields = new LinkedHashMap<>();
    private final Map<String, TComboBox> combos = new LinkedHashMap<>();
    private final Map<String, List<Object>> comboValues = new LinkedHashMap<>();
    private final List<DatorColumn> blobColumns = new ArrayList<>();

    public DataRecordWindow(DatorApplication app, DatorTable table, List<DatorColumn> columns,
            Map<String, Object> existingRow, TAction onSaved) {
        super(app, (existingRow == null ? "New Row: " : "Edit Row: ") + table.getDisplayLabel(),
                74, Math.min(30, 8 + Math.max(1, columns.size()) + 3), TWindow.MODAL);
        this.app = app;
        this.table = table;
        this.columns = columns;
        this.existingRow = existingRow;
        this.isNew = (existingRow == null);
        this.onSaved = onSaved;
        this.autoPkColumn = findAutoIncrementPk(columns);

        loadRelations();
        setupWidgets();
    }

    private DatorColumn findAutoIncrementPk(List<DatorColumn> cols) {
        DatorColumn pk = null;
        int pkCount = 0;
        for (DatorColumn c : cols) {
            if (c.isPk()) {
                pkCount++;
                pk = c;
            }
        }
        if (pkCount == 1 && pk != null && "INTEGER".equalsIgnoreCase(pk.getDataType())) {
            return pk;
        }
        return null;
    }

    private void loadRelations() {
        try {
            for (DatorRelation r : app.getMetaRepository().listRelations(table.getId())) {
                for (DatorColumn c : columns) {
                    if (c.getId() == r.getColumnId()) {
                        relationByColumnName.put(c.getName(), r);
                    }
                }
            }
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private void setupWidgets() {
        int y = 1;
        for (DatorColumn c : columns) {
            if (isNew && autoPkColumn != null && c.getId() == autoPkColumn.getId()) {
                continue;
            }
            if ("BLOB".equalsIgnoreCase(c.getDataType())) {
                addLabel(labelFor(c) + " (blob - not editable)", 2, y);
                blobColumns.add(c);
                y++;
                continue;
            }

            DatorRelation rel = relationByColumnName.get(c.getName());
            addLabel(labelFor(c), 2, y);
            if (rel != null) {
                buildFkCombo(c, rel, y);
            } else {
                Object currentVal = existingRow == null ? null : existingRow.get(c.getName());
                String text = currentVal == null ? "" : currentVal.toString();
                TField field = addField(26, y, 42, false, text);
                if (!isNew && autoPkColumn != null && c.getId() == autoPkColumn.getId()) {
                    field.setEnabled(false);
                }
                fields.put(c.getName(), field);
            }
            y++;
        }

        addButton("Save", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doSave();
            }
        });
        addButton("Cancel", 13, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private String labelFor(DatorColumn c) {
        String suffix = c.isPk() ? " (PK)" : (!c.isNullable() ? " *" : "");
        return c.getDisplayLabel() + suffix + ":";
    }

    private void buildFkCombo(DatorColumn fkCol, DatorRelation rel, int y) {
        List<String> labels = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        int selectedIndex = -1;
        Object currentVal = existingRow == null ? null : existingRow.get(fkCol.getName());

        try {
            DatorTable refTable = app.getMetaRepository().getTable(rel.getRefTableId());
            if (refTable != null) {
                List<DatorColumn> refColumns = app.getMetaRepository().listColumns(refTable.getId());
                DatorColumn refValueColumn = null;
                if (rel.getRefColumnId() != null) {
                    for (DatorColumn rc : refColumns) {
                        if (rc.getId() == rel.getRefColumnId()) {
                            refValueColumn = rc;
                        }
                    }
                }
                DatorColumn displayColumn = pickDisplayColumn(refColumns, refValueColumn);
                List<Map<String, Object>> refRows = app.getDataRepository().listRows(refTable, refColumns);
                for (Map<String, Object> row : refRows) {
                    Object value = refValueColumn != null
                            ? row.get(refValueColumn.getName())
                            : row.get(DataRepository.ROWID);
                    Object display = displayColumn != null ? row.get(displayColumn.getName()) : null;
                    String label = (display == null) ? String.valueOf(value)
                            : value + ": " + display;
                    labels.add(label);
                    values.add(value);
                    if (currentVal != null && String.valueOf(currentVal).equals(String.valueOf(value))) {
                        selectedIndex = labels.size() - 1;
                    }
                }
            }
        } catch (Exception e) {
            app.showError(e);
        }

        if (fkCol.isNullable()) {
            labels.add(0, "(none)");
            values.add(0, null);
            if (selectedIndex >= 0) {
                selectedIndex++;
            } else {
                selectedIndex = 0;
            }
        } else if (selectedIndex < 0 && !labels.isEmpty()) {
            selectedIndex = 0;
        }

        TComboBox combo = addComboBox(26, y, 42, labels, selectedIndex, 8, null);
        combos.put(fkCol.getName(), combo);
        comboValues.put(fkCol.getName(), values);
    }

    private DatorColumn pickDisplayColumn(List<DatorColumn> refColumns, DatorColumn refValueColumn) {
        for (DatorColumn c : refColumns) {
            if ("TEXT".equalsIgnoreCase(c.getDataType())
                    && (refValueColumn == null || c.getId() != refValueColumn.getId())) {
                return c;
            }
        }
        return refValueColumn;
    }

    private void doSave() {
        Map<String, Object> values = new LinkedHashMap<>();

        for (DatorColumn c : columns) {
            if (autoPkColumn != null && c.getId() == autoPkColumn.getId()) {
                continue;
            }
            if (blobColumns.contains(c)) {
                continue;
            }

            if (combos.containsKey(c.getName())) {
                TComboBox combo = combos.get(c.getName());
                List<Object> possible = comboValues.get(c.getName());
                String text = combo.getText();
                Object selected = null;
                boolean found = false;
                int idx = indexOfLabel(combo, text);
                if (idx >= 0 && idx < possible.size()) {
                    selected = possible.get(idx);
                    found = true;
                }
                if (!found && !c.isNullable()) {
                    app.messageBox("Validation Error", labelFor(c) + " is required.");
                    return;
                }
                values.put(c.getName(), selected);
                continue;
            }

            TField field = fields.get(c.getName());
            if (field == null) {
                continue;
            }
            String text = field.getText().trim();
            if (text.isEmpty()) {
                if (!c.isNullable()) {
                    app.messageBox("Validation Error", labelFor(c) + " is required.");
                    return;
                }
                if (c.getDefaultValue() != null && !c.getDefaultValue().isEmpty() && isNew) {
                    continue; // let the column's DEFAULT apply
                }
                values.put(c.getName(), null);
                continue;
            }
            String type = c.getDataType().toUpperCase(Locale.ROOT);
            if (type.equals("INTEGER")) {
                try {
                    values.put(c.getName(), Long.parseLong(text));
                } catch (NumberFormatException e) {
                    app.messageBox("Validation Error", labelFor(c) + " must be a whole number.");
                    return;
                }
            } else if (type.equals("REAL") || type.equals("NUMERIC")) {
                try {
                    values.put(c.getName(), Double.parseDouble(text));
                } catch (NumberFormatException e) {
                    app.messageBox("Validation Error", labelFor(c) + " must be a number.");
                    return;
                }
            } else {
                values.put(c.getName(), text);
            }
        }

        try {
            if (isNew) {
                app.getDataRepository().insertRow(table, values);
            } else {
                Object rowidObj = existingRow.get(DataRepository.ROWID);
                long rowid = ((Number) rowidObj).longValue();
                app.getDataRepository().updateRow(table, rowid, values);
            }
            if (onSaved != null) {
                onSaved.DO();
            }
            close();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private int indexOfLabel(TComboBox combo, String text) {
        List<String> list = combo.getList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(text)) {
                return i;
            }
        }
        return -1;
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
