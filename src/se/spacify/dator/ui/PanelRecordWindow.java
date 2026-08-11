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
import se.spacify.dator.panel.InitAssignment;
import se.spacify.dator.panel.PanelAttr;
import se.spacify.dator.panel.PanelDefinition;
import se.spacify.dator.panel.PanelField;
import se.spacify.dator.panel.PanelLiteral;
import se.spacify.dator.panel.PanelParseException;
import se.spacify.dator.panel.VerifyRule;

/**
 * Renders a parsed ISPF Dialog Manager-style {@link PanelDefinition} as a
 * create/edit form for one row of a DatorTable, at the exact row/column
 * layout the panel author typed in )BODY. Foreign-key columns still get a
 * combo box of referenced rows, same as the generic DataRecordWindow;
 * )INIT supplies default text for new rows and )PROC's VER rules gate Save.
 */
public class PanelRecordWindow extends TWindow {

    private static final int OFFSET_X = 2;
    private static final int OFFSET_Y = 1;

    private final DatorApplication app;
    private final DatorTable table;
    private final PanelDefinition panel;
    private final Map<String, Object> existingRow;
    private final boolean isNew;
    private final TAction onSaved;

    private final DatorColumn autoPkColumn;
    private final Map<String, DatorColumn> columnsByFieldName = new LinkedHashMap<>();
    private final Map<String, DatorRelation> relationByColumnName = new LinkedHashMap<>();
    private final List<FieldBinding> bindings = new ArrayList<>();

    private static class FieldBinding {
        PanelField field;
        DatorColumn column;
        TField textField;
        TComboBox combo;
        List<Object> comboValues;
    }

    /**
     * @throws PanelParseException if a )BODY field does not match any column
     *      on {@code table} by name (case-insensitive)
     */
    public PanelRecordWindow(DatorApplication app, DatorTable table, List<DatorColumn> columns,
            PanelDefinition panel, String title, Map<String, Object> existingRow, TAction onSaved) {
        super(app, title, windowWidth(panel), windowHeight(panel), TWindow.MODAL);
        this.app = app;
        this.table = table;
        this.panel = panel;
        this.existingRow = existingRow;
        this.isNew = (existingRow == null);
        this.onSaved = onSaved;
        this.autoPkColumn = findAutoIncrementPk(columns);

        bindColumns(columns);
        loadRelations();
        setupWidgets();
    }

    private static int windowWidth(PanelDefinition panel) {
        return Math.max(50, Math.min(132, panel.getBodyCols() + OFFSET_X + 3));
    }

