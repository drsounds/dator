package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TKeypress;
import jexer.TLabel;
import jexer.TMessageBox;
import jexer.TTableWidget;
import jexer.TWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/**
 * Browses the rows of one materialized table, either as a read-only grid
 * (with optional SUM/AVG/etc. summary rows) or as a vertical list of cards
 * (one per row, fields stacked). The view choice is remembered per table.
 * Rows can be narrowed down via a belongs-to multi-select filter.
 */
public class DataListWindow extends TWindow {

    private static final List<String> VIEW_OPTIONS = List.of("Table", "Card");

    private final DatorApplication app;
    private final DatorTable table;
    private List<DatorColumn> columns = new ArrayList<>();
    private List<Map<String, Object>> data = new ArrayList<>();
    private List<DatorSummary> summaries = new ArrayList<>();
    private Map<String, Set<String>> filterSelections = new LinkedHashMap<>();

    private boolean cardView;
    private TComboBox viewCombo;
    private TLabel filterStatusLabel;

    private TTableWidget grid;
    private TTableWidget topSummaryGrid;
    private TTableWidget bottomSummaryGrid;

    private int selectedCardIndex = 0;
    private final List<TWidget> cardWidgets = new ArrayList<>();
    private final List<List<TLabel>> cardLabelsByRow = new ArrayList<>();

    public DataListWindow(DatorApplication app, DatorTable table) {
        super(app, "Data: " + table.getDisplayLabel(), 96, 27);
        this.app = app;
        this.table = table;
        this.cardView = DatorTable.VIEW_CARD.equals(table.getListViewMode());
        setupWidgets();
        reload();
    }

