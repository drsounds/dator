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
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/**
 * Browses the rows of one materialized table: a read-only grid (with
 * optional SUM/AVG/etc. summary rows above and/or below) plus
 * New / View / Edit / Delete / Refresh actions.
 */
public class DataListWindow extends TWindow {

    private final DatorApplication app;
    private final DatorTable table;
    private List<DatorColumn> columns = new ArrayList<>();
    private List<Map<String, Object>> data = new ArrayList<>();
    private List<DatorSummary> summaries = new ArrayList<>();
    private TTableWidget grid;
    private TTableWidget topSummaryGrid;
    private TTableWidget bottomSummaryGrid;

    public DataListWindow(DatorApplication app, DatorTable table) {
        super(app, "Data: " + table.getDisplayLabel(), 96, 26);
        this.app = app;
        this.table = table;
        setupWidgets();
        reload();
    }

    private void setupWidgets() {
        addLabel("Ins=New  Enter=View  F4=Edit  Del=Delete  F5=Refresh  Esc=Close", 2, 1);

        addButton("New", 2, getHeight() - 3, new TAction() {
            public void DO() {
                newRecord();
            }
        });
        addButton("View", 12, getHeight() - 3, new TAction() {
            public void DO() {
                viewSelected();
            }
        });
        addButton("Edit", 21, getHeight() - 3, new TAction() {
            public void DO() {
                editSelected();
            }
        });
        addButton("Delete", 30, getHeight() - 3, new TAction() {
            public void DO() {
                deleteSelected();
            }
        });
        addButton("Refresh", 40, getHeight() - 3, new TAction() {
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
        Map<Integer, Object> summaryValues;
        try {
            columns = app.getMetaRepository().listColumns(table.getId());
            data = app.getDataRepository().listRows(table, columns);
            summaries = app.getMetaRepository().listSummaries(table.getId());
            summaryValues = app.getDataRepository().computeSummaries(table, columns, summaries);
        } catch (Exception e) {
            app.showError(e);
            return;
        }

        if (grid != null) {
            grid.remove();
        }
        if (topSummaryGrid != null) {
            topSummaryGrid.remove();
            topSummaryGrid = null;
        }
        if (bottomSummaryGrid != null) {
            bottomSummaryGrid.remove();
            bottomSummaryGrid = null;
        }

        int width = getWidth() - 4;
        int contentTop = 3;
        int contentHeight = getHeight() - 8;

        topSummaryGrid = GridUtil.buildSummaryRow(this, 1, contentTop, width, columns, summaries,
                summaryValues, DatorSummary.TOP);
        int topReserved = (topSummaryGrid != null) ? 4 : 0;
        int bottomReserved = GridUtil.hasSummaryAt(summaries, DatorSummary.BOTTOM) ? 4 : 0;

        int gridY = contentTop + topReserved;
        int gridHeight = Math.max(3, contentHeight - topReserved - bottomReserved);

        int colCount = Math.max(columns.size(), 1);
        int rowCount = Math.max(data.size(), 1);
        grid = new TabbableTable(this, 1, gridY, width, gridHeight, colCount, rowCount);

        if (columns.isEmpty()) {
            grid.setColumnLabel(0, "(no columns)");
        } else {
            for (int c = 0; c < columns.size(); c++) {
                DatorColumn col = columns.get(c);
                grid.setColumnLabel(c, col.getDisplayLabel());
                int width2 = Math.max(10, Math.min(24, col.getDisplayLabel().length() + 2));
                grid.setColumnWidth(c, width2);
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
                    grid.setCellText(c, r, GridUtil.formatValue(row.get(columns.get(c).getName())));
                }
            }
        }
        for (int c = 0; c < colCount; c++) {
            grid.setColumnReadOnly(c, true);
        }

        bottomSummaryGrid = GridUtil.buildSummaryRow(this, 1, gridY + gridHeight + 1, width, columns,
                summaries, summaryValues, DatorSummary.BOTTOM);

        activate(grid);
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

    private void newRecord() {
        new DataRecordWindow(app, table, columns, null, new TAction() {
            public void DO() {
                reload();
            }
        });
    }

    private void viewSelected() {
        Long rowid = selectedRowid();
        if (rowid == null) {
            return;
        }
        new DataRowView(app, table, rowid);
    }

    private void editSelected() {
        Long rowid = selectedRowid();
        if (rowid == null) {
            return;
        }
        try {
            Map<String, Object> row = app.getDataRepository().getRow(table, columns, rowid);
            if (row == null) {
                app.messageBox("Not Found", "This row no longer exists.");
                reload();
                return;
            }
            new DataRecordWindow(app, table, columns, row, new TAction() {
                public void DO() {
                    reload();
                }
            });
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
                "Delete this row?", TMessageBox.Type.YESNO);
        if (!result.isYes()) {
            return;
        }
        try {
            app.getDataRepository().deleteRow(table, columns, rowid);
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
        } else if (key.equals(TKeypress.kbEnter)) {
            viewSelected();
            return;
        } else if (key.equals(TKeypress.kbF4)) {
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
