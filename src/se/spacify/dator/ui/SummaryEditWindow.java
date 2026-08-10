package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TField;
import jexer.TKeypress;
import jexer.TTableWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/**
 * Manage the SUM/AVG/COUNT/MIN/MAX summary rows owned by a single table
 * model, shown at the top and/or bottom of its data grid (and of related
 * inline tables in the single-row view).
 */
public class SummaryEditWindow extends TWindow {

    private final DatorApplication app;
    private final DatorTable table;

    private List<DatorColumn> myColumns = new ArrayList<>();
    private final List<DatorSummary> summaries = new ArrayList<>();

    private TComboBox columnCombo;
    private TComboBox aggregateCombo;
    private TComboBox positionCombo;
    private TField labelField;
    private TTableWidget grid;

    public SummaryEditWindow(DatorApplication app, DatorTable table) {
        super(app, "Summary Rows for: " + table.getDisplayLabel(), 84, 24, TWindow.MODAL);
        this.app = app;
        this.table = table;
        loadData();
        setupWidgets();
        rebuildGrid();
    }

    private void loadData() {
        try {
            myColumns = app.getMetaRepository().listColumns(table.getId());
            summaries.clear();
            summaries.addAll(app.getMetaRepository().listSummaries(table.getId()));
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private void setupWidgets() {
        List<String> columnNames = new ArrayList<>();
        for (DatorColumn c : myColumns) {
            columnNames.add(c.getName());
        }

        addLabel("Column", 2, 1);
        columnCombo = addComboBox(2, 2, 20, columnNames, columnNames.isEmpty() ? -1 : 0, 6, null);

        addLabel("Aggregate", 24, 1);
        aggregateCombo = addComboBox(24, 2, 14,
                List.of(DatorSummary.SUM, DatorSummary.AVG, DatorSummary.COUNT,
                        DatorSummary.MIN, DatorSummary.MAX), 0, 5, null);

        addLabel("Position", 40, 1);
        positionCombo = addComboBox(40, 2, 14,
                List.of(DatorSummary.TOP, DatorSummary.BOTTOM, DatorSummary.BOTH), 1, 3, null);

        addLabel("Label (optional)", 56, 1);
        labelField = addField(56, 2, 20, false, "");

        addButton("Add Summary", 2, 4, new TAction() {
            public void DO() {
                addSummary();
            }
        });
        addButton("Remove Selected", 18, 4, new TAction() {
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

    private DatorColumn findColumnByName(String name) {
        for (DatorColumn c : myColumns) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private void addSummary() {
        if (myColumns.isEmpty()) {
            app.messageBox("No Columns", "This table has no columns to summarize.");
            return;
        }
        DatorColumn col = findColumnByName(columnCombo.getText());
        if (col == null) {
            app.messageBox("Validation Error", "Choose a column.");
            return;
        }
        DatorSummary s = new DatorSummary();
        s.setTableId(table.getId());
        s.setColumnId(col.getId());
        s.setAggregate(aggregateCombo.getText().isEmpty() ? DatorSummary.SUM : aggregateCombo.getText());
        s.setPosition(positionCombo.getText().isEmpty() ? DatorSummary.BOTTOM : positionCombo.getText());
        s.setLabel(labelField.getText().trim().isEmpty() ? null : labelField.getText().trim());
        summaries.add(s);
        labelField.setText("");
        rebuildGrid();
    }

    private void removeSelected() {
        if (summaries.isEmpty() || grid == null) {
            return;
        }
        int row = grid.getSelectedRowNumber();
        if (row < 0 || row >= summaries.size()) {
            return;
        }
        summaries.remove(row);
        rebuildGrid();
    }

    private void rebuildGrid() {
        if (grid != null) {
            grid.remove();
        }
        int rowCount = Math.max(summaries.size(), 1);
        grid = new TabbableTable(this, 2, 6, getWidth() - 4, getHeight() - 12, 4, rowCount);
        grid.setColumnLabel(0, "Column");
        grid.setColumnLabel(1, "Aggregate");
        grid.setColumnLabel(2, "Position");
        grid.setColumnLabel(3, "Label");
        grid.setColumnWidth(0, 16);
        grid.setColumnWidth(1, 12);
        grid.setColumnWidth(2, 10);
        grid.setColumnWidth(3, 20);
        grid.setHighlightRow(true);

        if (summaries.isEmpty()) {
            grid.setCellText(0, 0, "(none yet)");
        } else {
            for (int i = 0; i < summaries.size(); i++) {
                DatorSummary s = summaries.get(i);
                grid.setCellText(0, i, nameOfColumn(s.getColumnId()));
                grid.setCellText(1, i, s.getAggregate());
                grid.setCellText(2, i, s.getPosition());
                grid.setCellText(3, i, s.getLabel() == null ? "" : s.getLabel());
            }
        }
        for (int c = 0; c < 4; c++) {
            grid.setColumnReadOnly(c, true);
        }
        activate(grid);
    }

    private String nameOfColumn(int id) {
        for (DatorColumn c : myColumns) {
            if (c.getId() == id) {
                return c.getName();
            }
        }
        return "#" + id;
    }

    private void doSave() {
        try {
            app.getMetaRepository().replaceSummaries(table.getId(), summaries);
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