    private static int windowHeight(PanelDefinition panel) {
        return Math.max(10, Math.min(45, panel.getBodyRows() + OFFSET_Y + 6));
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

    private void bindColumns(List<DatorColumn> columns) {
        Map<String, DatorColumn> byName = new LinkedHashMap<>();
        for (DatorColumn c : columns) {
            byName.put(c.getName().toUpperCase(Locale.ROOT), c);
        }
        for (PanelField f : panel.getFields()) {
            DatorColumn col = byName.get(f.getName());
            if (col == null) {
                throw new PanelParseException("Panel field \"" + f.getName() +
                        "\" does not match any column on table \"" + table.getName() + "\"");
            }
            columnsByFieldName.put(f.getName(), col);
        }
    }

    private void loadRelations() {
        try {
            for (DatorRelation r : app.getMetaRepository().listRelations(table.getId())) {
                for (DatorColumn c : columnsByFieldName.values()) {
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
        for (PanelLiteral lit : panel.getLiterals()) {
            addLabel(lit.getText(), lit.getCol() + OFFSET_X, lit.getRow() + OFFSET_Y);
        }

        for (PanelField f : panel.getFields()) {
            DatorColumn c = columnsByFieldName.get(f.getName());
            int x = f.getCol() + OFFSET_X;
            int y = f.getRow() + OFFSET_Y;
            int maxWidth = Math.max(4, getWidth() - x - 2);
            int width = Math.min(Math.max(f.getAttr().getLen(), 4), maxWidth);

            if (isNew && autoPkColumn != null && c.getId() == autoPkColumn.getId()) {
                continue;
            }
            if ("BLOB".equalsIgnoreCase(c.getDataType())) {
                addLabel("(blob)", x, y);
                continue;
            }

            FieldBinding binding = new FieldBinding();
            binding.field = f;
            binding.column = c;

            DatorRelation rel = relationByColumnName.get(c.getName());
            boolean protectedField = !f.getAttr().isInput()
                    || (!isNew && autoPkColumn != null && c.getId() == autoPkColumn.getId());

            if (rel != null && f.getAttr().isInput()) {
                buildFkCombo(binding, rel, x, y, width);
            } else {
                Object currentVal = existingRow == null ? null : existingRow.get(c.getName());
                String text;
                if (currentVal != null) {
                    text = currentVal.toString();
                } else if (isNew) {
                    text = defaultTextFor(f.getName());
                } else {
                    text = "";
                }
                TField field = addField(x, y, width, false, text);
                if (protectedField) {
                    field.setEnabled(false);
                }
                binding.textField = field;
            }
            bindings.add(binding);
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

    private String defaultTextFor(String fieldName) {
        for (InitAssignment init : panel.getInitAssignments()) {
            if (init.getField().equalsIgnoreCase(fieldName)) {
                return init.getValue();
            }
        }
        return "";
    }

    private void buildFkCombo(FieldBinding binding, DatorRelation rel, int x, int y, int width) {
        DatorColumn fkCol = binding.column;
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
                    String label = (display == null) ? String.valueOf(value) : value + ": " + display;
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

        TComboBox combo = addComboBox(x, y, width, labels, selectedIndex, 8, null);
        binding.combo = combo;
        binding.comboValues = values;
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

    private FieldBinding bindingFor(String fieldName) {
        for (FieldBinding b : bindings) {
            if (b.field.getName().equalsIgnoreCase(fieldName)) {
                return b;
            }
        }
        return null;
    }

    private String currentText(FieldBinding b) {
        if (b.combo != null) {
            return b.combo.getText() == null ? "" : b.combo.getText();
        }
        if (b.textField != null) {
            return b.textField.getText() == null ? "" : b.textField.getText();
        }
        return "";
    }

    private boolean runVerifyRules() {
        for (VerifyRule rule : panel.getVerifyRules()) {
            FieldBinding b = bindingFor(rule.getField());
            if (b == null) {
                continue;
            }
            String text = currentText(b).trim();
            String label = b.column.getDisplayLabel();
            switch (rule.getKind()) {
                case VerifyRule.NONBLANK:
                    if (text.isEmpty()) {
                        app.messageBox("Validation Error", label + " must not be blank.");
                        return false;
                    }
                    break;
                case VerifyRule.NUM:
                    if (!text.isEmpty()) {
                        try {
                            Double.parseDouble(text);
                        } catch (NumberFormatException e) {
                            app.messageBox("Validation Error", label + " must be numeric.");
                            return false;
                        }
                    }
                    break;
                case VerifyRule.RANGE:
                    if (!text.isEmpty()) {
                        try {
                            double v = Double.parseDouble(text);
                            double min = Double.parseDouble(rule.getArgs().get(0));
                            double max = Double.parseDouble(rule.getArgs().get(1));
                            if (v < min || v > max) {
                                app.messageBox("Validation Error",
                                        label + " must be between " + rule.getArgs().get(0) +
                                                " and " + rule.getArgs().get(1) + ".");
                                return false;
                            }
                        } catch (NumberFormatException e) {
                            app.messageBox("Validation Error", label + " must be numeric.");
                            return false;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private void doSave() {
        if (!runVerifyRules()) {
            return;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldBinding b : bindings) {
            DatorColumn c = b.column;
            if (!b.field.getAttr().isInput()) {
                continue;
            }

            if (b.combo != null) {
                String text = currentText(b);
                int idx = indexOfLabel(b.combo, text);
                Object selected = null;
                boolean found = false;
                if (idx >= 0 && idx < b.comboValues.size()) {
                    selected = b.comboValues.get(idx);
                    found = true;
                }
                if (!found && !c.isNullable()) {
                    app.messageBox("Validation Error", c.getDisplayLabel() + " is required.");
                    return;
                }
                values.put(c.getName(), selected);
                continue;
            }

            if (b.textField == null || !b.textField.isEnabled()) {
                continue;
            }
            String text = currentText(b).trim();
            if (b.field.getAttr().isCaps()) {
                text = text.toUpperCase(Locale.ROOT);
            }
            if (text.isEmpty()) {
                if (!c.isNullable() || b.field.getAttr().isRequired()) {
                    app.messageBox("Validation Error", c.getDisplayLabel() + " is required.");
                    return;
                }
                if (c.getDefaultValue() != null && !c.getDefaultValue().isEmpty() && isNew) {
                    continue;
                }
                values.put(c.getName(), null);
                continue;
            }
            String type = c.getDataType().toUpperCase(Locale.ROOT);
            if (type.equals("INTEGER")) {
                try {
                    values.put(c.getName(), Long.parseLong(text));
                } catch (NumberFormatException e) {
                    app.messageBox("Validation Error", c.getDisplayLabel() + " must be a whole number.");
                    return;
                }
            } else if (type.equals("REAL") || type.equals("NUMERIC")) {
                try {
                    values.put(c.getName(), Double.parseDouble(text));
                } catch (NumberFormatException e) {
                    app.messageBox("Validation Error", c.getDisplayLabel() + " must be a number.");
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
