package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TField;
import jexer.TKeypress;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorRelation;
import se.spacify.dator.model.DatorTable;

/**
 * Manage the foreign-key style relations owned by a single table model:
 * which column on this table points at which (table, column) elsewhere.
 */
public class RelationEditWindow extends TWindow {

    private final DatorApplication app;
    private final DatorTable table;

    private List<DatorTable> allTables = new ArrayList<>();
    private List<DatorColumn> myColumns = new ArrayList<>();
    private final Map<Integer, List<DatorColumn>> columnsCache = new HashMap<>();
    private final List<DatorRelation> relations = new ArrayList<>();

    private TComboBox fkColumnCombo;
    private TComboBox refTableCombo;
    private TComboBox refColumnCombo;
    private TComboBox typeCombo;
    private TField labelField;
    private TTableWidget grid;

    public RelationEditWindow(DatorApplication app, DatorTable table) {
        super(app, "Relations for: " + table.getDisplayLabel(), 90, 24, TWindow.MODAL);
        this.app = app;
        this.table = table;
        loadData();
        setupWidgets();
        rebuildGrid();
    }

    private void loadData() {
        try {
            allTables = app.getMetaRepository().listTables();
            myColumns = app.getMetaRepository().listColumns(table.getId());
            columnsCache.put(table.getId(), myColumns);
            relations.clear();
            relations.addAll(app.getMetaRepository().listRelations(table.getId()));
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private List<DatorColumn> columnsOf(int tableId) {
        List<DatorColumn> cached = columnsCache.get(tableId);
        if (cached != null) {
            return cached;
        }
        try {
            List<DatorColumn> cols = app.getMetaRepository().listColumns(tableId);
            columnsCache.put(tableId, cols);
            return cols;
        } catch (Exception e) {
            app.showError(e);
            return new ArrayList<>();
        }
    }

    private void setupWidgets() {
        List<String> myColumnNames = new ArrayList<>();
        for (DatorColumn c : myColumns) {
            myColumnNames.add(c.getName());
        }
        List<String> tableNames = new ArrayList<>();
        for (DatorTable t : allTables) {
            tableNames.add(t.getName());
        }

        addLabel("FK Column", 2, 1);
        fkColumnCombo = addComboBox(2, 2, 18, myColumnNames, myColumnNames.isEmpty() ? -1 : 0, 6, null);

        addLabel("References Table", 22, 1);
        refTableCombo = addComboBox(22, 2, 20, tableNames, tableNames.isEmpty() ? -1 : 0, 6,
                new TAction() {
                    public void DO() {
                        onRefTableChanged();
                    }
                });

        addLabel("References Column", 44, 1);
        refColumnCombo = addComboBox(44, 2, 18, refColumnNamesFor(0), 0, 6, null);

        addLabel("Type", 64, 1);
        typeCombo = addComboBox(64, 2, 14,
                List.of(DatorRelation.MANY_TO_ONE, DatorRelation.ONE_TO_ONE), 0, 3, null);

        addLabel("Label (optional)", 2, 4);
        labelField = addField(2, 5, 40, false, "");

        addButton("Add Relation", 45, 5, new TAction() {
            public void DO() {
                addRelation();
            }
        });
        addButton("Remove Selected", 61, 5, new TAction() {
            public void DO() {
                removeSelected();
            }
        });

        addButton("Save", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doSave();
            }
        });
        addButton("Close", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private List<String> refColumnNamesFor(int index) {
        if (index < 0 || index >= allTables.size()) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        for (DatorColumn c : columnsOf(allTables.get(index).getId())) {
            names.add(c.getName());
        }
        return names;
    }

    private void onRefTableChanged() {
        DatorTable ref = findTableByName(refTableCombo.getText());
        if (ref == null) {
            refColumnCombo.setList(new ArrayList<>());
            return;
        }
        List<DatorColumn> cols = columnsOf(ref.getId());
        List<String> names = new ArrayList<>();
        int pkIndex = 0;
        for (int i = 0; i < cols.size(); i++) {
            names.add(cols.get(i).getName());
            if (cols.get(i).isPk()) {
                pkIndex = i;
            }
        }
        refColumnCombo.setList(names);
        if (!names.isEmpty()) {
            refColumnCombo.setIndex(pkIndex);
            refColumnCombo.setText(names.get(pkIndex));
        }
    }

    private DatorTable findTableByName(String name) {
        for (DatorTable t : allTables) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        return null;
    }

    private DatorColumn findColumnByName(List<DatorColumn> cols, String name) {
        for (DatorColumn c : cols) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private void addRelation() {
        if (myColumns.isEmpty()) {
            app.messageBox("No Columns", "This table has no columns to use as a foreign key.");
            return;
        }
        DatorColumn fkCol = findColumnByName(myColumns, fkColumnCombo.getText());
        DatorTable refTable = findTableByName(refTableCombo.getText());
        if (fkCol == null || refTable == null) {
            app.messageBox("Validation Error", "Choose a column and a referenced table.");
            return;
        }
        List<DatorColumn> refCols = columnsOf(refTable.getId());
        DatorColumn refCol = findColumnByName(refCols, refColumnCombo.getText());

        DatorRelation r = new DatorRelation();
        r.setTableId(table.getId());
        r.setColumnId(fkCol.getId());
        r.setRefTableId(refTable.getId());
        r.setRefColumnId(refCol == null ? null : refCol.getId());
        r.setRelationType(typeCombo.getText().isEmpty() ? DatorRelation.MANY_TO_ONE : typeCombo.getText());
        r.setLabel(labelField.getText().trim().isEmpty() ? null : labelField.getText().trim());
        relations.add(r);
        labelField.setText("");
        rebuildGrid();
    }

    private void removeSelected() {
        if (relations.isEmpty() || grid == null) {
            return;
        }
        int row = grid.getSelectedRowNumber();
        if (row < 0 || row >= relations.size()) {
            return;
        }
        relations.remove(row);
        rebuildGrid();
    }

    private void rebuildGrid() {
        if (grid != null) {
            grid.remove();
        }
        int rowCount = Math.max(relations.size(), 1);
        grid = new TTableWidget(this, 2, 7, getWidth() - 4, getHeight() - 13, 5, rowCount);
        grid.setColumnLabel(0, "Column");
        grid.setColumnLabel(1, "-> Table");
        grid.setColumnLabel(2, "-> Column");
        grid.setColumnLabel(3, "Type");
        grid.setColumnLabel(4, "Label");
        grid.setColumnWidth(0, 14);
        grid.setColumnWidth(1, 16);
        grid.setColumnWidth(2, 14);
        grid.setColumnWidth(3, 12);
        grid.setColumnWidth(4, 18);
        grid.setHighlightRow(true);

        if (relations.isEmpty()) {
            grid.setCellText(0, 0, "(none yet)");
        } else {
            for (int i = 0; i < relations.size(); i++) {
                DatorRelation r = relations.get(i);
                grid.setCellText(0, i, nameOfColumn(myColumns, r.getColumnId()));
                grid.setCellText(1, i, nameOfTable(r.getRefTableId()));
                grid.setCellText(2, i, r.getRefColumnId() == null ? "(rowid)" :
                        nameOfColumn(columnsOf(r.getRefTableId()), r.getRefColumnId()));
                grid.setCellText(3, i, r.getRelationType());
                grid.setCellText(4, i, r.getLabel() == null ? "" : r.getLabel());
            }
        }
        for (int c = 0; c < 5; c++) {
            grid.setColumnReadOnly(c, true);
        }
        activate(grid);
    }

    private String nameOfColumn(List<DatorColumn> cols, int id) {
        for (DatorColumn c : cols) {
            if (c.getId() == id) {
                return c.getName();
            }
        }
        return "#" + id;
    }

    private String nameOfTable(int id) {
        for (DatorTable t : allTables) {
            if (t.getId() == id) {
                return t.getName();
            }
        }
        return "#" + id;
    }

    private void doSave() {
        try {
            app.getMetaRepository().replaceRelations(table.getId(), relations);
            app.showTableList();
            close();
        } catch (Exception e) {
            app.showError(e);
        }
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