    private void setupWidgets() {
        addLabel("Ins=New  Enter=View  F4=Edit  Del=Delete  F5=Refresh  F6=Filter  Esc=Close", 2, 1);

        addLabel("View", 2, 2);
        viewCombo = addComboBox(8, 2, 12, VIEW_OPTIONS, cardView ? 1 : 0, 3, new TAction() {
            public void DO() {
                setCardView("Card".equals(viewCombo.getText()));
            }
        });
        filterStatusLabel = addLabel("", 24, 2);

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
        addButton("Filter", 51, getHeight() - 3, new TAction() {
            public void DO() {
                openFilter();
            }
        });
        addButton("Close", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private void setCardView(boolean value) {
        cardView = value;
        table.setListViewMode(cardView ? DatorTable.VIEW_CARD : DatorTable.VIEW_TABLE);
        try {
            app.getMetaRepository().updateListViewMode(table);
        } catch (Exception e) {
            app.showError(e);
        }
        selectedCardIndex = 0;
        reload();
    }

    void applyFilter(Map<String, Set<String>> selections) {
        filterSelections = selections;
        selectedCardIndex = 0;
        reload();
    }

    private void openFilter() {
        new FilterWindow(app, this, table, filterSelections);
    }

    public void reload() {
        Map<Integer, Object> summaryValues;
        try {
            columns = app.getMetaRepository().listColumns(table.getId());
            data = applyFilterClientSide(app.getDataRepository().listRows(table, columns));
            summaries = app.getMetaRepository().listSummaries(table.getId());
            summaryValues = app.getDataRepository().computeSummaries(table, columns, summaries);
        } catch (Exception e) {
            app.showError(e);
            return;
        }

        filterStatusLabel.setLabel(filterSelections.isEmpty() ? "" : "(filtered - " + data.size() + " rows)");

        if (cardView) {
            renderCardView();
        } else {
            renderTableView(summaryValues);
        }
    }

    private List<Map<String, Object>> applyFilterClientSide(List<Map<String, Object>> rows) {
        if (filterSelections.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            boolean matches = true;
            for (Map.Entry<String, Set<String>> entry : filterSelections.entrySet()) {
                Object value = row.get(entry.getKey());
                if (value == null || !entry.getValue().contains(String.valueOf(value))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result.add(row);
            }
        }
        return result;
    }

    private void clearTableWidgets() {
        if (grid != null) {
            grid.remove();
            grid = null;
        }
        if (topSummaryGrid != null) {
            topSummaryGrid.remove();
            topSummaryGrid = null;
        }
        if (bottomSummaryGrid != null) {
            bottomSummaryGrid.remove();
            bottomSummaryGrid = null;
        }
    }

    private void clearCardWidgets() {
        for (TWidget w : cardWidgets) {
            w.remove();
        }
        cardWidgets.clear();
        cardLabelsByRow.clear();
    }

    private void renderTableView(Map<Integer, Object> summaryValues) {
        clearCardWidgets();
        clearTableWidgets();

        List<DatorColumn> displayColumns = GridUtil.visibleColumns(columns);
        Map<String, Map<Object, GridUtil.FkInfo>> fkIndex = GridUtil.buildFkIndex(app, table, columns);

        int width = getWidth() - 4;
        int contentTop = 4;
        int contentHeight = getHeight() - 9;

        topSummaryGrid = GridUtil.buildSummaryRow(this, 1, contentTop, width, displayColumns, summaries,
                summaryValues, DatorSummary.TOP);
        int topReserved = (topSummaryGrid != null) ? 4 : 0;
        int bottomReserved = GridUtil.hasSummaryAt(summaries, DatorSummary.BOTTOM) ? 4 : 0;

        int gridY = contentTop + topReserved;
        int gridHeight = Math.max(3, contentHeight - topReserved - bottomReserved);

        int colCount = Math.max(displayColumns.size(), 1);
        int rowCount = Math.max(data.size(), 1);
        grid = new TabbableTable(this, 1, gridY, width, gridHeight, colCount, rowCount);

        if (displayColumns.isEmpty()) {
            grid.setColumnLabel(0, "(no columns)");
        } else {
            for (int c = 0; c < displayColumns.size(); c++) {
                DatorColumn col = displayColumns.get(c);
                grid.setColumnLabel(c, col.getDisplayLabel());
                int width2 = Math.max(10, Math.min(24, col.getDisplayLabel().length() + 2));
                grid.setColumnWidth(c, width2);
            }
        }
        grid.setHighlightRow(true);

        if (data.isEmpty()) {
            if (!displayColumns.isEmpty()) {
                grid.setCellText(0, 0, "(no rows - press Ins to add one)");
            }
        } else {
            for (int r = 0; r < data.size(); r++) {
                Map<String, Object> row = data.get(r);
                for (int c = 0; c < displayColumns.size(); c++) {
                    DatorColumn col = displayColumns.get(c);
                    grid.setCellText(c, r, GridUtil.formatCell(fkIndex, col, row.get(col.getName())));
                }
            }
        }
        for (int c = 0; c < colCount; c++) {
            grid.setColumnReadOnly(c, true);
        }

        bottomSummaryGrid = GridUtil.buildSummaryRow(this, 1, gridY + gridHeight + 1, width, displayColumns,
                summaries, summaryValues, DatorSummary.BOTTOM);

        activate(grid);
    }

    private void renderCardView() {
        clearTableWidgets();
        clearCardWidgets();

        List<DatorColumn> displayColumns = GridUtil.visibleColumns(columns);
        Map<String, Map<Object, GridUtil.FkInfo>> fkIndex = GridUtil.buildFkIndex(app, table, columns);

        int x = 2;
        int y = 4;
        int width = getWidth() - 6;
        int maxY = getHeight() - 5;

        if (selectedCardIndex >= data.size()) {
            selectedCardIndex = Math.max(0, data.size() - 1);
        }

        int shown = 0;
        for (int r = 0; r < data.size(); r++) {
            int neededHeight = Math.max(1, displayColumns.size());
            if (y + neededHeight + 1 > maxY) {
                TLabel more = addLabel("... " + (data.size() - shown) + " more row(s) not shown (resize window or use Table view)",
                        x, y);
                cardWidgets.add(more);
                break;
            }
            Map<String, Object> row = data.get(r);
            List<TLabel> rowLabels = new ArrayList<>();
            if (displayColumns.isEmpty()) {
                TLabel l = addLabel("(no columns)", x + 1, y);
                cardWidgets.add(l);
                rowLabels.add(l);
                y++;
            } else {
                for (DatorColumn col : displayColumns) {
                    String text = col.getDisplayLabel() + ": " + GridUtil.formatCell(fkIndex, col, row.get(col.getName()));
                    if (text.length() > width - 2) {
                        text = text.substring(0, Math.max(0, width - 5)) + "...";
                    }
                    TLabel l = addLabel(text, x + 1, y);
                    cardWidgets.add(l);
                    rowLabels.add(l);
                    y++;
                }
            }
            cardLabelsByRow.add(rowLabels);
            y++;
            shown++;
        }

        if (data.isEmpty()) {
            TLabel empty = addLabel("(no rows - press Ins to add one)", x, y);
            cardWidgets.add(empty);
        }

        highlightSelectedCard();
    }

    private void highlightSelectedCard() {
        for (int i = 0; i < cardLabelsByRow.size(); i++) {
            String colorKey = (i == selectedCardIndex) ? "tlist.selected" : "tlabel";
            for (TLabel l : cardLabelsByRow.get(i)) {
                l.setColorKey(colorKey);
            }
        }
    }

    private void moveCardSelection(int delta) {
        if (cardLabelsByRow.isEmpty()) {
            return;
        }
        selectedCardIndex = Math.max(0, Math.min(cardLabelsByRow.size() - 1, selectedCardIndex + delta));
        highlightSelectedCard();
    }

    private Long selectedRowid() {
        if (data.isEmpty()) {
            return null;
        }
        int idx = cardView ? selectedCardIndex : (grid == null ? -1 : grid.getSelectedRowNumber());
        if (idx < 0 || idx >= data.size()) {
            return null;
        }
        Object v = data.get(idx).get(DataRepository.ROWID);
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
        if (cardView) {
            if (key.equals(TKeypress.kbUp)) {
                moveCardSelection(-1);
                return;
            } else if (key.equals(TKeypress.kbDown)) {
                moveCardSelection(1);
                return;
            }
        }
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
        } else if (key.equals(TKeypress.kbF6)) {
            openFilter();
            return;
        } else if (key.equals(TKeypress.kbEsc)) {
            close();
            return;
        }
        super.onKeypress(event);
    }
}
